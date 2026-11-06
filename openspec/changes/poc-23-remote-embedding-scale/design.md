# Design

## Shape

POC 23 is a benchmark, not a product. It has one seam shared with POC 12 — the
`SkillScorer` (`score(query, entry) → double`) — and reuses POC 12's `ScaleSkillRegistry`
(ranking), `ScaleRetrievalRunner` (P@1 / top-k math), `LexicalScorer`, `SemanticScorer`, and
`ScaleSkillLibrary` (the 191/281 same-typed shadowing scenario) read-only. The only new scoring
component is `RemoteEmbeddingScorer`, which differs from POC 12's `SemanticScorer` and POC 20's
`RealEmbeddingScorer` solely in where the vectors come from: a **network** `EmbeddingService`.

```
ScaleSkillLibrary (191/281)  ─┐
LargeScaleLibrary (→1000)     ─┤
LexicalScorer                 ─┼─► ScaleSkillRegistry ─► ScaleRetrievalRunner / ScalingMeasurement
SemanticScorer (feature hash) ─┤        ▲
RemoteEmbeddingScorer ─────────┘        │
        ▲                               │
        └── RemoteEmbedder ── HTTP POST /embeddings ──► nomic-embed-text-v2 (llama.cpp via LiteLLM)
```

## RemoteEmbedder

- Talks to an OpenAI-compatible `POST {baseUrl}/embeddings` with `Authorization: Bearer <key>` and
  body `{"model": <model>, "input": [ ... ]}`; parses `data[].embedding` (indexed by `data[].index`)
  back into `double[][]`, preserving input order.
- The HTTP transport is an injectable `EmbeddingTransport` (`String post(String jsonBody)`). The
  default is a JDK `HttpClient`; tests supply a stub (deterministic vectors) or a local
  `com.sun.net.httpserver.HttpServer` to exercise the real request path offline.
- A `requestCount()` counter records one request per `embedMany` call, so the benchmark can report
  the true number of embedding round-trips (the amortised index cost).
- `AutoCloseable` — closes the `HttpClient` on shutdown.

## Memoisation and the scaling argument

`RemoteEmbeddingScorer` memoises query vectors (by query string) and entry vectors (by entry id) in
`ConcurrentHashMap`s. Consequences, which the benchmark makes visible:

- **One-time index cost is amortised.** Each distinct skill is embedded exactly once, regardless of
  how many queries are served. In the size sweep the shared scorer means moving from size *n* to
  size *m* embeds only the *m − n* new skills.
- **Steady-state per-query retrieval is pure CPU.** Once the vectors are cached, ranking a query is
  O(n) cosine with no model call — measured separately from the (network-bound) index build.
- A per-candidate LLM/transformer selector has no such separation: it needs *n* model calls per
  query. That contrast is the point of the scaling dimension.

## Two scenarios

1. **C10-comparable** — POC 12/20's identical 191/281 same-typed shadowing library. Reports
   Precision@1 for lexical / dense / remote-nomic at the core (191) and full (281) sizes, directly
   comparable to the existing C10 group.
2. **Scaling** — a `LargeScaleLibrary` generated to a configurable size (default up to 1000
   same-typed skills, round-robin across six verb families with distinct generated topics). Sweeps
   ascending sizes and reports P@1, the one-time index-build cost, and steady-state per-query
   latency.

## Endpoint gating

`RemoteEmbeddingGate.check(probe, model)` gates the live run: the probe `GET {baseUrl}/models` and
collects the model ids. Outcomes: unreachable (clear "not reachable" message), reachable but no
models, model present (ready), or model missing (names the found models). The probe is injectable
for tests. The launcher (`main`) reads `EMBED_BASE_URL` / `EMBED_API_KEY` (falls back to
`OPENAI_API_KEY`) / `EMBED_MODEL` / `EMBED_SCALE_SIZES`, gates, then runs `run(...)`. `run` is pure
apart from the supplied `RemoteEmbedder`, so offline tests drive it with a stub transport.

## Testing strategy

Offline (no endpoint): `RemoteEmbedderTest` (parsing, batch order, request count, and a real HTTP
round-trip against a local `HttpServer`), `RemoteEmbeddingGateTest` (reachable / unreachable /
model-missing / no-models), `RemoteEmbeddingScorerTest` (cosine ranking + memoisation),
`ScalingMeasurementTest` (sweep shape + amortised embedding count), `RemoteEmbeddingAppTest`
(two-scenario report shape). The live benchmark is the `exec:java` run against the real endpoint.

## Risks / trade-offs

- **Endpoint availability.** The live run needs the reachable endpoint; the gate turns a missing
  endpoint into an actionable error rather than a hang or opaque failure.
- **Embedding-model dependence.** The shadowing numbers are for *this* embedding model; a different
  model (e.g. the local MiniLM) scores differently under shadowing. The POC reports the specific
  model and keeps the comparison apples-to-apples on the same scenario.
- **Live run cost.** The full run embeds ~1300 short texts; against a CPU llama.cpp backend this
  takes a few minutes. The scale sizes are configurable to bound it.
