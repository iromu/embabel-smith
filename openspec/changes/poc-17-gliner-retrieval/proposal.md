# Proposal

## Why

POC 8 (lexical) and POC 12 (dense feature-hashing) both select among a 200+ same-typed skill
library by *text similarity*, and POC 12 measured that near-duplicate (shadowing) skills collapse
selection for both — lexical **17%** and dense **0%** Precision@1 — a failure it attributes to
text-similarity itself, not to a too-weak index. This POC proves a **different mechanism**: a real
transformer zero-shot classifier (GLiNER2 via GLiNER4j) that *understands* meaning rather than
matching hashed n-grams, run on the same shadowing scenario to test whether it breaks the tie that
text-similarity cannot.

## What Changes

- Add a new POC module `poc/poc-17-gliner-retrieval` (**POC 17**) to the `poc/pom.xml` aggregator.
- Introduce a GLiNER2-based **fine-grained skill classifier** (`GlinerSelector`) built on
  `gravitee-io/GLiNER4j` over the `fastino/gliner2-base-v1` ONNX bundle: it classifies a task query
  against a *small* candidate set (the canonical skill + its near-duplicates) and selects the top
  hit.
- Add a common **"selector → top-1 over a candidate set"** seam so the GLiNER2 classifier is
  directly comparable to POC 8's lexical and POC 12's dense scorers on *identical* candidate sets.
- **Reuse** POC 12's `ScaleSkillLibrary` (6 targets + near-duplicates) and POC 8/12's
  `LexicalSimilarity` / `SemanticSimilarity` as the two baselines (POC 17 depends on
  `poc-12-semantic-retrieval`).
- A `ShadowingRunner` + launcher report Precision@1 for each of the three methods on the
  within-topic shadowing scenario, in two variants: a POC-12-matching set (all near-duplicates,
  directly comparable) and a "strict" set (excluding the degenerate near-exact duplicate).
- Introduce a **new runtime category — local-model-gated**: the benchmark runs when the ONNX bundle
  is present (one-time `huggingface-cli download` of `gravitee-io/gliner4j-gliner2-base-v1`);
  offline unit tests mock the classifier so the module still builds green without the model.
- Document POC 17 in `README.md` (module table + run command) and `docs/04-poc-run-results.md`.

## Capabilities

### New Capabilities
- `gliner-skill-classification`: Select one skill from a same-typed candidate set using a GLiNER2
  transformer zero-shot classifier, and measure its selection accuracy against lexical and dense
  baselines on the skill-shadowing scenario.

### Modified Capabilities
<!-- none: no existing spec-level requirements change -->

## Impact

- **New module:** `poc/poc-17-gliner-retrieval` (Java 21; depends on `poc-12-semantic-retrieval`).
- **New dependencies:** `gravitee-io` GLiNER4j core artifact + `com.microsoft.onnxruntime:onnxruntime`
  (CPU execution provider). Exact GAV/version to be confirmed against the upstream release.
- **Model artifact:** one-time download of the `fastino/gliner2-base-v1` ONNX bundle
  (`gravitee-io/gliner4j-gliner2-base-v1`; INT8 quantized ~208 MB default) into the module's
  `models/` directory; the bundle is gitignored (large binary), not committed.
- **Docs:** `README.md` module table row + per-POC run command; a POC 17 section in
  `docs/04-poc-run-results.md`.
- **No changes to existing POCs** — POC 17 reuses POC 8/12 code read-only.
