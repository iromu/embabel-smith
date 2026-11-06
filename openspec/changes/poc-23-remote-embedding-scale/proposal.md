# Proposal

## Why

POC 20 proved that a *real* embedding index (a local bundled ONNX MiniLM) retrieves cleanly at the
distinct scale and beats text-similarity under shadowing. But it left two questions about the
**production-style** backend open: (1) does a genuine **remote** `EmbeddingService` — the kind the
runtime would actually wire in over the network, not a bundled model — work the same way on claim
C10's scenario; and (2) **how does selection scale** as the skill library grows far beyond the 281
same-typed skills POC 12/20 used? A per-candidate LLM/transformer selector needs *n* model calls
**per query**, which does not scale; an embedding index embeds each skill once and then ranks by
pure-CPU cosine, which does.

## What Changes

- Add a new POC module `poc/poc-23-remote-embedding-scale` (**POC 23**) to the `poc/pom.xml`
  aggregator.
- Introduce a **remote `EmbeddingService`** (`RemoteEmbedder`) that calls an OpenAI-compatible
  `POST /embeddings` endpoint (here the `nomic-embed-text-v2` llama.cpp server, proxied by LiteLLM)
  over the JDK `HttpClient`, with an injectable transport so the request/response handling is
  testable offline.
- Add a `RemoteEmbeddingScorer` (POC 12's `SkillScorer` seam) backed by the remote embedder, with
  memoised query/entry vectors, so it is directly comparable to the lexical / dense / MiniLM
  scorers on identical libraries.
- Add an **endpoint gate** (`RemoteEmbeddingGate`) that probes `{baseUrl}/models` and fails with a
  clear, actionable message when the endpoint is unreachable or the model is missing.
- Reuse POC 12's `ScaleSkillLibrary` (the 191/281 same-typed shadowing scenario) for a
  **C10-comparable** run, and add a new `LargeScaleLibrary` generator (to 1000 same-typed skills)
  for the **scaling dimension**.
- Add a `ScalingMeasurement` that sweeps library sizes and reports Precision@1, the one-time
  index-build cost, and the steady-state per-query retrieval latency (cosine over cached vectors).
- A `RemoteEmbeddingApp` launcher (via `exec-maven-plugin`) that runs both scenarios;
  **endpoint-gated** live run, offline tests use a stub transport / a local `HttpServer`.
- Document POC 23 in its `README.md` and in `docs/04-poc-run-results.md`; extend the shared C10
  results section (POC 8 / 12 / 17 / 19 / 20) with the remote-nomic data point and the scaling
  dimension.

## Capabilities

### New Capabilities
- `remote-embedding-scale`: Select skills at scale using a *remote* OpenAI-compatible
  `EmbeddingService`, and measure both its C10-comparable selection accuracy and the scaling
  dimension (amortised one-time embedding cost + per-query retrieval latency as the library grows).

### Modified Capabilities
<!-- none: no existing spec-level requirements change -->

## Impact

- **New module:** `poc/poc-23-remote-embedding-scale` (Java 21; depends on
  `poc-12-semantic-retrieval` for the scenario, the `SkillScorer` seam, the baselines, and the P@1
  math; adds `jackson-databind` for the embeddings request/response; HTTP via the JDK
  `HttpClient` — no new transport dependency).
- **No model download:** the embeddings come from a live endpoint, not a bundled artifact; the
  module carries no `models/` directory.
- **Docs:** a POC 23 row in the C10 claim table, a numbered entry in `docs/04-poc-run-results.md`
  §3, and an extension of §7.1's shared skill-selection results; the shared results block in the
  POC 8 / 12 / 17 / 19 / 20 READMEs is updated to include the remote-nomic row and the scaling
  dimension (kept byte-identical across all six).
- **No changes to existing POCs' code** — POC 23 reuses POC 12 code read-only.
