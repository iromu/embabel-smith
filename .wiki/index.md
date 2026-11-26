---
title: "Embabel Smith Wiki"
type: "index"
status: "active"
language: "default"
last_commit: "e57bbbe5ab46cbe671639a247c34c41d2cabbd02"
updated_at: "2026-11-26"
---

<!-- Incremental ingest 2026-11-26 (b): the agents-pack rename at `e57bbbe`. Drift since `9c5ce18` is 54
tracked files — `77a2b98`'s README rewrite plus one vocabulary commit that renamed the multi-agent
artifact: `ProcessSpec` and its `Process*` satellites to `AgentsPack*`, four methods, 21 files moved on
disk. Twenty of 47 pages carried drifted `source_paths`; three passes over disjoint page sets read the
sources at HEAD and corrected names, moved paths and line citations. Beyond the rename the pass fixed four
things that had drifted on their own: five citations in `spec-security-scanning.md` (the scanner file is
byte-identical between the checkpoint and HEAD, so those predate this commit), a `mvn -q verify` CI claim
in `model-endpoint.md` for a Makefile target that does not exist, an off-by-a-column hint recount in
`native-binary.md` (92 `*Test` types, 88 carrying a method list, 47 naming test methods verbatim), and the
second dangling pointer `19e87da`'s README move left in `build-tooling.md`. Names that did NOT move, and
are not drift: `process.yml` with its `process`/`version`/`agents` keys, the `@LlmTool` tool name
`validateProcess` with the `process` role and `processJson` argument, `AgentsPack.process()`,
`ProcessStore`, `ProcessExtractor`, and POC 24's own `gencmp.ProcessSpec`. Lint: 525 `source_paths` with
0 dead, 0 orphan pages, 0 pages carrying `status: "stale"`, 0 duplicate headings; the single link the
checker flags is a shell regex inside a fenced block in `build-tooling.md`, not a wikilink. `last_commit`
advances because the changed set was processed in full, not scoped. GitHub Pages re-publish remains OWED
(`BLOCKERS.md` §1) and was not attempted.

<!-- Full ingest 2026-11-26: 164 tracked files across 6 commits since `f96ecbe`, partitioned four ways
across disjoint page sets — the builder's flow and skill surfaces, the runtime with its script hosts and
the A2A surface, the seven overview pages, and the concept/risk tail — this page held by the pass itself.
Twenty-one pages rewritten, none added: 47 files, 46 cross-linked pages. What the diff *falsified*, not
merely added to. (1) `19e87da` folded the OpenSpec planning trees into `docs/` and deleted them, which
turned every `openspec/...` citation into a dead path. At HEAD `openspec/` holds `config.yaml` and two
`.gitkeep` placeholders, [[product-phase]] owns that account, and no page cites the trees any more. The
`design D1`–`D8` labels those trees defined are the residue this pass could not repoint: the rules they
name now live in `docs/05-architecture.md` §Cross-cutting invariants and the per-area pages, so the labels
used below and in eight pages read as historical numbering, not as a lookup. (2) `9c5ce18` made
`README.md` an overview and moved its run book, which falsified the three pages that pointed at it for
detail: the module table is gone, so `docs/05-architecture.md` is the module map — [[architecture]] and
[[product-phase]] now say the README carries only prose, and [[getting-started]] sends the per-POC run
commands to `docs/04-poc-run-results.md` §8 and the build entry points to `docs/05`. (3) `740d5a6` took
the conversational voice out of five `docs/` pages and changed no behaviour; `0e30d09` is a lockfile
refresh; `b973e91` and `54da618` are the previous ingest. Two findings this pass recorded and did not
fix, both repo-side: `run-loop.sh:8,26` still points at the README section `9c5ce18` deleted, and
`docs/12-backlog.md:459,471` cite a design D2 in a tree no longer in the repo. Lint after the ingest: 47
pages, 0 broken links, 0 orphans, 0 stale, 0 dead `source_paths`, 0 duplicate headings. GitHub Pages
re-publish is OWED: `wiki-pages.yml` fires on pushes to the default branch touching `.wiki/**`, and these
edits are on `poc/iteration-3`. -->

<!-- Full ingest 2026-11-25: 190 tracked files and 28 commits since `2457aab`, partitioned four ways across
disjoint page sets — the skill scripts and their JavaScript surface, the A2A starter with the native route
and the studio host, the builder's pack placement and flow surface, and the docs slice with the
overview/concept tail — this page and [[architecture]] held by the pass itself. Eighteen pages touched, one
added: 47 now. What the diff *falsified*, not merely added to. (1) The published skill folder has a host:
`skill/PublishedSkillHost` points Embabel's `withLocalSkills` at the folder `publish` wrote and
`cli/SkillRunCommand` is the route that attaches it to a call, so the absence [[skill-scripts-graaljs]]
recorded is closed rather than half-filled. A script tool is also now *offered* to a model, proven offline,
which retires the UNPROVEN sentence that ended [[skill-output-target]]:
`skill/PublishedSkillHostModelCallTest` puts `smith_published_skills_…` in the first request's tool list and
the script's own stdout in the follow-up, and `skill/EvalRunnerScriptArmTest` does the same on the with-skill
eval arm. (2) The durable statement arrived: `docs/13-skill-script-javascript.md` and the promoted
`skill-script-hosting` spec (four requirements), with `builder-skill-script-host` archived the same
day. Planning counts are now 25 specs / 20 archived / **0 open**, so [[what-the-pocs-prove]] no longer has a
change open repo-wide and [[product-phase]]'s tally moved. (3) A skill pack ships inside `prod/` for the first
time — `prod/embabel-smith-builder/src/main/resources/skills/skill-card-generator/`, three `scripts/*.js`
behind `references/` and `evals/` (`49a0298`) — which is what makes the two-host portability question live
rather than hypothetical. (4) The builder's pack address settled everywhere: all six packs sit under
`src/main/resources/specs/`, the pack-driving generators call `builderPack()` over
`builder/core/BuilderPackPaths` into `smith-common/io/SpecFolders.classpath` (classpath, then
`smith.specs.dir`/`SMITH_SPECS_DIR`, then `specs/` beside the program) instead of holding a `Path.of`
constant, the native build ships them beside the image (`pack-specs-beside-binary` → `target/specs`, asserted
by `.github/workflows/native-image.yml`), and even POC 27's `selfimprove/outerloop/BuilderPacks.PACK_ROOT`
names the new path. The two full-ingest notes below that assert HEAD has no `src/main/resources/specs/`, no
`BuilderPackPaths` and no `SpecFolders`, and that `poc-27` still points at the directory the packs left, are
history from here, not drift. (5) The loop-rewrites-the-deployed-copy consequence closed:
`builder/improve/BuilderPackSurface` with `BuiltPacksMatchTheSourceTest`, the studio's improve source
defaulting to it. (6) The A2A surface is a starter library hosted by the studio with no launcher anywhere;
`a9ab38b` fixed the last stale module name, which sat in `NoA2aEndpointTest`'s javadoc, and the starter's
`EnvironmentPostProcessor` carries two different precedences — the Embabel `AgentA2AAutoConfiguration`
exclusion contributed always, the servlet-plus-18090 defaults only under the flag and with `addLast`.
(7) `scripts/native.sh` defaults to GraalVM `25.0.2` with sdkman's rolling `25-graalce` as the hints-script
candidate, and `native-image.yml` pins the same `GRAAL_VERSION`. New page: [[skill-script-javascript]] — the
surface both hosts give a script, the four budgets, and the nine rules that follow. Unmeasured, and the pages
say so: a live A2A run through the surface, and a native image for the starter. Found and not touched:
`docs/13-skill-script-javascript.md`'s engine arm of its both-host check runs a `Harness.java` that is
committed nowhere — the committed engine-arm check is `skill/script/SkillCardGeneratorScriptsTest`. Dated
notes below are superseded rather than rewritten, including the launcher sentences, which recall a module
that existed when they were written. Lint: 47 pages, 0 broken links over 583 refs (the single grep hit is a
`grep -cE` bracket expression inside a code span in [[build-tooling]]), 0 orphans once this page lists the new
one, 0 duplicate headings, and 0 dead `source_paths` of 530 at HEAD. `last_commit` advances to `f96ecbe` —
this is a whole-tree pass, not a delta. GitHub Pages re-publish remains OWED: it needs a push to `main` or a
`workflow_dispatch`, and a push needs the user's word. -->

<!-- Targeted ingest 2026-11-25 (the A2A rename, two commits, superseding the note below): `58f9075`
renamed `prod/embabel-smith-runtime-a2a` to `prod/embabel-smith-a2a-starter` — directory, artifactId and
`com.embabel.smith.runtime.a2a` to `com.embabel.smith.a2a`, the FQCNs in `AutoConfiguration.imports` and
`spring.factories` moved with them — and deleted `prod/embabel-smith-a2a-launcher` outright, a starter
that needs a launcher to be used having not finished its job. `2ad0962` is the docs pass that carries the
settled wording (`docs/05-architecture.md`, `docs/07-runtime.md`, the `runtime-a2a` spec), quoted rather
than re-derived. The host is now `prod/embabel-smith-studio`, which declares the starter in its own pom,
and `make run-a2a` starts that studio with `smith.a2a.enabled=true`, `SPRING_PROFILES_ACTIVE=a2a` — a
profile with no config file, keeping the default `local` profile and its Docker Postgres out of a surface
needing no database — and `SERVER_PORT`, 18090 by default because the studio's own `application.yml`
resolves `${SERVER_PORT:8080}`. What the launcher supplied now comes from `SmithA2aEnvironmentPostProcessor`:
the Embabel library's `AgentA2AAutoConfiguration` exclusion is contributed always, and only when the flag
is `true` does one source add servlet mode and the `18090` default, added with `addLast` — deliberately the
lowest precedence — so the host's own files, `SERVER_PORT` and `SPRING_APPLICATION_JSON` all outrank it and
a host whose own file says `none` keeps `none`. Who can host it is forced rather than chosen: the starter's
`A2aConfiguration`, `RunBackedA2ARequestHandler` and `SpecSourceSkills` import `AgentSpecRunService`,
`TrustTier`, `TrustTierPolicy` and `JsonlTraceStore` from the runtime, so a runtime-to-starter edge is a
Maven cycle no scope hides, and the CLI boots non-web, closes its context and exits anyway. The spec's
SHALL therefore reads that no process exposes an endpoint unless the surface is explicitly enabled there,
the studio guarded both ways by `NoA2aEndpointTest` and `A2aEndpointWhenEnabledTest`. Unchanged: five
operations over protocol 0.3, the trust-tier gate, the trace record, and the unauthenticated
local-development posture. Measured: 52 starter, 83 runtime and 113 studio tests, 0 failures, and
`SMITH_A2A_SOURCE=… make run-a2a` booted Tomcat on 18090 and answered the card route 200 with
`protocolVersion 0.3.0` and skills from the configured source. Not measured: a live A2A run through the
surface, and a native image for the starter. Pages touched: [[a2a-endpoint]] (its `source_paths` named the
old module and both launcher files, the trap that made a launcher's own `application.yml` the servlet-mode
statement, the run instructions), [[architecture]] and [[product-phase]] (module listings with a launcher
in them), [[build-tooling]] (the `run-a2a` row), [[smith-runtime]] (the third-door sentence),
[[skill-scripts-graaljs]] (one dead path) and this page's §6 and FAQ row. The capability
`runtime-a2a` keeps its name, as do the dated notes below that recall the launcher
existing. Lint: 46 pages, 0 broken links, 0 orphans, 0 duplicate headings, and 482 `source_paths`
entries across the wiki all resolve at HEAD — 0 dead, including the 85 on the seven pages touched (the
note below counted four dead directory entries elsewhere; none remains now). `last_commit` deliberately
NOT advanced; `updated_at` moves to today because the pages changed. GitHub Pages re-publish remains
OWED — it needs a push, and a push needs the user's word. -->

<!-- Targeted ingest 2026-11-25 (the A2A split, two commits): the pack-address pages were repointed
earlier today (`c2280bd`) and the A2A split landed underneath that, so the launcher wording is repointed
here rather than rewritten. `d437722` made `prod/embabel-smith-runtime-a2a` a starter library and moved
the servlet host out to the new `prod/embabel-smith-a2a-launcher`. The library registers
`SmithA2aAutoConfiguration` through `META-INF/spring/…AutoConfiguration.imports`, conditional on the A2A
classes, a servlet web application and `smith.a2a.enabled=true` with no `matchIfMissing` — so depending
on it opens no endpoint and no port, and the launcher is the only place that flag is set. The
system-property story the pages carried went with the module that carried it: the launcher states servlet
mode, `port: ${SERVER_PORT:18090}` and the flag in its own `application.yml`, the file Boot finds first
for that candidate — the distinction that lets a launcher make that choice and forbids a library from
making it (`docs/05-architecture.md` states it; quoted here, not re-derived). A library cannot say
`@SpringBootApplication(exclude = …)`, so `SmithA2aEnvironmentPostProcessor`, registered in that module's
`spring.factories`, appends the Embabel library's `AgentA2AAutoConfiguration` to
`spring.autoconfigure.exclude`, which keeps the ambiguous `/a2a` card mapping closed even in a host that
never opted in. `make run-a2a` now targets the launcher. Unchanged: `prod/embabel-smith-runtime` still
boots non-web and opens no port, the studio still serves no A2A (`NoA2aEndpointTest` passes), and the
surface is still five operations over protocol 0.3. Measured: the enabled launcher answers the card route
200 with `protocolVersion 0.3.0` and skills from the configured source; with the flag off the registrar
logs zero endpoints and both the card route and `POST /a2a` answer 404. Not measured: a live A2A run
through the launcher, and a native image for it. Pages touched: [[a2a-endpoint]] (two `source_paths`
naming files `d437722` deleted, and the trap that had `main()` forcing servlet mode), [[build-tooling]]
(the `run-a2a` row's `-pl` path), [[smith-runtime]] (the third-door sentence), [[architecture]] and
[[product-phase]] (module listings with no launcher in them). Lint: 46 pages, 0 broken links over 558
refs — the one grep hit is a regex inside a code span — and 0 dead `source_paths` across the five pages
touched; 4 remain elsewhere, all directory entries predating this pass. `last_commit` deliberately NOT
advanced; `updated_at` moves to today because the pages changed. GitHub Pages re-publish remains OWED —
it needs a push, and a push needs the user's word. -->

<!-- Targeted ingest 2026-11-24 (fourth pass, same change, the leg the note below left open): the execution
claim now has a deterministic proof, so it no longer rests on a live endpoint.
`skill/PublishedSkillHostModelCallTest` boots the builder context against a stub OpenAI-compatible endpoint
on a loopback port, publishes a scripted skill through the real `FolderPublisher`, and answers the first
request with a call for the script tool. Embabel's own loop then dispatches into the engine with no test
invoking a tool by hand, and the assertion is the script's stdout arriving in the follow-up request — which
is what makes it a dispatch test rather than a listing one. Shown non-vacuous the same way as the pair:
script the call for a tool that does not exist and the follow-up carries a refusal naming the real one, so
the stdout assertion fails. That leg also surfaced a naming fact the listing pages had stated one half
short. A script tool's own name is the unsanitized skill-plus-filebase join, but that is not the name a
model is offered: a reference-carrying call puts the *reference's* sanitized name in front of the whole
join, so the shipped form is `smith_published_skills_cold-store-goods-receipt_check-temperature`. The
listing tests never saw the prefix because they read `Skills.tools()` before any naming strategy runs. So
the join has two halves with different hygiene, and a body that names the tool literally is wrong for any
hyphenated skill for a second reason on top of the unsanitized segment. Pages touched:
[[skill-output-target]] (that paragraph), [[skill-scripts-graaljs]] (its coverage list, now four tests on
the reader's half), [[trust-tiers]] (the sentence that delegated the name), `docs/12-backlog.md`. Lint:
46 pages, 0 broken links, 0 orphans, 0 duplicate headings, 0 dead `source_paths`. One lint scare of the pass
belonged to another commit: `d437722` moved the A2A servlet host into a launcher module and left
[[a2a-endpoint]] naming `runtime-a2a`'s application class and `application.yml` — two paths dead at HEAD,
with the repoint sitting uncommitted in this checkout rather than written by this pass. `last_commit` deliberately
NOT advanced. GitHub Pages re-publish remains OWED — it needs a push and a push needs the user's word. -->

<!-- Targeted ingest 2026-11-24 (third pass, one change): `builder-skill-script-host` landed the reader's
half of the skill-script seam, and the pages that recorded the absence were repointed rather than deleted —
the same shape as the `2b16c27` correction two notes below, one half further along. What is now true: two
callers reach `Skills.withScriptExecutionEngine` in `prod/` main, both behind
`smith.builder.skill-scripts.enabled` and off by default. `skill/SkillScriptHost` (build-internal, the
with-skill eval arm) is the one the note below recorded. `skill/PublishedSkillHost` is new: it points
Embabel's own `withLocalSkills` at the directory `publish` wrote — the real folder, not a re-derived copy —
attaches an engine only when the launcher's bean provider offered one, and names each folder the loader
refused together with its reason instead of leaving that in the loader's log. `cli/SkillRunCommand`
(`smith skill-run --skill-dir <dir> <request>`) is the one route that attaches it to a model call, so a
published script does execute in `prod/`, on that route. `SmithBuilderApplication` resolves the engine once
and hands the same value to `build` and `skill-run`; `SmithBuilderScriptEngineWiringTest` boots that
configuration rather than the bean and asserts the pair, and was shown not to pass vacuously by installing
an unconditional engine, which fails both of its tests. What did NOT close, and the pages say it rather
than implying otherwise: the A2A card still publishes `io.a2a.spec.AgentSkill` entries that describe rather
than load, a `smith run` is offered nothing a published folder holds, and with the switch off a published
script is still a file nothing executes. One tier statement did move — `scanSkill` now *reports*
`T3_PARTNER` for a skill that ships JavaScript where it reported `T1_COMMUNITY` for every skill, scriptless
or not, the scriptless skill being the control that still reads `T1_COMMUNITY` — and a tier is a set of
tool-group role names while a script declares none, so that number is the scanner's report and never a gate
on execution. The native half produced the pass's only measurement rather than a restatement, and it took
three images to get. An image with the `skills.spec` entry and nothing else now loads the published folder
and prints `skills loaded: <name>` — the error the note below recorded is closed — and dies one frame later
at `Unresolved class: com.embabel.agent.skills.Skills`, because `Skills.tools()` hands the reference to
`MethodToolFactory.fromInstance`, which enumerates its declared members over `KClass.memberScope`; and
before either of those, the same image rejected `--skill-dir` outright, because `addSubcommand` is a
compile-time call so `smith skill-run` registered and then showed a usage line with no options at all,
picocli having seen no `@Option` or `@Parameters` field. With all three entries the binary loads the folder,
answers the request and exits 0 with **0** `Unresolved class` lines — on `25-graalce` (GraalVM CE 25+37),
not the CI-pinned `25.0.2`, and with no engine inside it: its own loader says `script execution is not yet
supported`, which is the AOT freeze behaving as documented rather than a new gap. A callable script tool
inside an image stays unmeasured. `builder-native-hints` is 76 lines.
Pages touched: [[trust-tiers]] (the caller paragraph and the tier sentence), [[smith-builder]] (§Skill
scripts, and the subcommand count, now two), [[native-binary]] (hint inventory and the skill-path
paragraph), [[skill-output-target]] (its runtime sentence is still true and now says which module it
means), [[skill-scripts-graaljs]] (three new prod test classes, and the mutation that proves the pair).
Lint: 46 pages, 0 broken links over 548 refs, 0 orphans, 0 stale, 0 duplicate headings, and
0 dead `source_paths` at HEAD — the nine the pass first counted were the files this change
adds, and the code commit put them on HEAD before this note landed. `last_commit`
deliberately NOT advanced — this pass covered one change's delta, not the tree. GitHub
Pages re-publish remains OWED and was not attempted: it needs a push, and a push needs the user's word. -->

<!-- Targeted ingest 2026-11-24 (second pass, two commits): the pack move that the pass below left as a
watch item has landed, so its four pack-address pages are repointed. `e04ac04` took
`embabel-agent-starter-a2a` out of `prod/embabel-smith-runtime/pom.xml`, which restores the split the
`runtime-a2a` spec, `prod/embabel-smith-runtime-a2a/pom.xml` and the root pom's own comments already
claimed — that starter is declared by `embabel-smith-runtime-a2a` alone, and it had been reaching
`embabel-smith-studio` through the runtime, where its auto-configuration would have registered `/a2a` on
the studio; `NoA2aEndpointTest` is the guard and it passes. `8ce59c4` moved the six builder packs to
`prod/embabel-smith-builder/src/main/resources/specs/` and replaced each generator's `Path.of` constant
with `core/BuilderPackPaths.directory(<pack>)` over `smith-common`'s `SpecFolders.classpath`, so the launch
directory no longer decides whether a pack loads. The classpath move was ruled *intended*, not an
overreach: the scorer boundary survives as a directory rule (`prompts/` is the referee, `specs/` the mutable
surface) rather than a class-membership rule, which is what [[product-phase]] and [[smith-builder]] now say.
Three consequences stay open rather than settled, and are recorded as open in `docs/12-backlog.md`: the
`smith` binary route, because a native image's flat resource set is one `SpecFolders.classpath` refuses
rather than guesses at; the built-copy-vs-source-tree split an improvement loop now straddles; and
`poc-27`'s `outerloop/BuilderPacks.PACK_ROOT`, which still names the directory the packs left and is frozen
`poc/` evidence rather than a consumer to update. `last_commit` deliberately not advanced — this pass
covered the pack address and the runtime boundary, not the tree. GitHub Pages re-publish remains OWED. -->

<!-- Full ingest 2026-11-24: 128 tracked files and 107 commits since `80ec309`, partitioned four ways —
the builder's flow and tool surface, the skill target and its scripts, the docs plus the eight newly
promoted specs, and the live-measurement delta since the last page-touching commit. Fourteen pages
refreshed, none added: still 46. What the diff *falsified*, not merely added to. (1) The agentic and
harness routes are no longer UNPROVEN for want of a run — six agentic attempts and two harness arms are on
record, none printing a verdict line, so the 2-of-3 bar was missed as **not measured** rather than as a
measured negative, and `--flow java` stays default *not* because a divergence was observed
(`docs/08-builder.md`, [[smith-builder]]). (2) What the parity columns mean is now on the page, because each
of the four was being over-read: `answered` is an action-level line, `spent` is a line count over two
patterns, `offered` does not settle a decline, and `called` could not match a snake_case name before
`19df658` ([[build-tooling]]). (3) `BuildCommand` takes `(platform, ai, surface)` plus a four-argument
script-engine overload, the `newService` cite moved to `:172`, six `ToolGroup` beans resolve the seven roles
with upstream `math` as the control, and **`--flow` is inert under `--target skill`** — `SkillBuildResult`
carries no flow, so a skill build prints no `flow:` line. (4) The skill half filled in: publishing now
writes `scripts/*.js` and the content hash covers them, stage 7's `scanSkill` reads them as code under
`SCRIPT_HOST_ACCESS`/`SCRIPT_NETWORK`/`SCRIPT_PATH_ESCAPE`, and a new `references` stage refuses a skill
whose body names a file the folder does not carry — *after* the eval arms were paid for
([[skill-output-target]], [[spec-security-scanning]]). (5) The `prod/` absence that [[trust-tiers]] recorded
is now half-filled: exactly one caller reaches `Skills.withScriptExecutionEngine`, it is build-internal, and
nothing reads a published folder. (6) The native result is a control-run correction: the switch-**OFF**
image completes the whole skill path and prints a verdict, only the switch-**ON** one dies on
`Unresolved class: SkillDefinition`, so the gap is one bean and not the native skill route — and
`builder-native-hints` is 43 lines, not 22, now that `BuildCommand.flow` and its converter are registered
([[native-binary]]). (7) Six packs, three wired, three naming the `{"json": …}` envelope; the pack flow's
validator call now goes through the run memo (`BuilderPackTools.java:117`), which is what retracted
[[builder-pipeline]]'s "repeats are not deduped". (8) The promoted `process-to-skill-seam` is implemented
and offline-tested but **has no caller** — `SkillBrief` appears in zero JVM sources and `--target skill`
still takes prose ([[skill-generation]]); and of the promoted `builder-agentic-flow` hygiene rules, two are
validator-checkable and the no-`BackgroundMessage` one is **not**, because that type is on the validator's
framework list and only `BuilderAgentSpecPackTest` holds it. Planning counts moved to 24 specs / 19
archived / 1 open. Lint clean: 46 pages, 0 broken links, 0 orphans, 0 stale, 0 duplicate headings, 0 dead
`source_paths` at HEAD — the checker is a script under `.qwen/tmp/` (the skill ships only an export), and
its non-vacuity was proved against a control page that it flagged on all three failure classes. Two things
this pass could not verify from the tree: what a model actually does on the two new routes, which is
endpoint-bound, and whether a script tool is ever *offered*, which is unmeasured rather than answered no.
Method note worth repeating: HEAD moved twice mid-ingest (`fcac3ca` → `2457aab`) and five other sessions
were live in this checkout, so a move of the builder's six packs into `src/main/resources/specs/` sat
**staged and uncommitted** throughout — and it is still staged as this note is written. HEAD has no
`src/main/resources/specs/`, no `core/BuilderPackPaths` and no `SpecFolders`; each pack-driving generator
still names its pack with `Path.of("builder-agent-specs", <pack>)`, and its javadoc says outright that
nothing resolves the packs against the classpath because the mutable packs deliberately live outside
`src/main/resources`. `docs/12-backlog.md` describes the move only inside that same uncommitted edit. So
the pages keep HEAD's path and the move is a watch item, not a documented behaviour: when it lands, the
pack-address sentences in [[smith-builder]], [[generation-approaches]], [[product-phase]] and
[[what-the-pocs-prove]] are the ones to repoint. Found and not touched (repo-side, outside this wiki):
`examples/README.md:87` and `examples/parity-agentic.sh:12` still cite the `builder-executes-its-own-packs`
change folder, now archived; `BLOCKERS.md` §1 still counts 45 pages; and
`builder-skill-script-host`'s `proposal.md` links a folder that moved and calls
`skill-script-execution` an open delta though it is promoted. GitHub Pages re-publish remains OWED
(`BLOCKERS.md` §1 — the latest `wiki-pages.yml` run is still `45057831350`, a failure, and the last deploy
that served was 2026-11-16), so the published site still carries several of the absences these fourteen
pages just retracted. -->

<!-- Full ingest 2026-11-22 (the second today): 120 tracked files changed since the previous
`last_commit` — PR #5 (documents in, gated versioned packs out), the six OpenSpec changes archived and
promoted the same day, and one "update" commit that folded in the then-dirty files. `last_commit` advances
to 80ec309. Eleven pages were refreshed and one added. What the diff *falsified* rather than merely added
to: the build report names no flow at all (`BuildResult` had no flow field then; only the
`--flow pack --technique embabel` refusal printed one), so a run was attributable only to what was typed —
and that unimplemented delta requirement is what kept `builder-executes-its-own-packs` open until the
harness flow's report line closed it (see the 2026-11-23 harness ingest below); `--flow pack`
still spends Java-issued calls, because `newService` wires the process generator regardless of flow; the
pack path then resolved CWD-relative from the module directory and never off the classpath, and that is
still the shipped state at today's HEAD — `prod/embabel-smith-builder/builder-agent-specs/` addressed by a
`Path.of` constant per generator, with a classpath move for it staged and uncommitted somewhere else in
this checkout rather than in any commit this note describes; publishing a held
name **adds a version** (prose: `docs/06-spec-sources.md` §Publishing plus the promoted
`publish-versioning` spec — the wiki's folder-collision bullet understated it); `--technique` is read
before the target dispatch, so a skill build refuses `pack`+`embabel` too; `run-a2a` is no longer the only
target omitting `$(POC_FLAG)`; the Vite dev proxy now targets 8080, not 18080; POC 27's four headroom
tasks sit *beside* the frozen six because ten tasks declare 6600 s against a 5400 s ceiling; and the
planning counts moved to 16 specs / 15 archived / 2 open. `overview/native-binary.md` is new — the
native-image CI had no page at all, and the traced hints it commits carry 99 `*Test` types, 89 of them
with verbatim test-method names. Two claims were already retracted upstream and the pages now say so:
`docs/10` retracts "no candidate changes what `smith build` does", and `docs/08`'s new pack-flow section
closed the last docs gap in `poc27-improves-builder-agentspecs`. Lint clean: 46 pages, 0 broken links, 0
orphans, 0 stale, 0 dead `source_paths`. Found and not touched: `AGENTS.md` and `docs/index.md` still say
44 wiki pages, and `README.md:98` does not list `make native`. HEAD moved twice mid-ingest (d28e6a0 →
80ec309); recomputing coverage against live HEAD is what surfaced the three files no survey had seen.
Republish to GitHub Pages remains OWED — `wiki-pages.yml` fires on pushes to `main` touching `.wiki/**`,
this work sits on `poc/iteration-3`, and the repo does not push by default. -->

<!-- Scoped ingest 2026-11-22 (the studio's Retrieve and Remote panes): two OpenSpec changes landed the
last two POC-only panes — `studio-retrieval-curation` and `studio-remote-bridges`. This ingest is scoped
to what they shipped, so `last_commit` is deliberately unchanged: 30 of the 40 files changed since it
belong to the builder's pack flow and the POC 27 work, not to these panes.
[[smith-studio-web]] gained "Retrieve and Remote: two panes whose logic lives elsewhere" for the parts no
controller shows — the curation rules are POC 18's two (`retrieval/RetrievalCuration.java` drops a
`retired` pack and halves a `degraded` one, then re-orders, because a halved score can sink below the pack
that had ranked under it); scoring sits behind a one-method `SpecScorer` seam with `LexicalScorer` the only
implementation that ships; the ranking, entry-shaping and curation arithmetic all live in the *builder's*
`retrieval/` package and `RetrieveController` holds none of it; and a request names a bridge rather than
supplying an address, which is what keeps `/remote` from being an open proxy. One backlog claim needed
correcting against the code: `docs/12-backlog.md` says "every response names the active scorer", but
`dto/RetrieveResponse.java` documents that field as null on failure, so the page says every *successful*
response does. [[smith-runtime]] gained the four remote shape classes in its `source_paths` plus the two
settings that are fixed rather than configured (3 s to connect, 10 s for the request) and why a
`ConnectException`'s own name is the reason kept. Lint after the edit: 45 pages, 0 broken cross-links, 0
orphans, 0 stale, 0 `source_paths` that do not exist. Republish to GitHub Pages is still owed — it needs a
push, and this repo does not push. -->

<!-- Scoped ingest 2026-11-22 (the builder's pack flow): `smith build` gained `--flow java|pack`, so the
builder's own `builder-spec-one-shot` pack can issue the single generation call — `cli/BuildFlow` plus the
one branch in `BuildCommand.generators()`, with `--flow pack --technique embabel` refused before any model
call.
[[smith-builder]] gained a "The pack flow" section for the three facts that are not visible from the flag:
the goal output arrives as a property map that `eval/AgentSpecInvokerFactory` must read the bound string
out of (the crash class that made the pack flow build nothing on 2026-11-22), the reply envelope
`builder-spec-one-shot/steps/draftAgentSpec.yml` states because nothing validates prose about output shape,
and the re-measured live verdict — pack builds 1 of 3 against java 2 of 3, so the flip's decision stands
and the flow stays non-default. Its falsified clause ("the other two blueprint inputs are being
re-measured") was replaced with the landed numbers, and this page gained the §7 envelope rule and an §8
row. `last_commit` is deliberately NOT advanced: 62 tracked files changed since it and this pass covered
the builder's pack-flow surface only, not POC 27's new headroom tasks, its README reframe, or the
studio→builder test moves. Lint clean: 45 pages, 0 broken links, 0 orphans, 0 stale, 0 dead source paths.
GitHub Pages re-publish remains OWED and was not attempted here. -->

<!-- Scoped ingest 2026-11-23 (the builder's agentic tool surface): the `builder-agentic-tool-surface`
change made the builder's deterministic capabilities callable and put its own pack behind a flag that runs
them — `--flow agentic`, the `builder-agentic-blueprint` pack, and five new `ToolGroup` beans
(`blueprint`, `library`, `security`, `traces`, `process`) beside the existing `validate`.
[[smith-builder]] gained "The agentic flow" for the four facts not visible from the flag: the resolved role
set with the 62 upstream tool classes that reach nothing, the three load-bearing properties (the driver
chooses what is read, every result capped, an identical repeat answered once), the rule that says what is
excluded is a verdict, a write, a model call or the referee, and the fact that the deterministic checks are
now forced in Java between model calls because on one measured run the step was offered the validator and
declined it. [[trust-tiers]] carries the vocabulary decision: the allowlists are deliberately unchanged, so
every builder role lands at `T4_PLATFORM`, and backed-ness is proved against a booted registry rather than
by a tier. Three clauses this change falsified were rewritten rather than left standing: `AgentsPackValidationTool`
"carried by no registered group", "the tree now has two" `ToolGroup` beans (seven now), and the `--flow pack`
measurement's "repeats on an unchanged draft are not deduped" (every tool body now goes through the memo, the
validator's included). Two other negative claims were re-counted against the tree rather than trusted:
`docs/12-backlog.md`'s envelope gap now reads two packs of five with the greps that say so, and its
single-own-property count is eight of twenty-one. `last_commit` is deliberately NOT advanced: this pass
covered the builder's agentic surface, not the rest of the tree. Lint after the edit: 45 pages, 0 broken
cross-links, 0 dead `source_paths` in the two pages touched. GitHub Pages re-publish remains OWED
(`BLOCKERS.md`) and was not attempted. Everything about what a model does on the new route is UNPROVEN —
nothing here ran against an endpoint, and the native image was not rebuilt. -->

<!-- Scoped ingest 2026-11-23 (the builder as an agent harness): the `builder-as-agent-harness` change
added the fourth build flow — `--flow harness`, the `builder-harness` pack (three steps: survey
the document roots, decide what to read next, draft) and `eval/HarnessAgentSpecGenerator`, which extends
the agentic driver instead of restating the forcing loop, so the re-ask ceiling, the identical-call memo and
the per-build window clearing remain one implementation. The four-flow count is corrected wherever three
were asserted: `docs/08-builder.md`'s flow table gained the row, this page and `[[smith-builder]]` name all
four, and `examples/` describes the arm rather than one arm; neither README names the flow list, so neither
needed an edit. [[smith-builder]] gained "The harness flow" for the facts not visible from the flag: the
cursor-versus-corpus distinction, and the handover that did **not** move —
`cli/BuildCommand.resolveDescription()` still assembles the corpus under this flow because the multi-agent
candidate consumes it, so the change's spec scenario was narrowed to the claim that holds and
`docs/12-backlog.md` carries the dual-candidate cost of the rest — the gate-list asymmetry on an invalid
draft (java logs `[generate, validate]`, harness `[generate]`, same stage named, same refusal), and that
`examples/parity-agentic.sh` now takes the arm as `SMITH_PARITY_FLOW` with the java arm's 2-of-3 bar and its
answered / spent / offered / called columns intact. Two claims this change falsified were rewritten rather
than left standing: §The pack flow's "nothing in the transcript names the flow that ran" — `BuildResult`
carries the flow now and `report()` prints it, which is the clause that had kept
`builder-executes-its-own-packs` open — and §The builder's own packs' five packs, which are six with three
wired. Everything about what a model does on the new route is UNPROVEN: nothing ran against an endpoint, the
parity script was not executed, and the native image was not rebuilt. `last_commit` is deliberately NOT
advanced — this pass covered the builder's flow surface and the example scripts, not the rest of the tree.
Lint after the edit: 46 pages, 0 broken cross-links, 0 duplicate headings in the pages touched, 0 dead
`source_paths` in the two pages touched. GitHub Pages re-publish remains OWED (`BLOCKERS.md` §1: a Pages
environment rule refuses the deploy even from `main`, which no commit can change) and was not attempted. -->

<!-- Scoped ingest 2026-11-23 (the harness change's documentation tail): §6.1's three pages, closed on the
two pages that were still open. `docs/08-builder.md` §`skill` now says a published folder can carry
`scripts/*.js`, that stage 7's `security/AgentSpecSecurityScanner.scanSkill` reads them as code under three
CRITICAL codes (`SCRIPT_HOST_ACCESS`, `SCRIPT_NETWORK`, `SCRIPT_PATH_ESCAPE`, evidence naming the file), and
that execution is dormant: the engine is a bean behind `smith.builder.skill-scripts.enabled`, off by
default, and `prod/` main assembles no `Skills` to hand it to — the only `com.embabel.agent.skills` imports
outside `skill/script/` are in tests. It also states what `--flow harness` cannot reach: its three steps
declare only `blueprint`, `library`, `validate` and `security`, `list_docs`/`read_doc` are that
`blueprint` group's own tools, and no builder registration hands a step a write, a shell or a network call.
`docs/12-backlog.md` gained §Builder — skill scripts with the two deliberate openings: the host that would
run a published script (both of design D8's routes retired; `runtime-a2a/SpecSourceSkills` publishes
`io.a2a.spec.AgentSkill` card entries rather than loading a folder), and the script scan sitting at stage 7,
so a skill whose script was never publishable still pays for the eval arms first. `docs/10-self-improvement.md`
needed nothing: its mutable/immutable split already names the scorer prompts as classpath resources under
`skill/ScorerPromptBoundaryTest`. [[trust-tiers]] gained `builder-harness`'s three steps beside the two
drafting steps and the agentic pack — the same four roles, so the page's conclusion is untouched: none of
the six names is in an allowlist and every builder pack still reads `T4_PLATFORM` through T4's `*`. Neither
README needed an edit (§6.3). Lint after the edit came from two passes, and the numbers below say which
produced them. The `git-wiki` project skill does document a lint — `.agents/skills/git-wiki/SKILL.md`
§Lint, six checks — but it is a procedure written down, not a command: the skill ships one script
(`scripts/export-wiki-bundle.mjs`, a bundle export with no lint mode), `.agents/` is gitignored at
`.gitignore:1` so `git ls-files | grep git-wiki` finds nothing, and `make` has no wiki target
(help/build/test/test-live/test-module/lint/checkstyle/checkstyle-module/module/native/clean/install/
setup — `make lint` is Checkstyle). Four of its six checks are mechanical and were run: 0 broken
cross-links over 46 files with 514 link references parsed; 0 orphan pages other than this index, which is
the map rather than a page on it; 0 pages carrying `status: "stale"` (all 46 read `active`); and the
coverage check reports 122 files changed since `last_commit` `80ec309`, which is precisely why a scoped
pass does not advance it. Its two judgement checks — contradictions between pages, and concepts named
across several pages that lack a page of their own — were not run, so nothing here claims them. The second
pass is an ad hoc checker written for the two checks the skill's list omits: 0 dead `source_paths` of 432
parsed, and 0 duplicate headings, with its non-vacuity confirmed against a control page it flagged for all
three failure kinds. GitHub Pages
re-publish remains OWED (`BLOCKERS.md` §1) and was not attempted; `last_commit` is deliberately NOT
advanced. -->

<!-- Scoped ingest 2026-11-23 (the builder-side skill-script host, and the shipped sentence it falsified):
`2b16c27` put `skill/SkillScriptHost` into `prod/embabel-smith-builder` main, and that made this page's
first limit — "Nothing in `prod/` calls `Skills.withScriptExecutionEngine` yet, so a published script is
today a file nothing executes" — false the moment it landed. The correction is the half-filled one, not a
deletion, because the caller is narrower than the sentence it replaces: the host does call it
(`SkillScriptHost.java:90-91`, `withLocalSkills(root())` then `withScriptExecutionEngine(engine)` against a
folder it writes under a temporary directory), the engine bean stays behind `smith.builder.skill-scripts.enabled`
and off by default, and the only consumer is the skill *under build*'s with-skill eval arm — `EvalRunner.java:116`
builds the reference once per run and the two arms differ only by whether it is attached. Nothing reads a
*published* folder: `runtime-a2a/SpecSourceSkills` publishes `io.a2a.spec.AgentSkill` card entries rather than
loading one, so the seam design D8 leaves open is still open. Where the statement lived and where it went:
this page at `c389607`, refreshed for the harness run by `a04eef0`; `docs/08-builder.md` §`skill` at
`5bb81a8`; `docs/12-backlog.md` §Builder — skill scripts at `6157d39` and `59290f8`; the mutable-surface
paragraph of `docs/10-self-improvement.md`. The guard that came in with the host is
`skill/SkillFileReferences`, called at `SkillBuilderService.java:302` and blocking the build at `:304` with
`blocked("references", …)` — it exists because Embabel's reader refuses a whole skill over a dangling file
reference and logs the reason on the reader's side, out of the builder's view. Lint after the edit: 46 pages,
0 broken cross-links, 0 duplicate headings in the pages touched, 0 dead `source_paths`. GitHub Pages
re-publish remains OWED (`BLOCKERS.md` §1: a Pages environment rule refuses the deploy even from `main`, which
no commit can change) and was not attempted here — which is precisely why the *published* page can still carry
the absence while every tracked page says the half-filled truth. `last_commit` is deliberately NOT advanced:
this pass covered the skill-script host's claim and this index, not the rest of the tree. -->

<!-- Full ingest 2026-11-22: every tracked file changed since the previous `last_commit` (1311 of them
at this HEAD) was partitioned and surveyed — builder (305), studio (194), runtime + build/CI (86),
docs/POC evidence (717), root (5) — and 24 pages were refreshed against the code. What moved: the
runtime gained a retired-pack gate, per-run model pinning and a remote-action client; the builder gained
`--docs`/`--extract`, `smith blueprint`, validators exposed as `@LlmTool`s, its own packs (under
`builder-agent-specs/`), and the improvement engine (`builder/improve/`); the studio's loop now only
drives that engine; `ModelEndpointConfig` detects three providers, not one; CI exists
(`offline-verification.yml`) and runs the Makefile targets, not `mvn -q verify`; the groupId is now
`org.iromu.embabel.smith`; POC 27 landed with a *not proven* verdict. All seven dead path
references were repointed to `docs/`, since the planning trees were consolidated on 2026-11-15 and the
nine specs now in `docs/` were promoted by the 2026-11-21 archives. Lint clean: 45 pages,
0 broken links, 0 orphans, 0 dead source paths. GitHub Pages re-publish remains OWED: `wiki-pages.yml`
fires on pushes to `main` touching `.wiki/**`, and these edits sit on `poc/iteration-2` — until they
merge, https://iromu.github.io/embabel-smith/ still shows the pre-A2A wiki (`gh workflow run` is the
alternative). Known code/doc drift this ingest found but did not touch: `docs/07-runtime.md` still
places the `TraceStore` SPI in the runtime module, `docs/12-backlog.md:14` claims the nine promoted
specs restate rules `docs/` already states (two do not), `prod/embabel-smith-runtime-a2a/README.md:67`
claims the card and the run gate cannot disagree (the card is not lifecycle-aware), `LoopService`'s
javadoc links a `SelfImprovementLoop` class that no longer exists, and `offline-verification.yml`'s
header says four `@Tag("live")` tests where the tree has nine. -->

<!-- Scoped ingest 2026-11-21: the self-improvement engine moved from `studio/improve` into
`builder/improve`, so [[smith-studio]] and [[smith-builder]] were repointed and the module map below
corrected. `last_commit` is again deliberately NOT advanced — the full ingest is still owed, and the
GitHub Pages re-publish this file already records as owed is still owed. -->

<!-- Scoped ingests 2026-11-19: the A2A surface ([[a2a-endpoint]], [[smith-runtime]], [[build-tooling]],
[[model-calls-go-through-ai]]) and the skill `inspect` gate ([[skill-output-target]], [[smith-builder]]).
`last_commit` is deliberately NOT advanced — 1083 tracked files changed since it, so a full ingest is
still owed and the rest of these pages are unrefreshed since 2026-11-15. Lint ran clean (45 pages, 0
broken links, 0 orphans, 0 stale). GitHub Pages re-publish is OWED: `wiki-pages.yml` fires on pushes to
`main` touching `.wiki/**`, and these edits are on `poc/iteration-2`, so until they merge the site at
https://iromu.github.io/embabel-smith/ still shows the pre-A2A wiki (manual `gh workflow run` is the
alternative). -->

<!-- Scoped edit 2026-11-20: [[smith-studio-storage]] — the `packs` table's key is `(name, version)`,
not the bare name, and `PacksKeyMigration` re-keys a deployed name-keyed table at bind time.
`last_commit` again deliberately NOT advanced. Lint re-ran clean over all 45 pages (0 broken links, 0
orphans, 0 stale). GitHub Pages re-publish is still OWED for the same reason. -->

# Embabel Smith

A **GOAP-based agent builder** on the Embabel platform (v1.5.4, Java 21). One part *builds* a
spec; the other part *runs* one. The POCs under `poc/` prove the idea end to end; the product
modules under `prod/` ([[product-phase]]) turn that proven logic into a working builder and runtime.
This wiki explains how the pieces fit so a newcomer can orient fast and verify claims in code.

## 1. What is this?

Embabel Smith is a greenfield, multi-module Maven proof-of-concept (POC) that proves one idea end
to end: **a builder agent can turn a natural-language description into a deployable, no-code
"agent spec", and a fixed runtime can ingest that agent spec and run it — with zero compilation.**

An **agent spec** is a small directory of pure YAML (no code, no build step). It is the unit of
capability that the builder emits and the runtime ingests. See [[agent-spec-format]].

## 2. Get started

- **Build & test everything (offline):** `mvn -q verify` — that is the **default reactor**
  (`smith-common` + `prod/`); the POCs sit behind `-Ppoc` / `POC=1`. Tests pass with no model endpoint
  because the model-touching ones carry the `live` tag, which the build excludes by default.
- **Prefer the Makefile targets** (`make build`, `make test`, `make test-module MODULE=…`, `make lint`)
  — those are what CI runs. `make test` sources `~/.config/env/llm.env` first, but the tag exclusion
  still applies, so **`make test-live` is the only opt-in** that runs the endpoint-gated tests
  ([[build-tooling]]).
- **Once per clone:** `make setup` installs the pre-commit hooks (gitleaks, whitespace, `make lint`).
  Without it, a fresh clone commits unchecked.
- **Run a POC:** `mvn install -DskipTests` once (so sibling modules resolve), then run one module —
  with `-Ppoc`, since the POCs are outside the default reactor and `-pl poc/...` matches nothing
  without it:
  `BUILDER_PACK_DIR="$PWD/agent-specs/explainer" BUILDER_INPUT="Explain how a coffee machine
  works" mvn -Ppoc -pl poc/poc-1-no-code-runtime spring-boot:run`. Inputs are OS env vars
  (`BUILDER_*`), not `-D`.
- **The full loop:** `./run-loop.sh "<description>" "<input>"` builds a spec with the builder,
  captures the written pack, and runs it in the no-code runtime in one command.
- **In the browser:** start the studio and open its **Process** workspace to go from business
  documents to a published spec, or the **Compose** pane to wire one by hand
  ([[studio-process-workspace]], [[studio-compose-editor]]).
- **Required to run (not to build):** a model endpoint via env vars, **detected** from whichever is
  set — `OPENAI_CUSTOM_BASE_URL` + `OPENAI_CUSTOM_API_KEY` + `OPENAI_CUSTOM_MODELS` (any
  OpenAI-compatible server), or `OPENAI_API_KEY`, or `ANTHROPIC_API_KEY`, plus
  `EMBABEL_MODELS_DEFAULT_LLM` naming the model. Without any of them, endpoint-gated POCs fail with a
  **clear configuration error**, not an opaque model error — see [[model-endpoint]].
- **First files to read:** `README.md`, `docs/agent-spec-format.md`,
  `agent-specs/explainer/` (the sample pack), then
  `poc/poc-1-no-code-runtime/src/main/java/com/embabel/smith/runtime/SpecRunner.java`.
- **One safe first change:** edit a prompt in `agent-specs/explainer/steps/writeExplainer.yml` and
  re-run POC 1 — you only change data, never code.
- **One tempting dangerous change:** deleting the validate-before-write guard in
  `poc/poc-3-builder-agent/.../BuilderAgent.java` (`writeAgentSpec`). That is the single invariant
  that keeps "generate with no code" safe — see [[validate-before-write]].

Full orientation: [[getting-started]].

## 3. Why does it exist?

It is a research/POC vehicle for the **"specs, not agents"** paradigm on Embabel. The thesis (see
`docs/03-embabel-spec-agent-builder-design.md`): Embabel *already* is a specs runtime — a
**spec** is just an **exported goal** (`export: true`), and the platform's non-LLM **GOAP**
planner *is* the spec router. The POCs prove the load-bearing facts (YAML ingestion
without compilation, NL→draft compilation, deterministic builder structure, spec composition, CLI
invocation, retrieval, eval-guided refinement, trust tiers, progressive disclosure, semantic
retrieval at scale, remote/MCP invocation, the spec Composer, the visual builder UI, the
remote-action bridge, GLiNER2 spec classification, spec lifecycle / drift detection, small-LLM
spec selection, and the real semantic `EmbeddingService`).
All three Phase 1 specs-layer components doc 03 lists (registry, composer, runtime adapter) are
built, and every hypothesis gap doc 03 §2 lists — including a real semantic `EmbeddingService`
(POC 20) — is now closed.
**POCs 24–27 probe what comes next**, outside the original claim set: which generation mechanism
produces a usable multi-agent flow ([[generation-approaches]]), whether the portable unit can be an
Anthropic `SKILL.md` instead of a YAML spec ([[skill-generation]]), whether a skill's own JavaScript can
execute inside the agent ([[skill-scripts-graaljs]]), and whether the builder can improve the process it
builds with — the verdict on that last one is *not proven* ([[what-the-pocs-prove]]). Two of those
landed in the product: POC 24's measurement is the builder's default technique *and* the reason the build now
weighs two candidate shapes ([[two-candidate-build]]), and POC 25's became the `--target skill`
output ([[skill-output-target]]).
See [[exported-goal-as-spec]] and [[what-the-pocs-prove]].

## 4. What happens when I run it?

Three entry points matter:

- **Run a spec (POC 1):** the fixed runtime loads an agent spec, validates it, ingests the YAML
  with zero compilation, plans a path from `UserInput` to the goal's output type, executes it
  (only the action bodies call the model), and returns the goal output. See [[runtime-run]].
- **Build a spec (POC 3):** the builder agent plans `compileFromDescription → validateDraft →
  writeAgentSpec`. Only the first step calls the model; validate and write are pure Java, and an
  invalid draft is never written. See [[builder-pipeline]].
- **Compose specs (POC 6):** two independent packs are loaded into one platform and the GOAP
  planner chains an action from each through their shared intermediate type. See
  [[spec-composition]].
- **Author a composite spec (POC 14):** specs are chained on a type-checked canvas and exported
  as a single composite agent spec, which then runs through the no-code runtime. See
  [[spec-composer]].
- **Build a spec in the browser (POC 15):** the visual builder UI — a drag-and-drop canvas that
  loads the spec palette, snaps only type-compatible connections, live-validates the chain, and
  exports the composite over REST — plus a curation-aware **retrieval pane** (POC 18 lifecycle), an
  **author pane** (POC 2), an **eval/refinement pane** (POC 9), and a **curate re-eval** (POC 18).
  See [[spec-composer]].
- **Author a process in the product (studio):** the home **Process** workspace runs
  Discover → Generate → Simulate → Publish over your business documents, and the **Compose** pane
  wires a GOAP flow by hand on a type-checked canvas and publishes it. See
  [[studio-process-workspace]] and [[studio-compose-editor]].

## 5. Where is data saved?

- **Agent specs** live under `agent-specs/<name>/` (samples: `explainer`, `compose-extract`,
  `compose-explain`, `shell-runner`, `reference-heavy`).
- **Built packs** are written by the builder to `./built-specs/<name>/` (configurable via
  `builder.output-dir`).
- **Blueprints** to author from live in `examples/blueprints/` — five corpora: Aurora, Sentinel,
  TradingAgents, agentic-software-factory, deer-flow. `smith blueprint` (or
  `examples/generate-blueprint.sh`) reconstructs one from a codebase.
- **Intermediate types** are pure data in the pack's `types/` directory — no compiled classes.
- The POCs, the `smith run` runtime, and the `smith build` builder have **no database** —
  persistence is plain files on disk (the runtime assembles a temp directory per run and leaves the
  source pack untouched). The **studio** optionally adds a **Postgres** database for saved packs,
  the discovery history, and run traces — hot-swappable from the UI, and the app still boots with
  none ([[smith-studio-storage]]). Its dev database is `docker/docker-compose-infra.yml` on port
  5433 ([[build-tooling]]).

## 6. What are the important moving parts?

- `smith-common` — the **format owner**: data model, YAML I/O, and validation. Framework-free.
  See [[agent-spec-model]] and [[validation-codes]].
- `poc/poc-1-no-code-runtime` (POC 1, **keystone**) — proves the no-compilation ingest + run path.
  See [[runtime-run]].
- `poc/poc-3-builder-agent` (POC 3) — the builder as a code-backed `@Agent`. See
  [[builder-pipeline]].
- `poc/poc-2-nl-compile` (POC 2), `poc/poc-4-agent-spec-format` (POC 4), `poc/poc-5-eval` (POC 5) — see
  [[architecture]].
- `poc/poc-6`…`poc-23` — the GOAP-based extensions: composition, runtime adapter, retrieval,
  eval-guided refinement, trust tiers, progressive disclosure, semantic retrieval at scale,
  remote/MCP invocation, the spec Composer, the visual builder UI, the remote-action bridge,
  GLiNER2 spec classification, spec lifecycle / drift detection, small-LLM spec selection, the
  real semantic `EmbeddingService`, install-time security scanning, the unified builder UI (POC 22),
  and remote embedding selection at scale (POC 23). See [[what-the-pocs-prove]].
- `poc/poc-24`…`poc-27` — the generation, portable-unit and self-improvement wave: single-shot vs the
  Embabel NL compiler ([[generation-approaches]]), blueprint → Agent Skills ([[skill-generation]]),
  in-JVM GraalJS skill scripts ([[skill-scripts-graaljs]]), and whether the builder can improve the
  process it builds with — verdict *not proven* ([[what-the-pocs-prove]]).
- `prod/` — the **product phase** ([[product-phase]]): `specsource` (folder/git sources +
  content hash), the `smith run` runtime (multi-pack ingest + trust tiers + the retired-pack gate +
  JSONL trace + the remote-action bridge the studio's Remote pane drives), the
  `smith build` builder (one description → **two candidates**, a single-agent pack and a
  multi-agent process, both scored and one recommended, behind an `AgentSpecGenerator` technique
  seam and a `--flow` seam with four routes (`java|pack|agentic|harness`) that can hand the generation
  call to the builder's own pack — two of which let that pack decide what to read next — or a
  `SKILL.md` with `--target skill`; [[smith-builder]], [[two-candidate-build]],
  [[skill-output-target]]), and the `studio` (the driver of the self-improvement loop that rewrites
  failing steps from traces and republishes on proven improvement — the engine itself,
  `builder/improve/`, has lived in the builder since 2026-11-21 — exposed as a web product: REST
  surface + Vue UI + loop scheduling + the process workspace). All model calls in `prod/` go through Embabel's `Ai`,
  not the endpoint directly. `a2a-starter` is the one prod module that declares
  `embabel-agent-starter-a2a`, so it is the only one that gains a servlet engine from a dependency rather
  than by asking for one; its beans stay off until a host sets `smith.a2a.enabled`, and the host is the
  studio — which listens anyway, because it is the UI — serving an unauthenticated local development
  endpoint for another process to ask for a run ([[a2a-endpoint]]). The CLI runtime cannot host it: the
  starter depends on the runtime, so the reverse edge is a Maven cycle. `skillevaluator` is the Tier 1 **static skill validator** — a
  zero-model-call port of a Python `tier1` validator — and its only consumer is the `inspect`
  gate of the builder's skill target ([[skill-output-target]]). See
  [[spec-sources]], [[smith-runtime]], [[smith-builder]], [[smith-studio]], [[smith-studio-web]],
  [[studio-process-workspace]], [[studio-compose-editor]] and [[model-calls-go-through-ai]].
- Root tooling — the `Makefile` (stable Maven entry points), `setup.sh` (developer setup), the
  pre-commit hooks, and the SpringChecks/style gate that runs at Maven's `validate` phase.
  See [[build-tooling]].

## 7. What should I avoid breaking?

- **Validate-before-write** (design D5): the builder must never write a draft that fails
  validation. See [[validate-before-write]].
- **The format contract**: builder (producer) and runtime (consumer) both depend on
  `smith-common`; changing field names or the layout breaks both sides. See [[agent-spec-format]].
- **Endpoint gating**: keep the model endpoint env-driven and keep the "clear config error when
  unresolvable" behaviour. See [[model-endpoint]].
- **The style gate**: SpringChecks + the mandatory SPDX license / author header run at Maven's
  `validate` phase for every module's main sources, so a violation fails the build before
  compilation. See [[build-tooling]].
- **No direct endpoint calls in `prod/`**: every production model call goes through Embabel's `Ai`,
  and two source-level tests — the builder's and the studio's — fail the build on a `java.net.http` import
  under `src/main/java`. The ban is per-module: `prod/embabel-smith-runtime` carries no such test and its
  `remote/RemoteActionBridge.java` already uses that client for a remote-action sidecar, a different
  endpoint, so in the runtime this is a convention a reviewer holds rather than a gate. Adding a raw model
  caller reintroduces a second, silent model-name decision. See [[model-calls-go-through-ai]].
- **The studio's forced servlet mode**: `main()` sets `web-application-type=SERVLET` as a system
  property for a non-obvious reason (a transitive jar ships a `none` default). Do not "clean it up".
  See [[smith-studio-storage]].
- **The recommendation rule**: the multi-agent candidate wins only when it is valid *and* strictly
  out-scores the single-agent pack. Weakening `>` to `>=`, or letting an invalid process be
  recommended, silently ships the riskier shape. See [[two-candidate-build]].
- **The `Publishable` seam**: `Publisher.publish(Publishable, overwrite, push)` is the one abstract
  method every artifact publishes through. Changing its signature breaks *dependent modules' test
  lambdas*, not their main code — so it stays invisible until someone test-compiles another module.
  Run `mvn -pl <module> -am test-compile` across `prod/` after touching it.
- **The reply envelope named in the drafting steps**: three of the six builder packs — the three that
  draft an agent spec — spell the one-key object (`{"json": …}`) their goal type declares
  (`builder-spec-one-shot`, `builder-agentic-blueprint`, `builder-harness`). No validator reads prose about
  output shape, so dropping one of those sentences surfaces only as "did not return valid agent spec JSON"
  after a paid build. `BuilderAgentSpecPackTest` pins it by reading the key from the type, not from a Java
  literal. See [[smith-builder]].

## 8. Where do I look first?

| Question | Start here |
| --- | --- |
| What did the POCs actually prove? | [[what-the-pocs-prove]] · `docs/04-poc-run-results.md` |
| How is an agent spec laid out? | [[agent-spec-format]] · `docs/agent-spec-format.md` |
| What are the data classes? | [[agent-spec-model]] · `smith-common/.../model/` |
| How does a spec run? | [[runtime-run]] · `SpecRunner.java` |
| How is a spec built? | [[builder-pipeline]] · `BuilderAgent.java` |
| How are specs composed? | [[spec-composition]] · `CompositionRunner.java` |
| How is a composite spec authored + exported? | [[spec-composer]] · `poc/poc-14-composer/` |
| How do I build a spec in the browser (builder UI)? | [[spec-composer]] · `poc/poc-15-composer-ui/` |
| How does a spec call a CLI? | [[runtime-adapter]] · `shell-runner/` pack |
| How does a spec invoke a remote action over HTTP? | [[smith-runtime]] · [[runtime-adapter]] · `poc/poc-16-remote-action/` · prod: `runtime/remote/` |
| How is a spec scored / refined? | [[eval-scoring]] · [[eval-guided-refinement]] |
| How are specs retrieved at scale? | [[retrieval-shadowing]] · [[shadowing-remedies]] · prod: [[smith-studio-web]] |
| How are trust tiers enforced? | [[trust-tiers]] · [[spec-security-scanning]] |
| How is a spec vetted before install? | [[spec-security-scanning]] |
| How does a spec's lifecycle / drift work, and what does it block? | [[spec-lifecycle]] · [[smith-runtime]] |
| How does progressive disclosure bound context? | [[progressive-disclosure]] |
| What is an "exported goal"? | [[exported-goal-as-spec]] |
| How does zero-compilation work? | [[no-code-ingestion]] |
| How do the POCs become the product? | [[product-phase]] · `prod/` |
| How are spec sources (folder/git) resolved? | [[spec-sources]] |
| How does the `smith run` runtime work (CLI + trace)? | [[smith-runtime]] |
| How does another process ask this product to run a goal (A2A)? | [[a2a-endpoint]] · `prod/embabel-smith-a2a-starter/` |
| How does the `smith build` builder work (pipeline + publish)? | [[smith-builder]] |
| How does the build choose between a single agent and a multi-agent process? | [[two-candidate-build]] · `builder/core/CandidateRecommender.java` |
| How do I build a `SKILL.md` instead of a pack? | [[skill-output-target]] · `builder/skill/` |
| How is a drafted skill vetted before the eval is paid for? | [[skill-output-target]] · `builder/skill/SkillInspector.java` |
| How does a spec self-improve from its traces? | [[smith-studio]] |
| How is the studio exposed as a web product (REST + UI + loop control)? | [[smith-studio-web]] |
| How does the studio store packs/traces in Postgres (hot-swap + pluggable traces)? | [[smith-studio-storage]] |
| How do I author a process from business documents in the studio? | [[studio-process-workspace]] · `examples/blueprints/` |
| How do I wire a GOAP flow by hand in the studio? | [[studio-compose-editor]] · `frontend/src/compose/model.ts` |
| Which generation technique should I use, and why? | [[generation-approaches]] (POC 24) · `poc/poc-24-generation-approaches/results/comparison.md` |
| Which model does production actually call, and who decides? | [[model-calls-go-through-ai]] · `builder/core/AiSpecPromptCaller.java` |
| What stops a live build on this endpoint, and how do I read the parity columns? | [[model-endpoint]] · `examples/parity-agentic.sh` |
| How does the builder pick a generation technique? | [[smith-builder]] · `builder/core/GenerationTechnique.java` |
| Can a pack's own agent call the format validators? | [[smith-builder]] · `builder/tools/` |
| Who issues the generation call — the Java stages or the builder's own pack? | [[smith-builder]] · `builder/cli/BuildFlow.java` |
| What does CI actually verify, and what does a green run not cover? | [[build-tooling]] · `.github/workflows/offline-verification.yml` · `.github/workflows/native-image.yml` |
| How do I get a `smith` binary with no JVM, and what does its CI prove? | [[native-binary]] · `make native` |
| Can the portable unit be a `SKILL.md` instead of a YAML spec? | [[skill-generation]] (POC 25) |
| How does a skill's JavaScript run inside the agent? | [[skill-scripts-graaljs]] (POC 26) |
| How do I write a skill script that runs on both hosts? | [[skill-script-javascript]] · `docs/13-skill-script-javascript.md` |
| What build commands should I use? | [[build-tooling]] · `Makefile` |

## Page catalog

**Overview** — [[what-is-embabel-smith]] · [[getting-started]] · [[architecture]] ·
[[what-the-pocs-prove]] · [[product-phase]] · [[build-tooling]] · [[native-binary]]
**Entities** — [[agent-spec-format]] · [[agent-spec-model]]
**Concepts** — [[exported-goal-as-spec]] · [[no-code-ingestion]] · [[model-endpoint]]
**Flows** — [[runtime-run]] · [[builder-pipeline]] · [[eval-scoring]] · [[spec-composition]] ·
[[eval-guided-refinement]]
**Features** — [[nl-compile]] · [[spec-format-on-demand]] · [[runtime-adapter]] ·
[[retrieval-shadowing]] · [[shadowing-remedies]] · [[spec-lifecycle]] · [[trust-tiers]] ·
[[spec-security-scanning]] · [[progressive-disclosure]] · [[spec-composer]] · [[spec-sources]] ·
[[smith-runtime]] · [[a2a-endpoint]] · [[smith-builder]] · [[smith-studio]] · [[smith-studio-web]] ·
[[smith-studio-storage]] · [[studio-process-workspace]] · [[studio-compose-editor]] ·
[[generation-approaches]] · [[skill-generation]] · [[skill-scripts-graaljs]] ·
[[skill-script-javascript]] · [[two-candidate-build]] · [[skill-output-target]]
**Risks** — [[validate-before-write]] · [[model-calls-go-through-ai]]
**Reference** — [[validation-codes]] · [[sample-explainer]]
