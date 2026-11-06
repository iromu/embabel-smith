# Tasks

## 1. Module scaffold + dependencies

- [x] 1.1 Create the `poc/poc-17-gliner-retrieval` module (pom.xml + `src/main/java` + `src/test/java`)
  and register it in the `poc/pom.xml` aggregator; verify the reactor recognizes it with
  `mvn -q -pl poc/poc-17-gliner-retrieval validate`.
- [x] 1.2 Confirm the GLiNER4j `gliner4j-core` Maven GAV and the `com.microsoft.onnxruntime:onnxruntime`
  version it expects (from the upstream release / Maven Central), add those plus the
  `poc-12-semantic-retrieval` dependency to the module pom, and verify the module compiles with
  `mvn -q -pl poc/poc-17-gliner-retrieval -am compile`.

## 2. Selector seam + baseline selectors

- [x] 2.1 Add the `SkillSelector` interface (`String select(String query, List<SkillEntry> candidates)`)
  and verify it with a unit test asserting the top-1 contract on a trivial candidate set.
- [x] 2.2 Implement `LexicalSelector` (argmax over POC 8 `LexicalSimilarity`) and `DenseSelector`
  (argmax over POC 12 `SemanticSimilarity`); verify each with a unit test that returns the correct
  top-1 id on a small candidate set.

## 3. GLiNER2 classifier + model gating

- [x] 3.1 Implement `GlinerModel` — resolve the bundle path from `BUILDER_GLINER_MODEL_DIR` (default
  the module's `models/gliner2-base-v1`), verify the expected files, and throw a clear, actionable
  error naming the missing bundle and the download command when absent; verify the missing-model
  error with a unit test.
- [x] 3.2 Implement `GlinerSelector` — build one `ClassificationLabel(name, description)` per
  candidate, call `GLiNER4jClassifier.classify(query, threshold)`, and return the top-scored label's
  skill id; verify the label-building and top-1 selection with a unit test using a stubbed
  classifier (no model).
- [x] 3.3 Add a `models/` gitignore entry and a short setup note (the one-time `huggingface-cli
  download` of the INT8 bundle); verify `git status --ignored` shows `models/` ignored.

## 4. Benchmark runner + launcher

- [x] 4.1 Implement `ShadowingRunner` — drive each `SkillSelector` over the six `ScaleSkillLibrary`
  targets × the matching and strict candidate-set variants, compute Precision@1 per method per
  variant, and print a comparison table; verify with a unit test using stubbed selectors that the
  P@1 math is correct and deterministic.
- [x] 4.2 Implement the `GlinerRetrievalApp` launcher (wired via `exec-maven-plugin`) that loads the
  model, runs the runner, and prints the table; verify `mvn -pl poc/poc-17-gliner-retrieval
  exec:java` either runs or fails with the clear missing-model error when the bundle is absent.

## 5. Real benchmark run + results documentation

- [x] 5.1 Download the INT8 ONNX bundle into the module's `models/` directory (one-time), run the
  real benchmark, and record the three-method Precision@1 (matching + strict); verify the launcher
  prints the comparison table with a real transformer result.
- [x] 5.2 Document POC 17 in `README.md` (module table row + per-POC run command) and add a POC 17
  section to `docs/04-poc-run-results.md` with the measured numbers; verify the documented run
  command matches the launcher's actual invocation.

## Workflow follow-up

- Archive the change once the POC 17 benchmark has run and its results are documented.
