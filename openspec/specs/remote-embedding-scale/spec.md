# remote-embedding-scale Specification

## Purpose

Select specs at scale using a *remote* OpenAI-compatible `EmbeddingService`, and measure both its
C10-comparable selection accuracy and the scaling dimension (amortised one-time embedding cost and
per-query retrieval latency as the same-typed library grows).

## Requirements

### Requirement: Embed text via a remote OpenAI-compatible endpoint

The system SHALL obtain dense embeddings by calling an OpenAI-compatible `POST /embeddings`
endpoint, sending the model id and a batch of input texts, and returning one vector per input text
in input order. The HTTP transport SHALL be injectable so the request/response handling is
verifiable without the real endpoint.

#### Scenario: Batch returns vectors in order
- **WHEN** a batch of texts is embedded
- **THEN** the result contains one vector per input text, aligned to the input order

#### Scenario: Request counting
- **WHEN** embeddings are requested
- **THEN** each `POST /embeddings` request is counted, so the total number of embedding
  round-trips is observable

#### Scenario: Transport is injectable
- **WHEN** the embedder is constructed with a custom transport
- **THEN** the request is routed through that transport, enabling offline verification (including a
  real round-trip against a local HTTP server)

### Requirement: Score specs by remote-embedding cosine

The system SHALL rank same-typed specs for a task query by cosine similarity between the query's
embedding and each spec's embedding, memoising each distinct query and each distinct spec so that
each is embedded at most once.

#### Scenario: Closer embedding ranks higher
- **WHEN** two candidate specs are scored against a query
- **THEN** the spec whose embedding is closer to the query's embedding scores higher

#### Scenario: Memoisation
- **WHEN** the same query is scored against the same spec repeatedly
- **THEN** the spec and query are not re-embedded (no additional embedding request)

### Requirement: Gate the live run on endpoint availability

The live benchmark SHALL run only when the embedding endpoint is reachable and serves the requested
model, and SHALL report a clear, actionable error otherwise. The non-endpoint logic SHALL be
verifiable offline.

#### Scenario: Endpoint ready
- **WHEN** the endpoint is reachable and lists the requested model
- **THEN** the benchmark runs

#### Scenario: Endpoint unreachable
- **WHEN** the endpoint cannot be reached
- **THEN** the run reports that the endpoint is not reachable and how to fix it, rather than an
  opaque failure

#### Scenario: Model missing
- **WHEN** the endpoint is reachable but does not list the requested model
- **THEN** the run reports that the model is not served and lists the models that are

#### Scenario: Offline verification
- **WHEN** the offline test suite runs without the endpoint
- **THEN** the embedder, scorer, gate, scaling, and report logic are verified with a stub transport,
  and the build stays green

### Requirement: Run a C10-comparable selection benchmark

The benchmark SHALL evaluate the remote embedding, the lexical baseline, and the dense baseline on
POC 12's identical same-typed shadowing library and report Precision@1 for each at the distinct and
shadowing scales, so the results are directly comparable to the existing spec-selection group.

#### Scenario: One Precision@1 per method per scale
- **WHEN** the C10-comparable scenario runs
- **THEN** it reports a Precision@1 for each of the three methods at both the distinct and the
  shadowing scale

### Requirement: Measure the scaling dimension

The benchmark SHALL sweep ascending same-typed library sizes and, for each, report Precision@1, the
one-time index-build cost, and the steady-state per-query retrieval latency (cosine over cached
vectors, no model call), so the amortised embedding cost and the O(n) per-query cost are visible.

#### Scenario: One point per size
- **WHEN** the scaling sweep runs over its sizes
- **THEN** it reports one point per size carrying Precision@1, the index-build cost, and the
  per-query latency

#### Scenario: Amortised embeddings
- **WHEN** the sweep reuses a shared memoising scorer across ascending sizes
- **THEN** the total number of distinct embeddings is bounded by the largest size plus the queries,
  not the sum of every size
