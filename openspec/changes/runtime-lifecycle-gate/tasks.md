# Tasks

## 1. Read the state at ingest

- [x] 1.1 Add a small reader that returns a pack directory's declared lifecycle state, or "no state"
      when `lifecycle.yml` is absent or unreadable, using `com.embabel.smith.common.io.Yaml` from
      `smith-common` and no new module dependency. Verify with an offline unit test over three pack
      directories: sidecar `state: "RETIRED"`, sidecar `state: "ACTIVE"`, and no sidecar at all.
      *(New `core/LifecycleSidecar`; no new dependency — it reads one `state` key through
      `Yaml.mapper()`. `LifecycleSidecarTest` covers the three named cases plus a lowercase `retired`,
      a sidecar that is not a mapping, and a sidecar with no `state` key.
      `make test-module MODULE=prod/embabel-smith-runtime`: 56 tests, 0 failures, 0 Checkstyle
      violations.)*
- [x] 1.2 Confirm the fallback agrees with the studio's existing reader rather than inventing a rule —
      `ImprovementLoop.readSidecar` falls back to `LifecycleState.INCUBATING`. Verify by test that a
      missing sidecar and a corrupt sidecar both yield a runnable pack.
      *(Verified by `aPackWithoutASidecarHasNoStateAndStaysRunnable` and
      `aSidecarThatIsNotReadableIsNoStateAndStaysRunnable`. Agreement is on the *outcome*: the runtime
      records "no state" and treats it as runnable rather than defaulting the state to `INCUBATING`, so
      it never disagrees with the studio about runnability without importing the studio's default. The
      runtime module cannot name `LifecycleState` at all without the builder dependency this change
      rules out.)*
- [x] 1.3 Exclude a pack whose declared state is `retired` from `RuntimeCore.ingest`, and carry the
      excluded pack names on `IngestedSource`. Verify with a `RuntimeCoreTest` case that a two-pack source
      with one retired pack deploys one pack, and that a source whose only pack is retired fails as the
      existing empty-source failure does.
      *(Verified by `aRetiredPackIsNotDeployedAndIsNamedAsExcluded` (one pack deployed, the exclusion
      carried) and `aSourceWhoseOnlyPackIsRetiredFailsAsAnEmptySourceNamingIt` (the same
      `IllegalStateException` as before, with the excluded name appended). `IngestedSource` gained an
      `excluded` component of `ExcludedPack(name, goalName)`. The step-spec assembly now stages only the
      deployed roots, so a retired pack's actions are not plannable through another pack's goal either.)*
- [x] 1.4 Keep retirement out of the duplicate-goal check: two packs exporting the same goal where one is
      retired must not be reported as a duplicate. Verify with an offline test that the run proceeds and
      names the exclusion instead of the collision.
      *(Verified by `aRetiredTwinIsNotReportedAsADuplicateGoalCollision` — ingest succeeds, `dup-b` is in
      `excluded` — and `aLiveTwinStillWinsTheGoalItsRetiredTwinAlsoExported`, which runs the shared goal
      and asserts no throw: the block applies only when no deployed pack exports the goal.)*
- [x] 1.5 Report the exclusion by name where the source already reports skips. Verify by test that the
      excluded pack's name appears in the result, and prove the test is non-vacuous by mutating the
      reader to return `null` and watching it fail.
      *(The name appears in `IngestedSource.excluded` and in the empty-source message. Mutation M4 —
      `LifecycleSidecar.retired` returning `false` for every pack, i.e. the reader recording nothing —
      turned 8 of them red:
      `RuntimeCoreTest.aRetiredPackIsNotDeployedAndIsNamedAsExcluded:140`,
      `RuntimeCoreTest.aSourceWhoseOnlyPackIsRetiredFailsAsAnEmptySourceNamingIt:150`,
      `RuntimeCoreTest.aGoalOnlyARetiredPackExportsIsALifecycleBlockNotAGoalNotFound:173`,
      `RuntimeCoreTest.aRetiredTwinIsNotReportedAsADuplicateGoalCollision:160 » IllegalState duplicate
      goal name 'sameGoal' is exported by multiple packs: [dup-a, dup-b]`,
      `AgentSpecRunServiceTest.aLifecycleBlockIsAFailedRunThatStillEmitsATraceRecord:82`,
      `LifecycleSidecarTest.theDeclaredStateIsReadFromTheSidecar:33` and
      `LifecycleSidecarTest.retirementIsMatchedRegardlessOfHowTheWordIsCased:40`. Reverted;
      `grep -rn MUTATION prod/embabel-smith-runtime/src/main/java/` exits 1. Implemented as
      `retired() → false` rather than a `null` return, which is the same dead reader without a
      `Optional.of(null)`.)*

## 2. Name the block

- [x] 2.1 Add `core/LifecycleBlockedException` beside `core/TrustTierBlockedException`, with a stable
      message prefix and the retired pack's name in the message. Verify with a unit test asserting the
      message starts with the prefix and names the pack.
      *(New `core/LifecycleBlockedException` with `PREFIX = "Run blocked by lifecycle state"`;
      `aGoalOnlyARetiredPackExportsIsALifecycleBlockNotAGoalNotFound` asserts both
      `hasMessageContaining("Run blocked by lifecycle state")` and `hasMessageContaining("'explain'")`.)*
- [x] 2.2 Throw it from `RuntimeCore.run` when the requested goal was exported only by an excluded pack,
      in place of the `GoalNotFoundException` the exclusion would otherwise produce. Verify with an
      offline test that asking for a retired pack's goal raises the lifecycle block and not a goal-not-found
      error.
      *(`checkLifecycle` runs before `packForGoal`; the test also asserts
      `hasMessageNotContaining("No goal named")`. Mutation M5 — deleting the `checkLifecycle` call —
      turned exactly the two block tests red:
      `RuntimeCoreTest.aGoalOnlyARetiredPackExportsIsALifecycleBlockNotAGoalNotFound:174` and
      `AgentSpecRunServiceTest.aLifecycleBlockIsAFailedRunThatStillEmitsATraceRecord:82`; reverted.)*
- [x] 2.3 Add the new exception to the inner multi-catch in `AgentSpecRunService.run`
      (`catch (GoalNotFoundException | TrustTierBlockedException ex)`) so it becomes a failed run with the
      message as the error rather than falling into the outer catch-all. Verify with an
      `AgentSpecRunServiceTest` case that the result is a failure, that its error carries the prefix, and
      that a trace record was still appended.
      *(Verified by `aLifecycleBlockIsAFailedRunThatStillEmitsATraceRecord`. Stated honestly: for this
      path the inner and outer catch produce the identical outcome, so no mutation can distinguish them
      from outside — the red/green evidence above is the gate itself (M5). The clause is what keeps a
      policy block classified with the other policy blocks instead of under "failed before the run".)*
- [x] 2.4 Confirm the trace shape for a never-executed run is unchanged apart from the new reason —
      `steps` empty, `error` set. Verify with the existing trace tests plus one case for the lifecycle
      block.
      *(`TraceRecorderTest`, `JsonlTraceStoreTest` and `TraceRecordJsonTest` all still pass unchanged;
      the new case asserts `record.steps()` empty, `record.pack()` null and the prefix in `error`. The
      enumeration in `TraceRecord`'s and `RunOutcome`'s Javadoc now names the lifecycle block.)*

## 3. Keep the loop from refining a policy outcome

- [x] 3.1 In the studio, match the new prefix the way `Detector.TIER_BLOCK_PREFIX` matches the tier
      prefix, so a lifecycle block is excluded from the failures that trigger refinement. Verify with an
      offline `Detector` test that a window containing only lifecycle blocks queues nothing, mirroring the
      existing tier-block case.
      *(`Detector.LIFECYCLE_BLOCK_PREFIX` + `isLifecycleBlock`, mirrored as a literal the way the tier
      prefix is, per the design's named risk. `failedNonTierBlock` became `failedNonPolicyBlock` — it now
      excludes two policies and the old name would lie; it had one call site and no test or doc
      references. Verified by `lifecycleBlockDoesNotQueue`. Mutations: M6 (`isLifecycleBlock` matching the
      tier prefix instead) → `DetectorTest.lifecycleBlockDoesNotQueue:58` and
      `DetectorTest.theLifecyclePrefixMatchesWhatTheRuntimeThrows:71` red; M7 (the constant renamed to
      `"Run blocked by lifecycle gate"`) → the same two, at
      `DetectorTest.theLifecyclePrefixMatchesWhatTheRuntimeThrows:70`. Both reverted;
      `grep -rn MUTATION prod/embabel-smith-studio/src/main/java/` exits 1. The added
      `theLifecyclePrefixMatchesWhatTheRuntimeThrows` is the drift guard that makes the duplicated string
      fail loudly: it asserts the runtime's own exception message starts with the studio's constant.)*
- [x] 3.2 Verify the studio's own run path is covered by the runtime gate rather than duplicated there:
      read `RunController`/`AgentSpecRunService` usage and confirm no second lifecycle check is added.
      Verify by grep that the state is consulted in one place.
      *`grep -rn 'LifecycleSidecar.retired' prod/embabel-smith-runtime/src/main/java/` returns exactly
      one line — `RuntimeCore.java:113`. `RunController` gained no check; the studio's two remaining
      `lifecycle.yml` reads (`ImprovementLoop.readSidecar`, `SourceService`) are the loop's and the
      lifecycle pane's own readers, not gates.)*

## 4. Record the decision

- [x] 4.1 Add the gate to `docs/07-runtime.md` beside the trust-tier section: which state blocks, where
      the state is read from, what happens with no sidecar, and that a blocked run emits a trace record.
      Verify the page names a state that exists in `LifecycleState` and a file name that matches the
      sidecar the builder writes.
      *(New §"Lifecycle state" between "Trust tiers" and "Traces". It names `retired`, `INCUBATING` and
      `DEGRADED`, all in `LifecycleState`, and `lifecycle.yml`, which is the name
      `LifecycleStamper.write` writes.)*
- [x] 4.2 Narrow the "Lifecycle state is advisory in v1 — the runtime does not gate on it" sentence in
      `docs/10-self-improvement.md` to say retirement is enforced and the other states remain advisory.
      Verify the page no longer claims the runtime gates on nothing.
      *(Replaced by "Retirement is enforced; the other states are advisory", pointing at the runtime
      page.)*
- [x] 4.3 Update `TraceRecord`'s enumeration of never-executed run reasons in `docs/07-runtime.md` (and
      the class Javadoc) to include a lifecycle block. Verify the list matches the code.
      *(Class Javadoc updated (`TraceRecord`, and `RunOutcome.failed`, which carried the same two-item
      list). Deviation: `docs/07-runtime.md` had no such enumeration to update, so the page now carries
      one — a trace bullet listing the four reasons a never-executed run has a record, matching the code.)*
- [x] 4.4 Close the Runtime and storage item in `docs/12-backlog.md` with what was gated and what was
      deliberately left advisory. Verify the item's reasoning survives as the doc's rationale.
      *(The item is now "Closed — the runtime did not gate on lifecycle state" with the advisory states,
      the no-sidecar fallback and the no-override decision as its reasoning, plus two Check commands.)*
- [x] 4.5 Run the runtime module's offline suite and the studio's offline suite. Verify both pass with no
      endpoint configured.
      *`make test-module MODULE=prod/embabel-smith-runtime`: 56 tests, 0 failures.
      `make test-module MODULE=prod/embabel-smith-studio`: 127 tests, 0 failures. Both are the default
      `make test` entry point, so `test.excludedGroups=live` applies; the runtime's `RuntimeCoreTest`
      points the endpoint at a local 404 server rather than at any model.)*
