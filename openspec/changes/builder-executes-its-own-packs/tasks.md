# Tasks

## 1. Preconditions — the tool surface exists and resolves

- [x] 1.1 Confirm `poc27-improves-builder-agentspecs` 8.2 landed: `@LlmTool` or `@Tool` is **non-zero** in
      builder `src/main` and the adapter contains no gate logic. Check, do not assume:
      `grep -rn "@LlmTool\|@Tool" prod/embabel-smith-builder/src/main --include=*.java` (zero today, in this
      module and every other prod module) and read the new type to confirm it holds no threshold, verdict or
      publish call.
      **Status, 2026-11-21:** not landed. A delegation reported two adapters under `builder/tools/` and the
      tree holds neither them nor any `@LlmTool` anywhere in `prod/` — the research came back, the deliverable
      did not. The check above is the check that catches that, which is why it is written as a command rather
      than a citation. Re-tasked.
      **Landed 2026-11-22 (`719cc76`), and the command is what says so:** two hits, in
      `tools/AgentSpecValidationTool.java:54` and `tools/ProcessValidationTool.java:50`. Both read as
      adapters — a validator field, one call, no threshold, no verdict, no publish — with the pass-through
      equality pinned by `tools/ValidationToolPassthroughTest`. Read the count narrowly: `@LlmTool` is read
      by `MethodToolFactory`, which `Tool.Companion` implements, so the annotation produces a `Tool` when
      something asks for one. Nothing scans an annotated class into a running agent — the annotation's other
      readers are upstream's own tool libraries and `DomainToolFactory`, which extracts from domain sources.
      The count proves the adapters are annotated, not that a step can call them.
- [x] 1.2 Record the **group name** a step must declare to reach the validator, with the mechanism that makes
      it resolvable — the name is 8.2's output and this change's central input. If 8.2 reported that a named
      group needs a platform-side provider this repo does not have, **stop the change** and record that here;
      the flip is then blocked on upstream, not on work.
      **Claim on file, unverified:** the reported answer was `default-llm`, on the argument that an
      `@LlmTool` method lands in `ToolExecutionRequest.defaultToolGroups` rather than in a named group, so no
      provider bean is needed. That contradicts nothing in `eval/ToolGroupBindingTest` but is not established
      by it either — that test only proved a declared role is carried verbatim to the action. Treat the name
      as a hypothesis with a jar citation owed, not as 1.2's answer.
      **Answered 2026-11-22, and `default-llm` is not it — but a name can be made, so the stop condition
      does not fire.** No *stock* name reaches these validators. `@LlmTool` carries no group member at all
      (jar: `description`, `name`, `returnDirect`, `category`, `metadata`), so an annotated method enters no
      named group by being annotated; `api/annotation/ToolGroup` appears in no class but its own across all
      25 Embabel 1.5.4 jars, so annotating a bean registers nothing; and resolution runs against
      `RegistryToolGroupResolver(String, List<ToolGroup>)`, whose list Spring fills from `ToolGroup` beans
      (`AgentPlatformConfiguration.toolGroupResolver(List<ToolGroup>, List<List<ToolGroup>>)`) — upstream's
      members being the five `CoreToolGroups` plus MCP-configured groups.
      **A `ToolGroup` bean is authorable from Java and the recipe is already in this tree.** I first wrote
      that it was not, on the jar evidence: `ToolGroupMetadata`'s version accessor is `getVersion-Id9oKnY()`
      and the metadata factories are Kotlin companions `invoke-oteLqWg` / `invoke-bYD-3ao`, names `javac`
      cannot express, while `MinimalToolGroupMetadata` is package-private. That is only *direct* authoring.
      `poc/poc-7-runtime-adapter/.../ShellToolGroupFactory.java` builds one by resolving the five-argument
      companion factory by **signature** through reflection and passing the result to
      `ToolGroup.Companion.ofTools(metadata, tools)` — plainly named, callable from Java — and
      `ShellToolGroup` publishes it with `@Bean`, which is how POC 7's `toolGroups: [shell]` resolved at all.
      So 1.2's answer is: register a builder-owned group (a name in `search`/`web` shape, not `default-llm`)
      through that recipe, then declare it. Cost of the recipe: the lookup is signature-pinned and throws a
      named `IllegalStateException` on an Embabel bump rather than failing quietly.
      **What stays from the earlier reading:** `Tool.fromInstance(Object)` into the scope's `List<Tool>`
      remains a second, reflection-free route. Pick one in D7 before implementing; the difference that
      matters is that a registered group is *declarable by a pack*, so reach moves onto the mutable surface,
      whereas a host-wired tool list keeps reach off it.
- [x] 1.2b Check the tier consequence of that name before accepting it. `security/TrustTier.java` allowlists
      `search`, `web`, `file`, `shell` and `*` — `default-llm` appears in none of them, so a step declaring
      `default-llm` may leave `recommendedTier()` at `T1_COMMUNITY`, which is exactly the movement task 8.3
      exists to detect. If that is what happens, the finding is not "8.3 fails" but "the tier is not the reach
      control for builder tools", and design D10 of the preceding change needs amending before this one's D6
      gate 1 can be read as containment.
      **Checked 2026-11-22, and it is the second reading — with the opposite sign from the one expected.**
      `TrustTierPolicy.check` (`:52-55`) filters declared strings against the allowlists and resolves
      nothing, so `recommendedTier()` moves on *any* declaration: `web`/`search` to T2, `file`/`shell` to
      T3, and any other name — `default-llm`, a typo, `math`, `github` — to T4_PLATFORM, because T4 allows
      `*` and `minimumTier` (`AgentSpecSecurityScanner.java:251-256`) always terminates there. The tier
      therefore neither grants a tool nor withholds one; an unresolvable name reads as maximally privileged
      rather than as an error. D10 of the preceding change is amended for this (its measured paragraph after
      the 8.1 qualification), so gate 1 below is what carries containment, not the tier.
- [x] 1.3 Confirm 8.3 landed and the scanner's view moved: report `builder-single-spec`'s
      `AgentSpecSecurityScanner.scan(...).recommendedTier()` before and after its step declared the group.
      Still `T1_COMMUNITY` means the declaration is not reaching the scanner and D1's premise is unproven.
      **Scope narrowed 2026-11-22 by 1.2b:** this is a scanner test only. The tier moves on any declared
      string, so a moved tier proves the scanner read the step and nothing about resolution — D7 is what the
      reach now depends on.
      **Answered 2026-11-22 by 8.3's commit (`77f39bb`), so this task adds nothing.** `builder-single-spec`
      scans at `T1_COMMUNITY` with `assembleAgentSpec.yml` at `toolGroups: []` and at `T4_PLATFORM` with the
      declaration landed. Both halves are measurements through `scan(...)`, held as
      `builder/src/test/.../core/BuilderPackTierTest`: reverting the declaration fails the tier assertion,
      and the class's control shows `T4_PLATFORM` is what the scanner reports rather than what the allowlist
      table implies, because a pack declaring a name that resolves to nothing reports the same tier. D1's
      premise is therefore proven in the only sense 1.2b left it provable: the declaration is on the mutable
      surface and the scanner reads it. Resolution is D7's and 4.1's, not this task's.
- [x] 1.4 Confirm 8.5 landed — all three packs deploy in one scope. This change deploys a fourth pack
      alongside them for the process path, so a goal-name or type-name collision discovered later would land
      on the shipped path rather than a test.
      **Landed 2026-11-22** (`eval/CollaborationStructureIsFixedByConstructionTest`): one `deploy(AgentScope)`
      carrying all 12 steps and all 3 goals of the three packs, offline against a mock platform. Goal names are
      distinct and no step name is shared, so the loud collisions are absent — but the quiet one is real and
      this is where it belongs. `eval/AgentSpecTypeMapper.allRuntimeTypes` dedups types **by name**, so two
      packs that each declare `ProcessScope` with different properties collapse to whichever the reader met
      first, with no exception and no validator finding. A fourth pack therefore has to be checked against the
      other three **by type name**, not only for a distinct exported goal — and since the collapse order comes
      from `YmlStepSpecRepository.findAll` iterating an unsorted `File.listFiles()`, "whichever came first" is
      not something this repository controls. The mapper's own javadoc states the assumption that fails
      here: "the shared type two agents hand off through is declared by both, and the dictionary holds it
      once" — identical by construction, which is a promise the format does not enforce. The collision is not
      hypothetical: two names already collide
      across the three packs, and the interesting one is real. `builder-single-spec/types/ProcessScope.yml`
      declares `roles` and `packName`; `builder-process-composition/types/ProcessScope.yml` declares
      `responsibilities` and `processName`. Same name, four properties, two of them different — so one of the
      two packs' steps reads properties its own type declaration said it had and the surviving type does not.
      Check: `grep -h "^name:" prod/embabel-smith-builder/builder-agent-specs/*/types/*.yml | sort | uniq -d`
      prints `AgentSpecJson` and `ProcessScope`. The fourth pack did join them, and did so the cheap way —
      its `types/AgentSpecJson.yml` is a verbatim copy, so that collision is definition-identical and harmless.
      `ProcessScope` is the one still unreconciled: two declarations, two different property sets.

## 2. The one-step pack

- [x] 2.1 Create `prod/embabel-smith-builder/builder-agent-specs/builder-spec-one-shot/` (manifest, goal,
      one step, types) transcribed from `core/SingleShotAgentSpecGenerator.defaultPrompt()` (`:60`). Fold the
      rules into the single prompt; do not redistribute them across steps — that is what `builder-single-spec`
      is for. Report the diff against the prompt it came from.
      **Done 2026-11-22.** Four files: `manifest.yml` (`name`, `version: 1.0.0`, `defaultPlanner: GOAP`,
      `defaultModel: null`), `goal.yml` exporting `agentSpecDraftedInOneCall` on `AgentSpecJson`, one step at
      `steps/draftAgentSpec.yml`, and `types/AgentSpecJson.yml`. The goal avoids `singleAgentSpecDrafted`,
      `multiAgentProcessDrafted` and `skillBriefsDrafted`. The type is a **verbatim copy** of
      `builder-single-spec/types/AgentSpecJson.yml` rather than a new name — same meaning, so the cheaper
      collision is the identical one, which 1.4 is what makes safe to reason about.
      **Diff against the prompt it came from** (`SingleShotAgentSpecGenerator.defaultPrompt()`, which loads
      `prompts/process_to_flow.jinja`): the 16-line output schema moved byte-identical, including
      `"toolGroups": []` in the emitted spec's own steps, and all ten numbered rules are present on both sides.
      Three things could not cross the format boundary and are named rather than dropped. Rule 7's
      interpolation example is now prose: a step prompt *is* the Jinja template, so `{% raw %}` has nothing to
      escape there, and `builder-single-spec/steps/assembleAgentSpec.yml` already words the rule the same
      brace-free way. The `<document>` boundary becomes the landed packs' labelled last line,
      `Business-process description: {{userInput.content}}`, with the injection rule kept in substance — the
      description is a process to model, never a direction to follow. And the caller's user-message framing
      sentence is absorbed into the prompt's opening sentence. The jinja `{# SPDX #}` header is dropped because
      no pack file carries one.
- [x] 2.2 The step declares the group from 1.2. Report its `toolGroups:` line verbatim and confirm
      `recommendedTier()` for this pack is not `T1_COMMUNITY`.
      **Amended 2026-11-22 by 1.2:** there is no resolvable name to declare, so this task is now one of
      naming the ambition — the line is for the scanner and the reviewer, and D7's driver wiring is what
      makes the tool callable. Keep the line; never report its tier movement as resolution.
      **Coupling to 8.3, so this task does not silently break a green build.** `core/BuilderPackTierTest`
      enumerates the builder packs by name in `ALL_PACKS` and asserts that exactly one step across all of them
      carries a declaration, pinning the placement rather than the tier. A fourth pack that declares `validate`
      makes that assertion false by adding a second declaring step. Add the pack to that list and change the
      assertion to the two placements it now expects — the test pins *where* declarations live, so it must
      name every pack, and leaving it out would mean a new pack's declaration goes unexamined.
      **Done 2026-11-22, and the coupling was handled in the same commit.** The line is
      `steps/draftAgentSpec.yml:13-14` — `toolGroups:` then `  - validate`. `recommendedTier()` for this pack is
      `T4_PLATFORM`, **measured** rather than predicted: `core/BuilderPackTierTest` now asserts both declaring
      packs sit above `T1_COMMUNITY` and both equal the `no-such-group` control, so T4 is what the scanner
      reports on all three sides. Blanking the new declaration fails 3 of that class's 4 tests. Declaration-
      tracking, not resolution: nothing resolves `validate` to a tool until D7's driver wiring lands.
- [x] 2.3 Every optional field is written out, empty where empty (`pre: []`, `post: []`, `cost: null`,
      `value: null`, `canRerun: null`, `nullable: null`, `toolGroups:` on the step; `defaultModel: null` on
      the manifest), matching the landed packs. Check:
      `grep -L "toolGroups:" prod/embabel-smith-builder/builder-agent-specs/*/steps/*.yml` prints nothing.
      **Done 2026-11-22.** Every optional field is present on the step and `defaultModel: null` on the manifest,
      and the check prints nothing across all 13 step files. The keys were read off `AgentSpecReader` and the
      `AgentSpecManifest`/`GoalStepSpec`/`ActionStepSpec`/`DynamicTypeDef` records rather than guessed, so each
      one is a key the loader actually maps.
- [x] 2.4 Extend `core/BuilderAgentSpecPackTest` to cover the fourth pack: load, validate, deploy, and its
      exported goal name is distinct from the other three. Verify it fails when the pack is hidden.
      **Done 2026-11-22.** `builder-spec-one-shot` joined `PACK_NAMES`, which is what puts all four existing
      tests — load, own-type-per-output, validator, deploy — onto the new pack without new test methods.
      Hiding the directory was verified by moving it away and re-running: **all four** fail, each naming
      `pack directory builder-agent-specs/builder-spec-one-shot exists on disk`, so none of them can pass on
      the three packs that remain.
      **One honest wrinkle, deliberately left.** The goal-name test is still called
      `theThreeBuilderPacksExportDistinctGoalNames` while it now checks four. It was not renamed because
      `prod/embabel-smith-builder/src/main/resources/META-INF/native-image/com.embabel.smith/builder-traced-hints/reachability-metadata.json`
      lists that method name as an exact entry, so a rename strands a GraalVM hint — and that file is another
      session's in-flight native-image work, not this change's. Rename the method together with that entry, in
      one commit, by whoever owns the hints. `BuilderPackTierTest` is absent from the hints, which is why its
      rename to the two-placement name was free.
      **What else the fourth pack touched.** `skill/ScorerPromptBoundaryTest` scans the mutable surface by pack
      name, so it gained `builder-spec-one-shot`: floor 12 to 13 steps, re-measured containment 0.028 / 0.000 /
      0.000 over 13 step prompts and 17,549 characters (a fourth pack that transcribes a generation prompt
      rather than a scorer prompt moves the guard's own number and not its verdict).
      `eval/CollaborationStructureIsFixedByConstructionTest` names its three packs explicitly and is unaffected,
      which is the expected behaviour for a test about those three, not an oversight.

## 3. The driver and the flag

- [x] 3.1 Add `--flow java|pack` to `cli/BuildCommand`, default `java`. One flag; do not add a configuration
      property, and do not overload `--technique` (different axis: the pack flow drives the same generator
      the Java flow drives).
- [x] 3.2 The pack flow deploys through `eval/AgentSpecInvokerFactory.invokerFor(...)` and invokes the
      exported goal. Confirm no new module edge appears:
      `grep -rn "com.embabel.smith.runtime" prod/embabel-smith-builder/src/main` stays zero.
- [x] 3.2b The pack flow hands the scope its tools (design D7): pass
      `Tool.fromInstance(new AgentSpecValidationTool())` into the `List<Tool>` argument
      `StepSpecAgentScopeBuilder` already takes, and report the list size at deploy. This is the only route
      1.2 left open, so nothing downstream is testable until it exists. Report whether `fromInstance` yields
      the annotated method or an empty list — an empty list means the annotation is inert outside an `@Agent`
      class, which is the case 1.1 warns about, and then the adapter needs an explicit `Tool.of(...)` with
      the validator call as its body.
      **Two facts already established, so 3.2b starts from them rather than rediscovering them.** The site is
      `eval/AgentSpecInvokerFactory.java:91`, where the constructor's two trailing arguments are both
      `List.of()` — the second is the `List<Tool>` this task fills, so the host change is one expression, not
      a refactor. And neither `pom.xml` nor `prod/embabel-smith-builder/pom.xml` sets a `parameters` compiler
      flag, which means javac emits `arg0` for a method parameter unless `-parameters` is on. So if
      `fromInstance` does yield the annotated method, check the **parameter name in the tool schema the model
      is given**, not only that a tool exists: a one-argument tool whose parameter reads `arg0` is callable
      and still a worse prompt than one that reads `packDirectory`. That is an argument for the explicit
      `Tool.of(...)` body being the better route even when `fromInstance` works, and it is testable offline by
      printing the schema, so report the name beside the list size.
- [x] 3.3 The deterministic gates run on **both** flows in the same order: validate, security scan, lifecycle
      stamp, publish. Add a test that drives both flows over one fixture with a fake caller and asserts the
      recorded gate sequence is identical — the comparison, not the answer, is what must match.
- [x] 3.4 No fallback. A pack-flow failure surfaces as a failure naming the stage; there is no path that
      retries on the Java flow. Verify by making the pack fail to load and confirming the run exits with that
      reason rather than a successful build.
- [x] 3.5 `--technique embabel` combined with `--flow pack` either works or refuses with a named reason
      before any call is spent. Silently using the single-shot prompt instead would make the flow flag a lie.

      **Result, 2026-11-22 (all of section 3).** Ten files: `cli/BuildFlow`, `cli/BuildCommand`,
      `eval/PackDrivenAgentSpecGenerator`, `eval/BuilderPackTools`, `eval/AgentSpecInvokerFactory`,
      `core/AgentSpecNormalizer`, and four test classes. Run as `make test-module
      MODULE=prod/embabel-smith-builder` against `6ca4599` carrying these files, in a detached
      worktree so the other session's uncommitted native-image `pom.xml` work stayed out of the
      numbers: **5 modules SUCCESS, 1359 tests, 0 failures, 0 errors, 0 skipped** (counted from the
      surefire XML, not from the console lines, which also carry per-module summaries). New
      coverage: `FlowGateParityTest` 4, `BuilderPackToolsTest` 6, `PackDrivenAgentSpecGeneratorTest`
      6, and `BuildCommandTest` 25 → 30.

      **3.1** one `@Option --flow` with a strict `FlowConverter`, so a typo exits 2 (`anUnknownFlowIsAUsageError`);
      no `smith.builder.flow` property and no `BuildOptions` field — the only grep hit for that name is the
      javadoc sentence saying there is none. **3.2** `grep -rn "com\.embabel\.smith\.runtime"
      prod/embabel-smith-builder/src/main` returns zero, re-run after every edit, so the module still has no
      runtime edge. **3.2b** the route taken is the explicit `Tool.of(...)`, and the task's two questions
      have answers: `Tool.fromInstance` **does** yield the annotated method (so `@LlmTool` is not inert
      outside an `@Agent` class, which is what 1.1 left open), and its schema parameter is named by
      reflection — `fromTheAnnotationYieldsAToolWhoseArgumentReflectionNamesWithoutParameters` asserts it is
      *not* `packDirectory`. The `-parameters` premise was checked independently: the root pom imports
      `spring-boot-dependencies` as a BOM rather than inheriting `spring-boot-starter-parent`, and no pom in
      the repository mentions `parameters` at all. List size at deploy: **1**.
      **3.3** `FlowGateParityTest` drives `BuilderService` itself over one fixture text fed to both flows and
      asserts the recorded sequence `generate, validate, write, eval` on the finishing build, plus equal
      results at the three gates it cannot inject (invalid draft → `build`, below the score gate → `eval`,
      publish collision → `publish`). **3.4** verified at the declaring source, not only by the tests:
      `BuilderService.build` calls `compileFromDescription` at `:134` **outside** any `try`, so a pack-flow
      failure propagates to the CLI's exit 1; the `catch (IllegalStateException)` at `:233` belongs to
      `generateProcess`, i.e. the *second* candidate, so it cannot swallow this one. **3.5** refused in
      `call()` before `resolveDescription()`, exit 1 (well-formed to picocli, so not 2), with the generator
      map also lacking any `EMBABEL` entry as the structural second lock.

      **Mutation-checked**, four probes in the same worktree, each reverted: inverting the `--flow` branch
      fails `theDefaultFlowWiresTheJavaStagesGenerators` and `thePackFlowWiresThePackAlone`; renaming the
      schema constant to `arg0` adds two `BuilderPackToolsTest` failures (the `doesNotContain("arg0")` half
      and the control); deleting the 3.5 refusal adds
      `askingForThePackFlowWithTheEmbabelTechniqueRefusesWithoutBuilding`. **A fourth probe passed, and that
      is a hole worth naming:** replacing `BuilderPackTools.validationTools()` with `List.of()` at
      `AgentSpecInvokerFactory:100` leaves the whole suite green. Nothing reads the tool list back off a
      deployed `AgentScope` — `StepSpecAgentScopeBuilder` hands it to its `StepSpecContext` and neither
      `AgentScope` nor `Action` publishes it — so "every deploy carries the validator" is verified by
      inspection of that one expression, not by an assertion. The two deploy tests that now run with the
      list non-empty prove a populated list does not disturb a deploy, which is not the same claim.

      Two consequences for section 4, from the same reading. **Per-flow call attribution is not clean:**
      `newService` still wires `SingleShotProcessSpecGenerator(ai)` unconditionally, so a `--flow pack`
      build also generates the multi-agent candidate Java-side and spends Java-issued calls; the flag
      changes who issues the *single-spec* call only. And **`BUILDER_PACK` is resolved against the process
      working directory** (`Path.of("builder-agent-specs", "builder-spec-one-shot")`), which Surefire sets
      to the module basedir but a real `smith build --flow pack` from elsewhere does not — it fails with the
      named load error. That has to be settled before 5.1 makes `pack` the default. Whether a live model
      actually calls `validatePack` is 4.1's gated case, as designed.

## 4. The four numbers, before the default moves

- [x] 4.1 **Resolution.** On a booted platform, the group named in 2.2 yields at least one callable tool.
      Offline cannot answer this (8.1 proved the role is resolved at prompt time), so it is endpoint-gated
      under `@Tag("live")`, following `eval/ToolGroupBindingTest`'s gated case rather than inventing a second
      convention.
      **Split 2026-11-22 by 1.2 and D7.** Two numbers, not one. Whether the tool exists at all is now
      offline-answerable — 3.2b's `Tool.fromInstance(...)` either yields the validator or it does not, and no
      platform is involved — so assert that offline. What stays endpoint-gated is whether a model, given the
      tool, calls it: keep the `@Tag("live")` case for that half, and report the two separately. A step that
      never calls the validator is the finding 4.3 already names, not a resolution failure.
      **Live half run 2026-11-22, and the answer is "never offered" — so 4.1 stays open.**
      `eval/DeployedBuilderPackCallsValidatePackTest` is gated exactly as `ToolGroupBindingTest`'s
      `OnABootedPlatform` is, deploys the shipped pack through the production seam
      (`AgentSpecInvokerFactory.invokerFor`), and reads the platform's own event stream: an
      `AgenticEventListener` bean records `ToolLoopStartEvent.getToolNames()` per request and
      `ToolCallRequestEvent.getTool()`/`getToolInput()` per call. Measured on this machine's endpoint, one
      model call: **`validatePack offered=false to 1 tool loop(s); called=0`**, with the platform's own two
      lines beside it — `starting tool loop [] max=20`, and `WARN ToolConsumer - Could not resolve tool group
      with role='validate': No tool group matching role 'validate'`. The argument name 4.1 asks for is
      `packDirectory` (already pinned offline by `BuilderPackToolsTest`); it prints beside the count, so a
      call that does happen is attributable.
      **Non-vacuity, by mutation.** Neutering the recorder's `ToolLoopStartEvent` branch in a scratch
      worktree makes the case fail at its control assertion, with `starting tool loop []` still in that run's
      log — so the zero came from the dead listener, not from a run that never happened. Offline the class
      contributes 0 tests (tag plus env gate) and `spring-javaformat:validate` passes on the module.
      **Why it is inert, read off the resolved jar.** `PromptedActionSpec.emit` reads its *own* `tools` field
      (a `List<String>` of names), maps each name through a name→`Tool` map built from
      `StepSpecContext.getTools()` and drops a miss, and `PromptedActionSpecAction` then calls
      `PromptRunner.withTools(resolved)`. So a scope-wired tool reaches the model only if the step names it
      in a `tools:` key, which `ActionStepSpec`'s thirteen components cannot express. D7's route B is wired
      and unselectable — which is also the whole explanation for 3.4's green fourth probe — and
      `BuilderPackTools`'s "reach is the driver's property" javadoc is false as written. The fix is route A,
      which 4.1b now tracks.
      **Resolution measured, and it passes.** With 4.1b's group registered, the same live case re-ran:
      `starting tool loop [validatePack]`, `validatePack offered=true to 1 tool loop(s); called=0`, and the
      `Could not resolve tool group` warning gone from the log. So the declared group yields exactly one
      callable tool on a booted platform, which is what 4.1 asked; whether a model uses it is 4.3's number,
      and on that run it was zero.
      **Offered is asserted now, not assumed (2026-11-22, `3b138ce`).** The case ended in
      `Assumptions.assumeTrue(!validatorArguments.isEmpty(), ...)`, which SKIPPED whenever the model did not
      call the tool — so a resolution regression and a model choice produced the same green. The assumption
      is gone: the case asserts `offered` contains `validatePack` and leaves the call count and the argument
      strings printed for 4.3 to record. That is the right split, because offered is decided by two shipped
      files no model can change — `tools/ValidationToolGroup` and the step's `toolGroups:` line — while
      called is model behaviour this repo deliberately does not pin. Non-vacuity by mutation, live: deleting
      the `validate` entry from `draftAgentSpec.yml` makes the platform log `starting tool loop []` and the
      case fail on the new assertion, and restoring it is green again in 4.5 s. Four live runs, executed not
      skipped: `offered=true`, `called=0` every time; the two 124 s runs were the endpoint timing out at 60 s
      and retrying, not the tool. Note the gate needs **both** `-Dtest.excludedGroups= -Dgroups=live` —
      `-Dgroups=live` alone reports `Tests run: 0` and BUILD SUCCESS, which is an exclusion masquerading as a
      pass. One consequence for the wording below: the argument named here as `packDirectory` is `specJson`
      since the tool-inspects-its-own-draft fix, and 4.3's CLI-path sentence no longer names a call count
      this file did not measure.
- [x] 4.1b **Make the declaration resolve**, by registering a builder-owned `ToolGroup` bean whose role is
      the name the step already declares, with the recipe this repo already contains in frozen POC 7 evidence
      (`poc/poc-7-runtime-adapter/.../ShellToolGroupFactory.java` resolves the mangled Kotlin companion
      factory by *signature* through reflection, then `ToolGroup.Companion.ofTools`, then a plain `@Bean`).
      The resolver's registry is the `ToolGroup` beans in the context that booted the platform, and
      `SmithBuilderApplication` component-scans `com.embabel.smith.builder`, which covers both the CLI and
      `@SpringBootTest`. Then re-run 4.1's live case expecting `offered=true`; `called` stays the model's
      choice and stays recorded rather than asserted. Route B's scope wiring comes out with this, because a
      wired-but-unselectable tool left in the deploy is the misleading half of it.
      **Done 2026-11-22, and it is the fix.** `tools/ValidationToolGroup` publishes a `@Bean ToolGroup`
      whose role is `validate`, carrying `BuilderPackTools.validationTool()` alone — the authored
      `InputSchema`, so the argument is `packDirectory` where `Tool.fromInstance` would have named it
      `arg0`. Its `ToolGroupMetadata` comes from the companion's 5-argument factory located by *signature*
      through `getMethods()`, with a named `IllegalStateException` if that signature ever moves;
      `HOST_ACCESS`, because the validator reads a directory and 1.5.4 offers only `HOST_ACCESS` and
      `INTERNET_ACCESS`. `AgentSpecInvokerFactory`'s scope tool list is back to `List.of()`, and
      `BuilderPackTools.validationTools()` is deleted with the claim it carried.
      `tools/ValidationToolGroupTest` pins the role, the single tool, the `packDirectory` schema, and —
      offline, no endpoint, no `@Tag("live")` — that a booted platform's `availableToolGroups()` lists the
      role. It also reads the shipped pack's own `toolGroups:` block as a list-entry *pattern*, so the bean
      cannot drift from the declaration it exists to honour and prose in the step prompt cannot make it
      pass. **Three mutation probes, each reverted:** renaming the role fails the role test and the
      pack-coupling test; demoting the `@Bean` blinds the booted-platform test; swapping in
      `Tool.fromInstance` trips the `arg0` assertion. `core/BuilderPackTierTest` is untouched and green —
      `validate` is in none of `TrustTier`'s allowlists, so registering the group moved no tier.
      **Two things it does not settle.** The `--flow pack` CLI path has not been exercised end to end
      since — the live case drives `AgentSpecInvokerFactory` directly, and 4.2's runs are the first CLI
      exercise of the group. And a *misspelled* group name still resolves to nothing with only the
      `ToolConsumer` WARN, because `AgentSpecValidator` inspects no `toolGroups` field at all; that stays
      `docs/12-backlog.md`'s open item rather than being written up as closed here.
- [x] 4.2 **Parity.** Run both flows over the same task set and record both rates plus the difference. No
      verdict in the artifact — two numbers and a delta, so a reader can weigh it.

      **Both rates, and the delta, with no verdict attached.** Three inputs, both arms, one build each, on
      2026-11-22 between 09:05 and 09:52 UTC against HEAD `192b7d3`, model `Qwen-instruct` through the POC
      endpoint, the two arms of an input run one after the other so they never shared the endpoint:

      | input (`--docs`) | `--flow java` | `--flow pack` |
      |---|---|---|
      | `prod/embabel-smith-builder/src/test/resources/blueprints/parcel-claims` | **OK** | CRASH — goal JSON unparseable |
      | `examples/blueprints/Sentinel` | BLOCKED at `security` | CRASH — goal produced no output, after the 1800 s cap |
      | `examples/blueprints/TradingAgents` | **OK** | CRASH — goal JSON unparseable |

      **Built: `java` 2/3 = 66.7%, `pack` 0/3 = 0.0%. Delta −66.7 points.** The java arm's single miss is a
      security block, so its generation stage succeeded three times out of three. All three pack misses are
      the pack flow's own output boundary, not a downstream gate: twice the goal returned text Jackson
      refuses to read (`Unexpected character ('j' (code 106): was expecting double-quote to start field name
      … line: 1, column: 2`, and the same at `('m'`), which is the `AgentSpecJson` value type's single
      `json` **string** property answered as a rendered map — `{json={"manifest":{…` — instead of a string;
      once the goal produced no output at all after 30 minutes. Each arm was
      `timeout -k 30 1800 java -cp <builder classes and their dependencies> com.embabel.smith.builder.SmithBuilderApplication build --docs <abs input dir> --target agent-spec --technique single-shot --flow <arm> --out folder:<out>/<input>/<arm>/published --overwrite`,
      run from `prod/embabel-smith-builder`.

      **Superseded on the built count, not on the verdict (2026-11-22).** After `99f9cc0` and `e5e3b04` the
      same harness on the same three inputs gives `pack` **1/3** — `parcel-claims` publishes, `Sentinel`
      blocks at security where the java arm also blocks, `TradingAgents` blocks at the eval gate — and the
      goal-JSON crash that caused all three misses above appears 0 times in all three logs. 4.3's
      re-measure carries those numbers. Parity still fails: 1 of 3 against a bar of 2 of 3 is a fail, and
      what changed is the *reason* — the misses are downstream gates now rather than the pack flow's own
      output boundary, which is a better position to be in and not a passing one.

- [x] 4.3 **Cost.** Calls per build on both flows, with the validator calls the step actually made counted
      separately. A pack flow that never calls the validator is a finding to record, not a success to
      celebrate: it means the step is the Java flow again with extra ceremony.

      **Counted, from the same six runs.** Answered model calls per build (`received LLM response`), calls
      spent without an answer (`Retry attempt` / `timed out after`), and validator calls
      (`calling tool validatePack(`) kept in their own column:

      | input | java answered | java spent | java validator | pack answered | pack spent | pack validator |
      |---|---|---|---|---|---|---|
      | parcel-claims | 10 | 4 | 0 | 1 | 0 | 2 |
      | Sentinel | 9 | 8 | 0 | 1 | 31 | 116 |
      | TradingAgents | 14 | 24 | 0 | 1 | 4 | 3 |

      The pack flow did **not** stay validator-blind, so the "Java flow with extra ceremony" failure mode
      this task warned about is not the one that happened. What happened is worse in a different direction:
      the step calls the validator, and calls it on arguments it invents. Across the three pack runs the
      arguments were `.`, `/tmp/nonexistent_probe_dir`, the spec's own name as a bare relative path, that
      name with `./` and with a trailing slash, and one path to a `manifest.yml` *file* — and 92 of the 93
      results logged on the Sentinel arm's first pass were the same finding, `LAYOUT_MISSING_MANIFEST`.
      The reason is structural and it is in this repository's control: `BuilderPackTools.validationTool()`
      takes one argument, `@param packDirectory the directory holding the agent spec to validate`, and
      neither that description nor `builder-spec-one-shot/steps/draftAgentSpec.yml` names a directory the
      model could know. There is no draft on disk for it to point at while the step is running — the draft
      is the model's own answer, and the Java flow's equivalent check (`BuilderService.validateDraft`,
      called on the `CompiledDraft` in hand) has the spec itself, not a path. So the step is offered a tool
      it cannot aim, spends its 20-iteration tool-loop budget guessing, and on the Sentinel arm burned 31
      timed-out attempts before producing nothing. The java arm called the validator zero times in all three
      runs, which is the expected shape there: its validation is a stage, not an offer.

      **Re-measured 2026-11-22 after the two fixes, same harness, same three inputs.** The table above
      measures a tree that no longer exists, so it is kept as history rather than corrected in place. Two
      commits changed what a validator call can do: `99f9cc0` replaced the directory argument with
      `specJson`, the spec the step is about to return, and `e5e3b04` put the reply envelope into
      `draftAgentSpec.yml`'s own prompt, so the pack's prose and the schema the platform contributes
      describe one object instead of two. Both arms ran from the merged tree
      (`prod/embabel-smith-builder/target/classes` of the join commit, `--flow` the only difference, publish
      targets per input and arm, exit code captured before any grep):

      | input | flow | verdict | stage | answered | spent | validator calls | argument the model sent |
      | --- | --- | --- | --- | --- | --- | --- | --- |
      | `parcel-claims` | java | OK | done | 10 | 4 | 0 | — |
      | `parcel-claims` | pack | **OK** | done | 5 | 4 | 2 | `{"specJson": "{\n  \"manifest\": …` |
      | `Sentinel` | java | BLOCKED | security | 9 | 8 | 0 | — |
      | `Sentinel` | pack | BLOCKED | security | 31 | 50 | 6 | `{"specJson": "{\"manifest\":{\…` |
      | `TradingAgents` | java | OK | done | 14 | 24 | 0 | — |
      | `TradingAgents` | pack | BLOCKED | eval | 5 | 26 | 0 | none — the step declined the offer |

      The java rows are carried forward from the six runs above, not re-run: nothing in the two fixes
      touches the java path's generation, and `eval/AgentSpecInvokerFactory` — which the fix does change —
      is also the eval deploy seam, so a java re-run could legitimately move. Stated as a caveat rather
      than as parity.

      **What the fixes demonstrably did.** The crash class is gone: `did not return valid agent spec JSON`
      appears 0 times in all three pack logs, against three crashes before, and `LAYOUT_MISSING_MANIFEST`
      appears 0 times, against 92 of 93 results on Sentinel's first pass. Every call the model made carried
      its own draft rather than a guessed path, on both argument serialisations — the pretty-printed
      `{\n "manifest"` of one model answer and the compact `{"manifest":{` of another. `parcel-claims` now
      **publishes** on the pack flow, and its two calls are the loop working: the first returned a real
      `DANGLING_TYPE_REF` and the second returned `No findings: the agent spec is well-formed.`
      `Sentinel` reaches the security gate and blocks there, which is the same verdict the java arm reaches
      on that input.

      **What it did not buy.** Built is **1 of 3**, short of the 2-of-3 the flip asked for, so 4.5's
      decision stands and `--flow pack` remains non-default. On the ≤3-calls-per-build bar Sentinel is
      over at 6: repeat calls on an unchanged draft still are not deduped, which `docs/12-backlog.md`
      carries as its own open item. And `TradingAgents` is the honest counterexample — the step was offered
      the validator (`starting tool loop [validatePack]`) and declined it, after which the pipeline found
      sixteen `DANGLING_TYPE_REF` findings at eval and blocked at the score gate with 0.000. That is the
      shape 4.3 warned about: the same input where the offer is free, the model can ignore it and the pack
      flow costs more than java, not less.

      **On the two timeouts, so the record does not read as a code result.** The first re-run capped both
      Sentinel and TradingAgents at the harness's 1800 s with 62 spent-without-answer calls each. A trivial
      completion against the same endpoint returned in 0.01–0.56 s immediately afterwards, so the ceiling
      was the clock and not a dead model; both were re-run at 2700 s and completed with the verdicts above.
      Sentinel's 50 spent calls against 31 answered is the endpoint's own cost profile on a large blueprint,
      and the same pattern appears in the java arm's numbers, so it is not a pack-flow property.

- [x] 4.4 **Containment.** The scorer-out guard from 8.4 is green and names what it scanned (steps,
      characters) so its silence is measured rather than assumed.

      **Green, and the scan is named.** `./mvnw -o -pl prod/embabel-smith-builder
      -Dtest=ScorerPromptBoundaryTest -DfailIfNoSpecifiedTests=false -Dcheckstyle.skip=true test` at 09:23
      on 2026-11-22 against HEAD `192b7d3`: 4 tests, 0 failures, 0 errors, 0 skipped, 0.395 s. What it
      scanned is **13 step prompts and 17,549 characters**, recounted against this tree rather than quoted
      from the class javadoc: the four packs' prompt strings sum to 17,537 characters, and the guard's
      haystack is those strings joined with `"\n"`, which adds 12 separators, so the guard's own figure is
      17,549 and the javadoc agrees with the tree. The floors it enforces are ≥13 steps and ≥10,000
      characters, so a renamed pack directory or an unread `steps/` fails the scan-coverage test rather than
      making the guard quietly green, and the positive control is green in the same run — a planted verbatim
      copy reads at or above the 0.6 threshold, and so does `grade_eval` with its central term renamed. The
      guard's zero is a measurement, not a broken normaliser.
- [x] 4.5 Record all four in this file with the run that produced them. A gate with no number beside it is
      not a gate.

      **All four recorded above with their runs.** 4.1 and 4.1b from the live case at 08:2x and 08:58 on
      2026-11-22 against `ea2b1fe` and `192b7d3`; 4.2 and 4.3 from the six parity builds between 09:05 and
      09:52 against `192b7d3`, whose full command line is quoted in 4.2 so the runs are reproducible without
      the driver script that launched them; 4.4 from the guard run at 09:23 against `192b7d3`. The four
      gates do not agree:

      | gate | result |
      |---|---|
      | 4.1 resolution | **pass** — the declared role resolves and the tool is offered |
      | 4.2 parity | **fail** — pack 1/3 built against java 2/3 (re-measured 2026-11-22, 0/3 before the two fixes) |
      | 4.3 cost | **fail** — repeats are not deduped (6 calls on one build) and on one input the step was offered the validator and declined it, after which eval found sixteen dangling types |
      | 4.4 containment | **pass** — 13 step prompts, 17,906 characters at the merged tree, positive controls green |

## 5. The flip

- [x] 5.1 Flip the default to `pack` in its own commit, citing 4.1–4.4 in the message. If any of the four
      failed, do not flip; record which one and why, and leave `--flow java` as the default.

      **Not flipped, by this task's own branch.** Two of the four failed: 4.2 (the pack flow built none of
      the three inputs, and its failures are at the pack flow's own output boundary rather than at a gate
      downstream) and 4.3 (the validator the flip would put on the shipped path is a tool the drafting step
      cannot aim, so on one build it spent 116 calls and 31 timed-out attempts returning the same finding
      each time). `--flow java` remains the default and `BuildFlow` is untouched by this change. The pack
      flow stays reachable behind `--flow pack`, which is what section 3 shipped, so the measurement
      surface survives without the shipped path depending on it.
- [ ] 5.2 Update `docs/08-builder.md` for the new default — the build's driver is a durable concern, and the
      page is where the contract lives.

      **Not triggered.** There is no new default: 5.1 took its do-not-flip branch, so the sentence this task
      would write is the one `docs/08-builder.md` already carries. Two separate reasons it stays open rather
      than being ticked: if the flip is ever taken this becomes live again, and the file is currently held
      by another session's uncommitted native-image section.
- [ ] 5.3 Note the change in the wiki. A vocabulary or flow change is a documentation change there: 44+
      cross-linked pages, so it needs a wiki lint and a re-publish afterwards.

      **Local half done, one human step left.** The wiki's *reach* claims were corrected first, where 4.1b
      made them false — `.wiki/features/smith-builder.md` (the validator now arrives through the registered
      `validate` group, with both measurements) and `.wiki/features/trust-tiers.md` (the tree now has two
      `ToolGroup` beans, and an unresolvable name still lands at `T4_PLATFORM`). The flow note this task
      also asked for then landed at `d72b5fd`, which supersedes the earlier reading here that there was
      nothing to note because the default did not move: the flag is a shipped surface whether or not it is
      the default, and `.wiki/features/smith-builder.md` now carries a `The pack flow (--flow pack)`
      section — the usage block and option bullets with the strict-parse exit 2 and the pre-spend refusal
      of `pack` with `--technique embabel`, `generators()` dropping the EMBABEL entry so no Java fallback
      exists, the `{json=<text>}` goal read-back and the `goalOutputText` fix that ended the crash class,
      the load-bearing reply envelope pinned by `BuilderAgentSpecPackTest` reading the key from the type,
      and the re-measured verdict (pack 1 of 3 against java 2 of 3, crash class at zero occurrences, flow
      non-default). `.wiki/index.md` gained the envelope rule and the who-issues-the-call row, and its
      `last_commit` was deliberately left at `1b95d47` because the pass covered only this surface. Checked
      for both lint failures a prose edit causes: no duplicated heading, every `[[link]]` resolves, and the
      five added `source_paths` exist. `git-wiki` ships no lint script and the `Makefile` has no `wiki`
      target, so the counts quoted in the index note (45 pages, 0 broken links, 0 orphans, 0 stale, 0 dead
      paths) come from an ad-hoc pass over `.wiki/**/*.md`, not a committed tool. What is **not** done is
      the re-publish: `wiki-pages.yml` fires on a push to the default branch, and this change does not
      push.
- [x] 5.4 Report the POC-facing consequence: POC 27's outer loop mutates builder pack step prompts, and once
      the shipped path reads one of those packs, a published candidate changes what `smith build` does. State
      that in `docs/10-self-improvement.md` if it becomes true, because it is the first time this repository
      has had a mutable surface on its shipped path.

      **It did not become true, and `docs/10-self-improvement.md` says so.** The shipped path still calls
      the Java generators, so a POC 27 candidate cannot change what an ordinary `smith build` does — which
      is what that page's Refinement paragraph already states. What did change is narrower and is recorded
      there rather than smoothed over: reach. Two builder packs declare `validate` and
      `builder/tools/ValidationToolGroup` now registers that role, so a candidate built from either is
      *offered* the builder's validator where it was offered nothing. A candidate still cannot *add* a
      group, because `Refiner.withPrompt` copies the other twelve components through untouched, so the
      mutable surface is unchanged and the shipped path is unchanged.


## 6. Deliberate non-changes

- [x] 6.1 The Java stages in `BuilderService` stay. Retiring them is a later change with its own argument,
      and deleting them here would make the parity numbers unreproducible.
- [x] 6.2 `BuilderAgent` remains excluded from component scanning (`SmithBuilderApplication:40`). Design D5
      records why making it planner-driven is not the switch; leaving the filter in place is what keeps that
      decision visible.
- [x] 6.3 The multi-step packs are not the shipped default. POC 24's live result stands.

      **Verified 2026-11-22, by inspection of the state rather than of this plan.** **6.1** —
      `git diff 6ca4599 HEAD -- .../BuilderService.java` is empty: section 3 did not touch the file, and its
      Java stages are all still there in order (`compileFromDescription` at `:134`, then
      `validateDraft`, `writeAgentSpec`, `generateProcess`, `refine`, the score gate, the scan, the stamp,
      the publish). The parity numbers 4.2 still owes are therefore reproducible against the same stages
      that produced them. **6.2** — `SmithBuilderApplication:39-40` still carries
      `@ComponentScan(excludeFilters = … classes = BuilderAgent.class)`, with the `BuilderAgent` import at
      `:13` there to be read alongside it. **6.3** — four packs ship
      (`builder-spec-one-shot` 1 step, `builder-skill-from-process` 3, `builder-process-composition` 4,
      `builder-single-spec` 5) and the only one named anywhere in `src/main` is
      `builder-spec-one-shot`, at `PackDrivenAgentSpecGenerator.BUILDER_PACK`; the three multi-step packs
      appear in no production reference, and `builder-single-spec` is reached only from POC 27's improvement
      loop, not from a build. So the shipped default remains the one-step pack POC 24 measured.
