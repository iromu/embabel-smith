# Tasks

## 1. Scan what the publisher publishes

- [x] 1.1 Give `AgentSpecSecurityScanner` a `scanText` form that takes the texts to scan and have the
      existing single-text form delegate to it, and verify the existing scan tests still pass and a
      two-text call reports a finding raised in either text.
- [x] 1.2 Pass both model-authored documents to that scan in `SkillBuilderService` — the `SKILL.md`
      and the suite's `toJson()` — and verify a new `SkillBuilderServiceTest` case whose eval
      expectation carries a prompt-injection marker blocks at stage `security` and writes nothing to
      the target folder.
- [x] 1.3 Verify the clean path is unregressed: the existing `aPositiveLiftPublishesTheSkillFolder`
      and `theReportCarriesTheLiftTheScanAndTheLifecycleState` still pass, and confirm the lifecycle
      sidecar is not among the scanned texts.

## 2. Refuse a suite that cannot measure anything

- [x] 2.1 In `SkillBuilderService`, beside the existing `evals.evals().isEmpty()` guard, block when
      any scenario's expectation list is empty, naming the offending scenario ids, and verify a new
      test case with one empty scenario blocks at stage `eval` with those ids in the issues.
- [x] 2.2 Verify the refusal happens before the arms run: assert on the substituted
      `SpecPromptCaller`'s call count in that test that no with-skill or baseline call was made, so a
      degenerate suite costs fewer calls than it used to rather than four more per scenario.
- [x] 2.3 Verify the existing `aSuiteWithNoScenariosCannotBeValidated` and the lift-gate tests still
      pass, so a suite of non-empty scenarios is unaffected.

## 3. Put the lift minimum on the command line

- [x] 3.1 Add `--min-lift` to `BuildCommand` with `BuildOptions.DEFAULT_MIN_LIFT` as its default and
      `${DEFAULT-VALUE}` in its help text, thread it into the single `BuildOptions` construction in
      `call()`, and verify it through the seam `BuildCommandTest` already uses — its recording skill
      service captures the `BuildOptions` the command built — asserting the captured `minLift()`
      equals the flag's value, and equals `DEFAULT_MIN_LIFT` when the flag is omitted.
- [x] 3.2 Verify the omitted-flag path is unchanged: the existing default-minimum build test still
      publishes on any positive lift, and the blocked message still names the required minimum.
- [x] 3.3 Verify the option is accepted on an agent-spec build without changing that build's outcome,
      and that an unparseable value is a usage error (exit 2) rather than a fallback.

## 4. Record why the studio has no lift knob

- [x] 4.1 Leave `rest/BuildController` passing `BuildOptions.DEFAULT_MIN_LIFT` literally, and verify
      with `grep -n 'DEFAULT_MIN_LIFT\|BuildTarget.AGENT_SPEC' …BuildController.java` that both the
      constant and the target pin are still there. No `BuildRequest` field is added: `/api/build`
      pins `BuildTarget.AGENT_SPEC`, the lift gate applies only to skill builds, so a request field
      naming a minimum would be a knob nothing can reach.
- [x] 4.2 Add one sentence to `docs/08-builder.md`'s `skill` subsection stating that the minimum lift
      is a CLI knob only, and why — the studio's build is pinned to the agent-spec target, so no
      studio request can reach the lift gate. Verify the sentence against
      `rest/BuildController.java:64`.
- [x] 4.3 Verify `docs/08-builder.md` does not imply a studio skill build exists.

## 5. Record it

- [x] 5.1 Update `docs/08-builder.md`: the `skill` subsection states that the scan covers both
      published documents and that a scenario without expectations blocks the build; the CLI block
      gains `--min-lift`. Verify each sentence against the code it describes.
- [x] 5.2 Strike items 1 and 2 from *Builder — skill target* in `docs/12-backlog.md`, and record item 3
      as closed for the CLI **while stating that its studio half was resolved as "not reachable from
      the studio today", not as done** — the studio clause is a symptom of the target pinning, not a
      separate gap. Leave item 4 (the `git:` publish location) open, and verify the page's remaining
      text names no skill gap this change closed.
- [x] 5.3 Run `make lint` and the builder module's offline tests (`make test-module
      MODULE=prod/embabel-smith-builder`) and verify they are green with no endpoint configured. No
      studio file is touched by this change, so the cross-module compile check that a builder API
      change would need does not apply here.

---

## Verification actually run (2026-11-18)

Nothing in these runs reached a model. Stated precisely, because the distinction matters: `make test`
sources `~/.config/env/llm.env` and exports `OPENAI_CUSTOM_BASE_URL`, so the endpoint was *visible* to
the JVM (Embabel's model-config `INFO` lines appear in the log), and what keeps the default run
endpoint-free is the group exclusion — the root pom's `test.excludedGroups=live`. The reactor log
contains no `…Live…` test class at all, and every module reports `Skipped: 0`, so nothing was run and
then skipped either. The tests this change adds are offline by construction: the model is a
substituted `SpecPromptCaller` lambda and picocli's converter is exercised in-process.

| Command | Result |
| --- | --- |
| `make build` | exit 0, `BUILD SUCCESS` (all eight reactor entries) |
| `make test` | exit 0, `BUILD SUCCESS`. smith-common 39, specsource 29, skillevaluator 942, runtime 43, **builder 192** (was 184), studio 119. `Skipped: 0` in every module — the live tests are excluded by group, not skipped |
| `make test-module MODULE=prod/embabel-smith-builder` | exit 0, `Tests run: 192, Failures: 0, Errors: 0, Skipped: 0` |
| `make lint` | exit 0, `BUILD SUCCESS`, no violations |

**1.1** `AgentSpecSecurityScanner.java:117` is now `scanText(String, String)` delegating to
`scanText(String, List<String>)` at line 131, which calls the pre-existing private `textFindings`.
`AgentSpecSecurityScannerTest` went 3 → 4 tests; the new
`aMarkerRaisedInAnyOfSeveralDocumentsIsReportedAgainstTheArtifact` raises the marker in the **second**
document of a pair and asserts `BLOCK` with a non-empty `CAT_PROMPT_INJECTION` finding, and asserts
`SAFE` for a clean pair. The task's "either text" is covered asymmetrically on purpose: a marker in
the *first* text of a pair is the same code path the single-text form already exercised (it delegates),
so the new case tests the direction that was previously unreachable. Mutation-checked (block below):
scanning only the first text failed both it and `aMarkerInAnEvalExpectationBlocksAtSecurityAndPublishesNothing`.

**1.2** `SkillBuilderService` now scans `List.of(skill.toSkillMd(), evals.toJson())`.
`SkillBuilderServiceTest.aMarkerInAnEvalExpectationBlocksAtSecurityAndPublishesNothing` (stage
`security`, `result.scan().verdict() == BLOCK`, `library` empty) is that case; the body it ships is
the same benign body the publishing tests use, so the suite is the only thing that can stop the build.
Mutation-checked separately: reverting that one call to `scanText(skillName, skill.toSkillMd())`
produced exactly one failure in the module — that test, reporting `SKILL BUILD OK` where it must not.

**1.3** Both named tests pass unchanged, and the scanned list is built literally from the two model
documents — `lifecycle.yml` is produced after the gate by `LifecycleStamper` and never passed to
`scanText`, which is why the report still says `security:    SAFE` for a clean build.

**2.1 / 2.2** The guard sits immediately after the `isEmpty()` guard and names the ids
(`aScenarioWithNoExpectationsBlocksAtEvalAndBuysNoArmCalls` uses scenario id 7 and asserts the issue
list with `containsExactly`, so the wording and the id are both pinned). The same test counts the
substituted caller: `assertThat(calls).hasSize(2)` and every captured system prompt is a generation or
a suite-draft prompt — the arm and judge prompts would have thrown if reached. Mutation-checked (block
below): neutralising the guard's predicate produced exactly one failure, this test, and it failed on
the call-count side — the scripted caller threw `no arm or grading call expected for an unmeasurable
scenario: WITH-ARM`.

**2.3** `aSuiteWithNoScenariosCannotBeValidated` and all six lift-gate tests
(`aPositiveLiftPublishesTheSkillFolder`, `aZeroLift…`, `aNegativeLift…`, `aLiftBelowTheConfigured…`,
`aLiftThatExceedsTheConfigured…`, `aLiftExactlyAtTheConfigured…`) pass; they ship suites whose
scenarios all carry expectations, so the new guard does not fire for them.

**3.1** `--min-lift` is declared with `description = … (default ${DEFAULT-VALUE})…` and threaded
through the one `BuildOptions` construction in `call()`. `theMinimumLiftFlagReachesTheSkillGate`
captures `minLift() == 0.6`; the omitted-flag default is asserted by the pre-existing
`theSkillTargetRoutesToTheSkillPipeline`, which now also checks
`skillSeen.get().minLift()).isEqualTo(BuildOptions.DEFAULT_MIN_LIFT)`. `BuildCommandTest` 15 → 19.
Mutation-checked (block below): passing the constant instead of the parsed flag failed exactly the two
tests that assert `0.6`, and left the usage-text and usage-error tests green.

**3.2** `aPositiveLiftPublishesTheSkillFolder` (default minimum, publishes on lift +1.000) and
`aZeroLiftBlocksPublicationAndReportsEveryScenario` (message still
`skill lift +0.000 does not exceed the required +0.000`) both pass unchanged.

**3.3** `theMinimumLiftFlagIsAcceptedOnAnAgentSpecBuildWithoutDecidingIt` takes `--min-lift 0.6` on a
spec build, exits 0, captures the value on the spec options and never touches the skill service;
`anUnparseableMinimumLiftIsAUsageError` gets exit 2 with nothing captured (picocli's `double`
converter, so no fallback happens).

**4.1** No studio file was modified by this change. The grep returns exactly the two lines the design
depends on:
`64: … GenerationTechnique.SINGLE_SHOT, BuildTarget.AGENT_SPEC,` and
`65: BuildOptions.DEFAULT_MIN_LIFT, request.model());`. `BuildRequest` gained no field.

**4.2 / 4.3 / 5.1** `docs/08-builder.md`'s `skill` subsection gained the CLI-knob paragraph naming the
pin (verified against `BuildController.java:64` above), the both-documents scan paragraph, and the
expectations paragraph; the CLI block lists `[--min-lift <n>]`. Every sentence was checked against the
code it cites before being written. The page's remaining four `studio` mentions are about document
ingestion UI, the technique fallback, the CLI-knob paragraph itself, and the process workspace — none
implies a studio skill build exists. The *Evaluation and refinement* defaults table was deliberately
**not** given a `--min-lift` row: that table is scoped to the agent-spec pipeline's score gate and
refinement rounds, and the skill section already states the default (`DEFAULT_MIN_LIFT = 0.0`) beside
the gate it belongs to.

**5.2** `docs/12-backlog.md` §*Builder — skill target* now opens "All four are closed, the last three
verified 2026-11-18" and carries four `Closed —` notes with re-runnable `Check:` greps. Item 3's note
is headed "Closed for the CLI" and states in its own words that the studio half "was not implemented,
and is not a gap left open", attributing it to the target pinning. Item 4 (the `git:` location) was
closed by `builder-publish-location` and was left as that change wrote it rather than reopened. The
rest of the page was read for skill gaps this change closed: *Builder — generation*'s "Skill editing at
runtime, a skill improvement loop, and a process-level improvement loop" is explicitly out-of-scope
wording about other capabilities, and the *Skill evaluator* section is about the dormant module —
neither names anything this change fixed.

## Mutation check

Each new test was proven non-vacuous by breaking the code it guards, one mutation at a time, each run
with `make test-module MODULE=prod/embabel-smith-builder` and reverted before the next. The whole
module was re-run green after the last revert (`Tests run: 192, Failures: 0, Errors: 0`).

| Mutation | Failures it produced (of 192) |
| --- | --- |
| `textFindings(texts)` → `textFindings(texts.subList(0, 1))` | 2: `AgentSpecSecurityScannerTest.aMarkerRaisedInAnyOfSeveralDocumentsIsReportedAgainstTheArtifact:63` and `SkillBuilderServiceTest.aMarkerInAnEvalExpectationBlocksAtSecurityAndPublishesNothing:298` |
| skill scan → `scanText(skillName, skill.toSkillMd())` | 1: `aMarkerInAnEvalExpectationBlocksAtSecurityAndPublishesNothing:298` — the build reported `SKILL BUILD OK: receive-chilled-goods-into-the-cold-stor…` |
| guard predicate → `filter((eval) -> false)` | 1: `aScenarioWithNoExpectationsBlocksAtEvalAndBuysNoArmCalls:346 … IllegalState no arm or grading call expected for an unmeasurable scenario: WITH-ARM` |
| `this.minLift` → `BuildOptions.DEFAULT_MIN_LIFT` | 2: `theMinimumLiftFlagReachesTheSkillGate:136` and `theMinimumLiftFlagIsAcceptedOnAnAgentSpecBuildWithoutDecidingIt:152` |

The first row is the one worth reading twice: breaking the scanner alone also breaks the pipeline test,
so the two changes are load-bearing together — the widened `scanText` overload is what the skill
pipeline depends on, not an unused convenience. The third row fails on cost rather than on the message,
because the test's scripted caller refuses to answer an arm prompt at all.

One hazard to record for whoever edits these files next: the root pom binds `spring-javaformat:apply`
to `process-sources`, so a build rewrites the sources in place. A mutation shortens a line and the
formatter rejoins the chain around it, so a revert has to be written against the *reformatted* text,
not the text that was there before the mutation run.
