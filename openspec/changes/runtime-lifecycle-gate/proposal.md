# Proposal

## Why

`docs/10-self-improvement.md` states it plainly: "**Lifecycle state is advisory in v1 — the runtime does
not gate on it.** That is a deliberate simplification with the runtime-side gate left as a separate
change." `docs/12-backlog.md` lists the same under Runtime and storage: "**The runtime does not gate on
lifecycle state.** Advisory in v1; a runtime-side gate is its own change." This is that change.

The tree confirms nothing has crept in: `grep -rni 'lifecycle\|retired' prod/embabel-smith-runtime/src/`
returns zero matches — the module neither reads `lifecycle.yml` nor mentions a state. Meanwhile:

- `LifecycleState.RETIRED` is documented as "**The spec's contract is violated (its score collapsed) —
  terminal, out of the library.**"
- The studio already treats that seriously in one place only: `ImprovementLoop` skips a pack whose
  sidecar `state() == LifecycleState.RETIRED` with the comment "Retired packs are excluded from
  automatic refinement … a human revives them via an explicit rebuild".
- The sidecar is real and read elsewhere: `ImprovementLoop.readSidecar` opens
  `root.resolve("lifecycle.yml")` through `Yaml.mapper().readValue(...)` and falls back to
  `LifecycleState.INCUBATING` when the file is missing or unreadable.

So a pack that the state machine declares out of the library still runs everywhere the studio does not
happen to be looking — `smith run` over a shared source, and any consumer of `AgentSpecRunService`.
"Out of the library" currently means "not refined", not "not run".

This is the narrow gate: enforce the one state whose own definition says it must not run. `INCUBATING`,
`ACTIVE` and `DEGRADED` all describe packs that are meant to run — `DEGRADED` is "drift detected … but
the spec still functions" — so gating on them would be a policy nobody has stated.

## What Changes

- **A pack whose sidecar says `retired` is not ingested.** `RuntimeCore.ingest` already reads every pack
  of the resolved source into one platform; it skips a retired pack and records the exclusion by name in
  the ingested result, alongside the existing empty-source and duplicate-goal diagnostics.
- **A run whose goal belonged only to an excluded pack fails with a named lifecycle block**, not with the
  existing `GoalNotFoundException`. It follows the trust-tier convention exactly:
  `TrustTierBlockedException` prefixes its message `"Run blocked by trust tier: "` and
  `Detector.TIER_BLOCK_PREFIX` matches that prefix so the loop can tell a policy outcome from a spec
  defect. The lifecycle block gets the same treatment, because `docs/10-self-improvement.md` requires it:
  "A refinement must never try to fix a policy outcome."
- **A pack with no sidecar, or an unreadable one, runs.** This is the studio's existing behaviour — the
  fallback is `INCUBATING` — and it is what keeps the change from breaking every pack that predates
  stamping, including the sample packs and the frozen POC sources.
- **Nothing else is gated.** No minimum state, no per-state allow-list, no CLI flag, no `--allow-retired`
  escape hatch. A retired pack is revived the documented way — an explicit rebuild, which republishes it
  and moves it out of `retired`.

## Capabilities

### New Capabilities

- `runtime-lifecycle-gate`: which published packs the no-code runtime will execute. It covers where the
  lifecycle state is read from, which single state blocks a run, what a run blocked for that reason
  reports and records in the trace, how a pack with no recorded state is treated, and the exclusion's
  effect on a multi-pack source.

### Modified Capabilities

None. There are no capability specs under `openspec/specs/` — they were consolidated into `docs/` on
2026-11-15 — so there is no existing requirement text to modify. The durable homes are
`docs/07-runtime.md`, which gains the gate beside the existing trust-tier section, and
`docs/10-self-improvement.md`, whose "advisory in v1" sentence narrows to the states that remain
advisory.

## Impact

- **`prod/embabel-smith-runtime`**: `core/RuntimeCore` consults the sidecar during ingest; a new
  `core/LifecycleBlockedException` beside the existing `core/TrustTierBlockedException`; `core/IngestedSource`
  carries the excluded packs so the run can name them. `AgentSpecRunService` already converts a thrown
  block into a failed run whose message becomes the trace's `error` —
  `catch (GoalNotFoundException | TrustTierBlockedException ex) { outcome = RunOutcome.failed(ex.getMessage()); }`
  with an outer `catch (RuntimeException ex)` behind it — so the trace plumbing is reused; the new
  exception is added to that inner multi-catch so the block stays named rather than falling into the
  catch-all.
- **No new module dependency.** `LifecycleState`, `LifecycleMetadata` and `LifecycleStamper` live in
  `com.embabel.smith.builder.lifecycle`, and the runtime depends only on `smith-common` and
  `embabel-smith-specsource`. The runtime reads the sidecar's `state` value through `smith-common`'s
  `com.embabel.smith.common.io.Yaml` — the same mapper the studio already uses to read it — rather than
  depending on the builder. Relocating the lifecycle types into `smith-common` is the alternative and is
  rejected here, not overlooked; see `design.md`.
- **Behaviour change for a source that contains a retired pack**: goals it alone exported stop resolving,
  and a multi-pack plan can no longer route through it. That is the point of the change, and the error
  names the pack.
- **Traces gain one more never-executed run shape.** `TraceRecord`'s own javadoc enumerates "empty when
  the run never executed (tier block, goal not found, or no plan reachable)"; that list gains a lifecycle
  block.
- **`prod/embabel-smith-studio`**: one filter. `Detector` already excludes trust-tier blocks by matching
  `TIER_BLOCK_PREFIX`; it gains the same match for the lifecycle prefix. No new gate is added in the
  studio — the runtime's gate is the only one, and the studio's existing `RETIRED` skip in
  `ImprovementLoop` stays as the pre-emptive skip it already is.
- **Tests**: offline. `RuntimeCoreTest` and `TrustTierPolicyTest` already run the core against an inert
  local endpoint, and a retired pack is just a pack directory with a `lifecycle.yml` in it — no model, no
  network.
- **Docs**: `docs/07-runtime.md`, `docs/10-self-improvement.md`, `docs/12-backlog.md`.
