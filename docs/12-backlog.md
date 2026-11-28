# Backlog

Everything the project knows it has not done, with the reason each item is open. Consolidated from
the planning records that preceded these pages; each item was re-checked against the current tree,
and the verification is named so a future reader can re-run it.

## Pending right now

Re-counted against the tree on 2026-11-25, so the list below is the whole of it rather than a sample.
`BLOCKERS.md` holds the four things that need a human decision or an upstream release instead of code.

The OpenSpec tree carried **four unchecked boxes** when it was purged into these pages on 2026-11-25,
none of them code, and all four are recorded here so nothing was lost by the deletion:

- **`builder-executes-its-own-packs` 5.3** — the wiki note for that change. Its local half (the wiki
  prose, its links and its `source_paths`) is done; what remains is the GitHub Pages deploy, which
  `BLOCKERS.md` §1 owns.
- **`builder-skill-script-host` 4.2** — the same shape: the wiki pages that still describe a published
  skill as inert, then wiki lint and the Pages republish. Also `BLOCKERS.md` §1.
- **`builder-as-agent-harness` 1.5** — `make native` builds and the binary boots with the script engine
  on the classpath. A verification chore, not a new test; the substantive finding is already below in
  §Builder — skill scripts (the switch-ON image cannot load a skill folder, the switch-OFF one builds
  the whole skill path), so this box adds a boot check and nothing else.
- **`builder-as-agent-harness` 5.2** — the parity run itself: hand-run and endpoint-spending, with the
  task's own rule that nothing here claims a number it did not see.

That count is a correction, and it is the kind of correction this page keeps needing: it said *one*
unchecked box until 2026-11-24, and the two changes archived on 2026-11-24 and 2026-11-25 each arrived
with boxes of their own. A claim about how many open items exist goes stale by *addition*, and nothing
in the build or a hook catches it — so the check is the four bullets above plus
`grep -n 'Pages\|deploy' BLOCKERS.md`, not a sentence on this page. The pre-purge form of that check
was `grep -rn '^\s*- \[ \]' openspec/changes/archive/*/tasks.md`, which printed exactly these four
lines; with the planning tree purged it prints nothing at all, and an empty result from a check whose
subject was deleted is the absence that rotted the sentence, not a green light. The four boxes that were open when the
page said "five" all closed on 2026-11-22. `publish-keeps-versions` 3.2 and `builder-live-example-tests`
3.1 were never work — the first is withdrawn by its own design decision 7, the second is superseded by
its proposal's Direction change — and are now ticked as such with the code re-checked against each
ruling. The two documentation boxes on `builder-executes-its-own-packs` both landed in
`docs/08-builder.md`, which gained
`## Who issues the generation call: --flow`: the flag, that `java` stayed the default, the refusal that
precedes any spend, the packs and why the separation from the referee is now by directory rather than
by staying off the classpath, and the measured verdict that kept the flip unflipped. That section is
also where the `builder-flow-execution` requirement set now has a
docs home; it was the only one of the change deltas with none. Six changes were archived the same day,
leaving `builder-executes-its-own-packs` open on that single publish half and
`poc27-improves-builder-agentspecs` complete and waiting to be archived.

The open items in the rest of this page were each re-verified on 2026-11-22; the ones this purge
touched were checked again against the tree on 2026-11-25, and each still holds:

- **Closed 2026-11-23 by `builder-agentic-tool-surface` — a declared `toolGroups` entry is now checked,
  and a repeated tool call is deduped.** Both halves moved. `eval/AgenticAgentSpecGenerator#refuse`
  compares each group a pack's steps declare against the roles the booted resolver can actually resolve
  and refuses the deploy before any model call, so a misspelled entry is a build failure rather than a
  step that runs and silently reaches nothing; the check is on the agentic deploy path and not in
  `eval/AgentSpecInvokerFactory`, which is also the eval and improvement path (its
  `ToolGroupBindingTest` still pins that a typo deploys verbatim there). And `tools/ToolSurface#remembered`
  answers an identical repeat of a call from the first one, keyed on tool plus arguments, bounded and
  cleared at each build's start — so a model that re-sends an unchanged draft no longer pays for it
  twice, while a call that differs in any argument still runs. Every tool body routes through it, the
  three deterministic checks with the document reads, which is the half that matters: the repeats the
  measured runs counted were `validatePack` calls. Check:
  `grep -rn toolGroups smith-common/src/main/java/com/embabel/smith/common/validation/` still prints
  nothing — the structural validator remains role-blind, deliberately, because it has no registry to
  consult — and `grep -n "refuse(pack)" prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/eval/AgenticAgentSpecGenerator.java`
  names the check that replaced it.
- **Closed 2026-11-23 — `smith build` runs in the native binary again, and the native CI still cannot see
  it.** Found while measuring the native half of `builder-agentic-tool-surface` 5.6: the built
  `target/smith` booted and answered `smith` and `smith blueprint --help`, but every `smith build`
  invocation died with `MissingReflectionRegistrationError` naming `BuildCommand.flow`, because picocli
  reads every `@Option` field of a subcommand during parse. It was pre-existing — `flow` is absent from both
  hint files at `poc/iteration-3`'s head and at the base of the change that found it, and
  `git diff --stat <base>..HEAD -- src/main/resources/META-INF/native-image/` was empty — the traced hints
  list the other eleven `BuildCommand` option fields because the run that generated them predates `--flow`,
  and the hand-written file registered `BuildCommand$FlowConverter` but not the field beside it. The entry
  the error names is now in `builder-native-hints/` (`ab28a90`), beside that converter entry and for the same
  reason: no traced run reads it. A rebuild proves it — `./target/smith build --help` prints the usage banner with
  `[--flow=<flow>]` listed as java / pack / agentic, `./smith build --flow bogus` fails as a *usage* error
  from `FlowConverter` rather than a reflection error, and `grep -c MissingReflectionRegistrationError` on
  either run prints `0`. **Still open is the CI blind spot**: `.github/workflows/native-image.yml`'s smoke
  step runs `"$SMITH_BIN"` with no subcommand and greps for `Usage: smith`, and the root no-arg path never
  instantiates a subcommand's `ArgSpec`s — its comment claims it proves the image "parses arguments", which
  is true only of arguments the root accepts. Widening the smoke to invoke a real subcommand is a decision
  for the user, not a change to make unasked.
- **Pack loading no longer depends on the working directory; the image is unproven and the loop's destination is open.**
  The six builder packs are classpath resources under
  `prod/embabel-smith-builder/src/main/resources/specs/`, and all three pack-driving generators name their
  pack as `BuilderPackPaths.directory(<pack>)` — `core/BuilderPackPaths` (`ROOT` = `specs`) over
  `smith-common`'s `SpecFolders.classpath`, which returns the class directory itself when classes are
  exploded and a temporary extraction when they sit inside a jar. That closes the failure this entry
  recorded: a pack was `Path.of("builder-agent-specs", …)` resolved against the process working directory,
  so `--flow pack` and `--flow agentic` worked only from inside `prod/embabel-smith-builder/`. Measured
  then, with the endpoint pointed at a closed port so no call was spent: from the repository root both
  flows printed `failed to load the builder pack at builder-agent-specs/…: builder-agent-specs/…/manifest.yml`
  and exited 1 without a model call, while from the module directory the identical command got past pack
  loading and stopped on an okhttp socket connect. That message named a working-directory path, and no
  flow holds one now, so the launch directory has no route left to fail from. The measurement above is
  evidence about the old seam; the re-run from the repository root is on record, and it is a run against
  the exploded `target/classes` with the live endpoint already in the environment rather than a closed
  port, so it spent calls. Launched from the repository root with `--flow pack`, the build logs
  `Deployed agent builder-eval-scope-…` and `(action:draftAgentSpec) using LLM …` before stalling on the
  endpoint's 60s retries — pack loading is no longer the line it stops at.
  What followed from that split three ways, and each branch is settled except where it says otherwise.
  **(a) The binary.** `builder-native-hints` registers `specs/*/*.yml` and `specs/**/*.yml`, and the
  `native` profile copies `src/main/resources/specs` to `target/specs`, beside `target/smith`.
  Registration puts the pack files in the image, which makes an individual file resolve by name and does
  not make a pack's directory listable — an image has no directory entries — so `SpecFolders.classpath`
  falls through, in order, to `smith.specs.dir` / `SMITH_SPECS_DIR` and then to the `specs/` folder
  beside the running program, with the classpath route still answering first so an override left in
  someone's shell cannot shadow the pack that shipped. Unproven, because no image was built: that a
  GraalVM image reports its own executable path through `ProcessHandle.current().info().command()`,
  which the third route depends on and the second does not, and that the two globs carry the 48 files
  in at all. No run on record drives a pack flow from `./target/smith`, and the native smoke still
  invokes the binary with no subcommand, so the flow itself stays unproven. What CI does check is that
  the artifact is whole: the smoke refuses when no `specs/` sits beside the binary, and the upload
  carries both paths with their common ancestor stripped, so a downloaded `smith` has the folder its
  third route reads beside it.
  **(b) The improvement loop's surface.** `improve/BuilderPackSurface` resolves the builder's packs as
  source files — walking up from the working directory for
  `prod/embabel-smith-builder/src/main/resources/specs`, refusing and naming every directory tried when
  there is no checkout — and the studio's `improveSource` bean defaults to it instead of the empty temp
  folder that made the loop inert. An explicit `smith.studio.improve-source` or `smith.studio.sources`
  still wins, and the loop reads, gates and publishes the files it edited rather than a
  `target/classes/specs` copy that the next build would silently revert. The destination is what stays
  open, deliberately: `loopPublisher` resolves where an approved candidate is written from the
  configured URI and never consults the source bean, so with nothing configured the publish refuses and
  the candidate stays pending. That asymmetry is what keeps an unattended loop from rewriting the
  checkout — `FolderPublisher.publish` deletes and rewrites the pack folder with no git invocation
  anywhere in the class, so naming it as a destination is a file-level decision, and only a `git:`
  source reaches a commit, in a temp clone, pushed only with `smith.studio.loop.push` on. The price of
  the new default is that a studio installed without a checkout now fails context startup at that bean
  instead of booting with an inert loop.
  **(c) `poc/`.** `poc-27-recursive-self-improvement`'s `outerloop/BuilderPacks.PACK_ROOT` names
  `prod/embabel-smith-builder/src/main/resources/specs`, its walk-up and both refusal messages
  unchanged, with `BuilderPackCandidateTest` loading the pack from there offline. Two statements in that
  POC's own evidence are now false and were left as found: `src/main/resources/evidence/MUTABILITY.md:48`
  still argues the packs sit outside `src/main/resources/`, which nothing executes but which is the page
  later classes cite, and `README.md:85` with `MUTABILITY.md:46` still say four packs where six ship.
  `examples/parity-agentic.sh` still launches from `"$SMITH_ROOT/$BUILDER_MODULE"` (`7cc5f17`, added when
  the cd was what kept the harness from failing every input at the pack-load line); with pack loading off
  the CWD that cd is convention now, and it was left alone as a hand-run script whose prior records ran
  that way.
- **The tool-group resolution check covers the route that drives the model, not every deploy seam.**
  `grep -rln "availableToolGroups\|resolveToolGroup" prod/embabel-smith-builder/src/main/java` prints
  `eval/AgenticAgentSpecGenerator.java` alone, so a pack declaring an unresolvable role is refused on the
  agentic path and deploys verbatim through `eval/AgentSpecInvokerFactory`, which is also the eval and
  improvement deploy path — where `eval/ToolGroupBindingTest` deliberately pins that a typo passes. The
  permissiveness there is not an oversight: machine-generated candidates declare no roles, so the seam has
  never needed the rule, and adding it would make a Mockito-double platform (its resolver is null) throw for
  the wrong reason. It is still a wider surface than the rule, so a typo inside a hand-written pack reaching
  that seam stays silent. Closing it means giving that seam a real resolver to consult, which is a larger
  claim than the one `builder-agentic-tool-surface` made.
- **The reply-envelope sentence gates three packs, not six.**
  `grep -rl '"json"' prod/embabel-smith-builder/src/main/resources/specs/*/steps/*.yml` prints
  `builder-spec-one-shot/steps/draftAgentSpec.yml`,
  `builder-agentic-blueprint/steps/draftAgentSpecFromBlueprint.yml` and
  `builder-harness/steps/draftAgentSpecFromRoots.yml` — the same `{ "json": "<the spec document, as a
  string>" }` line in all three — while `grep -l outputTypeName
  prod/embabel-smith-builder/src/main/resources/specs/*/goal.yml` prints six paths. So every pack's goal names an
  output type and the sentence's absence is a three-pack claim: `builder-single-spec`,
  `builder-process-composition` and `builder-skill-from-process` still show the document as the reply. The
  absent trio is the same trio it was before the sixth pack landed — `builder-harness` arrived *carrying*
  the sentence rather than needing it, so the sixth pack moved the count on both sides of the sentence and
  changed nothing about which packs lack it. Latent because each pack flow deploys one pack alone:
  `--flow pack` the one-shot pack, `--flow agentic` the agentic pack, `--flow harness` the harness pack.
- **`/scale` is still a stub** — `grep -rln PocOnlyPane prod/embabel-smith-studio/frontend/src` prints
  `panes/ScalePane.vue` only, and that is a product decision rather than an omission.
- **The install-hint surface is gated, not gone** — `SkillInspector` requests the binary-free checks
  unless a caller passes `fullInspection`, so the third-party install strings are reachable only through
  `--full-inspection`, which is a human action.
- **A2A protocol 1.0 waits on `io.github.a2asdk`** leaving Alpha; `prod/embabel-smith-a2a-starter` is the
  only module declaring the starter, and its README states the non-goal against the discovery-path
  deviation.

Two stale claims surfaced by the re-count. One is now fixed; the other still stands.

- **Fixed 2026-11-23, moved again 2026-11-25** — `docs/index.md` §Where knowledge lives and `AGENTS.md` §Documentation status said
  the wiki cross-links 44 pages, and this entry's own check said `find .wiki -name '*.md' | wc -l` prints
  **45**. Both numbers were true once and neither is now: the count went 44 at the init commit, 45 with
  the 2026-11-22 ingest, 46 since `d06998b` added `.wiki/overview/native-binary.md` on 2026-11-23, and
  **47** since `54da618` added `.wiki/features/skill-script-javascript.md` on 2026-11-25.
  The two pages now say 46 *cross-linked* pages and name the file count beside it, because the two numbers
  answer different questions and the entry had conflated them: `find .wiki -name '*.md' | wc -l` prints 47
  files, and the distinct slugs the `[[...]]` links actually name are 46 — `index` is the only page no link
  names, since it is the map rather than a destination on it. The lesson the entry should have carried
  from the start is that a count stated without its command is a count that reads as durable, and the
  re-count two days later is that lesson applying to this entry.
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

Three more, on the skill's executable half. The reader's side of a published folder now has one
route — `skill/PublishedSkillHost`, reached by `smith skill-run` — so what is listed here is what
that route did not close. Design D8 of `builder-as-agent-harness` records the loader's behaviour, so
it is quoted rather than re-derived here.

- **A published skill has one runner, not a readership.** The engine sits behind
  `smith.builder.skill-scripts.enabled`, off by default, and a generated skill may carry `scripts/*.js`.
  Two callers of that seam exist in `prod/` main. `skill/SkillScriptHost` writes the skill *under
  build* out as an Agent Skill folder and loads it through `Skills` for the with-skill eval arm
  (`skill/SkillScriptHost.java:90`). `skill/PublishedSkillHost` loads the directory `publish` wrote,
  through Embabel's own `withLocalSkills` on the real folder rather than a re-derived copy, and
  `cli/SkillRunCommand` (`smith skill-run --skill-dir <dir> <request>`) is the route that hands it to a
  model call — so with the switch on, a published script executes in `prod/`, and the run prints which
  folders loaded and which the loader refused. What that does *not* buy is a readership: the A2A
  endpoint's skill list is still card entries built from a spec source
  (`a2a-starter/SpecSourceSkills` publishes `io.a2a.spec.AgentSkill`, a capability description and not
  a loader), and a `smith run` is offered nothing from a published folder. Check:
  `grep -rn 'withLocalSkills' prod poc --include=*.java` names the two hosts, the POC 26 runtime, and
  the two tests that read a published folder; `grep -rn 'withScriptExecutionEngine' prod poc
  --include=*.java` names the same set plus the engine's own configuration.
- **A script tool's name is not sanitized.** Embabel derives it as the skill name plus the file's base
  name, so a hyphenated skill publishes a hyphenated tool name —
  `cold-store-goods-receipt_check-temperature` in `publish/PublishedSkillScriptToolSurfaceTest.java:73` —
  while the activation tools use a sanitized prefix (same file, its opening javadoc). Neither of those is
  the whole name a model is offered: a reference-carrying call puts the *reference's* sanitized name in
  front of the join, so the published host's script reaches the wire as
  `smith_published_skills_cold-store-goods-receipt_check-temperature` — its reference being the host's
  `smith-published-skills` — which `skill/PublishedSkillHostModelCallTest` asserts in the first request's
  tool list before asserting the script's stdout in the follow-up. The listing tests name it unprefixed
  because they read `Skills.tools()`, before any naming strategy has been applied. A generated body
  naming the tool literally is therefore wrong for any hyphenated skill; the path inside the folder is the
  stable handle. Left to the host deliberately: the rule is public —
  `com.embabel.agent.skills.script.SkillScript.toolName`, which `script/ScriptTool` reads for the tool
  definition — so a builder-side preview is possible, but it would be a second place asserting a name the
  reader derives, and it drifts the first time upstream changes the join. What the builder does own is the
  body it generates, and the shipped instruction already points at the path rather than the tool:
  `prompts/generate_skill.jinja` tells the model to run the script and quote verbatim what it printed
  (`:50`) and its output contract names `scripts/<name>.js` (`:56`) without ever naming a tool. The hazard
  was recorded as latent, and one live build says otherwise at the argument rather than the body: the model
  called the annotation tools correctly but in 3 of 27 calls passed `system_specification_md_sentinel_multi_a`
  — the sanitized tool-name prefix — as the `skillName` argument, where the declared name is hyphenated, and
  those calls could not resolve however healthy the folder was. Check:
  `grep -c "Skill not found: 'system_specification" examples/out/Sentinel/skill/build.log` (3) against the
  hyphenated form on the same file (24). Also check:
  `grep -n 'In the body, say to run\|scripts/<name>.js' prod/embabel-smith-builder/src/main/resources/prompts/generate_skill.jinja`.
- **A skill body that names a script the folder lacks is refused silently by a reader — and now refused
  loudly by the builder.** The reader-side fact is unchanged: the loader drops the **whole skill** having
  logged a warning nobody reads. What landed on 2026-11-23 in `cd6de3d` is the builder refusing the same
  artifact before writing it: `skill/SkillFileReferences.missing(skill)`, called at
  `skill/SkillBuilderService.java:302` as the code comment's stage 7b beside the security gate, blocks the
  build at stage `references` naming both the path and the consequence. The extraction is Embabel's own
  `skills.support.InstructionFileReferenceExtractor` (`skill/SkillFileReferences.java:60`) — the object the
  reader's loader calls — so markdown link targets count and fenced code blocks do not: a body that
  *teaches* a path is not a body that claims one, and no second source of truth for a rule the framework
  owns. `skill/SkillBuilderServiceTest`'s
  `aBodyNamingAScriptTheSkillDoesNotCarryIsRefusedAndTheRefusalNamesTheFile` and
  `aBodyLinkingAFileTheBuilderNeverPublishesIsRefusedInTheSameWay` are the two refusals,
  `aBodyThatNamesNoFileAtAllStillPublishes` and `aBodyNamingTheScriptItActuallyCarriesPublishes` the two
  controls, mutation-checked in both directions. The generator's older guards stand where they were: a
  declared name that is not a `.js` file still stops the build at `skill/SkillGenerator.java:121`, and a
  blank-content script is still *dropped* at `:112` — that drop is the case that produced the finding, a
  model writing the instruction and shipping an empty file, and what changed is that the mismatch it
  creates now stops at publish instead of shipping. Check:
  `grep -n 'SkillFileReferences.missing\|blocked("references"' prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillBuilderService.java`.

## Builder — skill scripts

Three things `builder-as-agent-harness` left open on purpose: it published the folder and the checks over
it without deciding who runs the code inside it, it added those checks where the stage list already had a
security stage rather than where they would save the most spend, and it proved the plumbing that carries a
script without measuring whether a model writes one.

- **A published script executes in `prod/`, on one route.** `skill/script/GraalJsSkillScriptExecutionEngine`
  implements Embabel's `SkillScriptExecutionEngine` seam and is a bean only under
  `smith.builder.skill-scripts.enabled`, off by default. The stronger absence first recorded here — that no
  `Skills` is assembled anywhere in `prod/` main — was half-filled by `2b16c27` and is filled on the reader's
  side by `builder-skill-script-host`: `skill/PublishedSkillHost` assembles one over the directory
  `publish` wrote and `cli/SkillRunCommand` offers it to a call that answers a request, so with the switch on
  a published script runs, and the two surfaces that speak about its reach are the scanner that refused the
  dangerous ones and the sandbox that denies the host — the host itself adds no rule and reads no trust tier
  (`skill/PublishedSkillHostTest` asserts the tier absence as a type check with the scan report as its
  control). The build-internal half from `2b16c27` stands as it was: `skill/SkillScriptHost` assembles a
  `Skills` for the skill *under build* and `skill/EvalRunner` offers it to the with-skill arm, so the two
  arms are one prompt substitution per arm only with the switch off, which is the default and why the
  recorded lift numbers still mean what they measured. The switch-on half is now measured and it moves for a
  reason that is not the skill at all: on a build whose folder the reader's loader refused, the arm made 27
  calls that all came back `Skill not found` and the run printed `skill lift: -0.125 (with 0.688 vs baseline
  0.813)`, two lost checks out of sixteen. A switch-on lift is therefore comparable to a switch-off one only
  when the folder actually loaded — which a log says out loud, as `Skill not found` on every call and one
  early `Skipping unloadable skill` WARN. Design D8 carries that amendment, and the host now reports the
  refused folders itself (`loading()`) instead of leaving them in that log.
  Design D8 of that change offered two routes and one of them has now been taken: *the builder runs them*
  would need an agent-shaped with-skill arm, whose prompt is one of the three scorer files
  [`10-self-improvement.md`](10-self-improvement.md) keeps off the mutable surface, and it is still open;
  *the reader runs them* landed as a CLI route rather than as an A2A one, because
  `a2a-starter/SpecSourceSkills` publishes `io.a2a.spec.AgentSkill` card entries — a capability description,
  not a loader — and that endpoint is still the open reader, along with `smith run`. What remains of the
  original item is therefore the readers, not the mechanism. The two constraints the host inherits — the
  unsanitized tool name, and the loader's silent refusal of a body naming a script the folder lacks — are
  the two items that follow it under §Builder — skill target.
- **The script scan sits at stage 7 rather than earlier.** `SkillBuilderService`'s security stage is
  where a `scripts/*.js` reaching for the host, the network or a path above the skill's own folder is
  refused, by `security/AgentSpecSecurityScanner.scanSkill` — which is after generation, after the
  inspection gate's redrafts, and after the benchmark's four model calls per scenario. A skill whose
  script was never going to be publishable therefore buys the whole eval bill before anything reads the
  script. Moving the code checks to just after generation is cheap in principle, since they are static
  and offline; it was not done because this change's scope was the build flow and not the skill
  pipeline's stage order. The text checks' placement there is deliberate for other reasons
  ([`08-builder.md`](08-builder.md) §`skill`); this one is simply where the list put it. Check:
  `grep -n 'scanSkill(' prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillBuilderService.java`
  against the numbered stage list in that file's header comment.
- **No live build has yet emitted a script.** Everything about a generated skill's JavaScript is proven on
  the plumbing side: `skill/SkillGeneratorTest` shows scripts named in a model answer arrive as files under
  `scripts/` (including that a `../` path is reduced into the folder and a non-`.js` name stops the build),
  and `publish/PublishedSkillScriptToolSurfaceTest` shows the published folder loads with its script tool
  present. What is unmeasured is the model's half — the generation prompt asks for one script per computed
  step (`prompts/generate_skill.jinja`, "Emit one small script per computed step"), and no endpoint-gated
  test or example record has ever shown a real answer containing one. The live skill test builds a skill
  and asserts the deliverable token, the stage count and the published layout, and nothing about scripts;
  the only `scripts/` directories in the repository are `embabel-smith-skillevaluator`'s Python fixtures,
  which the builder never generated and which are not JavaScript. So "generated skills carry JavaScript" is
  a statement about the format the builder accepts, not yet about what this endpoint's model produces.
  Closing it is a hand-run record rather than a new test: an assertion that a live model emitted a script
  would join the class of endpoint-gated tests this repo already declines to trust, because it breaks on
  model variance rather than on a code change. `examples/generate-skill.sh` already publishes a folder, so
  one run that lists that folder's files and lands in the record is the whole job — and it is worth doing
  before the script-authoring half of that prompt joins the mutable surface, since a prompt rewrite can
  only be checked against a baseline somebody actually took. Check:
  `grep -n 'scripts' prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/LiveExampleBuildTest.java`
  returns javadoc only, and `find . -type d -name scripts -not -path '*/node_modules/*'` names that
  module's fixtures and nothing the builder published. One switch-on live build has now been counted in that
  sample and it does not close the gap: `generate-skill.sh Sentinel` at 21:00:08Z with
  `smith.builder.skill-scripts.enabled=true` was blocked at `eval`, so it never reached `publish` and
  `examples/out/Sentinel/skill/published/` does not exist — the host folder it did write held one file,
  `SKILL.md`, with no `scripts/` beside it, and `find examples/out -type d -name scripts` is empty repo-wide
  after the run as before it. That is a third state worth distinguishing from the two the run was meant to
  separate: not "emitted and unused", not "published without scripts", but no published folder at all
  because the verdict stopped earlier. The generator's half is still unmeasured.
- **The switch-ON native image cannot load a skill folder, and the switch-OFF one builds the whole
  skill path — so the gap is one bean, not the native skill route.** A `--target skill` build run by
  the image whose build did bake the switch in dies uncaught in `main` at
  the eval stage: `kotlin.reflect.jvm.internal.KotlinReflectionInternalError: Unresolved class: class
  com.embabel.agent.skills.spec.SkillDefinition`, through `EvalRunner.run → SkillScriptHost.skillsFor →
  Skills.withLocalSkills → DefaultDirectorySkillDefinitionLoader.load` into Jackson's Kotlin
  introspection. The control was run from the same tree: the image built with no
  `SPRING_APPLICATION_JSON` completes the same command line and prints a verdict
  (`SKILL BUILD BLOCKED at eval …`, `skill lift: +0.000`), with **0** occurrences of that error. So no
  page should say the native binary cannot reach the skill path — it reaches it and finishes it; what
  it cannot carry is the engine bean. Neither reflection-hints file mentions `com.embabel.agent.skills.*`,
  and the traced hints in `ab28a90` (03:46) predate `2b16c27` (13:08, same day), the commit that made
  the path reachable, so the tracer never saw
  a call site to record. The same build on a plain JVM with the same switch reaches the tool loop, so
  the gap is the image's reflection metadata, not the wiring. Two facts worth having before anyone
  retries it: `process-aot` evaluates the engine's `@ConditionalOnProperty`, so an image built without
  the property in the Maven environment cannot have the bean added by any runtime switch
  (`MAVEN_OPTS=-D…` does not reach `process-aot`; `SPRING_APPLICATION_JSON` in the build environment
  does); and a green boot proves nothing about execution either way, because the engine builds its
  Truffle `Context` lazily inside `execute`. The registration itself has landed — `SkillDefinition` and
  `SkillMetadata` are entries in `builder-native-hints/reachability-metadata.json`, with the reason the
  tracer missed them written into that file's comment. The check has since been run, so the error above is
  closed rather than open: an image built from this tree by `scripts/native.sh` takes
  `smith skill-run --skill-dir <published-folder> <request>`, prints `skills loaded: <name>`, answers the
  request, exits **0**, and logs **0** occurrences of `Unresolved class`. Two further entries were needed
  beyond those two, and both were found by running the binary rather than by reading it.
  `com.embabel.agent.skills.Skills` itself: with `skills.spec` registered and this line absent, the image
  loads the folder and prints the loaded line, then dies one frame later at `Unresolved class: class
  com.embabel.agent.skills.Skills`, through `SkillRunCommand.call → AiSpecPromptCaller.call →
  PromptRunner.withReference → Skills.tools → Tool.fromInstance → MethodToolFactory.fromInstance`, which
  enumerates the reference's declared members over `KClass.memberScope` to build its tools. And
  `SkillRunCommand`'s two option fields: `addSubcommand` is a compile-time call, so an image missing them
  registers `smith skill-run` and then rejects its own arguments with `Unknown options: --skill-dir` and a
  usage line of `Usage: smith skill-run` — the same picocli rule as `BuildCommand.flow`, on a subcommand no
  trace ever ran. Neither gap could have surfaced before this change, because with the engine off nothing in
  `prod/` constructs a `Skills` object to hand to `tools()`. Two qualifiers on that measurement. The toolchain
  was `25-graalce` (GraalVM CE 25+37), not the `25.0.2` the workflow pins — the script says so on its own
  output rather than hiding the drift — so the entries are proven on that build, and CI's patch line is a
  build away from confirming the same. And this is the engine-absent case: the run's own loader logs `Skill
  '…' contains scripts, but script execution is not yet supported`, which is the AOT freeze above doing
  exactly what it predicts. A *callable* script tool inside an image still needs one built with the switch
  baked in, and stays unmeasured.

- **Open — the agents-pack→skill seam has no caller outside its own test.** The seam is built and behaves:
  `skill/SkillBuilderService.buildFromAgentsPack` derives one stage description per member agent from that
  stage's own steps, runs the existing frontmatter, inspection, eval and lift gates through `build`
  rather than adding a second set, and ends the run at the first stage a gate refuses. What it does not
  have is a route: no CLI flag, no studio body and no `smith skill-run` path reaches it, and
  `builder-skill-from-process` is named nowhere in `src/main` (the pack-lifecycle item above). So the
  capability's "a named caller reaches it, not a method only its own test invokes" half is unmet while
  its behaviour half is met. Check: `grep -rn 'buildFromAgentsPack' prod/*/src/main` returns the
  declaration in `SkillBuilderService.java` and nothing else — one line, which is the whole finding.

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
- **`--flow` is inert under `--target skill`.** The skill pipeline never consults
  `BuildCommand.generators()`, so `--target skill --flow pack` (or `agentic`, or `harness`) runs the
  skill generator and names no flow at all — `SkillBuildResult` carries no flow to print, unlike the
  agent-spec report. Pre-existing for the two pack flows and
  unchanged by `builder-as-agent-harness`; a named refusal there is a small `BuildCommand` change, and
  the alternative is a flag that lies for one of the two targets.
- **The harness flow still receives the assembled corpus as its description on `--docs` builds.** The
  pack's steps read the roots themselves, but `BuildCommand.resolveDescription()` hands over the
  assembled text as well, because the second candidate's generator needs it in hand. Removing the
  handover is a change to the dual-candidate path with its own measurement, not a side effect of the
  pack — see design D2 of `builder-as-agent-harness`.
- **Both pack-discovery tools fail on every call when `--out` has not been created yet.** A first build
  against a fresh `--out` is the normal case, and `tools/PackLibraryToolGroup` resolves its pack root from
  that folder, so `list_packs` and `rank_packs` each return
  `SpecSourceException: folder source root does not exist or is not a directory`
  (`prod/embabel-smith-specsource/.../FolderSpecSource.java`) in zero milliseconds. Measured in one agentic
  build: 90 of its 386 tool calls were this one failure — 51 `list_packs`, 39 `rank_packs` — every call the
  step made to look for prior work. It is not fatal, which is the problem: the step carries on and names
  directories it invented instead, so the cost is invisible in the verdict. Either the build creates its
  pack root when the tool surface is installed, or the tools answer "no packs yet". Check:
  `grep -c 'folder source root' <build.log>` against the `calling tool list_packs` count in the same file.
- **Run-scoped dedupe is confirmed on a live build, and it is carrying much of the call count.** The
  promise `builder-as-agent-harness` design D2 makes — an identical repeat answered from the first call,
  cleared by `startRun()` so a second build cannot be answered from the first build's files — is not only
  unit-tested. One agentic `TradingAgents` build logged 386 tool calls and 273 lines reporting a repeat
  answered from an earlier identical call in the run (`read_doc` 217, `list_docs` 49, `assemble_corpus` 7;
  the 56 lines short enough to carry a duration say 0 or 1 ms), and its reproduction five hours later
  logged 299 of 440 (239 / 47 / 13). Two readings, both wanted: the mechanism works, so a loop that
  re-reads a file pays for it once; and a step spends most of its loop asking again for something it
  already holds, which means a call count from a parity run overstates how much the step learned. Check:
  `grep -c 'identical to an earlier call' <build.log>` against the `calling tool` count in the same file.
- **Two GraalVM version lines are live at once, on purpose, and a third is open.** The builder pins its
  skill-script engine's `org.graalvm.polyglot` pair to 25.0.2 in its own pom while the root's
  `graalvm.version` stays 25.4.4.1.1, because 25.4.4.1.1 fails `native-image` inside
  `TruffleBaseFeature` with a `NoSuchMethodError` on `OptimizedTruffleRuntime.getLoopNodeFactory()`, and
  `poc/` is frozen evidence measured on the newer pair. What is not settled is the toolchain itself:
  `scripts/native.sh` installs sdkman `25-graalce`, which reports GraalVM CE 25+37.1 — the JDK 25.0.0
  line — while `.github/workflows/native-image.yml` pins `GRAAL_VERSION: '25.0.2'`. The pinned classpath
  built and booted on the 25.0.0-line distribution, so nothing is broken today; what is open is that a
  local image and a CI image are not built by the same toolchain, so a local `make native` pass is not
  the same evidence as the CI one. Aligning them is a `scripts/` change. Check:
  `grep -n 'sdk use java' scripts/native.sh` against
  `grep -n GRAAL_VERSION .github/workflows/native-image.yml`, and
  `grep -n graalvm.polyglot.version prod/embabel-smith-builder/pom.xml`.

- **Open, by choice — the flow prompts still emit `"toolGroups": []`.** `process_to_flow.jinja:32` and
  `process_to_flow_multi.jinja:44` show a drafted step with an empty tool-group list, so a model that
  follows the example literally produces a step with no reach even where the shipped packs declare
  roles. It was left deliberately, not missed: the shipped packs carry the declarations, and a drafted
  spec naming a role the platform cannot resolve is now refused at deploy
  (`eval/AgenticAgentSpecGenerator#refuse`) instead of deploying inert and silently. Widening the
  example is a prompt change on a surface a candidate may rewrite, so it wants evidence rather than a
  tidy diff. Check: `grep -rn '"toolGroups"' prod/embabel-smith-builder/src/main/resources/prompts/`
  returns both lines.
- **Open, deferred — `--extract` is a CLI flag only.** Document ingestion is reachable from the studio's
  discover and extract endpoints, but a studio `generate` request cannot ask for the extract-then-build
  composition that `smith build --docs … --extract` performs; the UI reaching extraction directly is the
  reason it was deferred rather than built. Check: `grep -n 'extract'
  prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/rest/dto/GenerateRequest.java`
  returns nothing, while `grep -n 'extract' prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/cli/BuildCommand.java`
  returns the flag.

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
  `:194-208`), but no pack reachable by a shipped path has that shape: nine of the twenty-four
  `types/*.yml` under `prod/` are single-own-property and all nine are in the six builder packs — the
  only other single-property files are `embabel-smith-runtime`'s own test packs — no `prod/` Java file
  names the builder's pack directory at all, the packs being reached through `embabel-smith-builder`'s
  `core/BuilderPackPaths` — while the runtime's own library packs declare three-property types and the
  builder's published output declares none, both of which `String.valueOf` renders correctly. Nothing
  re-parses the value either: every consumer of `RunOutcome.output()` is display (`RunController.java:53`,
  `SimulateResponse.java:35`, `cli/RunCommand.java:70`, `RunBackedA2ARequestHandler.java:277`), and the
  runtime's README documents the `{topic=…, audience=…}` rendering as intent. Worst case is a run whose
  output reads oddly on screen, not a failed parse. The reason it stays a note is the cost of a guard: the
  runtime has no offline seam for `outcome()` — `RuntimeCoreTest` autowires a real `AgentPlatform` against a
  fake endpoint — so a test would be a new mock-platform seam built to exercise a branch nothing reaches.
  Check: `grep -rn "builder-agent-specs" prod --include=*.java | grep -v embabel-smith-builder/` prints
  nothing, and `grep -rl "builder-agent-specs" prod --include=*.java | wc -l` prints 0 over 0 matching
  lines, and the repo-wide `git grep -c builder-agent-specs -- '*.java'` prints nothing either — the
  string survives in no Java at all, `poc/poc-27-recursive-self-improvement`'s
  `outerloop/BuilderPacks.java:44` included, where the constant now names
  `prod/embabel-smith-builder/src/main/resources/specs` — the single-own-property counter
  `for f in prod/embabel-smith-builder/src/main/resources/specs/*/types/*.yml; do [ "$(awk '/^ownProperties:/{o=1;next} /^[a-z]/{o=0} o&&/^  - name:/{c++} END{print c+0}' "$f")" = 1 ] && echo "$f"; done | wc -l`
  prints 9 across six packs (8 across five at `1c2455b^`, the commit that added `builder-harness`), and
  `grep -n "String.valueOf" prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/{core/RuntimeCore,trace/TraceRecorder}.java`
  still prints three lines — the rendering is unchanged, and the counts are the unreachability.

- **Open — the CLI never says which planner ran.** The runtime resolves one on every run
  (`core/PlannerResolver.java:25`: the run's override, else the pack's `manifest.defaultPlanner`, else
  GOAP) and carries the resolved name on `RunOutcome.plannerName` into the trace record
  (`trace/TraceRecorder.java:128-130`), so precedence *is* recorded. It is not printed:
  `cli/RunCommand.java` emits only the run's output or its error, which leaves a `--planner` flag
  indistinguishable from the pack's default in stdout. Measured 2026-11-28 on the generic pack: the
  four-strategy sweep contains one `[smith-tools]` line per leg — the addon's boot line — and the only
  planner text anywhere in the logs is the platform's own logger names, and a `--planner HYBRID` leg
  against a pack whose manifest named a `defaultPlanner` behaved exactly like that strategy's other
  legs without saying the override had applied. Check:
  `grep -rn "plannerName" prod/embabel-smith-runtime/src/main --include=*.java` prints four lines, all
  producers (the record's javadoc, its component, the trace call), and
  `grep -n "System.out\|System.err" prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/cli/RunCommand.java`
  prints four lines, none of them naming a planner — the name exists to be printed and never is.

- **Open — a run stopped by a wall clock prints no report at all.** The report is emitted after the
  run returns (`cli/RunCommand.java:75-85` prints the output, the notes, or the error), so a process
  ended by `timeout` leaves nothing behind. Measured 2026-11-28: the per-pack note that a pack
  `carries scripts, not executed` appears seven times — once per definition — in every leg that
  terminated, including the leg that refused on its trust tier before executing anything, and zero
  times in the three legs the 420 s cap killed. The most expensive failure the runtime has — a step's
  tool loop burning its 60 s interaction timeout ten times — is the failure the CLI documents least.
  The shape is visible without the logs: `grep -n "System.out\|System.err" prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/cli/RunCommand.java`
  prints four lines, all of them reached only after `AgentSpecRunService.run` returns, and
  `grep -rn "Runtime.addShutdownHook\|addShutdownHook" prod/embabel-smith-runtime/src/main --include=*.java`
  prints nothing — no hook writes a partial report when the process is signalled.

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

  **Closed 2026-11-23 — the validator-inspects-nothing half.** `AgentSpecValidator` still reads no
  `toolGroups`, and that is now the right division of labour rather than the gap: a structural validator
  has no registry to resolve against, so resolvability belongs to the deploy that boots one.
  `eval/AgenticAgentSpecGenerator#refuse` does exactly that on the agentic route — every group a step
  declares against `availableToolGroups()`, refused before any model call — and
  `tools/ExposedToolGroupsTest` pins the resolvable set itself. What is *not* closed is the same check on
  the eval and improvement deploys: `eval/AgentSpecInvokerFactory` is shared with those paths, whose
  machine-generated specs declare nothing, and `eval/ToolGroupBindingTest` pins that a misspelled group
  deploys verbatim through it. The scope builder's `List<ToolGroupDescription>` argument is still not the
  route; nothing on the step-spec deploy path reads the context's tool-group list except
  `DefaultNaturalLanguageCompiler`, which belongs to the other generation technique.

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
  Still open inside the fix at the time it was written — **closed 2026-11-23** — nothing deduped a repeat,
  so a model that re-sends the same draft spends the same call twice (19 calls on one input that never got
  an answer at all). `tools/ToolSurface#remembered` now answers an identical repeat from the first call,
  keyed on the tool name plus its arguments and cleared by `startRun()` so a second build in one process
  cannot be answered from the first build's files; a call that differs in any argument runs again, which is
  the half that keeps paging a document from being answered with its first page.
  `BlueprintToolGroupTest` asserts both halves, and keying the memo on the tool name alone fails the
  second. Closed 2026-11-22 — the
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
  `grep -rn "toolGroups:" prod/embabel-smith-builder/src/main/resources/specs/*/steps/*.yml | grep -v "\[\]"`
  prints seven lines — the seven steps that declare a role at all, across four packs — of which the two
  above are the ones naming `validate` alone. The other eleven of the eighteen step files carry
  `toolGroups: []` on the key's own line, which is what the `grep -v` drops.
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
  Check: `grep -rl '"json"' prod/embabel-smith-builder/src/main/resources/specs/*/steps/*.yml` prints three
  files, one per shipped pack flow.
- **Open — the other three builder packs have the same gap and no fix.** Every pack's goal output type is a
  single-property value type — `builder-single-spec`, `builder-spec-one-shot`, `builder-agentic-blueprint`
  and `builder-harness` all declare `AgentSpecJson`, `builder-process-composition` declares `ProcessJson`,
  `builder-skill-from-process` declares `SkillBriefs` and `BlueprintBrief`, each with one own-property — and
  only the three steps on the shipped pack-flow paths name their envelope key. `assembleAgentSpec`,
  `composeProcessJson` and `writeSkillBriefs` still show the document as the reply, so a deploy of any of
  those three carries the same two-shape prompt collision that cost the pack flow its builds. Nothing
  exercises it: `--flow pack` deploys `builder-spec-one-shot` alone, `--flow agentic` deploys
  `builder-agentic-blueprint` alone, `--flow harness` deploys `builder-harness` alone, and
  `builder-process-composition` and `builder-skill-from-process` are named nowhere in `src/main`.
  Deliberately not fixed under it: three step
  prompts is three more pieces of load-bearing mutable prose with no gate, which is the item above.
  Check: `grep -rl '"json"' prod/embabel-smith-builder/src/main/resources/specs/*/steps/*.yml` prints three
  paths — `draftAgentSpec.yml`, `draftAgentSpecFromBlueprint.yml` and `draftAgentSpecFromRoots.yml` — and
  `grep -l outputTypeName prod/embabel-smith-builder/src/main/resources/specs/*/goal.yml` prints six.
- **Deeper-than-one-level pack nesting in a source** is "a future concern, not a v1 feature".

- **Open — no validator looks at a manifest version.** `AgentSpecManifest` documents a version as
  required and its two-argument constructor takes one, but nothing enforces it: the group-1 checks have
  no version code, so a manifest that omits the key validates cleanly and reaches publish as `null`,
  where the studio store substitutes `PackEntity.DEFAULT_VERSION`. The fallback is therefore load-bearing
  rather than defensive, and the two places that apply it (`store/ProcessStore`, `storage/PacksKeyMigration`)
  must keep agreeing. Reading a published skill's `metadata.version` from `SKILL.md` was left out of
  scope by decision, not by oversight. Check:
  `grep -c version smith-common/src/main/java/com/embabel/smith/common/validation/AgentSpecValidator.java`
  prints `0` — the validator never mentions versions — and
  `grep -n 'DEFAULT_VERSION' prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/storage/PacksKeyMigration.java`
  returns the fallback's second home.

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

- **Open — POC 27's outer loop is unmeasured, and one of its gates still reads the looser arm.** The
  rules the experiment settled are durable and now live in
  [`10-self-improvement.md`](10-self-improvement.md) §Before a paid run means anything; what is not
  settled is the measurement. Three things remain: the paid outer loop never produced a single scored
  candidate (its two named causes are answered inside the tree — the candidate is the builder's own
  shipped pack with one step prompt mutated, and the headroom tasks sit beside the frozen six because
  the frozen file count feeds the spend ceiling); the disagreement-excluding **deciding** arm is computed
  and reported by `baseline/Baseline` (`decidingScore`, `excludedTaskIds`) but the gate and the
  task-7.4 delta still read `baselineScore`, the primary substring field, so the excluded-task rule is
  not wired end-to-end; and live `Ai` bean resolution in that module is still unproven. Check:
  `grep -rn 'baselineScore' poc/poc-27-recursive-self-improvement/src/main/java/com/embabel/smith/selfimprove/outerloop/`
  returns the read at `OuterLoopProcess.java:274` and no use of `decidingScore`, and
  `grep -n 'PRIMARY_IS' poc/poc-27-recursive-self-improvement/src/main/java/com/embabel/smith/selfimprove/baseline/Baseline.java`
  finds the field that says the gate reads the primary arm.

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
  `skill/ScorerPromptBoundaryTest` keeps the three scorer prompts out of the builder packs under `specs/`, and its
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
  `grep -rn '@Tag("live")' prod/*/src/test | grep -v '\*'` prints one line per real live test — the
  plain `grep -rln` form over-counts by two builder files that name the tag in a comment explaining why
  they do not carry it — and it is written
  without an expected count on purpose, because this item said "exactly those four files" until the a2a
  surface added a fifth and the builder's pack, tool-group and studio key-migration work added three
  more, which is the rot the check was supposed to catch and did not, because the
  check itself went stale with the claim. `grep -n 'does not prove'
  docs/05-architecture.md` finds the statement, and `grep -n 'make test'
  .github/workflows/offline-verification.yml` finds the target that runs them.

## Re-creating a planning change

The planning trees have been consolidated into these pages and removed **twice**. The first purge, on
2026-11-15, took 108 files. Archiving completed changes with plain `openspec archive` then regenerated
capability specs under `openspec/specs/` as it promoted each change's delta into them, so by 2026-11-25
the trees held 140 files again — 25 capability specs and 20 archived folders, nothing active — and were
folded into these pages and purged a second time. The workflow is still kept: `openspec/config.yaml`,
`openspec/specs/.gitkeep` and `openspec/changes/archive/.gitkeep` remain, and
`make setup` still installs the OpenSpec CLI, so starting a new change is still valid and the capability
specs it would edit are these documents. Expect to rewrite this section after any batch of archives
rather than believing the purge was one-off: the archiving route the project chose (`openspec archive`,
not `--skip-specs`) refills `openspec/specs/` by design. The check for whether a purge is owed is
`find openspec/changes openspec/specs -type f | wc -l` — 3 is the clean state (the config and the two
placeholders), and anything larger means rationale has accumulated outside these pages again. Whatever
open work the folders hold is copied into the list above before they go, as the four unchecked boxes
were on 2026-11-25.

One piece of tooling knowledge went with the trees and is recorded here because nothing else keeps
it: capability *renames* cannot be expressed as deltas — a delta operates on requirement names and
must not move or rename a capability — so the 2026-11-11 vocabulary rename was applied as direct
edits, with renames ordered before archiving so archiving could not create incomplete capability
files.


## Misc

Closed — **a2a operations on the runtime module** (was: Send Message, Send Streaming Message, Get
Task, List Tasks, Cancel Task, Get Agent Card, on the spec
https://a2a-protocol.org/latest/specification/#11-key-goals-of-a2a). They are served by the starter
library `prod/embabel-smith-a2a-starter`, hosted by `prod/embabel-smith-studio` when a run sets
`smith.a2a.enabled` — `make run-a2a`, `POST /a2a`, card at
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
  `grep -rl '<artifactId>embabel-agent-starter-a2a</artifactId>' prod/*/pom.xml` returns the one
  declaring module (`embabel-smith-a2a-starter`; the studio declares that starter itself, and the
  runtime cannot — the starter depends on it), and
  `grep -n 'Non-goal: protocol 1.0' prod/embabel-smith-a2a-starter/README.md` finds the open half.

- **Open — a JVM-resident Python for skill scripts.** The process engine resolves each language to
  interpreter commands, so a Python that lives inside the JVM is one map entry away; where a host has
  no CPython on PATH, either candidate below makes `python` skill scripts runnable on the JVM alone.
  The two candidates are opposite in shape. **jython-slim** (the pip-distributed Jython build) is one
  small jar and no machinery, but Jython tops out at **Python 2.7 syntax**: a skill's `.py` script
  that uses f-strings, walrus assignments, or `pathlib` runs under CPython and dies under jython-slim,
  so publishing such a script to a jython-capable host is a silent portability cliff and the authoring
  rule becomes 2.7-safe (`.format()`, `os.path`, `print` as a function).
  **`org.graalvm.polyglot:python`** (GraalPy, pom-type; 25.6.5.1 is on Central) runs
  Python-3.11-class syntax, so that cliff disappears — and its costs are elsewhere. The pom pulls the
  graalpy + truffle dependency set into whichever module declares it, a second embedded language
  beside the GraalJS the builder and studio already carry at a pinned `graalvm.polyglot.version`
  (25.0.2 in both poms), and that cohabitation carries a measured warning: GraalJS 25.4.4.1.1 broke
  `make native` where 25.0.2 builds. A GraalPy line therefore has to move **together with** the
  existing pin, as one polyglot version set, and re-pass the native gauntlet — it cannot sit beside
  the 25.0.2 pin at 25.6.x. A new embedded language also needs its own native-image reachability work
  beyond what GraalJS needed. The wiring decision is the same either way: which interpreters the
  language map lists per host, and whether scripts are linted against the chosen interpreter's
  ceiling before that choice matters. Check:
  `javap -p -cp ~/.m2/repository/com/embabel/agent/embabel-agent-skills/1.5.4/embabel-agent-skills-1.5.4.jar com.embabel.agent.skills.script.ScriptLanguage`
  names the languages the map keys over.
