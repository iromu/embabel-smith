# Design

## Shape

```
CLI (picocli)
   |
   v
BuilderService                        (plain class — studio reuses it)
   |  1. BuilderAgent (Embabel agent — POC 3 pattern, unchanged thesis)
   |       compile → validate (throwaway platform) → write   [D5]
   |  2. EvalRunner (POC 5) + Refiner (POC 9), bounded loop
   |  3. SecurityScanner (POC 21) — static, offline
   |  4. LifecycleStamper (POC 18 contract) — hash + baseline + state
   |  5. Publisher → FolderPublisher | GitPublisher
   v
output source (folder: or git:)
```

## Extraction map

| Product piece | POC origin | Generalization |
|---|---|---|
| Builder agent (compile → validate → write) | POC 3 `BuilderAgent` (+ POC 2 compiler) | copied; output dir parameterized |
| Throwaway-platform validation | POC 3 | unchanged (D5) |
| Eval harness | POC 5 | task set parameterized |
| Prompt refinement from failures | POC 9 | bounded loop, bound = `--max-refine` (default 3) |
| Security scanner | POC 21 | copied; static/offline, high-severity = block |
| Lifecycle contract | POC 18 | content hash + baseline pass-rate + state |

## Decisions

### Keep the builder an agent
The POC-phase thesis ("the builder is itself an Embabel agent planning compile → validate → write → evaluate over typed preconditions") is proven and is the project's identity. `BuilderService` is a thin wrapper that runs the agent against a description and collects the written pack — the agent stays, the orchestration does not get hardcoded.

### Score gate
A pack publishes only if its final eval pass-rate ≥ threshold, default **1.0** (all eval tasks pass — the POC 9 end-state), configurable. Below the gate after the refinement bound: report and stop, nothing published. The pass-rate that clears the gate becomes the pack's **baseline** (the loop in change 05 compares future runs against it).

### Lifecycle metadata: sidecar, not manifest
The pack gains a `lifecycle.yml` sidecar (`contentHash`, `baselinePassRate`, `state: incubating`, `updatedAt`). Rationale: the manifest is format-owned by `smith-common` and shared with the frozen POCs; a sidecar keeps the format untouched while carrying exactly the POC 18 contract. Change 05 transitions the `state` field in place.

### Git publish = local commit, never push by default
`GitPublisher` materializes the target via the change-02 cache (clone/fetch), writes the pack into the working tree, and commits with the system `git` CLI: message `smith: publish <pack> (baseline <score>)`. `--push` is explicit and off by default — pushing is a shared-state action needing credentials and intent. Collision (same pack name exists in the target): report + require `--overwrite`, which replaces the directory and commits.

### Pack naming
Pack name = slug of the description's core noun phrase (deterministic function, offline-testable); the builder may refine the name during compile, but the slug function is the default so builds are reproducible.

## Testability

- Offline: validation gating (invalid draft → nothing written), scan gating (planted high-severity finding → blocked), refinement bound (fake failing eval → stops at bound), hashing, lifecycle sidecar content, folder publish, git publish against a local bare repo (commit exists, no push), collision handling, CLI parsing.
- Endpoint-gated (IT, skipped without env): one live build end-to-end — description → published pack with a real baseline score.
