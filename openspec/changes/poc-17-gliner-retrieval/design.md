# Design

## Context

See `proposal.md` for motivation. The relevant current state:

- POC 12 (`poc/poc-12-semantic-retrieval`) already builds the 200+ same-typed library
  (`ScaleSkillLibrary`: six verb-family targets, each a canonical skill + 15 near-duplicates) and
  the two baselines — POC 8's `LexicalSimilarity` (word-TF cosine) and POC 12's
  `SemanticSimilarity` (feature-hashing char/word n-gram cosine). POC 12 depends on POC 8.
- The `fastino/gliner2-base-v1` model is a unified 205M-param BERT (GLiNER2) that does NER **and
  text classification**. It is not distributed as ONNX, but `gravitee-io/gliner4j-gliner2-base-v1`
  is a ready-made ONNX bundle of that exact model, shipping `classifier_full.onnx` (the
  classification head), `gliner4j_config.json`, and `tokenizer.json`, in three precision variants
  (FP32 ~830 MB, FP16 ~416 MB, INT8 quantized ~208 MB).
- `gravitee-io/GLiNER4j` runs GLiNER2 over ONNX Runtime (CPU default), on Java 21, and exposes a
  `GLiNER4jClassifier` facade: `load(modelDir, labels)` → `classify(text, threshold)`.
- The project is Java 21, Maven multi-module; POCs are added under the `poc/pom.xml` aggregator.

## Goals / Non-Goals

**Goals:**
- A GLiNER2 fine-grained classifier that selects one skill from a *small* candidate set.
- A three-way benchmark (transformer vs lexical vs dense) scored on **identical** candidate sets,
  so the shadowing result is directly comparable to POC 12.
- Local-model-gated execution: runs with the bundle present, fails clearly without it, and keeps
  the non-model logic offline-testable.

**Non-Goals:**
- Not a flat classifier over all ~200 skills (GLiNER2 classification is only tractable over a
  small label set).
- Not a coarse family router (the two-stage "option A" — deferred to a possible future POC).
- Not a production retrieval layer; this is a POC/benchmark.
- No changes to POC 8 / POC 12 — POC 17 consumes their code read-only.

## Decisions

**D1 — Mechanism: fine-grained classifier (option B).** For each target, GLiNER2 classifies the
query against the candidate set `{canonical + its near-duplicates}` (~16 labels) and selects the
top hit.
- *Why over two-stage routing (A):* isolates the shadowing question (the exact hard part POC 12
  found), is the simplest design, and is the most literal "classify and select one."
- *Why over NER entity-overlap (C):* classification is the model's native task; entity-overlap is
  a derived similarity, not a true classifier, and would not test the transformer's semantic
  discrimination the way we want.

**D2 — Model + runtime: GLiNER4j over the ready-made ONNX bundle.** Consume
`gravitee-io/gliner4j-gliner2-base-v1` (the exact `fastino/gliner2-base-v1`, classification head
included). No Python conversion.
- *Why:* the user named `fastino/gliner2-base-v1`; this bundle *is* that model, with no export step
  and the classification head present.
- *Alternatives:* (a) convert the safetensors → ONNX via GLiNER4j's Python scripts — heavier, needs
  Python + PyTorch; (b) community ONNX exports (`lion-ai/`, `fenixpyre/`) — less certain they carry
  the classification head or match the GLiNER4j bundle layout. The gravitee-io bundle is the
  cleanest and is the upstream-recommended path.
- *Precision variant:* default **INT8 quantized** (~208 MB) for CPU speed; keep **FP32** available as
  an accuracy reference if results look threshold-sensitive.

**D3 — Candidate-set construction.** Reuse `ScaleSkillLibrary`: for each of the six targets, the
candidate set is `nearDuplicatesOf(targetId)` plus the canonical entry. Two variants:
- **matching** — all 15 near-duplicates + canonical (16 total), directly comparable to POC 12.
- **strict** — exclude the empty-tail `v0` (the near-exact duplicate of the canonical), leaving the
  canonical + 14 qualified variants (15 total), the meaningful shadowing signal.

**D4 — Selector seam (comparability).** Introduce a small `SkillSelector` interface —
`String select(String query, List<SkillEntry> candidates)` returning the top-1 id — with three
implementations:
- `LexicalSelector` — argmax over POC 8 `LexicalSimilarity` per-candidate scores.
- `DenseSelector` — argmax over POC 12 `SemanticSimilarity` per-candidate scores.
- `GlinerSelector` — build one `ClassificationLabel(name, description)` per candidate, call
  `GLiNER4jClassifier.classify(query, threshold)`, return the top-scored label's skill id.
- *Why a new seam rather than reusing POC 12's `SkillScorer` (`double score(query, entry)`):*
  GLiNER2 is a **batch** classifier over a label set, not a per-entry scorer. A `SkillSelector`
  seam lets all three methods be driven identically by one runner and lets the classifier be
  mocked in offline tests.
- *Alternative considered:* force GLiNER2 into `SkillScorer` by scoring per entry — rejected, it
  misrepresents the model and loses the batch classification.

**D5 — Model access + gating.** A `GlinerModel` loader resolves the bundle path (default
`poc/poc-17-gliner-retrieval/models/gliner2-base-v1`, overridable via the `BUILDER_GLINER_MODEL_DIR`
env var, following the existing `BUILDER_*` convention), verifies the expected files exist, and
throws a clear, actionable error (naming the missing bundle and the download command) if absent.
`GlinerRetrievalApp` (the `exec:java` launcher) is the model-gated entry point; offline tests build
a `SkillSelector` with a stubbed classifier so the build stays green without the model.
- *Why:* mirrors the existing endpoint-gated pattern (clear configuration error, not an opaque
  model error).

**D6 — Runner + reporting.** `ShadowingRunner` drives each `SkillSelector` over the six targets ×
two variants, computes Precision@1 per method per variant, and prints a comparison table
(transformer / lexical / dense). `GlinerRetrievalApp` is the launcher.

## Risks / Trade-offs

- [GLiNER4j exact Maven GAV + ONNX Runtime version unconfirmed] → Confirm coordinates
  (`gliner4j-core` groupId/artifact/version, and the `com.microsoft.onnxruntime:onnxruntime` version
  it expects) against the upstream release / Maven Central before coding; pin in the module pom.
- [ONNX Runtime native library] → `onnxruntime` pulls a native `.so`; verify it resolves from Maven
  Central and loads on this Linux host with a smoke test early.
- [Classification head on ~16 labels] → GLiNER2 is a zero-shot classifier for small label sets; ~16
  is in range, but verify empirically that scores discriminate. If scores are flat, re-run with the
  FP32 variant and note it.
- [Bundle is large (~208 MB INT8) and not committed] → gitignore `models/`; document the one-time
  `huggingface-cli download`; the offline build never needs the bundle.
- [INT8 quantization may reduce accuracy vs FP32] → default INT8 for speed; if P@1 looks
  threshold-sensitive, report FP32 alongside.
- [The `v0` near-exact duplicate makes "correct" ambiguous] → report both variants; treat the
  **strict** variant as the meaningful shadowing signal and the matching variant as the
  POC-12-comparable one.
- [English-only model] → the skill library is English, so this is fine; record it as a constraint.

## Migration Plan

N/A — new, additive module. Rollback: remove `poc-17-gliner-retrieval` from `poc/pom.xml` and delete
the directory; no other module depends on it.

## Open Questions

- Exact GLiNER4j Maven GAV and the ONNX Runtime version it requires — to be pinned at
  implementation (a dependency detail; does not change the specs, the approach, or the task
  breakdown).
- Whether to also report full-281-library behavior — out of scope for option B; defer.
