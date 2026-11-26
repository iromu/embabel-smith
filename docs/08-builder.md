# The Builder

`prod/embabel-smith-builder` turns a natural-language description into a deployable capability: it
generates a draft, validates it, evaluates it, refines it within a bound, security-scans it, stamps
lifecycle metadata, and publishes it. The output is a *target*: an agent spec, an Agent Skill, or a
multi-agent process.

## It is still an Embabel agent

`core/BuilderAgent` is a code-backed `@Agent` whose actions have typed pre- and postconditions:
compile → validate → write → evaluate. **`BuilderService.build()` holds that order explicitly** —
it invokes the actions in the sequence the GOAP planner would choose, so the order is deterministic
by construction rather than planned at run time.

**The actions are code-backed, not LLM-backed.** Downstream steps depend on the typed
`ValidationResult` contract, and letting a model decide the order would introduce exactly the
non-determinism a builder must not have. The split is precise: *pipeline order* is deterministic,
*emitted prompts* are model-generated — and `--flow pack` below does not move the order either, because
the pack it runs has a single step, so there is nothing there to plan.

**Validate before write is mandatory.** The generator is model-driven and can emit wrong bindings or
schemas, so the write step may run only after the deterministic `AgentSpecValidator` has passed the
draft — an invalid draft returns a blocked result and nothing is written. The throwaway-platform
deploy comes *later*, as the eval. This is the guardrail that makes "generate with no code" safe, and
it has survived every later builder change.

## Ingestion: documents as build input

A business-process document set is a build input, not only something a user interface can read.
`blueprint/` holds the three stages that turn documents into the description a build consumes, and
each is callable on its own:

| Stage | Class | Bound |
| --- | --- | --- |
| load | `blueprint/BusinessProcessDiscovery` | 300K of `.md`/`.markdown`, from a folder or a `git clone --depth 1` bounded to 180 s |
| assemble | `blueprint/BlueprintLoader` | 200K corpus, every document labelled with its path relative to the root |
| extract | `blueprint/ProcessExtractor` | the `business_process_discovery` prompt through `SpecPromptCaller`; the answer is plain text, not a spec to parse. The prompt wants a *codebase*, so this is the repository-to-blueprint step, not a step to run over a blueprint — see the note under the CLI |
| corpus | `blueprint/CodebaseCorpus` | 200K read from a repository's source *and* documents, the documents capped at half of it so a heavily documented project cannot crowd its own code out |
| archive | `blueprint/ZipArchive` | a repository archive (a forge's repository download) extracted to a temp directory, with the wrapper directory the forge adds lifted so the labels stay the repository's own; an entry named to write outside the extraction directory is refused |
| blueprint | `blueprint/BlueprintGenerator` | fetch → corpus → extract → split, writing the seven deliverables and an index that states how much of the repository the model did not see |

**These are pre-stages, not pipeline stages.** Each ends in a `String` and stops there, so
`BuilderService.build(String, PublishTarget, BuildOptions)` keeps its signature and the gate pipeline
is untouched — an ingestion step has no gate to belong to, and the studio's UI needs to show documents
and a description as separate steps. The two caps are different values on purpose: 300K is the corpus
the workspace *presents*, 200K is the corpus a generation *runs from*.

The repository clone runs with `GitPublisher`'s non-interactive posture
(`GIT_TERMINAL_PROMPT=0`, `core.askpass=false`), so a private repository fails rather than waits for
credentials on a terminal nobody is reading.

Four shapes were refused while this was being placed, and they are the reasons the code looks the way
it does. It went into its own `blueprint/` package rather than `core/`, because `core/` is about
generation and the two would become one package with two reasons to change (and `docs` was refused as
a name, colliding with the repository's own `docs/`); the class names were kept unchanged, because a
rename turns a reviewable `git` rename into an unreadable diff for no behavioural gain. Extraction did
not move inside `build()` behind a documents path, because extraction has no gate to belong to and
would force the studio to pay for a step its own UI exposes separately. `--extract` is a flag rather
than always-on or a separate `smith ingest` subcommand: always extracting doubles the model cost of
every docs build, and a subcommand would put the description in a temp file for a human to paste. The
two loaders each keep their own identical eight-line home-directory expander — consolidation was
correct but was left out so the move stayed a relocation a reviewer could check line by line.

## Generation technique

Two techniques, selectable, both feeding the same downstream pipeline:

| Technique | Default | Notes |
| --- | --- | --- |
| `single-shot` | **yes** | one model call, prompt instructs it to declare every non-framework type in `types/` |
| `embabel` | no | Embabel's natural-language compiler |

Single-shot became the default on measured evidence: POC 24 scored the Embabel NL compiler at
**0/6 valid packs against 4/6** for single-shot on the same live matrix. The builder had only the
weaker path. Read the single-shot figure with the POC's own caveat — the model is non-deterministic,
and a re-run of the same matrix scored single-shot multi **3/3** where the first run scored 1/3. The
stable finding is `embabel` at 0/N across every run, not the exact single-shot count.

Two design points a reader of the code will otherwise trip over:

- **The technique seam returns the builder's own `CompiledDraft`** (pack name + `AgentSpec` +
  issues), not an Embabel type. No `AgentCompilationResult` crosses the seam. Keeping the Embabel
  type would have meant an Embabel-only compiler plus a parallel single-shot path — two techniques
  shaped differently, which is the asymmetry the change removed. The draft record also preserves the
  existing issue channel, where an ambiguous description blocks at `build` with the draft retained.
- **An unrecognized technique is refused at the CLI** with a usage error; the studio falls back to
  single-shot rather than failing the request.

### Known upstream limit: the `embabel` technique cannot publish a multi-step pack

Embabel's NL compiler reports a type as *intermediate* only when **no step outputs it**, so for a
normal multi-step agent `getIntermediateTypes()` comes back empty. Every step and goal
`outputTypeName` then reaches `AgentSpecValidator` undeclared and the build blocks with one
`DANGLING_TYPE_REF` per reference. Observed directly: the compiler logs `3 actions, 0 intermediate
types` for a three-step agent, followed by seven dangling references. POC 24 scored this technique
0/6, but its README attributes those failures to cross-agent type-name mismatches, an invented entry
type and void goals as well — the dangling types are the dominant cause, not the only one.

This is upstream compiler behaviour, not a builder bug, so no amount of retrying or prompt work on
the embabel path fixes it. Synthesizing a `DynamicTypeDef` per undeclared output *would* let the
build publish, but `types/` would then carry bare names with no property definitions — a weaker pack,
not a correct one. That was **deferred rather than done silently**: the technique is verified at the
generation seam only. See [`12-backlog.md`](12-backlog.md).

## Who issues the generation call: `--flow`

A different axis from `--technique`, which decides *how* a description becomes a spec. `--flow`
decides *who* asks. It is a CLI flag with one branch in `cli/BuildCommand.generators()` and nothing
behind it: no `smith.builder.flow` property and no field in `BuildOptions`. What attributes a run to a
flow is its own output — the CLI stamps the flow on the `BuildResult` it got back (`withFlow`, one call
site) and `report()` prints a `flow:` line under its header — so a parity or cost run can be read from
what it printed rather than from how it was launched. A build driven from outside the CLI, the studio's
`/api/build` included, stamps nothing and prints no flow line rather than guessing `java`.

| Flow | Default | Generation call | Who runs the deterministic checks |
| --- | --- | --- | --- |
| `java` | **yes** | the Java stages call the generator | the numbered Java stages of `BuilderService` |
| `pack` | no | `specs/builder-spec-one-shot`, one step, `validate` declared | the same Java stages; the step *may* also call the validator |
| `agentic` | no | `specs/builder-agentic-blueprint`, two steps, reading through tools | the same Java stages, **plus** the driver itself, between model calls |
| `harness` | no | `specs/builder-harness`, three steps, deciding what to read next | the same Java stages, **plus** the driver itself, between model calls |

`--flow java` is what every build did before the flag existed and what a build does now. The pack path
in each row is a classpath resource under the builder module — `prod/embabel-smith-builder/src/main/resources/specs/`,
addressed by `core/BuilderPackPaths` — so a pack flow finds its pack wherever the process started. Asking a
pack flow for `--technique embabel` is refused before any model call, for all three pack flows: the
pack each runs is the single-shot prompt restated, and there is no pack for the NL compiler. Nothing
falls back — a flow that fails names the stage that stopped it.

`--flow agentic` is the route where the builder's own capabilities are tools — `harness` too, on the
same surface, so everything below about the surface, the forcing and the deploy check applies to both.
Its first step surveys the documents and the library; its second drafts the spec. What it can reach is a
closed list, and the list is a tested fact rather than a reading of the source:

| Role | Tools | Reads | Never |
| --- | --- | --- | --- |
| `validate` | `validatePack` | the spec text a step is about to return | a verdict — findings only |
| `blueprint` | `list_docs`, `read_doc`, `assemble_corpus` | the documents `--docs` opened, `.md`/`.markdown` only | any path outside those roots, or a directory of its own choosing |
| `library` | `list_packs`, `rank_packs` | the `folder:` library the build publishes to | a `git:` source — resolving one spawns git and writes a cache |
| `security` | `scan_spec` | the spec text, statically | `recommendedTier()`, which is suppressed in the tool |
| `traces` | `trace_window`, `detect_regressions` | the trace store the driver wired, if any | a path to `~/.smith/traces` it invented — with no store it says so |
| `process` | `validateProcess` | the process document a step is about to return | a directory to guess at |

Three properties of that surface are load-bearing. **The driver, not the model, chooses what is
read**: `tools/ToolSurface` holds the roots the command line was given, and a document name outside
them is refused — the failure it replaces is measured, one build spending 116 `validatePack` calls
guessing directories with 92 of 93 results the same `LAYOUT_MISSING_MANIFEST`. **Every result is
capped** with an explicit truncation marker (`tools/ToolOutput`, 20,000 characters), and a capped
`read_doc` says which offset to ask for next rather than ending the document. **An identical repeat of
a call is answered from the first one** (`ToolSurface#remembered`, keyed on tool plus arguments, cleared
by `startRun()` when either file-opening flow — `agentic` or `harness` — begins a build), so re-sending
an unchanged draft no longer costs
a second run of the same deterministic service. Every tool body routes through it — the three checks as
much as the reads — because the repeats the measured runs counted were validator calls: 6 on one
blueprint, 19 on another that never produced an answer. Where a driver does not clear the memo it is still
safe, and only because the services behind those tools are pure functions of their argument; the file
reads are the ones that need the per-build clear, which is why the driver that opens files is the one that
calls `startRun()`.

What is deliberately *not* on the list is a rule rather than an omission: everything excluded is a
verdict (`GateChain`, the lifecycle checks, `check_tier`), a write (publish, file edit), a model call
(`Refiner`, eval execution, and the three scoring prompts `grade_eval` / `agent_baseline` /
`agent_with_skill`, which stay classpath resources under `skill/ScorerPromptBoundaryTest`), or the
referee. A shell, filesystem-write, web, MCP, subagent or model-writable-memory tool would turn a
blueprint document into a remote-code-execution path, and the builder's own tier check would not stop
it — see [`12-backlog.md`](12-backlog.md).

Four of those refusals carry their own reason, because each looks harmless from inside a builder.
`todo_write` is refused beyond at most a read-only view of `ApprovalQueue#pending` and
`InFlightSet#inFlight`: a model-writable queue lets an agent clear its own approvals, and candidates
hold for approval by default ([`10-self-improvement.md`](10-self-improvement.md)). `agent` is refused
because the spec format has no delegation primitive and POC 27's referee is a separate JVM precisely so
a candidate cannot generate the code that grades it. `memory` is refused because the product's memory
already exists — packs, `lifecycle.yml` and traces, all written by deterministic Java — so a
model-writable memory file would be a mutable surface with no validator and no gate. And
`web_fetch`/`web_search` are refused because the bounded fetch (shallow clone, 180 s, non-interactive)
stays a driver stage, no search backend exists under `prod/`, and neither role resolves to anything.
The narrowing is one rule rather than six verdicts: everything refused is a verdict, a write, a model
call, or the referee; everything adopted is read-only, deterministic and free.

The forcing is the other half of the route, and it is in Java because an invitation was not enough.
The measured TradingAgents run *offered* the drafting step `validatePack` and the step **declined**
it; eval then found sixteen dangling type references. So `eval/AgenticAgentSpecGenerator` runs the
validator and the scanner on each draft itself and, where they report findings, re-invokes the same
pack with those findings appended to the same request — bounded at two re-invocations, after which the
draft goes on to the existing gates with what still failed recorded as its issues. The step's own
tool call is kept because self-correcting inside one call is cheaper than a whole re-invocation.

That deploy also refuses a declared role the platform cannot resolve, which closes the blind spot
`AgentSpecValidator` leaves open: it inspects no `toolGroups`, so a misspelled group used to deploy
verbatim and reach nothing at prompt time while `TrustTierPolicy` read the name as a declaration and
moved the tier as though it meant something. The check is in the agentic deployer rather than in
`eval/AgentSpecInvokerFactory`, which is also the eval and improvement path — and the harness flow gets
it for the same reason it gets the forcing loop: its generator extends that one, so declaring a role the
context does not resolve refuses the build before anything deploys or spends.

The resolved set, measured on a booted context rather than asserted: `availableToolGroups()` returns
exactly `validate`, `blueprint`, `library`, `security`, `traces`, `process` and `math` — the last one
registered by Embabel's own autoconfigure and not by anything here. `web`, `maps`, `github`,
`browser_automation`, `shell`, `file`, `search`, `agent` and `osx` resolve to nothing.
`embabel-agent-api-1.5.4.jar` carries 62 tool classes on this module's compile classpath, including
`tools/file/` and `tools/process/` with a shell-capable `ExternalProcessTools`, and **none of them is
reachable**, because nothing in this context publishes them as a group: classpath presence is not
reach. `tools/ExposedToolGroupsTest` pins the set as literals, so an upstream bump that auto-registers
a file- or process-backed group fails the build instead of quietly widening what a pack can do.

Whether a model then *calls* one of these tools is a different number, belonging to the hand-run
scripts below; a registry assertion is not evidence about model behaviour.

**`--flow harness` is the fourth value of the same flag, and it differs from `agentic` in one
respect: who decides what gets read.** `specs/builder-harness` is three steps —
`surveyDocumentRoots` (declares `blueprint`, `library`), `planWhatToReadNext` (`blueprint`) and
`draftAgentSpecFromRoots` (`blueprint`, `validate`, `security`) — each with an output type the pack
declares itself (`DocumentInventory`, `ReadingPlan`, `AgentSpecJson`). On the agentic route the driver
calls `assemble_corpus` once and every step sees the same blob; here a step calls `list_docs` for what
exists, `read_doc` for the one it wants (with the offset a capped answer names), and the plan step
decides what the drafting step should still go and read. The roots stay the driver's: the command line
opens them and a step that names a path outside them is refused, with an inside-the-roots read as the
control case (`tools/HarnessDocumentCursorTest`).

What it *cannot* reach is that same list read from the other side: the four names its steps declare are
the whole of its capability, `list_docs` and `read_doc` are the `blueprint` group's own tools rather
than a separate file surface, and no builder registration hands a step a write, a shell or a network
call. The flow buys a deciding act, not a wider one.

The driver is the same class rather than a second one — `eval/HarnessAgentSpecGenerator extends
eval/AgenticAgentSpecGenerator`, differing only in which pack it deploys and the label on its failure
messages — so the forcing ceiling, the identical-call memo and the per-build window clearing are one
implementation. Two things follow that are worth stating rather than leaving to inference. The corpus
handover did **not** move: `BuildCommand.resolveDescription()` still assembles it for a `--docs` build
under this flow, because `newService` wires `SingleShotAgentsPackGenerator` unconditionally and the
multi-agent candidate consumes the assembled text, so the pack's prompts tell the steps to work the
roots through the tools and treat the inline text as a capped first reading. Cutting that handover is a
dual-candidate change with its own cost, and it is in [`12-backlog.md`](12-backlog.md) rather than
buried here. And the gate list shows the one place the flow is visible in a build's record: on a draft
whose validation fails, the Java flow logs `[generate, validate]` and the harness flow `[generate]`,
because its validator ran inside generation and the defect arrived with the draft — both stop at the
same `build` stage and neither writes anything (`FlowGateParityTest`). One endpoint run of this flow is on
the record, and it did not build: `parcel-claims` under `--flow harness` answered its two reading steps —
`surveyDocumentRoots` in 30.409 s and `planWhatToReadNext` in 59.332 s, that second one 0.668 s inside the
interaction timeout — then spent 40 timeouts on `draftAgentSpecFromRoots` across 43 tool loops and 89 tool
calls before the script's own 2700 s cap, printing no verdict line. The same input under `--flow agentic`,
on code-identical classes, answered one step and made 124 to 179 calls in two passes, also with no verdict.
So the harness flow's measured delta over the agentic one is **one extra landing step, no extra verdict**,
and the wall is the same in both: a whole AgentSpec does not get emitted inside 60 s once the step has
spent part of that clock reading. Other inputs and other endpoints remain unmeasured, which is what the
parity script below is for.

**Six packs ship under `prod/embabel-smith-builder/src/main/resources/specs/` and three of them are wired.**
`builder-spec-one-shot` (1 step) is the pack `--flow pack` names, `builder-agentic-blueprint`
(2 steps) the one `--flow agentic` names and `builder-harness` (3 steps) the one `--flow harness`
names; `builder-single-spec` (5 steps), `builder-process-composition` (4) and
`builder-skill-from-process` (3) are read by tests and by POC 27 alone. They are classpath resources,
addressed by `core/BuilderPackPaths` (`ROOT` = `specs`) over `smith-common`'s `SpecFolders.classpath`,
which hands back the class directory itself when classes are exploded and a temporary copy when they sit
inside a jar — so the directory a build starts in decides nothing about whether its pack loads. What the
native binary does with them is a separate matter, and it is in [`12-backlog.md`](12-backlog.md). The
separation from the referee is now by directory rather than by being off the classpath, and it is still
load-bearing rather than tidy: `src/main/resources/prompts` holds the scorer an improvement loop must not
edit, `specs/` holds the surface it may, which is the boundary
`skill/ScorerPromptBoundaryTest` guards by content. Four of the six
name a role: `builder-spec-one-shot` and `builder-single-spec/steps/assembleAgentSpec.yml` declare
`toolGroups: [validate]`, and the agentic pack's two steps and the harness pack's three declare
`blueprint`, `library`, `validate` and `security` between them — all inside the seven roles a booted
context resolves, which is what makes the deploy-time refusal below reachable rather than theoretical.
So how far a step can reach is now partly a pack declaration rather than wholly the driver's. The
deterministic stages above are unchanged by that. None of the pack flows is the default because it lost
on measurement, not because it was left undone: against the same three inputs `pack` built one of three
where `java` built two of three, and on one input the drafting step was offered the validator and
declined it, after which eval found sixteen dangling types — the gap `--flow agentic` closes by running
those checks itself, and `--flow harness` closes the same way while also choosing its own readings. What
a pack candidate may touch is [`10-self-improvement.md`](10-self-improvement.md).

A pack step holds a *cognitive* stage and nothing else, and that is a format constraint rather than a
style rule: a step needs a prompt, and a step with no prompt is already invalid (`PROMPT_MISSING`), so
a deterministic check can enter a pack only as a tool the step is offered — which is exactly why the
deterministic half of the agentic route stayed in Java. One more format rule binds what a rewritten
step may look like: a step that declares an input its prompt never interpolates is refused
(`PROMPT_IGNORES_DECLARED_INPUT`), because a prompt is a literal template and an input it never names
is never sent to the model — see
[`agent-spec-format.md`](agent-spec-format.md) §Validation for that check and the POC 27 failure it
exists to catch.

## Evaluation and refinement

`eval/RefinementLoop` rewrites **the terminal action's prompt** — the one producing the goal output —
from the failures, one prompt per round, up to a bound. A wrong prompt on a mid-pipeline step is
never rewritten, so a pack can reach the bound without the failing step ever being touched.

| Setting | Default |
| --- | --- |
| score gate | `1.0` (`BuildOptions.DEFAULT_SCORE_GATE`) |
| refinement rounds | `3` (`--max-refine`) |

Below the gate after the bound, the build reports and stops — **nothing is published**. The pass-rate
that clears the gate becomes the pack's recorded **baseline**, which the improvement loop compares
future behaviour against.

**The eval invoker runs the pack's own goal.** It originally used
`AgentInvocation.create(platform, BackgroundMessage.class)`, which only matched packs whose goal
output `BackgroundMessage` — but the generation prompt asks the model for the *deliverable* type, so
every single-shot build aborted. It now deploys the pack and `runAgentFrom`s its own goal, the
runtime's path. An unachieved goal yields no output, fails the eval task, and the refinement loop
acts, instead of the build aborting.

## Security scan and lifecycle

`security/AgentSpecSecurityScanner` is the install-time static scan proven in POC 21; a `CRITICAL`
finding blocks publication, while `WARN` findings force review without blocking (the scale is
`INFO` / `WARN` / `CRITICAL` — there is no `HIGH`). It shares the honest limit of any non-LLM pattern
matcher:
it audits only what the spec *declares*, so a novel rephrasing outside its pattern set can slip
through and a capability smuggled through a bundled script is beyond a static YAML scan.

`lifecycle/LifecycleStamper` records a `lifecycle.yml` **sidecar** — content hash, baseline
pass-rate, state `incubating`. A sidecar rather than a manifest field because the manifest is
format-owned by `smith-common` and shared with the frozen POCs; the sidecar carries the lifecycle
contract without touching the format.

The pack name is a deterministic slug of the **whole** description — lowercased, non-alphanumerics
collapsed to hyphens, trimmed to 40 characters, falling back to `built-spec`. No noun-phrase
extraction is involved, so builds are reproducible but the name tracks the description's opening.

## Build targets

`--target` selects the *published* artifact, and there are exactly two values: `agent-spec` and
`skill`. `core/BuildTarget` declares nothing else, so `--target process` is a usage error — see the
last subsection. The gate differs per target, which is why they are parallel services rather than an
overloaded `build`.

Every `agent-spec` build also generates and scores a multi-agent process candidate, whatever the
caller asked for. That is a second set of model calls per build; it is not surfaced in `--help`.

### `agent-spec` (default)

The pipeline above, publishing through the grammar in
[`06-spec-sources.md`](06-spec-sources.md).

### `skill`

Generates a portable Agent Skill (`SKILL.md` plus `evals.json`), validated by a **with-skill versus
no-skill eval lift** rather than an absolute score. `SkillMdValidator` enforces the frontmatter
constraints (name 1–64 lowercase alphanumeric-or-hyphen, description ≤ 1024).

**The `inspect` gate runs the skill writing guidelines before any eval call is paid for.** After the
frontmatter check and *before* the model drafts the `evals.json` suite, `skill/SkillInspector` writes
the draft to `SKILL.md` in a temp folder named after the skill and runs
`embabel-smith-skillevaluator`'s `Tier1Runner` over it. Six checks block the build — `schema`,
`version`, `quality`, `unicode`, `lint` and `license` — and `pii` runs but never blocks: it is
advisory by check name, not by severity, so a stricter PII heuristic cannot quietly gain blocking
authority. Blocking findings go back to the model as a redraft request, at most `--max-inspect` times
(default `BuildOptions.DEFAULT_MAX_INSPECT = 2`, so at most three drafts); a draft that still fails is
refused at stage `inspect` and nothing is published. The placement is the point: inspection makes no
model call, so a draft scoring under the quality floor — 70 by default,
`Constants.QUALITY_DEFAULT_MIN_SCORE`, failed through the validator's gate rather than through a finding
— is refused before the benchmark spends four calls per scenario measuring it. Going over the 500-line
body ceiling is **not** one of the refusals: `line_count` reports MEDIUM, which the gate treats as
advisory, so a long draft is commented on and still published. Advisory findings are reported, never fed
back — they are MEDIUM and LOW nudges, and a redraft per nudge would buy padding and nothing else. Both
knobs are read only by the skill gate: `--max-inspect` is accepted on an
agent-spec build and unused there, exactly as `--min-lift` and `--max-refine` already are.

**A default skill build does no secret scanning, no SkillSpector security scan and no script risk
analysis.** The three checks that would do those things — `security`, `secrets` and `code-integrity` —
are not requested, because each shells out to a third-party binary (`skillspector`, `gitleaks`,
`bandit`, `semgrep`) whose absence the evaluator records as an incomplete scan. Every build that
reaches the gate therefore prints the limit as its own report line —
`not scanned: security, secrets, code-integrity (no secret, security or code-risk scanning; pass
--full-inspection to request them)` — beside `inspect: <n> checks run, quality <score>/100, <n>
draft(s)`. A build refused earlier, at `generate` or `validate`, prints neither line, because no
inspection ran. `--full-inspection` is the only way to request those checks, and it is an explicit
human action: it is the one route on which the evaluator's install hints can reach a terminal, and
those hints name third-party release URLs (`spi/SkillspectorSecurityScanner.java:96` pins a
SkillSpector wheel). The only security gate remains `security/AgentSpecSecurityScanner`, the static
prompt-injection scan in §Security scan and lifecycle, widened to `evals.json` by
`builder-skill-gate-integrity` and, since the change that added the harness flow, over any `scripts/`
folder a build publishes — those script checks are the builder's own static ones and run on every skill
build, unlike the third-party `code-integrity` analysis named above. The inspection gate is a
writing-quality gate; nothing in it looks for injection markers, secrets or risky code on the default
path.

Five alternatives to that lineup were refused, and each refusal names a property worth keeping. The
library's original three-check set (`schema`, `quality`, `unicode`) was refused because seven checks
need no binary at all, and under-using a dependency the owner ruled must be used is its own defect.
Full `DEFAULT_CHECKS` was refused because every absent binary sets `passed = false`, so the build
machine's tool inventory would decide whether a build succeeds — and `security` would duplicate the
stage that already owns it. Enabling the binary checks while tolerating their `incomplete` verdicts was
refused because that spends a 1200-second-watchdog subprocess per skill build on a result that cannot
affect the verdict: cost with no authority. Dropping `lint` and `license` as checks that cannot fire
was refused because they cost microseconds, they report what they looked at, and one of them starts
failing the day a skill ships a script. And the opt-in is a picocli flag rather than an environment
variable in the evaluator's own idiom, because a hidden variable that changes what a build checks is
the wrong shape for a check whose absence is a stated product limit — and the same reasoning keeps the
binary checks off when the tools merely happen to be present, since otherwise two people running the
same command get different guarantees.

**The lift gate is strictly greater than the required minimum.** `--min-lift <n>` sets that minimum on
the command line. It defaults to `BuildOptions.DEFAULT_MIN_LIFT = 0.0` — so the shipped behaviour is
"any lift that beats the baseline" — and the comparison stays strict, because an *inclusive* one at
that default would publish a skill that changed nothing. The flag is accepted on an agent-spec build
and has no effect there, exactly as `--max-refine` already is.

It is a **CLI knob only**. The studio's `/api/build` pins `rest/BuildController` to
`BuildTarget.AGENT_SPEC`, and the lift gate belongs to skill builds alone, so no studio request can
reach it; that is why `BuildRequest` has no lift field and the controller passes the constant
literally. The day that pane can build a skill, this sentence stops being true and the knob becomes
worth adding.

**The scan covers both documents the skill publishes from model output.** The static security scan
vetted by §Security scan below runs over the `SKILL.md` *and* the `evals.json`: both are model-authored
text and both are written into the published folder, so an injection marker sitting in an eval
expectation blocks the build at the `security` stage exactly as one in the body does, and nothing is
written. The `lifecycle.yml` sidecar is not scanned — the builder generated it from a content hash and
a pass rate, so scanning it would add noise, not coverage.

**A skill may carry scripts, and the builder scans them without running them.** `skill/Skill` has an
optional script list, `publish/SkillPublishable` writes it to a `scripts/` folder in the published
directory, and `prompts/generate_skill.jinja` carries the authoring instructions that decide when to
emit one — so the generation prompt that a rewrite may legitimately edit is also the text choosing what
code a reader ends up running. Two things bound that. The static scan covers it: stage 7 calls
`security/AgentSpecSecurityScanner.scanSkill(name, texts, scriptsByPath)`, whose script half reads the
sandbox's own promise backwards and refuses a script reaching for the host (`require`, `Java.type`),
for the network (`fetch`, `XMLHttpRequest`, `WebSocket`) or for a path above the skill's own folder
(`../`), each under its own CRITICAL code — `SCRIPT_HOST_ACCESS`, `SCRIPT_NETWORK`,
`SCRIPT_PATH_ESCAPE` — with the evidence naming the file, visited in path order so two identical builds
print the same report. A scriptless skill scans to exactly the report the text-only call produced. A
second static gate sits beside it for the same reason: `skill/SkillFileReferences.missing(skill)`, called
just after the scan, compares the paths the body itself claims against the files the publisher is about to
write — `SKILL.md`, `evals.json`, the `lifecycle.yml` sidecar and the skill's own scripts — and blocks at
stage `references`, naming the path and the consequence. It asks Embabel's own
`skills.support.InstructionFileReferenceExtractor`, the object the reader's loader validates with, so
markdown link targets count and paths inside fenced code blocks do not: a body that *teaches* a path is
not a body that claims one. The refusal matters because the reader's alternative is silent —
`DefaultDirectorySkillDefinitionLoader` validates references as it loads and drops the **whole skill** on
a missing one, logging the reason where the builder cannot see it, which is the one failure shape a green
build report could otherwise hide.
Execution is a separate, mostly dormant surface: `skill/script/GraalJsSkillScriptExecutionEngine`
implements Embabel's `SkillScriptExecutionEngine` seam with an embedded GraalJS (host access denied,
files confined to the skill folder, statement and time limits), published as a bean only under
`smith.builder.skill-scripts.enabled`, **off by default**. Two callers of that seam exist in
`prod/` main. The build-internal one is `skill/SkillScriptHost`: it writes the skill *under build* out as
an Agent Skill folder, loads it through `Skills` with the engine installed, and `skill/EvalRunner` hands it
to the with-skill arm — which is why the arms are prompt substitution on an ordinary build and why the
lift numbers taken with the switch off still measure what they measured. Measured with the switch **on**,
the attachment is real and the difference it made was negative: on one live build the reader's loader had
refused the folder the host wrote, all 27 calls the arm made came back `Skill not found`, and the run
reported `skill lift: -0.125 (with 0.688 vs baseline 0.813)` — two lost checks out of sixteen, from the
refused round-trips rather than from anything the skill said. So a switch-on lift against a folder the
loader refused measures the refusals, not the skill. The second caller reads what was
*published* rather than what is under build: `skill/PublishedSkillHost` points
Embabel's own `withLocalSkills` at the directory `publish` wrote — the real folder, not a
re-derived copy of it — installs the engine only when the launcher's bean provider offered one,
and reports which folders loaded and which the loader refused instead of leaving that to the
loader's log. `cli/SkillRunCommand` (`smith skill-run --skill-dir <dir> <request>`) is the route
that attaches it to a model call, so a published script does execute in `prod/`.

What that closes is the reader's seam for that one route, not the claim that every reader has
one. The A2A endpoint still derives its skill list from a spec source —
`a2a-starter/SpecSourceSkills` publishes `io.a2a.spec.AgentSkill` card entries, which describe a
capability rather than load a folder — and a `smith run` still runs a pack without being offered
a published skill's tools. On an ordinary build, with the switch off, a published script is
still a file nothing executes. See [`12-backlog.md`](12-backlog.md) for the readers that remain
open, and for why the scan sits at stage 7 rather than earlier.

**The switch has a CLI route, and a scripted skill now publishes at a tier that says so.**
Neither `smith build` nor `smith skill-run` takes a flag for the engine — it is a JVM property,
and `examples/smith-cli.sh` passes `SMITH_JAVA_OPTS` ahead of `-cp`, so
`SMITH_JAVA_OPTS=-Dsmith.builder.skill-scripts.enabled=true examples/generate-skill.sh X` is the
documented way to turn it on for an example run. The ambient `JAVA_TOOL_OPTIONS` reaches the
builder too, and is what the earlier switch-on measurements used; it simply cannot be seen in the
script or from `--help`. On the tier: `security/AgentSpecSecurityScanner.scanSkill` used to
report `T1_COMMUNITY` for every skill, executable or not, and now reports `T3_PARTNER` for one
that ships JavaScript, with the scriptless skill as the case that still reads `T1_COMMUNITY`. A
skill that publishes code asks its reader to run it, and the published tier is the builder's
claim about that. It stays a report rather than a gate: a tier admits a source by the tool-group
roles its packs declare, a script declares none, so no loader consults a tier about a script and
the pack-side `minimumTier(AgentSpec)` table is untouched.

**Every drafted scenario must carry an expectation.** A scenario with an empty expectation list
contributes 0/0 to both arms and cannot move the lift: a suite of them blocks with "skill lift +0.000"
indistinguishable from a genuinely useless skill, and a mixed suite gates silently on the scenarios
that do measure something. Such a suite is refused at the `eval` stage, naming the offending scenario
ids, *before* either arm runs — two model calls (draft the skill, draft the suite) instead of four
more per empty scenario. They are refused rather than dropped: dropping is precisely what makes a
build gate on a subset nobody chose.

This is a deliberate, documented departure from the spec path's deterministic gate: a relative lift
judged by an LLM is a softer instrument, so the per-scenario report is published alongside the
verdict to make a blocked build inspectable. Both eval arms go through the builder's `Ai` —
re-proving transport independence was POC 25's purpose, not the product's.

### Process output — not a target

A build generates **both** candidates — a single-agent spec and a multi-agent process — stores both,
and publishes only the recommended one. `core/CandidateRecommender` prefers the valid candidate with
the higher score; a tie or an invalid process recommends the single-agent spec.

`common/validation/AgentsPackValidator` checks hand-offs: input-producer coverage, goal uniqueness,
and cycles. `core/AgentsPackRepair` attempts a bounded reorder repair; a cycle that survives it blocks
*that candidate* — named in the report — while the build continues on the single-agent spec. Repair
is reorder-only, never branch restructuring.

**An unsound agents-pack is never stored**, because a stored one is something someone could ingest.
Agents-pack evaluation is reimplemented in the builder (the invoker generalized to an agents-pack)
rather than depending on the runtime module.

**An agents-pack reaches the skill pipeline through one method and nothing else.**
`skill/SkillBuilderService.buildFromAgentsPack(AgentsPack, …)` walks the pack's member agents and
calls the ordinary `build(…)` once per stage, handing it a description assembled from that stage's own
steps — its name, each step's description, the types it consumes and the type it produces — so two
distinct stages are given two distinct briefs and no caller has to restate the decomposition as prose.
The gates are the existing ones: frontmatter, inspection, eval and lift all run through `build`, and
the seam deliberately adds none of its own, so a stage of an agents-pack is gated exactly as a hand-written
description would be. The first stage a gate refuses stops the run and *is* the reported result,
because a process with an unpublished stage is not a built process; a process declaring no stages is
blocked at a named `decompose` stage rather than publishing nothing silently. The seam lives in the
builder, so a caller needs the builder and nothing that binds a port or opens a database. What is not
done is a caller: its only invocations are in its own offline test, so no CLI flag, studio route or
`smith skill-run` asks for a process-to-skill build today
([`12-backlog.md`](12-backlog.md) carries that half as open).

## CLI

```
smith build ("<description>" | --docs <path>) --out <folder:…|git:…>
            [--extract] [--target agent-spec|skill]
            [--technique single-shot|embabel] [--flow java|pack|agentic|harness] [--max-refine <n>]
            [--min-lift <n>] [--max-inspect <n>] [--full-inspection] [--overwrite] [--push]

smith blueprint (--repo <git-url> | --zip <archive> | --path <dir>) --out <dir>
                [--subdir <directory-in-the-repo>] [--overwrite]
```

Exactly one of `<description>` and `--docs` is required; naming neither, or naming both, is a usage
error. `--extract` is a modifier on `--docs`: without it the assembled corpus goes to the build as
the description, with it the discovery prompt runs first and the build gets the extracted business-
process description. Extracting costs a model call, which is why it is opt-in.

`smith blueprint` is the other command, and it ends in **documents** rather than a published
capability: it reconstructs the business process a repository implements and writes the seven
discovery deliverables plus an index, which is exactly the input `--docs` reads. It shares nothing
with the build pipeline — no eval, no refinement, no publish — because an artifact that is read by a
human has no gate to belong to. Its one check is completeness: an answer that carried four of the
seven deliverables writes those four, names the three it missed, and exits 1. The stages live in
`blueprint/BlueprintGenerator` (fetch → corpus → extract → split, where a fetch is a shallow clone or an
archive extraction), and `cli/BlueprintCommand` holds
no more logic than `BuildCommand` does.

`cli/SmithBuilderCommand` is the root (`smith`); `cli/BuildCommand` is `build` and
`cli/BlueprintCommand` is `blueprint`. As in the runtime,
the CLI wraps `BuilderService` and holds no build logic — what it does hold is the choice of input and
of driver, plus one refusal it makes before anything is spent: it composes the ingestion pre-stages
above into the one description the build takes, and `--flow` below decides who issues the generation
call.

### As a native binary

`make native` produces `prod/embabel-smith-builder/target/smith`: the same CLI, AOT-processed and
compiled `--no-fallback`, so it starts without a JVM and needs no runtime on the machine.
`scripts/native.sh` is the same command with a GraalVM JDK (with `native-image`) selected through sdkman at
the patch `native-image.yml` pins as `GRAAL_VERSION` — 25.0.2, the patch the polyglot artifacts are pinned
to — falling back to the rolling `25-graalce` with a printed note when that candidate is not installed
locally, because asking sdkman for a version it lacks hangs a build script with no network. The wiring is the `native` profile of the module's pom —
`spring-boot-maven-plugin:process-aot` at `prepare-package`, its output folded into
`target/classes`, then `native-image` at `package`. Other modules are untouched by `-Pnative`
until they declare the same two plugins, so the profile is opt-in per shipped module.

What static analysis cannot see is carried in two directories under
`src/main/resources/META-INF/native-image/com.embabel.smith/`. `builder-native-hints/` is
hand-written and small: the classpath resources Embabel opens by name (provider model catalogs, the
bundled prompts) and the `@ConfigurationProperties` types it binds through Kotlin reflection.
`builder-traced-hints/` is generated, and `scripts/native-hints.sh` regenerates it by running the CLI
and the offline suite under the GraalVM tracing agent. A binary that dies with
`MissingReflectionRegistrationError` names the entry to add, so the pair grows by failure rather
than by guesswork.

**That mechanism had one open instance, now closed, and it predated the agentic work.** Measured 2026-11-23 on a
`-Pnative` build: the binary boots its AOT context in 0.15 s and prints `Usage: smith`, and
`smith blueprint --help` parses, but **`smith build` does not** — every invocation of it dies naming
`com.embabel.smith.builder.cli.BuildCommand.flow`, in picocli's `ArgSpec.applyInitialValue`, which reads
*every* `@Option` field of a subcommand whether or not the caller passed it. The entry the error asks for is
the field beside the converter the hand-written file already registers —
`{"type": "com.embabel.smith.builder.cli.BuildCommand", "fields": [{"name": "flow"}]}` — and that is what
landed in `ab28a90`, proved on a rebuilt binary: `smith build --help` prints the banner with `--flow` listing
java, pack and agentic, and `smith build --flow bogus` fails as a *usage* error out of `FlowConverter`
rather than a reflection error. The fourth member added since needs no entry of its own — it is a value of
the same enum, reached by `BuildFlow.parse` through the field and converter that file already registers, not
by a new reflective site — but no binary has been rebuilt to prove that since `harness` landed, so the
standing check is unchanged: invoke a real subcommand. It is not a
regression from the tool surface: `flow` is absent from both hint files at `poc/iteration-3`'s head and at
the base commit of that change, which touches neither file, and a binary built from those base CLI sources
fails the same way. `builder-traced-hints` lists the other eleven `BuildCommand` option fields because the
tracing run that produced it predates `--flow`. Worth knowing for its own sake: `native-image.yml`'s smoke
step runs the binary with **no** subcommand and greps for the usage banner, and the root path never builds a
subcommand's `ArgSpec`s, so a green native CI is not evidence that `build` works. Proving the binary means
invoking a real subcommand.

`smith skill-run` is the second instance of that rule, and it took three rebuilt images to settle. The
route needed `skills.spec.SkillDefinition` and `SkillMetadata`, the deserialization target the loader hands
to Jackson; then `com.embabel.agent.skills.Skills` itself, because `PromptRunner.withReference` calls
`Skills.tools()`, which hands the reference to `MethodToolFactory.fromInstance` and that enumerates its
declared members — with only the first entry the binary loads the folder and dies one frame later; then
`SkillRunCommand`'s two option fields, without which `addSubcommand` still registers the subcommand and the
image answers `Unknown options: --skill-dir`, since picocli's view of a command's shape is reflective. A
binary with all three loads a published folder, answers the request and logs no `Unresolved class` line.
The measurement and its two qualifiers — the GraalVM patch it ran on, and that the image holds no engine, so
a *callable* script tool inside a binary is still unmeasured — are in
[`12-backlog.md`](12-backlog.md) §Builder — skill scripts.

## Verifying it against a real model

Building against a live endpoint is a run you start by hand, not a test phase. The scripts are in
`examples/` and they drive the product CLI, so a green run is evidence about the path a user takes
rather than about a test double of it.

| Script | Does | Leaves |
| --- | --- | --- |
| `generate-blueprint.sh <codebase>` | `smith blueprint --repo\|--zip\|--path …` | `examples/blueprints/<name>/` and `examples/out/<name>/blueprint/` |
| `generate-spec.sh <blueprint>` | `smith build --docs … --target agent-spec` | `examples/out/<blueprint>/agent-spec-<technique>/` |
| `generate-skill.sh <blueprint>` | `smith build --docs … --target skill` | `examples/out/<blueprint>/skill/` |
| `smith-cli.sh` | the shared "how to start the CLI" bootstrap the three source | — |
| `parity-agentic.sh [blueprint…]` | the same build on a pack flow — `SMITH_PARITY_FLOW` picks `agentic` (default) or `harness` — over the three inputs the parity record used (`parcel-claims`, `Sentinel`, `TradingAgents`) | `examples/out/<blueprint>/<flow>/`, plus a `built N of 3` line against the java arm's 2-of-3 bar |

**Cost is why this is manual.** A matrix once lived in the builder's test sources as three
parameterised JUnit suites — nine cells (three blueprints × two techniques × the skill target),
about two hundred model calls and fifty minutes of endpoint time, every cell ending blocked or
errored because the gate stops or the endpoint 500s. A test phase that cannot go green for a
reason in the code, and charges a GPU to find that out, gets skipped until it verifies nothing.
Each script states its own cost in its header instead — the skill path is ~15–19 calls per
blueprint because the eval runner spends four calls per scenario — so the decision to pay stays
with the person running it.

**What the agentic arm measured on 2026-11-23 is one phrase: not reachable on this endpoint.** Six
attempts were made against `http://spark.local:4000/v1` / `Qwen-instruct` and not one printed a verdict
line. The complete clean arm (`run started 2026-11-23T13:03:42Z at HEAD a45c549`, 2700 s per build) came
back `exit 124` on all three inputs — `answered=1 spent=94 offered=43` for `parcel-claims`, `answered=0
spent=94 offered=42` for `Sentinel` and for `TradingAgents` — with footer `built 0 of 3 against a bar of
2/3`. `TradingAgents` was then given the ninety-minute budget twice, five hours apart: 15:59:21→16:52:26
and 21:45:35→22:38:39, each printing `exit 1, answered=0 spent=116 steps-offered-tools=50` with 386 and
440 tool calls under the corrected pattern. Neither was cut off. Both stopped themselves at about 53
minutes on `RuntimeException: LLM call for interaction action:surveyBlueprint timed out after 60,000ms`,
`Caused by: java.util.concurrent.TimeoutException`, out of `core/BuilderAgent.compileFromDescription`
(`BuilderAgent.java:121`, via `BuilderService.build` at `BuilderService.java:134`) — so no value of
`SMITH_PARITY_TIMEOUT` is the binding constraint, and the two runs agree to the counter across a window
in which the endpoint's throughput moved tenfold. Every one of those `report.md` files begins `NO
REPORT: the CLI printed no verdict line, so nothing was built or blocked at a gate.`

So the script's own conclusion — "Output parity does not hold on this run" — reads as **not measured**,
not as a measured negative. The bar counts published specs, and an arm that never printed
`BUILD OK:` or `BUILD BLOCKED at` produced nothing to count; a bar missed because no action-level call
landed says nothing about whether the route builds better or worse specs — and it was not for want of
answers. The `answered` column counts the responses Embabel logs for an action, not the per-round
responses inside a tool loop, so the same logs record 386 and 440 tool calls, each one an answered
completion that column never sees. The default stays `--flow java` for
the reason this page already gives — the java arm's recorded 2-of-3, against the agentic arm's zero
verdicts — and not because any divergence was observed.

The reason is the endpoint's shape against Embabel's 60,000 ms interaction ceiling, measured rather than
assumed: a 16-token completion took 16.7 s with `finish_reason=stop`, and a 600-token cap returned 294
tokens in 99.4 s ≈ **2.96 tok/s**, which projects 2,000 tokens to ≈676 s and 12,000 to ≈4,058 s —
whatever a spec-sized generation needs is several times the 60 s the platform allows, ten retries do not
change that, and a drafting step that first spends part of that clock reading tools has less of it. State
the caveat with the number: that probe contended with a build, and the same endpoint measured 8.5–30.7
tok/s at other hours with nothing else running (16 tokens in 1.89 s, 200 tokens in 12.22 s at 21:31Z),
while the build's own 60 s timeouts argue it degrades under a build's own load. Either reading lands in
the same place. Two facts rule out the lazy diagnosis: `/models` answers in ~20 ms and a trivial
completion returns, so the server is up — the *call shape* is what this endpoint cannot service. Under
`--flow harness` one input ran (`parcel-claims`, 19:34:56→20:20:01, `exit 124`, `answered=2 spent=92
offered=43`, 89 calls, no verdict line) and the other two never ran, by decision: the harness flow spends
strictly more calls per build than the arm above, so on an endpoint that cannot land either flow's
drafting call a per-call cost comparison is not measurable either.

**`BUILD OK` is a published-candidate assertion, so the parity bar is not soft.** The line prints for
any `Status.BUILT` (`BuildResult.java:95`) and the `published:` line follows only when the field is
non-null (`BuildResult.java:120`), so the shape worth asking about is a `BUILT` carrying no publish
result. Nothing in `prod/` constructs one: the only production caller of `BuildResult.built` is
`BuilderService.java:193`, inside the publisher's try-with-resources and *after* `publisher.publish(...)`
returned; a same-named pack raises `CollisionException` and the build reports
`BUILD BLOCKED at publish` instead, an IO failure throws rather than returns, and `Publisher` has exactly
two implementations (`publish/FolderPublisher`, `publish/GitPublisher`), each of which returns a location
or throws. The success-path tests dereference `result.published().location()` as a matter of course
(`DualCandidateBuildTest:89`, `FlowGateParityTest:148-150`, `LiveBuildIntegrationTest:66`), so a `BUILT`
without a location would fail those before it ever printed a clean report — and that is not an
inference, it is what happens: substituting `null` for the publish result at `BuilderService.java:193`
turns seven of the fifteen tests in those two classes red with
`Cannot invoke "PublishResult.location()" because the return value of "BuildResult.published()" is null`,
which is the mutation that would have made the bar soft. A recorded `built N of 3` is
therefore a *published* N of 3, and the suggestion raised during this change that the java arm's 2-of-3
bar could be satisfied by a build that published nothing is closed by that code rather than by a
measurement.

**A blueprint is a directory.** `examples/blueprints/<Name>/` holds the documents, and that is the
whole registry: a new example is a new directory, and a script takes its slug case- and
hyphen-insensitively. There is no `case.yml` and no cell matrix. `examples/out/` is gitignored —
regenerated evidence, not authored content.

**No build script passes `--extract`.** `business_process_discovery.jinja` asks for a *codebase* — "Analyse
the entire codebase", "Do not describe classes, functions, modules or files" — and returns the seven
discovery deliverables. A blueprint already *is* that output (`blueprints/TradingAgents/` is a copy
of hand-written business documentation), so extraction over it re-summarises business prose into
less detailed business prose and charges a full-context call for it. `--extract` remains a modifier
on `--docs`, and `--docs` reads `.md`/`.markdown`/`.txt` only — so what belongs behind it is a
repository's documentation, never a blueprint.

**The script that does run the discovery prompt is `generate-blueprint.sh`, over a repository.**
That is the job the prompt was written for, and the last gap in the ingestion story is closed:
`smith blueprint` fetches the codebase — a shallow clone, an archive extraction, or a checkout read
where it sits — reads its source *and* documents into a bounded corpus, and writes the seven
deliverables the other two scripts consume. One call, and the largest prompt and longest completion
the builder sends — which is why it is a hand-started script too, and why the count of files the cap
left out is part of both its report and the index it writes. The fetch is the part an example does not
have to pay for twice: a repository download yields the same corpus as the clone (measured identical on
the example repository), so iterating on an example means pointing the script at an archive.

`make test-live` still runs the endpoint-gated integration tests that carry `@Tag("live")`; the
examples are not part of it. The cheap live test this page used to call "worth adding next" is now
`LiveExampleBuildTest`: one hand-written 1.5 KB blueprint (`src/test/resources/blueprints/parcel-claims/`)
built through both targets, asserting the shape the document dictates rather than the text the model
chose. Its one content assertion is the deliverable token the fixture states twice — proven to bite by
changing that constant and watching the test fail on it while still publishing. The fixture is not a
stylistic choice: the smallest real blueprint is 31 KB, and a live build over it took 12 minutes and 18
endpoint timeouts, against 39 seconds for this one.

## What the builder does *not* do

Skill editing at runtime, a skill self-improvement loop, and a process-level improvement loop are all
out of scope for their respective targets. The studio's process workspace delegates generation here
rather than growing its own generator — see [`09-studio.md`](09-studio.md).
