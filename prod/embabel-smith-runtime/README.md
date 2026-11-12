# embabel-smith-runtime

The runtime product: ingests agent specs from a source (folder or git URI), runs an exported
goal over them, enforces trust tiers, and records every run as a JSONL trace.

Everything executed is *data* (the agent-spec YAML): ingestion deploys the packs' step specs
onto the Embabel `AgentPlatform`, planning uses a non-model planner (default GOAP), and only
the action bodies invoke the model.

## Usage

```
smith run --source <uri> [--goal <name>] [--input <text>] [--planner <name>]
          [--trust-tier <tier>] [--trace-dir <dir>]
```

- `--source` (required) — spec source URI: `folder:<path>` or `git:<url>[@ref]`.
- `--goal` — goal to run; defaults to the single goal the source exports.
- `--input` — text bound to the goal's `UserInput`.
- `--planner` — planner override; defaults to GOAP.
- `--trust-tier` — `standard` (default) or `high`; case-insensitive. A run is refused when a
  pack declares tool groups the tier does not allow (POC 10 trust tiers).
- `--trace-dir` — directory for the JSONL trace; defaults to `~/.smith/traces` (file
  `runs.jsonl`).

On success the goal output is printed to **stdout** and the exit code is **0**. On run failure
the error is printed to **stderr** and the exit code is **1**. Usage errors (missing required
option, unknown value) exit **2**.

Example:

```
smith run --source folder:./agent-specs/explainer \
          --goal briefDone \
          --input "Explain how a coffee machine works"
```

### Model endpoint (env-driven)

The model endpoint is fully environment-driven — nothing is hardcoded (same convention as the
POCs, see `application.yml`):

| Variable | Purpose |
|---|---|
| `OPENAI_CUSTOM_BASE_URL` | OpenAI-compatible endpoint (LiteLLM, OpenAI, Ollama, ...) |
| `OPENAI_CUSTOM_API_KEY` | API key |
| `OPENAI_CUSTOM_MODELS` | Comma-separated model list exposed to the platform |
| `EMBABEL_MODELS_DEFAULT_LLM` | The model used for action bodies (must be one of the listed models) |

Without an endpoint the CLI exits 1 with a clear configuration error naming the missing
variables.

## Trace schema

Every run appends one JSON object (one line) to the trace store:

```json
{
  "runId": "3f2c...",
  "timestamp": "2026-11-05T10:00:00Z",
  "source": { "uri": "folder:/path/to/library", "ref": null, "resolvedAt": null },
  "pack": { "name": "extract", "contentHash": "9a1f...64-hex" },
  "goal": "briefDone",
  "input": "Explain how a coffee machine works",
  "planner": "GOAP",
  "trustTier": "standard",
  "steps": [
    { "name": "extractTopic", "status": "completed", "durationMs": 1234,
      "outputExcerpt": "{topic=coffee, audience=...}" }
  ],
  "success": true,
  "error": null,
  "outputExcerpt": "..."
}
```

Field semantics:

- `source.uri` — the source URI as given. `source.ref` is the pinned ref of a `git:` source
  (`null` for folder sources or unpinned git); `source.resolvedAt` is the commit SHA the git
  source materialized at (`null` otherwise).
- `pack` — the pack that **exports the run's goal**, with a content hash of the pack as
  resolved (`null` when the goal was not found, so no pack can be named).
- `planner` / `trustTier` — the planner the run resolved to and the tier granted
  (`null` planner when the run never started, e.g. tier block or goal not found).
- `steps` — one entry per executed action, in execution order: `name`, `status`
  (`completed` / `failed`), `durationMs`, and an `outputExcerpt` (the last blackboard value
  bound to the step's declared output type, truncated to 500 chars). Empty when the run never
  executed (tier block, goal not found, or no plan reachable).
- `success` / `error` — the overall outcome; `error` carries the failure reason (unmet
  preconditions name the unmet condition, tier blocks name the offending pack and tier, model
  failures carry the model error).
- `outputExcerpt` — the goal output on success (truncated to 500 chars); `null` on failure.

Readers are corrupt-line-tolerant: a malformed line is skipped and reported as a warning
rather than failing the read.

## Trace seam

Finding from the task-1.1 spike (live scratch run against the POC 1 `explainer` pack,
Embabel 1.5.4): the per-step extraction seam is `AgentProcess.history` plus the blackboard.

- **`AgentProcess.getHistory()`** — exactly one `ActionInvocation` per executed action, in
  execution order. Each entry carries `actionName`, the action start `timestamp`, and the
  action's `runningTime` (duration). Populated by `AbstractAgentProcess.doExecuteAction`
  after every action execution, regardless of outcome. This is the authoritative per-step
  name/status/timing source.
- **`process.getBlackboard().getObjects()`** — all blackboard entries in add order. For the
  spike run: `[UserInput, TopicBrief, BackgroundMessage]`. Pack-declared (dynamic) types
  appear as tagged `LinkedHashMap`s (e.g. `{topic=..., audience=..., tone=...}`), so
  `String.valueOf(entry)` yields a readable excerpt without extra mapping.
- **Overall outcome** — `process.getStatus()` (COMPLETED/STUCK/FAILED/...),
  `process.getFailureInfo()`, and `process.lastResult()` (the goal output, a
  `BackgroundMessage` for the `explainer` goal).
- **Unmet preconditions** — a goal's preconditions are its output-type conditions
  (`{backgroundMessage:com.embabel.agent.spec.model.BackgroundMessage=TRUE}`). On a STUCK run
  (no plan reachable), `process.getGoal()` is `null`, so the unmet preconditions are read from
  the **deployed `Goal` object itself** (it implements `ConditionStep` with
  `getPreconditions()`) against `process.getLastWorldState()` — a `BlackboardWorldState`,
  which implements `ConditionWorldState`. A precondition is unmet when its key is not
  `ConditionDetermination.TRUE` in the final state.

The recorder therefore reads: steps from `history` (name, duration), per-step output
excerpts from the blackboard by the step's declared output type, and the overall
success/error from `status`/`failureInfo`/`lastResult()`.

One platform behaviour worth knowing: action errors (e.g. a model-call failure) are
**rethrown to the caller** of `runAgentFrom` rather than recorded on the process, so
`RuntimeCore.run()` converts them into a failed `RunOutcome` (and the trace records the
failure reason).
