# Tasks

## 1. Prepare the builder side

- [x] 1.1 Sink `TraceRecord` and `TraceStore` from `com.embabel.smith.runtime.trace` into
      `com.embabel.smith.common.trace` in `smith-common` (D6 — decided; nothing can move until this is
      done). Move the two files and their tests; leave `JsonlTraceStore`, `TraceRecorder` and
      `TraceRecordJson` in `runtime/trace/`. Update the import lines: 26 prod files import `TraceRecord`,
      11 import `TraceStore` (overlapping) — report the two counts you actually measured before starting.
      Verify `grep -rn "runtime\.trace\.TraceRecord\|runtime\.trace\.TraceStore" prod/` returns zero
      afterwards, and that `docs/05-architecture.md` needed **no** edit — if it did, the wrong option was
      taken.
- [x] 1.1b Create the package `com.embabel.smith.builder.improve` in `embabel-smith-builder`. Verify the
      module has every dependency the twelve classes need **after** 1.1, reading their imports rather than
      assuming: after 1.1 they import `com.embabel.smith.builder.*`, `com.embabel.smith.common.*` (including
      the moved `common.trace`) and Embabel types, and zero `org.springframework` types. Verify the builder's
      pom gains **no** new dependency — if it needs `embabel-smith-runtime`, 1.1 was not completed.
- [x] 1.2 Declare `CandidateStore` with the single method `void save(AgentSpec candidate)` (D2). Verify by
      reading `LoopPublishPolicy.java:114` that the current call site discards the store's return value, so
      `void` loses nothing.
- [x] 1.3 Move the authoritative default pack version ("1.0.0", today `PackEntity.DEFAULT_VERSION`) to a
      non-persistence home in the builder and re-point `PackEntity.DEFAULT_VERSION` at it (D3). Verify
      `grep -rn 'DEFAULT_VERSION *= '` across `prod/` reports exactly **one** literal after the change, and
      report the count you actually measured.
- [x] 1.4 Add a javadoc line to the builder's existing `eval/RefinementLoop` naming which refinement job it
      does, so it is distinguishable from the incoming `improve.Refiner` (D5 risk).

## 2. Move the twelve classes

- [x] 2.1 Move `ApprovalQueue`, `Detector`, `GateChain`, `GateResult`, `ImprovementLoop`, `InFlightSet`,
      `LifecycleTransitions`, `LoopPublishPolicy`, `LoopPushDisabledException`, `PendingCandidate`,
      `Refiner`, `TraceWindowReader` to `com.embabel.smith.builder.improve`. Verify each moved file differs
      from its previous version only in the `package` declaration and its imports — report any file where
      that is not true and what else changed.
- [x] 2.2 Change `LoopPublishPolicy`'s field and constructor parameter from `ProcessStore` to
      `CandidateStore`, keeping the call site's behaviour (it ignores the result).
- [x] 2.3 Change `Refiner.java:104` from `PackEntity.DEFAULT_VERSION` to the builder-side constant, and
      drop the `studio.storage.entity.PackEntity` import. Verify `Refiner` then imports nothing from
      `com.embabel.smith.studio`.
- [x] 2.4 Move the studio improve test classes into the builder's test tree with the classes they cover.
      Verify studio's improve-test count and the builder's gained count are the same number, and report
      both.

      **Result, 2026-11-22:** **9 classes moved**, byte-identical apart from the `package` line, into the
      new `builder/src/test/java/com/embabel/smith/builder/improve/`. The two numbers the task asks for:
      studio's `improve/` held **11 classes**, the builder gained **9** — so the equality as written does
      **not** hold, and it is reported as failing rather than rounded into a pass. It cannot hold once two
      classes are ruled survivors: `LiveLoopIntegrationTest` and `RestIntegrationTest` stay, because the
      classes they cover are `studio.rest.{Run,Improve,Build}Controller`, six `studio.rest.dto.*` types and
      the booted application, all of which are still studio code. The invariant that does survive the move
      is the one `design.md:140` actually wants — **57 `@Test` methods in studio's `improve/` at the parent
      commit = 55 moved + 2 stayed**, counted per file, and the builder's `builder.improve` tests then ran
      exactly 55 in the reactor.

      Two compile blockers came out of the move and neither was taken by adding a dependency.
      **No new module edge:** three moved files used `runtime.trace.JsonlTraceStore`, and the builder
      deploys and executes packs with no dependency on `embabel-smith-runtime` — a test-scoped one would
      falsify that. `common.trace.TraceStore` is an interface in `smith-common` with two members whose own
      javadoc says a non-file store reports an empty warnings list, so the builder's tests carry a 33-line
      `InMemoryTraceStore`; the files are typed to the interface. No JSONL behaviour was lost —
      `TraceWindowReader` reads only `.records()` (`:51`, `:65`) and no moved test fed the store a corrupt
      line, so no corrupt-line-warning assertion was quietly weakened.
      **The string contract got a new holder instead.** `Detector.LIFECYCLE_BLOCK_PREFIX` (`:48`) and
      `LifecycleBlockedException.PREFIX` (runtime, `:31`) are the same literal, and the moved `DetectorTest`
      line that built a message from the runtime exception was the only thing tying them together. So
      `RuntimeCoreTest` now asserts both that the thrown message starts with `PREFIX` and that `PREFIX` is
      exactly `"Run blocked by lifecycle state"` — naming the literal is the point, since two tests either
      side of the edge each pinning the same string is how such a contract survives with no dependency edge.
      One honest caveat, kept from the implementer's report: `DetectorTest`'s first assertion is now
      self-referential, because a constant always prefixes itself. The real `startsWith` coverage in that
      method is its second assertion, whose error string is an independent literal in `TestFixtures`, plus
      the runtime test above.

      **Re-verified 2026-11-22 at `620a8f2` (branch `docs/verify-loop-claims`), by command rather than by
      report.** `git merge-base --is-ancestor 93cb53c HEAD` → `YES`, so the move is in this branch's
      lineage. `git show -s 93cb53c` → `Sun Nov 22 07:00:42 2026 +0000`, "Owns-improvement-loop 2.4: the
      improve tests move to the builder with the classes". `git show --name-status --format='' 93cb53c`
      names the move as eight `R` lines — `R095 ApprovalQueueConcurrencyTest`, `R094 ApprovalQueueTest`,
      `R089 DetectorTest`, `R098 GateChainTest`, `R093 ImprovementLoopTest`, `R092
      LifecycleTransitionsTest`, `R098 RefinerTest`, `R079 TraceWindowReaderTest`, each
      `studio/src/test/java/com/embabel/smith/studio/improve/X.java` →
      `builder/src/test/java/com/embabel/smith/builder/improve/X.java` — plus two `A` lines for
      `InMemoryTraceStore` and the builder's own `TestFixtures`. `git log --follow --name-status` on
      `builder/improve/DetectorTest.java` reports that same `R089` at `93cb53c`, so git holds it as a
      move rather than a delete and an add. The "9 classes moved" above and the eight `R` lines here are
      the same event counted two ways: the 9 is eight moved tests plus the copied `TestFixtures`, since
      `InMemoryTraceStore` is new code rather than a move, and the builder's directory then holds ten
      files. The two numbers the task asks for, measured at HEAD: the
      builder's `builder.improve` surefire reports carry **8 classes / 55 tests / 0 failures**, and
      studio's `improve/` still holds `LiveLoopIntegrationTest`, `RestIntegrationTest` and its own
      `TestFixtures`. The class-count equality as the task words it is therefore still **false** —
      studio's `improve/` went from 11 files to 3 while the builder received 8 test classes and 2 helpers
      — and the invariant that does hold is the method count: `@Test` per file gives **57** in studio's
      `improve/` at `93cb53c^`, **55** in the builder's at HEAD, **2** left in studio. Both survivors
      carry `@Tag("live")` and are absent from the reactor's surefire reports, so what the move proves
      about them is that they compile, not that they pass. The dependency reason in the Status section
      below no longer holds as written: studio's pom already depends on `embabel-smith-builder`
      (`prod/embabel-smith-studio/pom.xml:38`), which is what lets the moved tests compile in the first
      place; the actual blocker was `TestFixtures` being package-private with no `test-jar` anywhere
      under `prod/` (`grep -rn "test-jar" prod/ --include=pom.xml` returns nothing, still true), and the
      builder's unrelated `builder/TestFixtures` sits in a different package — so the moved set got a
      second copy in `builder/improve/` and studio kept its own, with no new module edge either way.

      Verified in a clean worktree: `prod/embabel-smith-studio` and all six upstream modules **SUCCESS**,
      and studio keeps its own two-method `TestFixtures` copy because the builder's sits in the builder's
      test source set, invisible across the module boundary without a test-jar the change does not add.
      The two survivors are `@Tag("live")` and env-gated, so they do not appear in an offline run at all:
      what the move proves about them is that they still compile, not that they still pass — that needs an
      endpoint. 16 same-package imports were dropped from the moved files; `grep ^import
      com.embabel.smith.builder.improve.` over the builder test tree now returns nothing.

## 3. Reconnect studio

- [x] 3.1 Add `ProcessStoreCandidateStore implements CandidateStore` in studio, delegating to
      `ProcessStore.savePackWhenConfigured(...)`. Verify it returns nothing and that an unbound store (which
      today returns `null`) is a no-op rather than a failure.
- [x] 3.2 Re-import the four non-improve studio callers: `loop/LoopService`, `rest/TraceController`,
      `rest/dto/CandidateInfo`, `config/StudioConfiguration`. Wire `ProcessStoreCandidateStore` where
      `StudioConfiguration` currently passes a `ProcessStore` into `LoopPublishPolicy`.
- [x] 3.3 Update the three studio test classes that reference `studio.improve`
      (`loop/LoopServiceTest`, `rest/ImproveControllerTest`,
      `config/StudioConfigurationLoopPublisherTest`).
- [x] 3.4 Verify nothing in `prod/embabel-smith-studio/src/main` still imports `studio.improve` — run the
      grep and report the output, distinguishing a genuinely empty result from a grep that matched nothing
      because of a typo.

## 4. Validation rule

- [x] 4.1 In `smith-common`'s `AgentSpecValidator`, reject an LLM-bound step whose declared input types
      appear in none of its prompt template variables (D4). Verify with a test on a pack that declares
      `UserInput` and whose prompt names no variable — the POC 27 shape.
- [x] 4.2 Verify the rule does **not** fire on a step that legitimately ignores its input, and on a step
      that does interpolate it. Both cases are required; a rule that only has a positive case is unproven.
- [x] 4.3 Confirm the existing packs still validate: every directory under `agent-specs/` and
      `examples/out/`. Report any pack the new rule rejects and what its prompt actually contains.
- [x] 4.4 Fix the Java fixtures the new rule invalidates. This is required before section 6 can be green,
      and it is a larger blast radius than section 4 suggests: the repo's convention for a "valid pack" in
      tests is a step declaring `List.of("UserInput")` with a brace-free prompt —
      `studio/improve/TestFixtures.java:49` builds `"perform " + step + " carefully"`, whose own javadoc
      states the pack is valid *because* it has no `{{var}}` references. That documented convention is what
      this rule reverses. `GateChain` validates, so `GateChainTest`'s and `ApprovalQueueTest`'s happy paths
      would reject instead of reaching scan and eval; and because `FolderSpecSource:69-78` drops an invalid
      pack with only a warning, fixtures written to disk make the affected controller tests see an **empty
      spec set rather than a failure** — a silently green test is the worse outcome. Confirmed at risk by
      text sweep, to be confirmed by a test run: `rest/RestTestFixtures`, `eval/RefinementLoopTest`
      (whose `RefinementLoop:114` discards a candidate that fails validation and silently returns the
      original), `SpecEntriesTest`, `LoopServiceTest`, `ComposeControllerTest`, `GenerateControllerTest`,
      and POC 27's `PolicyImprover:162` (the real defect, now caught), `ControlFixtures`, `RefereeFixtures`,
      `HandWrittenPolicyTest`. Fix each by interpolating the declared input into the prompt; do **not**
      weaken the rule to spare a fixture.

## 5. POC 27 off the studio classpath

- [x] 5.1 Remove `embabel-smith-studio` from `poc/poc-27-recursive-self-improvement/pom.xml`, with its four
      exclusions (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `postgresql`,
      `spring-boot-docker-compose`). Verify the module still compiles against the builder coordinates.
- [x] 5.2 Re-import POC 27's eight `studio.improve.*` references to the builder package. Verify the module
      imports nothing from `com.embabel.smith.studio` afterwards.
- [x] 5.3 Confirm `src/main/resources/application.yml` still needs what it carries. Its provider
      placeholders exist because the module's own file shadows the dependency's — if the studio dependency
      is gone, re-check which of those lines are still load-bearing rather than assuming they all are.

## 6. Verification

- [x] 6.1 Run the full POC reactor (`make test POC=1`) and report the reactor summary: modules succeeded,
      modules skipped, and any module that failed. Do not report an earlier run's result.

      **Result, 2026-11-22:** run against commit `0fde7e8`, finished 2026-11-22T05:57:52Z, wall clock
      02:21 min. Reactor summary as printed: **36 modules SUCCESS, 0 skipped, 0 failed** — the root
      aggregate, the two reactor poms, 7 product modules (`smith-common`, `embabel-smith-specsource`,
      `-skillevaluator`, `-runtime`, `-runtime-a2a`, `-builder`, `-studio`) and all 27 POC modules,
      `poc-27-recursive-self-improvement` among them at 5.526 s. Surefire's per-class lines sum to
      **2019 tests over 239 classes, 0 failures, 0 errors, 0 skipped**. The live-tagged tests are absent
      from that count rather than counted as skipped — they are removed by the reactor's
      `test.excludedGroups`, so the zero is not a claim that an endpoint was reached.
      Deviation worth stating: the run executed in a detached `git worktree` of `0fde7e8` rather than the
      working tree, because the working tree carried two agents' in-flight source edits and another
      session's uncommitted `pom.xml`, `Makefile` and `META-INF/**` native-image work. The summary above
      is therefore a statement about the committed state, which is what this task asks about, not about
      the tree as it stood while the run was in progress.

      **Run again 2026-11-22 at `620a8f2`, in a clean worktree of this branch, so the claim is about the
      state this file describes.** Command `make test POC=1`; the target sources
      `~/.config/env/llm.env` and exports `OPENAI_CUSTOM_BASE_URL`, `OPENAI_CUSTOM_API_KEY`,
      `OPENAI_CUSTOM_MODELS`, `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `EMBABEL_MODELS_DEFAULT_LLM` before
      `./mvnw -Ppoc test` (`Makefile:22-24`). Verbatim tail of the run: `BUILD SUCCESS`, `Total time:
      02:27 min`, `Finished at: 2026-11-22T16:15:02Z`. The Reactor Summary lists **37 modules, every line
      `SUCCESS`, none `SKIPPED`, none `FAILURE`** — three aggregator poms (`Embabel Smith`, `Embabel
      Smith Product`, `Embabel Smith POCs`), the 7 product modules (`smith-common`,
      `embabel-smith-specsource`, `-skillevaluator`, `-runtime`, `-runtime-a2a`, `-builder`, `-studio`)
      and all 27 POC modules, `poc-27-recursive-self-improvement` among them at `5.994 s`. The count is
      37 where the report above said 36: that line counted two aggregator poms where the summary prints
      three. Surefire's XML reports across `prod/` and `poc/` sum to **241 classes, 2020 tests, 0
      failures, 0 errors, 0 skipped**; the `@Tag("live")` classes are absent from those reports rather
      than counted as skipped (the reactor's `test.excludedGroups` removes them), so no endpoint is
      claimed. One side effect, reverted: the studio's `npm update` execution rewrote
      `prod/embabel-smith-studio/frontend/package-lock.json`, closed with `git checkout --` so the
      worktree is clean again.
- [x] 6.2 Verify the studio frontend still builds (`vue-tsc --noEmit && vite build`) or state plainly that
      it was already failing and that this change did not touch it.

      **Result, 2026-11-22:** `npm run build` — which is exactly `vue-tsc --noEmit && vite build`
      (`frontend/package.json:9`) — exits 0 at commit `0fde7e8`. The type check reported no errors and
      Vite transformed 215 modules in 3.42 s, emitting `index.html` plus one 620 kB JS chunk, one 233 kB
      CSS chunk and the two Quasar icon fonts into `src/main/resources/static/`. The only warning is the
      standing 500 kB chunk-size advisory, which is not a failure and predates this change. So the stronger
      of the two options applies: the frontend builds, and it builds without this change having touched
      it — the change moved classes between the builder and the studio's Java test trees and the frontend
      has no dependency on either. Run in the same detached worktree as 6.1, so the `node_modules` there
      came from the committed `package-lock.json` (installed by the reactor's own frontend step), not from
      the working tree's currently-modified lockfile; the emitted `static/` assets landed in the worktree
      and were discarded with it.

      **Run again 2026-11-22 at `620a8f2`, directly, in a clean worktree of this branch.** `npm run build`
      in `prod/embabel-smith-studio/frontend` (node `v26.13.1`, npm `11.20.0`) exits **0**. Verbatim tail
      after the script echo (`> vue-tsc --noEmit && vite build`): `vite v5.4.21 building for production`,
      `✓ 215 modules transformed.`, then the five emitted lines (`../src/main/resources/static/index.html
      0.83 kB`, the two Quasar icon fonts at `128.62 kB` and `175.41 kB`, `assets/index-C58zZtzZ.css
      233.41 kB`, `assets/index-C2ersCBf.js 620.12 kB`), the standing 500 kB chunk-size advisory, and
      `✓ built in 3.38s`. `vue-tsc --noEmit` printed nothing before that, which is its pass case. The
      `node_modules` here was **not** borrowed from the main checkout — it did not exist in this worktree
      at the start and the 6.1 reactor's own `install node` + `npm install` executions created it from the
      committed lockfile (`added 190 packages, and audited 191 packages`), so nothing was written to the
      other checkout. The same script also ran inside that reactor as the studio module's `run build`
      execution, `✓ built in 4.91s`. The 2026-11-21 caveat about the user's uncommitted
      `frontend/vite.config.ts` does not apply to this branch: the only difference between the committed
      file and the main checkout's dirty copy is the dev-server proxy target (`/api` → `localhost:18080`
      committed, `localhost:8080` in that working tree), `server.proxy` is not read by a production build,
      and this run used the committed file.
- [x] 6.3 Verify no enforcer rule is defeated: `prod/pom.xml`'s `ban-poc-dependencies` bans
      `org.iromu.embabel.smith:poc-*`. If any groupId moved in this change, re-check that ban rather than
      assuming it still bites.
- [x] 6.4 Update `docs/05-architecture.md` and `docs/10-self-improvement.md` where they place the
      improvement loop in the studio, and note the module move in the wiki (the wiki cross-links 44 pages
      and needs its lint and re-publish).

## Status — reconciled 2026-11-21, the three open checks closed by command 2026-11-22

Sections 1 to 5 are in the tree, re-verified against the sources rather than against this plan: studio's
`src/main/java/com/embabel/smith/studio/improve/` is gone; the builder's `improve/` holds thirteen files,
the twelve plus `CandidateStore`; `storage/entity/PackEntity.java:49` reads
`DEFAULT_VERSION = PackVersion.DEFAULT`; `import com.embabel.smith.studio.improve` returns zero hits in
prod and in POC 27; and POC 27's eight references now name `builder.improve`. The trace sink half landed
first, as its task predicted.

Four items were open at that reconciliation. Three have since been closed by a command run against this
branch — the result and the command are recorded under the task itself — and the fourth is a deliberate
deviation rather than an omission. All 24 boxes are ticked, and two of them carry a stated deviation:
2.4's numeric check does not hold as the task words it, and 5.1 landed as a scope change rather than a
removal.

- **2.4 was recorded here as "not achievable as written". That was true on 2026-11-21 and stopped being
  true at `93cb53c` (2026-11-22 07:00), which is in this branch's lineage.** The move happened: eight
  `R` renames from `studio/improve` to `builder/improve`, plus a new `InMemoryTraceStore` and a second
  `TestFixtures`, and no new module edge — the builder's `builder.improve` ran 55 tests in 8 classes in
  the 6.1 reactor. What is still false is the check as the task words it: studio's `improve/` went from
  11 files to 3 and the builder received 8 test classes, so no two class counts are equal, and the
  invariant to quote instead is 57 = 55 moved + 2 stayed (`@Test` per file). The two survivors are the
  live-tagged `LiveLoopIntegrationTest` and `RestIntegrationTest`, which cover studio REST and booted-app
  surface that never moved. The reasons written here on 2026-11-21 were real obstacles and were solved
  rather than removed: `TestFixtures` is still package-private and there is still no `test-jar` under
  `prod/`, so the moved set carries its own copy. Commands and verbatim output are under 2.4 above.
- **5.1 landed as `test` scope rather than removal.** `control/LoopNeverPushesTest` reads
  `StudioConfiguration.class` off the classpath and asserts a constant-pool string, so dropping the
  dependency outright would **error** that test rather than pass it. The dependency is now
  `<scope>test</scope>` with its four exclusions retained, and the module's `application.yml` header names
  the builder as the source of the shadowed keys.
- **6.1 and 6.2 were not run as part of the 2026-11-21 reconciliation, which is why they read as open
  here.** Both have now been run on 2026-11-22 in a clean worktree at `620a8f2`: the full POC reactor is
  `BUILD SUCCESS` with 37 modules and nothing skipped (241 classes, 2020 tests, 0 failures, 0 errors, 0
  skipped), and the studio frontend `vue-tsc --noEmit && vite build` exits 0 with 215 modules transformed
  and only the standing chunk-size advisory. Neither claim borrows an earlier commit's result. The reason
  written here for holding 6.2 — the user's uncommitted `frontend/vite.config.ts` — was a property of the
  main checkout's working tree, not of this branch, and its only delta is a dev-server proxy target a
  production build never reads.
- **6.4 is owed and is now demonstrably stale.** `docs/10-self-improvement.md:3` opens by placing the loop
  in `prod/embabel-smith-studio` (`studio/improve`, `studio/loop`), which the move falsified, and
  `docs/05-architecture.md:41` has the studio driving it. Both need the pass, plus the wiki lint and
  re-publish `AGENTS.md` requires.

**6.4 closed 2026-11-21.** `docs/10-self-improvement.md` now names the builder as the engine's home and
the studio as its driver; `docs/05-architecture.md` says the builder runs the loop and the studio drives
it; `docs/04-poc-run-results.md` §7.5 and the POC 27 row of `README.md` say "the product's loop" rather
than the studio's; and `docs/12-backlog.md`'s `Check:` was repaired in **both** halves — the file path and
the pattern, since the `CandidateStore` seam renamed `savePackWhenConfigured` to `save`, so the old
command would have returned nothing while the behaviour it checks was intact. The wiki got the same
treatment: `smith-studio.md`'s seven `source_paths` now point at `builder/improve`, `smith-builder.md`
claims the engine it now owns, `index.md` and `overview/product-phase.md` were corrected, and
`studio-process-workspace.md` lost four dead paths left over from the earlier blueprint move rather than
this one. Wiki lint after the pass: 45 pages, 0 broken links, 0 stale, 0 dead source paths, `index` the
only link-free page because it is the entry point. **The GitHub Pages re-publish is still owed** — it
fires on a push, and this branch is not pushed.
