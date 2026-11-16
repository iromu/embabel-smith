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

`core/TrustTier` and `core/TrustTierPolicy` gate which steps may run; a high-trust step needs an
explicit tier (`--trust-tier`) or the run fails with `TrustTierBlockedException`. This is the product
form of the per-tier tool-group allow-list proven in POC 10: a community-sourced pack must not reach
privileged tool groups.

## Traces

**Every run emits a trace record — including failed runs.** This is a product requirement, not an
observability extra: the self-improvement loop has no other evidence source, so a run that leaves no
trace is a run the loop cannot learn from.

`trace/TraceRecorder` writes `trace/TraceRecord` (per-step durations, success, input/output
excerpts, the trust tier). Design decisions that matter to a reader of the code:

- **The writer stays dumb.** It appends with `SYNC` and does no retention. The rolling window is a
  *read*-side concern applied by the loop. A writer that trims would eventually disagree with
  whoever owns retention.
- **Excerpts are truncated to 500 characters** (`DEFAULT_EXCERPT_LIMIT`). Traces serve the loop and
  the UI, not archival fidelity.
- **The trust tier is recorded in the trace** so the loop can distinguish "failed because the tier
  blocked it" from "failed because the spec is wrong". A refinement must never try to fix a policy
  outcome.
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

## The model endpoint

The endpoint is pluggable and env-driven (`OPENAI_CUSTOM_*`), and starting a run without it yields a
clear configuration error rather than an opaque model failure. The gate itself is
`smith-common`'s `ModelEndpointConfig`; see [`05-architecture.md`](05-architecture.md) for why no
model call in `prod/` may bypass Embabel's `Ai` layer.

Offline tests cover the core with the endpoint unset; the model-touching cases are endpoint-gated.
