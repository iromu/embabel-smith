# POC 12 — Semantic retrieval at scale

Extends POC 8's lexical pre-filter to the **200+ same-typed skill library** that doc 02 §3.4 calls
"the bottleneck when you have many skills" and the "More Skills, Worse Agents?" result
(arXiv:2605.24050, up to −21% at 202 skills) describes. It compares the **lexical baseline** (POC 8's
word-TF cosine) against a **denser retriever** (feature-hashing character/word n-gram embeddings — an
offline, deterministic stand-in for the embedding index doc 03 §2.1 asks for) and measures selection
accuracy as the library grows and as near-duplicate (shadowing) skills accumulate.

Fully offline and deterministic — no model endpoint.

## What it proves

- **Retrieval scales cleanly at 200+ skills when skills are distinct.** At 191 same-typed
  (`UserInput → BackgroundMessage`) skills, both the lexical and the dense retriever hit
  **Precision@1 100%** on the benchmark — the "big folder" is not inherently the problem.
- **Near-duplicate shadowing collapses selection for both retrievers.** Once same-topic variants
  (e.g. "Explains how a coffee machine works *for a child*" vs the canonical one) accumulate, the
  canonical skill is no longer the uniquely-best text match, so Precision@1 drops sharply
  (lexical 17%, dense 0%). This reproduces the *selection-failure* mechanism the literature
  attributes to skill shadowing (not context overhead).
- **A denser text index does not cure shadowing on its own.** The failure is intrinsic to
  text-similarity when same-topic variants are more similar than the canonical skill. The fix the
  literature points to — library **curation** (dedup / lifecycle, doc 02 §3.3: SE-GoS, TrackRL) or a
  higher-level router — is orthogonal to the retriever. A real `EmbeddingService` plugs into the same
  `SkillScorer` seam, so this is representative of the dense-retrieval regime without an endpoint.

## How it works

`ScaleSkillLibrary` builds the same-typed library: a **core** of 191 distinct-topic skills across six
verb families, plus **near-duplicates** — 15 same-topic variants per benchmark target. `SkillScorer`
is the retriever seam; `LexicalScorer` reuses POC 8's word-TF cosine, `SemanticScorer` uses
`SemanticSimilarity` (feature-hashing char/word n-grams → fixed 256-d vector → cosine).
`ScaleSkillRegistry` ranks by the pluggable scorer; `ScaleRetrievalRunner` measures precision@1/@5
and the growth curve as near-duplicates are interleaved in.

## Run it

Offline (no endpoint):

```bash
mvn -pl poc/poc-12-semantic-retrieval exec:java
```

See `docs/04-poc-run-results.md`.
