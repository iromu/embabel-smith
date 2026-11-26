---
title: "JavaScript written to run on both skill-script hosts"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "docs/13-skill-script-javascript.md"
  - "docs/08-builder.md"
  - "docs/04-poc-run-results.md"
  - "docs/12-backlog.md"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/script/GraalJsSkillScriptExecutionEngine.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/script/SkillScriptEngineConfiguration.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/script/ScriptExecutionResults.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/PublishedSkillHost.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/cli/SkillRunCommand.java"
  - "prod/embabel-smith-builder/src/main/resources/prompts/generate_skill.jinja"
  - "prod/embabel-smith-builder/src/main/resources/skills/skill-card-generator/scripts/discover_assets.js"
  - "prod/embabel-smith-builder/src/main/resources/skills/skill-card-generator/scripts/render_card.js"
  - "prod/embabel-smith-builder/src/main/resources/skills/skill-card-generator/scripts/validate_submission.js"
  - "prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/skill/script/SkillCardGeneratorScriptsTest.java"
  - "prod/embabel-smith-builder/pom.xml"
  - "pom.xml"
  - "poc/poc-26-graaljs-skill-scripts/src/main/java/com/embabel/smith/skillscripts/Es2026Conformance.java"
updated_at: "2026-11-26"
---

# JavaScript for both skill-script hosts

A generated skill may carry `scripts/*.js` beside its `SKILL.md`. Those scripts run in two places: inside the
builder's own JVM on embedded GraalJS ([[skill-scripts-graaljs]]), and — once a reader copies the published
folder out — under whatever `node` that reader already has. A script written for only one of the two works
until it does not: the in-JVM engine has no `require`, and Node has no `args` global. The durable statement
of the shared surface is `docs/13-skill-script-javascript.md`; this page is its summary and its wiring.

## The two hosts

| Host | What it is | What it adds |
| --- | --- | --- |
| `GraalJsSkillScriptExecutionEngine` | GraalJS as a Java library inside the builder's JVM — no Node, no container, no GraalVM distribution | `args`, `stdin`, `readFile`, `writeFile`; denies files, environment, network, threads, processes, host classes |
| `node` on a reader's machine | the ordinary way to run a `.js` file | `require`, `module`, `__dirname`, `Buffer`, timers, `fetch`, the whole npm ecosystem |

The engine is off by default: `SkillScriptEngineConfiguration.ENABLED_PROPERTY` is
`smith.builder.skill-scripts.enabled` with `@ConditionalOnProperty(havingValue = "true")` and no
`matchIfMissing`, so an unset property leaves Embabel's own default engine in place — one that claims no
language and therefore offers no script tool at all. Installing the engine *adds* tools to a build, which is
why it is an explicit decision rather than a classpath accident.

Node is not a supported deployment target of this product. It matters anyway: a script is the one part of a
published skill a reader can run by hand; the `ProcessSkillScriptExecutionEngine` Embabel ships spawns
`node` for the same files; and a script that also runs under Node is one whose behaviour was never decided by
an embedding accident.

**The rule this page exists to state: write plain ECMAScript against the surface below, feature-detect
anything above it, and the same file runs on either host.**

## The surface a script is given

`GraalJsSkillScriptExecutionEngine.PROLOGUE` evaluates a handful of globals into a fresh `Context` before the
script body. `args` and `stdin` are bound as real guest values — a JS array, not a host-list proxy, so
`args.join(',')` works — and the rest is defined guest-side, so the only host object reachable from a script
is its `Bridge`.

| Global | Value | Under `node` |
| --- | --- | --- |
| `args` | the arguments the model passed, as a string array | not defined — use `process.argv.slice(2)` |
| `stdin` | the piped input as a string, `""` when there was none | not defined — read fd 0 |
| `console.log` / `.error` / `.warn` | captured; `log` to stdout, `error`/`warn` to stderr | same |
| `process.argv` | the same array as `args` | `[execPath, scriptPath, …]` |
| `process.env` | `{}` — the host environment is not for the script | the real environment |
| `process.exit(code)` | stops the script, reports `code`, is *not* an error | same |
| `readFile(name)` | a file staged for the run, or a file of the skill's own folder | not defined |
| `writeFile(name, content)` | into this run's output directory, returned as artifacts | not defined |

Deliberately absent, all of it: `require`, `module`, `__dirname`, `Buffer`, `setTimeout`, `setInterval`,
`queueMicrotask`, `performance`, `TextEncoder`, `TextDecoder`, `URL`, `URLSearchParams`, `structuredClone`,
`AbortController`, `crypto`, `fetch`, `console.table`, `process.cwd`, `process.platform`, `process.stdout`.
Each is absent because the sandbox denies the capability behind it, not because GraalJS lacks it: `Context`
is built with `allowIO(false)`, `allowEnvironmentAccess(NONE)`, `allowCreateProcess(false)`,
`allowCreateThread(false)`, `allowNativeAccess(false)`, `allowHostClassLookup(name -> false)` and
`HostAccess.EXPLICIT`. A script that reaches for a denied global fails with a `TypeError` naming it, which
the model sees as tool output; nothing puts a Java stack into a prompt.

### Four budgets

| Budget | Default | When it trips |
| --- | --- | --- |
| wall clock | 5 000 ms | a watchdog cancels the `Context`; reported as a time-limit failure |
| guest statements | 25 000 000 | deterministic, and the one that holds on a loaded machine |
| captured output | 100 000 chars, stdout plus stderr | the rest is dropped with a truncation marker |
| script size | 512 000 bytes | refused in `validate`, before any context is built |

The statement budget is a real ceiling: POC 26's own comparison script hit it at 100 000 iterations while
Node ran the same loop happily. Write work that finishes; do not write a script that streams a million rows.

### `.js` is a script, `.mjs` is a module

`scriptSource()` compiles `.mjs` with MIME type `application/javascript+module`, which is what grants
`import`, `export` and top-level `await`. A `.js` file is compiled as a plain script, matching how a CLI file
behaves under Node. Module mode buys syntax, not reach.

## The portability surface, measured

Three arms: Node v26.13.1, and GraalJS on the two versions this repository builds with — `25.0.2`, which
`prod/embabel-smith-builder/pom.xml` pins for the shipped artifact, and `25.4.4.1.1`, the root
`graalvm.version` POC 26 measured against. All on Zulu OpenJDK 25 with no GraalVM distribution, which is the
configuration the engine is designed for. 64 probes over the language and host surface, each executed as one
unmodified plain-script file under every arm, passing by completing rather than by feature-sniffing.

The rows that decide how a script is written:

- ES2022–ES2025 (`Array.at`, class fields, static blocks, `WeakRef`, `BigInt`, `findLast`, `Object.groupBy`,
  iterator helpers, `Intl`, `String.replaceAll`, `toSorted`, `RegExp` `hasIndices`, `Error.cause`,
  `Float16Array`): present on all three arms. This is the shared surface.
- ES2026 (`Iterator.concat`, `Array.fromAsync`, `Error.isError`, `Map`/`WeakMap.getOrInsert*`,
  `Uint8Array.toBase64`, `JSON.rawJSON`): absent on 25.0.2, present on 25.4.4.1.1, present on Node.
  `Math.sumPrecise` is the odd one — absent on Node 26, absent on 25.0.2, present on 25.4.4.1.1.
- `Temporal`: Node only. `Worker`: nowhere.
- The Node-only host globals (`require`, `Buffer`, timers, `URL`, `crypto`, `fetch`, …) are all absent from
  the engine, and the engine's `args`, `stdin`, `readFile`, `writeFile` are absent from Node.

Two readings matter more than the rows.

**The ES2026 row is a version accident, not a design.** GraalJS 25.0.2 rejects the option outright —
`--js.ecmascript-version=2026` fails with *"Supported values are 5 to 16 or 2015 to 2025"* — so on the
version the shipped builder runs the ceiling is ES2025 and no configuration lifts it. The builder pins that
version for native-image compatibility (`prod/embabel-smith-builder/pom.xml`); the root property stays at
25.4.4.1.1 because POC 26 measured its ES2026 claims on that pair and `poc/` is frozen evidence. POC 26's
14/14 conformance is true of 25.4.4.1.1 and is not true of the artifact that ships.

**Node is not a superset.** Node 26 lacks `Math.sumPrecise`, the headline ES2026 numeric feature, while the
newer GraalJS has it. There is no host whose feature list can simply be targeted.

**Therefore: write to ES2022–ES2025, which every arm has, and feature-detect anything above it.**
`--js.ecmascript-version` is the knob a *host* can turn to pin a level; a script cannot rely on the host
having turned it.

Three limits travel with the numbers: they are probes, not the ECMA-262 conformance suite, so a feature
outside the list is unverified rather than known-good; they are pinned to these versions, and the ES2026 row
moves with the artifact; and the engine arm ran through a faithful reimplementation of the engine's
configuration rather than through `SkillRunCommand`, so if the two ever drift the tree wins.

## Rules that follow

1. **One file, no imports.** Module syntax exists in `.mjs`; module *resolution* does not, because resolving
   a sibling is a filesystem read and the sandbox denies filesystem reads. Measured in one directory with two
   files: `import { X } from "./b.js"` works under Node and fails in the engine with *Operation is not
   allowed for: b.js*; `import fs from "node:fs"` fails with *Socket IO is not allowed*. A second script
   under `scripts/` is therefore a second tool, not a library — shared helpers get duplicated or the skill
   gets two tools. Top-level `await` is the one module-only feature that is genuinely portable.
2. **Read input from three named doors, in order.** The model's `stdin`, a staged or in-skill file through
   `readFile`, then a shell pipe under Node via `require('node:fs').readFileSync(0, 'utf8')`. Each door is
   tested for existence before use, so its block is inert on the host that lacks it — that guard shape *is*
   the portability technique.
3. **Exit code and stderr are the contract.** `stdout` is the payload the model quotes, `stderr` is one line
   a human can act on, and the exit code is how a rule refusal reaches the agent loop; `process.exit(2)` is
   control flow, which the engine unwinds as a *success* carrying that code, exactly as a CLI does. One
   divergence has no workaround except discipline: the engine reads a script's completion value as its exit
   code, so a file whose last statement is a non-zero integer exits `42` in the JVM and `0` under Node. End
   the file with a call returning `undefined`, and say the code out loud with `process.exit`.
4. **Feature-detect the standard library, never assume it.** `Math.sumPrecise`, `Object.groupBy`,
   `Uint8Array.prototype.toBase64` and `Error.isError` each get a one-line guard and a fallback that does the
   work. This is not ceremony on the shipped 25.0.2 pin, where all four are absent and the fallback branch is
   the one that runs — and a script with no fallback does not fail loudly there, it fails with
   `TypeError: … is not a function`, which reads like a bug in the script. Iterator helpers, generators,
   tagged templates, private fields, `Map`/`Set` algebra, `BigInt` and `Intl` are inside the shared surface.
5. **No waiting, no streaming, no children.** `setTimeout` and `setInterval` do not exist in an embedded
   `Context` — GraalVM's own boundary between the `node` launcher and a Java embedding, not this engine's
   choice — and `fetch`, sockets, subprocesses and threads are denied outright. Promises and `await` work;
   anything needing a timer queue does not. Retry with backoff, polling and concurrency are the agent's
   problem. `Date.now()` exists and is the last source of nondeterminism on the surface, which matters
   because the improvement loop compares runs.
6. **Files by name, and write only artifacts.** `readFile` answers for a file staged for the run (matched by
   basename) or a file inside the skill's own folder; anything else is refused with the list of what *was*
   staged. `writeFile` strips any path and writes into a fresh per-run output directory returned as
   `ScriptArtifact`s, so a script cannot write into the skill it came from. Never build a path from
   model-supplied text.
7. **Numbers as cents, strings, or nothing.** Money is integer cents or `Math.sumPrecise` — rule 4's fallback
   is the cents path. Identifiers wider than a double travel as strings: measured on the 25.0.2 pin, the same
   serializer emitted `91827364550123456789` under Node and `"91827364550123456789"` in the engine.
8. **Stay inside the budgets.** Five seconds and 25 million statements is generous for parse-and-compute work
   and tight for anything iterative over a large corpus. Bound every loop, cap every excerpt — output beyond
   100 000 characters is truncated mid-stream, and a truncated JSON is worse than a short one.
9. **Fail in a way the model can use.** Validate the input and refuse with a message naming the argument to
   fix. `prompts/generate_skill.jinja` tells the generator to state the rules the script enforces so a
   non-zero exit is intelligible and never to tell the agent to work the figures out itself; a script that
   dies with `TypeError: undefined is not an object` invites exactly that fallback.

The reference instance is `docs/13`'s invoice script: it produced byte-identical stdout and identical exit
codes under Node 26 and under the engine on both version arms across four scenarios, including two rule
refusals (exit 4 for an unapproved discount, exit 3 for an unknown category). The rounding is the point of
the exercise — `[0.1, 0.2, 0.3]` sums to `0.6000000000000001` by `reduce` on every arm and to `0.6` through
the guarded helper on every arm.

## Checking a script on both hosts

The engine arm needs no GraalVM, only the two artifacts on a classpath — which is the design's whole claim.
The Node arm is `echo "$INPUT" | node scripts/<name>.js`, and the engine arm is the same file against the
builder's runtime classpath (`mvn -pl prod/embabel-smith-builder dependency:build-classpath`, then a
`java --class-path … Harness.java <script>` whose fifteen lines mirror the engine's `Context` builder and
`PROLOGUE`; copying both rather than reinventing them is what stops the arm measuring the harness). Neither
command is committed as a script in this repository. What *is* committed is the engine half for the shipped
pack: `skill/script/SkillCardGeneratorScriptsTest` unpacks `skills/skill-card-generator/` whole out of the
classpath and runs all three scripts through `GraalJsSkillScriptExecutionEngine` offline, pinning exit codes,
stderr markers, the written artifact and that no run hit a budget. Diff stdout and exit code rather than
pass/fail heuristics — the divergences that bite (completion values, big integers, `Math.sumPrecise`) all
show up as different bytes.

## When neutrality is the wrong goal

A skill that needs npm packages, a real filesystem, a socket, a subprocess or a timer should not be a `.js`
skill script at all: Embabel ships `ProcessSkillScriptExecutionEngine` plus Docker and Podman engines that
spawn a real interpreter and get OS-level containment instead. The in-JVM engine is hardened, not a security
boundary against a determined guest, so untrusted third-party scripts belong in a container
([[skill-scripts-graaljs]]). The published `skill-card-generator` shows the other end of the same judgement:
three scripts, plain ECMAScript, no Node API anywhere in roughly 1 900 lines, and a `discover_assets.js` that
hand-rolls `print`, `joinAbs`, `basename` and a Python-shaped `splitLines` because a shim is cheaper than a
portability bug. Its `SKILL.md` says so on its own terms — the scripts run only when the builder's engine is
switched on, and without it they are listed as text and never run.

## Where this is wired

| Thing | Where |
| --- | --- |
| the engine | `builder/skill/script/GraalJsSkillScriptExecutionEngine` |
| enablement | `builder/skill/script/SkillScriptEngineConfiguration`, `smith.builder.skill-scripts.enabled`, off by default |
| installed via | Embabel's `Skills.withScriptExecutionEngine(engine)` |
| call sites | `cli/BuildCommand` → `skill/SkillScriptHost` (build-internal, with-skill eval arm); `cli/SkillRunCommand --skill-dir` → `skill/PublishedSkillHost` |
| what the generator is told | `prompts/generate_skill.jinja` — the surface, *"Write plain ECMAScript against that surface: it runs in the reader's own JVM, not in Node and not in a browser"*, and the `{"path": "scripts/<name>.js"}` contract |
| the rules around the surface | `docs/08-builder.md` states them in prose — a published folder has a host (`skill/PublishedSkillHost`, reached by `cli/SkillRunCommand`), the engine is off by default, a script's reach is stated by the scanner and the sandbox rather than by a tier, and a folder the loader refuses is *named* by `loading()`; `docs/12-backlog.md` §Builder — skill scripts carries what still stays open |
| GraalJS version | `graalvm.polyglot.version` = 25.0.2 in the builder pom; root `graalvm.version` = 25.4.4.1.1 |
| shipped scripted skill | `prod/embabel-smith-builder/src/main/resources/skills/skill-card-generator/` — nothing in `prod/` main names it; the test unpacks it from the classpath |
| published tier | `security/AgentSpecSecurityScanner` reports `T3_PARTNER` for a skill that ships JavaScript and `T1_COMMUNITY` for the same skill scriptless |
| the evidence | `poc/poc-26-graaljs-skill-scripts`, `docs/04-poc-run-results.md` §7.4 |

The tier row needs a qualifier so it is not read as a gate: it is a **report**. A trust tier is a set of
tool-group roles a pack declares and a script declares none, so no loader consults a tier about a `.js` file
([[trust-tiers]]). Nothing in the tier machinery stops, or permits, the engine. Two ways of turning the tier
into a gate were tried and refused (`docs/13-skill-script-javascript.md`): making the loader refuse an
executable skill is empty, because the check would be a predicate that never sees its subject; and
registering a tool group so the tier has something to read does make the check real, which is still the wrong
move — it retracts the pinned fact that installing the engine adds no tool group, so the surface cannot
silently widen. The gate is bought at a worse price than it is worth when the scanner already refuses the
dangerous scripts and the sandbox already denies the host.

Related: [[skill-scripts-graaljs]] (the engine and the POC 26 SPI story), [[skill-output-target]] (the target
that writes the folder), [[smith-builder]] (the CLI routes), [[trust-tiers]] (why a tier cannot gate a
script), [[model-endpoint]] (the endpoint the live runs use).
