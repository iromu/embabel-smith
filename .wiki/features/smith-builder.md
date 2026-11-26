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
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/cli/SkillRunCommand.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/PublishedSkillHost.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/blueprint/ProcessExtractor.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/config/StudioConfiguration.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/SmithBuilderApplication.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/eval/RefinementLoop.java"
  - "prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/NoDirectModelRequestTest.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/security/AgentSpecSecurityScanner.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/tools/ValidationToolGroup.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/eval/BuilderPackTools.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/eval/PackDrivenAgentSpecGenerator.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/BuilderPackPaths.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/improve/BuilderPackSurface.java"
  - "smith-common/src/main/java/com/embabel/smith/common/io/SpecFolders.java"
  - "prod/embabel-smith-builder/src/main/resources/specs/builder-spec-one-shot/steps/draftAgentSpec.yml"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/eval/AgenticAgentSpecGenerator.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/tools/ToolSurface.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/tools/ToolGroups.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/tools/BlueprintToolGroup.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/tools/PackLibraryToolGroup.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/tools/SecurityScanToolGroup.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/tools/TraceToolGroup.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/tools/AgentsPackValidationToolGroup.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/tools/AgentsPackValidationTool.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/tools/ToolArguments.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/tools/ToolOutput.java"
  - "docs/12-backlog.md"
  - "prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/tools/ExposedToolGroupsTest.java"
  - "prod/embabel-smith-builder/src/main/resources/specs/builder-agentic-blueprint/steps/draftAgentSpecFromBlueprint.yml"
  - "prod/embabel-smith-builder/src/main/resources/specs/builder-spec-one-shot/types/AgentSpecJson.yml"
  - "prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/FlowGateParityTest.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/eval/HarnessAgentSpecGenerator.java"
  - "prod/embabel-smith-builder/src/main/resources/specs/builder-harness/steps/draftAgentSpecFromRoots.yml"
  - "prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/eval/HarnessAgentSpecGeneratorTest.java"
  - "prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/tools/HarnessDocumentCursorTest.java"
  - "prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/BuildResultFlowTest.java"
  - "examples/parity-agentic.sh"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/publish/Publishable.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/publish/SkillPublishable.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/Skill.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/script/GraalJsSkillScriptExecutionEngine.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/script/SkillScriptEngineConfiguration.java"
  - "prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/skill/script/SkillScriptEngineBeanTest.java"
  - "prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/skill/script/SkillScriptToolSurfaceTest.java"
  - "prod/embabel-smith-builder/src/main/resources/prompts/generate_skill.jinja"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/publish/Publisher.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/publish/GitPublisher.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/improve/ImprovementLoop.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/improve/GateChain.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/improve/Refiner.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/improve/LoopPublishPolicy.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/publish/PackVersion.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/tools/AgentSpecValidationTool.java"
  - "prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/tools/ValidationToolNormalizationTest.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/store/ProcessStore.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillBuildResult.java"
  - "docs/08-builder.md"
  - "docs/06-spec-sources.md"
  - "docs/10-self-improvement.md"
  - "prod/embabel-smith-builder/README.md"
updated_at: "2026-11-26"
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
            [--flow java|pack|agentic|harness]
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
- `--flow` — `java` (default), `pack`, `agentic` or `harness`: **who issues the generation call**, not how the
  description becomes a spec. `cli/BuildFlow.parse` is strict, so an unknown name is a usage error (exit 2),
  and a pack flow (`pack`, `agentic` or `harness`) combined with `--technique embabel` is refused with exit
  **1** from `call()` — before the service is built and before any model call — because there is no pack for
  the Embabel NL compiler and `docs/08-builder.md` §Who issues the generation call states that nothing falls
  back. The flow is also the one thing the
  CLI adds to the result it prints: `BuildCommand` stamps it with `BuildResult.withFlow` and `report()`
  prints a `flow:` line, so a run is attributable from its output and not only from what was typed. That
  attribution is **agent-spec only**: the stamp happens on the `buildAgentSpec` branch
  (`BuildCommand.java:365`), `skill/SkillBuildResult` (`:37`) has no flow field, and the skill branch
  (`cli/BuildCommand.java#buildSkill`) never consults `generators()`, so `--flow harness --target skill` parses, runs no pack and
  prints no `flow:` line — a skill run is attributable from its output on any flow exactly as on none.
  See the pack flow, the agentic flow and the harness flow below.
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

`smith` has two other subcommands. `blueprint` reconstructs the business process a repository
implements as a blueprint directory — the input `smith build --docs` reads. It reads a codebase
through exactly one of `--repo` / `--path` / `--zip`, requires `--out`, and also takes `--subdir` and
`--overwrite`; it runs no eval, refinement or publish stage, so its gate is only whether the answer
carried every deliverable. `examples/generate-blueprint.sh` is the scripted path and writes into
`examples/blueprints/`. The other is `skill-run`: `smith skill-run --skill-dir <dir> <request>` loads a
published skill folder and answers the request with it attached, which makes it the one route in `prod/`
where a published skill's script tool can be offered at all (§Skill scripts). It prints what loaded and
names each folder the loader refused with the reason, exits **1** with no model call when nothing loaded,
and takes no flag for the engine — that is the same JVM property the build reads, resolved once for both
subcommands at `SmithBuilderApplication.java:64`.

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
`smith.builder.flow` property and no `BuildOptions` field — `docs/08-builder.md` §Who issues the generation
call states that nothing sits behind the flag, and it is kept by the two flows that came after: the flag is
per-invocation, and what makes a run attributable is that `BuildCommand` stamps the
flow onto the `BuildResult` it got back (`withFlow`) and `report()` prints a `flow:` line under its
header — so a parity or cost run can be read from what it printed rather than from how it was launched (the
skill target stamps nothing, so this holds for an `agent-spec` build only — see `--flow` above).
That line is now stated as fact in `docs/08-builder.md` §Who issues the generation call rather than in a
planning tree: it landed with the harness flow, and `BuildResultFlowTest` plus the report cases in
`cli/BuildCommandTest` hold it. A build driven from outside the CLI — the studio's `/api/build` — stamps nothing and prints no
flow line rather than guessing `java`.

The multi-agent candidate is untouched — the flag governs the shipped single-spec generation only, and
`cli/BuildCommand.newService` wires `SingleShotAgentsPackGenerator` unconditionally
(`BuildCommand.java:172`), so under `pack` the process candidate is still generated Java-side and still
spends Java-issued model calls.

- **The pack address is the class loader, with two named fallbacks.** `eval/PackDrivenAgentSpecGenerator`
  answers its pack from `builderPack()` (`eval/PackDrivenAgentSpecGenerator.java#builderPack`) —
  the `specs/` classpath resource, resolved over `smith-common`'s `SpecFolders.classpath`, which hands back
  the class directory itself when classes are exploded and a temporary extraction when they sit in a jar
  (`core/BuilderPackPaths.java`). When the class loader cannot *list* the location it falls through, in
  order, to `smith.specs.dir` / `SMITH_SPECS_DIR` and then to a `specs/` folder beside the running
  executable, and a fallback that answers after the loader had named the location logs a warning — the only
  case in which a run can read a pack other than the one that shipped. The pack is resolved when the flow
  needs it rather than at class load: the generators expose a method, and `cli/BuildCommand` resolves it
  inside the handler that prints a message and exits 1 (`BuildCommand.packOf`), so an unresolvable pack is a
  build failure naming every route it tried rather than an `ExceptionInInitializerError` from a static
  initializer. The packs are still the mutable surface a loop rewrites; what changed is that their
  separation from the referee is by directory rather than by being off the classpath, so the directory a
  build starts in decides nothing. The failure this address used to carry — `--flow pack` exiting 1 from the
  repository root, before any model call, on a pack path resolved against the working directory — has no
  route left, and the move that closed it is the one recorded in `docs/12-backlog.md`.

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
  non-default. `Sentinel` spent 6 validator calls — over the ≤3 bar, because at the time nothing deduped a
  repeat on an unchanged draft (every tool body now goes through `tools/ToolSurface#remembered`, the
  validator's included, so that run is a before-picture) — and on `TradingAgents` the step was offered the
  validator and declined it, after which eval found sixteen dangling types. `FlowGateParityTest` holds the
  offline half: from the same answer text both flows run the same gates in the same order and stop at the
  same gate.

## The agentic flow (`--flow agentic`)

The third value of the same flag, and the one where the builder's own capabilities are tools rather than
Java stages. `cli/BuildCommand.generators()` gains one branch; `eval/AgenticAgentSpecGenerator`'s own
`builderPack()` (`eval/AgenticAgentSpecGenerator.java#builderPack`) deploys
`specs/builder-agentic-blueprint`, whose two steps are the surveying act
(`surveyBlueprint`, declaring `blueprint` and `library`) and the drafting act
(`draftAgentSpecFromBlueprint`, declaring `blueprint`, `validate` and `security`). `--flow java` remains
the default and `--flow pack`'s semantics are untouched.

- **What it can reach is a closed list, and the list is a tested fact.** A booted builder resolves exactly
  seven roles (`tools/ExposedToolGroupsTest`: `validate`, `blueprint`, `library`, `security`, `traces`,
  `process`, `math` as literals (`:91`), plus an absence assertion over
  `file shell browser_automation web maps github search osx agent` whose control is
  `contains("validate", "math")` (`:106`) — and `math` is the load half of that control, because it is the
  one role upstream registers and this repo never wrote, so an assertion that finds it cannot be reading an
  empty registry). That is worth the sentence it costs: `embabel-agent-api-1.5.4.jar` carries 62 upstream tool
  classes, including `com/embabel/agent/tools/file/` and `com/embabel/agent/tools/process/`, and no bean
  publishes them — presence on the classpath is not reach, and the exact-set assertion is what keeps it so.
  `blueprint`, `library`, `validate` and `security` are declared by a shipped step; `traces` and `process`
  are registered and await a step that names them. The six are the whole of the module's contribution: six
  `ToolGroup` `@Bean` methods sit in `src/main/java/com/embabel/smith/builder/tools/` — `documentTools`
  (`BlueprintToolGroup.java:77`), `libraryTools` (`PackLibraryToolGroup.java:75`), `processChecks`
  (`AgentsPackValidationToolGroup.java:51`), `securityScanTools` (`SecurityScanToolGroup.java:63`),
  `traceTools` (`TraceToolGroup.java:69`) and `validateToolGroup` (`ValidationToolGroup.java:82`) — and
  each is named for what it publishes rather than after its own class, because Spring's default bean name
  for the configuration class already occupies that slot (`ToolGroups.java:59-65`).
- **The driver chooses what is read.** `tools/ToolSurface` is a mutable bean holding the roots the command
  line was given; `read_doc` refuses a name outside them and a non-markdown file, `list_packs`/`rank_packs`
  are built over a `FolderSpecSource` so a `git:` source is excluded by construction rather than by a
  check, and the trace tools answer "no trace store is wired into this build" rather than inventing
  `~/.smith/traces` — the module has no runtime dependency, so there is no store to reach. The failure this
  replaces is measured: one build spent 116 `validatePack` calls guessing directories, 92 of 93 results the
  same `LAYOUT_MISSING_MANIFEST`.
- **Capped, and deduped.** Every body ends in `ToolOutput.cap` (20 000 characters,
  `tools/ToolOutput.java:37`, with the cap stated in the tool's own description and a capped `read_doc`
  naming the offset to ask for next). The `blueprint` group is three tools rather than two — `list_docs`,
  `read_doc` and `assemble_corpus` (`BlueprintToolGroup.java:51,54,57`, all three in the bean's list at
  `:81`) — and the third one hands back the corpus in the loader's own format, truncation marker included
  (`:197`), which is the same blob the java arm puts in its prompt. `read_doc`'s header is carved *out* of
  the cap rather than added to it: 256 characters are withheld from the body so the header and the paging
  hint survive the cut (`:69`, `:178-182`), because a truncation that removed the instruction for how to
  continue would have cut the only part a step could act on. And every body goes through
  `ToolSurface#remembered` — keyed on tool plus arguments (`tools/ToolSurface.java:142`), a 128-call window
  that drops the oldest call in it (`:60`, `:153-158`), cleared by `startRun()` at each build's start
  (`:130`) — so an identical repeat is answered from the first one while a changed argument runs again.
- **The dedupe carries much of the call count, and that is measured.** One agentic `TradingAgents` build
  logged 386 tool calls of which 273 reported being identical to an earlier call in the run and answered
  from it (`read_doc` 217, `list_docs` 49, `assemble_corpus` 7), and its reproduction five hours later 299
  of 440 (239 / 47 / 13) — `docs/12-backlog.md`. The mechanism working is the good reading; the other is
  that a step spends most of its loop asking again for what it already holds, so **a parity run's call count
  overstates how much the step learned**, and a cost comparison that reads only that column is comparing
  repeats.
- **Excluded by category, not by oversight.** Everything left off the list is a verdict (`GateChain`, the
  lifecycle checks, a `check_tier`), a write (publish, file edit), a model call (`Refiner`, eval, and the
  three scorer prompts, which stay classpath resources under `skill/ScorerPromptBoundaryTest`), or the
  referee. A shell, filesystem-write, web, MCP, subagent or model-writable-memory tool would make a
  blueprint document a remote-code-execution path — and `security/TrustTierPolicy` filters only the
  *declared* strings on a spec, so the builder's own tier check would not stop it. The tier vocabulary is
  deliberately unchanged (`T1 ∅ / T2 {search, web} / T3 {search, web, file, shell} / T4 *`), which puts
  every builder role at `T4_PLATFORM`: backed-ness is proved against a booted registry, never by a tier.
- **The checks are forced in Java, because an invitation was declined.** `eval/AgenticAgentSpecGenerator`
  runs `AgentSpecValidationTool` and `AgentSpecSecurityScanner` on each draft itself and re-invokes the
  same step with the findings appended, at most twice, after which the last draft goes to the existing
  gates with what still failed as its `CompiledDraft` issues. No gate moved into a pack.
- **A declaration is checked at deploy.** `refuse` compares each step's `toolGroups` against the roles the
  resolver reports and fails the build naming the pack, the step and the available roles — the blind spot
  `AgentSpecValidator` leaves open, closed on the route that exists to reach tools rather than in
  `eval/AgentSpecInvokerFactory`, whose `ToolGroupBindingTest` still pins that a typo deploys verbatim on
  the eval and improvement path.
- **Unproven where it is about the model** — and now for a stated reason rather than for want of a run.
  `docs/08-builder.md` records what the agentic arm measured on 2026-11-23 in one phrase: **not reachable on
  this endpoint**. Six attempts ran against `http://spark.local:4000/v1` / `Qwen-instruct` and not one
  printed a verdict line. The complete clean arm (`run started 2026-11-23T13:03:42Z` at HEAD `a45c549`,
  2700 s per build) came back `exit 124` on all three inputs — `answered=1 spent=94 offered=43` for
  `parcel-claims`, `answered=0 spent=94 offered=42` for `Sentinel` and for `TradingAgents` — under the
  footer `built 0 of 3 against a bar of 2/3`. `TradingAgents` was then given the ninety-minute budget
  twice, five hours apart (15:59:21→16:52:26 and 21:45:35→22:38:39), each printing `exit 1, answered=0
  spent=116 steps-offered-tools=50` with 386 and 440 tool calls under the corrected pattern; neither was cut
  off, and both stopped themselves at about 53 minutes on `action:surveyBlueprint timed out after 60,000ms`
  out of `core/BuilderAgent` (`BuilderAgent.java:121`, via `BuilderService.build` at
  `BuilderService.java:134`). So no value of `SMITH_PARITY_TIMEOUT` is the binding constraint — the 60 s
  interaction clock is — and on the earlier `parcel-claims` pair the survey answered in about 40 s before
  the drafting call timed out 41 times to the 2700 s cutoff, 124 to 179 tool calls across the two passes.
  "Does the step use the tools" is therefore answered yes, and "does it build" is answered **not measured**
  rather than no: the bar counts published specs, no `BUILD OK:` or `BUILD BLOCKED at` line printed on any
  of the six, so the 2-of-3 bar was missed by producing nothing to count rather than by producing worse
  specs. `--flow java` stays the default for the reason this page already gives — the java arm's recorded
  2-of-3 against these zero verdicts — and `docs/08-builder.md` says outright that it is *not* because a
  divergence was observed. A registry assertion is still not evidence about model behaviour.
- **What the parity columns cannot mean.** `examples/parity-agentic.sh` counts `answered` as
  `grep -c 'received LLM response'` (`:207`), and the platform prints that line for an *action*'s response
  — `(action:surveyBlueprint) received LLM response …` — not for the per-round responses inside a tool loop
  (`:47-49`), so `answered=0` sitting beside 386 tool calls is not "the model never answered": every one of
  those calls is a model answer that column never sees, and only the java arm's 9-14 are comparable with
  each other. `spent` is `grep -cE 'Retry attempt|timed out after'` (`:208`) — a line count over two
  patterns, so an event that logs under both is counted twice and the column runs above the event count
  rather than at it. `offered>0` says a step started a loop that named something, never that a validator was
  declined, and the script's own comment is that the surface differs by input — `validatePack` and
  `scan_spec` are on it for `parcel-claims` and absent for `TradingAgents` and `Sentinel` (`:57-62`) — which
  is in tension with the decline this page records for a `TradingAgents` run (§The pack flow); the
  disagreement is on the record and `offered` is not the column that settles it. And `called` before
  `19df658` (18:45) is instrumentation rather than behaviour, because the old pattern could not match a
  snake_case tool name and printed 0 against logs holding hundreds of calls.

The agentic flow's tests: `eval/AgenticAgentSpecGeneratorTest` that a clean draft ends the flow after one
call, that findings come back appended to the same request, that the re-asking is bounded and the last draft
carries what still failed, and that an unresolvable declared group refuses before any deploy or call — its
platform is a real `RegistryToolGroupResolver` over `tools/ToolGroups.ofTools`, not a mock whose resolver
would be null, so the refusal fails for the right reason. `cli/BuildCommandTest` that `--flow agentic` wires
the agentic pack alone and that `--flow agentic --technique embabel` refuses without building. Then the
per-group trio for each new role (`BlueprintToolGroupTest`, `PackLibraryToolGroupTest`,
`SecurityScanToolGroupTest`, `TraceToolGroupTest`, `AgentsPackValidationToolGroupTest`), each with a
body-semantics case, a booted `availableToolGroups()` case, and an identical-repeat case, and
`tools/ExposedToolGroupsTest` for the set. `tools/PackStepFiles` is what lets a coupling test read a step's
`toolGroups:` block as a list rather than grep the word anywhere — these prompts discuss documents and
libraries in prose, and prose would make any substring match pass without the declaration.


## The harness flow (`--flow harness`)

The fourth value of the same flag. `cli/BuildCommand.generators()` gains one branch and
`eval/HarnessAgentSpecGenerator` — which *extends* the agentic generator rather than restating it, so the
forcing ceiling, the identical-call memo and the per-build window clearing remain one implementation and not
two — deploys `specs/builder-harness` through its own `builderPack()`
(`eval/HarnessAgentSpecGenerator.java#builderPack`), whose three steps are `surveyDocumentRoots` (declares
`blueprint`, `library`), `planWhatToReadNext` (`blueprint`) and `draftAgentSpecFromRoots` (`blueprint`,
`validate`, `security`). `--flow java` is still the default and neither earlier flow's semantics moved:
redefining `agentic` in place would have invalidated the measurement record that cites it while leaving the
citation intact. The step *files* are read alphabetically, which is why
`HarnessAgentSpecGeneratorTest` asserts the set with `containsExactlyInAnyOrder` — the run order is the
planner's, not the reader's.

- **The deciding act is the difference.** On `agentic` the driver calls `assemble_corpus` once and both steps
  see the same blob; here a step calls `list_docs` for what exists, `read_doc` for the one it wants (with the
  offset a capped answer names), and `planWhatToReadNext` decides what the drafting step still has to read.
  That is the whole distinction the flow's name is about: a tool surface plus a loop that chooses its next
  reading.
- **What did not move is the corpus handover.** `cli/BuildCommand.resolveDescription()` still assembles the
  corpus for a `--docs` build under this flow, because `newService` wires `SingleShotAgentsPackGenerator`
  unconditionally and the multi-agent candidate consumes the assembled text. So the pack's prompts tell the
  steps to work the roots through the tools and treat the inline text as a capped first reading; cutting the
  handover is, in `docs/12-backlog.md`'s words, "a change to the dual-candidate path with its own
  measurement, not a side effect of the pack".
- **Roots stay the driver's, unchanged.** `tools/ToolSurface` still holds what the command line opened, so a
  step naming a path outside them is refused — with an inside-the-roots read as the control case, because an
  absence assertion that never reads a file passes for the wrong reason (`tools/HarnessDocumentCursorTest`).
  The same test pins that the step files' declared roles sit inside the pinned resolvable set, and that an
  identical repeat is answered from the first call with `startRun()` clearing the window per build — proved
  load-bearing by deleting that call, which makes the CLI case fail with the *previous* build's answer.
- **The gate list is where the flow shows.** `FlowGateParityTest` drives both arms over one fixture: on a
  draft that finishes, harness and java record the same gates in the same order and both publish; on a draft
  whose goal names a type nothing produces, both stop at the `build` stage and neither writes anything — but
  java logs `[generate, validate]` and harness `[generate]`, because its forced validator ran inside
  generation and the defect arrived with the draft rather than as a separate stage. Same stage named, one
  fewer recorded gate: that is what "the check moved into the flow" means as an assertion rather than prose.
- **Unproven where it is about the model** — with the reason now on the record rather than as an absence.
  `examples/parity-agentic.sh` takes the arm as `SMITH_PARITY_FLOW` (`agentic` by default (`:121`),
  `harness` for this route), keeping the java arm's 2-of-3 bar and the answered / spent / offered / called
  columns — §The agentic flow states what those four can and cannot mean — and the harness arm pays a third
  step's tool loop on top, so it is budgeted *above* the agentic arm rather than beside it. What the runs
  said: the agentic arm's six attempts are recorded in `docs/08-builder.md` as **not reachable on this
  endpoint**, zero published against the bar and no verdict line on any of them, which makes the missed bar
  *not measured* rather than a measured negative; on `parcel-claims` it ran twice on code-identical HEADs,
  each pass answering its survey in about 40 s and then failing 41 draft calls until the 2700 s cutoff. Two
  harness arms are on record, both on `parcel-claims`, and neither printed a verdict line either, so the
  harness zero is the same kind of zero: an orphaned one at 24 loops, 50 calls, 48 timeouts, and a complete
  one that reached the 2700 s cap (`docs/08-builder.md`: `exit 124`, `answered=2 spent=92 offered=43`) at 43
  loops, 89 calls, 92 timeouts — `surveyDocumentRoots` answered in 30.409 s, `planWhatToReadNext` in
  59.332 s, which is 0.668 s inside the interaction timeout, then 40 timeouts on
  `draftAgentSpecFromRoots`. Against the agentic arm on the same input and code-identical classes, the
  harness flow's measured delta is **one extra landing step and no extra verdict**, at fewer tool calls
  (89 against 124-179) and the same 43 loops. So the wall is not the routing: neither flow emits a whole
  AgentSpec inside 60 s once the step has spent part of that clock reading. What remains unmeasured is the
  rest of the matrix: no input has both arms from the same HEAD beyond this one, and no other blueprint has
  a harness arm at all. Two caveats travel with any of these numbers — the `called` column could not match a
  snake_case tool name until `19df658`, so earlier runs printed 0 where hundreds of calls happened, and the
  endpoint's throughput moved tenfold across the day these were taken.

Its tests: `eval/HarnessAgentSpecGeneratorTest` that the pack it drives is the harness pack on disk, that a
clean draft ends the flow in one call, that a step declining the offered validator does not skip the check,
that a second unfixed failure is recorded rather than published, and that a declared role the context cannot
resolve refuses before anything deploys or spends; `tools/HarnessDocumentCursorTest` for the containment and
dedupe facts above (same package, because `BlueprintToolGroup`'s read and listing are package-private);
`cli/BuildCommandTest` that `--flow harness` wires the harness pack alone, refuses `--technique embabel`
without constructing a build service, and opens its document roots and starts a fresh tool window; and the
two harness arms of `FlowGateParityTest`.

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

Because of that, `BuildCommand` receives no base URL, key or model name, and
`SmithBuilderApplication` no longer reads `OPENAI_CUSTOM_*` itself. What it does take grew at the other end:
at HEAD the constructor is `(platform, ai, ToolSurface surface)` (`BuildCommand.java:151`), with a
four-argument overload that adds the `SkillScriptExecutionEngine` (`:155`), and
`SmithBuilderApplication#run` resolves the surface from the context rather than building one. That is
not tidiness: the tool groups are beans that were handed the context's `ToolSurface` when the context
started, so a surface constructed inside the command would be populated and read by nobody — the comment at
`BuildCommand.java:134-141` says so in those terms — and it is the injected instance that makes the tool
groups see the same opened roots the stages do. The endpoint
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
4. **Score the process** (`AgentsPackInvokerFactory`): every pack deployed into one scope, the process's
   entry goal run against the same eval tasks.
5. **Recommend** between the candidates — and *then* apply the score gate, to the **recommended**
   candidate only: what must not ship below the gate is what the build ships.
6. **Store both** candidates (`BuilderAgent.writeAgentsPack` refuses an invalid process), then the
   **security gate** (`AgentSpecSecurityScanner`) over the packs about to be published — a process
   contributes all of its packs. A critical finding blocks at `security` ([[spec-security-scanning]]).
7. **Lifecycle** (`LifecycleStamper`) on the recommended candidate: content hash, baseline eval
   pass-rate, initial state `incubating` (promotion to `active` is owned by the studio), and a
   timestamp, as a `lifecycle.yml` sidecar ([[spec-lifecycle]]).
8. **Publish** only the recommended candidate.

## The publish seam

`publish/Publishable` is the content-agnostic unit a publisher takes — `name()`, `baselinePassRate()`,
`writeTo(dir)` — implemented by `AgentSpecPublishable`, `AgentsPackPublishable` and `SkillPublishable`.
`Publisher.publish(Publishable, overwrite, push)` is the single abstract method (the old 4-arg
`AgentSpec` form is now a `default`), so every artifact shares the collision and no-push semantics
instead of carrying its own publisher.

- **`folder:<path>`** — written to `<path>/<name>/` (for an agents-pack: `process.yml` plus one directory
  per pack) with `lifecycle.yml`. A same-named artifact is a collision unless `--overwrite` — and the
  directory is named from `Publishable.name()` alone, so a publish that passes the flag replaces it in
  place. That collision rule is folder-only and understates the system rule: publishing a name the store
  already holds **adds a version** rather than replacing what is there
  (`docs/06-spec-sources.md`), a bare pack name reads back as its highest version
  (`studio/store/ProcessStore`), and the improvement loop's candidate is published one patch above the pack
  it improved while the version it replaced stays stored and addressable
  (`improve/Refiner.nextVersion`, `improve/LoopPublishPolicy.publish`; `publish/PackVersion` is the version
  held for an artifact declaring none). The prose for that rule is §Publishing of the page just named.
- **`git:<url>[@ref]`** — cloned into a scratch work tree, written as a subdirectory named after the
  artifact, committed (author `Smith Builder`). `--push` pushes; without it the commit stays local.
  Re-publishing an unchanged artifact stages nothing. All git runs non-interactively
  (`GIT_TERMINAL_PROMPT=0`, `core.askpass=false`).

A success report prints `candidate:` (the recommendation's reason — the two scores, plus what stopped
the rejected candidate), `candidates:`, `eval score:`, `security:`, `lifecycle:` and `published:`; a
block names the stage, the score if there was one, and the reasons.

## Skill scripts (`scripts/*.js`)

A published skill can carry JavaScript beside its three files. `skill/Skill` holds an optional script list,
`publish/SkillPublishable` writes them into a `scripts/` folder under the published directory, and
`prompts/generate_skill.jinja` now carries the authoring instructions — emit one small script per computed
step a model can get wrong, none when the responsibility is judgement, plain ECMAScript against a surface
with no network and no host. A skill that carries none renders and publishes exactly as it did before
scripts existed.

Whether one *runs* is a separate, opt-in decision. `skill/script/GraalJsSkillScriptExecutionEngine`
implements Embabel's `SkillScriptExecutionEngine` and executes the script in the builder's own JVM:
`HostAccess.EXPLICIT` with host class lookup, environment, threads, processes and network all denied and
file access bound to the skill's own folder, a 25 000 000-statement limit backed by a 5 000 ms watchdog, and
a script that raises coming back as `Script failed: … [exit code: 1]` **tool output** rather than a stack
trace into the agent loop. The bean exists only under `smith.builder.skill-scripts.enabled`, off by default,
and `SkillScriptEngineBeanTest` boots the context both ways to hold that: off, the resolvable roles are the
seven they were; on, still those seven, because an engine is not a tool group. Whether a script is *offered*
at all is Embabel's own filter — `LoadedSkill.getScriptTools(engine)` keeps the languages the installed
engine claims, so with the shipped `NoOpExecutionEngine` the script is loaded and inert, which
`SkillScriptToolSurfaceTest` asserts as the control case.

Three limits stay on the record rather than implied. Two things in `prod/` call
`Skills.withScriptExecutionEngine`, both behind `smith.builder.skill-scripts.enabled` and off by default.
The build-internal one is `skill/SkillScriptHost`, and its caller is the skill *under build*'s with-skill
eval arm rather than any reader. The reader-side one arrived with `builder-skill-script-host`:
`skill/PublishedSkillHost` points the same `withLocalSkills` at the directory `publish` wrote — the real
folder, not a re-derived copy of it — installs the engine only when the launcher's bean provider offered
one, and names the folders the loader refused instead of leaving that to the loader's log
(`skill/PublishedSkillHost.java:111-112`, `loading()`); `cli/SkillRunCommand` (`smith skill-run --skill-dir
<dir> <request>`) is the only route that attaches it to a model call. So the reader seam
`docs/08-builder.md` names is closed on that one
route and open off it: the A2A card still publishes `io.a2a.spec.AgentSkill` entries that describe a
capability rather than load a folder, a `smith run` is offered nothing a published skill's folder holds, and
on an ordinary build with the switch off a published script is still a file nothing executes
(`docs/08-builder.md:449-461`). The native binary builds
and boots with the engine on its classpath, but the classpath is not the switch: `@ConditionalOnProperty`
is decided during `process-aot`, so no runtime property turns the bean on inside the image, and the one
live skill build attempted with the switch baked in at build time died at the eval stage in
`kotlin-reflect` — `Unresolved class: com.embabel.agent.skills.spec.SkillDefinition` — with no
reflection hint naming that package. `builder-native-hints` now names `skills.spec.SkillDefinition`, `SkillMetadata` and `skills.Skills`, and an
image built with them loads a published folder and answers a request through `smith skill-run` —
[[native-binary]] has the three-image way that was found, and the two extra entries were only visible by
running a binary. What has not been executed inside a binary is a *script*: that image carries no engine,
because `@ConditionalOnProperty` is decided at `process-aot`, so its own loader says `script execution is
not yet supported`. And a published
script is code the reader's agent runs, which is why
`security/AgentSpecSecurityScanner.scanSkill` reads `scripts/*.js` as code and not as text alone — and why,
since the same change, a skill that ships JavaScript is *reported* at `T3_PARTNER` rather than at the
`T1_COMMUNITY` every skill used to get, with the scriptless skill as the control that still reads
`T1_COMMUNITY`. A tier is a set of tool-group role names and a script declares none, so that number is the
scan's report and never a gate on execution — see [[trust-tiers]], and [[skill-scripts-graaljs]] for the
POC that measured the engine before it was ported.

## Validators as tools

`tools/AgentSpecValidationTool` and `tools/AgentsPackValidationTool` expose the two format validators as
`@LlmTool` methods, so a drafting step can read the findings against its own draft and re-draft instead of
waiting for the numbered repair stages `BuilderService` runs. They add nothing: the pack tool hands back
`AgentSpecValidator`'s own `ValidationResult` — same findings, same codes, same offending file — and the
process tool passes through `AgentsPackValidator`'s findings, shared-type count, connectivity flag and any
cycle. No threshold, verdict or publish decision lives in either class; blocking stays where it was, in
`GateChain` and `AgentSpecSecurityScanner`. Only the pack validator reaches a pack today: the `validate`
group carries exactly that one tool, and `AgentsPackValidationTool` is now carried by the registered
`process` group (`tools/AgentsPackValidationToolGroup`) — a role no shipped step declares yet, so it is
registered and awaiting a step rather than inert (`tools/ExposedToolGroupsTest` records which of the
six builder roles a shipped step actually names). The pack tool's report also gained a second half:
`validatePack` returns `ValidationReport(issues, patchedByBuild)` — still the validator's own findings,
but split, so a finding the build's normalizer removes is named as the build's work rather than left as
the model's homework (`tools/AgentSpecValidationTool.validatePack`,
`tools/ValidationToolNormalizationTest`).

How they reach a model is the part worth knowing. `@LlmTool` carries no tool-group member and the platform's
`@ToolGroup` annotation is referenced by no other class in the 1.5.4 jars, so a step's `toolGroups` entry
resolves only against `RegistryToolGroupResolver` — whose registry is the `ToolGroup` beans Spring can see.
`tools/ValidationToolGroup` is the builder's: a `@Bean ToolGroup` whose role is `validate`, carrying exactly
the tool `eval/BuilderPackTools.validationTool()` authors — whose schema names that tool's one argument
`specJson`, deliberately, because reflection would name it `arg0`. Its `ToolGroupMetadata` is built by
resolving the Kotlin companion factory **by signature** through `getMethods()`: a `ToolGroup` is not
*directly* authorable in Java (the accessor and companion names are hyphen-mangled, `invoke-bYD-3ao` and its
like) but it is authorable by signature, and `poc/poc-7-runtime-adapter`'s `ShellToolGroupFactory` is this
repo's pre-existing proof (`tools/ValidationToolGroup.java:97-103`, role `validate` at `:66`). The coupling
that comes with it: an Embabel bump that moves that signature makes the private factory throw
`IllegalStateException` (`:112`) from inside a `@Bean`, so the Spring context fails for **every** builder
command, not only a pack-flow build — a what-not-to-break. The recipe is no longer `validate`'s alone.
`tools/ToolGroups.ofTools` (`:87-89`) is the module's one group factory, stamping every group with
`VENDOR = "embabel-smith"` (`:68`), `VERSION = "1.0.0"` (`:71`) and the single permission `HOST_ACCESS`
(`:106`) — and five of the six groups (`blueprint`, `library`, `security`, `traces`, `process`) are built
through it, so the same probe for a five-argument `ToolGroupMetadata` factory (`:98-114`) now stands behind
the whole surface rather than behind one role; `ValidationToolGroup` still carries its own copy of the
probe, which is why both copies are the thing to check on a bump. The blast radius of losing that entry is
therefore every builder command and all six groups, not one tool group, because the throw lands inside a
`@Bean` on a shared context. That is the route the shipped flow
takes, and it replaced the other one. The scope's tool list in `eval/AgentSpecInvokerFactory` is empty, because
`PromptedActionSpec.emit` selects from it by the step's own `tools:` name list and `ActionStepSpec` has
thirteen components and no `tools` component — so a scope-wired tool is unreachable by a pack whatever the
driver passes. A live run measured
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

`prod/embabel-smith-builder/src/main/resources/specs/` holds six agent specs that describe the builder's own
flow — `builder-single-spec`, `builder-process-composition`, `builder-skill-from-process`,
`builder-spec-one-shot`, `builder-agentic-blueprint` and `builder-harness` — as classpath resources
addressed by `core/BuilderPackPaths` (`ROOT` = `specs`) over `smith-common`'s `SpecFolders.classpath`. A
pack step's prompt is still the surface an improvement loop rewrites, which is the point of keeping them as
data rather than code; what the move changed is the placement, not the mutability (`docs/08-builder.md`).
Each pack-driving generator names its pack through a
`builderPack()` method (`eval/PackDrivenAgentSpecGenerator.java#builderPack`,
`eval/AgenticAgentSpecGenerator.java#builderPack`, `eval/HarnessAgentSpecGenerator.java#builderPack`), one
shared helper resolving all of them, so the directory a build starts in no longer decides whether its pack
loads (§The pack flow). A method rather than a `static final` constant is load-bearing: resolution throws,
so a constant resolved the pack at class load and a pack that could not be found died as
`ExceptionInInitializerError` — and then as `NoClassDefFoundError` for everyone who touched the class
afterwards — instead of printing where it looked. The separation from the referee
now holds by directory rather than by being off the classpath: `src/main/resources/prompts` holds the
scorer an improvement loop must not edit and `specs/` holds the surface it may, the boundary
`skill/ScorerPromptBoundaryTest` guards by content while reading the packs through the same helper
(`skill/ScorerPromptBoundaryTest.java#packStepPrompts`), over all six packs and their eighteen
prompt-bearing steps — a count the test also asserts as a floor, so a scan that silently dropped a pack
fails instead of measuring nothing. Three consequences of the move were open and each now has its own
route. A native image carries resources as a flat named set with no directory entries, so
`SpecFolders.classpath` cannot list a pack inside the binary; it falls back to `smith.specs.dir` /
`SMITH_SPECS_DIR` and then to the `specs/` folder the `native` profile copies beside `target/smith`, with
`specs/*/*.yml` and `specs/**/*.yml` registered in the hand-written `builder-native-hints` so the files are
in the image at all, and `PackGlobCoversThePackTreeTest` ties those two globs to the pack sources. The
loop's surface is named by `improve/BuilderPackSurface`, which the studio's default improve source
(`StudioConfiguration#improveSource`) hands the loop as a writable `SpecSource`, and which locates
`src/main/resources/specs` by
walking up from the working directory and refuses rather than falling back to the copy the class loader
serves — an edit to a build artefact is recopied by the next `mvn` run, so the loop would have scored an
edit nothing reads — and `BuiltPacksMatchTheSourceTest` compares the folder the class loader answers for
with the folder the loop edits, which is what stops a step renamed in the source tree from going on being
deployed out of `target/classes/specs`. And POC 27's `outerloop/BuilderPacks` names the directory the packs
moved to, so that loop reads them from disk rather than ending at its own "cannot locate the builder's
packs" line. Three are the surface whose flip `docs/12-backlog.md` still tracks as open (on its publish half), committed
and pinned so the flip is a wiring change rather than an authoring task. The other three are
loaded when a pack flow runs — `eval/PackDrivenAgentSpecGenerator#builderPack` deploys
`builder-spec-one-shot` for `--flow pack`, `eval/AgenticAgentSpecGenerator#builderPack` deploys
`builder-agentic-blueprint` for `--flow agentic` and `eval/HarnessAgentSpecGenerator#builderPack` deploys
`builder-harness` for `--flow harness`, all three through `eval/AgentSpecInvokerFactory` instead of calling
the Java generator — and the flag defaults to `java`, so the shipped build still calls the Java generators
and no candidate changes what an ordinary `smith build` does. What those packs declare is no longer inert:
`draftAgentSpec.yml` and `builder-single-spec/steps/assembleAgentSpec.yml` each carry `toolGroups: [validate]`,
`tools/ValidationToolGroup` registers that role, and the agentic pack's two steps and the harness pack's
three declare `blueprint`, `library`, `validate` and `security` between them — four roles a booted platform
resolves, so a pack deployed on any of the three routes is *offered* what it declares where it was offered
nothing (see §Validators as tools, §The agentic flow and §The harness flow). The packs are not, however,
unconsumed: POC 27's outer loop reads
`builder-single-spec` off disk through its own `outerloop/BuilderPacks`, whose `PACK_ROOT` names the
directory the packs moved to (`outerloop/BuilderPacks.java#PACK_ROOT`) and locates it by walking up from the
working directory (`outerloop/BuilderPacks.java#packsRoot`) — appending its generated policy to one step prompt through its own
`outerloop/BuilderPacks.withStepPrompt` — the same field-for-field pass-through this module's private
`improve/Refiner.withPrompt` encodes, restated rather than called — and deploys the result through
`eval/AgentSpecInvokerFactory`, so
the packs are an improvement object now ([[what-the-pocs-prove]], `docs/04-poc-run-results.md` §7.5),
while the shipped build stays on the Java generators. The blanket form of that last claim — that no
candidate changes what `smith build` does — is retracted in `docs/10-self-improvement.md`: two packs
declare `toolGroups: [validate]` in their step YAML, so a pack edit can move what a step can reach, and
reach is no longer wholly a property of code a candidate cannot touch. What does stay fixed is the flow the
shipped build runs, and the candidate's own reach: `improve/Refiner.withPrompt` copies the `toolGroups`
field through untouched, and the agent order and each step's `pre`/`post` stay off the mutable surface, so
a candidate's reach is its parent pack's.

Three tests hold the ground: `core/BuilderAgentSpecPackTest`
that all six load, validate and deploy (it reaches each pack through
`core/BuilderPackPaths.directory(name)` (`core/BuilderAgentSpecPackTest.java:132`), the same
classpath address the generators use), and that the drafting step of each wired pack — `builder-spec-one-shot`,
`builder-agentic-blueprint` and `builder-harness` — names the envelope its own goal type declares;
`skill/ScorerPromptBoundaryTest` that none of the three scorer prompts appears
anywhere in their step prompts — the mutable surface must not contain the judge; and
`core/BuilderPackTierTest` that the drafting step's declared `validate` group moves the scanner's
recommended tier off `T1_COMMUNITY`, which is a scanner result and never a reach result — registering the
group moved no tier, because `validate` is in none of the allowlists and the pack reads `T4_PLATFORM`
through T4's `*` ([[trust-tiers]], [[spec-security-scanning]]). Its control is the stronger form of that
point: the four roles a builder pack really resolves to report the same tier as a name that resolves to
nothing.

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

Three hygiene rules these packs live under were promoted into
`docs/08-builder.md`, and they are enforced in three different places — which is
the part worth remembering, because one of them is not enforceable where you would look for it. A step that
declares an input its prompt never interpolates is rejected by the format validator itself,
`PROMPT_IGNORES_DECLARED_INPUT` (`smith-common/.../validation/AgentSpecValidator.java:280`) — the mirror of
the better-known `PROMPT_UNDECLARED_INPUT` two paragraphs earlier at `:237`, and the reason is in the
class's own summary: a prompt is a literal template, so a variable it never names is never sent
([[validation-codes]]). One bound on it, from that check's own javadoc (`:255-261`): it fires only when *none*
of the declared inputs is named, so a step that ignores one of several, or declares none at all, still
validates — and `core/BuilderAgentSpecPackTest`'s validator case
(`core/BuilderAgentSpecPackTest.java#eachBuilderPackPassesTheAgentSpecValidator`) is what runs it over the
shipped packs rather than leaving it to a build. A step that performs a deterministic check instead of a
prompt has no prompt to check, so `PROMPT_MISSING` — "action has no prompt" (`:216`) — is what keeps
deterministic work in host code rather than in the pack. How far that reaches, stated exactly: it catches a
step with no prompt at all, and the deploy-time refusal
(`eval/AgenticAgentSpecGenerator.refuse`) compares `toolGroups` and nothing else, so a
deterministic check *wrapped* behind a prompt is indistinguishable from a cognitive stage — that rule is held
by the construction already described (§The agentic flow: the checks are forced in Java, no gate moved into a
pack), not by a check a pack could fail. But the no-`BackgroundMessage` rule is *not*
validator-checkable: that type is on the validator's framework list (`frameworkTypes`, `:60`), so
validation passes whether or not a step names it. What holds the rule is
`core/BuilderAgentSpecPackTest`'s
`everyBuilderPackDeclaresItsOwnTypeForEveryStepAndGoalOutput`, which asserts every step output and the goal
output is one of the pack's own `types/` declarations — the structural form of the rule, so a violating pack
is rejected rather than waved through on a name it happens to match. The stake is recorded in that test's
own comment: the polymorphic `parts` of the background type is what cost POC 27 its run
([[what-the-pocs-prove]]).

## Tests

Offline tests cover the `BuilderAgent` compile/validate/write, the `RefinementLoop`, the
`AgentSpecSecurityScanner` verdicts, the `LifecycleStamper`, the publishers (collision, no-push), the
dual-candidate path (`DualCandidateBuildTest`, `CandidateRecommenderTest`, `AgentsPackRepairTest`,
`SingleShotAgentsPackGeneratorTest`) and the model layer on mocks (`AiPromptRewriterTest`,
`AiSpecPromptCallerTest` — the latter proves the system prompt goes out separately and that a blank
one is omitted) plus the `NoDirectModelRequestTest` guard. The live build (which calls the model) is
endpoint-gated; `LiveBuildIntegrationTest` asserts the caller-visible outcome, not wire-level error
text.
