---
title: "Running Agent Skill scripts in the JVM with GraalJS"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "poc/poc-26-graaljs-skill-scripts/README.md"
  - "poc/poc-26-graaljs-skill-scripts/src/main/java/com/embabel/smith/skillscripts/GraalJsSkillScriptExecutionEngine.java"
  - "poc/poc-26-graaljs-skill-scripts/src/main/java/com/embabel/smith/skillscripts/Es2026Conformance.java"
  - "poc/poc-26-graaljs-skill-scripts/src/main/java/com/embabel/smith/skillscripts/ScriptEngineComparison.java"
  - "poc/poc-26-graaljs-skill-scripts/src/main/java/com/embabel/smith/skillscripts/SkillScriptAgent.java"
  - "poc/poc-26-graaljs-skill-scripts/src/main/java/com/embabel/smith/skillscripts/ScriptExecutionResults.java"
  - "poc/poc-26-graaljs-skill-scripts/src/main/resources/skills/invoice-totalling/SKILL.md"
  - "poc/poc-26-graaljs-skill-scripts/pom.xml"
updated_at: "2026-11-14"
---

# Skill scripts in the JVM (POC 26)

An Agent Skill may carry `scripts/*.js`. Embabel 1.5.4 already has the **extension point** —
`SkillScriptExecutionEngine`, installed with `Skills.withScriptExecutionEngine(engine)` and wired
into the tool surface by `Skills.tools()` → `LoadedSkill.getScriptTools(engine)`. What it does not
have is an **in-JVM JavaScript runner**: the default engine logs *"script execution is not yet
supported"*, and the only JavaScript implementation it ships spawns an external `node`.

POC 26 supplies the missing engine. One class of ~525 lines, embedding **GraalJS on a stock JDK** —
two Maven dependencies (`org.graalvm.polyglot:polyglot` plus the `js` POM, which pulls js-language,
regex and the Truffle runtime), no GraalVM, no `node`. Claim C11 ([[what-the-pocs-prove]]) extended
from "a spec reaches an external CLI" ([[runtime-adapter]]) to "a skill carries its own script and has
it executed".

## The script becomes a callable tool

With the engine installed, the sample skill's `scripts/compute_totals.js` shows up in the agent's
tool list: `[activate, listResources, readResource]` becomes
`[…, invoice-totalling_compute_totals]`. The model calls it and the script answers **in-process** —
`netTotal 1259.88` / `payable 1310.28`, computed in 18 ms inside an 18-second model turn. The
`SKILL.md` is unchanged; only the engine is new.

## Why in-JVM is the point

Because the engine is a library rather than a subprocess, the host can enforce things a spawned
interpreter will not: `HostAccess.EXPLICIT`, no host-class lookup, no IO, no environment, no threads,
no native access, no child processes (`GraalJsSkillScriptExecutionEngine.java:197-203`) — plus a
statement budget, a wall-clock watchdog, and capped output. Defaults
(`:73-82`): 5 000 ms timeout, 25 000 000 statements, 100 000 output chars, 512 KB script.

Scripts get a **CLI-shaped surface** rather than raw host access — `args`, `stdin`,
`process.argv`/`exit`, `readFile`, and `writeFile` routed to staged artifacts — via a prologue and a
few `@HostAccess.Export` methods; a `ScriptExit` exception carries `exit` and a capped stream bounds
output.

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
  engines.
- **Kotlin interop is reflective.** Embabel's result types use `@JvmSynthetic` default-argument
  constructors and a `kotlin.time.Duration` value class, so `ScriptExecutionResults` builds them
  reflectively (`box-impl`, `DurationKt.toDuration`).

**Recommendation on record:** adopt the in-JVM engine as the default for `JAVASCRIPT` skill scripts,
keep the process/Docker/Podman engines for other languages, keep `NoOpExecutionEngine` as the opt-out
with its warning — and make the guardrails part of the SPI contract, because an in-JVM engine without
them is worse than an external one.

## Running it

`mvn -pl poc/poc-26-graaljs-skill-scripts test` runs **31 offline tests** (engine, runtime,
conformance, comparison). The tool-loop demo, where the model actually calls the script, is
**endpoint-gated** ([[model-endpoint]]). Related: [[skill-generation]] — the skills whose scripts this
engine executes.
