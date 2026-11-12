---
title: "The smith runtime product (smith run)"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/AgentSpecRunService.java"
  - "prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/core/RuntimeCore.java"
  - "prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/cli/RunCommand.java"
  - "prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/trace/TraceRecorder.java"
  - "prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/trace/JsonlTraceStore.java"
  - "prod/embabel-smith-runtime/README.md"
updated_at: "2026-11-09"
---

# The smith runtime product (`smith run`)

`prod/embabel-smith-runtime` is the product's runtime: it ingests agent specs from a source
(`folder:` or `git:` URI, see [[spec-sources]]), runs an exported goal over them, enforces trust
tiers, and records every run as a JSONL trace. It extracts POC 1's no-code ingest + run and
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
on usage errors. The model endpoint is env-driven exactly as in the POCs ([[model-endpoint]]);
without it the CLI exits 1 with a clear configuration error naming the missing variables.

## Ingest + run

`AgentSpecRunService` (a plain class — the CLI and the studio both embed it) owns the flow: parse +
resolve the source, ingest, run, record. `RuntimeCore.ingest(source)` reads every pack, builds a
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

## The JSONL trace

Every run appends one JSON object (one line) to the trace store: `runId`, `timestamp`, `source`
(uri / pinned ref / resolved commit), `pack` (the pack exporting the goal + its content hash),
`goal`, `input`, `planner`, `trustTier`, `steps` (one per executed action: name, status,
durationMs, a 500-char `outputExcerpt`), `success`, `error`, and `outputExcerpt`. `pack` is `null`
when the goal was not found; `steps` is empty when the run never executed (tier block, goal not
found, no plan). Readers are corrupt-line-tolerant — a malformed line is skipped and reported, not
fatal.

## The trace seam

The per-step data comes from `AgentProcess.getHistory()` (exactly one `ActionInvocation` per
executed action, in order, with name + start + duration) plus the blackboard (`getObjects()` in add
order — pack-declared types appear as tagged maps, so `String.valueOf` yields a readable excerpt).
The overall outcome comes from `getStatus()` / `getFailureInfo()` / `lastResult()`. On a STUCK run
(no plan reachable) the goal is `null`, so unmet preconditions are read from the deployed `Goal`
(it implements `ConditionStep`) against the final `BlackboardWorldState` — a precondition is unmet
when its key is not `TRUE`. See the module README's "Trace seam" section for the full spike
findings.

## Tests

`RuntimeCoreTest`, `PlannerResolverTest`, `AgentSpecTypeMapperTest`, `StepSpecAssemblerTest`,
`TrustTierPolicyTest`, `RunCommandTest`, `AgentSpecRunServiceTest` (pre-resolution / pre-ingestion
failures still emit a trace; a trace-write failure leaves the outcome unchanged), and the trace
tests (`JsonlTraceStoreTest`, `TraceRecordJsonTest`, `TraceRecorderTest`) — offline, using test
packs under `src/test/resources/test-packs/`. `LiveRunIntegrationTest` is the endpoint-gated live
run.
