---
title: "The smith runtime product (smith run)"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/AgentSpecRunService.java"
  - "prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/core/RuntimeCore.java"
  - "prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/core/LifecycleSidecar.java"
  - "prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/core/LifecycleBlockedException.java"
  - "prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/cli/RunCommand.java"
  - "prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/trace/TraceRecorder.java"
  - "prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/trace/JsonlTraceStore.java"
  - "prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/core/StepSpecAssembler.java"
  - "prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/remote/RemoteActionBridge.java"
  - "prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/remote/RemoteBridges.java"
  - "prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/remote/RemoteBridge.java"
  - "prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/remote/AdvertisedAction.java"
  - "prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/remote/BridgeState.java"
  - "prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/remote/ExecuteResult.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/eval/StepSpecAssembler.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/rest/RunController.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/rest/RemoteController.java"
  - "prod/embabel-smith-studio/src/test/java/com/embabel/smith/studio/NoA2aEndpointTest.java"
  - "prod/embabel-smith-studio/src/test/java/com/embabel/smith/studio/A2aEndpointWhenEnabledTest.java"
  - "docs/07-runtime.md"
  - "docs/05-architecture.md"
  - "prod/embabel-smith-runtime/src/main/resources/application.yml"
  - "smith-common/src/main/java/com/embabel/smith/common/trace/TraceRecord.java"
  - "prod/embabel-smith-runtime/README.md"
updated_at: "2026-11-26"
---

# The smith runtime product (`smith run`)

`prod/embabel-smith-runtime` is the product's runtime: it ingests agent specs from a source
(`folder:` or `git:` URI, see [[spec-sources]]), runs an exported goal over them, enforces trust
tiers and the lifecycle state, and records every run as a JSONL trace. It extracts POC 1's no-code
ingest + run and
generalizes it to **multi-pack** (POC 6), adding POC 10's trust tiers and a per-run trace.
Everything executed is *data* (the pack YAML): ingestion deploys the packs' step specs onto the
Embabel `AgentPlatform`, planning uses a non-model planner (default GOAP), and only the action
bodies invoke the model ([[runtime-run]], [[no-code-ingestion]]).

## The CLI

```
smith run --source <uri> [--goal <name>] [--input <text>] [--planner <name>]
          [--trust-tier <tier>] [--trace-dir <dir>]
```

- `--source` (required) — `folder:<path>` or `git:<url>[@ref]`.
- `--goal` — the goal to run; defaults to the single goal the source exports.
- `--input` — text bound to the goal's `UserInput`.
- `--planner` — planner override; defaults to GOAP.
- `--trust-tier` — `standard` (default) or `high`; a run is refused when a pack declares tool
  groups the tier does not allow ([[trust-tiers]]).
- `--trace-dir` — the JSONL trace directory; defaults to `~/.smith/traces` (file `runs.jsonl`).

Exit codes: **0** on success (goal output to stdout), **1** on run failure (error to stderr), **2**
on usage errors. The model endpoint is env-driven, as in the POCs ([[model-endpoint]]), and **the
runtime applies no endpoint gate**: `application.yml` feeds all three providers an inert but
well-formed default (`…:sk-smith-no-endpoint`, `…:http://127.0.0.1:9/v1`) so the app boots with
nothing set, and an unconfigured endpoint surfaces as a *failed run* — exit 1 carrying the run's
error — rather than as a named configuration error listing missing variables. The structured
`MODEL_NOT_CONFIGURED` refusal is a studio-layer concern (`smith-common`'s `ModelEndpointConfig`,
consulted by the studio's controllers). `docs/07-runtime.md` §"The model endpoint" is the authority
for that; `prod/embabel-smith-runtime/README.md` still carries the older wording. `docs/05-architecture.md`
states the detection order for the env-driven apps: any `OPENAI_CUSTOM_*` variable set selects the
OpenAI-compatible endpoint, else `OPENAI_API_KEY` the bundled OpenAI catalog, else `ANTHROPIC_API_KEY`
Anthropic's, else no provider is configured at all.

## Ingest + run

`AgentSpecRunService` (a plain class — the CLI, the studio and the A2A endpoint all embed it) owns
the flow: parse + resolve the source, ingest, run, record. `RuntimeCore.ingest(source)` reads every
pack, builds a
shared `DataDictionary` from the built-in I/O types plus every pack's intermediate types
(deduplicated by name — the shared type is the cross-pack composition glue), assembles step specs,
and deploys one agent. A source with no packs, or two packs exporting the same goal name, is an
error. `run(...)` checks the trust tier before execution, resolves the planner, and runs the goal.
One platform behaviour worth knowing: action errors (e.g. a model-call failure) are **rethrown** to
the caller rather than recorded on the process, so `run()` converts them into a failed `RunOutcome`.

Every run attempt emits a trace — including ones that fail *before* resolution or ingestion (bad
source URI, ingestion error, ambiguous goal selection): the record carries the source URI with
`pack` null. And a trace-write failure never changes the run's result — tracing observes, never
intervenes (the record is simply dropped).

## The lifecycle gate

A pack whose `lifecycle.yml` says `retired` is **not ingested at all**: `RuntimeCore.ingest` skips it
before deployment (`core/RuntimeCore.java:113`), because a retired pack left on the platform keeps its
actions plannable from a live pack's goal. A pack with no `lifecycle.yml` has no recorded state, which
stays runnable; a source whose packs are *all* retired reports an empty source.

A run that asks for a goal exported only by a retired pack throws `LifecycleBlockedException`
(`core/LifecycleBlockedException.java`), carrying the stable message prefix
`Run blocked by lifecycle state` plus the pack's name. That type is deliberately **not** a
`GoalNotFoundException`: retirement makes the goal genuinely absent, and a goal-not-found message would
be indistinguishable from a typo — and the improvement loop reads failures by their message prefix, so
it would classify a policy outcome as a spec defect and start refining prompts against it. The documented
revival is an explicit rebuild, which republishes the pack and leaves `retired`; there is no runtime
override. The block follows the trust-tier convention, and `AgentSpecRunService` catches the three
together (`GoalNotFoundException`, `LifecycleBlockedException`, `TrustTierBlockedException`) as run
failures rather than crashes.

The lifecycle vocabulary is **duplicated rather than shared**, and `docs/07-runtime.md` §"Lifecycle
state" is the authority. The builder owns it — `builder/lifecycle/LifecycleState` is the four-valued
enum and `LifecycleStamper` writes it — while the runtime reads `state` **as a string** and compares it
only against its own local `RETIRED` constant, so the reader never depends on the writer; the loop's
`builder/improve/Detector` likewise matches `LifecycleBlockedException`'s message prefix
(`Detector.LIFECYCLE_BLOCK_PREFIX`) rather than importing the class. The settled plan is to move those
types down into `smith-common` *when a second reader appears*, not to add a builder→runtime edge
meanwhile (`docs/05-architecture.md`). The contract between the modules is therefore one state string
and one message prefix — renaming either is a change in both places.

## Per-run model pinning

`AgentSpecRunService.run(sourceUri, goal, input, plannerOverride, tier, model)` takes a sixth
argument: the LLM every action step runs on. `RuntimeCore.ingest(source, model)` passes it to
`StepSpecAssembler.assemble(packDirs, targetDir, model)`, which writes an `llm.model` key into each
step as it copies that step into the flat staged step directory. The pin is a property of the *run*,
not of the pack: the source pack on disk keeps exactly what it had, and a step that already declared
an `llm` block keeps its other keys with only `model` replaced. A blank model leaves every step on the
model its own YAML names, which is what both other doors do — `RunCommand` has no `--model` flag and
the A2A handler calls the five-argument overload. The studio is the caller that pins:
`rest/RunController` passes `request.model()`. The builder keeps its own copy of the assembler
(`builder/eval/StepSpecAssembler.java`) with the same model argument.

## The remote-action client

`runtime/remote/` is the product's client for a remote-action sidecar — the three-endpoint contract
POC 16 proved (`GET /api/v1/actions`, `GET /api/v1/types`, `POST /api/v1/actions/execute`) — written
on plain `java.net.http.HttpClient`, so no remote library enters the product. It calls two of the
three: the catalog call is the reachability probe and the execute call runs an action, while
`GET /api/v1/types` is deliberately not fetched — the pane's one text box feeds only a `UserInput`,
and `AdvertisedAction.singleUserInput` marks an action declaring anything else as not runnable.
`probe()` decides reachability from the *response*: a 2xx whose body is not a JSON array is
unreachable, and a sidecar that never answers is a returned `BridgeState` rather than a thrown
exception, because an absent process is a state a pane has to display. Two settings behind that are
fixed rather than configured: **3 seconds to connect and 10 for the request** (`CONNECT_TIMEOUT` /
`REQUEST_TIMEOUT`) — the bridge list renders when a pane opens, so a timeout knob nobody asked for
buys a UI that can hang on a host that accepts the socket and never answers. And when the failure
carries no message (on this JVM `java.net.ConnectException` arrives with a null message all the way
down the cause chain) the reason kept is the exception's **own name**, which is what lets a reader
tell "refused the socket" apart from "answered with something that was not a catalog". `execute()` returns an
`ExecuteResult` in one of three states — executed with the sidecar's `content`, refused carrying the
sidecar's own status and untouched body, or unreachable. `RemoteBridges.parse` reads the operator's
comma-separated `name@url` list and reports the entries it cannot parse instead of inventing a name
or a host; `RemoteBridge`, `AdvertisedAction` and `BridgeState` are the shapes. The tests are
offline, against a stub sidecar.

Its only consumer is the studio's `rest/RemoteController`, which holds no HTTP of its own — the
studio's `NoDirectModelRequestTest` fails the build when a studio production source imports
`java.net.http.`, which is why the client lives in this module. That controller never starts a
sidecar and never accepts an address from a request: a request names a configured bridge, and a name
configuration does not define is refused before anything is contacted.

**Planning over a remote action is not a product capability** (`docs/07-runtime.md` §"Remote action
bridges"). The bridge can report that a sidecar offers an action, and it can run that action because a
person named it; it cannot put a remote action into a GOAP plan. The pack format has no step kind that
can name a remote action, so there is nothing for a planner to select; `embabel-agent-remote`, which
supplies `RestAction`, is not a dependency of any `prod/` module and must not become one before that
format decision; and remote actions would have to be registered into the platform the planner
searches, which the ingest path never does. The bridge is a pane feature, and the planning half stays
POC-only ([[runtime-adapter]]).

## The JSONL trace

Every run appends one JSON object (one line) to the trace store: `runId`, `timestamp`, `source`
(uri / pinned ref / resolved commit), `pack` (the pack exporting the goal + its content hash),
`goal`, `input`, `planner`, `trustTier`, `steps` (one per executed action: name, status,
durationMs, a 500-char `outputExcerpt`), `success`, `error`, and `outputExcerpt`. `pack` is `null`
when the goal was not found; `steps` is empty when the run never executed (tier block, goal not
found, no plan). Readers are corrupt-line-tolerant — a malformed line is skipped and reported, not
fatal.

The store is an SPI, and the seam does **not** live here: `TraceRecord` (a record) and `TraceStore`
(an interface) are framework-free types in `smith-common/common/trace/`, which is why the improvement
loop could move from the studio into the builder without the builder depending on the runtime
(`docs/05-architecture.md`). This module contributes the default implementation,
`trace/JsonlTraceStore`, at `~/.smith/traces/runs.jsonl`; the studio contributes
`storage/JpaTraceStore` implementing the same interface. (`docs/07-runtime.md` §"The trace store is an
SPI" still says the seam lives in the runtime; the imports say otherwise.)

## The trace seam

The per-step data comes from `AgentProcess.getHistory()` (exactly one `ActionInvocation` per
executed action, in order, with name + start + duration) plus the blackboard (`getObjects()` in add
order — pack-declared types appear as tagged maps, so `String.valueOf` yields a readable excerpt).
The overall outcome comes from `getStatus()` / `getFailureInfo()` / `lastResult()`. On a STUCK run
(no plan reachable) the goal is `null`, so unmet preconditions are read from the deployed `Goal`
(it implements `ConditionStep`) against the final `BlackboardWorldState` — a precondition is unmet
when its key is not `TRUE`. See the module README's "Trace seam" section for the full spike
findings.

## A third door: the A2A endpoint

The starter library `prod/embabel-smith-a2a-starter` supplies the beans and
`prod/embabel-smith-studio` hosts them (`make run-a2a` sets `SMITH_A2A_ENABLED=true`, the env form of
`smith.a2a.enabled`), driving the same
`AgentSpecRunService` over the A2A protocol, so an A2A run is the same run: same tier gate, same planner
selection, one record in the same JSONL trace — which is also what its `tasks/get` reads. This module
hosts nothing, and not by choice: `smith run` boots non-web, closes the context and exits, and the
module cannot carry the starter even if it wanted to — the starter's main sources import
`AgentSpecRunService`, `core.TrustTier`, `core.TrustTierPolicy` and `trace.JsonlTraceStore` from here,
so a runtime→starter dependency is a reactor cycle. The contract is stated as an explicit opt-in
(`docs/07-runtime.md`): no process exposes an endpoint unless the surface is enabled in
that process, and the CLI cannot expose one at all because it never depends on the starter. The surface
is an unauthenticated local development endpoint on port 18090, serving five operations. See
[[a2a-endpoint]].

## Tests

`RuntimeCoreTest`, `PlannerResolverTest`, `AgentSpecTypeMapperTest`, `StepSpecAssemblerTest`,
`TrustTierPolicyTest`, `RunCommandTest`, `AgentSpecRunServiceTest` (pre-resolution / pre-ingestion
failures still emit a trace; a trace-write failure leaves the outcome unchanged), and the trace
tests (`JsonlTraceStoreTest`, `TraceRecordJsonTest`, `TraceRecorderTest`) — offline, using test
packs under `src/test/resources/test-packs/`. `LiveRunIntegrationTest` is the endpoint-gated live
run.
