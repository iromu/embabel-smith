# Backlog

Everything the project knows it has not done, with the reason each item is open. Consolidated from
the planning records that preceded these pages; each item was re-checked against the current tree,
and the verification is named so a future reader can re-run it.

## Pending right now

Re-counted against the tree on 2026-11-22, so the list below is the whole of it rather than a sample.
`BLOCKERS.md` holds the four things that need a human decision or an upstream release instead of code.

The OpenSpec tree itself carries **five unchecked boxes**, and two of them are not work:
`publish-keeps-versions` 3.2 is withdrawn by its own design decision 7 (keep the `--overwrite` flag) and
`builder-live-example-tests` 3.1 is struck through. The other three are documentation: two edits to
`docs/08-builder.md` — `builder-executes-its-own-packs` 5.2, whose own note says there is no new default to
write, and `poc27-improves-builder-agentspecs` 9.2's third clause, which adds the builder packs to that
page — and the wiki note with its Pages re-publish in 5.3. The first two were briefly blocked by another
session's uncommitted native-image section of that file; that landed as `a4200e2`, so all three are
ordinary documentation work rather than waits.

The open items in the rest of this page were each re-verified today and still hold:

- **Nothing checks that a declared `toolGroups` entry resolves** — `grep -rn toolGroups
  smith-common/src/main/java/com/embabel/smith/common/validation/` prints nothing, so a misspelled
  group still deploys clean. **And nothing dedupes a repeated `validatePack` call**:
  `tools/AgentSpecValidationTool` keeps no state between calls (its only `Set` is the
  normalization-diff at `:131`), so a model that re-sends an unchanged draft pays for it twice.
- **The reply-envelope sentence gates one pack, not four.**
  `grep -rc '"json"' prod/embabel-smith-builder/builder-agent-specs/*/steps/*.yml | grep -v ':0$'` prints
  `builder-spec-one-shot/steps/draftAgentSpec.yml` alone, while
  `grep -c outputTypeName prod/embabel-smith-builder/builder-agent-specs/*/goal.yml` prints four — so the
  other three packs still show the document as the reply.
- **`/scale` is still a stub** — `grep -rln PocOnlyPane prod/embabel-smith-studio/frontend/src` prints
  `panes/ScalePane.vue` only, and that is a product decision rather than an omission.
- **The install-hint surface is gated, not gone** — `SkillInspector` requests the binary-free checks
  unless a caller passes `fullInspection`, so the third-party install strings are reachable only through
  `--full-inspection`, which is a human action.
- **A2A protocol 1.0 waits on `io.github.a2asdk`** leaving Alpha; `prod/embabel-smith-runtime-a2a` is the
  only module declaring the starter, and its README states the non-goal against the discovery-path
  deviation.

Two stale claims surfaced by the re-count, both cheap to fix and neither yet fixed:

- `docs/index.md` §Where knowledge lives and `AGENTS.md` §Documentation status both say the wiki
  cross-links 44 pages. `find .wiki -name '*.md' | wc -l` prints **45** — the ingest of 2026-11-22 added a
  page and the two counts did not move.
- `docker/docker-compose-infra.yml`'s header comment offers
  `jdbc:postgresql://localhost:5433/smith_studio`, while its `environment` block creates `embabel_smith`
  for database, user and password. The documented URL names a database the dev container does not have.

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
  behind it, and every successful response names the active scorer — an error response carries none,
  because `RetrieveResponse.error` leaves that field null — so a denser scorer is a bean change rather
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

- **Accepted latent defect, unfixed by inspection:** `runtime/core/RuntimeCore.java:278` and
  `runtime/trace/TraceRecorder.java:185` read a goal's last output with `String.valueOf(last)`, and
  `TraceRecorder.java:171` renders a step's output the same way — the same binding the builder just
  stopped doing, since a value-type goal output arrives from
  `PromptedActionSpecAction.callLlmWithDynamicType` as a raw property `Map`, so a pack whose goal output type
  wraps one string would render as `{json=…}` rather than the text inside it. Left unfixed on evidence, not
  indifference. The runtime deploys packs the same way (`RuntimeCore.java:141-147` over `combinedTypes`,
  `:194-208`), but no pack reachable by a shipped path has that shape: single-own-property types are six of
  the nineteen `types/*.yml` in the tree and all six are in the four builder packs — `builder-agent-specs`
  is named by 9 `prod/` Java files over 18 occurrences, every one inside
  `embabel-smith-builder` — while the runtime's own library packs declare three-property types and the
  builder's published output declares none, both of which `String.valueOf` renders correctly. Nothing
  re-parses the value either: every consumer of `RunOutcome.output()` is display (`RunController.java:53`,
  `SimulateResponse.java:35`, `cli/RunCommand.java:70`, `RunBackedA2ARequestHandler.java:277`), and the
  runtime's README documents the `{topic=…, audience=…}` rendering as intent. Worst case is a run whose
  output reads oddly on screen, not a failed parse. The reason it stays a note is the cost of a guard: the
  runtime has no offline seam for `outcome()` — `RuntimeCoreTest` autowires a real `AgentPlatform` against a
  fake endpoint — so a test would be a new mock-platform seam built to exercise a branch nothing reaches.
  Check: `grep -rn "builder-agent-specs" prod --include=*.java | grep -v embabel-smith-builder/` prints
  nothing, `grep -rl "builder-agent-specs" prod --include=*.java | wc -l` prints 9, and
  `grep -n "String.valueOf" prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/{core/RuntimeCore,trace/TraceRecorder}.java`
  still prints three lines — the rendering is unchanged, and the counts are the unreachability.

## Format and sources

- **Open — nothing checks that a declared `toolGroups` entry resolves to anything.**
  `builder/eval/ToolGroupBindingTest` (2026-11-21) measures the absence rather than inferring it:
  `AgentSpecValidator` has no tool-group code path, `PromptedActionSpec.emit` maps each declared string
  straight into `ToolGroupRequirement(role)` without consulting the scope's offered groups, and
  `DefaultAgentPlatform.deploy` is a map insertion plus an event. A pack declaring `web` and a pack
  declaring `webb` therefore deploy identically, and the misspelling surfaces only when a prompt comes back
  with no tools — the same family as the unnamed prompt variable that cost POC 27 its run, except that the
  prompt-variable case acquired a validator rule (`PROMPT_UNDECLARED_INPUT`) and this one did not. The
  related discrepancy is in names, not plumbing: upstream's roles are `web`, `math`, `maps`, `github` and
  `browser_automation` (`CoreToolGroups` in `embabel-agent-api-1.5.4.jar`), while
  `builder/security/TrustTier.java:24,27` allowlists `search`, `web`, `file` and `shell`, so three of a
  tier-3 pack's four names may resolve to nothing while being spelled correctly by this repository's own
  lights. The tier is still a real policy surface — it is read per pack and a pack cannot widen it — but
  whether its extra names are capabilities or vocabulary is a live-platform question, which is what the
  test's endpoint-gated case asks. Check:
  `grep -rn "toolGroups" smith-common/src/main/java/com/embabel/smith/common/validation/` returns nothing.

  **Answered in part 2026-11-22, offline, from the 1.5.4 jars.** The vocabulary half is settled.
  `@LlmTool` carries no group member (its members are `description`, `name`, `returnDirect`, `category`,
  `metadata`), and `api/annotation/ToolGroup` is referenced by no class but its own across all 25 jars, so
  annotating anything registers nothing. Resolution runs against
  `RegistryToolGroupResolver(String, List<ToolGroup>)`, whose list Spring fills from `ToolGroup` beans
  (`AgentPlatformConfiguration.toolGroupResolver`), and out of the box that list is upstream's five roles
  plus whatever MCP configuration adds — none of them a builder service.
  A `ToolGroup` bean is **not authorable directly** in Java — `ToolGroupMetadata`'s accessor is
  `getVersion-Id9oKnY()` and its companion factories are `invoke-oteLqWg` / `invoke-bYD-3ao`, names `javac`
  cannot express — but it **is authorable**, and this repository already contains the recipe:
  `poc/poc-7-runtime-adapter/.../ShellToolGroupFactory.java` resolves the five-argument companion factory by
  *signature* through reflection and hands the result to `ToolGroup.Companion.ofTools` (a plainly-named
  method), and `ShellToolGroup` publishes it with `@Bean`. So a step can declare a name that resolves, and
  giving the builder's validators a name is repo work rather than an upstream request. The recipe's cost is
  the one its own failure message names: the lookup is signature-pinned, so an Embabel bump turns it into a
  loud `IllegalStateException` rather than a silent loss.
  Second, the tier does not merely under-grant, it over-reports: `TrustTierPolicy.check` filters the
  declared **strings** against the allowlists and resolves nothing, so an unrecognised name — a typo
  included — lands at `T4_PLATFORM`, because T4 allows `*` and `minimumTier` always terminates there. A
  pack can read as maximally privileged while reaching nothing, which disqualifies a tier movement as
  evidence that a declaration works. Check:
  `grep -rn "@ToolGroup" prod/ --include=*.java` returns nothing, and
  `grep -n "@Bean" poc/poc-7-runtime-adapter/src/main/java/com/embabel/smith/adapter/ShellToolGroup.java`
  was the tree's only working registration when that check was written. The second one has since landed
  under `prod/`, and the paragraph below is about it.

  **Closed for the two declaring packs, 2026-11-22 — the reaching-nothing half.**
  `prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/tools/ValidationToolGroup.java` is a
  `@Bean ToolGroup` whose role is `validate`, carrying exactly the tool
  `eval/BuilderPackTools.validationTool()` authors, and the two steps that name it —
  `builder-single-spec/steps/assembleAgentSpec.yml` and `builder-spec-one-shot/steps/draftAgentSpec.yml` —
  now resolve. Measured twice, because there are two ways to reach the same deploy. On the live test case
  that produced the warning, the tool loop starts as `starting tool loop [validatePack]` where it started as
  `starting tool loop []`, the validator is offered and not called (`called=0`), and the goal completed in
  one model call. On the shipped CLI path (`--flow pack`, three blueprint inputs, 2026-11-22) it is offered
  *and* called — twice on one input, three times on another, 116 times on the third — so the registration
  did not merely make the name resolve, it made the step reach a Java service mid-generation.
  `eval/AgentSpecInvokerFactory`'s scope tool list is empty again — that channel was never reachable by a
  pack, because `PromptedActionSpec.emit` selects from it by the step's own `tools:` name list, which
  `ActionStepSpec` cannot express: thirteen components, no `tools` component.

  **Open — the validator-inspects-nothing half.** `AgentSpecValidator` still reads no `toolGroups`, so a
  *misspelled* entry resolves to nothing with only the `ToolConsumer` WARN, exactly as it did before the
  registration. The scope builder's `List<ToolGroupDescription>` argument does not close it: nothing on
  the step-spec deploy path reads the context's tool-group list, and the only reader in the jar is
  `DefaultNaturalLanguageCompiler`, which belongs to the other generation technique.
  `builder-executes-its-own-packs` design D7 remains where the route is decided.

  **Closed 2026-11-22 — offered was not the same as usable, and the argument was the reason.**
  `validatePack` took one argument, a directory, and on the shipped `--flow pack` path there is no
  directory the drafting step could know to pass: the draft is the model's own pending answer, not a file.
  So the step guessed — measured arguments were `.`, `/tmp/nonexistent_probe_dir`, the spec's own name as a
  relative path, that name with `./` and with a trailing slash, and a path to a `manifest.yml` *file* — and
  92 of the 93 results on one build's first pass were the same `LAYOUT_MISSING_MANIFEST`. The Java flow did
  not have that problem, which was the tell: `core/BuilderAgent.validateDraft`, called from
  `BuilderService.java:141`, validates the `CompiledDraft` in hand and never needs a path. `99f9cc0` took
  the first of the two options recorded here rather than the pack-text one: the tool's single parameter is
  now `specJson`, the spec the step is about to return, and
  `tools/AgentSpecValidationTool.validatePack(String)` runs the same in-memory
  `AgentSpecValidator.validate(AgentSpec)` overload the Java flow uses. Layout is the one check whose
  subject is a directory, so the tool no longer runs it; `BuilderAgent.java:160` still re-validates the
  directory it wrote, which is why nothing is lost end-to-end. An argument that is not a spec is answered
  with `PARSE_ERROR` rather than thrown. Offline that is red-before/green-after on `BuilderPackToolsTest`
  (2 of 7 failing at the old SHA) and a mutation of the raw-text fallback fails exactly one case. On the
  live `--flow pack` path the argument the model sends is now its own draft: a `parcel-claims` build
  published and logged two calls, each `calling tool validatePack({"specJson": "{\n  \"manifest\": …`, the
  first returning a real `DANGLING_TYPE_REF` and the second `No findings: the agent spec is well-formed.` —
  the loop closing rather than spinning, against 116 path-guessing calls on the same input before.
  Still open inside the fix: nothing dedupes a repeat, so a model that re-sends the same draft spends the
  same call twice (19 calls on one input that never got an answer at all). Closed 2026-11-22 — the
  over-report half: `tools/AgentSpecValidationTool.validatePack` now runs the validator on the
  `AgentSpecNormalizer.normalize` output the build would ship — the same spec `BuilderService.java:141`
  then validates — so a finding normalization removes (`GOAL_NOT_EXPORTED`, prompt-variable casing) is
  named as the build's own fix instead of the draft's homework, and only what survives it is left to the
  model; `tools/ValidationToolNormalizationTest` pins both halves. Check: `grep -n '"specJson"' prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/eval/BuilderPackTools.java`
  names the parameter, and a `--flow pack` build logs `calling tool validatePack({"specJson":`.

  The over-report is unchanged by the registration. `validate` is in none of
  `builder/security/TrustTier`'s allowlists (`search`, `web`, `file`, `shell`, plus T4's `*`), so both
  declaring packs still read `T4_PLATFORM` through the wildcard; registering the group moved no tier, and
  `builder/src/test/.../core/BuilderPackTierTest` is untouched and green — it still draws that same tier
  from a name chosen to be unresolvable, which is why it pins the declaration's *placement* rather than
  trusting the tier. Check:
  `grep -rn "toolGroups:" prod/embabel-smith-builder/builder-agent-specs/*/steps/*.yml | grep -v "\[\]"`
  now prints those two lines.
- **Open — the drafting step's reply-envelope sentence is load-bearing text with no gate around it.**
  `builder-spec-one-shot/steps/draftAgentSpec.yml` now states that the reply is a one-key object whose
  `json` value is the whole spec document (`e5e3b04`), which is what the pack's goal output type
  `types/AgentSpecJson.yml` declares and what `PromptedActionSpecAction.callLlmWithDynamicType` contributes
  as an advisory schema. Those two previously disagreed — the step's own prose showed the spec document as
  the reply — and a model that followed the pack rather than the contributed schema answered the document at
  the top level, which is the `Unexpected character ('m' …)` variant of the read-back failure. The
  consequence for the improvement loop is that a mutation of step prose can now reopen that failure with
  nothing flagged on the way: `AgentSpecValidator` never reads prose about output shape, the deploy
  succeeds, the recommended trust tier is unchanged, and the damage surfaces only as
  `PackDrivenAgentSpecGenerator`'s "did not return valid agent spec JSON" at the end of a paid build. The
  drift pin added with the fix,
  `BuilderAgentSpecPackTest.theStepThatProducesTheGoalOutputNamesTheEnvelopeItsGoalTypeDeclares`, reads the
  key from the pack's own goal output type rather than a Java literal and requires it to appear quoted in
  the prompt of the step that produces that type — so a rename and a deletion both fail it, but only for the
  one pack that test loads. Same family as the misspelled `toolGroups` entry and the unnamed prompt variable
  that cost POC 27 a run: resolves to nothing, indistinguishable from correct, until something downstream
  notices. `poc/poc-27-recursive-self-improvement/src/main/resources/evidence/MUTABILITY.md` still says a
  generation may change only a step's `prompt:` text, which remains literally true and now understates.
  Check: `grep -rl '"json"' prod/embabel-smith-builder/builder-agent-specs/*/steps/*.yml` prints one file.
- **Open — the other three builder packs have the same gap and no fix.** Every pack's goal output type is a
  single-property value type — `builder-single-spec` and `builder-spec-one-shot` both declare
  `AgentSpecJson`, `builder-process-composition` declares `ProcessJson`, `builder-skill-from-process`
  declares `SkillBriefs`, each with one own-property — and only the one step on the shipped pack-flow path
  names its envelope key. `assembleAgentSpec`, `composeProcessJson` and `writeSkillBriefs` still show the
  document as the reply, so a deploy of any of those three carries the same two-shape prompt collision that
  cost the pack flow its builds. Nothing exercises it because `--flow pack` deploys `builder-spec-one-shot`
  alone and the other three are named nowhere in `src/main`. Deliberately not fixed under it: three step
  prompts is three more pieces of load-bearing mutable prose with no gate, which is the item above.
  Check: `grep -c '"json"' prod/embabel-smith-builder/builder-agent-specs/*/steps/*.yml | grep -v ':0$'`
  prints one file, and `grep -n outputTypeName prod/embabel-smith-builder/builder-agent-specs/*/goal.yml`
  prints four.
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
  prod/embabel-smith-builder/src/main/java/` returns nothing. The path is the builder's since 2026-11-21,
  when the improvement engine moved out of the studio; the pattern is worth running across all of
  `prod/*/src/main` too, because an absence check against a directory that no longer exists passes
  without checking anything. What the loop does pass today is a configured value —
  `LoopPublishPolicy:107` hands the publisher its `this.push` field, not a literal.
- **Closed — the loop no longer depends on the folder for durability.** The forced `overwrite = true`
  is still there, deliberately: `FolderPublisher` names the destination directory from
  `Publishable.name()` and `Publishable` exposes no version, so clearing the flag would make the loop's
  second publish throw `CollisionException` — unchecked, and caught nowhere on the loop's path —
  instead of improving. What changed is where the previous version survives. A publish that succeeds
  now also records the pack — through the builder's `CandidateStore.save` seam, which the studio
  implements over `ProcessStore.savePackWhenConfigured` — and `packs` is keyed by
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
  `grep -rn 'packStore.save(candidate.candidate())'
  prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/improve/LoopPublishPolicy.java`
  returns the post-publish save. Both halves of that command changed on 2026-11-21: the file moved to the
  builder with the rest of the improvement engine, and the `CandidateStore` seam replaced
  `savePackWhenConfigured` with `save`, so the older pattern now returns nothing while the behaviour it
  checks for is unchanged. `grep -rn 'boolean overwrite = true'` on that same file still returns
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
  exposed as a CLI or left unused: the deliberate-dormancy ruling once recorded for it was reversed by
  the human, and the change folder that held that ruling was deleted on 2026-11-20 once its two
  surviving tasks had landed; a CLI entry point remains unimplemented. It
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

- **Closed — the scorer-out guard's threshold now means "copied", not "about the same subject".**
  `skill/ScorerPromptBoundaryTest` keeps the three scorer prompts out of `builder-agent-specs/`, and its
  measure is now the longest run of consecutive normalised words of a scorer that occurs verbatim and in
  order in the pack text, as a fraction of that scorer's own words — a contiguity requirement in place of
  the fraction of shared 5-word runs it took before. `MATCH_THRESHOLD` stayed at 0.6; the measure moved,
  not the constant. On the same thirteen step prompts (17,906 characters, 2,750 normalised words) the three
  scorers now measure 0.037 / 0.000 / 0.000 where they measured 0.023 / 0.000 / 0.000, a planted verbatim
  copy still measures 1.000, and the builder's legitimately-related generation prompts fall from 0.818
  (`process_to_flow`) and 0.627 (`process_to_flow_multi`) to **0.209** and **0.104** — both under the
  threshold, which is the reassurance the old paragraph asserted but did not have. The longest block the
  packs share with `process_to_flow` is 96 words of its 459: related, not copied. The exposure this entry
  recorded is closed and the calibration is now asserted rather than argued —
  `legitimatelyRelatedBuilderTextDoesNotReadAsACopy` fails on the old measure (mutation-checked: removing
  the contiguity requirement fails it at 0.8176; refusing to extend a seeded anchor fails the planted-copy
  control at 0.023; planting a copy in the guard's own haystack fails the guard at 1.0). What the change
  costs is stated in the test's javadoc rather than hidden: a copy with its central term renamed now
  survives one or two scattered renames (0.881, 0.835) and slips at three (0.463), where the run-overlap
  measure held to 0.907, and a 1-in-10 scattered paraphrase measures 0.041 against 0.528 — under the
  threshold under both, so a paraphrased grading step stays the review obligation D10 records, not a closed
  gap. Check: `grep -n "MATCH_THRESHOLD = \|longestRunFraction\|legitimatelyRelatedBuilderText"
  prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/skill/ScorerPromptBoundaryTest.java`
  prints the unchanged 0.6, the contiguity measure, and the calibration control.
- **Closed — nothing recorded that the default test run leaves unproven.** The endpoint-gated
  integration tests (`LiveBuildIntegrationTest`, `LiveExampleBuildTest`, `LiveRunIntegrationTest`, `SmithA2aLiveTest`,
  `LiveLoopIntegrationTest`, `RestIntegrationTest`) are now tagged `@Tag("live")` *as well as* env-gated, and the group is
  excluded by default through `test.excludedGroups`, with `make test-live` the single opt-in — that
  machinery shipped on 2026-11-17. What was missing was the consequence, and it is now stated in
  [`05-architecture.md`](05-architecture.md) §"The live group is opt-in": the paths these tests
  alone exercise — a build publishing a pack the model really produced, a successful run, the a2a
  endpoint answering a real task, the studio's
  REST surfaces with model-backed cores, one full loop iteration — and that a green default run
  evidences none of them. That page names its list by a grep so it cannot rot silently; the builder's
  example suites it used to list were removed on 2026-11-17 and are not part of `make test-live`.
  The affordable next live test — a small blueprint with a known expected spec and skill — was named in
  [`08-builder.md`](08-builder.md) and **now exists**: the builder's `LiveExampleBuildTest` builds one
  hand-written 1.5 KB blueprint through both targets and asserts the deliverable its fixture names twice,
  with the assertion proven to bite by mutating the expected token. **What changed since is who runs the offline suite, not what it
  proves:** `.github/workflows/offline-verification.yml` now runs `make build`, `make test` and
  `make lint` on every pull request and every push to the default branch, with no endpoint and no
  secrets, on the default reactor. So a green CI badge is the same evidence as a green local `make test`
  and nothing more — the paths above are still outside it, as is the `poc/` reactor. Read it as
  "nobody had to remember to run it", never as "the publish, run and loop paths were verified". Check:
  `grep -rln '@Tag("live")' prod/*/src/test` prints the files named in this item — and it is written
  without an expected count on purpose, because this item said "exactly those four files" until the a2a
  launcher added a fifth, which is the rot the check was supposed to catch and did not, because the
  check itself went stale with the claim. `grep -n 'does not prove'
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
