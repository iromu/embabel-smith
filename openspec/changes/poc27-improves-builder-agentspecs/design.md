# Design

## Context

The claim this change rests on was read, not assumed. The builder's process exists as a planned agent —
`@Agent(planner = PlannerType.GOAP)` at `core/BuilderAgent.java:52`, three `@Action`s
(`:90`, `:130`, `:142-144`) — and the builder can already **execute** packs in its own module:
`eval/AgentSpecInvokerFactory` holds an `AgentPlatform` (`:50`), implements `invokerFor(AgentSpec)`
(`:58`) and `invokerFor(ProcessSpec)` (`:69`), and its own javadoc records that a process is deployed as
**one scope** because the hand-off between two agents is a shared type a per-pack planner cannot see
across. Multi-agent collaboration through a shared type is proven live: `agent-specs/compose-extract`
and `compose-explain` hand off through `TopicBrief`, and `poc/poc-6-agent-spec-composition` chains them.

So the missing thing is not an agentic flow and not an executor. It is the builder's flow **written in
the format it builds**, and the seam that lets a decomposition reach the skill pipeline.

Two facts constrain what any pack here can contain, and both are permanent rather than incidental:

- **A pack holds cognitive stages only.** `smith-common/.../validation/AgentSpecValidator.java:212` adds
  `PROMPT_MISSING` for a step with no prompt, so a step that is a deterministic check cannot exist. Every
  gate — `ProcessValidator.validate(ProcessSpec)` (`smith-common/.../validation/ProcessValidator.java:58`),
  `skill/SkillMdValidator`, the gate chain, `publish/*` — is host Java by construction.
- **A step cannot emit a whole nested AgentSpec.** `model/PropertyDef.java:26` types a property as a
  `String` naming a scalar or a declared type (`:19`: "`boolean`, or a type name) — no compiled class is
  required"), and `model/DynamicTypeDef.java:31` holds `List<PropertyDef>`. The pattern this repository
  already proves works is a step emitting JSON or text and **Java** parsing and validating it — which is
  what `SingleShotProcessSpecGenerator` does with `AgentSpecNormalizer.extractJson`
  (`core/AgentSpecNormalizer.java:51`, package-private).

## Goals / Non-Goals

**Goals:**

- The builder's three named processes exist as AgentSpec packs in the builder module, loadable with
  `smith-common`'s existing `io/AgentSpecReader` and deployable with the existing `AgentSpecInvokerFactory`.
- POC 27's second leg improves those packs, and what "improve" may touch is stated as a set, not a
  metaphor.
- The four measurement conditions that made POC 27's run uninterpretable are guarded **before** a paid
  run, each with the number that decides it.
- Model spend is declared as arithmetic before the first call and refused when the arithmetic does not
  fit.

**Non-Goals:**

- Not rewiring `cli/BuildCommand` onto the packs. It keeps calling the Java generators; design D8 says
  what stops the packs from becoming decoration instead.
- Not a rewrite of `BuilderAgent`. Its three actions stay; the packs describe the same process in the
  mutable format, and design D2 is explicit about which of the three named processes is transcription and
  which is new.
- Not touching the inner loop's design. Its flat derivative is a corpus property (POC 27 README §3), and
  the fix there is more corpus, not new machinery.
- Not a POC 28. The user chose to reframe POC 27 in place.

## Decisions

### D1 — The packs live in a directory of the builder module, not in `agent-specs/` and not as resources

`prod/embabel-smith-builder/builder-agent-specs/<pack>/`, laid out exactly like
`agent-specs/explainer/` (`manifest.yml`, `goal.yml`, `steps/*.yml`, `types/*.yml` — that layout was read
from disk, and `explainer/steps/extractTopic.yml` ends with `User request: {{userInput.content}}`, the
interpolation POC 27's candidate lacked).

Three placements were ruled out by what the code does, not by taste:

- **Not `agent-specs/` at the repository root.** That directory is the published **spec source** the
  runtime ingests; the builder's internals do not belong in it, and putting them there would make them
  candidates for every other loop.
- **Not named `agent-specs` anywhere else either.** That name is a discovery sentinel: POC launchers walk
  up from the working directory looking for `agent-specs` or `agent-specs/compose-extract`
  (`poc/poc-21-agent-spec-security/src/main/java/com/embabel/smith/security/SpecSecurityApp.java:106`,
  `poc/poc-6-agent-spec-composition/src/test/java/com/embabel/smith/composition/CompositionWiringTest.java:113`,
  `poc/poc-15-composer-ui/src/main/java/com/embabel/smith/ui/SpecCatalog.java:118`). A second directory
  with that name invites one of them to find the wrong one, so the builder's copy is named
  `builder-agent-specs`.
- **Not `src/main/resources/`.** Packs are read from the filesystem. `io/AgentSpecReader.read(Path dir)`
  lists `steps/` and `types/` with `Files.list` (`smith-common/.../io/AgentSpecReader.java:38,43,48,55`),
  and a spec source resolves to a `Path` (`folder:./agent-specs` becoming `Path.of("./agent-specs")`,
  `prod/embabel-smith-specsource/src/test/java/com/embabel/smith/specsource/SpecSourceParseTest.java:25-28`).
  A file under `src/main/resources` is packaged into a jar and is not addressable as a directory, so a
  pack placed there could not be read by the reader this change reuses. A module directory can, with no
  resource-to-temp extraction standing between the packs and the existing reader.

*Alternative considered*: generate the packs from `BuilderAgent`'s annotations at build time. Rejected —
a second representation that must be kept in sync with the first, generated by code that is itself part of
the mutable surface.

### D2 — Three packs, and an honest exists/new split per process

| The user's process | Already exists (read) | Genuinely new |
| --- | --- | --- |
| Builds skills from a process specification | `skill/SkillGenerator.generate(String, String)` (`:56`), `skill/SkillBuilderService.build(String, PublishTarget, BuildOptions)` (`:101`), `blueprint/ProcessExtractor.extract(String)` (`:74`) | **The `ProcessSpec` → skill seam.** Both skill entry points take free text and `ProcessSpec` appears nowhere in `skill/` (grep: zero hits). New: a per-stage description derived from a decomposition, feeding the existing `build(String, …)`. |
| Builds AgentSpecs — many agents collaborating | `core/ProcessSpecGenerator` (interface, `generateProcess(String)`, with a `default` model overload), `SingleShotProcessSpecGenerator.generateProcess(String)` (`:66`), `eval/AgentSpecInvokerFactory.invokerFor(ProcessSpec)` (`:69`) | The **pack form**: the decomposition stages as prompt-backed steps of one pack. The decomposition itself is not new. |
| Builds one AgentSpec with multiple steps | `SingleShotAgentSpecGenerator.generate(String)` (`:83`), `BuilderAgent`'s compile → validate → write chain (`:90`, `:130`, `:142`), `ProcessRepair`, `DraftToAgentSpec` | The **pack form**, and only the cognitive half of it: compile and any repair-negotiation stage are steps; validate and write are host Java (D3). |

The middle row's collaboration claim is transcription of a proven mechanism, not a new one: two packs
meeting through a shared `DynamicTypeDef` with no orchestration code (`agent-specs/compose-extract` /
`compose-explain`, POC 6).

*Alternative considered*: one pack doing all three. Rejected — the user named three, and a single pack
would make a mutation unattributable, which POC 27's `MUTABILITY.md` already rules out ("One mutation per
generation").

### D3 — The mutable surface is the step prompts of those packs, and only those

**Mutable:** the `prompt:` text of each step in the three builder packs. That is precisely what the
existing primitive changes — `builder/improve/Refiner.withPrompt` (`:157-160`, in the builder since
`builder-owns-improvement-loop` moved the engine out of the studio on 2026-11-21; the line numbers are
unchanged by the move) rebuilds an `ActionStepSpec`
passing `step.stepType(), step.name(), step.description(), step.inputTypeNames(), step.outputTypeName(),
prompt, step.pre(), step.post(), step.cost(), step.value(), step.canRerun(), step.nullable(),
step.toolGroups()`, changing only the sixth argument. The reframe therefore needs no new mutation
operator, and the surface is defined by code rather than by prose.

**Not mutable, and permanently so:** `AgentSpecValidator`, `ProcessValidator`, `SkillMdValidator`, the
gate chain and its scorer, the publishers, `BuilderAgent`'s Java, `AgentPlatform`. The reason is
structural, not policy: by `AgentSpecValidator.java:212` a step without a prompt is invalid, so no
deterministic gate can ever be expressed *inside* a pack — and a mutation that can only edit pack files
cannot reach host Java. Widening the surface would mean either allowing prompt-less steps (which voids the
validator) or letting a generation edit Java (which POC 27's `MUTABILITY.md` already refuses, on the
grounds that no gate in this repository reviews generated Java before executing it).

**Also not mutable, carried unchanged from POC 27's `evidence/MUTABILITY.md`:** the frozen task set, the
labelled corpus and its held-out split, the generation archive, the noise-floor artifact, the referee
process, human control.

A pack is not cut off from deterministic logic, though. Two fields let a step **name** something host
Java provides — `toolGroups` and the `pre`/`post` condition names (`model/ActionStepSpec.java:31,36`,
documented as "the precondition condition names" and "the tool groups the action may use"). Neither is a
step, so D3's structural claim stands: a mutation that edits pack text cannot execute host code. What the
two fields do change is the *reach* of a pack, and that is a decision of its own, taken in D9 and D10.

*Alternative considered*: let a generation also change process shape (step count, hand-offs). Deferred —
it multiplies the mutation space without a scorer that could attribute it, and D6's headroom fix is what
actually buys the derivative.

### D4 — A step emits text or JSON; Java decides what it means

Because of `PropertyDef`'s scalar-or-named-type constraint, a builder pack step that "produces a spec"
produces a **string**, and the pack's declared `DynamicTypeDef`s describe only the intermediate
cognitive artifacts (a stage list, a skill brief). Parsing and validation stay in Java, following
`AgentSpecNormalizer.extractJson` (`core/AgentSpecNormalizer.java:51`) and
`skill/LlmJson.extractJson` (`:74`). This is a stated consequence of the format, not a shortcut: a pack
whose steps returned specs would be a pack the validator rejects.

### D5 — The packs must not route a step output through `BackgroundMessage`

The NL compiler's own prompt instructs the model to return `BackgroundMessage` for a notify-or-generate
action — the vendored copy at
`poc/poc-24-generation-approaches/src/main/resources/prompts/compiler/compile_action.jinja:19-20`
says *"If the purpose of the action appears to be to notify the user of something, or generate a message,
return type `com.embabel.agent.spec.model.BackgroundMessage`"* (the POC 27 record cites this as
`compile_action.jinja:16-18`; the vendored copy here puts it two lines lower). And
`DraftToAgentSpec` remaps a non-returnable goal output onto `BackgroundMessage` — `isNonReturnable` at
`core/DraftToAgentSpec.java:61,88`, the rewrite of the producing step at `:67`, the constant at `:106` —
and `NON_RETURNABLE_OUTPUTS` (`:101-103`) includes `java.lang.String` as well as `string`, so even a
scalar-string goal inherits it. Its `parts` is a
polymorphic list whose deserializer discriminates on the property name, and it cost POC 27 its run. Each
of the three packs therefore declares a named `DynamicTypeDef` for every step output.

*Honest note*: `agent-specs/explainer/goal.yml` does declare
`outputTypeName: com.embabel.agent.spec.model.BackgroundMessage` on its **goal** and runs. The constraint
this change adopts is about a **step** whose output the model must emit as JSON, which is the shape that
failed; that distinction is what the guard test asserts, and it is asserted against both a violating and a
conforming pack so the check cannot be satisfied by naming.

### D6 — Four guards, each decided by a number, before any paid run

| Guard | How it is measured | Existing | New |
| --- | --- | --- | --- |
| **Headroom** | Score the **unmutated** seed packs under both arms; `ScoredAnswers.gateArmHasHeadroom()` (`baseline/ScoredAnswers.java:73`) and `Baseline.of(…, headroom)` (`baseline/Baseline.java:94`). Refuse the run when the seed passes every task. | `baseline/BaselineHeadroomTest` already proves the check can say "no" (its own comment: a headroom check that only saw 2/6 is unproven) | Authored task lines the seed **fails** — the current six pass at 0.667 gate / 0.333 human and the 26-line corpus is saturated |
| **Arm agreement** | `Baseline` already carries `humanLabelFailingTaskIds`, `divergingTaskIds` and `armsDiverge()` (`:119`). Report the count and the ids; **exclude a diverging task from the deciding score** and carry it as a separate reported number rather than letting the looser arm decide it. | both arms are already recorded per task | the exclusion rule, and the report line |
| **Noise floor** | ≥3 repeats over the unchanged seed **after** the prompt fix, through `baseline/NoiseFloor.of(FrozenTaskSet, List<ScoredAnswers>)` (`:99`) + `answersMoved()` (`:117`), written to `evidence/noise-floor.json`. A measured 0.0 stays 0.0 — POC 27's own rule, kept. | the whole record + `outerloop/NoiseFloor.measured`/`injected` (`:45`, `:55`) | nothing; this is a re-measurement the prompt fix makes meaningful |
| **Distinct requests** | For N tasks with distinct `input` values (`eval/EvalTask` is `(name, input, expected)`, `eval/EvalTask.java:20`) the N answers MUST NOT be all equal. Checked on the answers, because `eval/AgentSpecInvoker` is `String invoke(String input)` (`:17`) and exposes nothing else. | nothing — a grep for a distinctness assertion across every `*.java` in the repo returns no hits | the assertion, as a launcher precondition that stops the run and records why |

The static half of the last guard is the preceding change's validator rule (a step that declares inputs
and names none is rejected). The live half is this change's, and it is deliberately a **launcher**
precondition: it fires after task 1 and task 2 disagree-or-don't, not after all sixty calls.

### D7 — The budget is arithmetic, and the wall clock is part of it

`outerloop/Budget.declare(maxModelCalls, maxGenerations)` (`:33`) and `admits(spent, costPerCandidate)`
(`:51`) plus `outerloop/MeteredInvokerFactory` (`:54`) already do the accounting. What this change adds is
the input: `costPerCandidate = 1 deploy + N task invocations`, declared from the task count before the
first call, and a **wall-clock** ceiling beside the call ceiling, because POC 27 measured two 60 s
timeouts on the one real completion it got — a 60-call budget there lands in hours, not minutes. The
reason for any spend is written in the same place as the number, and the launcher refuses when the
arithmetic does not fit the ceiling.

### D8 — The packs must have a live consumer, or they repeat this change's own premise

An unread pack is not a new hazard here — it is the hazard this change exists to close. The builder's flow
today lives only in Java, which is why POC 27 had to assemble its candidate in Java at
`outerloop/PolicyImprover.java:161-162` rather than mutate one. A pack no code loads would reproduce that
exactly, in a new directory. So the packs are not documentation: an offline
builder test loads each pack with `io/AgentSpecReader`, validates it with `AgentSpecValidator`, and
deploys it through `AgentSpecInvokerFactory` behind a fake invoker, and POC 27's outer loop deploys them
for real. A pack that fails to load or validate is a build failure, not a stale file.

The generator prompts keep their existing consumer throughout — `defaultPrompt()` reads them from the
classpath (`core/SingleShotAgentSpecGenerator.java:60` → `bundledPrompt` `:70`, via `getResourceAsStream`
at `:71`) — so the packs are an
additional, mutable representation of the same instructions and must be read against that Java, not
instead of it (Risks).

The seam from D2 gets the same treatment: a named caller, and an offline end-to-end test that runs
`ProcessSpec` → per-stage descriptions → skills with a fake `SpecPromptCaller`, so the seam cannot be a
method only its own test calls.

### D9 — Agency needs a tool surface, not a format change, and the tool that decides it is `validate`

The builder's flow is already declared as an agent: `@Agent(planner = PlannerType.GOAP)` at
`core/BuilderAgent.java:52`, with its javadoc recording that `BuilderService` drives the three actions
directly in the order the planner would have chosen. Agency is therefore already present in the
declaration and deliberately bypassed in execution, for a reason the code states — determinism.

A pack's agent can reach deterministic services, because `toolGroups` is not a comment on a step: in
`embabel-agent-spec-1.5.4-20261112.000934-68.jar`, both `com.embabel.agent.spec.StepSpecAgentScopeBuilder`
and `support.PromptedActionSpecAction` reference tool groups, which is the execution path this change's
packs already travel. What is missing is anything to point them at. `@LlmTool` and `@Tool` appear **zero**
times in builder `src/main`, and in no other prod module — so a pack's steps can reach upstream's built-in
groups but never the builder's own compile, validate, scan or write.

**Decision: expose the builder's deterministic services as tools, and treat `validate` as the one that
earns the change.** An agent that can call only generators is a planner choosing among calls that always
run in the same order, which is the flow it replaces with extra ceremony. An agent that can call the
validator reads its own errors and re-drafts, which is the self-repair `BuilderService` performs today as
numbered stages in Java. The rest of the surface — compile, scan, write — is exposed on the same mechanism
without changing that argument.

The cost is stated rather than discovered later: every step is a model call, so a multi-step pack trades
today's single call for N. That is exactly why single-shot generation was chosen in the first place, and
this change does not retire it — `cli/BuildCommand` stays on the Java generators (D8). The packs are where
the agentic form exists and is measured, not where the shipped build goes.

*Alternative considered*: the flow as prompt-backed steps with no tools, a pure chain of model calls.
Rejected — the deterministic half would then have to be re-prompted, which is a worse version of the Java
it replaces, and it forfeits the only behaviour agency adds here.

**Measured 2026-11-21 (task 8.1), which narrows the claim above without overturning it.**
`eval/ToolGroupBindingTest` deploys single-step packs through the production
`eval/AgentSpecInvokerFactory.invokerFor` behind a platform double that records the scope handed to
`deploy`, so the scope builder, `PromptedActionSpec.emit`, the assembler and the writer all run unmocked.
A declared group does arrive at the compiled `Action` as a `ToolGroupRequirement` whose `role` is the
declared string. But it arrives as a **pass-through name**, not as a resolved capability:
`PromptedActionSpec.emit` never reads the scope's offered-group list, `DefaultAgentPlatform.deploy` is a
map insertion plus an event, and `AgentSpecValidator` has no tool-group code path at all. A pack that
declares `web` and a pack that declares `webb` are therefore indistinguishable at deploy time — the
misspelling is carried verbatim, and the test names the case for it. The validator's blindness is a
measured absence, not an unreached code path: the same validator, on the same pack shape, does raise
`DANGLING_TYPE_REF` for a bad *type* name, so a clean result here means the check is missing rather than
that nothing was examined. This is the same family as POC 27's unnamed prompt variable, with the aggravating
difference that the prompt-variable case acquired a check (`PROMPT_UNDECLARED_INPUT`) and this one has none.

Two consequences follow, and both are cheaper to state now than to find in section 3. First, a step
declaring a group cannot be shown offline to be *able to use* it: resolution happens at prompt time, when
`support.PromptedActionSpecAction` hands the requirement set to `PromptRunner.withToolGroups` and the
running platform's `ToolGroupResolver` answers. That half is out of reach without a model call, so the
test carries an endpoint-gated case asserting the precondition D9 silently assumes — that a booted platform
publishes a group whose role a step could name. Second, and less expected: the roles upstream publishes are
`web`, `math`, `maps`, `github` and `browser_automation` (`CoreToolGroups` in
`embabel-agent-api-1.5.4.jar`), while `security/TrustTier.java` allowlists `search`, `web`, `file` and
`shell`. Three of those four are not core roles, so a `T3_PARTNER` pack naming `file` or `shell` may point
at a group that resolves to nothing even though it is spelled correctly by this repository's own lights.
Whether anything registers those names at runtime is exactly what the gated case asks.

### D10 — The scorer stays outside the mutable pack by placement, and the reach control is the trust tier that already exists

The builder's eight prompts split by whom they serve, not by package. Five serve generation —
`process_to_flow.jinja` (`core/SingleShotAgentSpecGenerator.java:34`), `process_to_flow_multi.jinja`
(`core/SingleShotProcessSpecGenerator.java:35`), `business_process_discovery.jinja`
(`blueprint/ProcessExtractor.java:36`), `generate_skill.jinja` and `generate_evals.jinja`
(`skill/SkillPrompts.java:22,24`). Three serve the scorer — `grade_eval.jinja`, `agent_baseline.jinja`,
`agent_with_skill.jinja`, all declared in the same file at `skill/SkillPrompts.java:26,28,30`. Those three
decide whether a generated artifact is good, and POC 27's `evidence/MUTABILITY.md` already fixes the
scoring rule as immutable on the grounds that an improver which scores itself optimises its own scorer.

**Decision: the three scoring prompts remain classpath resources read by `SkillPrompts` and become steps
of no pack.** This uses the same containment as D3 rather than a new rule: a mutation that edits pack
files cannot reach text that is not in a pack. Where a pack needs to be limited in what it can *ask for*,
the mechanism is already in the module — `security/TrustTier.java` names the groups per tier
(`:21` `T1_COMMUNITY` allows none, `:24` `T2_VERIFIED` search and web, `:27` `T3_PARTNER` adds file and
shell, `:30` `T4_PLATFORM` allows `*`), read per pack by `security/TrustTierPolicy.java:31` and
`security/AgentSpecSecurityScanner.java:241`. So D9's tool surface does not need a new guardrail: a pack's
tier is what bounds it, and the scan already runs at build time. Task 8.1 put a limit on that sentence: a
tier bounds a pack only where the names it allowlists are names something resolves, and three of the four
(`search`, `file`, `shell`) are not upstream core roles, so tier 3's extra reach may be a list of names
rather than a list of capabilities. The control is real as a *policy* surface — it is read per pack, and a
pack cannot widen it by editing itself — but its enforcement depends on the resolver question D9 now
carries.

Task 8.2 measured the rest of that sentence, and it runs the other way. `TrustTierPolicy.check`
(`security/TrustTierPolicy.java:52-55`) compares the *declared strings* against the allowlists and
resolves nothing, so a pack's tier moves on any declaration at all: `web` or `search` gives T2, `file` or
`shell` gives T3, and any other name — a typo, or a builder tool that has no upstream group to name —
lands at `T4_PLATFORM`, because T4 allows `*` and `minimumTier`
(`security/AgentSpecSecurityScanner.java:251-256`) therefore always terminates there. The tier is a policy
surface a reviewer reads, not a reach control: it neither grants a tool nor withholds one, and an
unresolvable declaration reads as maximally privileged rather than as an error. What bounds a pack is
placement — the scoring text lives outside the pack directories — and that is the part task 8.4 now
guards by content rather than by filename. The heading above records the decision as taken; this
paragraph is what the measurement left of it.

*Honest gap*: nothing today stops a later commit making a grading prompt a step of a builder pack. The
guard this change can afford is offline and cheap — assert that no step of any pack under
`builder-agent-specs/` carries a prompt matching one of the three scorer resources — and it is asserted
against a conforming pack as well, so it cannot be satisfied by naming. The residual risk is a grading
step written in different words, which no file check catches; that one is a review obligation, and it is
recorded as one in Risks rather than claimed as closed.

### D11 — A multi-agent builder flow is a `ProcessSpec`, which the format already has

`model/ProcessSpec.java:44` is `ProcessSpec(ProcessSpecManifest manifest, List<AgentSpec> agents)`, with
`withAgents(List<AgentSpec> reordered)` at `:100`, `totalSteps()` and `finalOutputType()`. The three
builder agents are therefore a `ProcessSpec` of three `AgentSpec`s — the container is existing, and
`eval/AgentSpecInvokerFactory.invokerFor(ProcessSpec)` (`:69`) already deploys one as a single scope, which
is how the hand-off between two agents works at all (design Context).

The consequence worth fixing down is that **agent ordering is not part of the mutable surface.** Reordering
is reachable in the model through `withAgents`, but the only mutation operator in the loop rebuilds an
`ActionStepSpec` (D3), which cannot move an agent. So ordering is fixed by construction, consistent with
the deferral in D3, and a claim that the loop improves collaboration structure would be false.

A related fact, recorded because it bounds any future widening: `AgentSpecValidator` never inspects a
step's `pre` or `post` condition names — no reference to either appears in the file — and the spec source
resolves no conditions, so an edited condition name surfaces at deploy rather than at validation. Today
the loop cannot reach those fields; if a mutation operator ever does, that gap needs closing first.

### D12 — The frozen task set stays six files; headroom tasks live beside it, not inside it

Task 6.1 asks for task files the unmutated packs fail, and names
`poc/poc-27-recursive-self-improvement/src/main/resources/benchmark/tasks/` as where they go. The four
authored files do live in this module, but in `benchmark/tasks-headroom/`, and the reason is measured
rather than stylistic.

The frozen set's file count is not a description of the benchmark, it is an input to the system.
`Baseline` reads `tasks.ids().size()` for its denominator and for the `2/6` its verdict quotes as design
intent; `Budget.costPerCandidate(frozenTaskCount)` and `OuterLoopProcess.budget(frozen)` derive the
declared spend from it. With the four files inside the directory, the module measured **23 failing
assertions across ten classes**, and among them `OuterLoopProcessTest` did not fail on a number — it
failed with `OuterLoopRefused`, because ten tasks declare `5 x (1 + 10) x 2 = 110` calls, which at the
60 s per-call figure the POC measured is 6600 s against the 5400 s wall-clock ceiling, so `runPass`
refuses before spending anything. Growing the set would therefore have required raising a guardrail that
exists precisely because a call ceiling alone lets a run take hours.

Behind several of those assertions sit `evidence/baseline.json` and `evidence/noise-floor.json`, measured
over six prompts and cited as `2 of 6` in `docs/04` and the module README. D7 already treats the budget as
arithmetic the evidence feeds, so changing the denominator means re-measuring the evidence, not editing
the assertions — and this change planned a re-run for the noise floor only.

So: the launcher keeps scoring the six, every recorded denominator stays true, and
`baseline/AddedHeadroomTasksTest` is the one place that loads both directories — which is where the
headroom claim, including its all-correct stop branch, is actually checked. Folding the four in remains
available as a decision with a named price: re-measure both evidence files over ten prompts, update the
six fixtures and the `SpendCeilingTest`/`OuterLoopFixtures` arithmetic, and raise the wall-clock ceiling
past 6600 s or cut the generation count. What this change will not do is let the addition of benchmark
files make a guardrail change by side effect.

## Risks / Trade-offs

- **Transcribing a Java flow into prompts can silently change what the flow does.** Mitigation: the two
  transcribed packs are checked against the Java they describe — the step prompts carry the same
  instructions `defaultPrompt()` at `core/SingleShotAgentSpecGenerator.java:60` and
  `core/SingleShotProcessSpecGenerator.java:61` carry, and a reviewer diffing the two must be told which
  wording moved.
- **The headroom fix could be gamed by writing tasks the seed trivially fails.** Mitigation: the tasks
  are authored **before** any mutation and frozen, exactly as POC 27's `benchmark-task-set` is fixed in
  `MUTABILITY.md`; and each new task carries a written `check` field like `b27-t02-quality-floor.json`'s,
  which states why the answer cannot be guessed from the question.
- **Removing diverging tasks from the deciding score shrinks the deciding score.** Stated plainly: on
  POC 27's own numbers that is 2 of 6 tasks, leaving 4. A smaller honest denominator beats a larger one a
  reader cannot trust, and the excluded ids are reported, not hidden.
- **Tool groups are honoured upstream, not here, and this repository has never exercised them.** The
  evidence that `toolGroups` reaches execution is that `StepSpecAgentScopeBuilder` and
  `PromptedActionSpecAction` reference it (D9) — which is a reference, not a demonstrated behaviour.
  Mitigation: a test that a step declaring a group can use it and a step declaring none cannot. If
  upstream does not honour the declaration, D9's agency claim collapses and the builder's flow stays in
  Java; that outcome is stated here so a failing test reads as a resolved question rather than a surprise.
- **Exposing builder services as tools widens what a *generated* pack can ask for.** A pack the builder
  writes could declare `shell`. Mitigation: the install-time scan already reads declared groups
  (`security/AgentSpecSecurityScanner.java:241`, `security/TrustTierPolicy.java:31`) and `T1_COMMUNITY`
  allows none, so the exposure is bounded by the tier a pack is assigned, not by what the tool registry
  happens to hold.
- **The scorer split is enforced for copy, not for intent.** The offline assertion in D10 catches a grading
  prompt pasted into a builder pack; it cannot catch one rewritten in other words. A reviewer adding a step
  to a builder pack needs to know that `grade_eval`, `agent_baseline` and `agent_with_skill` are the
  scorer's, which is why D10 names the file they live in rather than describing them.
- **A multi-step pack costs N model calls where the shipped build pays one.** Mitigation: the shipped
  build is unchanged (D8), and the calls are declared as arithmetic before the run (D7), so the extra spend
  is a number chosen in advance rather than a surprise in the wall clock POC 27 measured at 60 s timeouts.
- **Three packs plus a seam plus four guards is more than one change's worth of surface.** Mitigation:
  the guards are POC-side and offline; the product-side additions are one seam and three resource
  directories. If the seam is cut, POC 27's second leg still runs against the other two packs.
