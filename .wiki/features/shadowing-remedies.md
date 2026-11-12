---
title: "Shadowing remedies: transformer, small-LLM, and embedding (POC 17, 19, 20, 23)"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "poc/poc-17-gliner-retrieval/src/main/java/com/embabel/smith/retrieval/gliner/GlinerSelector.java"
  - "poc/poc-17-gliner-retrieval/src/main/java/com/embabel/smith/retrieval/gliner/GlinerModel.java"
  - "poc/poc-19-small-llm-selector/src/main/java/com/embabel/smith/selector/llm/LlmSelector.java"
  - "poc/poc-19-small-llm-selector/src/main/java/com/embabel/smith/selector/llm/DecisionPrompt.java"
  - "poc/poc-20-semantic-embedding/src/main/java/com/embabel/smith/retrieval/embedding/RealEmbeddingScorer.java"
  - "poc/poc-20-semantic-embedding/src/main/java/com/embabel/smith/retrieval/embedding/MiniLmEmbedder.java"
  - "poc/poc-23-remote-embedding-scale/src/main/java/com/embabel/smith/retrieval/remote/RemoteEmbedder.java"
  - "poc/poc-23-remote-embedding-scale/src/main/java/com/embabel/smith/retrieval/remote/RemoteEmbeddingScorer.java"
  - "poc/poc-23-remote-embedding-scale/src/main/java/com/embabel/smith/retrieval/remote/ScalingMeasurement.java"
updated_at: "2026-11-05"
---

# Shadowing remedies: transformer, small-LLM, and embedding (POC 17, 19, 20, 23)

POC 12 ([[retrieval-shadowing]]) showed that near-duplicate shadowing collapses text-similarity
for both lexical (17%) and dense feature-hashing (0%) retrievers. These POCs test
**meaning-based** alternatives on the *identical* candidate sets — and POC 23 adds the
**production-shaped remote embedding** plus the **scaling dimension**:

| POC | Technique | Gate | Result (matching / strict) |
| --- | --- | --- | --- |
| POC 17 | GLiNER2 zero-shot transformer (ONNX) | local-model-gated | **83% / 100%** |
| POC 19 | 0.6B fine-tuned LLM (llama.cpp Docker) | docker-gated | 50% / 67% (free-form) · 0% / 50% (decision format) |
| POC 20 | Real MiniLM embedding (local ONNX) | local-model-gated | 33% (full-library P@1, 281 specs) |
| POC 23 | Remote nomic embedding (OpenAI-compatible endpoint) | endpoint-gated | 17% (full-library P@1, 281) · **100% P@1 to 1000 specs** |

All four **beat or match text-similarity**; none fully cures the collapse (the near-duplicates
are near-identical in *meaning* too). The real fix is library **curation** ([[spec-lifecycle]]).

## POC 17 — GLiNER2 transformer

A zero-shot BERT classifier (GLiNER4j over ONNX Runtime, INT8) that scores the task query against
a small candidate set by *meaning*. On the 6-target shadowing sets it recovers the tie
text-similarity cannot: **strict 100%** (near-exact duplicate removed), **matching 83%**.
Local-model-gated (one-time ~253 MB ONNX download; offline tests mock the classifier).

## POC 19 — small-LLM selector

A 0.6B model fine-tuned for single-label decisions (`iromu/Qwen3-0.6B-openjev`, Q8_0 GGUF),
served in a `llama.cpp` Docker container whose lifecycle **Spring Boot Dev Services manages** from
the module's `compose.yml`. Scored under two prompt formats: the model's own typed-decision
contract (lettered options) and a free-form prompt. The free-form prompt outperforms the
model's own trained format (67% vs 50% strict) — the options are full spec descriptions, not the
short labels its 84.9% was measured on. Docker-gated (one-time ~380 MB GGUF download).

## POC 20 — real semantic `EmbeddingService`

A genuine ONNX sentence-encoder (`all-MiniLM-L6-v2`, 384-dim BERT, HuggingFace tokenizer,
mask-aware mean-pool, L2-normalised) behind POC 12's `SpecScorer` seam. At the **distinct**
scale (191 specs) it retrieves cleanly (**100%**); under **shadowing** (281 specs) it scores
**33%** — better than lexical (17%) and dense (0%) because it scores by meaning — but still gets
shadowed on 4 of 6 targets. Local-model-gated (one-time ~23 MB ONNX download). This is the
genuine `EmbeddingService` doc 03 §2.1 calls for, not a stand-in.

## POC 23 — remote embedding at scale

The production-shaped backend: a **remote** `EmbeddingService` — the `nomic-embed-text-v2`
OpenAI-compatible `POST /embeddings` endpoint (a llama.cpp server, proxied by LiteLLM, on a
different machine) — behind POC 12's `SpecScorer` seam. Two findings:

- **C10-comparable.** On POC 12/20's identical 191/281 same-typed shadowing library, the remote
  embedding retrieves cleanly at the distinct scale (100% at 191) and, under shadowing, ties
  lexical (17%) and beats dense (0%) but trails the local MiniLM (33%) — an honest data point that
  the *specific* embedding model matters, and that curation remains the real fix.
- **Scaling.** On a generated library to **1000** same-typed specs, Precision@1 holds at **100%**
  while steady-state per-query retrieval (cosine over cached vectors, no model call) grows linearly
  and stays **0.48 ms → 1.27 ms**. The one-time embedding index (one embedding per spec) is paid
  once and amortised; a per-candidate LLM/transformer selector would need *n* model calls *per
  query*. Endpoint-gated (offline tests use a stub transport / a local `HttpServer`).

## Shared reading

Retrieval is clean while specs are distinct (100% at every scale). Near-duplicate shadowing
collapses text-similarity, and no text-similarity index cures it. Meaning-based methods all beat
text-similarity, but the near-duplicates are near-identical in meaning too — so curation
([[spec-lifecycle]], applied in the POC 15 retrieval pane) is the real fix. See
[[what-the-pocs-prove]] and `docs/04-poc-run-results.md` §7.1.
