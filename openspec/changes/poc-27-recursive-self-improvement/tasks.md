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

- [ ] 3.1 Build the referee as a separately started process that constructs the gate over the frozen task set through its existing injectable seams, and verify scoring one known-good and one known-bad candidate returns the expected verdicts offline
- [ ] 3.2 Enforce that the referee loads no skill folder and opens no script context, and verify a test asserts the referee's own classpath and context carry neither
- [ ] 3.3 Make the task set read-only to the loop and verify a candidate that emits proposed evaluation tasks is excluded from scoring and the exclusion is recorded
- [ ] 3.4 Refuse to start the outer loop when no baseline is recorded and verify the refusal names the missing artifact

## 4. Inner loop — the improver as a generated script

- [ ] 4.1 Run a hand-written detection policy as a JavaScript skill script through `poc-26`'s engine and verify it reproduces the existing loop's three detection triggers on a known trace window
- [ ] 4.2 Generate detection policies as JavaScript from a policy prompt and verify the generated script parses and executes under the engine's statement and time limits
- [ ] 4.3 Score each generated policy against the labelled corpus, reporting precision and recall separately on the held-out split, and verify the offline scoring test asserts a mixed policy yields two numbers rather than one blended score
- [ ] 4.4 Run the inner loop to convergence over the free generations and verify the run writes a per-generation record with zero model calls counted
- [ ] 4.5 Record a policy that attempts to reach outside its sandbox as a rejected generation and verify the rejection names the fixed artifact from `MUTABILITY.md`

## 5. Process generation from the self-description

- [ ] 5.1 Generate a multi-agent process from the self-description through the existing process generator with single-shot technique only, and verify the output carries at least two agent specs
- [ ] 5.2 Validate the generated hand-offs with the existing process validator, writing the pack folder directly rather than adding a build target, and verify a spec set with an uncovered input or an unresolved cycle is named and not scored
- [ ] 5.3 Verify the generated process describes the pipeline the builder actually runs, by checking each named stage against `docs/08-builder.md`, and record any stage the generation dropped

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

- [ ] 8.1 Hold gated candidates for approval and publish nothing, and verify a passing candidate increments the held count with no pack written to a source
- [ ] 8.2 Verify the loop never pushes, by asserting the push flag is never set on any code path the POC can reach

## 9. Evidence and documentation

- [ ] 9.1 Verify every cited paper's arXiv identifier against arXiv and correct the list, since `poc/` is frozen afterwards and a wrong identifier is permanent
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
