---
title: "Running Agent Skill scripts in the JVM with GraalJS"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/script/GraalJsSkillScriptExecutionEngine.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/script/SkillScriptEngineConfiguration.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/script/ScriptExecutionResults.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillScriptHost.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/EvalRunner.java"
  - "prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/publish/PublishedSkillScriptToolSurfaceTest.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/PublishedSkillHost.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/cli/SkillRunCommand.java"
  - "prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/skill/PublishedSkillHostTest.java"
  - "prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/skill/PublishedSkillHostModelCallTest.java"
  - "prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/cli/SkillRunCommandTest.java"
  - "prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/SmithBuilderScriptEngineWiringTest.java"
  - "prod/embabel-smith-builder/pom.xml"
  - "pom.xml"
  - "prod/embabel-smith-a2a-starter/src/main/java/com/embabel/smith/a2a/SpecSourceSkills.java"
  - "docs/13-skill-script-javascript.md"
  - "docs/08-builder.md"
  - "docs/12-backlog.md"
  - "docs/04-poc-run-results.md"
  - "prod/embabel-smith-builder/src/main/resources/skills/skill-card-generator/scripts/discover_assets.js"
  - "prod/embabel-smith-builder/src/main/resources/skills/skill-card-generator/scripts/render_card.js"
  - "prod/embabel-smith-builder/src/main/resources/skills/skill-card-generator/scripts/validate_submission.js"
  - "prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/skill/script/SkillCardGeneratorScriptsTest.java"
  - "poc/poc-26-graaljs-skill-scripts/README.md"
  - "poc/poc-26-graaljs-skill-scripts/src/main/java/com/embabel/smith/skillscripts/GraalJsSkillScriptExecutionEngine.java"
  - "poc/poc-26-graaljs-skill-scripts/src/main/java/com/embabel/smith/skillscripts/Es2026Conformance.java"
  - "poc/poc-26-graaljs-skill-scripts/src/main/java/com/embabel/smith/skillscripts/ScriptEngineComparison.java"
  - "poc/poc-26-graaljs-skill-scripts/src/main/java/com/embabel/smith/skillscripts/SkillScriptAgent.java"
  - "poc/poc-26-graaljs-skill-scripts/src/main/java/com/embabel/smith/skillscripts/ScriptExecutionResults.java"
  - "poc/poc-26-graaljs-skill-scripts/src/main/resources/skills/invoice-totalling/SKILL.md"
  - "poc/poc-26-graaljs-skill-scripts/pom.xml"
updated_at: "2026-11-26"
---

# Skill scripts in the JVM (GraalJS — POC 26, now in the builder)

An Agent Skill may carry `scripts/*.js`. Embabel 1.5.4 already has the **extension point** —
`SkillScriptExecutionEngine`, installed with `Skills.withScriptExecutionEngine(engine)` and wired
into the tool surface by `Skills.tools()` → `LoadedSkill.getScriptTools(engine)`. What it does not
have is an **in-JVM JavaScript runner**: the default engine logs *"script execution is not yet
supported"*, and the only JavaScript implementation it ships spawns an external `node`.

POC 26 supplied the missing engine, and the product now ships it. **POC 26 is the measurement, not the
location**: the engine that runs inside a build lives in
`prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/script/` —
`GraalJsSkillScriptExecutionEngine.java` (one class, ~600 lines), `SkillScriptEngineConfiguration.java`
(the gate that publishes it as a bean) and `ScriptExecutionResults.java` (the Kotlin-interop shim). It
embeds **GraalJS on a stock JDK** on the POC's terms — two Maven dependencies
(`org.graalvm.polyglot:polyglot` plus the `js` POM, which pulls js-language, regex and the Truffle runtime),
no GraalVM, no `node` (`prod/embabel-smith-builder/pom.xml:89-97`). So the guardrail numbers below are cited
from the prod file and the measurements stay cited to the POC. Claim C11 ([[what-the-pocs-prove]]) extended
from "a spec reaches an external CLI" ([[runtime-adapter]]) to "a skill carries its own script and has
it executed".

## The script becomes a callable tool

With the engine installed, the sample skill's `scripts/compute_totals.js` shows up in the agent's
tool list: `[activate, listResources, readResource]` becomes
`[…, invoice-totalling_compute_totals]`. The model calls it and the script answers **in-process** —
`netTotal 1259.88` / `payable 1310.28`, computed in 18 ms inside an 18-second model turn. The
`SKILL.md` is unchanged; only the engine is new. The name is the skill name, an underscore and the file's
basename joined **without sanitizing either** — so a hyphenated skill gives
`cold-store-goods-receipt_check-temperature`, at the listing — on a reference-carrying call the reference's
own sanitized name sits in front of that whole join, which [[skill-output-target]] keeps
(`publish/PublishedSkillScriptToolSurfaceTest.java:73`).

## Why in-JVM is the point

Because the engine is a library rather than a subprocess, the host can enforce things a spawned
interpreter will not: `HostAccess.EXPLICIT` and no host-class lookup
(`skill/script/GraalJsSkillScriptExecutionEngine.java:220-221`), no IO, no environment, no threads,
no native access, no child processes (`:218-228`) — plus a statement budget, a wall-clock watchdog, and
capped output. Defaults (`:83-99`): `DEFAULT_TIMEOUT_MILLIS` 5 000 ms, `DEFAULT_STATEMENT_LIMIT`
25 000 000 statements, `DEFAULT_MAX_OUTPUT_CHARS` 100 000 chars, `DEFAULT_MAX_SCRIPT_BYTES` 512 000
(512 KB). Those four are **unchanged from the POC**
  (`poc/poc-26-graaljs-skill-scripts/src/main/java/com/embabel/smith/skillscripts/GraalJsSkillScriptExecutionEngine.java:64-75`
— the port moved the citation, not the number), as are the engine's identity `NAME = "graaljs-in-jvm"`
(`:80`) and `SUPPORTED = {JAVASCRIPT}` (`:128`).

Scripts get a **CLI-shaped surface** rather than raw host access — `args`, `stdin`,
`process.argv`/`exit`, `readFile`, and `writeFile` routed to staged artifacts — via a prologue and a
few `@HostAccess.Export` methods; a `ScriptExit` exception carries `exit` and a capped stream bounds
output. The prod engine carries the same prologue
  (`skill/script/GraalJsSkillScriptExecutionEngine.java:100-125`).

## The gate, and who installs it in the product

Nothing installs the engine implicitly. `skill/script/SkillScriptEngineConfiguration.java:40,47` publishes
it as a `SkillScriptExecutionEngine` bean under
`@ConditionalOnProperty(name = "smith.builder.skill-scripts.enabled", havingValue = "true")` with **no
`matchIfMissing`**: unset means absent, which is Embabel's default engine claiming no language. The reason
is on the record in that file's javadoc — Embabel lists a skill's script as a tool only when an installed
engine claims its language (`LoadedSkill.getScriptTools` filters on `supportedLanguages()`), so installing an
engine **adds tools** to a build: a script becomes callable that was inert text a run ago. That is somebody's
explicit decision, not a side effect of a dependency landing on the classpath.

Two callers in `prod/` main install it, on two different folders. The build-internal one:
`skill/SkillScriptHost.java:90-91` writes the skill *under
build* out as an Agent Skill folder under a temporary directory and hands back a `Skills` built with
`withLocalSkills(root().toString())` then `withScriptExecutionEngine(engine)`;
`skill/EvalRunner.java:116-118` attaches
that reference to the **with-skill arm only**, which is why the two arms are still one prompt substitution
each with the switch off — the default — and why the lift numbers taken with it off still mean what they
measured. The scratch folder is registered for removal at JVM exit rather than deleted when the call ends
(`skill/SkillScriptHost.java:45-54,89`), deliberately: `LoadedSkill.listResources` reads `scripts/` from disk
per call — the host's own javadoc flags that as an inference from behaviour, not a line read from source — so
an earlier cut that deleted the folder at run end left the skill activating and its body reading back while
offering no script tool at all.

**A published folder now has a host.** `skill/PublishedSkillHost` takes a directory as `publish` wrote it,
loads it through Embabel's own `Skills.withLocalSkills(root)`, and attaches the engine only when one was
handed in (`skill/PublishedSkillHost#skills`); `cli/SkillRunCommand --skill-dir` is the route that hands the
resulting tools to a model call. `prod/embabel-smith-a2a-starter/…/SpecSourceSkills` still publishes
`io.a2a.spec.AgentSkill` card entries, which describe a capability rather than load one — `docs/08-builder.md`
says that of it in prose, and keeps the three neighbours of that rule with it: the engine is published as a
bean only under `smith.builder.skill-scripts.enabled`, **off by default**; a script's reach is stated by the
scanner and the sandbox rather than by a trust tier; and a folder the loader refuses is *named* by
`PublishedSkillHost#loading()` rather than left in the reader's log. Two routes were once on the table for
this seam and the reader's half was taken as this CLI route rather than an A2A one; the planning folder that
recorded that choice (its design decision 8) was purged into `docs/` on 2026-11-25, so
`docs/12-backlog.md` §Builder — skill scripts is now where that fork and what still remains open (the
readers, not the mechanism) are stated, and §Builder — skill target keeps the two constraints the host
inherits (the unsanitized tool name, the loader's silent refusal). The host adds no rule of its own and
reads no trust tier — the two
surfaces that bound a script remain the scanner that refused the dangerous ones and the sandbox that denies
the host, which is what `skill/PublishedSkillHostTest` pins.

## The GraalJS version is an image constraint, not taste

`prod/embabel-smith-builder/pom.xml:34` pins `graalvm.polyglot.version` to **25.0.2** while the root
`pom.xml:38` keeps `graalvm.version` at 25.4.4.1.1. That is not drift: at 25.4.4.1.1 the native-image build
dies inside `TruffleBaseFeature` with a `NoSuchMethodError`, because the distributions this repo builds the
shipped artifact with — `scripts/native.sh` on sdkman 25-graalce, `.github/workflows/native-image.yml` on
`GRAAL_VERSION` 25.0.2 — are both on the 25.0.x line, whose `OptimizedTruffleRuntime` exposes the
`getLoopNodeFactory()` that `SubstrateTruffleRuntime` asks for (`prod/embabel-smith-builder/pom.xml:25-33`).
The root property stays put because `poc/` is frozen evidence and POC 26 measured its ES2026 claims against
the 25.4.4.1.1 pair, so the divergence is scoped to the module that ships as a binary. Bump it only together
with the distribution: the pairing, not the number, is what matters.

## Measured, and where it falls short

- **ES2026: 14/14 probes** executed as real scripts (`Es2026Conformance`) — `Math.sumPrecise`,
  `Iterator.concat`, `Array.fromAsync`, `Error.isError`, `Map.getOrInsert`/`getOrInsertComputed`,
  `Uint8Array` base64/hex, `JSON.rawJSON`. These are the edition's named features, **not** the
  ECMA-262 conformance suite — a feature outside the probe list is unverified, not known-good.
- **Interpreter speed is the honest cost.** Without Graal's compiler on `--upgrade-module-path`,
  JavaScript runs interpreted at roughly **10× Node** on CPU-bound work (**396 ms vs 38 ms**,
  `ScriptEngineComparison`). Adding `org.graalvm.compiler:compiler` recovers it but pins deployment
  to a JVMCI-capable JVM — deliberately left open.
- **The statement budget is a real ceiling.** The 100 000-iteration comparison script was stopped by
  the 25 M limit while Node ran it happily. Raise it for trusted skills; do not remove it for
  untrusted ones.
- **Not a Node runtime** — no `require`, no npm, no `process.env`, no real filesystem. A skill that
  needs the Node ecosystem still wants the process engine.
- **Hardened, not a security boundary.** GraalJS 25.4's `SandboxPolicy.CONSTRAINED` and above reject
  a builder that sets stdout/stderr — which a script engine must do — so this engine relies on the
  explicit `allow*` lockdowns instead. Untrusted third-party scripts belong in the Docker/Podman
  engines. (Observed on the POC's 25.4 pair; the product ships 25.0.2 — see the version note above.)
- **Kotlin interop is reflective.** Embabel's result types use `@JvmSynthetic` default-argument
  constructors and a `kotlin.time.Duration` value class, so `ScriptExecutionResults` builds them
  reflectively (`box-impl`, `DurationKt.toDuration`).

**Recommendation on record:** adopt the in-JVM engine as the default for `JAVASCRIPT` skill scripts,
keep the process/Docker/Podman engines for other languages, keep `NoOpExecutionEngine` as the opt-out
with its warning — and make the guardrails part of the SPI contract, because an in-JVM engine without
them is worse than an external one.

## Offered to a model: proven offline, unmeasured on a live endpoint

That a script tool reaches a model's tool list is now proven twice, both offline.
`skill/PublishedSkillHostModelCallTest` boots the builder context against a stub OpenAI-compatible endpoint
on a loopback port, publishes a scripted skill through the real `FolderPublisher`, and answers the first
request with a call for the script tool; the assertion is the script's own stdout arriving in the follow-up
request, so the dispatch was Embabel's own loop and no test invoked a tool by hand. Pointing that scripted
call at a tool that does not exist makes the follow-up carry a refusal naming the real tool and the stdout
assertion fails, which is what shows the test is not vacuous. `skill/EvalRunnerScriptArmTest` covers the
build-internal arm against a recording caller: the with-skill call is the reference-carrying one, that
reference's tool list contains `<skill>_<script>`, and the baseline arm reaches no reference-carrying call
at all.

One switch-on build *has* been run end to end, and its number is a warning rather than a result: with the
switch on, the reader's loader refused the folder the host had written, all 27 calls the with-skill arm made
came back `Skill not found`, and the run printed `skill lift: -0.125 (with 0.688 vs baseline 0.813)` — two
lost checks out of sixteen, from the refusals rather than from anything the skill said
(`docs/08-builder.md`, `docs/12-backlog.md` §Builder — skill scripts). A switch-on lift is comparable to a
switch-off one only when the folder actually loaded, and the log says that out loud — `Skill not found` on
every call plus one early `Skipping unloadable skill` WARN.

What remains unmeasured is the model's half of a real run. No live build has yet emitted a script into a
published folder: the one switch-on build was blocked before `publish`, and
`find examples/out -type d -name scripts` names nothing at HEAD (`docs/12-backlog.md` §Builder — skill
scripts). Two live logs from this machine — `/tmp/sentinel-skill-off-baseline.log` (ASCII) and
`/tmp/skill-jvm-run.log` (read as binary, so `grep -a` for both) — show `…_activate` and `…_listResources`
executing, `listResources` returning `No scripts found` for the skill, and no script-shaped tool name in any
offered list; that is an absence of evidence in logs that were never built to answer the question, the same
question the POC's tool-loop demo answered only for a fixture skill ([[model-endpoint]]). The scripted skill
folders in the repository at HEAD are the POC 26 fixture, `embabel-smith-skillevaluator`'s Python fixtures,
and the hand-ported `skill-card-generator` pack under the builder's own resources (below) — none of them
generator output. So the mechanism is proven and the generator's habit is not.

## What covers it in the product

Five prod test classes now stand behind this: `skill/SkillScriptTest` (where a generated script may land
inside the folder), `skill/script/GraalJsSkillScriptExecutionEngineTest` (the engine contract, offline),
`skill/script/SkillScriptEngineBeanTest` (the bean appears with the switch on and not with it unset),
`skill/script/SkillScriptToolSurfaceTest` (the tool list a model would be offered, from a folder it built
itself), and `skill/EvalRunnerScriptArmTest` (the two arms differing only by the switch). Plus
`publish/PublishedSkillScriptToolSurfaceTest`, which hands a folder `SkillPublishable` actually wrote to
Embabel's own loader and finds the script tool present with an engine and absent without one — the
containment half, and the only test that would notice a publisher writing scripts one directory over. Each
of those also activates the skill by name and requires its body back, because `loadAll` quietly skips a
skill whose body references a file the folder lacks, and a test that only read `tools()` would report that
refusal as containment. Four more stand behind the reader's half of the seam:
`skill/PublishedSkillHostTest` (a folder `SkillPublishable` wrote activates, lists and reads its resources,
and its script tool is the skill-plus-filebase join with an engine and absent without one; a folder the loader
refuses is *named* rather than silently dropped), `cli/SkillRunCommandTest` (the route's model call arrives
carrying the published folder's tools, and nothing loads means no call is spent),
`skill/PublishedSkillHostModelCallTest` (the deterministic leg of the execution claim: a scripted local
endpoint answers with a call for the script tool, so Embabel's own loop dispatches into the engine with no
model involved, and the assertion is the script's stdout arriving in the follow-up request rather than a tool
any test invoked by hand), and
`SmithBuilderScriptEngineWiringTest`, which boots the launcher's own configuration rather than the bean and
asserts the pair — property unset, that path constructs no engine and offers no script tool; property set,
exactly one. The pair is what guards `SmithBuilderApplication`, whose engine lookup was an untested seam
before it: replacing the lookup with an unconditional `new GraalJsSkillScriptExecutionEngine()` fails both
of them, which is how they were shown not to pass vacuously. The dispatch test was shown the same way, by
scripting a call for a tool that does not exist: the follow-up then carries a refusal naming the real one and
the stdout assertion fails. A sixth stands behind a skill the builder did not generate:
`skill/script/SkillCardGeneratorScriptsTest` copies the shipped `skill-card-generator` folder whole out of the
builder's classpath resources into a temp root — the scripts read their own folder's `references/`, so a
folder carrying only `scripts/` would prove something else — and runs all three scripts through this module's
engine offline, pinning the documented exit codes, the stderr marker classes, the rendered card as a written
artifact rather than an echo, and that no run reached a budget.

## Running it

`mvn -Ppoc -pl poc/poc-26-graaljs-skill-scripts test` runs **31 offline tests** (engine, runtime,
conformance, comparison) — the `-Ppoc` is not optional, because the POC reactor sits behind that profile and
Maven can only select a module already in the reactor. The launcher is
`mvn -pl poc/poc-26-graaljs-skill-scripts spring-boot:run` (its sections 1–5 print before Spring starts and
need no endpoint; section 6, the model calling the script through the tool loop, is
**endpoint-gated** ([[model-endpoint]])) — `docs/04-poc-run-results.md` §8 is the run book. The product half
runs with the builder module's own tests
(`mvn -pl prod/embabel-smith-builder test`). Related: [[skill-generation]] and [[skill-output-target]] —
the skills whose scripts this engine executes, and the target that writes the folder it reads — plus
[[skill-script-javascript]], the host-neutral JavaScript those scripts are written against.
