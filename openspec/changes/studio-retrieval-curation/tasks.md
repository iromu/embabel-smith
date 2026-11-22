# Tasks

Independent of `builder-docs-ingestion` and `builder-live-example-tests`: retrieval reads a source,
it neither ingests documents nor builds a pack.

## 1. Retrieval core in the builder

- [x] 1.1 Copy `SpecEntry` from `poc/poc-8-retrieval` into a new `retrieval/` package in
      `prod/embabel-smith-builder`, keeping its defensive copy of `inputTypes`, and verify a unit test
      constructs an entry with null input types and reads back an empty list.
      → `retrieval/SpecEntry.java` (copied, compact-constructor `List.copyOf` kept).
      `make test-module MODULE=prod/embabel-smith-builder` → `Tests run: 212, Failures: 0, Errors: 0,
      Skipped: 0` / `BUILD SUCCESS`; `SpecEntryTest` 2 passed
      (`nullInputTypesReadBackAsAnEmptyList`, `inputTypesAreCopiedSoTheCallerCannotMutateThem`).
      Mutation: replacing `List.copyOf(inputTypes)` with `inputTypes` →
      `[ERROR] SpecEntryTest.inputTypesAreCopiedSoTheCallerCannotMutateThem` (KILLED, reverted).
- [x] 1.2 Copy `LexicalSimilarity` from POC 8 and declare `SpecScorer` as the one-method
      `double score(String query, SpecEntry entry)` seam, with `LexicalScorer` implementing it as
      cosine over word term-frequency vectors over name plus description, and verify a test scores a
      query sharing two words with an entry above one sharing none, and that an unrelated query scores
      zero.
      → `LexicalSimilarity` verbatim (javadoc de-POC'd), `SpecScorer` is `@FunctionalInterface` with
      exactly that one method, `LexicalScorer` scores over `name() + " " + description()`.
      `LexicalScorerTest` 3 passed. Mutation evidence: the first mutant (`text = entry.name()`,
      descriptions dropped) **survived** the original test, which was therefore vacuous — it was
      rewritten to use description-only words (`reconcile totals` vs `weather forecast`), then
      `[ERROR] LexicalScorerTest.aQuerySharingTwoWordsOutranksOneSharingNone:35` (KILLED).
- [x] 1.3 Write `SpecRanker` that scores, sorts descending and takes the top-k, keeping the score
      beside the entry so the caller can render it, and verify a test with three entries and `k = 2`
      returns the two best in order, that `k` above the entry count returns all of them, and that an
      empty entry list returns no results rather than throwing.
      → `SpecRanker.rank` + nested `record ScoredEntry(SpecEntry entry, double score)`, driven by a
      stub scorer so a failure here can only be a ranking failure.
      `SpecRankerTest` 4 passed (`returnsTheTwoBestInDescendingOrder` = three entries, `k = 2`;
      `aKAboveTheEntryCountReturnsEveryEntryOnce`; `anEmptyEntryListRanksToNoResults`;
      `aBlankQueryRanksToNoResultsRatherThanKZeroScoredPacks`). Mutants: dropping the `limit` and
      inverting the sort each killed the named test.
- [x] 1.4 Write the index-entry factory that builds one entry from an `AgentSpec`: name, goal
      description, every step description, then the external input type names and the output type
      name, and verify a test asserts a query worded only from a step description matches that pack,
      and that a pack with blank descriptions still yields an entry carrying its type names.
      → `SpecEntries.of(AgentSpec)`; `externalInputTypes` = every step input type minus every type the
      pack's own steps produce (POC 14's rule).
      `SpecEntriesTest` 4 passed. Mutation evidence: "goal description left out" **survived** the
      first version (the pack was named `brief` and the query contained it), so the goal description
      became `produce a condensed onboarding digest` and the query `condensed onboarding digest` →
      `[ERROR] SpecEntriesTest.theGoalDescriptionIsPartOfTheMatchedText:44` (KILLED).
      `anInternalTypeIsNotAnExternalInput` killed by removing the produced-type subtraction.
- [x] 1.5 Write `RetrievalCuration` over the builder's existing `LifecycleState`: not surfaced when
      retired, de-rank factor `0.5` when degraded, `1.0` otherwise, and a null state treated as
      healthy, and verify a test covers all five cases including null.
      → `RetrievalCuration` (`surfaced`, `deRankFactor`, `curatedScore`, plus `curate(...)` which
      composes filter → de-rank → re-sort by curated score → top-k, so the controller holds none of
      that arithmetic). `RetrievalCurationTest` 7 passed: retired, degraded, active, incubating, null.
      Mutation evidence: `surfaced → true` → `[ERROR] RetrievalCurationTest.aRetiredPackIsNotSurfaced:25`;
      `deRankFactor → 1.0` → `[ERROR] RetrievalCurationTest.aDegradedPackIsDeRankedToHalfItsScore`;
      mis-keying the per-pack state lookup → `[ERROR] RetrievalCurationTest.curatingDropsARetiredTopHitAndLetsADegradedOneSink:65`
      and `curatingTakesOnlyTheTopK:76`.
      **Bug this caught:** the test first passed `STATES::get` as the state function. That binds to
      `Map.get(Object)`, so it compiles against `Function<SpecEntry, LifecycleState>` while looking the
      **entry** up in a map keyed by **String** — always null, i.e. every pack silently looked
      sidecar-less. It only surfaced on a clean rebuild, because the per-method mutants were each
      killed by the five unit tests while the composition stayed broken. Fixed to a lambda keyed on
      `entry.id()` that throws on an unknown id, so a mis-keyed lookup can never again read as "no
      sidecar".
- [x] 1.6 Verify the package compiles and its tests pass with no endpoint configured
      (`make test-module MODULE=prod/embabel-smith-builder`) and that the module's `pom.xml` gained no
      dependency.
      → `make test-module MODULE=prod/embabel-smith-builder` → `Tests run: 212, Failures: 0,
      Errors: 0, Skipped: 0` / `BUILD SUCCESS` (baseline 192 + 20 new retrieval tests;
      `%regex[com.embabel.smith.builder.retrieval.*]` alone → `Tests run: 20, Failures: 0`).
      Phrasing note: this target *does* source `~/.config/env/llm.env` and export
      `OPENAI_CUSTOM_BASE_URL` (that file exists here), so offline-ness is **not** because no endpoint
      was configured — it is because the root pom sets `test.excludedGroups=live` and no test in
      `retrieval/` carries a live tag (`grep -n "Tag\|live"` over the five test files: no hits).
      `git status --porcelain prod/embabel-smith-builder/pom.xml` → empty (untouched, no new
      dependency, so no `poc/` dependency).

## 2. The retrieve endpoint

- [x] 2.1 Add `RetrieveRequest` (`source`, `query`, `k`) and `RetrieveResponse` (`error`, `scorer`,
      `results`) plus `RetrieveResult` (`name`, `description`, `score`, `outputType`,
      `lifecycleState`) to `rest/dto`, and verify `vue-tsc` is unaffected and the records serialize
      with those field names.
      *Amended: this task originally named only `scorer` and `results`, and the task was wrong, not
      the code. `error` is required because 2.4 and the spec demand "the same error the other
      source-backed panes return", and that shape in this repo **is** HTTP 200 + an `error` field on
      the response record — `SecurityResponse`, `TrustResponse` and `ComposeResponse` each expose
      `ok(...)`/`error(...)`, and there is no `@ControllerAdvice` and no `ResponseEntity` in any
      source-backed pane. A two-field record cannot express it, and letting the exception propagate
      yields Spring's 500 whitelabel JSON, which is the opposite of the convention being asked for.
      Recorded to the lead and accepted 2026-11-18.*
      → `npm run typecheck` (`vue-tsc --noEmit`) → exit 0, no output.
      Field names asserted from the serialized JSON in `RetrieveControllerTest` (`error`, `scorer`,
      `results`, `name`, `description`, `score`, `outputType`, `lifecycleState`).
- [x] 2.2 Write `RetrieveController` with `POST /api/retrieve`: resolve the source through
      `SourceService`, read each pack, build its entry, rank all of them, curate, take the top-k capped
      at the declared maximum, and verify by reading the class that it contains no ranking or curation
      arithmetic of its own.
      → `RetrieveController` (`SCORER_NAME = "lexical"`, one `@PostMapping`, delegates to
      `SpecRanker` + `RetrievalCuration`). Read back: the class contains no comparator, no sort, no
      multiply — its only logic is `listPacks` → `readPack` → `SpecEntries.of`, the `states` map, DTO
      mapping, and the `catch (Exception)` error shape. The k cap lives in the DTO
      (`RetrieveRequest.effectiveK()`, `DEFAULT_K = 10`, `MAX_K = 50`).
      An unreadable pack contributes no entry rather than failing the query.
- [x] 2.3 Report the active scorer name on every response, and verify a MockMvc test asserts the
      `scorer` field is present and non-blank on a successful retrieve.
      → `RetrieveControllerTest.namesTheScorerAndRanksTheBestMatchFirst` asserts
      `body.path("scorer").asText()` is not blank; the scorer name is also asserted in
      `aBlankQueryReturnsNoResultsButStillNamesTheScorer` and
      `aSourceWithNoPacksReturnsNoResultsRatherThanAnError`.
      These are the studio's first `MockMvcBuilders.standaloneSetup` tests.
- [x] 2.4 Write `RetrieveControllerTest` on MockMvc with the offline fixtures pattern from
      `RestTestFixtures`, covering: a query that ranks one pack first; a retired pack absent from the
      results; a degraded pack scoring below its own baseline and carrying the state; a blank query
      returning no results; a source with no packs returning no results; and an unparseable source
      returning the same error shape the other source-backed panes return.
      → 7 tests, all six cases covered (the degraded case is `aDegradedPackScoresHalfItsIdenticalPackAndCarriesTheState`:
      two packs with identical steps and token counts, so the only difference is the sidecar — the
      degraded one scores half and is re-ordered below the healthy one).
      `make test-module MODULE=prod/embabel-smith-studio` → `Tests run: 134, Failures: 0, Errors: 0,
      Skipped: 0` / `BUILD SUCCESS` (baseline 127 + 7 new).
      Mutation evidence (all reverted, green re-verified): mis-keying the state lookup in the
      controller → `[ERROR] RetrieveControllerTest.aDegradedPackScoresHalfItsIdenticalPackAndCarriesTheState:90`
      and `[ERROR] RetrieveControllerTest.omitsARetiredPackFromTheResults:66`.
      Two notes: the `lifecycle.yml` sidecar helper lives in this test rather than in
      `RestTestFixtures`, because that file is outside the write scope; and the halving assertion uses
      `isCloseTo(healthyScore * 0.5, within(0.0001))` rather than equality, because scores are
      published rounded to four decimals so `round(r)/2` is not bit-exact with `round(r/2)` — the
      first version failed on exactly that (`expected: 0.13365 but was: 0.1336`).
- [x] 2.5 Verify no test in the new controller test carries an endpoint gate, and that
      `make test-module MODULE=prod/embabel-smith-studio` passes with `OPENAI_CUSTOM_BASE_URL` unset.
      → `grep -n "Tag\|live"` over `RetrieveControllerTest.java` finds only the word "live" inside a
      comment stating that no test here carries a gate; there is no `@Tag` and no model call.
      Ran it literally: `env -u OPENAI_CUSTOM_BASE_URL -u OPENAI_CUSTOM_API_KEY ./mvnw -o test -pl
      prod/embabel-smith-studio -am` → `Tests run: 134, Failures: 0, Errors: 0, Skipped: 0` /
      `BUILD SUCCESS`. **The variable was explicitly unset for that run** (the Makefile target itself
      would have exported it from `~/.config/env/llm.env`).

## 3. The retrieve pane

- [x] 3.1 Mirror the three new DTOs in `frontend/src/api/types.ts` and add one `retrieve()` function
      to `frontend/src/api/client.ts`, the single fetch site, and verify `npm run typecheck`
      (`vue-tsc --noEmit`) passes, which is what proves the mirror matches the backend.
      → `RetrieveRequest` / `RetrieveResult` / `RetrieveResponse` interfaces added; one
      `export const retrieve = (req) => post<RetrieveResponse>('/api/retrieve', req)` in the single
      fetch site (no `withModelTimer` wrapper — retrieval makes no model call).
      `npm run typecheck` → exit 0, no output.
- [x] 3.2 Replace the `PocOnlyPane` wrapper in `RetrievePane.vue` with a source picker fed by
      `/api/sources`, a query field, a submit action and a sortable results table with name, score,
      output type and lifecycle state, keeping `PageHeader` and `SectionCard` for the chrome.
      → `PocOnlyPane` import removed; source select via the shared `useSourceSpec()` composable
      (`/api/sources`), task field, Search button, `q-table` with all four columns `sortable: true`
      and an initial `sortBy: 'score'` descending. `PageHeader` + `SectionCard` kept; the lifecycle
      badge reuses the existing `lifecycleColor()` from `improve/model.ts` rather than a second
      mapping. `router.ts` already routed `/retrieve` to `RetrievePane`, so it needed no change, and
      `/remote` and `/scale` still render `PocOnlyPane`.
- [x] 3.3 Show the active scorer in the pane, the empty-result state, and the source-resolution error
      as a banner rather than a thrown request, and verify a component test asserts the scorer label
      renders and that an empty result set renders the empty state rather than a spinner left
      running.
      → `data-test="retrieve-scorer"` ("Scorer: lexical · N match(es)"), `retrieve-none` for an empty
      result set, `retrieve-error` banner fed from `res.error` (the thrown-request path is the
      `catch`, which also lands in the banner).
      Tests: `ranks the curated hits, naming the scorer and each pack lifecycle state` asserts the
      scorer label renders; `shows the no-match state when the source has nothing to rank` asserts the
      no-match block renders, the table does not, and `.q-spinner` is absent.
      *This clause is only under-satisfied, not satisfied: `RetrievePane` has no loading spinner at
      all — the search is a single awaited `POST` with no pending state — so "rather than a spinner
      left running" cannot fail here, and the `.q-spinner` assertion proves the absence rather than
      proving a spinner was stopped. The pane deliberately does not add a spinner the other
      source-backed panes do not have; if a loading state is ever wanted, this assertion is the one to
      replace. Flagged to the lead, who judged the clause under-specified rather than the work
      incomplete (2026-11-18).*
      Mutants (reverted, green re-verified): badge no longer rendering the state →
      `× RetrievePane > ranks the curated hits, naming the scorer and each pack lifecycle state`;
      `if (res.error)` never taken → `× RetrievePane > shows a source-resolution failure as a banner`.
- [x] 3.4 Add the pane to the existing component test file alongside the other panes, mocking `fetch`
      once, and verify it asserts a retired pack is absent and a degraded pack is shown with its
      state.
      → 4 tests in the existing `src/__tests__/panes.component.test.ts`, reusing its single
      `vi.mock('../api/client')` seam (`retrieve: vi.fn()` added to it, no second mock).
      `does not surface a retired pack the endpoint dropped` seeds the *source's own pack list* with a
      `RETIRED` pack, so the assertion is not vacuous — if the pane ever rendered the pack list
      instead of the retrieval hits, the retired pack would appear; `expect(wrapper.text()).not.toContain('retired-pack')`
      holds. `omitsARetiredPackFromTheResults` covers the same rule server-side.
      Mutant killed: table rows taken from `specs` instead of the retrieval hits →
      `× RetrievePane > ranks the curated hits…` and `× RetrievePane > does not surface a retired pack…`,
      with the third RetrievePane test correctly staying green (it does not read that line).
- [x] 3.5 Verify the frontend gates run green: the type gate, the Vitest logic tests and the component
      tests, with no backend running.
      → `npm run typecheck` → exit 0. `npm test` (`vitest run`) →
      `Test Files  6 passed (6)` / `Tests  67 passed (67)`, `panes.component.test.ts (11 tests)`;
      Vitest runs against the mocked api module, so no backend is involved.

## 4. Decisions recorded

- [x] 4.1 Update `docs/09-studio.md`: move `/retrieve` out of the "none — POC-only stubs" row of the
      Surfaces table, leave `/remote` and `/scale` in it, and state beside the table why the scale
      pane stays POC-only, and verify the page's route table matches `frontend/src/router.ts` route
      for route.
      → `/retrieve` now has its own row (`RetrieveController`, `SourceController`); the stub row is
      `/remote`, `/scale`. Added the rationale paragraph (the scale pane presents POC 12/23's
      Precision@1-over-a-large-library harness, cited to `04-poc-run-results.md` §7.1, rather than a
      capability a user can invoke). Two things the move invalidated and were fixed in the same page:
      "`SourceController` … backs **four** of the panes" → five, and the closing line "What the
      product dropped is the retrieve, remote and scale panes … curation-aware retrieval survive only
      in the POC", which the change makes false.
      Verified route-for-route: `comm -3` over the paths in `router.ts` and the paths in the docs
      table → no output, `exit=0`.
- [x] 4.2 Amend the POC-only claim at the top of `docs/11-spec-selection-pocs.md` so it names only
      `/scale`, and verify the sentence no longer claims `/retrieve` is a stub.
      → the sentence now names `/scale` as the only stub and records the exception explicitly: the
      mechanism those POCs measured (POC 8's entry shape, POC 12's lexical scorer, POC 18's curation
      rule) was **copied** into the builder and is wired behind `/retrieve` — copied, not depended on,
      since `poc/` is frozen evidence. The three selector POCs the page is about (17, 19, 23) are
      named as still unwired, which is true. `grep -n "retrieve" docs/11-spec-selection-pocs.md`
      returns only that new sentence.
- [x] 4.3 Close the "Retrieval at scale" bullet in `docs/12-backlog.md`, recording the scorer, the
      curation rules, the endpoint and the `/scale` decision, and verify the remaining open bullets
      in that section still describe only what is genuinely unwired.
      → bullet is now `**Closed — retrieval at scale.**` naming `POST /api/retrieve`, the one-method
      `SpecScorer` seam with `LexicalScorer`, the two curation rules, the builder/studio split, and
      what stays open (only the scale benchmark behind `/scale`).
      The section's `Verified 2026-11-15 … (no retrieve/scale/remote controller)` line was made false
      by this change, so it now reads `no scale/remote controller` plus a 2026-11-19 re-verification
      for retrieval. Remaining open bullets in that section verified still accurate:
      `grep -n "PocOnlyPane" docs/12-backlog.md` → `/scale` and `/remote` only.
- [x] 4.4 Re-ingest `.wiki/`, run the wiki lint and republish, and verify the wiki lint reports no
      broken cross-links after the pages above changed.
      → **Re-ingested and linted 2026-11-22; the republish is still owed.** The precondition held once
      `docs/09`, `docs/11` and `docs/12` were committed and clean at `HEAD` (`620a8f2`), so the lint read
      the changed pages rather than the pre-change wiki. Lint counts over `.wiki/**/*.md`: 45 pages,
      **0 broken cross-links**, 0 orphan pages, 0 pages at `status: "stale"`, 0 `source_paths` entries
      that do not exist, 0 pages missing a frontmatter key. [[smith-studio-web]] gained the retrieval
      section for what no controller shows: POC 18's two curation rules (`retrieval/RetrievalCuration.java`
      drops a `retired` pack, halves a `degraded` one, then re-orders), the one-method `SpecScorer` seam
      with `LexicalScorer` the only implementation that ships, and that the scoring, ranking and curation
      arithmetic is the builder's `retrieval/` package with none of it in the controller.
      **One doc claim the code does not support was corrected in the wiki rather than copied:**
      `docs/12-backlog.md` states "every response names the active scorer", but `dto/RetrieveResponse.java`
      documents that field as null on failure and the controller sets it only on the success path — so the
      page says every *successful* response names it.
      **Republish not done:** publishing to GitHub Pages needs a push, and this repo does not push, so
      that half of the task stays open for the human.

> The stale "no mention anywhere in `docs/`" assertion in that file's Skill-evaluator bullet is
> corrected by the `skillevaluator-dormant` change, which owns that bullet. Not this change's work.

## 5. Checks

- [x] 5.1 Verify `make lint` is green, remembering that the reactor stops at the first failing module
      so a style violation in the builder hides everything behind it.
      → `make lint` → every module `SUCCESS` through `embabel-smith-studio`, `BUILD SUCCESS`
      (`Total time: 1.396 s`). Note `includeTestSourceDirectory=false`, so the new test sources are
      not linted — the main sources are, and they pass.
- [x] 5.2 Verify `make build` is green offline, which is the check that the extraction added no
      dependency on `poc/` — the enforcer ban would fail the build if it had.
      → `make build` (`./mvnw -q compile -DskipTests`) → **exit 0** (its only stderr output is npm's
      `install-scripts` allowlist warning and Vite's chunk-size note, both pre-existing). The ban is
      the `ban-poc-dependencies` execution, and it runs on every module:
      `Rule 0: org.apache.maven.enforcer.rules.dependency.BannedDependencies passed` for
      `embabel-smith-prod`, `specsource`, `skillevaluator`, `runtime`, **`builder`** and **`studio`**.
      `poc/` sits behind the off-by-default `poc` profile and nothing in `prod/` references it.
- [x] 5.3 Run one retrieve against a real source by hand with no endpoint configured and read the
      response, and verify the named scorer, the ordering and each hit's lifecycle state are what the
      source's `lifecycle.yml` files say they should be.
      → Booted the studio with `SMITH_STUDIO_SOURCES=folder:<source>` on a spare port, with
      `OPENAI_CUSTOM_BASE_URL`, `OPENAI_CUSTOM_API_KEY`, `OPENAI_API_KEY` and `ANTHROPIC_API_KEY`
      explicitly unset. `/api/status` → `{"configured":false,…,"error":"No resolvable model endpoint is
      configured. Missing: OPENAI_CUSTOM_BASE_URL, OPENAI_CUSTOM_API_KEY…"}` — so the endpoint really
      was unconfigured while the retrieve worked.
      Source: the real published pack `examples/out/TradingAgents/agent-spec-single-shot/published/single_instrument_analysis`
      (sidecar `state: "INCUBATING"`), plus two byte-identical copies carrying `DEGRADED` and `RETIRED`
      sidecars, so the three packs differ only in lifecycle state.
      `POST /api/retrieve` with `"final investment rating and investment thesis"` →
      `"scorer":"lexical"`; `single_instrument_analysis` score `0.415` `lifecycleState:"INCUBATING"`;
      `degraded_twin` score `0.2106` `lifecycleState:"DEGRADED"`; `retired_twin` **absent**.
      Every value matches its `lifecycle.yml`: the degraded twin's raw score was `0.4212` — *above* the
      healthy pack's `0.415` — and halving it dropped it to second, which is the re-order the design
      calls for, observed on real data. `outputType` is `FinalDecision`, the exported goal's type, and
      the matched text is name + goal description + all twelve step descriptions + `UserInput` +
      `FinalDecision`, exactly as 1.4 specifies.
      The hand-run also confirmed the one thing the fixtures could not: `PackInfo.name()` is
      `AgentSpec.name()` (`SourceService:81`), the same value the state lookup keys on, so the two keys
      agree by construction rather than by luck — a folder source whose directory names differed from
      its pack names would otherwise have read as `UNKNOWN` everywhere.
