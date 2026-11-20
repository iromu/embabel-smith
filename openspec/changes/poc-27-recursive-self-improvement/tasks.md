# Tasks

## 1. Module scaffolding

- [x] 1.1 Create `poc/poc-27-recursive-self-improvement/` with the standard Spring Boot POC layout and verify `make build MODULE=poc/poc-27-recursive-self-improvement` compiles
      **Done 2026-11-20.** `poc/pom.xml` parent, no `<relativePath>`, bare `spring-boot-maven-plugin`,
      `@SpringBootApplication` launcher in `com.embabel.smith.selfimprove` following poc-26's pattern
      (offline evidence printed before `SpringApplication.run`, Spring entered only with the endpoint
      vars). Verified with `make module MODULE=poc/poc-27-recursive-self-improvement GOAL=compile` —
      **BUILD SUCCESS**, 1 source file compiled, 12 resources copied. That is `-pl … -am`, which is the
      module-scoped form of the command named above; the full-reactor form is 1.2.
- [x] 1.2 Register the module in `poc/pom.xml` (`<modules>` and a `dependencyManagement` entry) and verify `make build POC=1` includes it in the reactor
      **Done.** Module entry after poc-26, plus the **first** `dependencyManagement` entry for
      `poc-26-graaljs-skill-scripts` — that file managed 13 POCs and neither poc-25 nor poc-26, so the
      entry had to be added rather than reused. Verified by reactor listing rather than by exit code:
      `make build POC=1` runs Maven with `-q` and prints nothing, so it proves nothing about membership;
      `./mvnw -Ppoc validate` puts `poc-27-recursive-self-improvement` at **`[37/37]`** in the build
      order and `BUILD SUCCESS`.
- [x] 1.3 Add the `poc-26-graaljs-skill-scripts` dependency for the script engine and verify the engine type resolves on the POC's classpath
      **Done.** The dependency is declared without a version, resolved from the new managed entry. The
      launcher's own deps are only poc-26 plus test-scoped JUnit/AssertJ — `@SpringBootApplication`
      arrives transitively through `embabel-agent-starter`, which is how poc-26 compiles the same three
      classes without declaring Spring Boot itself. Verified by an offline test that constructs
      `new GraalJsSkillScriptExecutionEngine()` and asserts `supportedLanguages()` contains
      `ScriptLanguage.JAVASCRIPT`: **`Tests run: 1, Failures: 0`**. It is one assertion and its
      non-vacuity is structural — remove the dependency and the module stops compiling.
- [x] 1.4 Give every source file the author and SPDX header and verify `make checkstyle-module MODULE=poc/poc-27-recursive-self-improvement` reports zero violations
      **Done.** SPDX block plus `@author Ivan Rodriguez` on both `.java` files, tabs for indentation.
      `make checkstyle-module MODULE=poc/poc-27-recursive-self-improvement` → **0 violations**, and the
      reactor `validate` run above re-ran checkstyle on it as part of the full POC tree.

## 2. Frozen evidence

- [x] 2.1 Write the self-description of embabel-smith's own process into `src/main/resources/self/self_process_description.md`, distilled by hand from `docs/05`, `docs/08` and `docs/10`, and verify it names every stage the builder's own pipeline runs
      **Done** (11.5 KB). Eight stages in the order `BuilderService.build()` holds them — generate,
      validate, write, evaluate (with refine and the process candidate inside it), inspect, security scan,
      lifecycle stamp, publish — plus the ingestion pre-stages, the improvement loop, and the invariants,
      each stage stating what it consumes, what it emits and what stops it. Every number in it was
      re-checked against source rather than trusted: score gate `1.0` and 3 refine rounds
      (`BuildOptions.java:50,53`), quality floor 70 and at most three drafts
      (`docs/08-builder.md:159-162`), the severity scale with no `HIGH` (`docs/08:118`), `--target
      process` a usage error (`docs/08:135`), and `embabel` technique 0/6 against 4/6 (`docs/08:61`). It
      states the deliverable explicitly, as the builder's own blueprints do, because a model is going to
      be asked to turn it into a multi-agent process.
- [x] 2.2 Author the frozen task set under `src/main/resources/benchmark/tasks/` with human-written expected outputs, and verify each task parses and declares its own expectation
      **Done** — six tasks, one JSON file each, keys `id`, `stage`, `prompt`, `require`, `anyOf`,
      `forbid`, `check`. The check is literal and deterministic (case-folded, whitespace-collapsed,
      phrase delimited at both ends), so it needs no model call and grants no partial credit; each file
      states its own check in its `check` field, and the matching rule is documented once in
      `benchmark/tasks/README.md` rather than per file. Topics are the things a candidate about *this
      pipeline* gets wrong by default: the severity scale, the inspect gate's floor and draft bound, which
      prompt refinement is actually allowed to rewrite, the strict baseline, commit-never-push, and
      `--target process`. Each was checked for discrimination: a correct answer passes, a plausible
      generic answer fails. An anti-restatement invariant holds — no `require`/`anyOf` phrase appears in
      its own prompt — so a task cannot be passed by echoing the question.
      **Stated as design intent, not measurement:** the expected baseline is **2/6 pass**, chosen for
      headroom per D4, and it is 2.4's job to replace that figure with a measurement. The README labels it
      as intent for exactly that reason.
- [x] 2.3 Author the frozen trace corpus under `src/main/resources/corpus/traces/` with a human label per line for "genuine defect", and verify a held-out split is designated and disjoint from the tuning split
      **Done** — 26 JSONL lines: `tuning.jsonl` 16 (8 labelled defect), `heldout.jsonl` 10 (5 defect),
      labels in the records themselves (`label`, `label_reason`, `spec_context` on drift cases). Fields
      were copied from the real record shape before authoring (`TraceRecord.java:36,39,42,54`;
      `TraceRecorder.java:127,134,150`), and the coverage follows the product's own distinctions: genuine
      failed-step defects, trust-tier blocks and lifecycle `retired` blocks (both excluded by
      `Detector.java:41,48`), below-baseline, content-hash drift, ordinary successes, a latency-only
      non-defect, a goal-not-found defect. Disjointness is inspectable rather than asserted: ids carry
      their split prefix, and the held-out split reuses none of the tuning split's sources, packs or
      timestamps.
      **Two consequences worth carrying forward.** The product's reader cannot read this corpus:
      `TraceRecordJson.java:30` is a bare `new ObjectMapper()`, so unknown properties fail and
      `JsonlTraceStore.read()` would report every labelled line as corrupt — the inner loop needs its own
      reader. And the corpus had to simplify a real shape: two of `Detector`'s three triggers are
      properties of a *pack and its sidecar*, not of a trace line, so drift and below-baseline are encoded
      as `spec_context` on a representative line; documented in `corpus/traces/README.md`, not smoothed
      over. Two decoy lines mention the trust tier *without* the prefix, which makes them defects under
      the product's `startsWith` rule — that is where a keyword policy loses recall.
- [ ] 2.4 Record the baseline score of the unmodified process against the frozen task set and verify it leaves measurable headroom; if it does not, stop and record that the outer loop cannot run
- [ ] 2.5 Measure the repeat-to-repeat noise floor by scoring the same unchanged candidate at least three times, and verify the floor is written to `evidence/noise-floor.json` before any prompt is tuned
- [x] 2.6 Write `src/main/resources/evidence/MUTABILITY.md` naming the mutable set and the fixed set for the run, and verify every later group's classes cite it rather than restating it
      **Done** (5.5 KB) — the D1 boundary verbatim, seven mutable surfaces and eleven fixed ones, each
      fixed item carrying *why*, plus a "what counts as a mutation" section that settles the two cases the
      boundary would otherwise be argued about: constructing a fixed class with generation-chosen
      arguments is configuration and allowed, and generating a script that runs under a fixed engine is
      allowed. `noise-floor` is listed as fixed while pointing at a file 2.5 has not produced yet, so the
      launcher prints that it is missing and that a run may not start without it.
      **The verification's second half is a standing obligation, not a completed one:** only the launcher
      exists to cite it. Each later section's classes must reference this file rather than restate it, and
      that is checked as each section lands, not here.

## 3. The referee

- [x] 3.1 Build the referee as a separately started process that constructs the gate over the frozen task set through its existing injectable seams, and verify scoring one known-good and one known-bad candidate returns the expected verdicts offline
      **Done.** `selfimprove/referee/` — `Referee`, `RefereeProcess` (a `main` started by hand; the
      launcher's own javadoc says the referee is a separate process it never starts), `Candidate`,
      `RefereeReport`, `FrozenTaskSet`, `FrozenTask`. **No prod file is changed to build it**: the seam is
      `GateChain`'s two-argument constructor taking the eval task list and the `InvokerFactory`, which
      `docs/10-self-improvement.md` already records as injected collaborators. The eval scorer is built
      *inside* `GateChain` and is not injectable, and that is kept as the referee's tenure rather than fixed —
      an improver that could reach the scorer would optimise its scorer. Offline evidence is
      `FrozenGateVerdictsTest`, 6 tests: a known-good candidate clears both scorers; a known-bad candidate is
      rejected at the baseline and names the tasks it missed; the frozen set scores the priors where
      `benchmark/tasks/README.md` says they belong; each candidate is asked each prompt exactly once across
      both scorers; no task can be passed by echoing its own question; and the gate cannot carry a `forbid`
      condition, which is why the report keeps two scores instead of one. The once-only test exists because
      the gate re-runs the pack during re-eval — without the `RecordedInvokerFactory` wrap the referee would
      pay for the same calls twice and could report two different scores for one answer set because the model
      answered differently the second time. Verified: **`Tests run: 18, Failures: 0`** for the module, **0**
      Checkstyle violations, poc-27 at `[11/11]` of the `-am` reactor.
- [x] 3.2 Enforce that the referee loads no skill folder and opens no script context, and verify a test asserts the referee's own classpath and context carry neither
      **Done, with the limit stated where it belongs rather than at the end.** Two mechanisms, not one claim.
      `Referee.start(resourcesDir, skillFolder, factory)` throws when a skill folder is supplied, naming the
      `referee-process` row of `evidence/MUTABILITY.md` as the fixed artifact it is honouring; and
      `RefereeIsolationTest` walks the object graph that constructor produces (`ObjectGraphWalk`) and fails if
      a script engine or script context is reachable in it. **The walk flags by type as well as by name, and
      the control case is the reason:** a deliberately neutrally-named `Watchmaker` stub implementing
      `SkillScriptExecutionEngine` is caught by the `instanceof` and would have slipped past a name-only check
      — the first draft of this test asserted only that a walked name contained `skills.script`, and the
      concrete engine's name does not (`skillscripts`), so it was passing for the wrong reason. **The limit,
      kept in the class javadoc:** isolation is by process boundary and by construction, **not by absent
      jars**. Both loops are in this one Maven module, so poc-26's engine classes are on the shared classpath
      and a "no Graal classes here" claim would be false; jar-level isolation would need a second module,
      which this plan did not ask for. A lambda is invisible to a field walk, so the fourth test reads the
      source text of the whole package instead — a static call is the one thing the walk cannot see. That test
      failed on its first run for a self-defeating reason worth recording: four Javadoc comments named the
      scorer, one of them inside the assertion's own message, so the test was asserting the scorer's absence
      while spelling it out.
- [x] 3.3 Make the task set read-only to the loop and verify a candidate that emits proposed evaluation tasks is excluded from scoring and the exclusion is recorded
      **Done.** `FrozenTaskSet` has no writer — nothing in the class can add, remove or edit a task, which is
      what makes read-only a property of the code rather than a rule to remember — and `Candidate` takes an
      optional proposed-tasks directory that `Referee.score` drops by identifier and reports as
      `excludedProposedTaskIds()`, so the exclusion is evidence in the record rather than an assumption.
      `TaskSetReadOnlyToLoopTest`, 4 tests: two proposals authored to be trivially passable are excluded by
      name, never put to the candidate (the recording factory proves those prompts never reached it), and the
      denominator stays the frozen six so the free points cannot move the score; a candidate that proposes
      nothing records no exclusion; a proposal that is not even JSON is *still* recorded, because a proposal
      is excluded rather than validated and must not become a scoring failure or a crash; and scoring twice
      leaves the tasks directory identical to the snapshot taken before.
- [x] 3.4 Refuse to start the outer loop when no baseline is recorded and verify the refusal names the missing artifact
      **Done.** `Referee.assertOuterLoopMayStart()` refuses while `evidence/baseline.json` or
      `evidence/noise-floor.json` is absent, and the message names every missing artifact together with the
      path it looked for. `OuterLoopRefusalTest`, 3 tests, including one that isolates the cause — a working
      referee built against a temp resources directory that simply lacks the artifacts, so the refusal is
      caused by absence and not by the referee being unable to run — and one that a noise floor without a
      baseline still refuses and names only the baseline. **Path caveat to keep:** presence is checked, not
      content, and task 2.4 has not yet named the file its measured baseline will be written to.
      `evidence/baseline.json` is the path this referee chose; when 2.4 lands it either writes there or this
      constant changes with it.

**Cost accepted for the seams above.** poc-27's pom now depends on `embabel-smith-studio`,
`embabel-smith-builder`, `smith-common` and `jackson-databind`, all version-less and resolved from the root
pom's management. The studio is where `GateChain` lives, so the dependency is the seam, not a convenience —
but it means every `-am` build of poc-27 pays for the studio's `frontend-maven-plugin` npm build, visible in
the run above as 26.994 s of the 11-module reactor. The module also carries its own
`src/main/resources/application.yml` setting `spring.main.web-application-type: none`, because the studio
jar ships a config that binds `${SERVER_PORT:8080}`, and 8080 is held on this machine by an unrelated
service.

## 4. Inner loop — the improver as a generated script

- [x] 4.1 Run a hand-written detection policy as a JavaScript skill script through `poc-26`'s engine and verify it reproduces the existing loop's three detection triggers on a known trace window
      **Done.** `handwritten-detect.js` runs through `GraalJsSkillScriptExecutionEngine` over the frozen
      corpus. The important choice is what it is compared against: not a paraphrase of the rules but the
      product's own code — `Detector.failedNonPolicyBlock`, `Detector.belowBaseline` and `Detector.hasDrift`
      are all `public static`, so the test asserts the policy's flag set **equals** the detector's for the
      same window, and the compiled policy carries `Detector.TIER_BLOCK_PREFIX` / `LIFECYCLE_BLOCK_PREFIX`
      by reference rather than a restated string. The two decoys are caught, not missed: `t-007` and `h-006`
      mention the trust tier without carrying the prefix, so the prefix rule keeps them as defects — which is
      exactly where a keyword policy would lose recall.
      **Read this before quoting the number:** the reported precision and recall of 1.0 on the held-out split
      are **hand-computed from the corpus labels, not measured**, and the labels and the policy were authored
      by the same hand. A policy agreeing with labels written to match it is a consistency check, not
      evidence about detection quality.
- [x] 4.2 Generate detection policies as JavaScript from a policy prompt and verify the generated script parses and executes under the engine's statement and time limits
      **Done, and "generated" here means a deterministic search, not a model.** A `PolicyPrompt` grammar is
      mutated by one operator per generation off a frozen seed prompt and rendered by `PolicyCompiler`; no
      endpoint is involved. Determinism is an executed assertion rather than a claim: the same seed yields
      identical variant ids **and** identical sources, and a different seed yields different ids, so the seed
      is demonstrably used. `RAN` status is the proof of staying inside the limits, because a statement or
      time breach surfaces from the engine as a `Failure` naming the limit.
- [x] 4.3 Score each generated policy against the labelled corpus, reporting precision and recall separately on the held-out split, and verify the offline scoring test asserts a mixed policy yields two numbers rather than one blended score
      **Done.** `SplitScore` is `(double precision, double recall)` and the test pins the *types* of the two
      components, which is stronger than blacklisting a name — a third blended number would have to be a
      third component. A hand-authored mixed policy has positive false positives **and** positive false
      negatives with `precision != recall`, so the two numbers cannot be equal by construction; a
      keyword-instead-of-prefix variant scores 1.0 precision against below-1.0 recall on the held-out split,
      which is the failure mode the split exists to show. Input hygiene is asserted too: the rendered stdin
      field set is a subset of the corpus evidence and contains neither `label` nor `label_reason`, so a
      policy cannot read the answer key.
- [x] 4.4 Run the inner loop to convergence over the free generations and verify the run writes a per-generation record with zero model calls counted
      **Done, and the zero is measured rather than typed.** Every record reports `modelCalls == 0` and the
      meter itself reads zero — but the test that earns the tick is the one where a listener deliberately
      spends **one** metered call on generation 1: that record then reads `1`, its neighbours read `0`, and
      the run total reads `1`. Without it, "zero model calls" would be indistinguishable from a constant.
      `generations.jsonl` carries one line per record with variant, parent, operator and separate numeric
      precision and recall for both splits; patience 2 over 12 variants stops early with `CONVERGED`.
- [x] 4.5 Record a policy that attempts to reach outside its sandbox as a rejected generation and verify the rejection names the fixed artifact from `MUTABILITY.md`
      **Done.** Two probes run straight through the engine — `Java.type('java.lang.Runtime')` and
      `readFile('heldout.jsonl')` — and the fixed-artifact name is derived from the engine's **own denial
      text** (`"not allowed"`, `"was not staged"`), not from which operator the loop thinks it ran; those are
      the same wordings poc-26 pins in its own test at `:159` and `:196`, so the mapping cannot silently
      drift. A rejected generation carries no tuning or held-out score, the run halts with
      `STOPPED_AT_REACH`, and the written record cites `evidence/MUTABILITY.md` and the artifact by name —
      with the cited file read back from the classpath and checked to actually name both. A control
      (malformed plus a dropped trigger) is classified `FAILED`/`SCORED` rather than rejected, so the
      classifier is not rejecting everything it does not like.

## 5. Process generation from the self-description

- [x] 5.1 Generate a multi-agent process from the self-description through the existing process generator with single-shot technique only, and verify the output carries at least two agent specs
      **Done offline against a canned answer; the live path is written but not yet run.** The generator is
      the product's `SingleShotProcessSpecGenerator` with its bundled `process_to_flow_multi` prompt — a test
      asserts the system message *is* that prompt, so "single-shot technique only" is checked rather than
      asserted, and the whole self-description arrives inside `<document>…</document>` untruncated. A
      one-agent answer is refused with "single agent" rather than reported as a process. `SelfProcessGenerationLiveTest`
      carries the same claim against a real model, `@Tag("live")` plus the endpoint gate, one paid call shared
      by its two tests. **It has not been executed** — this module's test classpath has only `junit-jupiter`
      and `assertj-core`, so it boots Spring by hand (`SpringApplicationBuilder(SelfImproveApp.class)
      .web(NONE)`); whether an `Ai` bean resolves there is unproven until 10.2 runs it.
- [x] 5.2 Validate the generated hand-offs with the existing process validator, writing the pack folder directly rather than adding a build target, and verify a spec set with an uncovered input or an unresolved cycle is named and not scored
      **Done, with no prod change and no new build target** — the folder is written with `smith-common`'s own
      `ProcessSpecWriter` and read back with `ProcessSpecReader`, then validated and repaired. "Not scored" is
      asserted as an absence that could have been present: the scorer is an injected call-counting function,
      so an uncovered input (`publish-pack`/`publish` needing a `ReviewedDraft` nothing produces) and a
      mutual-data cycle each name the offender and leave the counter at **zero**. The control is what makes
      those two tests mean something: the same validator and repair on a *sound* data flow merely narrated in
      the wrong order settles after one reorder and **does** reach the scorer. The generator's own canned
      answer also goes generator → writer → reader → validator end to end, so the claim is about emitted
      shape and not about a tidier hand-authored fixture.
      **Two limits kept in the open:** a pack folder is named after its pack, so two agents sharing a name
      cannot be represented and the second overwrites the first; and the `reorders() == 1` in the control is
      derived from reading `ProcessRepair`'s loop — it breaks *before* incrementing when the data order is
      unfixable, which is what distinguishes a real cycle (0) from a wrong ordering (1).
- [x] 5.3 Verify the generated process describes the pipeline the builder actually runs, by checking each named stage against `docs/08-builder.md`, and record any stage the generation dropped
      **Done as an instrument; the measurement itself is owed to the live run.** `BuilderPipelineStages`
      carries the builder's eight stages in `build()` order, each with a verbatim quote anchoring it to
      `docs/08-builder.md`, and a test re-checks all eight anchors are still in that page — so the stage list
      cannot silently drift from the document it claims. Named ∪ dropped must partition the eight, which
      means a stage can be neither lost nor invented by the check. **The dropped count is deliberately not
      asserted:** pinning it would freeze one run's answer into permanent evidence in a frozen directory, and
      a drop is 5.3's finding rather than a failure. It is printed into the surefire report for the live run
      to read. The instrument is a coarse substring match over declared text and errs toward reporting a
      drop, so a paraphrased stage reads as missing — evidence to read, not a verdict.

## 6. Skill scripts that run

- [ ] 6.1 Generate a skill with a `scripts/` folder from a process step and verify `SkillMdValidator` accepts the frontmatter and the folder contains at least one `.js` file
- [ ] 6.2 Execute the generated script inside the candidate's own context through the engine and verify a successful run credits the skill and a script that exits with an error withholds credit and attributes the failure to the skill
- [ ] 6.3 Verify the candidate-side script context keeps `poc-26`'s hardening (no host class lookup, no IO, no process or thread creation) by asserting a probing script is denied

## 7. Outer loop — generations and the archive

- [ ] 7.1 Run the surviving offline policy as the generation-1 improver and score its candidates through the referee, and verify the run records score, parent and cost per variant
- [ ] 7.2 Retain every variant including ones that score below their parent, and verify an archive query returns a losing variant by its parent pointer
- [ ] 7.3 Derive generation 2's improver from generation 1's and run it, and verify the report states improvement per generation rather than a single score
- [ ] 7.4 Judge improvement as a paired delta against the recorded noise floor and verify a delta inside the floor is reported as "no improvement" and rejected by the gate
- [ ] 7.5 Compare archive selection against greedy selection over the identical generations and verify both curves are reported
- [ ] 7.6 Declare the model-call and generation budget before the first call and verify a run that exhausts it stops at the boundary and states which measurements were not taken
- [ ] 7.7 Report a single-completed-generation outcome as one round of hill-climbing with the recursive claim marked untested, and verify the reporting path is covered by a test

## 8. Human control

- [x] 8.1 Hold gated candidates for approval and publish nothing, and verify a passing candidate increments the held count with no pack written to a source
      **Done, by reusing the product's queue rather than counting in a parallel place.**
      `HeldForApproval` hands the candidate to `ApprovalQueue` and never receives a publisher it could call:
      the `LoopPublisher` it is given is `NOTHING_PUBLISHED`, which throws. Five tests: two gated candidates
      take the held count 0→1→2 with the ids in order and the destination directory still empty; an ungated
      candidate rejected at the `baseline` stage throws naming the stage and never enters the queue; a
      gated report carrying no gate score is **refused** rather than silently held as a zero; and the class
      exposes no method returning `PublishResult` while the same return-type detector does find
      `ApprovalQueue.approve`. The non-vacuity half is the one that matters — a real `FolderPublisher`
      publishing the same pack to the same destination **does** create the directory, so "empty after
      holding" is a result and not a test that could not have written anything.
- [x] 8.2 Verify the loop never pushes, by asserting the push flag is never set on any code path the POC can reach
      **Done three ways, each with a control, because an absence assertion passes by being blind.**
      **(a)** a walk of the object graph reachable from a populated `HeldForApproval` finds no
      `Publisher`/`Publishable`/`PublishResult`/`LoopPublishPolicy` and reports that it read everything it
      could — with a control that plants a `FolderPublisher` behind a field and a `LoopPublishPolicy(true,
      "git:", null)` and shows the same walk reports both;
      **(b)** `LoopPublishPolicy` is `final` with exactly one constructor whose first parameter is `boolean`
      and declares no static factory, so push can only arrive explicitly — and the only configured value on
      this classpath is read from `StudioConfiguration`'s constant pool, which contains
      `smith.studio.loop.push:false` and never `:true`. The constant pool is used instead of reflection
      because this module's pom excludes the studio's web, JPA, Postgres and docker-compose starters, so
      resolving that `@Configuration` class's signatures would risk `NoClassDefFoundError`;
      **(c)** no POC production source names a publishing type or the setting key, with the scanned set
      provably including the class under test, and a control package where the same matcher does report hits.
      **Two things this leaves true and undone:** the scan in (c) deliberately spans the whole
      `selfimprove` production tree rather than one package, because the claim is POC-wide — so a later
      section that legitimately needs a publishing type will fail a test it does not own, and the honest
      resolution then is to narrow the scan and say why, not to relax it; and `NOTHING_PUBLISHED` is never
      executed by any test, since no public path reaches it by design, so its refusal text is unverified and
      the class-shape assertion is what stands in for it.

**Section 4, 5 and 8 verification, one place.** `make test-module MODULE=poc/poc-27-recursive-self-improvement`
→ **`Tests run: 70, Failures: 0, Errors: 0, Skipped: 0`**, 0 Checkstyle violations, BUILD SUCCESS, with
upstream green around it (common 39, specsource 29, skillevaluator 942, runtime 82, builder 241, studio 162,
poc-26 31). `SelfProcessGenerationLiveTest` appears zero times in the log, so the two live tests were
group-excluded and **no model was called**. Getting there took five fixes, all of them in code the agents
wrote against APIs they had not compiled against: `ArrayNode.set(node)` → `add(node)` in `PolicyInput`; an
invented `builder.lifecycle.LifecycleTransitions` replaced by the real `LifecycleMetadata` record built with
`SpecContentHash.of(pack)`; AssertJ's non-existent `ListAssert.concatWith` replaced by a `Stream.concat`
union; the return-type detector taught to skip synthetic members, since it was matching the compiler-
generated body of the `NOTHING_PUBLISHED` lambda; and a drift-test fixture that passed `goal = null` and so
threw inside `SpecContentHash.of`. 22 `InnerTypeLast` violations were returned to the section that authored
them rather than fixed by me.

## 9. Evidence and documentation

- [x] 9.1 Verify every cited paper's arXiv identifier against arXiv and correct the list, since `poc/` is frozen afterwards and a wrong identifier is permanent
      **Done, and the task's premise turned out to be wrong.** There was no list to correct: the only
      paper list for POC 27 is the Literature bullet in `proposal.md`, and it named twelve papers with
      **no identifiers at all** while stating that they would be "verified against arXiv before the README
      is frozen". So 9.1 became resolve-and-attach rather than audit. Twelve identifiers attached there,
      each one re-fetched independently from `export.arxiv.org` and confirmed on title, first authors and
      publication date — none wrong, none unsupported, because the list attached no per-paper claim to get
      wrong. Two things the verification produced that the README must carry:
      **(a)** arXiv's stored spellings are not consistent — `2505.22954` is filed unaccented as "Darwin
      Godel Machine" while `2410.04444` keeps its accent as "Gödel Agent", and `cs/0309048` is titled
      "…Making **Provably Optimal** Self-Improvements", not the "…Provable…" wording usually quoted —
      and **(b)** `design.md` D6's "greedy selection from one best variant converges early" is this POC's
      own reasoning set against DGM's open-ended archive, **not a finding reported by `2505.22954`**, so
      9.2 must phrase it as an analogy rather than a cited result. "The reward-overoptimization result"
      was ambiguous and now names `2210.10760` (Gao–Schulman–Hilton) explicitly, distinguishing it from
      the 2024 direct-alignment follow-up `2406.02900`.
- [ ] 9.2 Write `poc/poc-27-recursive-self-improvement/README.md` with the hypothesis, the mermaid diagram, the paper list and the measured results, and verify it ends with the Conclusion and shared-results sections the POC convention requires
- [ ] 9.3 Add the POC 27 row and run results to `docs/04-poc-run-results.md` and the module table in `README.md`, and verify the documented commands run as written
- [ ] 9.4 State the honest outcome either way in the README, and verify a flat derivative is reported as a finding rather than softened

## 10. Integration check

- [ ] 10.1 Run `make test POC=1` and verify the whole POC reactor is green with no endpoint configured
- [ ] 10.2 Run the documented end-to-end sequence once against a live endpoint and verify it produces the archive, the per-generation report, and the held-candidate count in one pass

## Workflow follow-up

- Update `.wiki/` for the new POC and vocabulary, run the wiki lint, and re-publish to GitHub Pages (AGENTS.md: a vocabulary change is a documentation change).
- Commit the change; do not push (AGENTS.md and `docs/05-architecture.md`).
- Archive with `/opsx-archive` once the run results are recorded and reviewed.
