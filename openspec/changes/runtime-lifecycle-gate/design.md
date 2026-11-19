# Design

## Context

See `proposal.md` for motivation; `specs/runtime-lifecycle-gate/spec.md` for the contract.

Facts this design has to work with, each read from the tree:

- `RuntimeCore.ingest(ResolvedSpecSource, String model)` reads every `ResolvedSpec` of the source with
  `this.reader.read(resolved.root())`, throws on an empty source, calls `checkGoalNameCollisions(packs)`,
  then deploys one agent. `run(...)` calls `ingested.packForGoal(goalName)` and then
  `checkTier(ingested.packs(), tier)`.
- The gate to copy already exists. `checkTier` throws `TrustTierBlockedException`, whose message begins
  `"Run blocked by trust tier: "` and names each offending pack. The studio matches that prefix —
  `Detector.TIER_BLOCK_PREFIX = "Run blocked by trust tier"` — precisely so a policy block is not fed to
  the refiner.
- `ResolvedSpec` is `record ResolvedSpec(Path root, String name, String contentHash, boolean validationOk)`
  — it carries no lifecycle state, and `specsource` is deliberately format-only.
- The sidecar is `lifecycle.yml` inside the pack directory, written by `LifecycleStamper.write` with
  `Yaml.mapper()`. A real one reads:

  ```yaml
  contentHash: "0be2d86f26cd3f99e15453df3407d9e3a72fcd42740947a6ceef060b635b24e7"
  baselinePassRate: 1.0
  state: "INCUBATING"
  updatedAt: "2026-11-17T21:35:38.683642067Z"
  ```

- The studio's reader falls back when there is no sidecar: `readSidecar` returns
  `new LifecycleMetadata(SpecContentHash.of(current), 0.0, LifecycleState.INCUBATING, Instant.now().toString())`
  when the file is absent or unreadable. Whatever this change decides must not disagree with that, or the
  studio and the runtime would disagree about whether a pack is runnable.
- Module edges: the runtime depends on `smith-common` and `embabel-smith-specsource`. The lifecycle types
  are in `embabel-smith-builder`. `smith-common` owns `com.embabel.smith.common.io.Yaml`.
- `LifecycleState` has exactly four states, and only `RETIRED` is described as terminal and out of the
  library; `DEGRADED` is explicitly "the spec still functions".

## Goals / Non-Goals

**Goals**

- A pack the state machine put out of the library cannot be run by the runtime.
- The block is named, in the error and in the trace, so it is never mistaken for a spec defect.
- Packs with no recorded state keep running — the gate must not retroactively punish anything published
  before stamping, nor any frozen POC source.
- No new module dependency, and no edit to `poc/`.

**Non-Goals**

- Not gating `INCUBATING` or `DEGRADED`. A run being refused for those states is a policy nobody has
  written down, and `DEGRADED`'s own definition says the pack still functions.
- No `--allow-retired` flag or any other override. The documented revival is an explicit rebuild, which
  republishes and leaves `retired`; a bypass flag would be a second, weaker revival path.
- Not writing lifecycle state from the runtime — it reads, never stamps.
- Not adding retrieval or ranking behaviour for `DEGRADED` packs. POC 15's curation-aware retrieval
  de-ranking lives in a POC; productising it is its own question.
- Not persisting or migrating sidecars, and not changing `PackContentHasher` / `SpecContentHash`.

## Decisions

**1. Gate `retired` only, at ingest, by excluding the pack.**
"Out of the library" is an ingest-time property: if the pack is in the platform, the planner can route
through it, which is exactly what cross-pack composition does (`docs/07-runtime.md`: "All packs from a
source land in one platform, so the planner can chain an action from one pack into another"). A check
that only looked at the *goal* pack would leave a retired pack's actions plannable from a live pack's
goal.
*Alternative — block only when the requested goal's pack is retired.* Rejected: it enforces retirement
for the entry point and ignores it for everything the planner can reach through composition, which is
half a gate with a surprising shape.
*Alternative — gate at plan time per step.* Rejected: it makes plan structure depend on sidecar content,
and `docs/07-runtime.md` promises "Plan structure is deterministic. The planner is not a model, so the
same pack and goal produce the same step sequence every time."

**2. A run whose goal was exported only by an excluded pack throws a named block, not `GoalNotFoundException`.**
The exclusion makes the goal genuinely absent, so `GoalNotFoundException` would fire — and it would be
indistinguishable from a typo, so the loop would treat a retirement as a spec defect and try to refine
it. `docs/10-self-improvement.md` forbids that: "A refinement must never try to fix a policy outcome."
The exception therefore carries the trust-tier convention: a stable message prefix naming the lifecycle
block and the retired pack, matched by the studio the same way `TIER_BLOCK_PREFIX` is matched.
*Alternative — leave it as `GoalNotFoundException` and let the message explain.* Rejected: the studio's
filter is a message-prefix match, so an unanchored explanation is invisible to it.

**3. Read the state through `smith-common`'s `Yaml`, not through a new dependency.**
The runtime needs one scalar from `lifecycle.yml`. It reads the file and takes `state`, using the mapper
it already has on its classpath.
*Alternative — add an `embabel-smith-builder` dependency to the runtime.* Rejected: the builder is the
writer side of the loop and pulls the generation and publishing stack with it; the runtime is the reader
and today depends only on `smith-common` and `specsource`.
*Alternative — relocate `LifecycleState` and `LifecycleMetadata` into `smith-common`.* This is the
cleaner end state and the one to reach for if a second reader appears, but it touches the builder's and
the studio's imports across a dozen files for one field. Rejected for this change, not dismissed; the
sidecar is format-owned-adjacent enough that the move will eventually be right.
*Alternative — put the state on `ResolvedSpec`.* Rejected: `specsource` is the read layer for the format,
and the sidecar is the lifecycle contract, which `docs/08-builder.md` explains is deliberately *not*
format-owned ("a sidecar rather than a manifest field because the manifest is format-owned by
`smith-common` and shared with the frozen POCs").

**4. Absent or unreadable sidecar means runnable.**
Matching `readSidecar`'s existing `INCUBATING` fallback keeps one rule in two places. A stricter rule
would break the sample packs, the frozen POC sources, and anything published before stamping.

**5. The exclusion is reported, never silent.**
`docs/07-runtime.md` already establishes the habit of naming what was skipped (an invalid candidate is
"reported as a warning and skipped"; duplicate goals name *both* packs). The ingested result carries the
excluded pack names for the same reason: a run that quietly lost a pack produces a plan the reader cannot
explain.

## Risks / Trade-offs

- **A source with a retired pack now runs a smaller plan than before.** Intended; the error and the
  exclusion list make the difference visible rather than silent.
- **A pack retired by mistake stops running with no runtime override.** Accepted: the revival path is
  documented and it is a rebuild, and the alternative — a bypass flag — is how a policy decays.
- **The message prefix becomes a contract** between the runtime and the studio, as the trust-tier prefix
  already is. Two modules matching a string is the existing convention here; it is named as a risk rather
  than engineered away.
- **Every pack now costs one extra file read at ingest.** Negligible, and it happens where the pack's
  files are already being read.
- **`DEGRADED` stays runnable**, so a drifting pack keeps serving. That is what the state means, but it
  means this change does not by itself protect a caller from degraded quality — only retirement stops a
  run.
