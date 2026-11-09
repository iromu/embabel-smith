# Design

## Shape

```
CLI (picocli)
   |
   v
SkillRunService                      (plain class — studio reuses it)
   |  1. SkillSource.parse(uri).resolve()          (change 02)
   |  2. RuntimeCore.ingest(resolvedPacks)         (extracted POC 1 + POC 6)
   |  3. RuntimeCore.run(goal, input, planner, tier)
   |  4. TraceRecorder.record(TraceRecord)         (JSONL append)
   v
AgentPlatform (Embabel)
   - multi-pack DataDictionary (POC 6 pattern)
   - trust-tier guardrail (POC 10 pattern)
   - GOAP default, per-run planner override (POC 1 pattern)
```

## Extraction map

| Product piece | POC origin | Generalization |
|---|---|---|
| Ingest + run single pack | POC 1 `SkillRunner` | → `RuntimeCore`, multi-pack aware |
| Multi-pack ingest + cross-pack plan | POC 6 | merge into `RuntimeCore` |
| Trust-tier enforcement | POC 10 `InputSanitizerGuardRail` + tier policy | guardrail installed per run, tier from CLI/config |
| Step-spec assembly, type mapping | POC 1 `StepSpecAssembler`, `SkillPackTypeMapper` | copied, pack-parameterized |

## Trace capture — the new seam

**Spike (first task):** verify what `AgentProcess` exposes for per-step extraction. POC 16's output shows per-action log lines and process status/duration, and POC 1 reads `process.lastResult()`; the expected seam is the blackboard (typed entries accumulate per step) plus process-level status/timing. The spike pins the exact API before the recorder is built. Fallback if per-step data is thin: wrap each step's output extraction at ingest time (the runtime already assembles step specs, so it can name the steps and read their blackboard entries by type).

**TraceRecord schema** (one JSON object, one per run):

```json
{
  "runId": "uuid",
  "timestamp": "ISO-8601",
  "source": { "uri": "git:...", "ref": "main", "resolvedAt": "sha" },
  "pack": { "name": "explainer", "contentHash": "sha256:..." },
  "goal": "explainerDone",
  "input": "...",
  "planner": "GOAP",
  "trustTier": "standard",
  "steps": [
    { "name": "draftExplanation", "status": "completed", "durationMs": 1234, "outputExcerpt": "..." }
  ],
  "success": true,
  "error": null,
  "outputExcerpt": "..."
}
```

Excerpts are truncated to 500 chars (configurable) — traces are for the improvement loop and the UI, not archival fidelity.

## Decisions

### CLI: picocli
Small, zero-spring, offline-testable, gives us `--help` and clean errors for free. The POCs used env vars + `main()` because they were one-shot demos; a product CLI needs real argument parsing. `smith run` is the entry; the module also exposes `SkillRunService` as a plain class so studio (change 06) embeds it without the CLI.

### Trace store: one JSONL file, append-only
Default `~/.smith/traces/runs.jsonl`, overridable via `--trace-dir`. Append-only, no locking beyond `Files.write(..., APPEND, SYNC)` — v1 is single-process per host. The reader (change 05) streams lines, skips corrupt ones with a warning. A rolling-window retention (default: keep last 5000 lines per host) is applied at read time by the loop, not by the writer — the writer stays dumb.

### Trust tiers carried into the trace
The granted tier is recorded per run so the loop can later distinguish "failed because blocked by tier" from "failed because the skill is bad" — a refinement must not try to "fix" a tier block.

### Duplicate goal names
Fail at ingest, not at plan time: two packs exporting the same goal name is a source-library bug; naming both packs at ingest is the actionable error.

## Testability

- Offline: ingest (valid/invalid/multi/collision), planner selection, trace record construction + JSONL append + corrupt-line read, CLI arg parsing, trust-tier block (using a deterministic fake step that would execute if unblocked).
- Endpoint-gated (IT, skipped without env): one live run end-to-end producing a real trace.
