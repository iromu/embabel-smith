# Design

## Context

The hard facts this design has to work with, each read from the tree on 2026-11-18.

- The skill pipeline publishes three files and scans one of them. `SkillPublishable.writeTo` writes
  `SKILL.md` (line 55) and `evals.json` (line 56) and then the sidecar
  (`this.stamper.write(directory, this.lifecycle);`); `SkillBuilderService.java:130` scans only
  `skill.toSkillMd()`. The scan happens at stage 6, after the lift gate, so a blocked scan has
  already spent the eval calls.
- The scanner already accepts text rather than a spec: `AgentSpecSecurityScanner.java:117-118`
  `public ScanReport scanText(String name, String text) { List<Finding> findings =
  textFindings(List.of(text));` — and `textFindings` takes a **list** of texts. So scanning a second
  document needs no new check, only a second element.
- The suite's emptiness is a default, not an error: `EvalSuite.Eval`'s compact constructor line 80
  `expectations = (expectations != null) ? List.copyOf(expectations) : List.of();`. `EvalSource`
  parses whatever the model returned (`LlmJson.callAndParse(caller, prompt, user, 3, "the generated
  evals", EvalSuite::parse)`) and validates nothing about its shape beyond "parses".
- `Benchmark.rate` line 71 `return (total == 0) ? 0.0 : (double) passed / total;` — an empty
  scenario contributes `0/0` to both arms, so it drags both pass rates toward zero equally and the
  lift is unaffected by it; the harm is the wasted calls, the misleading `+0.000` verdict, and the
  silent gating on whatever real scenario survives.
- The only existing refusal is `SkillBuilderService.java:120` `if (evals.evals().isEmpty()) {`, and
  the only test of it is `SkillBuilderServiceTest`'s `aSuiteWithNoScenariosCannotBeValidated`.
- `BuildOptions` already carries `minLift` with `DEFAULT_MIN_LIFT = 0.0` and the gate is already
  strict (`summary.lift() <= options.minLift()` → blocked), so the knob needs no new semantics —
  only a caller that can set it.
- `BuildCommand` builds its options once, at line 173-174, passing both gate constants as defaults;
  `--max-refine` shows the established shape of a per-build knob with a `${DEFAULT-VALUE}` in its
  help text.
- The skill service is constructed per call in `newSkillService()`, and its caller (`SpecPromptCaller`)
  is the seam the tests substitute, so "no arm ran" is observable as a call count in
  `SkillBuilderServiceTest`.
- The studio already models an optional per-request knob as a nullable wrapper:
  `rest/dto/BuildRequest.java:21`
  `public record BuildRequest(String description, String outputSource, Integer maxRefine, Boolean overwrite, Boolean push,`
  `String model)`, consumed as
  `(request.maxRefine() == null) ? BuildOptions.DEFAULT_MAX_REFINE : request.maxRefine()`. The lift
  one line further down is not treated that way: line 65 passes `BuildOptions.DEFAULT_MIN_LIFT`
  literally.
- The same construction pins the target — line 64
  `request.push() != null && request.push(), GenerationTechnique.SINGLE_SHOT, BuildTarget.AGENT_SPEC,`
  — so `/api/build` cannot produce a skill, and the lift gate is therefore unreachable from the
  studio regardless of what the request carries.

## Goals / Non-Goals

Goals:

- Every byte of model-authored text that reaches a published skill folder has been through the
  static scan that already guards the `SKILL.md`.
- A suite that cannot measure anything is refused as malformed, at the point it is known to be
  malformed, rather than run and reported as a zero lift.
- The person running `smith build --target skill` can set the lift the skill must clear.

Non-Goals:

- No change to the lift formula, to the strictness of the comparison, or to the judge.
- No retry against the model to get a better suite. The eval-drafting call already retries three
  times on a parse failure (`LlmJson.callAndParse(..., 3, ...)`); a suite that parses but is empty
  is a model answer the builder should report, not one it should pay for again.
- No per-scenario weighting, no "drop the empty ones and gate on the rest", no minimum scenario
  count beyond "at least one, each non-empty".
- No studio lift knob, and no studio code change at all: `/api/build` pins `BuildTarget.AGENT_SPEC`,
  so the lift gate is unreachable from the studio and a request field naming a minimum would be dead
  configurability. The reason is recorded in `docs/08-builder.md` instead (see D5).
- No change to the `git:` publish location — separate change.

## Decisions

**D1 — Scan the published documents, not the skill object.** The scan call gains the suite's JSON as
a second text, through a list-taking `scanText` overload that the existing single-text form delegates
to. Rejected: concatenating `SKILL.md` and `evals.json` into one string before the call — it works,
but a finding's evidence line would be attributed to a document that is neither, and the report
already names the scanned artifact. Rejected: scanning the files after `writeTo` has written them —
that would put the scan after the write, which is the ordering the validate-before-write invariant
forbids. Rejected: scanning `lifecycle.yml` — `LifecycleStamper` generated it from a hash and a pass
rate; scanning the builder's own output adds noise, not coverage.

**D2 — Refuse a scenario with no expectations, at the `eval` stage, listing the ids.** The check sits
beside the existing `evals.evals().isEmpty()` guard and returns through the same
`SkillBuildResult.blocked("eval", …)` channel, so a blocked build stays inspectable in the same
shape. Rejected: dropping empty scenarios and aggregating the rest — that is precisely the "silently
gates on the one real eval" case the backlog names, now made invisible. Rejected: grading an empty
scenario as a pass — it would inflate both arms and manufacture lift from nothing. Rejected:
validating in `EvalSuite`'s constructor — `EvalSuite.parse` is also how a *published* `evals.json` is
read back (`SkillPublishableTest` asserts that round-trip), and a format constructor that refuses
would make already-published degenerate folders unreadable.

**D3 — `--min-lift` as a plain `double` option with the constant as its default.** It is threaded
into the one `BuildOptions` construction in `call()`. Rejected: making it an error on an agent-spec
build — picocli options are not target-scoped today (`--max-refine` is accepted for both) and
enforcing that would add a validation branch for no benefit. Rejected: a `--score-gate` twin in the
same change — the score gate is the agent-spec concern and adding it here would widen the diff
without a requirement behind it; it stays a documented default.

**D4 — The docs edit is part of the change, not an afterthought.** `docs/08-builder.md` is the
contract for the skill target; its sentence "With a configurable minimum of 0" is currently true
only of a programmatic caller.

**D5 — The studio gets no lift knob. Ruled in, then ruled out; this is the ruling that stands.**
Backlog item 3 names the studio's `DEFAULT_MIN_LIFT` beside the missing flag, so this change first
drafted knob parity as a nullable `BuildRequest` field consumed the way `maxRefine` already is. That
draft was withdrawn on a fact found while it was being written: `rest/BuildController.java:64` reads
`request.push() != null && request.push(), GenerationTechnique.SINGLE_SHOT, BuildTarget.AGENT_SPEC,` —
`/api/build` pins the agent-spec target, and the lift gate only applies to skill builds. A field
naming a minimum would therefore be an option no caller can reach. `AGENTS.md` rules out
"flexibility or configurability that wasn't requested", and a spec requirement over an unreachable
field is worse than no requirement because it makes dead configurability look contracted. So the
literal stays and `docs/08-builder.md` records why.
Rejected: exposing the generation technique or the build target in the pane to make the knob
reachable. That is a UI decision — which control, which pane, what the build report then has to show
for a skill — with no requirement behind it, and the studio is a UI whose build logic belongs to the
builder. Rejected too: adding the field anyway "for when a skill build is exposed", which is
speculative by definition.
**Why this decision is worth reading closely**: the leader ruled the field in, the implementer-facing
finding at line 64 showed it was unreachable, and the leader then ruled it out. The reversal, not the
first ruling, is the state to implement.

## Risks / Trade-offs

- **A stricter suite gate blocks more builds.** A model that drafts a scenario-less suite now stops
  the build instead of publishing on a hollow `+0.000`. That is the intent; the mitigation for the
  person hit by it is the message, which names the scenario ids so the description can be sharpened.
- **Scanning the suite can block on JSON syntax noise.** The scanner's checks are substring and
  pattern based over prompts; `evals.json` is prompts in JSON clothing, which is what it is. If a
  legitimate expectation string trips a `CRITICAL`, the finding carries the evidence and the build
  report is the place it surfaces — the same false-positive profile the `SKILL.md` scan already has.
- **`--min-lift` invites a user to raise the bar to a value the eval cannot measure.** With four
  scenarios, the lift moves in coarse steps; the report already prints both pass rates, so a
  mis-sized gate is visible rather than mysterious.
- **Test growth is small but real**: two service tests and one CLI test, each with the existing fake
  caller rather than an endpoint.
- **The no-studio-knob reason can go stale.** It is true only while `rest/BuildController.java:64`
  pins `BuildTarget.AGENT_SPEC`. The day that pane can build a skill, the sentence in
  `docs/08-builder.md` becomes false and a real request knob becomes warranted — task 4.1's grep is
  what falsifies it, and the `docs/12-backlog.md` wording ("not reachable from the studio today")
  reads as a dated claim rather than a permanent ruling.
