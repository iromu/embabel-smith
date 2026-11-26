# Spec-Selection POCs

Three POCs answer one question: **when a library holds many same-typed specs, which one does an
agent pick?** Measured results live in [`04-poc-run-results.md`](04-poc-run-results.md) §7.1 — this
page records the contracts, the shared methodology, and the design reasoning, so the numbers are not
restated (and cannot drift) in two places.

These are **POC-only**: no module here is a dependency of `prod/`, and the studio's `/scale` route is
still an explicit POC-only stub. One exception is this page's own finding
being acted on rather than a second POC: the *mechanism* these POCs were measured against — POC 8's
index-entry shape and POC 12's lexical scorer, plus POC 18's lifecycle curation rule — was **copied**
into the builder's `retrieval/` package and is wired behind the studio's `/retrieve` pane. Copied, not
depended on: `poc/` is frozen evidence. The selector mechanisms below (POC 17, 19, 23) remain
unwired. See [`12-backlog.md`](12-backlog.md).

## The scenario, and why it is hard

POC 12 established the failure: retrieval is clean at 191 *distinct* specs, but once same-topic
near-duplicates accumulate, the canonical spec stops being the uniquely-best match and Precision@1
collapses. A denser index does not cure it — near-duplicates are near-identical in *meaning* too. The
remedies are library curation (POC 18's lifecycle, dedup) or a higher-level selector. Each POC below
is one selector measured against that scenario.

## Shared methodology

Three contracts are identical across the three POCs and are stated once here rather than three times.

**Identical candidate sets, one top-1 selection.** Every method is driven over the *same* candidate
sets and reports Precision@1, so a method comparison is never a scenario comparison. Selecting from
an empty candidate set yields "none" rather than an error. A non-answer from a model counts as a
**miss, not a random guess** — that keeps the table honest.

**Two candidate-set variants.** A strict variant and a POC-12-matching variant are both reported.
The *strict* one is the meaningful signal; the matching variant exists only for comparability with
POC 12, because its near-exact duplicate with an empty tail makes "correct" ambiguous.

**Resource gating.** Each POC runs its live benchmark only when the backing resource is present,
fails with an actionable error when it is not, and keeps its offline tests green by stubbing the
gated dependency. This is the same gate shape as the runtime's endpoint gate —
see [`05-architecture.md`](05-architecture.md).

| POC | Mechanism | Gate |
| --- | --- | --- |
| 17 `poc-17-gliner-retrieval` | GLiNER2 zero-shot transformer over ONNX | local-model-gated (one-time ONNX bundle) |
| 19 `poc-19-small-llm-selector` | 0.6B decision-tuned LLM, lettered-option contract | docker-gated (app-managed llama.cpp container) |
| 23 `poc-23-remote-embedding-scale` | remote OpenAI-compatible `POST /embeddings` | endpoint-gated |

## Why each mechanism was built the way it was

**POC 17 — a fine-grained classifier, not a router.** Option B (classify directly over a small label
set) was chosen over a two-stage family router and over NER entity-overlap, because it isolates the
shadowing question, is the most literal reading of "classify and select one", and NER overlap is "a
derived similarity, not a true classifier". It is explicitly *not* a flat classifier over all ~200
specs — GLiNER2 is only tractable over a small label set. INT8 is the default bundle for CPU speed,
with FP32 kept as the accuracy reference.

A new `SpecSelector` seam (batch, top-1) was introduced rather than reusing the per-entry
`SpecScorer`, because forcing a batch classifier through a per-entry scorer misrepresents the model
and loses the batch classification. The new seam lets every method be driven identically and mocked
offline.

**POC 19 — the prompt format is itself a measured row.** The model is a typed-decision contract
(lettered options, single-letter answer), so in-distribution and out-of-distribution formats are
registered as *two separate methods* rather than averaged together. Serving uses Spring Boot's
docker-compose Dev Services rather than Testcontainers, because the Compose variant keeps everything
on the main classpath, leaves an inspectable `compose.yml`, can run standalone, and connects-only
when the container is already up — which makes prompt iteration cheap without reloading the model.

**POC 23 — embeddings are the mechanism that scales.** A per-candidate LLM or transformer selector
needs *n* model calls **per query**. An embedding index embeds each spec once, so steady-state
retrieval is pure-CPU cosine over cached vectors with no model call. That separation *is* the scaling
dimension POC 23 measures: Precision@1, the one-time index-build cost, and per-query latency out to a
1000-spec same-typed library.

## Caveats that must travel with any number quoted from these POCs

- **Shadowing numbers are embedding-model-specific.** A different model scores differently, so a
  result names its model and keeps the scenario apples-to-apples.
- **Some hardware findings are host-specific**, not general: the 2080 Ti (Turing) cannot serve
  ModelOpt FP8, which needs Hopper or newer, and CPU-only serving sufficed for a 0.6B model. Label
  such figures as observations about this machine.
- Large model artifacts are gitignored and acquired by a documented one-time command. **Container
  images are *not* pinned**: POC 19's compose file uses the floating tag
  `ghcr.io/ggml-org/llama.cpp:server` with no `@sha256:` digest, and the results document records
  neither a tag nor a digest. Treat the latency figures as observations about this machine at this
  moment, and pin the digest before re-running them for a comparison.
