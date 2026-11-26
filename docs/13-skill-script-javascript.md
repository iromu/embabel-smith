# JavaScript for skill scripts

A generated skill may carry `scripts/*.js` beside its `SKILL.md`. Those scripts run in two places:
inside the builder's own JVM on embedded GraalJS, and — the moment a reader copies the published
folder out — under whatever `node` that reader already has. A script written for only one of the two
works until it does not: the in-JVM engine has no `require`, and Node has no `args` global.

This page states the surface those two hosts share, the idioms that stay on it, and the code that
demonstrates each idiom. **Every snippet here was executed under both hosts** — method and versions
in [Provenance](#provenance-of-every-number-here).

## The two hosts

| Host | What it is | What it adds |
| --- | --- | --- |
| `GraalJsSkillScriptExecutionEngine` | GraalJS as a Java library inside the builder's JVM — no Node, no container, no GraalVM distribution | `args`, `stdin`, `readFile`, `writeFile`; denies files, environment, network, threads, processes, host classes |
| `node` on a reader's machine | the ordinary way to run a `.js` file | `require`, `module`, `__dirname`, `Buffer`, timers, `fetch`, the whole npm ecosystem |

The engine is not on by default: `SkillScriptEngineConfiguration.ENABLED_PROPERTY` is
`smith.builder.skill-scripts.enabled`, and `@ConditionalOnProperty(havingValue = "true")` means an
unset property leaves Embabel's own default engine in place, which claims no language and so offers no
script tool at all. Installing the engine *adds* tools to a build, which is why it is somebody's
explicit decision rather than a classpath accident (`docs/08-builder.md` §Build targets).

Node is not a supported deployment target of this product. It matters anyway, for three reasons: a
script is the one part of a published skill a reader can run by hand; the `ProcessSkillScriptExecutionEngine`
Embabel ships spawns `node` for exactly the same files; and a script that runs under Node is a script
whose behaviour was never decided by an embedding accident.

**The rule this page exists to state: write plain ECMAScript against the CLI-shaped surface below,
feature-detect anything above it, and the same file runs on either host.**

## The surface a script is given

`GraalJsSkillScriptExecutionEngine.PROLOGUE` evaluates a handful of globals into a fresh `Context`
before the script body. The engine binds `args` and `stdin` as real guest values — a JS array, not a
host-list proxy, so `args.join(',')` works — and defines the rest guest-side, so the only host object
reachable from a script is its `Bridge`.

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

Deliberately absent, all of it: `require`, `module`, `__dirname`, `Buffer`, `setTimeout`,
`setInterval`, `queueMicrotask`, `performance`, `TextEncoder`, `TextDecoder`, `URL`,
`URLSearchParams`, `structuredClone`, `AbortController`, `crypto`, `fetch`, `console.table`,
`process.cwd`, `process.platform`, `process.stdout`. Each is absent because the sandbox denies the
capability behind it, not because GraalJS lacks it — `Context` was built with `allowIO(false)`,
`allowEnvironmentAccess(NONE)`, `allowCreateProcess(false)`, `allowCreateThread(false)`,
`allowNativeAccess(false)`, `allowHostClassLookup(name -> false)` and `HostAccess.EXPLICIT`.

A script that reaches for a denied global fails with a `TypeError` naming it, which the model sees as
tool output and can act on. Nothing about a failure puts a Java stack into a prompt.

### Four budgets

| Budget | Default | When it trips |
| --- | --- | --- |
| wall clock | 5 000 ms | a watchdog cancels the `Context`; reported as a time-limit failure |
| guest statements | 25 000 000 | deterministic, and the one that holds on a loaded machine |
| captured output | 100 000 chars, stdout plus stderr | the rest is dropped with a truncation marker |
| script size | 512 000 bytes | refused in `validate`, before any context is built |

The statement budget is a real ceiling: POC 26's own comparison script hit it at 100 000 iterations
while Node ran the same loop happily. Write work that finishes; do not write a script that has to
stream a million rows.

### `.js` is a script, `.mjs` is a module

`scriptSource()` compiles `.mjs` with MIME type `application/javascript+module`, which is what grants
`import`, `export` and top-level `await`. A `.js` file is compiled as a plain script, matching how a
CLI file behaves under Node. **Module mode buys syntax, not reach** — see the first rule.

## The portability surface, measured

Three arms: Node v26.13.1, and GraalJS on the two versions this repository actually builds with —
`25.0.2`, which `prod/embabel-smith-builder/pom.xml` pins for the shipped artifact, and `25.4.4.1.1`,
the root `graalvm.version` that POC 26 measured against. All on Zulu OpenJDK 25 with no GraalVM
distribution, which is the configuration the engine is designed for.

| Surface | node 26 | GraalJS 25.0.2 | GraalJS 25.4.4.1.1 |
| --- | :---: | :---: | :---: |
| ES2022 `Array.at`, class fields, static blocks, `WeakRef`, `BigInt` | ✓ | ✓ | ✓ |
| ES2023 `findLast` · ES2024 `Object.groupBy` · ES2025 iterator helpers | ✓ | ✓ | ✓ |
| `Intl.Segmenter`, `NumberFormat`, `DateTimeFormat`, `Collator` | ✓ | ✓ | ✓ |
| `String.replaceAll`, `Array.toSorted`, `RegExp` `hasIndices`, `Error.cause` | ✓ | ✓ | ✓ |
| ES2026 `Iterator.concat`, `Array.fromAsync`, `Error.isError`, `Map`/`WeakMap.getOrInsert*`, `Uint8Array.toBase64`, `JSON.rawJSON`, `JSON.parse` source arg | ✓ | ✗ | ✓ |
| ES2026 `Math.sumPrecise` | ✗ | ✗ | ✓ |
| ES2026 `Float16Array` | ✓ | ✓ | ✓ |
| `Temporal` | ✓ | ✗ | ✗ |
| `Atomics`, `SharedArrayBuffer` (present; no threads to share them with) | ✓ | ✓ | ✓ |
| `console.log`/`error`, `process.argv`/`exit`/`env` | ✓ | ✓ | ✓ |
| `Worker` | ✗ | ✗ | ✗ |
| `require`, `module`, `__dirname`, `Buffer`, timers, `performance`, `TextEncoder`/`TextDecoder`, `URL`, `structuredClone`, `AbortController`, `crypto`, `fetch`, `console.table`, `process.cwd`/`platform`/`stdout`/`nextTick` | ✓ | ✗ | ✗ |
| `args`, `stdin`, `readFile`, `writeFile`, `Java` | ✗ | ✓ | ✓ |

Two readings of that table matter more than the rows.

**The ES2026 row is a version accident, not a design.** GraalJS 25.0.2 rejects the option outright —
`--js.ecmascript-version=2026` fails with *"Supported values are 5 to 16 or 2015 to 2025"* — so on the
version the shipped builder runs, the ceiling is ES2025 and no configuration lifts it. The builder's
pom pins that version for an image-compatibility reason and says so at the pin: the root property
stays at 25.4.4.1.1 *"because poc-26 measured its ES2026 claims on that pair and poc/ is frozen
evidence, so the divergence is scoped to the module that ships as a binary."* POC 26's
[14/14](../poc/poc-26-graaljs-skill-scripts/README.md) is true of 25.4.4.1.1 and is not true of the
artifact that ships.

**Node is not a superset.** Node 26 lacks `Math.sumPrecise`, the headline ES2026 numeric feature,
while the newer GraalJS has it. There is no host whose feature list you can simply target.

**Therefore: write to ES2022–ES2025, which every arm has, and feature-detect anything above it.**
`--js.ecmascript-version` is the knob a host can turn to pin the level (GraalVM's own guidance for
production is to pin a finalized edition), but a *script* cannot rely on the host having turned it.

## Rules that follow

### 1. One file. No imports.

Module syntax is available in `.mjs`; module *resolution* is not, because resolving a sibling is a
filesystem read and the sandbox denies filesystem reads. Measured, same directory, same two files:

```js
import { MARKER } from "./b.js";        // node: works
                                         // engine: Error: Operation is not allowed for: b.js
import fs from "node:fs";               // node: works
                                         // engine: Error reading module 'node:fs' … Socket IO is not allowed
const x = await Promise.resolve(42);    // works on both, in .mjs
```

So a skill script is one self-contained file, and a second script under `scripts/` is a second tool,
not a library. Shared helpers get duplicated or the skill gets two tools. Top-level `await` is the one
module-only feature that is genuinely portable.

### 2. Read input from the three named doors, in this order

```js
function argv() {
  if (typeof args === 'object' && args !== null) return args;
  return Array.isArray(process.argv) ? process.argv.slice(2) : [];
}

function readInput(flags) {
  if (typeof stdin === 'string' && stdin.trim() !== '') return stdin;
  if (typeof flags.file === 'string' && typeof readFile === 'function') return readFile(flags.file);
  if (typeof require === 'function') {
    // Node has no `stdin` global; fd 0 is the pipe.
    try { return require('node:fs').readFileSync(0, 'utf8'); } catch (ignored) { return ''; }
  }
  return '';
}
```

Three doors covers every caller: the model's `stdin`, a staged or in-skill file through `readFile`,
and a shell pipe under Node. Note the shape — each door is tested for existence before use, so the
block is inert on the host that lacks it. That is the whole portability technique in six lines.

### 3. Exit code and stderr are the contract

`stdout` is the payload the model will quote; `stderr` is one line a human can act on; the exit code
is how a rule refusal reaches the agent loop. `process.exit(2)` is control flow, not a failure — the
engine unwinds a `ScriptExit` and reports a *success* carrying that code, exactly as a CLI does.

**One divergence has no workaround except discipline.** The engine reads a script's completion value
as its exit code: a script whose last statement evaluates to a non-zero integer exits with that number
in the JVM and with `0` under Node. Measured — the same two-line file exits `42` in the engine and `0`
in Node, and a `main()` that returns `7` exits `7` in the engine. So end the file with a call whose
return value is `undefined`, and say the exit code out loud with `process.exit` when you mean one.

```js
function fail(message, code) {
  console.error(message);
  process.exit(code);
}
```

### 4. Feature-detect the standard library, never assume it

The guard is one line and the fallback does the work. Each pair below was executed on all three arms
and produced the same answer everywhere.

```js
// Exact sums. reduce() drifts; sumPrecise does not.
function sumExact(values) {
  if (typeof Math.sumPrecise === 'function') return Math.sumPrecise(values);
  return values.reduce((cents, v) => cents + Math.round(v * 100), 0) / 100;
}

// Grouping.
function groupBy(rows, key) {
  if (typeof Object.groupBy === 'function') return Object.groupBy(rows, (r) => r[key]);
  const out = {};
  for (const row of rows) (out[row[key]] = out[row[key]] || []).push(row);
  return out;
}

// base64 without Buffer and without TextEncoder.
function toBase64(text) {
  if (typeof Uint8Array.prototype.toBase64 === 'function') return new Uint8Array(utf8(text)).toBase64();
  /* … 12 lines of the same arithmetic by hand … */
}

// A cross-realm error test — the only reliable one inside a catch.
function isError(value) {
  if (typeof Error.isError === 'function') return Error.isError(value);
  return Object.prototype.toString.call(value) === '[object Error]' || value instanceof Error;
}
```

The fallbacks are not ceremony. On the shipped 25.0.2 pin, `Math.sumPrecise`, `Iterator.concat`,
`Error.isError`, `Map.getOrInsertComputed`, `Uint8Array.toBase64` and `JSON.rawJSON` are all absent,
so the guard branch is the one that runs — and a script with no fallback does not fail loudly there,
it fails with a `TypeError: … is not a function` that reads like a bug in the script.

Richer features are fair game the same way. Iterator helpers exist on every arm, so a script can be
written around generators and lazy pipelines:

```js
function* lines(source) {
  let start = 0;
  for (let i = 0; i < source.length; i++) {
    if (source[i] === '\n') { yield source.slice(start, i); start = i + 1; }
  }
  if (start < source.length) yield source.slice(start);
}

const firstThree = Array.from(lines(text))[Symbol.iterator]().take(3).toArray();
```

Tagged templates, classes with private fields, `Map`/`Set` algebra, generators, `BigInt` for exact
ratios and `Intl` for locale-aware formatting are all inside the shared surface and all make a script
better than string concatenation would:

```js
const csv = (strings, ...values) =>
  strings.reduce((acc, chunk, i) => {
    if (i >= values.length) return acc + chunk;
    const raw = values[i] == null ? '' : String(values[i]);
    return acc + chunk + (/[",\n]/.test(raw) ? '"' + raw.replaceAll('"', '""') + '"' : raw);
  }, '');

const pct = (part, whole) =>
  (typeof 1n !== 'bigint') ? (part / whole) * 100
                           : Number((BigInt(part) * 10000n) / BigInt(whole)) / 100;
```

### 5. No waiting, no streaming, no children

`setTimeout` and `setInterval` do not exist in an embedded `Context` — that is GraalVM's documented
boundary between the `node` launcher and a Java embedding, not this engine's choice. Promises and
`await` work; anything that needs a timer queue does not. `fetch`, sockets, subprocesses and threads
are denied outright. So a script does one bounded piece of synchronous-ish work and returns; retry
with backoff, polling and concurrency are the agent's problem, not the script's.

`Date.now()` exists but is the one source of nondeterminism left on the surface, and the improvement
loop compares runs. Derive timestamps from the input, not from the clock, unless the script's answer
genuinely depends on now.

### 6. Files by name, and write only artifacts

`readFile` answers for a file staged for the run (matched by basename) or a file inside the skill's
own folder; anything else is refused with the list of what *was* staged, rather than resolved.
`writeFile` strips any path and writes into a fresh per-run output directory, whose contents come back
as `ScriptArtifact`s. A script cannot write into the skill it came from. Pass names, not paths, and
never build a path from model-supplied text.

### 7. Numbers: cents, strings, or nothing

Money is integer cents or `Math.sumPrecise` — see rule 4, where the fallback is the cents path.
Identifiers wider than a double (ledger accounts, 20-digit order ids) must travel as **strings**:
`JSON.rawJSON` keeps them raw on a newer GraalJS and on Node, and on the 25.0.2 pin the fallback
either loses digits or changes the JSON's type. Measured on that pin: the same `serializeLedger`
emitted `91827364550123456789` under Node and `"91827364550123456789"` under the engine. Quote it
everywhere and the question does not arise.

### 8. Stay inside the budgets

5 seconds and 25 million statements is generous for parse-and-compute work and tight for anything
iterative over a large corpus. Bound every loop, cap every excerpt, and prefer printing a capped
summary with a pointer over printing everything — output beyond 100 000 characters is truncated
mid-stream, and a truncated JSON is worse than a short one.

### 9. Fail in a way the model can use

Validate the input, and refuse with a message that names the argument to fix. The `SKILL.md` body is
supposed to state the rules the script enforces — `generate_skill.jinja` says so explicitly: *"state
the rules the script enforces so a non-zero exit is intelligible, and never tell the agent to work the
figures out itself as a fallback."* A script that dies with `TypeError: undefined is not an object`
gives the model nothing to act on and invites exactly the fallback that prompt forbids.

## The reference script

This file is the rules above assembled. **It produced byte-identical stdout and identical exit codes
under Node 26 and under the engine on both version arms**, across four scenarios.

```js
const VAT_RATE = { goods: 0, freight: 21, services: 21 };
const DISCOUNT_LIMIT = 15;
const CENTS = 100;

function argv() {
  if (typeof args === 'object' && args !== null) return args;
  return Array.isArray(process.argv) ? process.argv.slice(2) : [];
}

function parseArgs(tokens) {
  const flags = {}, positional = [];
  for (let i = 0; i < tokens.length; i++) {
    const token = String(tokens[i]);
    if (!token.startsWith('--')) { positional.push(token); continue; }
    const eq = token.indexOf('=');
    if (eq >= 0) { flags[token.slice(2, eq)] = token.slice(eq + 1); continue; }
    const next = tokens[i + 1];
    if (next === undefined || String(next).startsWith('--')) flags[token.slice(2)] = true;
    else { flags[token.slice(2)] = String(next); i++; }
  }
  return { flags, positional };
}

function readInput(flags) {
  if (typeof stdin === 'string' && stdin.trim() !== '') return stdin;
  if (typeof flags.file === 'string' && typeof readFile === 'function') return readFile(flags.file);
  if (typeof require === 'function') {
    try { return require('node:fs').readFileSync(0, 'utf8'); } catch (ignored) { return ''; }
  }
  return '';
}

function sumExact(values) {
  if (typeof Math.sumPrecise === 'function') return Math.sumPrecise(values);
  return values.reduce((cents, v) => cents + Math.round(v * CENTS), 0) / CENTS;
}
const roundCents = (v) => Math.round(v * CENTS) / CENTS;

function groupBy(rows, key) {
  if (typeof Object.groupBy === 'function') return Object.groupBy(rows, (r) => r[key]);
  const out = {};
  for (const row of rows) (out[row[key]] = out[row[key]] || []).push(row);
  return out;
}

function fail(message, code) { console.error(message); process.exit(code); }

// The rule-4 helper: Uint8Array.toBase64 where present, the same arithmetic by hand where not.
function toBase64(text) {
  const bytes = [];
  for (const ch of String(text)) {
    const cp = ch.codePointAt(0);
    if (cp < 0x80) bytes.push(cp);
    else if (cp < 0x800) bytes.push(0xc0 | (cp >> 6), 0x80 | (cp & 63));
    else if (cp < 0x10000) bytes.push(0xe0 | (cp >> 12), 0x80 | ((cp >> 6) & 63), 0x80 | (cp & 63));
    else bytes.push(0xf0 | (cp >> 18), 0x80 | ((cp >> 12) & 63), 0x80 | ((cp >> 6) & 63), 0x80 | (cp & 63));
  }
  if (typeof Uint8Array.prototype.toBase64 === 'function') return new Uint8Array(bytes).toBase64();
  const B = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
  let out = '';
  for (let i = 0; i < bytes.length; i += 3) {
    const n = (bytes[i] << 16) | ((bytes[i + 1] || 0) << 8) | (bytes[i + 2] || 0);
    const rest = bytes.length - i;
    out += B[(n >> 18) & 63] + B[(n >> 12) & 63] + (rest > 1 ? B[(n >> 6) & 63] : '=') + (rest > 2 ? B[n & 63] : '=');
  }
  return out;
}

function main() {
  const { flags } = parseArgs(argv());
  const raw = readInput(flags);
  if (raw.trim() === '') fail('no invoice on stdin and no --file that this run can read', 2);

  let invoice;
  try { invoice = JSON.parse(raw); }
  catch (error) { fail('stdin is not JSON: ' + (error.message || error), 2); }

  const lines = Array.isArray(invoice.lines) ? invoice.lines : [];
  if (lines.length === 0) fail('invoice has no lines', 2);
  const unknown = lines.filter((l) => !(l.category in VAT_RATE));
  if (unknown.length > 0) fail('unknown category: ' + unknown[0].category, 3);

  const discount = Number(invoice.discountPercent || 0);
  if (discount > DISCOUNT_LIMIT && !flags.approval) {
    fail('a ' + discount + '% discount needs an --approval=<who> argument', 4);
  }

  const byCategory = groupBy(lines, 'category');
  const netByCategory = {}, vatByCategory = {}, checksumParts = [];
  for (const category of Object.keys(byCategory).sort()) {
    const rows = byCategory[category];
    const priced = (discount > 0 && category !== 'freight')
      ? rows.map((r) => roundCents(Number(r.amount) * (1 - discount / 100)))
      : rows.map((r) => roundCents(Number(r.amount)));
    const net = sumExact(priced);
    netByCategory[category] = net;
    vatByCategory[category] = roundCents(net * VAT_RATE[category] / 100);
    checksumParts.push(category + '=' + net + '|n=' + rows.length);
  }

  const netTotal = sumExact(Object.values(netByCategory));
  const vatTotal = sumExact(Object.values(vatByCategory));
  console.log(JSON.stringify({
    number: invoice.number || null,
    lineCount: lines.length,
    discountApproved: (discount > DISCOUNT_LIMIT) ? String(flags.approval) : null,
    netByCategory, vatByCategory, netTotal, vatTotal,
    payable: roundCents(netTotal + vatTotal),
    checksum: toBase64(checksumParts.join('|')),
  }, null, 2));
}

main();
```

| Scenario | stdout | exit | Both hosts |
| --- | --- | :---: | :---: |
| two lines, no discount | `netTotal 1259.88`, `vatTotal 50.4`, `payable 1310.28` | 0 | identical |
| 20 % discount with `--approval=alice` | `discountApproved "alice"`, `payable 388.1` | 0 | identical |
| 20 % discount, no approval | — | 4 | identical (`stderr`: *a 20% discount needs an --approval=<who> argument*) |
| category not in the table | — | 3 | identical (`stderr`: *unknown category: licorice*) |

The rounding is the point of the exercise: `[0.1, 0.2, 0.3]` sums to `0.6000000000000001` by
`reduce` on **every** arm, and to `0.6` through `sumExact` on every arm. That is the difference
between a script an auditor can sign off and a model asked to do floating point in its head.

## Checking a script on both hosts

The engine arm needs no GraalVM, only the two artifacts on a classpath — which is the entire claim of
the design. The commands that produced every number on this page:

```bash
# Node arm
echo "$INPUT" | node scripts/invoice_totals.js --approval=alice

# Engine arm: the same file, the same Context lockdown, a plain JDK
mvn -q -pl prod/embabel-smith-builder dependency:build-classpath \
    -Dmdep.outputFile=/tmp/cp.txt -Dmdep.includeScope=runtime
java --class-path "$(cat /tmp/cp.txt)" Harness.java "$PWD/scripts/invoice_totals.js"
```

A harness is fifteen lines of Java if it only mirrors the engine's `Context` builder and `PROLOGUE`;
copy both from `GraalJsSkillScriptExecutionEngine` rather than reinventing them, or the arm measures
the harness. Diff stdout and the exit code, not pass/fail heuristics — the divergences that bite
(completion values, big integers, `Math.sumPrecise`) all show up as different bytes.

## When neutrality is the wrong goal

The shared surface is a genuine constraint, and pretending otherwise costs more than naming it. A
skill that needs npm packages, a real filesystem, a socket, a subprocess or a timer should not be
written as a `.js` skill script at all: use the `ProcessSkillScriptExecutionEngine`, Docker or Podman
engine Embabel already ships, which spawn a real interpreter and get OS-level containment instead.
The in-JVM engine is hardened, not a security boundary against a determined guest — `POC 26`'s own
limits section says so, and untrusted third-party scripts belong in a container.

The published `skill-card-generator` skill shows the other end of the same judgement: three scripts,
plain ECMAScript, no Node API anywhere in 1 900 lines, and a `discover_assets.js` that hand-rolls
`print`, `joinAbs`, `basename` and a Python-shaped `splitLines` because a shim is cheaper than a
portability bug.

## Where this is wired

| Thing | Where |
| --- | --- |
| the engine | `builder/skill/script/GraalJsSkillScriptExecutionEngine` |
| enablement | `builder/skill/script/SkillScriptEngineConfiguration`, `smith.builder.skill-scripts.enabled`, off by default |
| installed via | Embabel's `Skills.withScriptExecutionEngine(engine)` |
| call sites | `cli/BuildCommand` → `skill/SkillScriptHost` (build-internal, with-skill eval arm); `cli/SkillRunCommand --skill-dir` → `skill/PublishedSkillHost` |
| what the generator is told | `prompts/generate_skill.jinja` — the surface, *"Write plain ECMAScript against that surface: it runs in the reader's own JVM, not in Node and not in a browser"*, and the `{"path": "scripts/<name>.js"}` output contract |
| GraalJS version | `graalvm.polyglot.version` = 25.0.2 in the builder pom; root `graalvm.version` = 25.4.4.1.1 |
| native image | no GraalJS entry in the builder's `native-image/` config; the engine reaches a native image through the traced hints, and a script's own code is never AOT-compiled |
| turning it on | a JVM system property, not a flag: `SMITH_JAVA_OPTS=-Dsmith.builder.skill-scripts.enabled=true` (`docs/08-builder.md`). `--help` cannot show it, which is how a switch-on measurement goes unnoticed |
| published tier | `security/AgentSpecSecurityScanner` reports `T3_PARTNER` for a skill that ships JavaScript and `T1_COMMUNITY` for the same skill scriptless |
| pinned by | `GraalJsSkillScriptExecutionEngineTest` (`givesTheScriptACliSurfaceRatherThanANodeOne` asserts `require`/`module`/`__dirname` are `undefined`, plus one denial per capability), `SkillScriptEngineBeanTest`, `SmithBuilderScriptEngineWiringTest`, `SkillScriptToolSurfaceTest`, `PublishedSkillScriptToolSurfaceTest`, `EvalRunnerScriptArmTest`, `SkillCardGeneratorScriptsTest` |
| the evidence | `poc/poc-26-graaljs-skill-scripts`, `docs/04-poc-run-results.md` §7.4 |
| open gaps | `docs/12-backlog.md` §Builder — skill scripts |

The tier row needs one qualifier so it is not read as a gate: it is a **report**. A trust tier is a set
of tool-group roles a pack declares, and a script declares none, so no loader consults a tier about a
`.js` file — the tier only records honestly that the artifact asks its reader to run code a model
wrote. Nothing in the tier machinery stops, or permits, the engine.

Two ways to make the tier into a gate were tried and refused. Asking the tier to refuse an executable
skill at load sounds obvious and is empty: a tier is a role set fed by what a pack declares, a script
contributes no role to either side, so the check would be a predicate that never sees its subject. And
registering a tool group so the tier has something to read *does* make the check real, which is still
the wrong move — it retracts a fact the previous change pinned on purpose (installing the engine adds
no tool group, so the surface cannot silently widen) and would add an eighth name to the exact set of
exposed roles that change exists to defend. The gate is bought at a worse price than it is worth when
the scanner already refuses the dangerous scripts and the sandbox already denies the host.

## Provenance of every number here

Measured on this machine, 2026-11-24, on branch `poc/iteration-3`: Zulu OpenJDK 25 (a stock JDK, no
GraalVM), Node v26.13.1, GraalJS `25.0.2` and `25.4.4.1.1` resolved from the builder's own runtime
classpath.

- **64 probes** over the language and host surface, one generated plain-script file executed
  unmodified under each arm; a probe passes by completing, not by feature-sniffing alone.
- The engine arm reproduces `GraalJsSkillScriptExecutionEngine`'s `Context` builder, `PROLOGUE`,
  module mode for `.mjs` and completion-value-as-exit-code, so it measures the engine's contract
  rather than a approximation of it.
- The reference script was run through both arms across four scenarios, diffing stdout and exit code.
- Statements about GraalJS itself — the ES2026 default and its per-version history,
  `--js.ecmascript-version` values, the embedded-`Context` versus `node`-launcher boundary, the
  embedding Maven coordinates and the `js.commonjs-require` option — are from
  [the GraalVM JavaScript documentation](https://www.graalvm.org/javascript/docs/).
- Statements about this repository are checkable against the tree at the paths above.

Three limits travel with the numbers. They are probes, not the ECMA-262 conformance suite — a feature
outside the list is unverified, not known-good. They are pinned to these versions, and the ES2026 row
in particular moves with the artifact. And the engine arm ran through a faithful reimplementation of
the engine's configuration, not through `SkillRunCommand`; if the two ever drift, the tree wins.
