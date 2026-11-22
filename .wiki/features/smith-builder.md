---
title: "The smith builder product (smith build)"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/BuilderService.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/BuildOptions.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/BuildResult.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/BuilderAgent.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/AgentSpecGenerator.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/AgentSpecGenerators.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/BuildTarget.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/GenerationTechnique.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/AiSpecPromptCaller.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/eval/AiPromptRewriter.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/cli/BuildCommand.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/cli/BuildFlow.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/cli/SmithBuilderCommand.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/cli/BlueprintCommand.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/blueprint/ProcessExtractor.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/config/StudioConfiguration.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/SmithBuilderApplication.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/eval/RefinementLoop.java"
  - "prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/NoDirectModelRequestTest.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/security/AgentSpecSecurityScanner.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/tools/ValidationToolGroup.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/eval/BuilderPackTools.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/eval/PackDrivenAgentSpecGenerator.java"
  - "prod/embabel-smith-builder/builder-agent-specs/builder-spec-one-shot/steps/draftAgentSpec.yml"
  - "prod/embabel-smith-builder/builder-agent-specs/builder-spec-one-shot/types/AgentSpecJson.yml"
  - "prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/FlowGateParityTest.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/publish/Publishable.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/publish/Publisher.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/publish/GitPublisher.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/improve/ImprovementLoop.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/improve/GateChain.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/improve/Refiner.java"
  - "prod/embabel-smith-builder/README.md"
updated_at: "2026-11-22"
---

# The smith builder product (`smith build`)

`prod/embabel-smith-builder` is the product's builder: it takes a natural-language description and
produces a **published, validated** artifact through a deterministic pipeline. It extracts the proven
POC logic — POC 2/3 (build), POC 5 (eval), POC 9 (refine), POC 21 (security), POC 18 (lifecycle) —
into one pipeline that ends in a publish to a `folder:` or `git:` source. Non-launcher packages are
plain classes (no Spring) so the studio product can reuse `BuilderService` and the stages directly. It
never depends on a `poc-*` artifact ([[product-phase]]).

Since 2026-11-21 it also **hosts the self-improvement engine** (`builder/improve/`), which moved out of
the studio; the studio drives that engine, it no longer contains it. What the engine does is described
on [[smith-studio]], and the reason for the move is that building agents and skills is the builder's
concern while the studio is a frontend.

## The CLI

```
smith build ("<description>" | --docs <dir>) --out <source-uri>
            [--extract] [--target agent-spec|skill] [--technique <name>]
            [--flow java|pack]
            [--max-refine <n>] [--min-lift <n>] [--max-inspect <n>]
            [--full-inspection] [--overwrite] [--push]
```

- `<description>` **or** `--docs <dir>` — an exclusive `ArgGroup` of multiplicity 1: exactly one of
  the natural-language description of the artifact, or a directory of business-process documents
  whose assembled corpus becomes the description. Naming neither, and naming both, is a usage error.
- `--extract` — run the `business_process_discovery` prompt over `--docs` first and build the
  business-process description it extracts rather than the documents themselves. It applies only to
  `--docs`, and naming it alone is a usage error (exit 2) — enforced in `call()`, because the pinned
  picocli jar ships no `dependsOn` validation.
- `--out` (required) — source URI to publish to: `folder:<path>` or `git:<url>[@ref]`
  ([[spec-sources]]).
- `--target` — `agent-spec` (default, an executable pack) or `skill` (a `SKILL.md` folder).
  `core/BuildTarget.parse` is **strict**, so an unknown name is a usage error. The skill target is a
  separate service; see [[skill-output-target]].
- `--technique` — `single-shot` (default) or `embabel`; see the seam below.
- `--flow` — `java` (default) or `pack`: **who issues the generation call**, not how the description
  becomes a spec. `cli/BuildFlow.parse` is strict, so an unknown name is a usage error (exit 2), and
  `--flow pack --technique embabel` is refused with exit **1** from `call()` — before the service is
  built and before any model call — because there is no pack for the Embabel NL compiler and design D4
  refuses rather than falls back. See the pack flow below.
- `--max-refine` — max refinement rounds (default **3**).
- `--min-lift` — skill lift the with/without-skill gate requires (default **0.0**).
- `--max-inspect` — redrafts the skill inspection gate is allowed to ask for (default **2**, so at most
  three drafts).
- `--full-inspection` — also request the skill checks that need an external scanner. Off by default, so
  **a default skill build does no secret, security or code-risk scanning** and says so in its report;
  see the gate in [[skill-output-target]].
- `--overwrite` — replace an existing same-named artifact instead of colliding.
- `--push` — push the git commit to the remote (default: **local commit only**).

The three skill knobs (`--min-lift`, `--max-inspect`, `--full-inspection`) are accepted on an
`agent-spec` build and unused there, exactly as `--max-refine` is accepted on a skill build and unused
there — one command surface, two gates.

There is **no `--score-gate` flag**: the gate is a `BuildOptions` field (default **1.0**) that only
programmatic callers of `BuilderService` — the studio — can set. Exit codes: **0** when published
(report to stdout), **1** when blocked (the stage and reasons to stdout), **2** on usage errors. The
model endpoint is env-driven ([[model-endpoint]]).

`smith` has one other subcommand: `blueprint`, which reconstructs the business process a repository
implements as a blueprint directory — the input `smith build --docs` reads. It reads a codebase
through exactly one of `--repo` / `--path` / `--zip`, requires `--out`, and also takes `--subdir` and
`--overwrite`; it runs no eval, refinement or publish stage, so its gate is only whether the answer
carried every deliverable. `examples/generate-blueprint.sh` is the scripted path and writes into
`examples/blueprints/`.

## The generation seam

Since commit `ec1a788`, the builder — not the studio — owns generation. `BuilderAgent` reaches the
model **only** through an injected `AgentSpecGenerator` (`builder/core/AgentSpecGenerator.java`), and
`core/GenerationTechnique` names the two implementations:

- `SINGLE_SHOT` → `SingleShotAgentSpecGenerator` — one model call emits the whole spec as JSON.
- `EMBABEL` → `EmbabelAgentSpecGenerator` — Embabel's production NL compiler.

`AgentSpecGenerators.production(ai, dataDictionary)` returns the `Map<GenerationTechnique,
AgentSpecGenerator>` both the CLI and the studio wire up, so the studio owns no generator of its own
([[studio-process-workspace]]). POC 24 is the measurement behind the default: the NL compiler cannot
coordinate shared type names across sibling agents, so **single-shot is the default and the right
choice for multi-agent work** ([[generation-approaches]]).

`--technique` is parsed **strictly**: `cli/BuildCommand.TechniqueConverter` calls
`GenerationTechnique.parse`, which throws `Unknown generation technique` for a name that is neither
`single-shot` nor `embabel`, so a typo is a usage error rather than a silent fallback. The lenient
variant, `GenerationTechnique.from()`, is not on the CLI path — the studio's generate request uses
it, where an unrecognized name silently becomes `single-shot` and changes behaviour without a
warning.

## The pack flow (`--flow pack`)

`--flow` is a second axis beside `--technique` and independent of it: it changes who issues the single
generation call. `cli/BuildCommand.generators()` is the only branch — `pack` returns a one-entry map whose
`SINGLE_SHOT` generator is `eval/PackDrivenAgentSpecGenerator`, so the `EMBABEL` entry disappears and no
Java generator is left in the build for a failed pack flow to fall back to. There is deliberately no
`smith.builder.flow` property and no `BuildOptions` field (design D3): the flag is per-invocation so a run
can be attributed to a flow. The multi-agent candidate is untouched — the flag governs the shipped
single-spec generation only.

- **Failure is a failure.** Every exit from `PackDrivenAgentSpecGenerator` that is not a draft is an
  `IllegalStateException` naming the pack flow and the reason (the pack would not load, the goal produced
  nothing, the answer was not spec JSON). Its parse is `AgentSpecNormalizer`'s, shared with the Java
  generator, so the two flows cannot diverge before a gate sees them.
- **The goal comes back as a map.** A step whose output type is one of the pack's own value types is
  answered by `PromptedActionSpecAction` as a raw property map, so `builder-spec-one-shot`'s
  `AgentSpecJson` arrives as `{json=<text>}`; `eval/AgentSpecInvokerFactory.goalOutputText` returns the
  bound string rather than the rendered wrapper. That was the crash class that made the pack flow build
  nothing on 2026-11-22 (`Unexpected character ('j' …` — the wrapper's unquoted key), and
  `eval/GoalOutputReadBackTest` holds both shapes.
- **The reply envelope is pack text.** `builder-spec-one-shot/steps/draftAgentSpec.yml` states the one-key
  reply object (`{"json": …}`) that its goal type declares (`types/AgentSpecJson.yml`, one own-property).
  That sentence is load-bearing and nothing on the validation side checks it: the platform only *tells* the
  model about the schema and validates nothing against the reply, so a prompt describing a different object
  is a competing shape instruction that surfaces only as the generator's "did not return valid agent spec
  JSON" after a paid build. `core/BuilderAgentSpecPackTest` reads the key **from the type** rather than from
  a Java literal, so renaming the property without renaming it in the prompt fails offline.
- **Measured, and still not the default.** Re-measured 2026-11-22 over three blueprints after both fixes:
  `pack` builds **1 of 3** (`parcel-claims` publishes, `Sentinel` blocks at security where the java arm also
  blocks, `TradingAgents` blocks at eval) against java **2 of 3**, with the goal-JSON crash appearing zero
  times. Parity still misses the 2-of-3 bar, so the flip's decision stands and `--flow pack` stays
  non-default. `Sentinel` spent 6 validator calls — over the ≤3 bar, because repeats on an unchanged draft
  are not deduped — and on `TradingAgents` the step was offered the validator and declined it, after which
  eval found sixteen dangling types. `FlowGateParityTest` holds the offline half: from the same answer text
  both flows run the same gates in the same order and stop at the same gate.

## The model layer

Everything in this module that talks to a model does so through Embabel's `Ai` — no class here opens
its own HTTP connection to the endpoint, and a source-level test (`NoDirectModelRequestTest`) fails
the build if one ever imports `java.net.http` ([[model-calls-go-through-ai]]). Two collaborators carry
it:

- `core/AiSpecPromptCaller` — the `SpecPromptCaller` both generation techniques run their prompts
  through. 12 000-token budget, temperature 0.2, and the system prompt sent as a system prompt
  (`PromptRunner.withSystemPrompt`), skipped when blank.
- `eval/AiPromptRewriter` — the `PromptRewriter` stage 3 refines with. Replaces the deleted
  `LlmPromptRewriter`; a blank completion still returns the original prompt unchanged.

Because of that, `BuildCommand` takes only `(platform, ai)` — it no longer receives a base URL, key
and model name, and `SmithBuilderApplication` no longer reads `OPENAI_CUSTOM_*` itself. The endpoint
env still decides whether a build can run at all; it just doesn't name the model
([[model-endpoint]]).

## The pipeline

`BuilderService.build(...)` runs the stages in order; a block at any stage stops the build and names
the stage in the report. Nothing is published when a gate fails.

1. **Compile → validate → write** the single-agent pack (`BuilderAgent`): compiled through the
   injected generator, validated for type bindings and prompt references, and — only if valid —
   written to a scratch dir and re-validated. An invalid draft is **never** written
   ([[validate-before-write]]); compiler issues (e.g. an ambiguous description) block at `build`.
2. **The second candidate**, from the same description: decompose → validate → repair a cycle →
   re-validate ([[two-candidate-build]]).
3. **Eval → bounded refine** (`RefinementLoop`) on the pack; below the gate the terminal action's
   prompt is rewritten and the loop repeats, up to `--max-refine`. A rewrite that fails validation is
   discarded ([[eval-guided-refinement]]).
4. **Score the process** (`ProcessInvokerFactory`): every pack deployed into one scope, the process's
   entry goal run against the same eval tasks.
5. **Recommend** between the candidates — and *then* apply the score gate, to the **recommended**
   candidate only: what must not ship below the gate is what the build ships.
6. **Store both** candidates (`BuilderAgent.writeProcess` refuses an invalid process), then the
   **security gate** (`AgentSpecSecurityScanner`) over the packs about to be published — a process
   contributes all of its packs. A critical finding blocks at `security` ([[spec-security-scanning]]).
7. **Lifecycle** (`LifecycleStamper`) on the recommended candidate: content hash, baseline eval
   pass-rate, initial state `incubating` (promotion to `active` is owned by the studio), and a
   timestamp, as a `lifecycle.yml` sidecar ([[spec-lifecycle]]).
8. **Publish** only the recommended candidate.

## The publish seam

`publish/Publishable` is the content-agnostic unit a publisher takes — `name()`, `baselinePassRate()`,
`writeTo(dir)` — implemented by `AgentSpecPublishable`, `ProcessPublishable` and `SkillPublishable`.
`Publisher.publish(Publishable, overwrite, push)` is the single abstract method (the old 4-arg
`AgentSpec` form is now a `default`), so every artifact shares the collision and no-push semantics
instead of carrying its own publisher.

- **`folder:<path>`** — written to `<path>/<name>/` (for a process: `process.yml` plus one directory
  per pack) with `lifecycle.yml`. A same-named artifact is a collision unless `--overwrite`.
- **`git:<url>[@ref]`** — cloned into a scratch work tree, written as a subdirectory named after the
  artifact, committed (author `Smith Builder`). `--push` pushes; without it the commit stays local.
  Re-publishing an unchanged artifact stages nothing. All git runs non-interactively
  (`GIT_TERMINAL_PROMPT=0`, `core.askpass=false`).

A success report prints `candidate:` (the recommendation's reason — the two scores, plus what stopped
the rejected candidate), `candidates:`, `eval score:`, `security:`, `lifecycle:` and `published:`; a
block names the stage, the score if there was one, and the reasons.

## Validators as tools

`tools/AgentSpecValidationTool` and `tools/ProcessValidationTool` expose the two format validators as
`@LlmTool` methods, so a drafting step can read the findings against its own draft and re-draft instead of
waiting for the numbered repair stages `BuilderService` runs. They add nothing: the pack tool hands back
`AgentSpecValidator`'s own `ValidationResult` — same findings, same codes, same offending file — and the
process tool passes through `ProcessValidator`'s findings, shared-type count, connectivity flag and any
cycle. No threshold, verdict or publish decision lives in either class; blocking stays where it was, in
`GateChain` and `AgentSpecSecurityScanner`. Only the pack validator reaches a pack today: the `validate`
group carries exactly that one tool, and `ProcessValidationTool` is carried by no registered group and
wired as a `Tool` nowhere in `prod/`.

How they reach a model is the part worth knowing. `@LlmTool` carries no tool-group member and the platform's
`@ToolGroup` annotation is referenced by no other class in the 1.5.4 jars, so a step's `toolGroups` entry
resolves only against `RegistryToolGroupResolver` — whose registry is the `ToolGroup` beans Spring can see.
`tools/ValidationToolGroup` is the builder's: a `@Bean ToolGroup` whose role is `validate`, carrying exactly
the tool `eval/BuilderPackTools.validationTool()` authors — whose schema names that tool's one argument
`specJson`, deliberately, because reflection would name it `arg0`. Its `ToolGroupMetadata` is built by
resolving the Kotlin companion factory **by signature** through `getMethods()`: a `ToolGroup` is not
*directly* authorable in Java (the accessor and companion names are hyphen-mangled, `invoke-bYD-3ao` and its
like) but it is authorable by signature, and `poc/poc-7-runtime-adapter`'s `ShellToolGroupFactory` is this
repo's pre-existing proof. That is the route the shipped flow takes, and it replaced the other one. The
scope's tool list in `eval/AgentSpecInvokerFactory` is empty, because `PromptedActionSpec.emit` selects from
it by the step's own `tools:` name list and `ActionStepSpec` has thirteen components and no `tools`
component — so a scope-wired tool is unreachable by a pack whatever the driver passes. A live run measured
that route inert: the deployed pack's step declared `toolGroups: [validate]`, the platform logged
`Could not resolve tool group with role='validate'`, and the tool loop started as `starting tool loop []`,
the validator never offered and never called. After the registration the loop starts as
`starting tool loop [validatePack]` on both routes to the same deploy. On the test-seam deploy the model
then declines it — `called=0`, goal completed in one model call; on the `--flow pack` CLI path it calls it.
What a call achieved turned on the argument, and `99f9cc0` replaced it: while the argument was a directory
the step had none to name — the draft is its own pending answer — so the counts recorded then, 2, 3 and 116
calls across three blueprint inputs with 92 of 93 results on one build the same `LAYOUT_MISSING_MANIFEST`,
measure a tool the step could not aim rather than the checks it ran. The argument is now `specJson`, the spec
the step is about to return, run through the same in-memory `AgentSpecValidator.validate(AgentSpec)` overload
`core/BuilderAgent.validateDraft` uses for the Java flow; layout is the one check dropped and
`core/BuilderAgent.writeAgentSpec` still re-validates the directory it wrote. On one `parcel-claims`
pack-flow build since the change it was called twice, each time with the model's own draft, the first answer
a real `DANGLING_TYPE_REF` and the second `No findings: the agent spec is well-formed.`, and the build
published. The three-blueprint re-measure has since landed (§The pack flow): pack builds 1 of 3, `Sentinel`
spent 6 calls on undeduped repeats, and on `TradingAgents` the step declined the offer. So reach is partly a pack declaration rather
than wholly the driver's, and a pack can gain capability by being edited — though not by a candidate
editing itself, since `improve/Refiner` copies the `toolGroups` field through untouched. The scope builder's `List<ToolGroupDescription>` argument gates none of this: nothing on the
step-spec deploy path reads the context's tool-group list, and the only reader in the jar is
`DefaultNaturalLanguageCompiler`, which belongs to the `embabel` technique. Registering the group moved no
tier: `validate` is in none of `security/TrustTier`'s allowlists, so both declaring packs still read
`T4_PLATFORM` through the `*` wildcard ([[trust-tiers]]). `tools/ValidationToolPassthroughTest` pins the
pass-through.

## The builder's own packs

`prod/embabel-smith-builder/builder-agent-specs/` holds four agent specs that describe the builder's own
flow — `builder-single-spec`, `builder-process-composition`, `builder-skill-from-process` and
`builder-spec-one-shot`. Three are named nowhere in `src/main`: they are the surface the
`builder-executes-its-own-packs` change is working towards, committed and pinned so the flip is a
wiring change rather than an authoring task. The fourth is loaded by `src/main` when
`smith build --flow pack` runs — `eval/PackDrivenAgentSpecGenerator.BUILDER_PACK` deploys
`builder-spec-one-shot` through `eval/AgentSpecInvokerFactory` instead of calling the Java generator — and
the flag defaults to `java`, so the shipped build still calls the Java generators and no candidate changes
what an ordinary `smith build` does. What those packs declare is no longer inert: `draftAgentSpec.yml` and
`builder-single-spec/steps/assembleAgentSpec.yml` each carry `toolGroups: [validate]`, and
`tools/ValidationToolGroup` registers that role, so a pack deployed on either route is *offered* the
builder's validator where it was offered nothing (see §Validators as tools). The packs are not, however, unconsumed: POC 27's outer loop reads
`builder-single-spec` off disk, appends its generated policy to one step prompt through its own
`outerloop/BuilderPacks.withStepPrompt` — the same field-for-field pass-through this module's private
`improve/Refiner.withPrompt` encodes, restated rather than called — and deploys the result through
`eval/AgentSpecInvokerFactory`, so
the packs are an improvement object now ([[what-the-pocs-prove]], `docs/04-poc-run-results.md` §7.5),
while the shipped build stays on the Java generators and no candidate changes what `smith build` does —
though a candidate built from a declaring pack is offered the validator, so reach is now partly pack text
rather than wholly the driver's — and unchanged from its parent pack, because the refiner copies the
`toolGroups` field rather than rebuilding it.
Three tests hold the ground: `core/BuilderAgentSpecPackTest`
that all four load, validate and deploy (the packs sit at the module root, off the test classpath, so the
test locates them by path), and that `builder-spec-one-shot`'s drafting step names the envelope its own goal
type declares; `skill/ScorerPromptBoundaryTest` that none of the three scorer prompts appears
anywhere in their step prompts — the mutable surface must not contain the judge; and
`core/BuilderPackTierTest` that the drafting step's declared `validate` group moves the scanner's
recommended tier off `T1_COMMUNITY`, which is a scanner result and never a reach result — registering the
group moved no tier, because `validate` is in none of the allowlists and the pack reads `T4_PLATFORM`
through T4's `*` ([[trust-tiers]], [[spec-security-scanning]]).

The pack flow added one test per seam rather than one per feature: `cli/BuildCommandTest` that the flag is
advertised, that the pack flow wires the pack alone with no `embabel` entry to fall back to, that an unknown
flow name exits 2, and that `--flow pack --technique embabel` refuses without ever constructing a build
service; `FlowGateParityTest` that both flows run the same gates in the same order and stop at the same
gate; `eval/PackDrivenAgentSpecGeneratorTest` that each failure names the flow and that a pack which will
not load never asks for a deploy; `eval/GoalOutputReadBackTest` on the goal boundary;
`tools/ValidationToolGroupTest` (role, one tool, `specJson`, and a booted context whose resolver lists
`validate`) with `eval/BuilderPackToolsTest` for the tool's own pass-through; and
`eval/DeployedBuilderPackCallsValidatePackTest`, `@Tag("live")`, where *offered* is asserted but *called* is
only recorded — whether a model reaches for the tool is not this repository's property, and the two seams
that run the pack are not evidence about each other.
`eval/CollaborationStructureIsFixedByConstructionTest` is the falsifier for anyone who later writes that the
improvement loop improves a pack's *collaboration structure*: the operator rewrites prompts and carries
`pre`/`post` and the agent order through untouched.

## Tests

Offline tests cover the `BuilderAgent` compile/validate/write, the `RefinementLoop`, the
`AgentSpecSecurityScanner` verdicts, the `LifecycleStamper`, the publishers (collision, no-push), the
dual-candidate path (`DualCandidateBuildTest`, `CandidateRecommenderTest`, `ProcessRepairTest`,
`SingleShotProcessSpecGeneratorTest`) and the model layer on mocks (`AiPromptRewriterTest`,
`AiSpecPromptCallerTest` — the latter proves the system prompt goes out separately and that a blank
one is omitted) plus the `NoDirectModelRequestTest` guard. The live build (which calls the model) is
endpoint-gated; `LiveBuildIntegrationTest` asserts the caller-visible outcome, not wire-level error
text.
