---
title: "Retrieval and spec shadowing (POC 8, POC 12)"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "poc/poc-8-retrieval/src/main/java/com/embabel/smith/retrieval/SpecRegistry.java"
  - "poc/poc-8-retrieval/src/main/java/com/embabel/smith/retrieval/RetrievalRunner.java"
  - "poc/poc-8-retrieval/src/main/java/com/embabel/smith/retrieval/LexicalSimilarity.java"
  - "poc/poc-8-retrieval/src/main/java/com/embabel/smith/retrieval/SampleSpecLibrary.java"
  - "poc/poc-12-semantic-retrieval/src/main/java/com/embabel/smith/retrieval/scale/ScaleRetrievalRunner.java"
  - "poc/poc-12-semantic-retrieval/src/main/java/com/embabel/smith/retrieval/scale/SpecScorer.java"
updated_at: "2026-11-04"
---

# Retrieval and spec shadowing (POC 8)

POC 8 measures the **spec-shadowing** problem (doc 03 §2.1, doc 02 caveat #1): as a spec library
grows, many specs share the same input/output types, so a pure type-based (GOAP) router cannot
tell them apart — only their *descriptions* can. POC 8 quantifies that problem and shows a
**semantic pre-filter** resolves it, so the planner only sees a narrow, on-topic candidate set.
Fully **offline** — no endpoint needed.

## The three measurements (`RetrievalRunner`)

- **`shadowingFactor`** — how many same-typed specs a type-based router would have to consider for
  a task needing `inputTypes → outputType`.
- **`precisionAt1`** — how often the pre-filter returns the expected spec as the top hit, across a
  set of benchmark queries.
- **`growthCurve`** — for one target query, start from a base library, add same-verb distractors one
  at a time, and record whether the target is still retrieved first at each size. This is the
  "selection accuracy as the library grows" measurement.

## The registry and the scorer

- **`SpecRegistry`** — an in-memory index over `SpecEntry` metadata. `typeCompatibleCount`
  computes the shadowing factor; `retrieve(query, k)` ranks specs by similarity to their
  `name + description` and returns the top-k.
- **`SpecEntry`** — the lightweight index entry: `id`, `name`, `description`, `inputTypes`,
  `outputType`. Only this metadata is held hot; the full pack is read on demand once a spec is
  selected (progressive disclosure — see [[progressive-disclosure]]).
- **`LexicalSimilarity`** — a deterministic, dependency-free scorer: cosine similarity over
  term-frequency vectors. It stands in for the embedding-based index doc 03 sketches; swapping it
  for an `EmbeddingService` is a drop-in change to `SpecRegistry.retrieve`.

## The sample library

`SampleSpecLibrary.base()` is 14 same-typed specs (all `UserInput → BackgroundMessage`, so type
alone can't select), several of which share the "explain X" verb on purpose. `withExplainDistractors`
adds four more same-verb entries to stress the pre-filter.

## Result

Over the 14-spec library the lexical retriever hits **Precision@1 100%**, and the
coffee-explainer stays top-1 as four same-verb distractors are added.

## The 200+ scale (POC 12)

POC 12 (`poc/poc-12-semantic-retrieval`) extends this pre-filter to a 191→281 same-typed library
and compares the lexical baseline against a dense feature-hashing retriever (a `SpecScorer` seam
that a real `EmbeddingService` plugs into). It is clean (P@1 100%) at 191 distinct specs, but
near-duplicate (shadowing) specs collapse P@1 for **both** retrievers (lexical 17%, dense 0%) —
a library-curation problem, not a retriever problem.

## The shadowing remedies (POC 17, 19, 20)

Three POCs test **meaning-based** alternatives on the identical candidate sets: a GLiNER2
transformer (POC 17 — strict 100%), a 0.6B fine-tuned LLM in Docker (POC 19 — 67% free-form), and
a real MiniLM embedding (POC 20 — 33% full-library). All beat text-similarity decisively; none
fully cures the collapse. The real fix is library curation ([[spec-lifecycle]]). See
[[shadowing-remedies]] and [[what-the-pocs-prove]].
