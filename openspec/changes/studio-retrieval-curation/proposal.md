# Proposal

## Why

Six POCs proved how to rank a spec library and proved what the ranking cannot do alone. The measured
reading in `docs/04-poc-run-results.md` §7.1 is that "retrieval is clean while specs are distinct
(100% at every scale, up to 1000 specs); near-duplicate shadowing collapses text-similarity (lexical
17%, dense 0%), and no text-similarity index cures it", and that "the real fix for shadowing is
library **curation** (dedup / lifecycle — POC 18, applied in the POC 15 retrieval pane)".

None of that reached the product. Verified 2026-11-18:

- `ls prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/rest/ | grep -Ei
  'retrieve|scale|remote'` → no output (exit 1). There is no retrieve or scale controller.
- `grep -ril 'retriev' prod/*/src/main/java | wc -l` → `0`. No scorer, no registry, no ranking class
  anywhere under `prod/`.
- `grep -rln 'PocOnlyPane' prod/embabel-smith-studio/frontend/src/` → `RetrievePane.vue`,
  `RemotePane.vue`, `ScalePane.vue`.

Meanwhile the product already holds the half of curation that was hardest: a pack's lifecycle state is
read from its `lifecycle.yml` sidecar and surfaced today by `SourceService.listPacks` as
`PackInfo(name, contentHash, lifecycleState, baselinePassRate, drift, updatedAt)`. The POC had to seed
lifecycle in memory (`poc/poc-22-unified-ui/.../SpecCuration.java`); the product does not need to,
because the state is already on disk. So the missing piece is small: a scorer seam, a ranker, two
curation rules, one endpoint, one pane.

## What Changes

- **The builder gains a `retrieval/` package** holding the extracted POC code (extract-don't-depend:
  copied, `poc/` untouched): `SpecEntry` (the index entry), `SpecScorer` (the `@FunctionalInterface`
  seam), `LexicalScorer` over `LexicalSimilarity` (cosine over word term-frequency), `SpecRanker`
  (score, sort, top-k), a factory that derives an entry's matched text from a pack's goal and step
  descriptions plus its I/O type names, and `RetrievalCuration` carrying the two rules — a retired
  pack is not surfaced, a degraded pack's score is halved.
- **One endpoint**: `POST /api/retrieve` with `{source, query, k}` returning
  `{scorer, results: [{name, description, score, outputType, lifecycleState}]}`. The controller reads
  the source through the existing `SourceService` and delegates all ranking and curation to the
  builder; the studio keeps no logic of its own.
- **`/retrieve` becomes real**: `RetrievePane.vue` replaces its `PocOnlyPane` wrapper with a source
  picker, a query box and a ranked table showing score, output type and lifecycle state, through the
  single `src/api/client.ts` fetch site and the `types.ts` DTO mirror.
- **Exactly one scorer ships: the lexical one.** The seam is the interface plus the `scorer` name in
  every response, so a denser scorer drops in later without changing the endpoint or the pane. No
  embedding, GLiNER or small-LLM scorer is wired, and no new dependency is added.
- **`/scale` stays POC-only.** Recorded as a decision in `docs/09-studio.md` rather than left as an
  unexplained stub.
- **Retrieval makes no model call**, so the pane works with no endpoint configured — the same
  degradation contract the rest of the disk-backed panes already hold.

## Capabilities

### New Capabilities

- `spec-retrieval`: ranking a source's packs against a natural-language query through a replaceable
  scorer, curating that ranking with the pack's recorded lifecycle state, and presenting the top-k
  with the scorer that produced it. It covers the index entry a pack contributes, what happens with
  no query or no packs, and the fact that the ranking never calls a model.

### Modified Capabilities

None. There are no capability specs under `openspec/specs/` — they were consolidated into `docs/` on
2026-11-15 — so there is no existing requirement text to modify. The durable home for these rules is
`docs/09-studio.md` (the Surfaces table and the POC-only row) and the POC-only claim in
`docs/11-spec-selection-pocs.md`, both named under Impact.

## Impact

- **`prod/embabel-smith-builder`**: new `retrieval/` package, extracted from
  `poc/poc-8-retrieval` (`SpecEntry`, `LexicalSimilarity`), `poc/poc-12-semantic-retrieval`
  (`SpecScorer`, `LexicalScorer`, `ScaleSpecRegistry`) and `poc/poc-22-unified-ui` (the two curation
  rules and the index-entry text synthesis). No new dependency.
- **`prod/embabel-smith-studio`**: `RetrieveController` plus its request/response DTOs; `SourceService`
  gains nothing (it already lists packs with lifecycle state and reads a named pack); the frontend
  gains `RetrievePane` content, two client functions and the mirrored DTO types.
- **`docs/09-studio.md`**: the `/retrieve` row leaves the "none — POC-only stubs" row, and the
  `/scale` decision is written down. **`docs/11-spec-selection-pocs.md`**: its opening claim that
  "the studio's `/retrieve` and `/scale` routes are explicit POC-only stubs" becomes true only of
  `/scale`. **`docs/12-backlog.md`**: the retrieval bullet is closed and the `/scale` decision
  recorded. A vocabulary change is a documentation change, so `.wiki/` needs a re-ingest, a wiki lint
  and a republish afterwards.
- **`poc/`**: untouched. The extraction copies; it does not add a dependency, which is what the
  enforcer ban on POC dependencies requires.
- **Not touched**: the agent spec format, the runtime, the builder's generation or ingestion paths,
  and the improvement loop. Retrieval reads a source; it changes nothing in one.
