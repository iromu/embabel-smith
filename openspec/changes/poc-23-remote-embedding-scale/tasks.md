# Tasks

## 1. Module scaffold + dependencies

- [x] 1.1 Create the `poc/poc-23-remote-embedding-scale` module (pom.xml + `src/main/java` +
  `src/test/java`) and register it in the `poc/pom.xml` aggregator; verify the reactor recognizes it.
- [x] 1.2 Add the `poc-12-semantic-retrieval` and `jackson-databind` dependencies (HTTP via the JDK
  `HttpClient` — no new transport dependency) and verify the module compiles with
  `mvn -q -pl poc/poc-23-remote-embedding-scale -am compile`.

## 2. Remote EmbeddingService + scorer seam

- [x] 2.1 Implement `RemoteEmbedder` — OpenAI-compatible `POST /embeddings` over an injectable
  `EmbeddingTransport`, batch request/response parsing (input order preserved), a `requestCount()`
  counter, and `AutoCloseable`; verify with unit tests including a real HTTP round-trip against a
  local `HttpServer`.
- [x] 2.2 Implement `RemoteEmbeddingScorer` (POC 12 `SkillScorer` seam) with memoised query/entry
  vectors and cosine via POC 12's `SemanticSimilarity.cosine`; verify ranking and memoisation (no
  re-embed on repeat scoring) with a stub transport.

## 3. Endpoint gate

- [x] 3.1 Implement `RemoteEmbeddingGate` — an injectable model-list probe, `GET {baseUrl}/models`,
  and a `check` that returns ready / unreachable / no-models / model-missing with actionable
  messages; verify all four outcomes with a stub probe.

## 4. Scenarios + scaling measurement

- [x] 4.1 Add `LargeScaleLibrary` — a generator of exactly *n* distinct-topic same-typed skills
  (round-robin across six verb families) plus its six benchmark targets; verify the exact count and
  that the targets are in the library.
- [x] 4.2 Implement `ScalingMeasurement` — sweep ascending library sizes, reporting Precision@1,
  the one-time index-build cost, and steady-state per-query latency (cosine over cached vectors);
  verify the sweep shape and that embeddings are amortised (shared memoising scorer) with a stub.

## 5. Launcher + live benchmark

- [x] 5.1 Implement `RemoteEmbeddingApp` (wired via `exec-maven-plugin`) — read the endpoint env
  vars, gate, then run both scenarios (C10-comparable on POC 12/20's 191/281 library + the scaling
  sweep); verify `run` is testable offline with a stub transport.
- [x] 5.2 Run the live benchmark against the real `nomic-embed-text-v2` endpoint and record the
  results (C10-comparable P@1 + the scaling curve to 1000 skills); verify the launcher prints both
  scenarios.

## 6. Documentation

- [x] 6.1 Write `poc/poc-23-remote-embedding-scale/README.md` (mermaid diagram, endpoint-gating,
  run command, the shared C10 results section, and a Conclusion against the hypothesis).
- [x] 6.2 Document POC 23 in `docs/04-poc-run-results.md` (C10 claim row, a numbered §3 entry, and
  the §7.1 shared results) and update the shared C10 results block in the POC 8 / 12 / 17 / 19 / 20
  READMEs to stay byte-identical across all six.

## Workflow follow-up

- Archive the change once the POC 23 live benchmark has run and its results are documented.
