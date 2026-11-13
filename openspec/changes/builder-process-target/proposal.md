# Proposal

## Why

POC 24 showed a business process can be decomposed into several coherent agents — a process is N
`AgentSpec` packs composed at runtime through shared intermediate types. The builder today emits
exactly one pack and has no notion of a process. This change lets the builder generate a
multi-agent **process** (N packs), validate the cross-agent hand-offs (including cycle detection),
repair the common failure, and publish the packs — leveraging the runtime's existing multi-pack
composition.

## What Changes

- **smith-common**: a new `ProcessSpec` aggregate (a named, ordered list of `AgentSpec` packs) plus
  its reader/writer.
- **Builder**: the build generates *both* a single-agent spec and a multi-agent process (N agents in
  one single-shot call), validates the process's cross-agent hand-offs with a `ProcessValidator`
  (shared types, per-agent goal reachability, process-wide feeding, goal-name uniqueness,
  connectivity, **cycle detection**), runs a bounded reorder repair on a detected cycle, evaluates
  both candidates, recommends the valid higher-scoring one (a tie, or an invalid process, recommends
  the single-agent spec), stores both, and publishes only the recommended one.
- A new capability spec `agent-spec-process`.

## Capabilities

### New Capabilities

- `agent-spec-process`: the builder decomposes a business-process description into multiple agent
  specs, validates the cross-agent hand-offs (including cycles), publishes the packs, and evaluates
  the composed process.

### Modified Capabilities

(none)

## Impact

- `smith-common`: a `ProcessSpec` model + reader/writer.
- `prod/embabel-smith-builder`: the dual-candidate generation / recommendation / validation / repair
  / publish / eval path (no new CLI flag — the build always generates both candidates and
  recommends).
- `openspec/specs/`: a new `agent-spec-process` spec.
