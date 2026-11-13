# Tasks

## 0. Resolve open design decisions

- [x] 0.1 Resolve the design open questions (eval mechanism, shape, repair aggressiveness,
      sequencing vs change B) and record the decisions in `design.md`. Verify `design.md` no longer
      lists them as open.

## 1. ProcessSpec in smith-common

- [ ] 1.1 Add the `ProcessSpec` aggregate (named, ordered list of `AgentSpec`) and its reader/writer
      (one directory per pack) to `smith-common`. Verify an offline round-trip test writes and reads
      a process back to the same packs.
- [ ] 1.2 Port POC 24's `ProcessValidator` (shared types, per-agent goal reachability, process-wide
      feeding, goal-name uniqueness, connectivity, cycle detection) into `smith-common`. Verify
      offline tests: a well-formed process is valid; a cycle is detected; an unconnected agent is
      flagged.

## 2. Process generation + repair

- [ ] 2.1 Extend the single-shot generation (from change A) to emit a `ProcessSpec` (the multi-agent
      candidate). Verify an offline test (canned multi-agent JSON) parses into an ordered list of
      packs.
- [ ] 2.2 Add the bounded cycle repair (topological reorder; block if the cycle persists). Verify an
      offline test that a reorderable cycle is repaired and re-validates, and a non-repairable cycle
      blocks.

## 3. Dual-candidate evaluation + recommendation

- [ ] 3.1 Generalize the eval invoker to deploy N packs and run the entry goal (reimplemented in the
      builder, no runtime dependency), and score both candidates (the single-agent spec and the
      composed process). Verify an offline test (fake model) scores both candidates.
- [ ] 3.2 Add the recommendation (recommend the valid candidate with the higher score; a tie or an
      invalid process recommends the single-agent spec). Verify offline tests: a valid higher-scoring
      process is recommended; an invalid process falls back to single; a tie recommends single.

## 4. Store both, publish the recommended + docs

- [ ] 4.1 Store both candidates and publish only the recommended one via the named-directory
      publisher (reusing change B's generalization). Verify an offline test that the recommended
      candidate publishes to `folder:` and both candidates are stored.
- [ ] 4.2 Wire the dual-candidate build end-to-end (generate both → evaluate both → recommend →
      store both → publish the recommended) and update the builder `README.md`. Verify `smith build`
      generates both candidates, reports the recommendation, and publishes the recommended one; run
      the builder + smith-common offline suites (green, no endpoint) and a live build.
