# Tasks

Paths are relative to the repository root. Every path and line number below was opened while writing
this plan; items marked **(inferred)** are reasoning the reading supports but did not show directly.

## 1. Precondition — the preceding change landed

- [x] 1.1 Confirm `builder-owns-improvement-loop` is complete before starting anything here. Check, do
      not assume: `prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/improve/` is gone
      (it held all twelve files when this plan was written; it is gone now),
      `prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/improve/`
      holds them, and `grep -rn "CandidateStore" prod/` is non-empty (it was empty then; seven files under
      `prod/*/src` name it now). The trace sink
      half is **already** in the tree — `smith-common/src/main/java/com/embabel/smith/common/trace/`
      already holds `TraceRecord.java` and `TraceStore.java`, and `prod/embabel-smith-runtime/.../runtime/trace/`
      already holds only `JsonlTraceStore`, `TraceRecorder`, `TraceRecordJson` — so report that task as
      already done rather than redoing it.

      **Result, 2026-11-21:** all three checks pass, and so does the one this task does not ask for —
      `import com.embabel.smith.studio.improve` is zero in prod and in POC 27. "Complete" is **not** the
      right word for the preceding change, though: it stands at 20 of 24 with 6.1 (full POC reactor),
      6.2 (studio frontend build) and 6.4 (the docs and wiki pass) open and 2.4 ruled not achievable as
      written. What is satisfied is the **structural** half — sections 1 to 5, which is what sections 2 to
      8 below build on. 6.4 is the one open item that outlives this change rather than blocking it:
      `docs/10-self-improvement.md:3` still places the loop in `studio/improve`, and items 9.2 and 9.3
      below are where that gets fixed.
- [x] 1.2 Confirm the builder gained no dependency on `embabel-smith-runtime`: read
      `prod/embabel-smith-builder/pom.xml` (its declared dependencies today are `smith-common`,
      `embabel-smith-specsource`, `embabel-smith-skillevaluator`, the `embabel-agent-*` starters,
      `jackson-*` and `picocli`) and run
      `grep -rn "com.embabel.smith.runtime" prod/embabel-smith-builder/src/main` — expected zero.

      **Result, 2026-11-21:** zero in `src/main` and zero in `src/test`. The declared list is as stated,
      plus `embabel-agent-spec` and `embabel-agent-eval`, which is what makes design D9's claim — the
      builder can already deploy and execute packs — true without a module edge.

## 2. The new seam: ProcessSpec into the skill pipeline

- [x] 2.1 Read the two skill entry points before writing anything:
      `prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillBuilderService.java:101`
      (`build(String description, PublishTarget target, BuildOptions options)`) and
      `skill/SkillGenerator.java:56` (`generate(String skillName, String description)`). Record that
      `grep -rn "ProcessSpec" prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/`
      returns **zero** hits today — that is the gap, and this task is the evidence it existed.
- [x] 2.2 Add `skill/SkillBuilderService.buildFromProcess(ProcessSpec process, PublishTarget target,
      BuildOptions options)` in the same file, deriving one skill description per stage and delegating to
      the existing `build(String, PublishTarget, BuildOptions)`. It must not duplicate a gate: the
      inspection and with/without-skill gates stay where `build` already runs them. Verify by reading the
      new method's body and confirming it contains no new gate call — the only new logic is the
      stage-to-description step.
- [x] 2.3 Decide and record where the stage description comes from. `common/model/ProcessSpec.java` holds
      the member `AgentSpec`s, and each `common/model/ActionStepSpec.java` carries `name`, `description`,
      `inputTypeNames` and `outputTypeName` — so a stage description is assembled from those fields in
      Java, with no model call. State the exact template you used in the method's javadoc. **(inferred)**
      that these four fields are sufficient to describe a stage to `SkillGenerator`; the offline test in
      2.4 is what tests that, not this task.
- [x] 2.4 Add `skill/SkillBuilderServiceFromProcessTest` in
      `prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/skill/`, running
      `ProcessSpec` → per-stage descriptions → skills end to end with a fake `SpecPromptCaller` (the
      seam `SkillBuilderService`'s constructor already takes, `:79`). Assert the **number** of skills
      equals the number of stages and that two distinct stages produced two **distinct** descriptions —
      the same distinctness discipline as 6.4, applied offline.

## 3. The three builder packs

- [x] 3.1 Create `prod/embabel-smith-builder/builder-agent-specs/` with three
      directories laid out like `agent-specs/explainer/` (read that directory first: `manifest.yml`,
      `goal.yml`, `steps/*.yml`, `types/*.yml`):
      - `builder-skill-from-process/` — the skills-from-a-process-specification flow, consuming the 2.2 seam.
      - `builder-process-composition/` — the many-agents-collaborating flow.
      - `builder-single-spec/` — one AgentSpec, multiple steps.
- [x] 3.2 Transcribe, do not invent, the two packs whose Java already exists. `builder-single-spec`
      mirrors `core/BuilderAgent.java`'s cognitive stage (`compileFromDescription`, `:90`) — **not**
      `validateDraft` (`:130`) or `writeAgentSpec` (`:142`), which stay host Java per design D3 — and its
      compile step prompt carries the instructions at `core/SingleShotAgentSpecGenerator.defaultPrompt()`
      (`:60`). `builder-process-composition` mirrors
      `core/SingleShotProcessSpecGenerator.generateProcess(String)` (`:66`) and carries
      `defaultPrompt()` (`:61`). Report the diff between each pack's prompt and the `defaultPrompt()` it
      came from; a wording change with no reason behind it is a finding.
- [x] 3.3 Every step declares a named `DynamicTypeDef` under the pack's `types/` for its output — never
      `com.embabel.agent.spec.model.BackgroundMessage` — because
      `core/DraftToAgentSpec.java:61,67` remaps a step whose goal output is non-returnable onto it (the
      constant at `:106`), and its set at `:101-103` includes `java.lang.String`; its polymorphic `parts`
      cost POC 27 its run. Verify with
      `grep -rn "BackgroundMessage" prod/embabel-smith-builder/builder-agent-specs/`
      returning zero, and report the count.
- [x] 3.4 Every step that declares an input type interpolates it. `agent-specs/explainer/steps/extractTopic.yml`
      is the model (`User request: {{userInput.content}}`); `poc/poc-27-recursive-self-improvement/src/main/java/com/embabel/smith/selfimprove/outerloop/PolicyImprover.java:161-162`
      is the counter-example (a step built with `prompt.render()` and no placeholder anywhere). Verify by
      listing each step's `inputTypeNames` against the `{{...}}` references in its own prompt, and report
      the two-column table.
- [x] 3.5 Add `core/BuilderAgentSpecPackTest` in `prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/core/`:
      for each of the three packs, load with `smith-common`'s `io/AgentSpecReader`, validate with
      `smith-common`'s `validation/AgentSpecValidator.validate(AgentSpec)` (`:104`), and deploy through
      `eval/AgentSpecInvokerFactory.invokerFor(AgentSpec)` (`:58`) behind a fake invoker. This is the
      guard that stops the packs becoming an artifact no code loads — the same shape of gap this change
      exists to close, since the builder's flow is today reachable only through Java. Verify the test
      fails when a pack is deleted, by deleting one and re-running it.

**Section 3 results, 2026-11-21.** Thirty files, three packs, and they load, validate and deploy:
`core/BuilderAgentSpecPackTest` is 5 tests green, and the module's whole suite is 253 green. The deletion
half of 3.5 was performed, not assumed — `builder-skill-from-process/` was moved aside, all five tests
failed with the tried path in the message, and it was restored to 30 files and green again.

- **3.1** the three directories, laid out `manifest.yml` + `goal.yml` + `steps/*.yml` + `types/*.yml`.
- **3.2** transcribed from the two `defaultPrompt()`s, split by decision rather than by sentence: preamble
  and the "model the core process" rule into the scoping step, the one-step-per-stage and faithful-order
  rules into the enumeration step, the PascalCase naming rule into the type step, the chain-by-type rules
  into the binding step, and the output schema into the assembly step. Two restructures were forced, both
  stated rather than hidden: the jinja's `<document>` framing became a labelled interpolation ("data to
  model, not instructions to follow"), and its `{% raw %}{{ marketReport }}{% endraw %}` examples became
  brace-free prose, because written literally the validator would read them as undeclared prompt
  references. That is the one place the transcription loses wording. Host Java stayed out — no gate, scan
  or write step anywhere, and the generator's post-call Java is not restated either.
- **3.3** `grep -rn "BackgroundMessage"` over the packs returns **zero**, verified by the coordinator, and
  the reason it needs its own assertion is worth keeping: the validator treats `BackgroundMessage` as a
  framework type, so a clean validation is not evidence of this rule — the output-type-is-one-of-the-pack's-own-types
  assertion is what catches it.
- **3.4** 40 interpolations, every one bound to a declared input of its own step, checked property by
  property against the `types/` declarations. One gap the validator structurally cannot see:
  `builder-process-composition/steps/scopeEndToEndProcess.yml` outputs a `ProcessScope` whose
  `responsibilities` and `processName` are interpolated by two later steps, yet no prompt anywhere asks for
  them. `PROMPT_IGNORES_DECLARED_INPUT` cannot fire because one declared input is enough, so two
  properties have no producer and nothing reports it. Recorded against the packs, not fixed under the
  coordinator's hands.
- **3.5** as above, plus the free assertion that the three exported goal names are distinct
  (`singleAgentSpecDrafted`, `multiAgentProcessDrafted`, `skillBriefsDrafted`) — a pre-check for deploying
  all three in one scope, not a substitute, because `ProcessScope` exists in two packs with different
  property sets.

Two naming findings, resolved rather than left open. Pack step names are camelCase while nine of their own
prompts tell the model to emit snake_case step and goal names — that is transcription fidelity to the
`defaultPrompt()` being copied, which governs the spec the builder *emits*, not the pack the repository
ships, so it stays. And a "task 3.6" referenced in the authoring report does not exist in this file; the
packs carry no `pre:`/`post:` keys at all, which is what D11 predicts and what 8.5 will have to introduce
rather than assert.

**Every optional field is written, empty where it is empty** (added on review, 2026-11-21). Each of the 12
action steps declares `pre: []`, `post: []`, `cost: null`, `value: null`, `canRerun: null`,
`nullable: null` and `toolGroups: []`, and each manifest declares `defaultModel: null`, so a reader of a
pack sees the whole surface a generation could widen rather than only the half that is in use — which is
the point of a mutable surface. The goals already declared all five fields `GoalStepSpec` has. Lists are
`[]` and scalars `null` because both mean *declared, nothing set* while asserting no value: writing
`canRerun: false` would be a value, and a value the platform may treat differently from an unset field.
Verified against the loader rather than assumed — the module's 253 tests are green with the fields present.
One limit worth recording so nobody writes a futile guard: `ActionStepSpec`'s compact constructor maps
`null` to `List.of()`, so once a pack is parsed an omitted field and an explicit empty are the same object,
and no assertion on a loaded pack can tell them apart. The check is textual.
Check: `grep -L "toolGroups:" prod/embabel-smith-builder/builder-agent-specs/*/steps/*.yml` prints nothing.

## 4. POC 27's second leg retargets onto the builder's own packs

- [x] 4.1 Replace the hand-built candidate in
      `poc/poc-27-recursive-self-improvement/src/main/java/com/embabel/smith/selfimprove/outerloop/PolicyImprover.java`
      (`candidate(...)`, `:161-162`) so a candidate is a **builder pack with mutated step prompts**, not a
      one-step pack built in Java. Mutate only the `prompt` argument of `common/model/ActionStepSpec` —
      the discipline `prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/improve/Refiner.java:157-160`
      already encodes (it passes every other field through unchanged). The path is the builder's since
      2026-11-21, when `builder-owns-improvement-loop` moved the engine out of the studio; `withPrompt`
      kept its line numbers in the move, verified the same day. Verify by asserting a candidate
      differs from its parent in step prompts and in nothing else: same step names, same
      `inputTypeNames`, same `outputTypeName`, same types.

      **Result, 2026-11-22:** `candidate(...)` now returns `Candidate(variantId, P, null)` where `P` is
      `builder-single-spec` read off disk by a new `outerloop/BuilderPacks` (`load` via `smith-common`'s
      `AgentSpecReader`, walking up from the working directory for `prod/embabel-smith-builder/builder-agent-specs`
      the way poc-14 and poc-21 walk for `agent-specs/`; a pack that is not there is a named refusal, not a
      fallback to a pack assembled in Java), renamed to the variant id, with **one** step prompt rewritten.
      The step is `scopeCoreProcess` — verified against the pack's own file to be its entry step, the one
      declaring `UserInput` and interpolating `{{userInput.content}}`, so an eval task's prompt still reaches
      the candidate. The policy text is *appended* to that step's own instruction, which is what keeps the
      interpolation. `builder-single-spec` rather than the new `builder-spec-one-shot`: 4.1 rules out a
      one-step pack. Chosen over `Refiner.withPrompt` because that method is `private static` and the public
      `refine` needs failure traces and a `PromptRewriter` — reusing it means a prod edit in a module another
      change owns, so the pass-through is restated field-for-field in `BuilderPacks.withStepPrompt` instead,
      against `ActionStepSpec`'s canonical constructor (13 components, order verified from the declaring
      source). One deviation to know about: `ActionStepSpec`'s compact constructor maps a null
      `pre`/`post`/`toolGroups`/`inputTypeNames` to `List.of()`, so a mutation that drops one of those to null
      is invisible to this test by construction — the same fact 3.x recorded from the other direction.
      Verified: `BuilderPackCandidateTest` (5 tests, new class — no outer-loop test had ever inspected the
      candidate pack, which is how a hand-built one-step pack sat in `candidate(...)` unnoticed) plus the
      module's 142 tests green, run in a clean worktree of `0fde7e8` so other in-flight edits stayed out.
      Mutation-checked twice. Replacing the step's instruction instead of appending to it fails **7 tests
      across 5 classes** — the three in the new class plus `OuterGenerationRecordTest`, `OuterLoopProcessTest`
      and two `NoiseFloorGateTest` cases, so the interpolation rule is held by the suite and not only by its
      own test. The second probe, writing a `toolGroups` entry onto the mutated step, **passed at first**:
      the parent/child comparison cannot see a mutation applied identically to every candidate, because both
      sides of it go through the same mutator. That is now closed by a second assertion comparing each
      candidate with the shipped pack, where the only permitted differences are `pack.name` — lineage, since
      every variant carries its own identifier as its pack name — and `scopeCoreProcess.prompt`; the same
      probe then fails on `what b27-inner-seed changed in the shipped pack`. Nothing was executed against an
      endpoint.

- [x] 4.2 Score candidates through the existing path, not a new one:
      `prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/eval/AgentSpecInvokerFactory.invokerFor(AgentSpec)`
      (`:58`) behind `outerloop/MeteredInvokerFactory` (`:54`), into the gate chain's
      `gate(AgentSpec, double baseline)`. Confirm the builder needs nothing new for this — `AgentSpecInvokerFactory`
      already holds an `AgentPlatform` (`:50`) and deploys a `ProcessSpec` as one scope (`:69`, javadoc at
      `:30-36`), so a multi-pack candidate is runnable without the runtime module. **(inferred)** that
      deploying all three builder packs into one scope does not collide on goal names; the deploy in this
      task is the test — if the platform refuses a duplicate goal, record that as a finding and rename.

      **Result, 2026-11-22:** the builder needed nothing new, and this task turned out to be a confirmation
      rather than a wiring change. `AgentSpecInvokerFactory` already holds an `AgentPlatform`, already writes
      the pack to a temp directory and deploys it as one scope, `OuterLoopProcess.main` already passes that
      factory, and `Referee.score` already calls `gate(candidate.spec(), baseline)` — so the only edit was a
      javadoc note on `MeteredInvokerFactory`. What changed is what sits *behind* a counted call: the counter
      still counts one per `invoke(String)`, which is what `Budget.costPerCandidate` was derived against, but
      on `builder-single-spec` each counted invocation now drives five model calls. That gap is recorded as
      the budget's to carry, not papered over by inflating a counter that is also the run's evidence, and it
      means a live pass spends step-count × the declared per-candidate calls.
      On the **(inferred)** goal collision: it does not arise on this path — a candidate is one pack in one
      scope, not three deployed together — and for the three-together case
      `builder/src/test/.../eval/CollaborationStructureIsFixedByConstructionTest` already proves offline,
      against a mock `AgentPlatform`, that all twelve steps and three distinct goal names deploy into a single
      scope. No rename was needed. The quieter collision in the same area is a type one:
      `eval/AgentSpecTypeMapper.allRuntimeTypes` dedups domain types by name with first-wins, and
      `builder-single-spec` and `builder-process-composition` both declare `ProcessScope` with different
      property sets — not on this path either, and deliberately not "fixed" by renaming a type.
      The gate accepts a mutated pack on its deterministic arms: validation passes because the entry prompt
      keeps its interpolation and the exported goal and types are the shipped pack's, and the scan reaches at
      most REVIEW because `validate` is not a high-privilege group and neither the pack nor the policy text
      carries a CRITICAL marker; only BLOCK rejects, so the comparison is what decides. Asserted offline in
      `theGateTakesAMutatedPackPastItsDeterministicArms` with the unmutated on-disk pack as the control, so the
      pair cannot pass on two empty findings lists. Not executed against an endpoint.
      Finding worth carrying: the frozen six are question→answer tasks, while a candidate now answers by
      drafting a spec through five steps, so a **live** pass over `benchmark/tasks` would score drafted spec
      text against defect-answer `require`/`anyOf` lists. Offline scoring is unaffected; the live runs in 6.3
      and section 9 need to read this before they interpret a number.
- [x] 4.3 Keep the inner loop as it is. Its flat derivative is a property of the 26-line corpus
      (`poc/poc-27-recursive-self-improvement/src/main/resources/corpus/traces/`, 16 tuning + 10 held-out,
      counted with `wc -l`), not of its machinery. Record this as a deliberate non-change rather than
      leaving it implicit.

## 5. Mutable surface, written down

- [x] 5.1 Amend `poc/poc-27-recursive-self-improvement/src/main/resources/evidence/MUTABILITY.md` with a
      section naming the builder packs as the mutable surface and listing the gates as fixed **with the
      structural reason**: `smith-common/src/main/java/com/embabel/smith/common/validation/AgentSpecValidator.java:212`
      rejects a prompt-less step, so a deterministic gate cannot be expressed inside a pack at all, and a
      mutation that edits only pack files cannot reach host Java. Do not restate the eleven existing fixed
      entries; cite them.

      **Result, 2026-11-21:** section `## Mutable — the builder's own AgentSpec packs` added between the two
      existing tables — three-row pack table, the placement reason (`AgentSpecReader.read(Path)` lists
      `steps/` and `types/` with `Files.list`, so a jar-packaged directory would not be addressable), the
      surface defined by code (`Refiner.withPrompt` passes every field but the sixth), the structural gate
      reason, the `toolGroups`/`pre`/`post` reach-not-code distinction, and `BuilderAgent` named as not
      mutable (`compileFromDescription` `:91` mirrored; `validateDraft` `:131` and `writeAgentSpec` `:144`
      are host Java). The eleven existing rows are byte-identical; the new section cites them. Two plan
      citations were wrong and are corrected here rather than in the file: the prompt-less rejection is
      `AgentSpecValidator.java:216` (`PROMPT_MISSING`), `:212` is a section comment; and `ProcessValidator`
      lives in `smith-common/`, not `prod/`. Nothing parses the file — `SelfImproveApp.mutability()` counts
      non-blank lines and `SandboxBreachRejectionTest` asserts two row names that are untouched — so the
      two-column convention was kept anyway, 31 rows verified.
- [x] 5.2 Add the named gates to that fixed list individually: `validation/AgentSpecValidator.validate`
      (`:104`), `validation/ProcessValidator.validate` (`:58`), `skill/SkillMdValidator`, the gate chain,
      and `prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/publish/`. Verify the file
      states which of the three packs are mutable and that `BuilderAgent`'s Java is not.

      **Result, 2026-11-21:** four rows appended, each with its own reason — `AgentSpecValidator.validate`
      (`:86` on disk, `:107` in memory, and cross-referenced to `GateChain.gate:115`),
      `ProcessValidator.validate` (`:58` confirmed, run by `BuilderAgent.writeProcess:180`,
      `BuilderService:236`, `ProcessRepair:62`), `SkillMdValidator` (`:31`, sole prod caller
      `SkillBuilderService:186`) and the whole `builder/publish/` package (collision gate
      `FolderPublisher:46`; `GitPublisher:66` commits, `:72` pushes only with `--push`). **The gate-chain
      row was declined**, not forgotten: the table already carries a `GateChain` row and an `EvalScorer`
      row covering both halves of the design's phrase "the gate chain and its scorer", so a fifth row would
      restate one of them; the validator row reaches the chain by cross-reference instead. Followed from 5.1
      because the diagram at the top of the file still listed only the pre-reframe surfaces — its MUTABLE
      block now names the builder packs and its IMMUTABLE block the three validators and the publisher.
      Also corrected here, both stale since the engine moved: `design.md` D3 and `proposal.md` cited
      `studio/improve/Refiner.withPrompt`; it is `builder/improve/`, line numbers unchanged.

## 6. Measurement validity — the four guards, before a paid run

- [x] 6.1 **Headroom.** Author task files under
      `poc/poc-27-recursive-self-improvement/src/main/resources/benchmark/tasks/` that the **unmutated**
      builder packs fail, following `b27-t02-quality-floor.json`'s shape (`id`, `stage`, `prompt`,
      `require`, `anyOf`, `forbid`, and a `check` field that says why the answer cannot be guessed from
      the question). Extend the corpus lines under `corpus/traces/` the same way — the README's §3 shows
      the seed already scores 1.0/1.0 held-out, so there is nothing to climb. Verify with the existing
      `baseline/BaselineHeadroomTest` pattern: its own comment records that a headroom check that only
      ever saw 2/6 is unproven, so the test must include the all-correct case reporting **no** headroom.
      **Done 2026-11-22, with one deviation that is itself the finding.** Four files (`b27-t07`
      pii-check-advisory, `b27-t08` secrets-not-default, `b27-t09` git-source-no-push-refused, `b27-t10`
      retired-not-auto-refined), one per stage, each carrying `anyOf` and `forbid` so the gate's substring arm
      narrows them the way it narrows the six, each with a `check` naming why the answer is not guessable from
      the question, and none restating its own answer. `baseline/AddedHeadroomTasksTest`, 5 tests; deleting
      `b27-t10` fails 3 of them, including the one whose message reads "a task file that did not reach the
      classpath is not a task". The all-correct negative this task asked for is there and it is new:
      `BaselineHeadroomTest` only ever reached the stop branch over six tasks, and this asserts both arms at
      1.0 with headroom false and the verdict saying STOP, over ten.
      **The deviation is design D12.** The task named `benchmark/tasks/` as the destination, and putting the
      files there measured **23 failing assertions across ten classes** — and `OuterLoopProcessTest` did not
      fail on arithmetic but with `OuterLoopRefused`, because ten tasks declare 110 calls, 110 x 60 s is 6600 s
      against the 5400 s wall-clock ceiling, so the launcher refuses before spending anything. Doing that
      properly means re-measuring `evidence/baseline.json` and `evidence/noise-floor.json` over ten prompts
      (only the noise floor was ever planned), rewriting six fixtures plus the
      `SpendCeilingTest`/`OuterLoopFixtures` arithmetic, and raising a guardrail that exists precisely because
      a call ceiling alone lets a run take hours. So the four files live in `benchmark/tasks-headroom/`, the
      frozen six keep their count, every recorded denominator stays true, and `AddedHeadroomTasksTest` is the
      one place that loads both directories — which is where the headroom claim, stop branch included, is
      actually checked. Folding them in stays available as a decision with a named price; D12 states the price.
      **Corpus extended as asked, and that half cost nothing**: `tuning.jsonl` 16 to 17 (`t-017`, a
      lifecycle-blocked retired pack still on disk) and `heldout.jsonl` 10 to 12 (`h-011`, a step failure that
      merely *mentions* lifecycle state, so a keyword policy misreads it as a block; `h-012`, an equal re-eval
      that `belowBaseline`'s strict `<` must not call a defect). Module green at 137 tests, and
      `PolicyScoringTest`'s mixed-policy inequality still holds because the two numbers it compares stayed
      unequal. `b27-t09` has no trace evidence, recorded rather than papered over: a trace records runs of a
      published pack, never a publish that was refused.
- [x] 6.2 **Arm agreement, measured rather than assumed.** `baseline/Baseline.java` already carries
      `humanLabelFailingTaskIds`, `divergingTaskIds` and `armsDiverge()` (`:119`), and
      `referee/RefereeReport` already compares the same answers under both arms. Add the decision rule: a
      task where the substring arm and the human label disagree is **excluded from the deciding score**
      and reported separately by id. Verify with a test that a disagreement changes the reported score
      and the reported id list — a rule that only prints is unproven. Report the count honestly: on POC
      27's own numbers this removes 2 of 6 tasks from the deciding denominator.

      **Result, 2026-11-21:** `Baseline` gained `excludedTaskIds`, `decidingTaskIds` and `decidingScore`, and
      `passMarkForCandidates()` now returns the deciding score. The exclusion is **derived, never handed in**:
      the compact constructor recomputes it as the symmetric disagreement of the two verdict lists and refuses
      a record whose excluded list is anything else, so a task cannot be dropped for being inconvenient — and
      there is a test that a task both arms agreed on cannot be excluded by hand. An empty denominator yields
      `null` with a named refusal, not a flattering zero. On the checked-in numbers the arithmetic is as this
      task predicted: disagreement is {t05, t06}, so 4 tasks remain and the deciding score is 0.5 where the
      primary arm alone reads 0.667. The record and the launcher's new line both say *smaller denominator, not
      better score*. Not done, and not owed here: `OuterLoopProcess` still gates on `baselineScore`, because
      the gate re-scores a candidate over the full frozen set and swapping the field would compare a 4-task
      mark against a 6-task re-score. The launcher now prints which mark it used; the reconciliation is an
      open item, not a silent half-wire. Coordinator's own edits: three `SpringTernary` checkstyle violations
      in the new code (two `==`-tests, one unparenthesised ternary) and the module's 132 tests then green.
- [x] 6.3 **Noise floor, re-measured after the prompt fix.** Re-run the existing launcher
      (`baseline/BaselineProcess`) for ≥3 repeats over the **unchanged** seed pack after 4.1 lands, and
      write `src/main/resources/evidence/noise-floor.json` through
      `baseline/NoiseFloor.of(FrozenTaskSet, List<ScoredAnswers>)` (`:99`) using `answersMoved()` (`:117`).
      Keep POC 27's rule that a measured `0.0` is passed through at `0.0` and never raised for safety.
      Verify the recorded artifact's repeats count matches the runs performed, and report the gate scores
      as a list.

      **Result, 2026-11-22:** `BaselineProcess` run against the working tree with 4.1 landed, model
      `Qwen-instruct` through the configured endpoint, 02:14 min wall clock, **18 calls read off the meter**
      — 6 tasks × 3 repeats, declared before the first call, with nothing spent beyond the declared budget.
      Recorded to `src/main/resources/evidence/noise-floor.json` through `NoiseFloor.of(tasks, passes)`;
      `answersMoved()` reports both flipping lists.
      - `repeats: 3` — matches the 3 passes the run performed, so the artifact's own count is not inflated.
      - **Gate scores (the primary floor, and the only threshold 7.4 may use): `[0.6666666666666666,
        0.6666666666666666, 0.6666666666666666]` → `gateFloor: 0.0`**, `gateFlippingTaskIds: []`.
      - `humanLabelScores: [0.16666666666666666, 0.16666666666666666, 0.16666666666666666]` →
        `humanLabelFloor: 0.0`, `humanLabelFlippingTaskIds: []`. Recorded beside it, never a threshold.
      POC 27's rule held: a measured `0.0` is written as `0.0` and not raised for safety, which is the
      honest reading — three repeats of the same prompts produced identical answers under both scorers, so
      repeat-to-repeat agreement is real here and 7.4's delta therefore clears a floor of zero at any size.
      One difference worth carrying rather than hiding: the **previous** recording of this artifact had
      `humanLabelScores` at `[0.333, 0.333, 0.333]`, and this run measured `[0.167, 0.167, 0.167]` — one
      task the label scorer used to credit no longer. Both arms show zero within-run spread, so that shift
      is a difference *between* recordings (the prompt fix and the model's current behaviour), not noise
      inside a run, and it does not touch the threshold the gate uses.
      Reproduced here so a reader knows where it came from: the README's documented launcher command needs
      the reactor installed first — `./mvnw -Ppoc -pl <module> spring-boot:run` cannot resolve the sibling
      `0.1.0-SNAPSHOT` artifacts, and the snapshot remote answers 401, so the run fails in about three
      seconds unless `install -DskipTests` has been done or `-am` is added.
- [x] 6.4 **Distinct requests.** Add the assertion that POC 27's run needed and the repository does not
      have: for N eval tasks with distinct `input` values (`builder/eval/EvalTask.java:20` is
      `(name, input, expected)`), the N answers MUST NOT all be equal. It belongs in the launcher, after
      the first two tasks come back and before the rest of the budget is spent, because
      `builder/eval/AgentSpecInvoker` is a single `String invoke(String input)` (`:17`) and exposes no
      prompt to inspect. Verify with a fake invoker that returns a constant: the launcher must stop with a
      named refusal and a recorded reason, and a second fake that varies its answer must pass the same
      check. Both cases are required.


      **Result, 2026-11-21:** `DistinctAnswerGuard` wraps the metered invoker factory, and the check runs
      inside the returned invoker immediately after each answer returns — so it trips on the second answer of
      the first candidate, after two distinct prompts have been asked and before that candidate's remaining
      four tasks or the other nine variants. It throws `OuterLoopRefused`, which `main` records through the
      same `REFUSED — ` line as its other refusals, naming how many answers, that they were equal, and the
      first value. Both fakes exist: a constant-answering invoker stops the launcher with exactly two calls
      spent, a varying one passes the same check and continues past the two answers. One limit worth stating:
      the comparison is byte-equality on whole answers, so if the frozen set ever grows a short-answer task, a
      boilerplate reply to two different prompts would trip it.
- [x] 6.5 Confirm the static half of 6.4 exists after the preceding change: read
      `smith-common/.../validation/AgentSpecValidator.java`'s prompt-reference check (`checkPromptReferences`,
      around `:210-222`, driven by the `PROMPT_REF` pattern at `:52`) and report whether an input-blind
      pack is rejected offline. If it is not, that rule belongs to the preceding change — say so and stop
      here rather than duplicating it.

## 7. Model spend, declared before it is spent

- [x] 7.1 Set the budget from arithmetic in
      `poc/poc-27-recursive-self-improvement/src/main/java/com/embabel/smith/selfimprove/outerloop/Budget.java`
      callers, using `Budget.declare(maxModelCalls, maxGenerations)` (`:33`) and `admits(spent, costPerCandidate)`
      (`:51`), with `costPerCandidate = 1 deploy + N task invocations` computed from the frozen task count.
      Report the numbers you declared and the task count they came from.


      **Result, 2026-11-21:** the cost side was already derived, not a constant — `OuterLoop.java:100` set
      `costPerCandidate` to `referee.frozen().ids().size()`, which is 6 on the checked-in set, and that was the
      module's only `admits(...)` site. The only arithmetic missing was the deploy term, so `Budget` now owns
      `CALLS_PER_DEPLOY = 1` and `costPerCandidate(frozenTaskCount) = 1 + count`, called by both the launcher's
      ceiling and the loop's `admits` site so the two cannot drift. Declared on the checked-in set: 5 variants
      x 7 x 2 generations = **70 calls**, from a frozen task count of 6 `.json` files (the 7th entry under
      `benchmark/tasks/` is README.md, which `FrozenTaskSet.load` does not read). One correction to this task's
      own framing: a candidate's deploy costs **no metered call** in this POC, because the referee's
      `RecordedInvokerFactory` replays answers, so the deploy term is a reserve rather than a measurement —
      the run declares 70 and the meter reports 60.
- [x] 7.2 Add a **wall-clock** ceiling beside the call ceiling, and refuse the run when the arithmetic
      exceeds it. The input is measured, not guessed: POC 27's record shows two 60 s timeouts on the one
      real completion it obtained, so a call budget alone puts a 60-call run in hours.


      **Result, 2026-11-21:** two constants beside `GENERATIONS` — `SECONDS_PER_MODEL_CALL = 60`, from the
      POC's own record (`README.md:179` and `:243`, two 60 s timeouts on the one real completion) — and
      `MAX_RUN_SECONDS = 5400`, which is 90 declared calls at that measured latency. The checked-in two-
      generation run declares 70 and fits; the third generation the record argues against declares 105 and is
      refused before any call is spent. No new knob. The refusal sits at the top of `runPass` rather than in
      `main`'s pre-Spring block so a caller other than `main` cannot bypass it, which means Spring boots first
      while still making no call.
- [x] 7.3 Make the launcher print the reason for the spend in the same line as the number, and confirm it
      refuses on the **contents** of the artifacts (POC 27 already refuses on a recorded `headroom: false`,
      per `OuterLoopProcessTest.refusesARecordedBaselineThatSaysThereIsNoHeadroom`) rather than on their
      presence.

## 8. The tool surface (design D9, D10, D11)



      **Result, 2026-11-21:** the budget line now carries the arithmetic beside the number, including the
      deploy term, and a new line prints the time ceiling with the measured latency it came from. The
      contents-refusal half was already true and was confirmed rather than rewritten: `refusalReason` reads
      `headroom` out of the recorded baseline, and `refusesARecordedBaselineThatSaysThereIsNoHeadroom` still
      covers it.
- [x] 8.1 **Prove `toolGroups` is honoured before anything is built on it.** Design D9 rests on
      `StepSpecAgentScopeBuilder` and `support.PromptedActionSpecAction` referencing tool groups in
      `embabel-agent-spec-1.5.4-20261112.000934-68.jar` — a reference, not a demonstrated behaviour. Deploy
      a two-step pack through
      `prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/eval/AgentSpecInvokerFactory.invokerFor(AgentSpec)`
      (`:58`), one step declaring a group and one declaring none, and report what actually differs. Include
      a step declaring a **typo'd** group: if that deploys silently, record it as a finding — a group name
      that resolves to nothing is the same family of defect as the unnamed prompt variable that cost POC 27
      its run, and it is the failure mode this whole section inherits. A live half under `@Tag("live")`
      (the convention already in six prod test files) shows whether the tool is callable, not merely
      carried. If the platform ignores the declaration, D9's agency claim collapses and the builder's flow
      stays in Java — say so rather than working around it.

      **Result, 2026-11-21:** `eval/ToolGroupBindingTest`, 5 offline tests green (`Tests run: 5, Failures: 0,
      Errors: 0`) plus an endpoint-gated nested case. The declaration is honoured only as a **name**: the
      declared string arrives on the compiled `Action` as a `ToolGroupRequirement` role, `PromptedActionSpec.emit`
      never reads the scope's offered groups, `deploy` never looks at them, and `AgentSpecValidator` has no
      tool-group path — so `web` and `webb` are indistinguishable at deploy and the typo is the recorded
      finding, with a control case (the same validator does raise `DANGLING_TYPE_REF` on the same pack shape)
      so the absence is measured. D9 does not collapse: the declaration reaches the artifact the platform is
      handed. It narrows: resolution is a prompt-time question in the platform's `ToolGroupResolver`, so
      "callable" is what the gated case asks and not what the offline five prove. Deviation from the literal
      brief — three single-step packs rather than one two-step pack, which measures the same three cases and
      adds the fourth assertion that the deploys differ *only* in the role string. New finding, unasked:
      upstream's roles are `web`/`math`/`maps`/`github`/`browser_automation` (`CoreToolGroups` in
      `embabel-agent-api-1.5.4.jar`) while `security/TrustTier.java` allowlists `search`/`web`/`file`/`shell`,
      so three of a tier-3 pack's four names are not core roles. Written into D9 and qualified in D10.
- [x] 8.2 Register the builder's deterministic services as tools, starting with the one that decides the
      argument. `grep -rn "@LlmTool\|@Tool" prod/embabel-smith-builder/src/main` is **zero** today, as it is
      in every other prod module, so a pack's steps see upstream's built-in groups and never the builder's
      compile, validate, scan or write. Expose `smith-common`'s `validation/AgentSpecValidator.validate`
      (`:104`) and `validation/ProcessValidator.validate` (`:58`) first. Each tool is an **adapter over the
      existing service**: verify by reading the new type and confirming it contains no gate logic — no
      threshold, no verdict, no publish. Report the `@LlmTool` count after the change; it is the evidence
      the gap closed.
      **Done 2026-11-22 (`719cc76`).** `tools/AgentSpecValidationTool` and `tools/ProcessValidationTool`
      are the adapters, and the count is now **2** where it was 0. Neither holds a threshold, verdict or
      publish call; `tools/ValidationToolPassthroughTest` (4 tests, offline) feeds the same directory to
      tool and service and compares the results field for field, pinning a `DANGLING_TYPE_REF` at
      `steps/say.yml` so the comparison cannot pass on two empty lists.
      **The half of the premise that did not survive:** declaring a group does not make these reachable.
      `@LlmTool` carries no group member (jar: `description`, `name`, `returnDirect`, `category`,
      `metadata`); `api/annotation/ToolGroup` is referenced by nothing but itself across all 25 Embabel
      1.5.4 jars, so annotating a class with it registers nothing; and `RegistryToolGroupResolver(String,
      List<ToolGroup>)` resolves only against the `ToolGroup` beans in the context
      (`AgentPlatformConfiguration.toolGroupResolver(List<ToolGroup>, List<List<ToolGroup>>)`), whose
      upstream members are the five `CoreToolGroups` plus MCP-configured ones. A `ToolGroup` bean cannot be
      authored in Java **directly**: the interface's metadata accessor is `getVersion-Id9oKnY()` and the
      metadata factories are Kotlin companions `invoke-oteLqWg` / `invoke-bYD-3ao`, names `javac` cannot
      express, and `MinimalToolGroupMetadata` is package-private. It **can** be authored indirectly, and the
      recipe is already in this repo's frozen evidence — `poc-7-runtime-adapter`'s `ShellToolGroupFactory`
      resolves the five-argument companion factory by signature through reflection and calls the plainly-named
      `ToolGroup.Companion.ofTools`, with `ShellToolGroup` publishing the result as a `@Bean`. So a
      builder-owned group name is makeable if this project wants declarations to resolve; what does not exist
      is a stock name for a builder service. The reflection-free alternative is
      `StepSpecAgentScopeBuilder`'s `List<Tool>` constructor argument, which makes the reach host Java rather
      than pack YAML. Both are recorded as a choice in the follow-on change's D7.
- [x] 8.3 Give `builder-single-spec`'s drafting step the validate tool group, which is what makes the
      planner able to read its own validation errors and re-draft — the self-repair
      `core/BuilderService.java` performs today as numbered stages in Java. Two verifications, both cheap:
      report the step's `toolGroups:` line, and run
      `security/AgentSpecSecurityScanner.scan` over the pack and report `recommendedTier()` — `minimumTier`
      at `security/AgentSpecSecurityScanner.java:79` derives the tier from declared groups, so a pack that
      still recommends `T1_COMMUNITY` (`security/TrustTier.java:21`, which allows none) after declaring a
      group is a pack whose declaration is not reaching the scanner. Report the before-and-after tier.
      **Measured 2026-11-22, before the edit.** The check as written is one-directional, and 8.2 says why:
      `TrustTierPolicy.check` (`:52-55`) filters declared **strings** against the tier allowlists and
      resolves nothing, so any declaration at all moves `recommendedTier()` off `T1_COMMUNITY` — `web` or
      `search` to T2, `file` or `shell` to T3, and anything else to T4_PLATFORM, because T4 allows `*` and
      so `minimumTier` always terminates there. A moved tier therefore proves only that the scanner read
      the step, never that the group resolves to a tool. Two consequences, and 8.3 stays open on the
      first: putting a name on the drafting step is a scanner-visible declaration whose prompt-time effect
      is decided by the platform's resolver and not by this repo, while the reach 8.2 actually bought
      arrives through the scope's tool list, where a generation cannot reach it either way. That is the
      containment reading amended in D10.
      **Done 2026-11-22.** `builder-single-spec/steps/assembleAgentSpec.yml:16` is the change: `toolGroups: []`
      becomes a one-item list naming `validate`, and the git diff is that single hunk — no prompt text moved.
      `assembleAgentSpec` is the drafting step because it is the only one of the pack's five steps whose prompt
      asks for spec content; the other four produce an input to the spec (class javadoc has the read).
      **Tiers, both through `AgentSpecSecurityScanner.scan` rather than read off `TrustTier`:** before
      `T1_COMMUNITY`, after `T4_PLATFORM`. The after-value is measured, not inferred — the new test's control
      asserts the drafting pack's tier equals that of a pack declaring `no-such-group`, and asserts that
      control's own tier is `T4_PLATFORM`, so T4 is reported by the scanner, on both sides.
      `core/BuilderPackTierTest`, 4 offline tests; module green (266 tests in the module run; the one failure
      in the aggregate tally was `LiveExampleBuildTest`'s report file from 2026-11-20, live-tagged and excluded
      by `test.excludedGroups`, confirmed by mtime rather than assumption). Mutation-checked twice: reverting
      the declaration fails 3 tests (placement, tier-above-T1, equality-with-the-control) and leaves the T1
      baseline test passing, which is the pair that says the assertions track the declaration; moving the same
      declaration to `scopeCoreProcess.yml` fails exactly 1 test — placement — so a name spread onto a planning
      step is rejected rather than rewarded.
      **Not done, deliberately:** the step's own prompt still asks the model to emit `"toolGroups": []` for the
      steps of the spec it drafts. Giving the builder a group is a change to the builder's agency; making every
      generated spec carry one would be a change to the drafted format, and nobody asked for that.
      **Learning:** a pack's `recommendedTier()` moves off `T1_COMMUNITY` on *any* declaration string, so the
      tier is a policy surface a reviewer reads, not a reach control — the `no-such-group` equality is the
      control that says so, and it is why this task's verification is the placement assertion and not the tier.
- [x] 8.4 Assert the scorer stays out of every builder pack. No step prompt under
      `prod/embabel-smith-builder/builder-agent-specs/` may match `grade_eval.jinja`, `agent_baseline.jinja`
      or `agent_with_skill.jinja`, the three the same file loads as evaluation instrumentation at
      `skill/SkillPrompts.java:26,28,30`. Write it against a conforming pack as well as the real one — an
      absence assertion satisfied by naming is not an assertion. State in the test's javadoc what it cannot
      catch: a grading prompt **rewritten in other words** passes this check, so keeping the scorer out
      remains a review obligation, which is how design D10 records it.
      **Done 2026-11-22 (`22a8b7f`).** `skill/ScorerPromptBoundaryTest`, 4 tests, module green. It compares
      text rather than names: the share of each scorer's five-word runs that appears in the concatenated
      step prompts of all three packs, read through `AgentSpecReader.read(Path)` and through
      `SkillPrompts`' own accessors so the bytes measured are the bytes production sends to the judge.
      Measured 0.023 / 0.000 / 0.000 over 12 step prompts and 14,435 characters; a copy planted in the same
      haystack reads 1.000, and with its central term renamed 0.907. Mutation-checked by hand: appending
      `grade_eval` to `builder-single-spec/steps/assembleAgentSpec.yml` makes the guard fail and name the
      12 steps and 15,878 characters it scanned, then restores the pack.
      **Deviation from the wording, in the task's favour:** the conforming half is a planted copy in the
      real haystack rather than a second pack fixture, because scorer-against-itself is 1.0 for any measure
      including a degenerate one. The pair still pins both ends — a measure blind to a copy fails the
      planted test, one that always matches fails the guard, and an empty trace set fails inside
      `containment` rather than passing as a zero.
      **Two corrections to the task's own framing.** `SkillPrompts` declares five prompts, of which three
      are the eval instrumentation named here (`:26,28,30`); and two of those three are not scorers —
      `grade_eval` is the judge, while `agent_baseline` and `agent_with_skill` are the two eval *arms*, so
      editing them changes what the comparison measures rather than the verdict rule. They belong off the
      mutable surface for that reason, and the javadoc says so instead of calling them scorers. Coverage is
      length-dependent and recorded as such: a scattered term rename through the 214-trace `grade_eval`
      still measures 0.84-0.96, while renaming the central term of the 9- and 45-trace arms falls to 0.44
      and 0.58, under the threshold.
- [x] 8.5 Deploy the three packs as one scope through
      `eval/AgentSpecInvokerFactory.invokerFor(ProcessSpec)` (`:69`) as a `ProcessSpec` of three
      `AgentSpec`s — `common/model/ProcessSpec.java:44`, with `withAgents(List<AgentSpec> reordered)` at
      `:100` — and assert that a candidate's **agent order is unchanged**, alongside 4.1's assertion that a
      candidate differs from its parent only in step prompts. Ordering is reachable in the model and
      unreachable through the mutation operator, so it is fixed by construction; a claim that the loop
      improves collaboration structure would be false and this task is what keeps it false. Also record in
      the mutability evidence amended by 5.1 that no operator may touch a step's `pre` or `post`:
      `smith-common/.../validation/AgentSpecValidator.java` never inspects either field and the spec source
      resolves no conditions, so an edited condition name fails at **deploy**, not at validation. **(inferred)**
      that deploying three builder packs as one scope behaves as 4.2's deploy does; if the platform refuses
      duplicate goal names, that is the same finding 4.2 anticipates, not a new one.
      **Done 2026-11-22.** `eval/CollaborationStructureIsFixedByConstructionTest`, 4 offline tests — a mock
      `AgentPlatform` and no model call, so it carries no `live` tag. One `deploy(AgentScope)` for three packs,
      carrying all 12 step names and all 3 goal names; the candidate's agent list equals the parent's in order
      and so does the goal the deploy runs; `withAgents(reverse)` moves that goal, which is the control that
      keeps the survival assertion from being a tautology; and `pre`/`post` survive the rewriter byte-for-byte
      on a synthetic pack that *names* conditions, while every prompt carries the rewriter's mark.
      **Deviation from the wording, in the task's favour.** The task asked for the *deployed scope's* ordering.
      That observation does not exist: `eval/StepSpecAssembler` names each assembled file
      `<packIndex>-<stepFile>.yml`, but the reader is `YmlStepSpecRepository.findAll`, which iterates an
      unsorted `File.listFiles()` — no `sortedBy` anywhere in it (1.5.4 bytecode). So the scope's action list
      arrives in filesystem order, unspecified by the JDK, and an assertion that it groups by pack would pass
      on one filesystem and fail on another. The test asserts ordering where shipped code actually reads it:
      `eval/AgentSpecInvokerFactory.entryGoalName` takes the **last** agent's goal and narrows the deployed
      agent to it. Same claim, observable rather than filesystem-dependent.
      **Why the claim is stronger than the task asked.** `withAgents` has exactly one main-code caller —
      `core/ProcessRepair.java:61`, reached from `BuilderService:238` on the build path, not the improvement
      path — and nothing under `improve/` imports `ProcessSpec` at all. `Refiner.refine` is handed a pack, and
      a pack has no agent order to reorder, so ordering is unreachable through the operator **by signature**,
      not by discipline.
      **Mutation-checked by the coordinator.** Blanking `step.pre(), step.post()` in `improve/Refiner.java:159`
      fails exactly the condition test and nothing else in the class; restored, module green.
      **Recorded in the mutability evidence**, as this task asked: `poc/poc-27-.../src/main/resources/evidence/MUTABILITY.md`
      gained two paragraphs in 5.1's section — the `pre`/`post` deploy-not-validation failure mode, with the
      note that keeping it is free because `withPrompt` copies the field and all twelve shipped steps declare
      `[]`; and the ordering claim, so the section now rules out "improves collaboration structure" too.
      **Two findings for 4.2's collision family.** No collision of the loud kind: the three packs export
      distinct goals (already pinned in `core/BuilderAgentSpecPackTest`) and share no step name. But
      `AgentSpecTypeMapper.allRuntimeTypes` dedups types **by name**, so two packs that each declare
      `ProcessScope` with different properties collapse to whichever the reader met first — no exception, no
      validator finding, a silently narrower type. That is the collision that lands on the shipped path, and
      it is written into the follow-on change's 1.4 rather than left here.
      **Learning:** the ordering question is settled by reading the operator's signature, not by running
      anything — and the one deploy-time observable that *does* read order is the entry goal, so that is what
      a test can honestly assert. Asserting a compiled scope's action order is not a claim about the loop at
      all; it is a claim about directory iteration order.

## 9. Record the outcome

- [x] 9.1 Update `poc/poc-27-recursive-self-improvement/README.md` with a reframe section: the mutable
      surface is now the builder's own packs, and §3/§4's measured numbers stay as they were — they are
      evidence, not an embarrassment to edit. Add the four guards' numbers.

      **Result, 2026-11-22.** `### The reframe: the improvement object is a builder pack` under *How it
      works* and `### 5. The four measurement guards` in *Results*; §3 and §4's measured numbers were not
      touched. The mermaid `MUTABLE` subgraph gained the builder-packs node and its two edges, checked
      well-formed (node declared, `-->|label|` edges, brackets balanced) rather than assumed. Two
      corrections the page needed and this task's wording did not ask for: the mutation operator is the
      POC's own `outerloop/BuilderPacks.withStepPrompt`, **not** the builder's `Refiner.withPrompt`, which
      is `private static` at `builder/improve/Refiner.java:157` and is only mirrored there; and the
      pack count is four, not three, because `builder-spec-one-shot` landed at `1519660` after
      `evidence/MUTABILITY.md` was last written — the artifact itself now lists it, so the page and the
      artifact agree. The claim that a candidate changes nothing in `smith build` is now scoped to the
      default: `--flow pack` (not the default) deploys `builder-spec-one-shot`, so a candidate to that
      pack would change such a run.
- [ ] 9.2 Update `docs/04-poc-run-results.md` §7.5 and `docs/10-self-improvement.md` to say what the
      improvement object now is, and add the builder packs to `docs/08-builder.md`. Cite the settled
      wording in those pages rather than re-deriving it.

      **Two of three landed, 2026-11-22, so this stays open rather than being rounded up.**
      `docs/04-poc-run-results.md` §7.5 gained the "what the improvement object is now" lead and the four
      guards paragraph; `docs/10-self-improvement.md`'s **Refinement** paragraph now names the surface as
      the step prompts of a pack and states that nothing on that page changes. Both got the same two
      corrections as 9.1 (the `BuilderPacks.withStepPrompt` attribution, and four packs). The third page,
      `docs/08-builder.md`, is deliberately untouched: another session holds uncommitted edits to that
      exact file — its current diff is a native-image section — so writing it here would ride into their
      commit. The two pages that did land link to it as where the builder packs belong, so the seam is
      visible to whoever picks it up.
- [x] 9.3 Note the change in the wiki. A vocabulary change is a documentation change there: it cross-links
      44 pages and needs its lint and its re-publish afterwards (per `AGENTS.md`).

      **Result, 2026-11-22.** `.wiki/features/smith-builder.md`'s "The builder's own packs" section was
      corrected in place: it claimed the packs were only what a *queued* change would run, that nothing in
      `src/main` loads them, and that `smith build` has no option selecting a pack-driven flow — all three
      falsified by `9bebf30`, which added `--flow` with `java` as its default and `src/main` loading
      `builder-spec-one-shot` under `pack`. `.wiki/index.md`'s ingest note says four packs. Added one
      outgoing link to `[[what-the-pocs-prove]]`. **Lint after the pass: 45 pages, 0 broken `[[links]]`, 0
      orphans** (every page except `index` has an inbound link), and no page still says "three packs". The
      GitHub Pages re-publish remains owed — it fires on a push, and this branch is not pushed.
- [x] 9.4 Run the POC reactor for this module only and report the reactor summary as it prints — modules
      succeeded, skipped, failed — plus the model-call count read off `MeteredInvokerFactory`, not
      asserted. Do not report an earlier run's result.

      **Result, 2026-11-22:** run at commit `4d5cc73` (the 4.1/4.2/6.3 commit), in a detached worktree of
      that commit so the tree matched what is recorded rather than whatever the concurrent edits in the
      working tree happened to be. `make test-module MODULE=poc/poc-27-recursive-self-improvement POC=1`.
      Reactor summary as printed — **11 modules SUCCESS, 0 skipped, 0 failed**: `Embabel Smith` 1.216 s,
      `smith-common` 4.166 s, `Embabel Smith Product` 0.147 s, `embabel-smith-specsource` 2.105 s,
      `embabel-smith-skillevaluator` 15.656 s, `embabel-smith-runtime` 8.003 s, `embabel-smith-builder`
      18.775 s, `embabel-smith-studio` 32.293 s, `Embabel Smith POCs` 0.029 s,
      `poc-26-graaljs-skill-scripts` 6.386 s (the module's declared sibling dependency, so it is in the
      chain), `poc-27-recursive-self-improvement` 5.880 s.
      The model-call figure has to be stated carefully, because this run does not produce one: the default
      test group excludes the live-tagged classes, so the offline suite spends **zero** endpoint calls and
      `MeteredInvokerFactory` has nothing to read. The count this module does read off the meter is the 6.3
      live baseline's **18** — 6 tasks × 3 repeats, printed by the launcher as "model calls : 18 read off
      the meter" rather than asserted by a test — and the 4.1 finding applies to it: behind each of those
      counted invocations sits the candidate pack's step count, not one call.

## Results recorded during implementation

- **2.2 and 2.3, 2026-11-21.** `SkillBuilderService.buildFromProcess(ProcessSpec, PublishTarget,
  BuildOptions)` is in, and the module compiles. A **stage is a member `AgentSpec`** of the process, and
  its description is assembled in Java from `stage.name()` plus, per step, `step.name()`,
  `step.description()`, `step.inputTypeNames()` and `step.outputTypeName()` — no model call in the
  derivation. Two of the plan's framings turned out to need settling rather than following:
  **POC 25 has no stage concept in its code at all** (`grep stage` over its `src/main/java` returns
  nothing), so there was no convention to reuse and D2's "genuinely new" column is the accurate one; and
  the **return shape is not aggregate** — the first stage a gate refuses stops the run and is what gets
  reported, because a process with an unpublished stage is not a built process, and when all stages pass
  the earlier skills are already published and the report is the final stage's. The method body contains
  no gate call of its own.
- **4.3, 2026-11-21.** Deliberate non-change, recorded rather than implicit: the inner loop's machinery is
  untouched. Its flat derivative is a corpus property, confirmed by `wc -l` at 16 tuning + 10 held-out =
  26 lines, and the fix for a saturated corpus is more corpus.
- **6.5, 2026-11-21.** The static half of 6.4 exists and belongs to the preceding change, so it is not
  duplicated here: `PROMPT_IGNORES_DECLARED_INPUT` is in `AgentSpecValidator` and is exercised by
  `smith-common`'s own `AgentSpecValidatorTest`.
