---
title: "The native smith binary"
type: "overview"
status: "active"
language: "default"
source_paths:
  - "Makefile"
  - "pom.xml"
  - "prod/embabel-smith-builder/pom.xml"
  - "scripts/native.sh"
  - "scripts/native-hints.sh"
  - "smith-common/src/main/java/com/embabel/smith/common/io/SpecFolders.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/BuilderPackPaths.java"
  - ".github/workflows/native-image.yml"
  - "prod/embabel-smith-builder/src/main/resources/META-INF/native-image/com.embabel.smith/builder-native-hints/reachability-metadata.json"
  - "prod/embabel-smith-builder/src/main/resources/META-INF/native-image/com.embabel.smith/builder-traced-hints/reachability-metadata.json"
  - "docs/08-builder.md"
  - "docs/12-backlog.md"
updated_at: "2026-11-26"
---

# The native `smith` binary

`make native` compiles the builder CLI into one executable file, `prod/embabel-smith-builder/target/smith`,
that starts without a JVM and needs no runtime on the machine (`docs/08-builder.md#As a native binary`
is the run book). Same CLI, same flags — only how it starts changes. This is unrelated to GraalJS, which runs a
skill's JavaScript inside a live agent ([[skill-scripts-graaljs]]); same vendor, different mechanism.

## The `native` profile is opt-in per module

The root profile is `pluginManagement` only: it turns on GraalVM's reachability-hint metadata repository
and adds `--no-fallback` and `-H:+ReportExceptionStackTraces` (`pom.xml:82-104`), and pins
`native-maven-plugin` to **0.11.5** because the 1.1.x line writes a hint schema the JDK-25 image tooling
cannot read (`pom.xml:47-50`). A module gets a binary only once it declares the plugins itself, and only
the builder does (the `native` profile in `prod/embabel-smith-builder/pom.xml`): `spring-boot:process-aot`, then a
`maven-resources-plugin` `fold-aot-output` execution at `prepare-package` that copies
`target/spring-aot/main/{classes,resources}` into `target/classes` — the one directory `native-image`
already has on its classpath. The pom comment records why the fold exists rather than a direct output
path: pointing `process-aot` at `target/classes` **deletes the module's compiled classes**. A second
`prepare-package` execution, `pack-specs-beside-binary`, is described below. Then
`native-image` runs `compile-no-fork` at `package` with `imageName` `smith`.

`make native` is `package -Pnative -DskipTests -pl :embabel-smith-builder -am` (`Makefile:72-73`);
`scripts/native.sh` is that same call behind an sdkman selection that prefers the **25.0.2** patch the CI
workflow pins (`GRAAL_VERSION` overrides it) and falls back to the rolling `25-graalce` with a printed
warning when that candidate is not installed locally — asking sdkman for a version it does not have would
hang the script on a network call. A local build can therefore run on a GraalVM other than the one CI
proves, and the script prints the expected patch beside `native-image --version` so the mismatch is on
the screen. Skipping the
tests is deliberate — `process-aot` rewrites the module's classes and the suite is not native-aware.

## Two hint directories, one of them generated

Static analysis cannot see everything the CLI reaches, so `src/main/resources/META-INF/native-image/`
carries two directories. `builder-native-hints/` is hand-written and 76 lines: the Kotlin-reflected
`LlmDataBindingProperties`, globs for the provider model catalogs (`models/**/*.yml`), the bundled
prompts (`prompts/*.jinja`) and the builder's own packs (`specs/*/*.yml`, `specs/**/*.yml`) that Embabel
opens by name, and several entries the file's own comment keeps for
gaps the tracing agent cannot rediscover — `ToolGroupMetadata$Companion`, whose factory `ValidationToolGroup`
locates by scanning `getMethods()` for a signature whose JVM name contains a hyphen, so a trace comes back
empty without it; and `BuildCommand$FlowConverter` together with the `BuildCommand.flow` *field*, because
picocli reads every `@Option` field's initial value when it clears a subcommand's scope, which means an
unregistered `flow` fails every `smith build` rather than only the ones that name a flow — and the
argument-free native smoke cannot see either half. The same two rules now apply three times more, all of
them found by running an image rather than by guessing (§The skill path): `skills.spec.SkillDefinition`
with `SkillMetadata`, which is what lets an image load a published skill folder instead of dying in
kotlin-reflect; `skills.Skills` itself, because `Skills.tools()` hands the reference to
`MethodToolFactory.fromInstance`, which enumerates its declared members over `KClass.memberScope`; and
`SkillRunCommand.skillDir` with `.request` — `addSubcommand` is a compile-time call, so an image missing
those two fields still registers `smith skill-run` and then rejects its own arguments with
`Unknown options: --skill-dir` and a usage line of `Usage: smith skill-run` with nothing after it, which
reads as a mistyped flag rather than as the missing registration it is. `builder-traced-hints/` is **generated and
committed** — 12 754 lines, 1 413 reflection entries and 490 resource globs — produced by
`scripts/native-hints.sh`, which runs four CLI invocations and then the whole offline Surefire suite
under the tracing agent. The suite is what leaks: of the file's 1 413 reflection entries, 92 name a `*Test` type and 88 of those
carry verbatim test-method names (`DualCandidateBuildTest.aCycleTheReorderSettlesIsRepairedStoredAndPublished`).
Both directories are load-bearing — drop the hand-written one and the catalogs and prompts stop resolving,
drop the traced one and the third-party, Jinjava and provider-SDK reflection goes with it. The pair grows
"by failure rather than by guesswork": a `MissingReflectionRegistrationError` names the entry to add. The
script also wants a dependency classpath dump at `/tmp/smith-cp.txt` (`CP_FILE` overrides) that nothing in
the repo writes — `examples/smith-cli.sh:53` caches its own dump under a different name.

One path is untraced by construction: none of the four invocations passes `--flow pack`
(`scripts/native-hints.sh:42-51`), and the one test that really deploys a pack is `@Tag("live")`, so the
offline Surefire run skips it too ([[smith-builder]]). That is why the pack's *resource* half is written
by hand rather than waited for: `builder-native-hints` registers `specs/*/*.yml` and `specs/**/*.yml`, and
the `native` profile's `pack-specs-beside-binary` execution copies
`prod/embabel-smith-builder/src/main/resources/specs` to `target/specs`, next to the binary. Both halves
are needed and neither alone is enough. Registering the globs puts the 48 pack files *in* the image, which
makes an individual file resolve by name; it does not make a pack's directory listable, because an image
carries resources as a flat named set with no directory entries. `smith-common`'s `io/SpecFolders.java`
closes that half: the classpath route answers first — so an override left set in someone's shell cannot
shadow the pack that compiled in — and only when it cannot list does the call fall through, in order, to
`smith.specs.dir` / `SMITH_SPECS_DIR` and then to a `specs/` folder beside the running program's own
executable, which is what the beside-binary copy exists for. That third route is inert on a JVM, where the
running program is `java`. The location is named by `builder/core/BuilderPackPaths.java#ROOT` (`specs`).
What the fallback refuses is a silent swap: a fallback answering after the class loader *located* the
resource logs a warning naming both copies, and a total miss names every route it tried rather than
guessing file names. None of it is proven on an image — nothing on record drives a pack flow from
`./target/smith`, so whether a GraalVM image reports its own executable path (the third route needs it,
the second does not) and whether the two globs carry all 48 files both stay open. The pack flow's
*reflection* needs therefore still enter the file only when a binary first trips over them.

## CI builds it on any PR that could change it

`.github/workflows/native-image.yml` triggers on `pull_request` filtered to the workflow itself,
`Makefile`, any `pom.xml`, any `.java` file and any `src/main/resources/**`, plus manual dispatch — no
push trigger, no schedule, and it is a blocking check. It downloads **GraalVM 25.0.2** from
`graalvm-ce-builds` rather than a setup action, asserting `JAVA_HOME` is the unpack root because
`bin/native-image` is a symlink into `lib/svm`; adds 6 G of swap and caps the compiler with
`-J-Xmx10g`; then runs `make native` under a 75-minute timeout. Before invoking anything it asserts a
`specs/` folder sits beside the binary, failing with a note about the missing `target/specs` when there is
none — the no-argument smoke cannot notice that absence, and a binary without the folder refuses on its
first pack flow. The smoke runs the binary with no
arguments and requires a `Usage: smith` banner and exit code ≤ 2 (picocli exits 2 by design; 1/137/139
would mean a crash). The artifact is two paths — `prod/embabel-smith-builder/target/smith` and
`prod/embabel-smith-builder/target/specs` — uploaded with their common ancestor stripped so an unpacked
download has the folder beside the binary, and kept 7 days. A green run does **not** cover any other
module, any live model call, any non-Linux platform, or the test suite — the workflow header says so at
`:11-19`. See [[build-tooling]] for what the other workflow verifies.

## The skill path, measured on both binaries

`docs/12-backlog.md` §Builder — skill scripts, the bullet that opens "The switch-ON native image cannot load
a skill folder", runs one `--target skill` command line through two images built from the same
tree. The image whose build baked the switch in dies uncaught in `main` at the eval stage:
`kotlin.reflect.jvm.internal.KotlinReflectionInternalError: Unresolved class: class
com.embabel.agent.skills.spec.SkillDefinition`, through `EvalRunner.run → SkillScriptHost.skillsFor →
Skills.withLocalSkills → DefaultDirectorySkillDefinitionLoader.load` into Jackson's Kotlin introspection.
The control — the image built with no `SPRING_APPLICATION_JSON` — completes that same command
line and prints a verdict (`SKILL BUILD BLOCKED at eval …`, `skill lift: +0.000`), with **0** occurrences
of that error. So no page may say the native binary cannot reach the skill path: it reaches it and
finishes it. What the switch-ON image cannot carry is the engine bean, and the same build on a
plain JVM with the same switch does reach the tool loop — which is what makes the gap the image's
reflection metadata rather than the wiring.

That silence was the gap, and the hand-written half of it is now closed:
`builder-native-hints/reachability-metadata.json` names `skills.spec.SkillDefinition`, `SkillMetadata` and
`skills.Skills`.
The committed traced file still greps no hit for `com.embabel.agent.skills.*`, and that silence is not
evidence that nothing was missing: the traced hints came from `ab28a90` (2026-11-23 03:46), and `2b16c27`
(13:08 the same day) is the commit that made the path reachable, so the tracer never saw a call site to
record (the same bullet of `docs/12-backlog.md` §Builder — skill scripts, and the same
reasoning at `docs/08-builder.md`). An entry being present is not
the same claim as an entry working, so the claim here is a run: `target/smith skill-run --skill-dir
<published-folder> <request>` on an image built from this tree prints `skills loaded: <name>`, answers the
request, exits **0**, and logs **0** occurrences of `Unresolved class`. Two of the three entries were only
findable that way. With `skills.spec` registered and nothing else, the image loads the folder and prints
that line, then dies one frame later at `Unresolved class: class com.embabel.agent.skills.Skills` —
`PromptRunner.withReference` calls `Skills.tools()`, which hands the reference to
`MethodToolFactory.fromInstance`, and that enumerates the reference's declared members over
`KClass.memberScope`. And with neither option field registered, the image registers `smith skill-run` and
rejects its own arguments, because `addSubcommand` is a compile-time call while picocli's view of a
command's shape is reflective. Neither gap was reachable from a trace or from the switch-OFF control build:
with the engine off nothing in `prod/` constructs a `Skills` for `tools()` to run on, so this route is the
first thing that ever asked. Two qualifiers on what that proves: the toolchain was `25-graalce`
(GraalVM CE 25+37) rather than the `25.0.2` the workflow pins, and the run carries no engine — its own
loader logs `Skill '…' contains scripts, but script execution is not yet supported`, which is the AOT rule
below behaving as documented rather than a fresh gap. A script tool *callable* inside an image still needs
one built with the switch baked in, and stays unmeasured.

Why no runtime switch rescues an image built without the property is an AOT rule rather than a boot-time
one: `process-aot` evaluates the engine's `@ConditionalOnProperty`, so an image built without the property
in the Maven environment cannot have the bean added afterwards by any runtime switch — `MAVEN_OPTS=-D…`
does not reach `process-aot`, while `SPRING_APPLICATION_JSON` in the build environment does
(`docs/12-backlog.md` §Builder — skill scripts, same bullet). A boot started with the property set therefore proves nothing about which
beans the image holds; the build already decided that. Nor does a green boot prove anything about script
execution in either direction, because the engine builds its Truffle `Context` lazily inside `execute` —
nothing runs at startup that could fail (`docs/12-backlog.md` §Builder — skill scripts, same bullet).

What a green native run proves is therefore still only what the section above says it proves. Script
execution *inside* an image has never been observed on either binary: the switch-baked one died before the
tool loop and the control never had an engine to run. The task list that tracked that open item went
with the planning trees, so what survives at HEAD is the finding itself in `docs/12-backlog.md` §Builder
— skill scripts, which records that the registration landed, plus the one unchecked
`builder-skill-script-host` box left in §Pending right now — 4.2, a wiki-and-Pages chore. What settles
the question is therefore still a run rather than a checkbox: a booted image loading a folder, not a
successful build. The native half is a hint with a reason attached, not a
redistribution of what the binary can do.
