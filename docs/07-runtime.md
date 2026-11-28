# The No-Code Runtime

`prod/embabel-smith-runtime` ingests agent specs from a source and runs an exported goal over them
with **zero compilation**. The format it ingests is defined in
[`agent-spec-format.md`](agent-spec-format.md); where specs come from in
[`06-spec-sources.md`](06-spec-sources.md).

## How a run happens

`AgentSpecRunService` is the entry point; `core/RuntimeCore` does the work. A run resolves a source,
ingests every pack into one platform, selects the goal, and executes a GOAP plan.

- **Multi-pack ingest.** All packs from a source land in one platform, so the planner can chain an
  action from one pack into another through a shared intermediate type.
- **Duplicate exported goals fail at ingest, not at plan time.** Two packs exporting the same goal
  name is a source-library bug; the error names *both* packs, because naming only one would not make
  it findable.
- **A missing goal is not a stack trace.** `GoalNotFoundException` lists the goals that *are*
  available.
- **The planner is selectable per run** (`core/PlannerResolver`), defaulting to GOAP.
- **Plan structure is deterministic.** The planner is not a model, so the same pack and goal produce
  the same step sequence every time. Only the action bodies vary, because those are model calls.

## References and skills

A step's `references:` entry names a skill the pack carries. `core/RuntimeCore` hands the scope
builder a `core/ReferenceAwareStepSpecRepository` over the step repository, and that wrapper does two
things nothing else in the deploy path can: the scope builder takes tool groups and tools but no
references, so the context a step is emitted against would otherwise carry none, and upstream resolves
a declared name against that list with a plain map lookup whose miss is silent.

- **One container per definition.** `core/PackSkills` builds a `Skills` reference named
  `<agent>-skills` from the definition's own `skills/`, and `shared-skills` from the `skills/` of its
  **immediate parent** — the directory the definitions in a folder share. Ancestors higher up are
  never consulted, because the parent is also the source root, so a stray `skills/` above it would
  silently be global to every pack in the source.
- **Own shadows shared.** A name the agent's own folder carries is served by that copy, and the run
  names the folder that answered.
- **The names are the wire.** Embabel prefixes every callable name with the sanitized reference name,
  so a skill's activation tool reaches the model as `ask_skills_notes_activate_skill`, not as
  `notes_activate_skill`. Skill text points at paths inside the folder rather than at tool names for
  that reason. The names are pinned by test.
- **Resolution is per definition.** A name carried only by a sibling definition, or by an unrelated
  folder, does not resolve; it is reported as dangling rather than resolving through a neighbour.
  Ahead of that, the pack validator refuses a step that declares a skill no folder its pack can reach
  carries — naming the step, the declared name, and the names actually carried — so the pack is
  dropped at the source and never reaches a run. The wrapper's dangling line is the backstop for a
  path that bypasses validation, not the ordinary route.
- **A reference never changes the plan.** It alters what a selected step's model call can reach, not
  which steps the planner selects.

What an ingest decided goes to **stderr**, ahead of the result, so the goal's own output stays the
only thing on stdout: which step was attached to which container, every skill folder the loader
refused (by path), every declared name that resolved to nothing (naming the step and the name), and
the script state in force. A silent absence and a policy block look identical otherwise.

Scripts are **off by default**: a pack whose skill ships a `scripts/` folder offers no script tool,
and the run says so and names the switch — `smith.tools.scripts.engine` — rather than leaving the
absence to be mistaken for a tier refusal. Installing `embabel-agent-skills` changes none of this:
the jar ships no auto-configuration, so a `Skills` reference exists only where this module constructs
one.

## Trust tiers

`core/TrustTier` and `core/TrustTierPolicy` gate which **packs** may run. The check is per pack, over
every pack in the source, and runs **once before execution** — so a single disallowed pack aborts the
whole run with `TrustTierBlockedException`, even if nothing in it was heading for that pack. A
high-trust pack needs an explicit tier (`--trust-tier`). This is the product
form of the per-tier tool-group allow-list proven in POC 10: a community-sourced pack must not reach
privileged tool groups.

## Lifecycle state

`core/LifecycleSidecar` reads the `state` key out of the pack's own `lifecycle.yml` sidecar — the file
[`08-builder.md`](08-builder.md)'s stamper writes — and `RuntimeCore.ingest` **excludes a pack whose
state is `retired`**. It is not deployed at all, so neither its goal nor its actions are plannable,
which is what "out of the library" has to mean once the planner can chain through a pack. Retirement is
also kept out of the duplicate-goal check: a retired twin of a live pack is an exclusion, not a
collision.

- **Only `retired` blocks.** `INCUBATING` and `DEGRADED` stay runnable — `DEGRADED`'s own definition is
  "the spec still functions", and gating the others would be a policy nobody has written down.
- **No sidecar means no recorded state, and no recorded state runs.** The same fallback the loop's
  reader applies, so packs published before stamping existed and the frozen POC sources keep working.
  A sidecar that cannot be parsed is read the same way; refusing to run on unreadable data would turn
  a corrupt file into an outage.
- **The exclusion is reported by name.** `IngestedSource.excluded` carries it, and the empty-source
  error appends what it left out — a library that quietly lost a pack produces a plan nobody can
  explain.
- **Asking for a retired pack's goal is a named block, not a missing goal.** `LifecycleBlockedException`
  — message beginning `Run blocked by lifecycle state`, naming the pack — is thrown in place of
  `GoalNotFoundException`, because the loop filters failures by message prefix and must not read a
  retirement as a spec defect. A live pack that exports the same goal still wins.
- **A blocked run emits a trace record** like any other failure: no steps, no pack, the reason in
  `error`.
- **There is no override.** The documented revival is an explicit rebuild, which republishes the pack
  and leaves `retired`; a bypass flag would be a second, weaker revival path.

The lifecycle vocabulary is deliberately duplicated rather than shared. The builder owns it —
`lifecycle/LifecycleState` is the four-valued enum and `lifecycle/LifecycleStamper` writes it — while
the runtime reads `state` **as a string** and compares it only against its own local `RETIRED`
constant, so the reader side never depends on the writer side. The loop's `Detector` on the builder
side likewise matches `LifecycleBlockedException`'s message prefix instead of importing the class. The
settled plan is to move these types down into `smith-common` *when a second reader appears*, not to
add a builder-to-runtime edge in the meantime — which leaves the contract between the two modules as
one state string and one message prefix, so renaming either is a change in both places.

## Remote action bridges

`remote/` is the one part of the runtime that talks to another process. It is a *client* of a
remote-action sidecar — the contract POC 16 proved — and the studio's `/remote` pane is its only
consumer. It lives here rather than in the studio for a reason a test enforces: the studio's
`NoDirectModelRequestTest` fails the build if any studio production source imports
`java.net.http.`, so the HTTP has to live somewhere else, and this is the module that already owns
"run an action".

The client is `RemoteActionBridge`, with `RemoteBridge` (a name and a base URL), `AdvertisedAction`,
`BridgeState` and `ExecuteResult` as its shapes and `RemoteBridges.parse` for the `name@url`
configuration grammar. The contract has three endpoints and this client calls two of them:

| Endpoint | Used by the product client? |
| --- | --- |
| `GET /api/v1/actions` | yes — the action catalog, and the reachability decision below |
| `POST /api/v1/actions/execute` | yes — run one action; body `{action_name, parameters: {userInput: {content}}}`, and the reply's `content` field is the output |
| `GET /api/v1/types` | **no** |

The type catalog is the endpoint the product does not call, and that is a choice rather than an
oversight: the pane has one text box that maps to `UserInput`, so it can run an action whose declared
inputs are exactly that one type and nothing else, and lists anything else with its declared input
types marked not runnable. Fetching a type catalog would let the client *describe* shapes it still
cannot construct, so it is omitted rather than half-wired.

- **Reachability is decided from the response, not from the absence of an exception.** A 2xx whose
  body parses as a JSON array is reachable; a non-2xx, a body that is not an array, and a socket that
  was never accepted are each unreachable, with their own reason kept. A service that answers
  `200 OK` with something that is not a catalog is not a sidecar, and calling it reachable because
  the call threw nothing would be a badge that lies.
- **Timeouts are fixed at 3 seconds to connect and 10 seconds for the request.** The bridge list is
  rendered when a pane opens, so the cost of a configurable timeout is a knob nobody asked for and a
  UI that can hang on a host that accepts the socket and never answers.
- **The reason is the exception's own name when it carries no message.** On this JVM
  `java.net.ConnectException` arrives with a null message all the way down the cause chain, so
  `ex.getMessage()` — what the POC displayed — renders as a bare `toString()`. Keeping
  `ConnectException` is what lets a reader tell "refused the socket" apart from "answered with
  something that was not a catalog".
- **A decline is passed through, never rewritten.** When the sidecar answers non-success, its status
  and its untouched body travel back together; inventing a result for an action that did not run is
  the failure mode this avoids.
- **The runtime never starts a sidecar.** A process that speaks the contract is started by hand; the
  reasoning is in [`09-studio.md`](09-studio.md) and the decision record in
  [`12-backlog.md`](12-backlog.md).

**Planning over a remote action is not a product capability.** The bridge can report that a sidecar
offers `remoteDigest`, and it can run that action because a person named it. It cannot put a remote
action into a GOAP plan. Three things stand between the two, and none of them is in this module
today: the pack format has no step kind that can name a remote action, so there is nothing for a
planner to select — POC 16 chained the two by hand in Java, which is not a format;
`embabel-agent-remote`, which supplies `RestAction`, is not a dependency of any `prod/` module and
must not become one before that format decision is made; and remote actions would have to be
registered into the platform the planner searches (`RestServer(…).agentScope(platform)`), which the
current ingest path never does. POC 16 is the evidence that the wiring works; the format decision is
what would make it a product feature rather than a demo.

## Traces

**Every run emits a trace record — including failed runs.** This is a product requirement, not an
observability extra: the self-improvement loop has no other evidence source, so a run that leaves no
trace is a run the loop cannot learn from.

`trace/TraceRecorder` writes `trace/TraceRecord` (per-step durations, success, the trust tier, an
excerpt of the goal output). The run's `input` is stored **verbatim** — only outputs are excerpted, at
`DEFAULT_EXCERPT_LIMIT`. Design decisions that matter to a reader of the code:

- **The writer stays dumb.** It appends with `SYNC` and does no retention. The rolling window is a
  *read*-side concern applied by the loop. A writer that trims would eventually disagree with
  whoever owns retention.
- **Excerpts are truncated to 500 characters** (`DEFAULT_EXCERPT_LIMIT`). Traces serve the loop and
  the UI, not archival fidelity.
- **The trust tier is recorded in the trace** so the loop can distinguish "failed because the tier
  blocked it" from "failed because the spec is wrong". A refinement must never try to fix a policy
  outcome.
- **A run that never executed still has a record**, with `steps` empty and the reason in `error`: a
  trust-tier block, a lifecycle block, a goal no pack exports, or no plan reachable. The loop matches
  the first two by message prefix and drops them from the failures it refines from.
- **v1 is single-process per host**, with no locking beyond append-and-sync. Concurrent writers are
  a known limitation, not an oversight.

### The trace store is an SPI

`trace/TraceStore` is the seam; `trace/JsonlTraceStore` is the default implementation at
`~/.smith/traces/runs.jsonl` (directory created on first use, `--trace-dir` overrides).
`prod/embabel-smith-studio` contributes `storage/JpaTraceStore` implementing the same interface.

The SPI deliberately lives in the **runtime** module and is record-shaped — `TraceRecord` in, nothing
storage-specific out — so a future backend drops in without reworking the Postgres one, and the
CLI's `--trace-dir` contract never changed when the database option arrived. Readers skip corrupt
lines and report them rather than failing the read.

## CLI

Picocli, chosen because it is small, has no Spring dependency, is offline-testable, and gives
`--help` for free. The POCs used environment variables and a bare `main()` only because they were
one-shot demos.

```
smith run --source <folder:…|git:…[@ref]> [--goal <name>] [--input <text>]
          [--planner <GOAP|…>] [--trust-tier <tier>] [--trace-dir <dir>]
```

`cli/SmithCommand` is the root (`smith`); `cli/RunCommand` is `run`. **Every CLI wraps a plain
service class** (`AgentSpecRunService`) rather than holding logic itself, so the studio can embed the
same capability without going through the CLI.

Standalone, the module builds an executable jar beside its library jar —
`java -jar target/embabel-smith-runtime-<version>-exec.jar run --source …`. That jar is not what the
container image runs: the image runs this module's GraalVM native image, `target/smith-runtime`,
which the same `package` produces when `-Pnative` is on. Keeping the jar is what makes a native
failure diagnosable — when the binary and the jar disagree about the same argv, the jar is the
control that says which of the two is wrong. The classifier is what lets both exist: this module is
a compile dependency of the studio and the a2a starter, and a repackaged jar's classes sit under
`BOOT-INF/classes`, where a normal classpath cannot see them (see
[14 — Containers](14-containers.md)).

### Run paths for the shipped example

`agent-specs/coding-agent` is run three ways rather than one, because a run can fail in three ways
and a reader who only ever sees the succeeding one cannot tell them apart. Each is the whole command
against the executable jar above, and each outcome was measured by hand on 2026-11-28.

```
# 1 — the reader, nothing configured at all. This is the default tool set doing its job.
java -jar target/embabel-smith-runtime-<version>-exec.jar run \
    --source folder:$SRC --goal codebaseMapped --input "Report which files exist…"

# 2 — the changer, with the write switch named on the line, in a working directory it may edit.
java -Dsmith.tools.coding.writes=true -jar target/…-exec.jar run \
    --source folder:$SRC --goal codeChangeApplied --input "Append a line to notes/todo.txt"

# 3 — the verifier, where the shell group is both switched on and tier-granted…
java -Dsmith.tools.shell.enabled=true -jar target/…-exec.jar run \
    --source folder:$SRC --goal buildVerified --trust-tier high --input "…"
# …and the same command at the default tier, which refuses before executing anything.
java -jar target/…-exec.jar run --source folder:$SRC --goal buildVerified --input "…"
```

Path 1 achieved `codebaseMapped` in PT3M11 with `readFile` called 42 times and no property set
anywhere; the boot line for that run reads `ours by default [file] … decided by {file=no switch set
(documented default)}`, which is the sentence that distinguishes it from paths 2 and 3. Path 2
achieved `codeChangeApplied` in PT35.4S and the proof is the file, not the log: `notes/todo.txt`
gained the requested line. Path 3 at the high tier achieved `buildVerified` in PT16.2S with
`runCommand` called three times and the report reading `ours switched on [shell]`; the same command
at the default tier printed `Run blocked by trust tier: pack 'coding-agent-verify' declares tool
group(s) [shell] that require a higher trust tier (granted: standard)` and ran nothing.

The tier refusal is **scope-wide, not plan-wide**: `RuntimeCore.checkTier` is handed every ingested
pack, so a source that carries a shell-declaring definition refuses the run even when the requested
goal is reachable without it. That is why path 1 is shown against a source holding the reader alone:
pointed at the whole `coding-agent` folder, `--goal codebaseMapped` refuses at the default tier for a
step it never needed. Whether the gate should instead consider only the planned steps is an open
question, recorded against the example's task list rather than decided here.

Three facts about `--source` that the commands above depend on and that no error message states.
`folder:` resolves the **children** of the directory it is given, so pointing it at a pack rather
than at a folder of packs yields `spec source '…' contains no agent specs` — `arithmetic` is
addressable through its parent, `coding-agent`'s definitions through `coding-agent`. Shared skills
are read from the source root's own `skills/` directory, so lifting one definition out of
`coding-agent` without that directory leaves its `references: [repo-map]` unresolvable, and the
result is the same empty-source line with nothing naming the pack or the name — the run report that
names unresolved references applies to a deployed pack's steps and never gets the chance to fire for
a pack that never arrived. And `--trust-tier` gates on tool-group role names only, so it cannot
restrict a capability that registers no role.

`agent-specs/arithmetic` shows the tier from the other side, since its single step declares `math`,
which registers from upstream rather than from this project's switches. At the default tier it
refuses before execution naming both: `pack 'arithmetic' declares tool group(s) [math] that require
a higher trust tier (granted: standard)`. At `--trust-tier high` with no tool property set it
achieved `arithmeticAnswered` in PT2.8S. Both halves ran against a staging directory holding
`arithmetic` alone, so the goal's discoverability is a fact about the pack and its own folder rather
than about the corpus beside it: `folder:` resolves children, and the only child there is the pack. No
switch is involved in either half, and that is the one default this project inherits rather than
chooses. On the offered names: `RunCommand` prints no
`offered=` line — that surface exists in the runtime module's live test, not in the CLI — so what a
CLI run shows instead is the calls themselves, `calling tool math` and then `multiply`, alongside
the boot's `2 tool groups: file, math`. A first attempt at that leg reached the same two tools and
then aborted on an empty-content model return, which is the endpoint's flake family rather than a
wiring fault, and the clean run above is the one being cited.

The third way a declaration fails is an operator switching a group off, and it is deliberately not
shaped like a tier — or like a failure. With `-Dsmith.tools.file.enabled=false` at the high tier against
the same example, the run printed **no** tier refusal at all and still achieved its goal,
`goal codebaseMapped achieved in PT2M16.1S`, answering through the skill rather than the disabled group:
34 activation calls, 9 `listResources`, 9 `readResource`, and no `listFiles` anywhere in the run. The
degradation shows in two places, neither of them the exit code. The boot line reads
`ours by default [none], ours switched on [none], ours disabled by configuration [file], … decided by
{file=switch 'smith.tools.file.enabled'}`, and the answer's own payload says
`paths=Unable to determine — no file-listing or shell execution tool is available in this environment,
so the working directory could not be listed or inspected.` So the three are still distinguishable from
the report alone — a tier refusal names the pack and the granted tier, a switch-off names the property
that decided it, a missing capability names neither — but a switch-off has to be read off the boot line,
because the run reports itself as having succeeded.

### The generic pack and the concrete instance

`agent-specs/` ships two packs that exercise the same channel from opposite ends, and they are worth
comparing explicitly because it is easy to read one as the other.

`coding-agent` is the **concrete instance**: three definitions, each naming a subject matter — locate
files, patch files, check a build — with two steps sharing one folder skill and one carrying a
definition-local skill beside it. Its claims are about resolution: which container answered, that an
agent's own copy shadows a shared one, that a reference never changes the plan.

`skill-interpreter` is the **generic pack**: seven definitions — `orient`, `select`, `plan`, `act`,
`script`, `verify`, `escalate` — where every hand-off is a named type (`RunBrief`, `SkillDirective`,
`ActionPlan`, `WorkLog`, `ScriptEvidence`, `Verification`, `OpenQuestion`, `TaskReport`) and no step
prompt names a subject matter at all. The named types are what make the chain typed rather than
merely ordered, since the format has no ordering to test. A grep for domain nouns across those
prompts is empty once word boundaries are used; a boundary-free grep chases ghosts, because `folder`
contains `order` and `reported` contains `repo`.

**The attribution rule between them.** A failure in one pack is not a failure of the channel. If the
generic pack produces a poor report for the subject it was handed, that is the pack's prompt quality
and it says nothing about whether skill-following works. The converse holds and matters more: the
concrete instance passing its paths is no evidence that the channel generalises, because that pack was
written around the skills it carries. Generality is demonstrated only by running the generic pack
against a skill nobody wrote it for, which is why that leg exists — and why its results belong to the
pack rather than to the runtime.

### The generic pack, run live

The live half, kept separate from the claims above as the coding example keeps its own. It needs a skill
the pack was never written for, so the leg stages one: the pack's seven definitions plus a throwaway
skill — instructions, one reference, one script — in a single source folder, with `select`'s
`references:` pointed at the new name.

```
rm -rf /tmp/interp-src && mkdir -p /tmp/interp-src/skills
cp -r agent-specs/skill-interpreter/* /tmp/interp-src/
cp -r agent-specs/throwaway/skills/release-notes /tmp/interp-src/skills/
sed -i 's/workspace-inventory/release-notes/' /tmp/interp-src/select/steps/select.yml

timeout 420 java -jar target/embabel-smith-runtime-<version>-exec.jar run \
    --source folder:/tmp/interp-src --goal taskReported --trust-tier high \
    --input "Summarise the changes in this working directory for a release note."
```

Skill used: `release-notes`, a name that appears in none of the pack's prompts. Strategy: the pack's own
default, GOAP. Tier: high. Measured 2026-11-28, 06:05 UTC.

It found the skill. The leg is 104 skill-tool calls — 44 `shared_skills_listResources`,
36 `shared_skills_activate`, 24 `shared_skills_readResource` — and the reads include the throwaway
skill's own `references/severity-rules.md`, so both kinds of content it carries reached the model. Where
it stopped is the part worth knowing: `executing orient` once, `executing select` once, nothing after.
All 104 calls sit inside `select`'s tool loop, and the leg ended when the 420 s cap killed it with
`LLM action:select: attempt 6 timed out after 60,000ms` on retry 6 of 10. It never terminated, so it
reported nothing about the skill being unusable. Read against the attribution rule above, that splits
cleanly: the channel generalised — a pack with no subject matter in its prompts reached a skill nobody
wrote it for and used its instructions and its resource — and this pack's `select` prompt does not
converge on this endpoint, which is prompt quality, not runtime behaviour.

The same command at the default tier refuses in 4 s before any step runs, and refuses on a step the plan
never reached: `Run blocked by trust tier: pack 'interpreter-act' declares tool group(s) [shell] that
require a higher trust tier (granted: standard)`. That is the scope-wide gate seen in the coding example,
now on the generic pack: `act` is why nothing ran, and `act` was never going to run.

**The four strategies.** Same staged source, same goal, same tier, one input
("List the files in this working directory and say what each is for."), `--planner` varied, each leg
capped at 420 s. Measured 2026-11-28 06:12–06:26 UTC.

| `--planner` | steps selected | terminated on the skill's criteria | model calls | wall |
| --- | --- | --- | --- | --- |
| `GOAP` (the pack's default) | `orient`, then `select` | no — killed with `select` on retry 6/10 of its 60 s timeouts | 101 (44 list, 32 activate, 24 read, 1 `listFiles`) | 419 s |
| `UTILITY` | none | no — `STUCK` | 0 | 3 s |
| `HYBRID` | none | no — `STUCK` | 0 | 4 s |
| `SUPERVISOR` | `orient`, then `select` — the platform routes it to the GOAP algorithm, and it behaves like GOAP | no — killed at the cap | 77 (42 list, 18 activate, 16 read, 1 `listFiles`) | 419 s |

The two utility-family legs are informative in a way the timeouts are not. Both print
`1/8 known actions available in current state` and then
`Business goal taskReported not achievable in 1 step` — from `UtilityPlanner`, and from
`HybridUtilityPlanner` for the hybrid — followed by
`Goal 'taskReported' not achieved (status=STUCK): unmet precondition(s) [taskReport:TaskReport]`. A
planner with a one-step horizon cannot see a goal seven typed hand-offs away, and it says so while naming
the type it could not produce, at zero model cost. That is a diagnosis a reader can act on; the GOAP
timeout is churn, and the difference between the two is only visible in the log, because a killed run
prints no report at all. The line saying a pack `carries scripts, not executed` appears seven times —
once per definition — in every leg that terminated, including the tier refusal, and zero times in the
three legs the cap killed.

Nothing in that output says which planner ran. Each leg contains exactly one `[smith-tools]` line, the
addon's boot line, and the only planner text anywhere in the four logs is the platform's own logger
names — so a `--planner` flag is indistinguishable from the pack's `defaultPlanner` in what the run
reports. A `--planner HYBRID` leg against the same source with the pack's manifest naming a
`defaultPlanner` behaved exactly as the STUCK legs above (under 4 s, 0 calls, same named precondition)
and said nothing about the override having applied.

## A2A endpoint

The A2A surface — the starter library `prod/embabel-smith-a2a-starter`, hosted by
`prod/embabel-smith-studio` when a run sets `smith.a2a.enabled` — is a **local development surface on
an explicit non-default port (18090) with no authentication and no TLS**, so **anyone who can reach the
port can start a real run and pay for its model calls**; it speaks **A2A protocol 0.3**, so a client
expecting the current 1.0 specification will not find it. Both halves are rulings rather than gaps: the
posture is the studio's, widened to a port anyone on the network can open, and 0.3 is what
`embabel-agent-starter-a2a:1.5.4` implements. The module's own README
(`prod/embabel-smith-a2a-starter/README.md`) holds the deviation table; what a reader of the runtime
needs is here.

The surface is `GET /a2a/.well-known/agent.json` plus `POST /a2a`, started with `make run-a2a`. The
library is the only `prod/` module that declares the starter, and there is no launcher: `make run-a2a`
starts the studio with `smith.a2a.enabled=true` and `SERVER_PORT` (18090 by default, because the
studio's own `application.yml` defaults to 8080). Setting the flag is also what contributes servlet
mode and that port default, from the starter's `SmithA2aEnvironmentPostProcessor` as the
lowest-precedence property source in the environment — so the host's own config, `SERVER_PORT` and
`SPRING_APPLICATION_JSON` all outrank it, and a host whose own file says `none` keeps `none`. A
host that merely depends on the library registers no endpoint and opens no port: the
auto-configuration is conditional on that property, and the library's own
`AgentA2AAutoConfiguration` is excluded from the environment in any process that carries this
starter, whether or not the surface is enabled. `SmithRuntimeApplication` is untouched — `smith run`
still boots non-web and opens no socket — and it could not host the surface in any case, because the
starter depends on the runtime, so the reverse edge is a Maven cycle. The studio's
`NoA2aEndpointTest` and `A2aEndpointWhenEnabledTest` are the guards that notice a regression, one in
each direction.

**Five operations, never six:** `message/send`, `message/stream`, `tasks/get`, `tasks/cancel`, plus the
card document. **Listing tasks is not among them**, and a client that asks gets HTTP 500 with
`Method tasks/list is not supported` — protocol 0.3 has no such method, so the library's facade
rejects it before any handler runs. Serving it would mean a route outside the dispatch of the library
this surface reuses, which is the second protocol surface the reuse ruling exists to avoid; so it is a
decision, recorded in [`12-backlog.md`](12-backlog.md), and not work owed. A client does not need it:
`message/send` returns the task and `tasks/get` reads it back by id, and a caller that lost an id can
read the trace file directly, because that is what both operations are built on.

**One run is one task, and the task id is the `runId` of the `TraceRecord` that run appends** — the
same record a CLI run writes, so an A2A run is evidence for the loop without a second registry of any
kind. A success is a `COMPLETED` task carrying the goal output as an artifact; a failure is a `FAILED`
task carrying the run's error in its status message, because a missing goal, a trust-tier block and an
unresolvable source are runs the service already reports, not HTTP errors.

**The tier is configured once for the endpoint** (`smith.a2a.trust-tier`, default `STANDARD`) because
the protocol request carries no caller identity to select one from; `smith.a2a.source` configures the
spec source the same way, since the request carries no source either. The goal and the planner *are*
per-request, in the message-send `metadata` under `goal` and `planner`. The card's skill list is
filtered by the same per-pack tool-group check the run gate makes, so the two cannot disagree about
which packs are the problem.

**The card's skills come from that configured source**, one per exported goal, read once on the first
card request and never at boot. Its `tags` and `examples` are empty because a `goal.yml` declares
neither — those two lists belong to `@Agent` on a Java class, and nothing is invented to fill them. A
source that cannot be read fails the card request rather than serving an empty skill list, because an
empty list tells a client the agent can do nothing. **A readable source that simply contains no packs
still yields `skills: []`**, and it should: a `folder:` source reads the *immediate subdirectories* of
the path it names, so pointing it at one pack (`folder:…/agent-specs/explainer`) rather than at a
directory of packs is a correct read of nothing, not a failure.

**Nothing here stops a run.** The trace record is written after the run returns, so every task the
store holds is already terminal: `tasks/get` never reports a `WORKING` task, and `tasks/cancel` refuses
with a status message saying the run is synchronous and was not cancelled. `message/stream` emits the
task, then its artifact, then a terminal status update, and closes — no invented progress events.

The module holds **no model call** of its own: every request goes through `AgentSpecRunService`, so an
A2A run is the same run `smith run` makes, and the `Ai`-layer rule in
[`05-architecture.md`](05-architecture.md) is untouched.

## The model endpoint

The endpoint is pluggable and env-driven (`OPENAI_CUSTOM_*`). **The runtime applies no endpoint
gate**: `application.yml` feeds every provider an inert but well-formed default
(`…:sk-smith-no-endpoint`, `…:http://127.0.0.1:9/v1`) so the app boots with nothing set, and an
unconfigured endpoint surfaces as a *failed run* — a `RunOutcome.failed` — not a named
configuration error. The `MODEL_NOT_CONFIGURED` gate is a studio-layer concern
(`smith-common`'s `ModelEndpointConfig`, consulted by its controllers); see
[`05-architecture.md`](05-architecture.md) for why no model call in `prod/` may bypass Embabel's
`Ai` layer.

Offline tests cover the core with the endpoint pointed at an inert local target — an in-process
404 `HttpServer`, or `http://127.0.0.1:9/v1` — rather than leaving it unset, because the config
always supplies a value. The model-touching cases are endpoint-gated.
