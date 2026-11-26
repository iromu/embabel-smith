# embabel-smith's own build process

A self-description, written by hand from `docs/05-architecture.md`, `docs/08-builder.md` and
`docs/10-self-improvement.md`. It describes the process the builder actually runs, in the order it
runs it, with the names the code uses. It is build input for POC 27: a model is asked to turn it
into a multi-agent process, so each stage states what it consumes, what it emits, and what stops it.

## Deliverable

The deliverable is a **published, deployable capability pack**: a versioned folder an Embabel host
ingests and runs, whose exported goal produces the output type the description names. There are
exactly two published targets, `agent-spec` and `skill`; a multi-agent process is generated and
scored on every `agent-spec` build but is published only when it is the recommended candidate. The
pack ships with a `lifecycle.yml` sidecar carrying its content hash, its baseline pass-rate and its
lifecycle state. A build that does not reach publish produces no deliverable — a blocked or
sub-gate build writes nothing publishable and says so.

## Pre-stages: ingestion (not pipeline stages)

These run before a build and each ends in a `String`. They have no gate, because there is nothing
gated yet, and they leave the build entry point's signature unchanged.

| Stage | Class | Consumes → emits | Bound |
| --- | --- | --- | --- |
| load | `blueprint/BusinessProcessDiscovery` | a folder or a shallow clone of `.md`/`.markdown` → the documents | 300K presented; clone bounded to 180 s |
| assemble | `blueprint/BlueprintLoader` | documents → one corpus, each document labelled with its path relative to the root | 200K |
| extract | `blueprint/ProcessExtractor` | a **codebase** corpus → a business-process description in plain text | the `business_process_discovery` prompt; one model call |
| corpus | `blueprint/CodebaseCorpus` | a repository's source *and* documents → one corpus | 200K, documents capped at half of it |
| archive | `blueprint/ZipArchive` | a repository archive → an extracted checkout with the forge's wrapper directory lifted | an entry that would write outside the target is refused |
| blueprint | `blueprint/BlueprintGenerator` | a repo URL, an archive, or a checkout → the seven discovery deliverables plus an index | fetch → corpus → extract → split |

The two 200K/300K caps are different numbers on purpose: 300K is what a workspace presents, 200K is
what a generation runs from. `extract` asks for a codebase and says so, so it is not run over a
blueprint — a blueprint already is its output, and re-extracting one re-summarises prose and
charges a full-context call for it. `smith blueprint` ends in documents, not a capability: it shares
no eval, no refinement and no publish with the build.

## Pipeline stages

`core/BuilderService.build()` holds this order explicitly, invoking the `@Agent`'s code-backed
actions in the sequence the planner would choose. Pipeline order is deterministic by construction;
only the emitted prompts are model-generated.

1. **generate** (the action is named `compile`) — description → `CompiledDraft` (pack name,
   `AgentSpec`, issues). Technique is `single-shot` by default, one model call, or `embabel`,
   Embabel's natural-language compiler. `single-shot` is the default on measured evidence: the
   `embabel` technique scored 0/6 valid packs against 4/6 on the same live matrix and is 0/N in
   every recorded run, because the upstream compiler reports no intermediate types for a multi-step
   agent and every undeclared output then blocks as a dangling type reference. An ambiguous
   description blocks here with the draft retained.
2. **validate** — `AgentSpec` → `ValidationResult`, deterministic, no model call. **Validate before
   write is mandatory**: the generator is model-driven and can emit wrong bindings or schemas, so the
   write stage may run only after `common/validation/AgentSpecValidator` has passed the draft. An
   invalid draft returns a blocked result and nothing is written.
3. **write** — the validated draft is written. The throwaway-platform deploy comes later, as the
   eval; the write gate is static.
4. **evaluate** — deploy the pack and run *its own exported goal* (the runtime's path), score the
   result against eval tasks. The score gate is `1.0` by default.
   - **refine** (inside evaluate) — `eval/RefinementLoop` rewrites **the terminal action's prompt**,
     the one producing the goal output, from the failures: one prompt per round, up to 3 rounds by
     default. A wrong prompt on a mid-pipeline step is never rewritten, so a pack can reach the
     bound without the failing step ever being touched. Below the gate after the bound, the build
     reports and stops and nothing is published. The pass-rate that clears the gate becomes the
     pack's recorded **baseline**.
   - **agents-pack candidate** — every `agent-spec` build also generates and scores an agents-pack.
     `common/validation/AgentsPackValidator` checks hand-offs: input-producer coverage, goal
     uniqueness, cycles. `core/AgentsPackRepair` attempts a bounded *reorder* repair, never a branch
     restructuring; a cycle that survives repair blocks that candidate, named in the report, while
     the build continues on the single-agent spec. An unsound agents-pack is never stored, because a
     stored one is one someone could ingest. `core/CandidateRecommender` prefers the valid candidate
     with the higher score; a tie or an invalid agents-pack recommends the single-agent spec,
     which is the one published.
5. **inspect** (the `skill` target only, and it runs *before* the eval is paid for) — after
   `skill/SkillMdValidator` has enforced the frontmatter (name 1–64 lowercase alphanumeric-or-hyphen,
   description ≤ 1024), `skill/SkillInspector` writes the draft to a temp folder and runs the
   `Tier1Runner` over it. Six checks block — `schema`, `version`, `quality`, `unicode`, `lint`,
   `license` — and `pii` runs and never blocks: advisory by check name, not by severity. The quality
   floor is 70. Blocking findings go back as a redraft request, at most twice by default, so at most
   three drafts; a draft that still fails is refused at stage `inspect` and nothing is published.
   Placement is the point: inspection makes no model call, so a draft under the quality floor is
   refused before the benchmark spends four calls per scenario measuring it. Exceeding the 500-line
   body ceiling is advisory, not a refusal. A default skill build runs no `security`, `secrets` or
   `code-integrity` check — those shell out to third-party binaries and are requested only by an
   explicit `--full-inspection`; the build report prints that limit as its own line.
   - **skill eval** — a drafted `evals.json` suite is run against a general-purpose agent *with* the
     skill and *without* it, and the gate is the lift: strictly greater than the required minimum
     (`0.0` by default, so "any lift that beats the baseline"). An inclusive comparison at that
     default would publish a skill that changed nothing. Every drafted scenario must carry an
     expectation: a suite with an empty-expectation scenario is refused at the `eval` stage naming
     the offending ids *before* either arm runs, because dropping them would gate on a subset nobody
     chose.
6. **security scan** — `security/AgentSpecSecurityScanner`, the static install-time scan. The scale
   is `INFO` / `WARN` / `CRITICAL`; there is no `HIGH`. A `CRITICAL` finding blocks publication;
   `WARN` findings force review without blocking. On the skill target it runs over `SKILL.md` *and*
   `evals.json` — both are model-authored text written into the published folder — and not over the
   generated `lifecycle.yml`. It audits only what the spec declares, so a novel rephrasing outside
   its pattern set can slip through.
7. **lifecycle stamp** — `lifecycle/LifecycleStamper` writes the `lifecycle.yml` sidecar: content
   hash, baseline pass-rate, state `incubating`. A sidecar rather than a manifest field because the
   manifest format is owned elsewhere and shared with the frozen POCs.
8. **publish** — write the pack through the source's publisher. **Publishing commits; it never
   pushes.** Pushing is a shared-state action needing credentials and intent and takes an explicit
   `--push`; a name collision needs an explicit `--overwrite`. A `git:` source with pushing off is
   refused rather than published, because the commit would be made in a temporary clone that is
   then deleted; the candidate stays in the approval queue and the iteration records that it
   published nothing.

The gate order inside the improvement loop follows the same rule as inside the builder: **cheap
deterministic checks before model calls**, so structural validation and the static security scan run
first and a broken or unsafe candidate never spends eval budget. The baseline comparison comes last
because it needs the re-eval score.

## The loop that improves a published pack

Traces are its only evidence source: every run — success, tier block, lifecycle block, goal not
found — appends one JSONL record naming its source, pack content hash, goal, planner, trust tier,
executed steps, and failure reason.

- **detect** — three triggers: failed traces that are *not* policy blocks, a re-evaluated pass-rate
  below the recorded baseline, and content-hash drift from the sidecar. A trust-tier block and a
  lifecycle block are excluded on purpose: both are policy outcomes a prompt rewrite cannot fix, so
  refining against them is noise, and the detector filters on the failure reason's message prefix.
- **refine** — rewrite only the steps implicated by the traces, using the trace evidence as input.
  Not a whole-pack regeneration.
- **gate** — validation, static security scan, re-evaluation, then the baseline comparison. **"Beat
  the baseline" is strict (`>`)**: equal is not progress, and accepting equal would let noise churn
  the source. The stated consequence: a pack already at 1.0 can only improve by fixing behaviour the
  eval does not cover and in practice is never queued at all — no failed traces, no drift. A
  documented failure-fix fallback covers that ceiling case.
- **publish** — reuse the builder's publishers and move the lifecycle state through
  `incubating → active → degraded → retired`. `retired` is excluded from automatic refinement and
  enforced at ingest; a human revives it with an explicit rebuild. `incubating` and `degraded` are
  advisory.
- **human control** — candidates hold for approval by default; auto-publish is off; pushing is off
  and is not runtime-toggleable. A rejected candidate is never published.

## Invariants this process preserves

- Validate before write. A generated spec is never written unless the deterministic validator passes
  it.
- The static security scan gates publication: `CRITICAL` blocks, `WARN` forces review.
- Trust tiers are recorded in the trace, so a policy block is never mistaken for a bad spec.
- Publishing commits and never pushes by default.
- Every run emits a trace record.
- Every model call under `prod/` goes through Embabel's `Ai` layer; no production class opens its own
  HTTP connection to a model endpoint, and no endpoint is ever hardcoded — base URL, key and model
  list come from the environment.
- POC modules are frozen evidence; product logic is copied out of them and generalised, never depended
  on.
