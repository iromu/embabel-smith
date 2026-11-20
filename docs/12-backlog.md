# Backlog

Everything the project knows it has not done, with the reason each item is open. Consolidated from
the planning records that preceded these pages; each item was re-checked against the current tree,
and the verification is named so a future reader can re-run it.

## Builder — skill target

Four gaps found in review on 2026-11-14 and deliberately left out of the change that found them: the
change's completion criterion was its own spec, and each gap lies outside what that spec requires.
Each needed requirement wording before code. **All four are closed**, the last three verified
2026-11-18.

Closed — **the published `evals.json` was never security-scanned** (was item 1). The static scan now
runs over both documents the build publishes from model output — `SkillBuilderService` calls
`scanText(skillName, List.of(skill.toSkillMd(), evals.toJson()))` — so a marker sitting in an eval
expectation blocks the build at the `security` stage and writes nothing. The `lifecycle.yml` sidecar
stays out of it: the builder generated that from a content hash and a pass rate, so scanning it would
add noise, not coverage. `skill/SkillBuilderServiceTest`'s
`aMarkerInAnEvalExpectationBlocksAtSecurityAndPublishesNothing` proves the suite is read, and
`security/AgentSpecSecurityScannerTest`'s
`aMarkerRaisedInAnyOfSeveralDocumentsIsReportedAgainstTheArtifact` proves a finding raised in any one
document is reported against the artifact. Check: `grep -n 'scanText(skillName, List.of'
prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillBuilderService.java`.

Closed — **a scenario with zero expectations was run and scored 0/0** (was item 2). Such a suite is
now refused at the `eval` stage, naming the offending scenario ids, *before* either arm is paid for —
two model calls instead of four more per empty scenario. It is refused rather than dropped, because
dropping scenarios is exactly what makes a build gate on a subset nobody chose.
`skill/SkillBuilderServiceTest`'s `aScenarioWithNoExpectationsBlocksAtEvalAndBuysNoArmCalls` asserts
the stage, the message and that neither arm nor the judge was called. Check: `grep -n 'no expectations'
prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillBuilderService.java`.

Closed for the CLI — **there was no `--min-lift` flag** (was item 3). `BuildCommand` now takes it,
defaulting to `BuildOptions.DEFAULT_MIN_LIFT`, and accepts it on an agent-spec build without letting
it decide anything there, exactly as `--max-refine` already behaves. `cli/BuildCommandTest`'s
`theMinimumLiftFlagReachesTheSkillGate`,
`theMinimumLiftFlagIsAcceptedOnAnAgentSpecBuildWithoutDecidingIt` and
`anUnparseableMinimumLiftIsAUsageError` cover the three outcomes. **The studio half of that item was
not implemented, and is not a gap left open:** `rest/BuildController` pins `BuildTarget.AGENT_SPEC`
and passes `DEFAULT_MIN_LIFT` literally, and the lift gate belongs to skill builds alone, so no studio
request could reach a lift knob. The studio clause was a symptom of that target pinning rather than a
separate missing feature; [`08-builder.md`](08-builder.md) §`skill` records the knob as CLI-only and
states the day that stops being true. Check: `grep -n 'min-lift'
prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/cli/BuildCommand.java` and
`grep -n 'BuildTarget.AGENT_SPEC'
prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/rest/BuildController.java`.

Closed — **`GitPublisher` returned a path inside the temporary work tree that `close()` then
deleted** (was item 4), so a `git:` publish reported a `published:` location that no longer existed.
`PublishResult` now carries one location stated in the source's own terms: a `folder:` publish reports
the directory it wrote, a `git:` publish reports `git:<repo>#<unit>`, and the working tree is
surfaced nowhere — both build reports and both studio responses print the same string. A skill over
`git:` is no longer untested: `publish/PublisherTest`'s
`aSkillPublishedToGitLandsInACommitThatOutlivesThePublisher` re-clones a bare repository in a temp
directory, asserts the commit carries `SKILL.md`, `evals.json` and `lifecycle.yml`, and reads the
reported location *after* `close()` has run; `aSkillCollidesInAGitSourceAndOverwriteReplacesTheDirectory`
applies the collision and `--overwrite` rules to a skill on the same target. The rule is recorded in
[`06-spec-sources.md`](06-spec-sources.md) §Publishing. Check: `grep -n 'Reported location'
docs/06-spec-sources.md` and `grep -n 'aSkillPublishedToGitLandsInACommit' prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/publish/PublisherTest.java`.

## Builder — generation

- **The `embabel` technique cannot publish a multi-step pack** — an upstream compiler limitation, not
  a builder bug; see [`08-builder.md`](08-builder.md). Synthesizing a `DynamicTypeDef` per undeclared
  output would make the build publish, but `types/` would then hold bare names with no property
  definitions: a weaker pack, not a correct one. Deferred rather than done silently.
- **Single-shot multi-agent reliability is unbounded.** POC 24 found it "sometimes valid". Cycles
  arising when parallel branches merge remain the honest failure mode that the validator and bounded
  reorder repair only *detect and block*.
- **Skill editing at runtime, a skill improvement loop, and a process-level improvement loop** are
  each out of scope for their target, not overlooked.

## Product gaps proven in POCs

Retrieval and remote invocation were the two POC-proven capabilities this product had not wired.
Both are wired now, and the decision about launching sidecars is closed. One gap remains open, and it
is listed first because it is the only one still owed work.

- **Open — the scale benchmark.** `/scale` still renders `PocOnlyPane.vue`. It presents POC 12 and
  POC 23's Precision@1-over-a-large-library harness, which is a measurement rather than a capability
  a user can invoke, so wiring it would mean inventing a feature to hang under the name. It stays a
  stub until someone decides what the pane should actually do. See
  [`11-spec-selection-pocs.md`](11-spec-selection-pocs.md).
- **Closed — retrieval at scale.** `/retrieve` is wired. `POST /api/retrieve` ranks a source's packs
  and curates the ranking with each pack's `lifecycle.yml` state — retired dropped, degraded halved —
  the two rules POC 18 measured. The scorer is the one-method `SpecScorer` seam with `LexicalScorer`
  behind it, and every response names the active scorer, so a denser scorer is a bean change rather
  than a contract change. Scoring, ranking and curation all live in the builder's `retrieval/`
  package; the controller holds none of that arithmetic. See [`09-studio.md`](09-studio.md).
- **Closed — remote invocation.** `/remote` is wired. `GET /api/remote/bridges` probes each
  configured sidecar and `POST /api/remote/execute` runs one of its advertised actions; the pane
  shows reachability, the advertised actions with their declared input types, and the output the
  sidecar's own process produced. All of the wire protocol is in the runtime's `remote/` package —
  `RemoteController` holds no HTTP, which a guard test enforces. What POC 16 proved and the product
  still does **not** do is plan over a remote action: the pack format has no step that can name one,
  `embabel-agent-remote` is not a `prod/` dependency, and nothing registers remote actions into the
  platform the planner searches. See [`07-runtime.md`](07-runtime.md) for the three-endpoint client
  and what planning would need.
- **Closed — spawning remote sidecars from the UI.** Decided **against**: sidecars are started by
  hand and the product never spawns a process. This product binds a port and is reachable from a
  browser, and a browser-reachable tool that launches processes on its host is a
  remote-code-execution surface; it would also make Docker a dependency of a library that does not
  otherwise need it. The reasoning that made it look attractive — that the gating logic is identical
  either way, so launching is free convenience — is exactly the reasoning the risk overturns: the
  convenience is free only because the *reporting* is the same, and reporting is what stays. An
  absent sidecar is therefore reported, with the URL that was probed, and the pane carries no control
  that would start one.

Verified 2026-11-19 by listing `prod/embabel-smith-studio/src/main/java/.../rest/` (a
`RemoteController` now sits there; no scale controller does) and reading the two pane components:
`RemotePane.vue` is a real pane, `ScalePane.vue` still wraps `PocOnlyPane.vue`. The retrieval bullet
above was re-verified the same way: `RetrieveController` sits in that package and `RetrievePane.vue`
no longer wraps `PocOnlyPane.vue`.

## Runtime and storage

- **Multi-process trace writing.** v1 is single-process per host with no locking beyond
  append-and-sync.
- **Closed — the runtime did not gate on lifecycle state.** `RuntimeCore.ingest` now excludes a pack
  whose `lifecycle.yml` sidecar declares `retired`, and reports the exclusion by name, so a retired
  pack's actions are not plannable even through another pack's goal. A run asking for a retired pack's
  goal gets `LifecycleBlockedException` (`Run blocked by lifecycle state…`, naming the pack) rather than
  a goal-not-found error, because the loop reads failure *prefixes* and must not treat a retirement as a
  spec defect; the block is traced like any other failure, with no steps. `INCUBATING` and `DEGRADED`
  were deliberately left advisory — `DEGRADED` means "the spec still functions" — as was the fallback
  that a pack with no sidecar, or an unreadable one, has *no recorded state* and keeps running, which is
  what keeps pre-stamping packs and the frozen POC sources working. There is no `--allow-retired`: the
  revival path is the documented rebuild. Check: `grep -rn 'LifecycleSidecar.retired'
  prod/embabel-smith-runtime/src/main/java/` returns the one ingest call site, and `grep -n
  'LifecycleBlockedException' prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/core/RuntimeCore.java`
  finds the throw.
- **The approval queue and loop watermarks are in-memory.** A restart loses them; the on-disk traces
  let the loop rebuild. File-backed persistence is a future change if the UI needs it.
- **Further trace backends** — `OtelTraceStore`, BigQuery, MongoDB — were named out of scope. The
  record-shaped `TraceStore` SPI in the runtime module exists so one drops in without reworking the
  JPA implementation.
- **No automatic migration of existing `~/.smith` file data into Postgres.** The filesystem *import*
  endpoint covers the need deliberately.
- **Accepted security risk, documented not fixed:** the storage settings document holds the database
  password in plaintext at `~/.smith/studio/storage.json` (mode `0600`, local development tool, no
  network exposure).
- **Accepted race:** rebinding storage destroys the old entity-manager factory after the swap, so a
  request mid-transaction on the old binding can fail. Tolerated as a short-transaction dev tool; it
  surfaces as a clear error and is noted in the pane.

## Format and sources

- **Deeper-than-one-level pack nesting in a source** is "a future concern, not a v1 feature".

## Spec selection

- **A coarse two-stage family router** over the library was deferred as a possible future POC; the
  POC 17 design rejected it for the shadowing question specifically.
- **Whole-library behaviour reporting** (the full ~281-spec library) was explicitly deferred by POC
  17's open questions.
- **GPU serving, FP8, and multi-model serving** for the small-LLM selector are out of scope, and the
  FP8 checkpoint is excluded by this host's hardware — see the host-specific caveat in
  [`11-spec-selection-pocs.md`](11-spec-selection-pocs.md).

## Loop

- **Closed — the full builder→runtime loop.** This was listed as the single unchecked task in the
  archived `embabel-agent-builder` set (7.1, *needs a live endpoint*). It is done: `run-loop.sh` runs
  it and the results document records it **GREEN** — `docs/04-poc-run-results.md` §"Full loop (task
  7.1)", building a pack, ingesting it, running it and achieving the goal. The unticked box was a
  bookkeeping artefact of the deleted planning tree. Check: `grep -n 'Full loop (task 7.1)'
  docs/04-poc-run-results.md`.
- **Closed — the loop published with `push = true`, against the product's commit-never-push rule.**
  The invariant won. `smith.studio.loop.push` (default `false`, not runtime-toggleable) decides, and
  both call sites — `ImprovementLoop.publishCandidate` and `LoopService.publish` — read it through one
  `LoopPublishPolicy`. A `git:` improve source with pushing off is *refused* rather than published,
  because `GitPublisher` commits into a temporary clone that `close()` deletes: publishing anyway would
  have discarded an approved improvement silently, which is worse than the deviation it replaces. The
  candidate stays pending, the refusal names the setting, and an auto-publishing iteration reports
  `PUSH_DISABLED` instead of reporting a publish. A `folder:` source is unchanged. Check:
  `grep -rn 'proposedLifecycle(), true, true'
  prod/embabel-smith-studio/src/main/java/` returns nothing.
- **Closed — the loop no longer depends on the folder for durability.** The forced `overwrite = true`
  is still there, deliberately: `FolderPublisher` names the destination directory from
  `Publishable.name()` and `Publishable` exposes no version, so clearing the flag would make the loop's
  second publish throw `CollisionException` — unchecked, and caught nowhere on the loop's path —
  instead of improving. What changed is where the previous version survives. A publish that succeeds
  now also records the pack through `ProcessStore.savePackWhenConfigured`, and `packs` is keyed by
  `(name, version)`: `improve/Refiner` builds the candidate one patch above the pack it improves, an
  artifact declaring no version is stored at `PackEntity.DEFAULT_VERSION`, a bare name resolves to the
  highest version, and `storage/PacksKeyMigration` re-keys an existing name-keyed table when the
  database is bound, because `hbm2ddl.auto = update` adds columns but never re-keys a primary key. `ProcessStoreTest`'s
  `twoVersionsOfOneNameAreStoredAsTwoRows` and `highestVersionComparesNumericallyNotLexically`,
  `RefinerTest`'s `candidateIsOnePatchAboveThePackItImproves` and
  `candidateOfAPackDeclaringNoVersionStartsAboveTheStoredDefault`, and `ImprovementLoopTest`'s
  `aLoopPublishLeavesARowAtTheCandidateVersion` pin it; the builder gained no persistence dependency,
  and `builder/eval/RefinementLoop` still returns the version it was given
  (`RefinementLoopTest.aRefinedPackKeepsTheVersionItWasGiven`) because a `--max-refine` build does not
  publish. The rule is stated once, in [`06-spec-sources.md`](06-spec-sources.md) §Publishing. Check:
  `grep -rn 'savePackWhenConfigured(candidate.candidate())'
  prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/improve/LoopPublishPolicy.java`
  returns the post-publish save. `grep -rn 'boolean overwrite = true'` on that same file still returns
  a line: the flag's survival is the ruling, not the gap.

## Skill evaluator

- **Closed — it is wired to the builder's `skill` target.** It is a full reactor module
  (`prod/pom.xml`) — the Tier 1 static validator, `Tier1Runner` plus schema, license, hygiene,
  version, quality-score, script-lint, Python-syntax, code-risk and security validators — with no
  model call. `builder-skill-inspection-gate` gave it its consumer: `skill/SkillInspector` in the
  builder is the `inspect` gate described in [`08-builder.md`](08-builder.md), the builder declares the
  dependency, and the root POM's `dependencyManagement` names it. The gate requests the seven checks
  the evaluator runs in-JVM — six block the build, `pii` reports as an advisory — and does not request
  the three that need an external scanner. It is integrated into the skill build gate rather than
  exposed as a CLI or left unused: the deliberate-dormancy ruling `skillevaluator-dormant` recorded was
  reversed by the human and that change is superseded, and a CLI entry point remains unimplemented. It
  is named in `README.md` and in `docs/05-architecture.md`; it has no `.wiki/` page of its own yet.
  Check: `grep -rn skillevaluator prod/*/pom.xml pom.xml docs/`.
- **Open — an enabled scanner can print an install hint naming a third-party release URL.**
  `spi/SkillspectorSecurityScanner.java:96` holds `Install with: uv tool install
  https://github.com/NVIDIA/SkillSpector/releases/download/…whl`; the other binary-backed scanners
  carry their own (`SecretsGitleaksCli.java:75` a `go install`, `CodeRiskValidator.java:881,885` the
  Bandit and Semgrep routes). On the default skill-build path every one of those strings is inert: the
  builder's lineup (`skill/SkillInspector.java:46`) never requests `security`, `secrets` or
  `code-integrity`, so the validator that would print a hint is never constructed. `--full-inspection`
  is the single route that can surface one, and it is an explicit human action. Whether the product
  should ever tell a user to fetch and run a third-party binary is a supply-chain decision for the
  human, not for a change. Check: `grep -n 'uv tool install'
  prod/embabel-smith-skillevaluator/src/main/java/org/iromu/embabel/smith/skillevaluator/spi/SkillspectorSecurityScanner.java`
  and `grep -n 'BINARY_FREE_CHECKS = '
  prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillInspector.java`.
- **Closed — its provenance is now attributed.** The module is a port of a Python `tier1` validator and
  8 of its source files carry `SPDX-FileCopyrightText: Copyright (c) 2025/2026 NVIDIA CORPORATION &
  AFFILIATES` (50 carry the project author). The root `NOTICE` now names NVIDIA SkillEvaluator as the
  upstream, states Apache-2.0, and lists those eight files by path and by the year each header actually
  carries — six configuration YAMLs at 2026, two Python test fixtures at 2025. No Java file carries an
  NVIDIA header; the ten Java files that mention NVIDIA do so incidentally — an example author name, a
  reference in a doc comment — and carry the project author's header. `README.md` names the module and
  links the notice, and the module's own `NOTICE` carries the same statement. What was open here was the
  attribution, not the copy: the eight SPDX lines stay exactly as
  upstream wrote them, as Apache-2.0 §4 requires. Check: `grep -rho 'SPDX-FileCopyrightText:.*'
  prod/embabel-smith-skillevaluator/src | sort | uniq -c`.

## Test coverage

- **Closed — nothing recorded that the default test run leaves unproven.** The four endpoint-gated
  integration tests (`LiveBuildIntegrationTest`, `LiveRunIntegrationTest`, `LiveLoopIntegrationTest`,
  `RestIntegrationTest`) are now tagged `@Tag("live")` *as well as* env-gated, and the group is
  excluded by default through `test.excludedGroups`, with `make test-live` the single opt-in — that
  machinery shipped on 2026-11-17. What was missing was the consequence, and it is now stated in
  [`05-architecture.md`](05-architecture.md) §"The live group is opt-in": the four paths these tests
  alone exercise — a build publishing a pack the model really produced, a successful run, the studio's
  REST surfaces with model-backed cores, one full loop iteration — and that a green default run
  evidences none of them. That page names its list by a grep so it cannot rot silently; the builder's
  example suites it used to list were removed on 2026-11-17 and are not part of `make test-live`.
  The affordable next live test — a small blueprint with a known expected spec and skill — is named in
  [`08-builder.md`](08-builder.md). **What changed since is who runs the offline suite, not what it
  proves:** `.github/workflows/offline-verification.yml` now runs `make build`, `make test` and
  `make lint` on every pull request and every push to the default branch, with no endpoint and no
  secrets, on the default reactor. So a green CI badge is the same evidence as a green local `make test`
  and nothing more — the four paths above are still outside it, as is the `poc/` reactor. Read it as
  "nobody had to remember to run it", never as "the publish, run and loop paths were verified". Check:
  `grep -rn '@Tag("live")' prod/*/src/test` returns exactly those four files, `grep -n 'does not prove'
  docs/05-architecture.md` finds the statement, and `grep -n 'make test'
  .github/workflows/offline-verification.yml` finds the target that runs them.

## Re-creating a planning change

The planning trees were consolidated into these pages and removed, but the workflow was kept:
`openspec/config.yaml`, `openspec/specs/.gitkeep` and `openspec/changes/archive/.gitkeep` remain, and
`make setup` still installs the OpenSpec CLI. Starting a new change is therefore still valid; the
capability specs it would edit are now these documents.

One piece of tooling knowledge went with the trees and is recorded here because nothing else keeps
it: capability *renames* cannot be expressed as deltas — a delta operates on requirement names and
must not move or rename a capability — so the 2026-11-11 vocabulary rename was applied as direct
edits, with renames ordered before archiving so archiving could not create incomplete capability
files.


## Misc

Closed — **a2a operations on the runtime module** (was: Send Message, Send Streaming Message, Get
Task, List Tasks, Cancel Task, Get Agent Card, on the spec
https://a2a-protocol.org/latest/specification/#11-key-goals-of-a2a). They are served by
`prod/embabel-smith-runtime-a2a` — `make run-a2a`, `POST /a2a`, card at
`GET /a2a/.well-known/agent.json` — over protocol 0.3, as **five** operations: `message/send`,
`message/stream`, `tasks/get`, `tasks/cancel`, plus the card document. **Listing tasks is not among
them and is not owed.** Protocol 0.3 has no `tasks/list`, so the reused facade rejects the method
before any handler runs and a client that asks gets HTTP 500; serving it would have meant a route
outside that library's dispatch, which is the second protocol surface the reuse ruling exists to
avoid. Each deviation, with the test that pins it, is in the module's own README; the surface is
documented in [`07-runtime.md`](07-runtime.md).

The note's *"reuse embabel and Spring AI libs"* cannot be honoured as written: **there is no Spring AI
A2A artifact on Maven Central** — five probes of the plausible names (`spring-ai-a2a`,
`spring-ai-starter-a2a`, `spring-ai-starter-a2a-server`, `spring-ai-autoconfigure-a2a-server`,
`spring-ai-a2a-server`) all returned 404 and the local `spring-ai-bom` carries no `a2a` entry. What
does resolve, and is what the module declares, is `com.embabel.agent:embabel-agent-a2a:1.5.4` —
reached as `embabel-agent-starter-a2a:1.5.4` through `embabel-agent-a2a-autoconfigure:1.5.4` — plus
`io.github.a2asdk:a2a-java-sdk-spec`, which resolves at **0.3.2.Final** here through
`embabel-agent-api:1.5.4`. The 0.3.3.Final that `embabel-build-dependencies:2.0.3` pins never enters
this tree, and the two are distinct binaries, so a root pin to 0.3.3 would be an untested upgrade, not
a no-op.

- **Open — A2A protocol 1.0.** The reused library speaks 0.3: the served card reports
  `protocolVersion: 0.3.0`, the dispatch keys off the 0.3 method names, and discovery is at
  `/a2a/.well-known/agent.json` rather than the current specification's
  `/.well-known/agent-card.json` — so a client expecting 1.0 will not find this endpoint. Getting to
  1.0 means waiting for `io.github.a2asdk` 1.0.0 to leave Alpha or hand-writing the binding, which is
  the option the reuse ruling rejected. Check:
  `grep -rn 'embabel-agent-starter-a2a' prod/*/pom.xml` returns the one module, and
  `grep -n 'Non-goal: protocol 1.0' prod/embabel-smith-runtime-a2a/README.md` finds the open half.
