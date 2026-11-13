# Design

## Context

`smith-common` has no multi-pack concept — `AgentSpec` is a single pack. The builder pipeline
(`BuilderService`) operates on one pack. The runtime, however, already composes multiple packs
(POC 6): `RuntimeCore.ingest` reads every pack in a source into one platform with a shared data
dictionary, and `run` executes a goal across them via the planner. POC 24 has the pieces this change
needs: `ProcessSpec` (a named ordered list of `AgentSpec`), `ProcessValidator` (shared types,
reachability, feeding, goal-name uniqueness, connectivity, cycle detection), and the single-shot
multi generator (one call emits N agents). See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- A `shape=process` build generates, validates, (repairs), publishes, and evaluates a multi-agent
  process.
- The process packs are directly ingestible by the runtime's existing multi-pack composition.

**Non-Goals:**
- Changing the single-pack spec build (change A owns the generation technique; change B owns the
  skill target).
- A process-level self-improvement loop.

## Decisions

- **`ProcessSpec` lives in `smith-common`.** It is the shared data model the builder produces and
  the runtime composes, with a reader/writer that emits one directory per pack (so a published
  process is a folder the runtime ingests as one source).
- **`ProcessValidator` ported into `smith-common`.** POC 24's validator (shared types, per-agent
  goal reachability, process-wide feeding, goal-name uniqueness, connectivity, and **cycle
  detection**) becomes the cross-agent gate; a process with any issue is not valid.
- **Generation produces two candidates.** The single-agent spec comes from change A's engine; the
  multi-agent process comes from the same single-shot engine extended to emit a `ProcessSpec` (one
  model call emits the whole process as JSON, parsed into an ordered list of `AgentSpec`).
- **Bounded repair.** On a detected cycle, the builder reorders the agents (topologically, where
  possible) and re-validates; if the cycle persists within the bound, the build blocks.
- **Eval generalizes the single-pack invoker to N packs.** The composed process is run by deploying
  all packs and invoking the entry goal — the same pattern the runtime uses. Both candidates are
  scored.
- **Recommend, store both, publish the recommended.** The build recommends the valid candidate with
  the higher score (a tie, or an invalid process, recommends the single-agent spec). Both candidates
  are stored; the publisher writes only the recommended one (reusing change B's named-directory
  publisher).

## Risks / Trade-offs

- [Single-shot multi is fragile (cycles in merging parallel branches)] → POC 24 found it "sometimes
  valid." The `ProcessValidator` detects the cycles and the bounded repair removes what it can; what
  it cannot is an honest block, not a silent publish.
- [Multi-pack eval is more expensive and less deterministic than single-pack] → The composed run
  invokes the model across N agents; the report shows the per-pack structure and the entry-goal
  result so a failure is inspectable.

## Resolved Decisions

- **Eval mechanism:** reimplement in the builder — generalize `AgentSpecInvokerFactory` to deploy N
  packs and run the entry goal (the same pattern the runtime uses). No builder → runtime dependency.
- **Shape: try both, recommend one.** The build generates *both* a single-agent spec and a
  multi-agent process, evaluates both, and recommends the candidate that validates cleanly with the
  higher eval score; a tie, or an invalid process, recommends the single-agent spec. Both candidates
  are stored; only the recommended one is published.
- **Repair aggressiveness:** reorder-only — topologically reorder the agents to break a cycle; block
  if the cycle persists (no branch restructuring).
- **Sequencing vs change B:** C lands after B and reuses B's "publish a named directory" publisher
  generalization to write the N pack directories.
