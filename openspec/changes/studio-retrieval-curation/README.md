# studio-retrieval-curation

Wire retrieval into the product: extract the `SpecScorer` seam, the lexical scorer and the ranking
registry from the retrieval POCs into the builder, add curation rules that drop a retired pack and
de-rank a degraded one from the lifecycle sidecar the studio already reads, expose one
`POST /api/retrieve` endpoint over a source, and replace the `PocOnlyPane` stub at `/retrieve` with
the ranked, curated top-k. The `/scale` pane stays POC-only by decision: it benchmarks selector
methods over a synthetic library, which is evidence, not a capability a person asks the product for.
