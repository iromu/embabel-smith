# Tasks

Code in `prod/embabel-smith-builder` plus the two POMs; docs, `NOTICE` and `README` at the end. Nothing
under `prod/embabel-smith-skillevaluator/` is edited — it gains a consumer, not a change. Do not run
`make build`/`make test` while another writer holds the tree; use the module-scoped entry points and say
which one you ran.

## 1. Give the evaluator a consumer

- [x] 1.1 Add `embabel-smith-skillevaluator` to the root `pom.xml` `dependencyManagement` beside the other
      `prod/` entries (`embabel-smith-runtime`/`embabel-smith-builder` are there today) with
      `${project.version}`, and verify `grep -n skillevaluator pom.xml` returns a `dependencyManagement`
      line where it previously returned nothing.
- [x] 1.2 Add the dependency to `prod/embabel-smith-builder/pom.xml` with no `<version>` (managed) and no
      scope, and verify the enforcer's `ban-poc-dependencies` rule still passes on a module build of the
      builder — the artifact must not match `org.iromu.embabel.smith:poc-*`.
- [x] 1.3 Run `mvn -pl prod/embabel-smith-builder dependency:tree -Dincludes=org.yaml:snakeyaml` and record
      which SnakeYAML version resolves (the evaluator pins 2.3 at
      `prod/embabel-smith-skillevaluator/pom.xml:19` while the root POM imports the Spring Boot BOM). If
      the BOM's version wins, pin `org.yaml:snakeyaml` in the root `dependencyManagement` and re-run the
      command. Verify the recorded version is the one the evaluator's own tests were green against.
- [x] 1.4 Verify the reactor order needs no edit — `prod/pom.xml:25` lists the evaluator before the
      builder at line 27 — by building the builder module alone with `-am` and confirming the evaluator is
      built first, from source, not resolved from the local repository.

**Wave A landed 2026-11-19 (1.1–1.4).** The seam is `skill/SkillInspector.java` plus
`SkillInspectorTest` (10 offline tests). Two things the plan did not anticipate:

- **1.3 resolved the other way round.** The BOM's version did win — the builder resolved
  `org.yaml:snakeyaml:jar:2.6` while the evaluator's own suite was proved at 2.3 — but the fix is **not** a
  root pin at 2.3. That downgrades the YAML parser every product module uses (smith-common's spec I/O, the
  runtime's ingest, the studio) in order to satisfy a ported validator. Confined to the builder it is worse:
  the studio embeds the builder, so the evaluator would be proved at 2.3 in its own build and run at 2.6
  inside the studio — an untested combination, silently. So the evaluator moved **up** to 2.6, with the
  reason in a comment on its declaration, and the reactor resolves 2.6 in seven places and 2.3 in none.
  Evidence: the evaluator's **942 tests pass at 2.6** (`dependency:tree` shows
  `org.yaml:snakeyaml:jar:2.6:compile`), and the builder's **222** pass with it. A deliberate deviation from
  the version the port arrived with, proved by that port's own suite rather than assumed.
- **1.4's line numbers are off by one.** `prod/pom.xml:25` is the evaluator and the builder is at **28**,
  not 27 — line 27 is `embabel-smith-runtime-a2a`. The order is already correct, so nothing needed editing.

## 2. The inspection seam

- [x] 2.1 Create one class in `com.embabel.smith.builder.skill` (suggested `SkillInspector`) that is the
      only builder class importing `org.iromu.embabel.smith.skillevaluator`, and verify `grep -rln
      'org.iromu.embabel.smith.skillevaluator' prod/embabel-smith-builder/src/main/java` lists exactly
      that file.
- [x] 2.2 Materialise the in-memory `Skill` for the run: write `skill.toSkillMd()` to
      `<system temp>/<skillName>/SKILL.md`, run, and delete the directory in a `finally`. Verify a test
      asserts the folder name equals the skill name (the schema validator's `name_consistency` finding is
      HIGH when they disagree) and that no temp directory survives a run, including a failing one.
- [x] 2.3 Run `Tier1Runner.builder().checks("schema", "version", "quality", "unicode", "lint", "license",
      "pii")` — every check the library runs without an external tool — and translate the returned
      `List<ValidationResult>` into three separate values: blocking findings (from results where
      `isPassed()` is false — `toLegacyString()` plus `getSuggestion()` when present, plus each
      `getGateFailures()` entry), advisory findings (`getWarnings()`), and incomplete-scan names
      (`getIncompleteScans()`). Verify a test with a canned failing skill body yields a non-empty blocking
      list, and that a passing skill yields all three empty.
- [x] 2.4 Classify **by check name, not severity**: `pii` findings go to the advisory list even when its
      `isPassed()` is false, and no other check's failing result is downgraded to advisory. Verify with a
      stubbed `pii` result carrying a high-severity finding — the inspection verdict stays pass and the
      finding surfaces labelled as a `pii` advisory.
- [x] 2.5 Treat a result whose `getStatus()` is `incomplete` as neither a pass nor a block: its scanner
      name goes to the incomplete list only. Verify with a stubbed lineup that includes a check whose
      result is incomplete — the inspection verdict must stay pass/fail on findings alone and the name
      must surface in the report.
- [x] 2.6 Record the checks that were **not** requested — `security`, `secrets`, `code-integrity` — as
      their own value on the seam's result, distinct from the incomplete list. Verify a test asserts the
      three names are present on a default run and absent when `--full-inspection` is set.
- [x] 2.7 Verify the seam is offline: the test in 2.3 runs with no endpoint variables set and none of
      `gitleaks`, `skillspector`, `bandit`, `semgrep` on `PATH`, and asserts no `ProcessBuilder` is
      reachable from the default lineup by asserting the incomplete list is empty for a passing skill.

**Wave A landed 2026-11-19 (2.1–2.7), with four corrections the later sections have to honour.**

- **`secrets` is not a requestable check name.** `Tier1Runner.RECOGNIZED_CHECKS` is a copy of
  `DEFAULT_CHECKS` (`Tier1Runner.java:61-65`), which has no `secrets` entry — `secrets` appears only in
  `SKIPPABLE_CHECKS` (71–72), where it names the secrets scan running *inside* `code-integrity`. Asking for
  it yields an option-validation result saying `Unknown Tier 1 check(s): secrets`, and that result is
  **blocking**. So task 4.1a's "appends `security`, `secrets` and `code-integrity` to the lineup" would fail
  every full-inspection build if implemented literally. The full lineup is `Tier1Runner.DEFAULT_CHECKS`;
  `secrets` is still disclosed by name as not-run.
- **A well-formed generated skill never has an empty advisory list.** Task 2.3's "a passing skill yields all
  three empty" is false: `Skill.toSkillMd()` writes only `name` and `description`, so a clean draft yields
  blocking `[]` and incomplete `[]` but **12 advisory lines** — `license`, the `schema` author and
  standard-location notes, quality's missing `version`/`metadata.author`/`metadata.tags`, a missing
  `## Purpose`, thin content, action verbs. Sections 4 and 6 must not describe an empty advisory list as the
  clean case, because for a generated skill it never happens. The gate does not block on these.
- **A result can fail with nothing behind it, and the specified three-way split would have passed it.** An
  option-validation failure carries `getErrors()` with no `Finding` and no gate failure, so translating only
  findings and gate failures lets **a lineup that never ran** through as a pass. `classify` quotes
  `getErrors()` when a failing non-`pii` result produced no blocking line — three lines, driven out
  test-first. Related, and the reason classification cannot match on one string: a validator's *report* name
  is not its check name (`Quality Score (4-Dimension Analysis)` vs a result stamped `QUALITY`,
  `Script Lint (Advisory)` vs `SCRIPT_LINT`), so the seam carries a report-name→check map and treats an
  unmapped name as **blocking**, which fails closed if a validator is ever renamed.
- **2.6 is asserted at the lineup level rather than by executing a full inspection.** This machine has
  `gitleaks`, `skillspector` and `semgrep` installed, so a test that really ran `--full-inspection` would
  shell out to three scanners — semgrep alone takes minutes. The assertion is on `requestedChecks(true)` and
  on the disclosure value. **Keep `--full-inspection` out of the offline suite** (5.1), and note the flip
  side: because the binaries *are* installed here, 2.7's empty-incomplete-list assertion is meaningful.

## 3. The gate and its redraft loop in the skill pipeline

This section edits `skill/SkillBuilderService`, which `builder-skill-gate-integrity` also edits, so it
starts only after that change has landed (D2).

- [x] 3.1 Insert the gate in `skill/SkillBuilderService.build` after the `SkillMdValidator` check (line
      107) and before `EvalSource.generate`, so no eval scenario is drafted and no arm runs for a skill
      that fails inspection. Verify a test with a canned bad draft asserts the eval prompt caller is
      never invoked.
- [x] 3.2 Add a redraft entry point on `skill/SkillGenerator` taking the previous round's blocking
      findings and appending them to the generation request (mirroring how
      `eval/RefinementLoop.describeFailures` describes failures), keeping the existing
      `generate(skillName, description)` behaviour intact. Verify a test captures the outbound prompt and
      asserts the finding text appears in it and the original instruction block is unchanged.
- [x] 3.3 Bound the loop at `round <= maxInspect` with an explicit break on the last round (the
      `RefinementLoop.refine` shape), so the last round's skill is the inspected one and no redraft
      follows it. Verify a test with a caller that always returns a failing draft is called exactly
      `maxInspect + 1` times.
- [x] 3.4 Cap the finding text handed to the model at a fixed number of lines and record the cap on the
      class that applies it. Verify a test with more findings than the cap asserts the prompt stays within
      it and the report still lists every finding.
- [x] 3.5 Feed back blocking findings and gate failures only; advisory warnings never reach the model, and
      a `pii` finding never reaches it either whatever its severity. Verify a test asserts an advisory-only
      text (a `Missing recommended section` line) is absent from the redraft prompt and present in the
      build report, and that a stubbed high-severity `pii` finding is likewise absent from the prompt.
- [x] 3.6 When the last round still fails, return
      `SkillBuildResult.blocked("inspect", skillName, findings, null, null)` and publish nothing. Verify a
      test asserts no `Publisher` is opened and the report names stage `inspect` plus the round count.
- [x] 3.7 Confirm the gate reads only `SKILL.md` and never `evals.json`, so it does not duplicate the
      `evals.json` scan belonging to `builder-skill-gate-integrity`. Verify by asserting the materialised
      temp folder contains exactly one file.

## 4. Knob and report surface

- [x] 4.1 Add `--max-inspect` to `cli/BuildCommand` with a default constant on `BuildOptions` (default 2)
      and one `BuildOptions` field; verify `--max-refine` keeps its current meaning and default of 3
      (`BuildOptions.java:41`) and that an agent-spec build is unaffected by the new field.
- [x] 4.1a Add `--full-inspection` (default false) as a second `BuildOptions` field that switches the
      lineup to `Tier1Runner.DEFAULT_CHECKS` when set, documented in `docs/08-builder.md` in the same release
      as the gate. **Do not implement this as "append `security`, `secrets` and `code-integrity`"** —
      `secrets` is not a requestable check name and asking for it produces a blocking
      `Unknown Tier 1 check(s): secrets` result, so the literal wording fails every full-inspection build;
      the seam's `requestedChecks(true)` already returns the right nine. Verify a test asserts the default
      run requests only the seven binary-free checks, that the flag's run requests the nine, and that
      `secrets` is named in `checksNotRun` on the default path but not in the requested list on either. Keep
      the flag's own execution out of the offline suite (see section 2's notes).
- [x] 4.2 Have the studio keep the default (no request field), matching how `rest/BuildController.java`
      already passes `BuildOptions.DEFAULT_MIN_LIFT` literally. Verify the studio test sources still
      compile and no request DTO changed.
- [x] 4.3 Put the inspection outcome on `SkillBuildResult` for **both** paths — checks run, quality score,
      drafts used, advisory findings labelled with the check that produced them, incomplete-scan caveats,
      and the checks that did not run — and render it in `report()` beside the existing `security:` line.
      The "did not run" line is printed unconditionally, not only when a scan came back incomplete, so a
      default build states plainly that no secret or code-risk scanning happened. Verify a built-skill
      report shows the score and the disclosure, and a blocked-at-`inspect` report shows the findings and
      the same disclosure.
- [x] 4.4 Verify the example script `examples/generate-skill.sh` header still states a truthful call count
      after the loop is added, and update the number and its reason if it does not.

**Wave B landed 2026-11-19 (3.1–3.7, 4.1–4.4).** The gate is in `SkillBuilderService.build` between the
frontmatter check and `EvalSource.generate`; the redraft entry point is `SkillGenerator.generate(name,
description, blockingFindings)`; the knobs are `BuildOptions.maxInspect`/`fullInspection` behind
`--max-inspect`/`--full-inspection`; the report section is `SkillBuildResult.appendInspection`. New
`SkillInspectionGateTest` (9 offline tests) plus 6 in `BuildCommandTest` and 2 in `SkillGeneratorTest`:
builder **239 tests, 0 failures** (was 222), `make lint` clean. Four things the plan got wrong or did not
know:

- **3.5's fixture premise was wrong, and the truth is more useful.** `Missing recommended section` **is** a
  MEDIUM finding (`SchemaValidator.java:638,648`) and MEDIUM is *not* an error severity
  (`Severity.isError(false, false)` is false for it), so `ValidationResult.addFinding` puts it in
  `getWarnings()` itself (`ValidationResult.java:176-191`; `recalculateFromFindings()` 288-308 rebuilds the
  same way). Advisory text and MEDIUM findings are therefore the *same list* — design D5's wording is
  correct, and the claim that a passing check's MEDIUM findings are "dropped entirely" is false. The real
  mechanism to honour is the opposite one: `classify` sweeps **every** finding of a *failing* blocking check
  into the blocking list, so a convention nudge rides along into the redraft whenever its own check also
  failed outright. That is what the fixture does, and the tests assert the invariant that actually holds —
  nothing the seam classifies as advisory reaches the prompt (`noAdvisoryReachesTheRedraftPrompt`), and no
  `pii` finding does whatever its severity.
- **4.3's quality score had no path to the builder at all.** The evaluator records it as result *metadata*
  (`QualityScoreValidator` → `quality_scores` → `overall_score`), never as a finding, so a passing quality
  check reported no number anywhere the seam could read. `SkillInspector` now reads that metadata key; the
  evaluator was not touched, and `SkillBuilderServiceTest:167` asserts the score is non-null on a real build.
- **`BuildOptions` grew two components (8 → 10) and kept an 8-arg constructor.** Task 4.2 forbade a studio
  edit, so the pre-inspection form delegates with the defaults rather than the studio being updated. The
  studio is untouched (`git status` lists no studio file) and no request DTO changed; **its compilation is
  proved by 5.5, not here.**
- **3.4's cap is on the prompt only, and the report's half of its verification holds by construction.**
  `MAX_FINDINGS_IN_PROMPT = 20` lives on `SkillGenerator`, which is the class that applies it; findings past
  the cap are counted, not quoted ("and N more findings of the same kind"), so the model is not told a
  truncated list was the whole verdict. The report renders `inspection.blockingFindings()` uncapped, so it
  does list every finding — but no test feeds the pipeline 27 findings to watch that happen.
- **Two verifications were re-pointed, not skipped.** 3.6 needed a 6-arg `SkillBuildResult.blocked` overload
  to carry the report, and proves "no publisher opened" with a `git:` target pointing at a nonexistent
  repository — `GitPublisher` clones in its constructor, so reaching it throws instead of returning. 3.7's
  "assert the temp folder contains exactly one file" is not observable from outside `SkillInspector`, so the
  test seeds a witness `evals.json` in `<tmp>/<slug>/` and asserts the gate's footprint is that one
  pre-existing file, unchanged, and that the eval suite was never drafted.
- **4.1a's documentation clause is 6.9's.** The flag ships working and self-describing in `--help` today; the
  `docs/08-builder.md` text — including that a default build does no secret, security or code-risk scanning —
  is 6.9's deliverable, and the gate is not documented until that lands.

## 5. Offline and regression guarantees

- [x] 5.1 Verify `make test-module MODULE=prod/embabel-smith-builder` passes with no model endpoint and no
      scanner binary installed — the new tests are offline and the evaluator's own suite is untouched.
- [x] 5.2 Verify `make lint` is clean, and that every new file carries the project author header the
      checkstyle header rule requires.
- [x] 5.3 Verify the evaluator's own tests still pass unmodified (`make test-module
      MODULE=prod/embabel-smith-skillevaluator`) and that none of its test files was edited to accommodate
      the consumer.
- [x] 5.4 Prove the new tests are non-vacuous: temporarily make the gate always pass and confirm the
      blocked-at-`inspect` test fails; restore. Record which test each mutation killed.

- [x] 5.5 **Run the studio module, not just its upstreams.** The studio embeds the builder, so it now
      resolves the evaluator transitively and compiles against a builder whose API this change widens — and
      this repo has a recorded history of builder changes breaking studio *test* compilation without any
      default build noticing, because the reactor stops at the first failing module. Verify
      `make test-module MODULE=prod/embabel-smith-studio` passes, or if the `frontend-maven-plugin`
      `npm install` binding makes that impractical here, run `test-compile` over the studio and say plainly
      which of the two was executed. Note that the SnakeYAML question is **not** a studio risk: the reactor
      resolves 2.6 everywhere, which is what the studio already ran before this change.

**Wave C landed 2026-11-19 (5.1–5.5), with two conditions the machine cannot supply.**

- **5.1 is proved for the endpoint and only argued for the scanners.** The endpoint half is direct: 14
  model-related variables (`OPENAI*`, `ANTHROPIC*`, `EMBABEL*`, `GEMINI*`) were removed from the
  environment with `env -u` and the builder suite still ran **239 tests, 0 failures** — the tests never
  touch a network because every caller in them is a scripted lambda. The scanner half is **not**
  reproducible on this box: `gitleaks`, `skillspector` and `semgrep` are installed
  (`bandit` is not), so the suite was never run in the state the task describes. What stands in its place
  is structural and stated as such: `requestedChecks(false)` names only the seven binary-free checks, and
  the whole module suite finishes in 16 s, which three scanner subprocesses would not permit.
- **5.2's evidence is the pre-commit hook, not a separate invocation.** `make lint` runs as a repo hook,
  and it passed on the gate commit; the header rule is part of the same Checkstyle config, and the new
  test file carries the SPDX header and `@author`.
- **5.3 is stronger than asked.** The evaluator ran **942 tests, 0 failures**, and its `src/test` tree has
  exactly one commit touching it — `init`, the squashed root. Nothing was edited to accommodate the
  consumer, and `git status` reports the module clean.
- **5.4 used two mutations, because one only proves half.** Forcing `Inspection.passed()` to `true` killed
  **8 of the 9** gate tests — all but `theDefaultLineupRunsSevenChecksAndDisclosesThree`, which correctly
  survives: it asserts the lineup and the disclosure, and under that mutation the build sails past the gate
  into the empty-eval-suite block, which still carries the same inspection record. Feeding
  `advisoryFindings()` instead of `blockingFindings()` into the redraft killed exactly the three feed-back
  tests — `advisoriesAndPiiAreReportedButNeverFedBack`, `everyRedraftAfterTheFirstCarriesThePreviousFindings`,
  `noAdvisoryReachesTheRedraftPrompt` — and left the six verdict tests alone, which is the split that shows
  the two rules are tested separately rather than by one fixture. Both mutations reverted; `git diff
  --quiet` confirmed the tree was byte-identical to the committed state afterwards.
- **5.5 ran the documented command, not the fallback.** `make test-module MODULE=prod/embabel-smith-studio`
  passed end to end — the `frontend-maven-plugin` node/npm/build bindings included — with the studio at
  **147 tests, 0 failures** and the reactor green through `smith-common`, `specsource`, `skillevaluator`,
  `runtime`, `builder` (239) and `studio` in 65 s. So the retained 8-arg `BuildOptions` form does keep the
  studio compiling, which wave B had asserted without proof.

## 6. Reversal of the dormancy record, plus the carried-over attribution

- [x] 6.1 In `docs/05-architecture.md`, replace the "**It is not yet wired to a consumer** … Treat it as
      built-but-unintegrated" sentence in the `embabel-smith-skillevaluator` paragraph with the reversal:
      its consumer is the builder's skill target, and it is absent from `dependencyManagement` no longer.
      Verify the page states the consumer by name and no longer claims dormancy or absence from the POM.
- [x] 6.2 In the same paragraph, name the six blocking checks, `pii` as run-but-advisory, and the three
      that are not requested on the default path, cross-linking `08-builder.md`. Verify the page does not
      imply SkillSpector, Gitleaks, Bandit or Semgrep run during a default build.
- [x] 6.3 Close the wiring bullet in `docs/12-backlog.md` as integrated-by-this-change rather than dormant,
      keeping its `Check:` command and verifying the command's output now matches the closed wording.
      **Baseline moved:** `24c6ce5` already rewrote this bullet — it now reads "named in `README.md` and in
      `docs/05-architecture.md`; it has no `.wiki/` page yet", and its "wired to nothing" claim is the part
      this change closes. Rework the existing text rather than replacing an older version of it.
- [x] 6.4 **Done by `24c6ce5`, not by this change.** The provenance bullet in `docs/12-backlog.md` now
      carries exactly the facts this task asked for — 50 project-author SPDX lines, 6 NVIDIA 2026, 2 NVIDIA
      2025, the eight being six configuration YAMLs plus two Python test fixtures, and no Java file carrying
      an NVIDIA header — with the `grep -rho 'SPDX-FileCopyrightText:.*' … | sort | uniq -c` check re-run
      against the wording. The bullet is marked closed. Do not rewrite it again.
- [x] 6.5 **Done by `24c6ce5`.** The clause "until now it had no mention anywhere in `docs/`, `README.md` or
      `.wiki/`" is gone, replaced by what is actually true now. Note the replacement names the opposite
      absence from the one this task predicted: the root `NOTICE` line and the `README.md` entry it expected
      to be missing both exist since `f8224f0`; what is genuinely absent is a `.wiki/` page.
- [x] 6.6 **Done by `f8224f0`.** The root `NOTICE` already carries the upstream paragraph — NVIDIA
      SkillEvaluator, its URL, Apache-2.0, and the eight verbatim files grouped by the year each header
      actually carries. **Appending again would duplicate the attribution**, so this task is closed rather
      than repeated. Verify with `grep -c 'NVIDIA' NOTICE` (four lines today) and leave it alone.
- [x] 6.7 **Reword the `README.md` row that already exists** — `f8224f0` added the
      `embabel-smith-skillevaluator` row, so it needs no new entry. What it must lose is its current claim,
      "**Built but not yet wired to a consumer**", which this change falsifies; replace it with the builder's
      skill target running the six binary-free checks as blockers plus `pii` as an advisory. Verify the
      wording does not claim the evaluator security-scans a skill, and that no second `skillevaluator` row
      was introduced.
- [x] 6.8 Record the supply-chain residual as its own line: `spi/SkillspectorSecurityScanner.java:96`
      holds an install hint naming an NVIDIA release URL, and on the default path it stays inert because
      the builder lineup never requests that scanner — `--full-inspection` is the one route that can
      surface it, and that flag is an explicit human action. Verify the file still holds that string and
      that the builder's default lineup does not include `security`.
- [x] 6.9 Record in `docs/08-builder.md` what the gate does **not** check, in plain words a reader cannot
      misread: a default skill build does no secret scanning, no SkillSpector security scan and no script
      risk analysis, and the build report says so. Name `--full-inspection` as the only way to request
      them, and that `AgentSpecSecurityScanner` remains the only security gate, widened to `evals.json` by
      `builder-skill-gate-integrity`. Verify the page names the `inspect` stage, `--max-inspect` and
      `--full-inspection` as they actually exist.
- [x] 6.10 Mark `openspec/changes/skillevaluator-dormant/` superseded in its own `README.md` with a
      pointer here, without editing its other files, and verify the two changes no longer state opposite
      decisions about the same module.

**Wave C landed 2026-11-19 (6.1–6.3, 6.7–6.10).** Four pages changed — `README.md`'s module row, the
`embabel-smith-skillevaluator` paragraph in `docs/05-architecture.md`, a new `inspect`-gate passage plus the
`smith build` synopsis in `docs/08-builder.md`, and the wiring bullet and a new supply-chain bullet in
`docs/12-backlog.md`. `6.10` needed no edit: the banner was already there from the supersession, and the
folder's other files were left alone. Two things worth recording:

- **A claim in the new `docs/08` text was wrong and was corrected before commit.** It said a draft "over the
  500-line ceiling or under the quality-70 floor is refused". The floor is right —
  `Constants.QUALITY_DEFAULT_MIN_SCORE = 70`, reached through `failGate`, so it blocks — but the ceiling is
  not: `SchemaValidator`'s `line_count` reports `Severity.MEDIUM` through
  `addFinding(finding, false, false)` (`SchemaValidator.java:567-578`), which makes it advisory. A
  600-line draft is commented on and published. The page now says that, because the plausible-sounding
  version of this sentence would have misled a reader into thinking length is enforced.
- **6.3's `Check:` command was run, and it is what proves the bullet closed.** `grep -rn skillevaluator
  prod/*/pom.xml pom.xml docs/` now returns `pom.xml:211` (the `dependencyManagement` entry that the old
  wording said was absent) and `prod/embabel-smith-builder/pom.xml:33` (the consumer), which is exactly the
  pair the closed wording claims. The negative-claim sweep ran across `README.md`, `docs/`, `.wiki/` and
  every `prod/` source: no page anywhere still calls the module dormant, unwired, or lacking a consumer.
  `docs/05`'s "carry no guard because they hold no model call" and the wiki's `NoDirectModelRequestTest`
  note were checked and stay true — the gate's checks are static, and the only route to an LLM-assisted
  quality scan is `--full-inspection`, which is not wired from the builder.

## 7. Close-out

- [x] 7.1 Re-ingest `.wiki/`, run the wiki lint and republish — the vocabulary changed (a new gate stage,
      a module that is no longer dormant) and the pages cross-link. Verify the lint reports no broken
      cross-links and the published pages show the reversal.
- [x] 7.2 Verify `git status --porcelain` lists only the builder sources, the two POMs, the docs/`NOTICE`/
      `README` pages, the `.wiki/` refresh and this change folder — and nothing under
      `prod/embabel-smith-skillevaluator/` or `poc/`.
- [x] 7.3 Run one live skill build through `examples/generate-skill.sh` and read the report, so the gate is
      evidenced against a real model draft rather than only canned JSON. Record the quality score and
      whether a redraft was needed.
- [x] 7.4 **Found while setting up 7.3, and fixed: the example scripts could not run a builder CLI at all
      once the seam landed.** `examples/smith-cli.sh` resolved the CLI classpath with
      `dependency:build-classpath -pl prod/embabel-smith-builder` after a `compile -am`, and that step now
      dies offline — `The POM for org.iromu.embabel.smith:embabel-smith-skillevaluator:jar:0.1.0-SNAPSHOT is
      missing` then `Cannot access embabel-snapshots … has not been downloaded from it before`, because a
      sibling dependency is resolved from the local repository, not from the reactor. Nothing in the test
      suite could have caught it: inside the reactor Maven resolves the sibling from source. Adding `-am`
      to the classpath goal does **not** help (tried — it still fails, and writes a classpath file for an
      earlier module). The fix is one word: the build step is now `install -am -DskipTests`, which is also
      what makes the evaluator's transitive SnakeYAML reachable. Verified: after the install, the resolved
      classpath contains `embabel-smith-skillevaluator-0.1.0-SNAPSHOT.jar` and `snakeyaml-2.6.jar`.

**Wave C close-out (7.1, 7.3).**

- **7.1 was a scoped ingest, and the Pages re-publish stays owed.** Three pages changed:
  `.wiki/features/skill-output-target.md` (the pipeline gains the `inspect` step and a section for the
  gate; its `Gaps` list also lost three claims that `9e88df3` had already falsified — `evals.json`
  published unscanned, a zero-expectation scenario being indistinguishable, and "no `--min-lift` flag"),
  `.wiki/features/smith-builder.md` (the CLI synopsis and the knob notes), and `.wiki/index.md` (the
  scoped-ingest note, the module's one-line identity, and one routing row). `last_commit` is deliberately
  **not** advanced — 1083 tracked files have moved since it, and a scoped ingest must not imply a full
  one. The link pass was recomputed here rather than taken on report: **45 pages, 44 distinct link
  targets, 0 broken links, 0 orphans.** `wiki-pages.yml` fires on pushes to `main` touching `.wiki/**`,
  and these commits are on `poc/iteration-2`, so https://iromu.github.io/embabel-smith/ still shows the
  pre-A2A wiki until the branch merges (or a manual `gh workflow run`).
- **7.3 ran a real build and the gate behaved exactly as specified — and then a different gate stopped
  the build.** `./generate-skill.sh Sentinel` against the local endpoint: 18 model calls over roughly 25
  minutes (2-3 min each on a contended GPU), and the report opened

  ```
  SKILL BUILD BLOCKED at security (system-specification-md-sentinel-multi-a)
    - CRITICAL SECRET_EXFIL: Spec prompt references a secret or credential [credential]
      inspect:     7 checks run, quality 84.2/100, 1 draft
      not scanned: security, secrets, code-integrity (no secret, security or code-risk scanning; pass
                  --full-inspection to request them)
  ```

  So against a real model draft the gate ran the seven-check lineup, scored **84.2/100**, and accepted it
  in **one draft** — no redraft was needed, which is the case no offline test can exhibit, since every
  canned fixture is either refused or synthetic. The advisory list came back with **13 lines, all
  MEDIUM or LOW** — three `Missing recommended section` nudges among them — which is the live
  confirmation that a MEDIUM finding on a passing check reaches the advisory list, exactly as the
  corrected `docs/08` wording says, and that the list is never empty for a generated skill. The build
  then blocked at the pre-existing `security` stage on a `CRITICAL SECRET_EXFIL` finding from
  `AgentSpecSecurityScanner`, so this run evidences the gate, not the publish path: the two gates are
  independent, and the writing-quality gate passing says nothing about the security one.
