# Tasks

## 1. Build core (extraction)

- [ ] 1.1 Extract POC 3's `BuilderAgent` + POC 2's compiler into `prod/embabel-smith-builder` (output dir parameterized; D5 validation on a throwaway platform intact)
- [ ] 1.2 Wire `BuilderService` (description → agent → written pack) as a plain class
- [ ] 1.3 Offline tests: coherent description builds (fake compiler), invalid draft writes nothing, ambiguous description returns issues

## 2. Eval + refinement

- [ ] 2.1 Extract the POC 5 eval harness (task set parameterized) and the POC 9 refiner
- [ ] 2.2 Implement the bounded refinement loop (`--max-refine`, default 3) and the score gate (default 1.0, configurable)
- [ ] 2.3 Offline tests: passing eval retains prompts, failing eval refines, bound stops the loop, below-gate blocks publication

## 3. Security + lifecycle

- [ ] 3.1 Extract the POC 21 security scanner; wire it as the pre-publish gate (high-severity = block)
- [ ] 3.2 Implement the `lifecycle.yml` sidecar (content hash, baseline pass-rate, `state: incubating`, updatedAt)
- [ ] 3.3 Offline tests: clean pack passes, planted high-severity finding blocks, sidecar content correct

## 4. Publishing

- [ ] 4.1 Implement `FolderPublisher` (`folder:<path>` → `<path>/<pack-name>/`)
- [ ] 4.2 Implement `GitPublisher` (materialize via change-02 cache, write pack, local commit `smith: publish <pack> (baseline <score>)`; `--push` explicit, off by default)
- [ ] 4.3 Collision handling: same-name pack in target → report, require `--overwrite`
- [ ] 4.4 Offline tests: folder publish, git publish against a local bare repo (commit present, nothing pushed), collision + overwrite

## 5. CLI + verify

- [ ] 5.1 Implement the picocli CLI `smith build` (args per spec; build report on stdout; blocking reason + non-zero exit on failure)
- [ ] 5.2 `mvn -pl prod/embabel-smith-builder test` green offline
- [ ] 5.3 Endpoint-gated integration test (skipped without `OPENAI_CUSTOM_*`): one live build publishes a pack with a real baseline
- [ ] 5.4 Write `prod/embabel-smith-builder/README.md` (usage, gates, publish semantics)
