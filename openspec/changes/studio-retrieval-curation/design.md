# Design

## Context

See `proposal.md` for motivation; `specs/spec-retrieval/spec.md` for the contract.

Facts this design has to work with, each read this session:

- The studio holds no capability of its own. `docs/09-studio.md`: "It holds **no capability of its
  own** — every controller delegates to a proven class from the runtime or builder." So ranking code
  cannot live in `prod/embabel-smith-studio`.
- The lifecycle half of curation is already in the product. `SourceService.listPacks` reads each
  pack's `lifecycle.yml` and returns `PackInfo(..., lifecycleState, baselinePassRate, drift, ...)`,
  with the sidecar's state rendered by the Lifecycle pane today.
- The scorer seam is a one-method interface. `poc/poc-12-semantic-retrieval/.../scale/SpecScorer.java`
  declares `double score(String query, SpecEntry entry);` and `ScaleSpecRegistry` ranks a
  `List<SpecEntry>` by it and takes the top-k — the whole registry is a stream with a sort.
- The POC's curation was in-memory because the POC had no sidecar. `SpecCuration` seeds every catalog
  spec as `ACTIVE` at "an assumed-passing score of 1.0" and keeps the states in a `Map`.
- POC 22's `SpecCatalog.toEntry` synthesizes the matched text: name, then the goal description, then
  every step description, then the input and output type names — "so a natural-language query matches
  on meaning words, not just type names".
- The POC methodology fixes the degenerate cases: "Selecting from an empty candidate set yields
  'none' rather than an error."
- The measured numbers: lexical scores 100% P@1 on 191 distinct specs and 17% on 281 specs under
  shadowing; the remote nomic embedding ties it at 17% there.

## Goals / Non-Goals

**Goals:**

- A person picks a source, types a task, and sees which packs it routes to, with the score, the
  output type and the lifecycle state of each.
- A retired pack cannot be recommended by the product, and a degraded one sinks.
- The scorer is replaceable without touching the endpoint, the DTO or the pane.
- Everything runs offline. No model call, no new dependency.

**Non-Goals:**

- Not a benchmark. No Precision@1 curve in the product; `/scale` stays POC-only (D6).
- Not a semantic retriever in this change. One lexical scorer ships (D4).
- Not a deduplication tool, and not a lifecycle engine — both already exist (the loop's
  `LifecycleTransitions` and the builder's `LifecycleStamper`). This change only *reads* the state.
- No change to the agent spec format, to generation, or to what a run does.

## Decisions

**D1 — The retrieval core lives in the builder, in a new `retrieval/` package.**
Rejected: putting it in `prod/embabel-smith-studio`, which contradicts the module's stated rule above
and would make the logic unreachable from the builder's own CLI; rejected: `prod/embabel-smith-specsource`,
whose job is resolving where packs come from, not judging which one answers a query — and it has no
dependency on the scorer concept today. The studio's contribution is one controller that maps a
request to a call and back to a DTO, which is the pattern every other pane's controller follows
(`TrustController` delegates to `TrustTierPolicy` the same way).

**D2 — Curation reads the sidecar state the studio already has; the POC's `SpecCuration` is not ported.**
The controller asks `SourceService` for the source's packs, and each pack's `lifecycleState` string is
already in hand. Rejected: porting `SpecCuration`, which seeds an in-memory baseline at an assumed
1.0 pass rate and holds state in a `Map` — the product persists state in `lifecycle.yml` and the
improvement loop transitions it, so a second, in-memory copy would disagree with the loop the moment
a pack was refined. The POC needed the seed because it had no sidecar; the product has one.

**D3 — Two curation rules, no more: retired is dropped, degraded is halved.**
`surfaced(state) = state != RETIRED` and `deRankFactor(state) = (state != DEGRADED) ? 1.0 : 0.5`, the
same two rules and the same 0.5 the POC ran. A pack with **no** sidecar at all — `lifecycleState` is
`null`, which is a normal state for a freshly authored pack — is surfaced at full score: absence of a
contract is not evidence of a broken one. Rejected: de-ranking `INCUBATING` too, which would hide
every pack the loop has not yet validated and would silently make the pane useless on a new source.

**D4 — One scorer ships: lexical. The seam is the interface plus the name in the response.**
The response carries `scorer` (POC 22 did the same), so wiring a second implementation later is a
bean change, not a contract change. Rejected now: the embedding scorer (`builder.scorer=embedding` in
the POC wired `RealEmbeddingScorer` over an ONNX MiniLM) — it adds a model-runtime dependency and a
download artifact to a dev UI, and the evidence says a text-similarity collapse is *not* cured by
swapping scorer alone (the remote nomic embedding tied lexical at 17% under shadowing), so buying it
buys latency at scale rather than the fix. Rejected: no seam at all, which hardcodes cosine inside the
controller and makes the next method an API change.

**D5 — The index entry's matched text is goal description + step descriptions + I/O type names.**
Carried from the POC verbatim in shape, because it is the measured thing: type names alone made
natural-language queries miss. The synthesis takes an `AgentSpec`, which the builder already reads,
and lives next to the scorer it feeds.

**D6 — `/scale` stays POC-only, and the reason is written down.**
`ScaleController` measures a *method* over `ScaleSpecLibrary.core()` and `.full()` — generated specs
with nothing to do with the operator's library. In the product it could only report the one shipped
scorer, and `docs/04-poc-run-results.md` §7.1 already records that method's curve across six
techniques. Rejected: wiring it, which spends a pane on re-running evidence; rejected: deleting the
route, which removes the pointer to that evidence for no gain. The backlog's "retrieval at scale" gap
is answered by the seam plus curation, not by a live benchmark.

**D7 — Degenerate input returns an empty result, not an error.**
Blank query, or a source with no readable packs, yields `results: []` with the scorer named. This is
the POC methodology carried over, and it keeps the pane honest: an empty library is a fact about the
source, not a fault. An *unparseable source URI* still throws as it does for every other pane,
because that is a bad request.

**D8 — `k` defaults to 10 and is capped.** The POC's retrieve response is already bounded by `k`;
the product caps it so a typo cannot ask for the whole library, and the cap is one constant.

## Risks / Trade-offs

- **A lexical-only pane under-performs exactly where the POC says it does.** → The pane names its
  scorer in its own header, and D4's response field means a better scorer is a bean, not a redesign.
  The curation rules are the part that addresses the measured collapse.
- **Curation silently hides a pack the operator expected to see.** → The response carries each hit's
  `lifecycleState`, and the pane renders it, so a sink is visible. A retired pack is dropped, which is
  the point of retiring it; the Lifecycle pane still lists it.
- **Two implementations of the same curation rule now exist** (POC 22 and the builder) and could
  drift. → Acceptable and intended: `poc/` is frozen evidence. The builder's copy is the one with
  tests that matter, and the rule is two one-line methods.
- **Ranking reads every pack in the source per query.** `SourceService.readPack` re-reads from disk.
  → Fine at the sizes the product targets and for a dev tool; a cached index is what the embedding
  scorer would bring, and it is explicitly deferred rather than silently pre-built.
- **The docs' POC-only claim becomes partly false if this lands without the doc edit.** → The
  `docs/09`, `docs/11` and `docs/12` edits are tasks in this change, not a follow-up, and the wiki
  re-ingest is named with them.
