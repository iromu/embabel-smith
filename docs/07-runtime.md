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
`java -jar target/embabel-smith-runtime-<version>-exec.jar run --source …` — which is what the
container image runs. The classifier is what lets both exist: this module is a compile dependency of
the studio and the a2a starter, and a repackaged jar's classes sit under `BOOT-INF/classes`, where a
normal classpath cannot see them (see [14 — Containers](14-containers.md)).

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
