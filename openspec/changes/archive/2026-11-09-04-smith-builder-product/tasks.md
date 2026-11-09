# Tasks

## 1. Build core (extraction)

- [x] 1.1 Extract POC 3's `BuilderAgent` + POC 2's compiler into `prod/embabel-smith-builder` (output dir parameterized; D5 validation on a throwaway platform intact)
- [x] 1.2 Wire `BuilderService` (description → agent → written pack) as a plain class
- [x] 1.3 Offline tests: coherent description builds (fake compiler), invalid draft writes nothing, ambiguous description returns issues

## 2. Eval + refinement

- [x] 2.1 Extract the POC 5 eval harness (task set parameterized) and the POC 9 refiner
- [x] 2.2 Implement the bounded refinement loop (`--max-refine`, default 3) and the score gate (default 1.0, configurable)
- [x] 2.3 Offline tests: passing eval retains prompts, failing eval refines, bound stops the loop, below-gate blocks publication

## 3. Security + lifecycle

- [x] 3.1 Extract the POC 21 security scanner; wire it as the pre-publish gate (high-severity = block)
- [x] 3.2 Implement the `lifecycle.yml` sidecar (content hash, baseline pass-rate, `state: incubating`, updatedAt)
- [x] 3.3 Offline tests: clean pack passes, planted high-severity finding blocks, sidecar content correct

## 4. Publishing

- [x] 4.1 Implement `FolderPublisher` (`folder:<path>` → `<path>/<pack-name>/`)
- [x] 4.2 Implement `GitPublisher` (materialize via change-02 cache, write pack, local commit `smith: publish <pack> (baseline <score>)`; `--push` explicit, off by default)
- [x] 4.3 Collision handling: same-name pack in target → report, require `--overwrite`
- [x] 4.4 Offline tests: folder publish, git publish against a local bare repo (commit present, nothing pushed), collision + overwrite

## 5. CLI + verify

- [x] 5.1 Implement the picocli CLI `smith build` (args per spec; build report on stdout; blocking reason + non-zero exit on failure)
- [x] 5.2 `mvn -pl prod/embabel-smith-builder test` green offline
- [x] 5.3 Endpoint-gated integration test (skipped without `OPENAI_CUSTOM_*`): one live build publishes a pack with a real baseline
- [x] 5.4 Write `prod/embabel-smith-builder/README.md` (usage, gates, publish semantics)
