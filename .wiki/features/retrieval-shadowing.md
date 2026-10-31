---
title: "Retrieval and skill shadowing (POC 8)"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "poc/poc-8-retrieval/src/main/java/com/embabel/smith/retrieval/SkillRegistry.java"
  - "poc/poc-8-retrieval/src/main/java/com/embabel/smith/retrieval/RetrievalRunner.java"
  - "poc/poc-8-retrieval/src/main/java/com/embabel/smith/retrieval/LexicalSimilarity.java"
  - "poc/poc-8-retrieval/src/main/java/com/embabel/smith/retrieval/SampleSkillLibrary.java"
updated_at: "2026-11-01"
---

# Retrieval and skill shadowing (POC 8)

POC 8 measures the **skill-shadowing** problem (doc 03 §2.1, doc 02 caveat #1): as a skill library
grows, many skills share the same input/output types, so a pure type-based (GOAP) router cannot
tell them apart — only their *descriptions* can. POC 8 quantifies that problem and shows a
**semantic pre-filter** resolves it, so the planner only sees a narrow, on-topic candidate set.
Fully **offline** — no endpoint needed.

## The three measurements (`RetrievalRunner`)

- **`shadowingFactor`** — how many same-typed skills a type-based router would have to consider for
  a task needing `inputTypes → outputType`.
- **`precisionAt1`** — how often the pre-filter returns the expected skill as the top hit, across a
  set of benchmark queries.
- **`growthCurve`** — for one target query, start from a base library, add same-verb distractors one
  at a time, and record whether the target is still retrieved first at each size. This is the
  "selection accuracy as the library grows" measurement.

## The registry and the scorer

- **`SkillRegistry`** — an in-memory index over `SkillEntry` metadata. `typeCompatibleCount`
  computes the shadowing factor; `retrieve(query, k)` ranks skills by similarity to their
  `name + description` and returns the top-k.
- **`SkillEntry`** — the lightweight index entry: `id`, `name`, `description`, `inputTypes`,
  `outputType`. Only this metadata is held hot; the full pack is read on demand once a skill is
  selected (progressive disclosure — see [[progressive-disclosure]]).
- **`LexicalSimilarity`** — a deterministic, dependency-free scorer: cosine similarity over
  term-frequency vectors. It stands in for the embedding-based index doc 03 sketches; swapping it
  for an `EmbeddingService` is a drop-in change to `SkillRegistry.retrieve`.

## The sample library

`SampleSkillLibrary.base()` is 14 same-typed skills (all `UserInput → BackgroundMessage`, so type
alone can't select), several of which share the "explain X" verb on purpose. `withExplainDistractors`
adds four more same-verb entries to stress the pre-filter.

## Result

Over the 14-skill library the lexical retriever hits **Precision@1 100%**, and the
coffee-explainer stays top-1 as four same-verb distractors are added.

**The 200+ scale is now covered by POC 12** (`poc/poc-12-semantic-retrieval`), which extends this
pre-filter to a 191→281 same-typed library and compares the lexical baseline against a dense
feature-hashing retriever (a `SkillScorer` seam that a real `EmbeddingService` plugs into). It is
clean (P@1 100%) at 191 distinct skills, but near-duplicate (shadowing) skills collapse P@1 for
**both** retrievers — a library-curation problem, not a retriever problem. See [[what-the-pocs-prove]].
