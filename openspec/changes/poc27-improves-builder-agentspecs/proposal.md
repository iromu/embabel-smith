# Proposal

## Why

Two gaps, one of which the record already names.

**The builder's own process was never written down in the format it builds.** The builder *is* an agent —
`core/BuilderAgent.java:52` is `@Agent(planner = PlannerType.GOAP)` with `compileFromDescription`
(`:90`), `validateDraft` (`:130`) and `writeAgentSpec` (`:142-144`, `@AchievesGoal`). But that flow exists
only as Java annotations. Nothing in the repository holds the builder's process as an AgentSpec, so
there is nothing for an improvement loop to mutate: POCS 24 and 25 generate *other* people's packs, and
POC 27's outer loop had to hand-build a one-step pack in Java to have a candidate at all
(`outerloop/PolicyImprover.java:161-162`). The builder's prompts are equally unreachable — and
reaching them means editing the builder: `SingleShotAgentSpecGenerator.defaultPrompt()`
(`core/SingleShotAgentSpecGenerator.java:60`) and `SingleShotProcessSpecGenerator.defaultPrompt()`
(`core/SingleShotProcessSpecGenerator.java:61`) read them off the classpath through `bundledPrompt`
(`core/SingleShotAgentSpecGenerator.java:70`, via `getResourceAsStream` at `:71`), so a prompt is live but is
not a versioned, gated artifact a loop could propose a change to.

**The second gap is the one POC 27's own verdict says to close first.** Its README's Recommendation is
two things, in order: make a candidate see its input, then author held-out corpus lines the hand-written
policy *fails* so the loop has a derivative to climb at all
([`poc/poc-27-recursive-self-improvement/README.md`](../../poc/poc-27-recursive-self-improvement/README.md),
[`docs/04-poc-run-results.md`](../../docs/04-poc-run-results.md) §7.5). Measured there: the labelled
corpus is saturated (hand-written and seed both 1.0/1.0 held-out, every mutation strictly worse); the
gate arm and a human disagree on 2 of 6 tasks and the looser gate arm is the one that decides; the noise
floor is a measured 0.0; and the outer loop's candidate prompt interpolated no `{{userInput…}}`, so six
distinct eval tasks sent six byte-identical requests. **No test in the repository compares answers across
eval tasks** — a grep for a distinctness assertion across every `*.java` returns nothing — so all four
conditions are unguarded today.

This change puts the builder's process in AgentSpec format, moves POC 27's second leg onto it, and buys
the measurement validity the first run lacked before it spends another call.

## What Changes

- **Three builder packs, in the builder.** `builder-agent-specs/` inside
  `prod/embabel-smith-builder/` — a module directory, not a packaged resource, because packs are read from
  the filesystem by `io/AgentSpecReader.read(Path)` (design D1). One pack per process the user named:
  skills from a process
  specification, a multi-agent composition, and a single multi-step spec. They use the pack layout the
  repository already proves (`agent-specs/explainer/{manifest,goal,steps/*,types/*}.yml`) and the
  `{{userInput.content}}` interpolation that pack already uses. Two of the three are **already built** in
  Java and are transcribed, not written: decomposition exists as
  `core/ProcessSpecGenerator` / `SingleShotProcessSpecGenerator.generateProcess(String)` (`:66`), single
  multi-step generation as `SingleShotAgentSpecGenerator.generate(String)` (`:83`).
- **One genuinely new seam: `ProcessSpec` → the skill pipeline.** Today
  `skill/SkillBuilderService.build(String description, PublishTarget, BuildOptions)` (`:101`) and
  `skill/SkillGenerator.generate(String skillName, String description)` (`:56`) take free **text**, and
  `grep -rn ProcessSpec prod/embabel-smith-builder/.../skill/` returns **zero** hits — there is no path
  from a decomposition into the skill pipeline. That seam is a prerequisite for the first pack, not
  something to reuse.
- **POC 27's second leg retargets.** The improvement object becomes the builder's own packs; the mutable
  surface is their **step prompts**, which is exactly what the existing mutation primitive already
  touches — `builder/improve/Refiner.withPrompt` (`:157-160`, moved out of the studio by the preceding
  change, which has landed) rebuilds an `ActionStepSpec` changing only the prompt. Deterministic gates stay host Java and
  permanently outside the surface, for a structural reason design D3 states.
- **Four measurement guards, each with a number attached**: seed headroom proven non-saturated before any
  mutation; gate-versus-label agreement reported and a diverging task removed from the deciding score; the
  noise floor re-measured after the prompt fix; and a new assertion that distinct eval tasks produced
  distinct answers. Three of the four have machinery already in POC 27 (`baseline/Baseline.of` `:94`,
  `armsDiverge()` `:119`, `ScoredAnswers.gateArmHasHeadroom()` `:73`, `baseline/NoiseFloor.of` `:99`) and
  are gaps in *use*, not in code. The distinctness check is new code.
- **A declared call budget, computed from arithmetic before the first call**, reusing
  `outerloop/Budget.declare` (`:33`) / `admits` (`:51`) and `outerloop/MeteredInvokerFactory`, with the
  wall-clock ceiling POC 27 measured (two 60 s timeouts on the one real completion) applied as well as
  the call count.

## Impact

- **Depends on** `builder-owns-improvement-loop` landing first. Its trace sink is already in the tree
  (`smith-common/src/main/java/com/embabel/smith/common/trace/TraceRecord.java` exists and
  `runtime/trace/` now holds only `JsonlTraceStore`, `TraceRecorder`, `TraceRecordJson`); its twelve-class
  move and `CandidateStore` are **not** done — `prod/embabel-smith-studio/.../studio/improve/` still holds
  all twelve files and `grep -rn CandidateStore prod/` is empty. This change refers to the loop by its
  post-move names and does not restate that change's content.
- Affected code: `prod/embabel-smith-builder` (three resource packs, one skill seam, no new dependency —
  the builder already holds `eval/AgentSpecInvokerFactory`, which deploys a pack or a whole `ProcessSpec`
  through `AgentPlatform` directly at `:50`, so the builder can execute these packs without the runtime
  module), `smith-common` (only if a validator rule is needed beyond the preceding change's),
  `poc/poc-27-recursive-self-improvement` (in place: the outer loop's candidate source, the corpus, the
  guards).
- **`poc/` is treated as evidence**: POC 27 is reframed in place because the user chose that over a POC
  28, so its existing 123 offline tests and its recorded artifacts stay; edits are additive and the
  README's measured numbers are not rewritten.
- Out of scope: changing what `cli/BuildCommand` does. The packs are the builder's process **written
  down**, and POC 27 is their first executing consumer; rewiring the shipped build path onto them is a
  separate decision with its own risk. `docs/`, `README.md` and the wiki are updated by the tasks, not by
  this proposal.
