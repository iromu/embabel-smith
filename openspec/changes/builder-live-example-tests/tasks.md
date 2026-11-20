# Tasks

Depends on `builder-docs-ingestion`: the extraction suite drives ingestion through the builder, and
while ingestion lives in the studio the builder's test sources cannot reach it.

## 1. Opt-in wiring

- [x] 1.1 Add `<excludedGroups>${test.excludedGroups}</excludedGroups>` to the root pom's Surefire
      `pluginManagement` block with a `test.excludedGroups` property defaulting to `live`, and verify
      `make test-module MODULE=prod/embabel-smith-builder` runs zero live tests and passes with no
      endpoint configured.
- [x] 1.2 Tag the four endpoint-gated tests `@Tag("live")` — builder `LiveBuildIntegrationTest`,
      runtime `LiveRunIntegrationTest`, studio `improve/RestIntegrationTest`, studio
      `improve/LiveLoopIntegrationTest` — keeping each `@EnabledIfEnvironmentVariable` in place, and
      verify a default run reports them not-run while an opted-in run selects them. (The plan named
      three; the runtime's live test is gated the same way and runs under `make test` too, so
      leaving it out would have left the first requirement unmet.)
- [x] 1.3 Add a `test-live` target to the `Makefile` that sources `~/.config/env/llm.env`, exports the
      endpoint variables and passes `-Dtest.excludedGroups= -Dgroups=live`, add its `make help` line,
      and verify the target's selected-test list contains the live classes and nothing else.
- [x] 1.4 Record in the `Makefile` comment and the `README.md` run book that `mvn test -Dgroups=live`
      alone selects nothing, because the default exclusion still applies, and verify the documented
      invocation is byte-identical to the one the target runs.
- [x] 1.5 Document the convention in `docs/05-architecture.md` beside the existing build entry points,
      and verify the page names the property and the target that actually exist.

## 2. Example harness plumbing

> Superseded 2026-11-17 — built, then removed with the suites that used it. The scripts under
> `examples/` need no repo-root helper and no cell matrix.


- [x] 2.1 Write the repository-root helper that reads `smith.repoRoot` and falls back to walking up
      from `user.dir` to a directory holding both `pom.xml` and `examples/`, and verify a test resolves
      it from a module working directory and fails naming both candidates when neither is present.
- [x] 2.2 Configure the builder module's Surefire to pass `smith.repoRoot=${maven.multiModuleProjectDirectory}`
      and `<runOrder>alphabetical</runOrder>`, and verify a probe test reports the resolved examples
      path and the three suites' execution order.
- [x] 2.3 Define the declared example — `examples/cases/<slug>/case.yml` naming blueprint, targets,
      techniques and eval tasks — plus its loader and directory scan, and verify loader tests cover a
      valid case, a case missing a required field which the failure names, and an unrecognised target
      or technique value.
- [x] 2.4 Write the per-cell `result.json` writer, documenting its fields on the class that writes it,
      and verify a round-trip test reads back stage, score, lift, issues and model.

## 3. Extraction suite

> Superseded 2026-11-17 — the prompt it drove is written for a codebase, not for these
> blueprints. See the Direction change in `proposal.md`.


- [ ] ~~3.1 Implement `BlueprintExtractionLiveTest`: for each declared example, load and assemble its
      blueprint through the builder and write the extracted description to
      `examples/cases/<slug>/description.md`, and verify a live run leaves one non-empty description per
      declared example.~~
      **Superseded 2026-11-17 by the section note above, and struck through 2026-11-20 so a closed section
      does not read as an open task.** The layout it names is gone: there is no `examples/cases/` directory,
      and the examples are hand-written blueprints under `examples/blueprints/` consumed directly by
      `examples/generate-spec.sh` and `examples/generate-skill.sh`. Nothing was implemented for it and nothing
      should be — the extraction prompt it would have driven is written for a codebase, not for a blueprint.
- [x] 3.2 Verify the skip path: with the live group requested and no endpoint configured, the suite is
      reported skipped rather than failed.

## 4. Generation suite

> Superseded 2026-11-17 — nine cells at ~200 model calls and 50 minutes of endpoint time
> published nothing. `examples/generate-spec.sh` and `examples/generate-skill.sh` do one cell each,
> by hand.


- [x] 4.1 Implement `ExampleGenerationLiveTest` parameterized over the cells each example declares,
      consuming the extracted description and failing any example whose description is absent with a
      message naming `BlueprintExtractionLiveTest`, and verify a case with no description fails that
      way without any extraction call being made.
- [x] 4.2 Wire each cell so the `BuilderAgent` scratch directory is the cell's `candidates/`, the
      publish target is the cell's `published/`, and `overwrite` is set, and verify a rerun of one cell
      replaces its artifacts instead of colliding at `publish`.
- [x] 4.3 Write `report.md` (the build report verbatim) and `result.json` for both outcomes — published
      and blocked-at-a-gate — and verify a blocked cell still leaves both files naming its stage, and
      that a blocked cell does not by itself fail the run.
- [x] 4.4 Keep the `embabel` cell in the matrix as evidence, recording the `DANGLING_TYPE_REF` block the
      technique is known to produce, and verify the report shows that cell rather than omitting it.

## 5. Reporting suite
> Superseded 2026-11-17 with the suites whose results it aggregated.


- [x] 5.1 Implement `ExampleReportLiveTest` to fail when a declared cell produced no `result.json`, and
      verify that deleting one result file makes it name that cell.
- [x] 5.2 Render `examples/generated/REPORT.md` with one row per declared cell — stage reached, eval
      score, skill lift, model, wall time — reading the values from `result.json` rather than
      re-deriving them, and verify the row count equals the declared cell count.
- [x] 5.3 Record quality without gating on it, and verify a cell with a zero score or zero lift is
      rendered and leaves the suite passing.

## 6. Integration checks

- [x] 6.1 Superseded: the matrix was run once, and its outcome (nine cells, 49.9 min, zero
      published) is what ended it. See the Direction change in `proposal.md`.
- [x] 6.2 Verify `make test` and `make lint` are green offline with the live group excluded, so the
      default reactor is unaffected by everything above.

## Workflow follow-up

- Decided 2026-11-17: generated output is gitignored (`examples/out/`), and both the `cases/` tree
  and the extracted `description.md` files are gone with the suites that wrote them.
- The matrix-sizing note turned out to be the whole finding: nine cells was ~200 model calls, and
  that is why the unit of work is now one blueprint, one target, one deliberate run.
- Archive after a full live run has been captured, and re-ingest `.wiki/` plus republish once
  `docs/05-architecture.md` changes.

## 7. Scripts in place of the matrix (2026-11-17)

- [x] 7.1 `examples/smith-cli.sh` — the bootstrap the scripts share: load the endpoint env, resolve
      the builder's runtime classpath (cached under `/tmp` keyed on the module pom), start the CLI
      with `java -cp`, because `spring-boot:run` cannot pass picocli argv. Verify a run resolves 206
      classpath entries offline and reaches `SmithBuilderApplication.main`.
- [x] 7.2 `examples/generate-spec.sh <blueprint>` — one blueprint to one agent spec through
      `smith build`, writing `report.md` and `exit.txt` whether or not a gate let it through, with
      its header stating the per-build call cost. Verify the arg plumbing, blueprint lookup and
      output folders against a dead endpoint.
- [x] 7.3 `examples/generate-skill.sh <blueprint>` — the same for the skill target, its header
      naming the ~15-19 calls and why (four calls per eval scenario). Verify as above.
- [x] 7.4 `examples/README.md` as the run book, including why no script passes `--extract`, and
      `examples/out/` gitignored.
- [x] 7.5 Run both scripts once against a live endpoint and read the reports — the scripts are
      verified as plumbing, not yet as a build that published.
      **Run 2026-11-20 against `http://spark.local:4000/v1` with `Qwen-instruct`, blueprint
      `Sentinel` (one 31.7 KB document), both with `-- --max-refine 0`. They are now also evidence of
      a build that published, and of a gate that refused one.**
      - `generate-spec.sh` — **exit 0, `BUILD OK: sentinel_security_ops`**, in 12 minutes over 18
        endpoint timeouts. Published `goal.yml`, `manifest.yml`, `lifecycle.yml`, 8 `steps/`, 9
        `types/`; eval 1.000, security SAFE at T1_COMMUNITY, lifecycle INCUBATING. The two-candidate
        mechanism ran for real and did its job: the multi-agent process
        `sentinel_security_operations` failed validation (goal output unreachable from the entry, four
        agents with no producer, one cycle, `DANGLING_TYPE_REF` across six packs), so the single-agent
        candidate was chosen and scored. The `DANGLING_TYPE_REF` cluster the `embabel` technique is
        known for, reproduced here by a generated process rather than by that technique.
      - `generate-skill.sh` — **exit 1, `SKILL BUILD BLOCKED at security`**, and the block is the more
        interesting half. The lift gate **passed**: `+0.438` (with 0.875 against a 0.438 baseline) over
        four model-drafted scenarios, so the skill measurably improved the agent. The security scanner
        then refused it on `CRITICAL SECRET_EXFIL — Spec prompt references a secret or credential`.
        Inspection had already run: 7 checks, quality 84.2/100, one draft, 16 advisory findings, none
        blocking. Worth a reader's attention rather than my verdict: the blueprint *is* a
        security-operations process, so its subject matter is credentials and exfiltration, and a
        domain-accurate skill can trip an exfil heuristic on what it is about. Not investigated here;
        recorded, not assumed to be a false positive.
      - **Three defects this run found — all in the harness, not the product, all fixed.**
        `generate-skill.sh` documented `[-- <extra args>]` and never stripped the separator, so picocli
        read it as end-of-options and rejected flags it does declare; the run died at startup
        (`c352df6`). A run that prints no verdict left a **zero-byte `report.md`**, the file the run
        book tells you to read; it now keeps the CLI's last words. And the report cut demanded a colon
        after the verdict word, which only the `OK` forms print — **so every blocked build had been
        cutting an empty report**, including this one, whose 2.9 KB report named the refusing gate and
        the measured lift and had been thrown away (`894a185`). That third one means 7.2's promise that
        `report.md` is written "whether or not a gate let it through" was false from the day it was
        ticked; its verification checked the plumbing against a dead endpoint, which only ever produces
        the no-verdict case. All three fixed and verified against real logs, not against a model of
        them.
      - **What this does not settle:** one blueprint, one technique, one model, `--max-refine 0`.
        Nothing was pushed through `--technique embabel` (its block is already recorded from the
        matrix), and the security refusal above is unexplained.
- [ ] 7.6 Deferred: the live test worth adding — a small blueprint with a known expected agent spec
      and skill, asserted rather than recorded.
