---
title: "The smith builder product (smith build)"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/BuilderService.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/BuilderAgent.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/eval/RefinementLoop.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/security/AgentSpecSecurityScanner.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/publish/GitPublisher.java"
  - "prod/embabel-smith-builder/README.md"
updated_at: "2026-11-06"
---

# The smith builder product (`smith build`)

`prod/embabel-smith-builder` is the product's builder: it takes a natural-language description and
produces a **published, validated** agent spec through a deterministic pipeline. It extracts the
proven POC logic — POC 2/3 (build), POC 5 (eval), POC 9 (refine), POC 21 (security), POC 18
(lifecycle) — into one pipeline that ends in a publish to a `folder:` or `git:` source. Non-launcher
packages are plain classes (no Spring) so the studio product (change 05) can reuse `BuilderService`
and the stages directly. It never depends on a `poc-*` artifact ([[product-phase]]).

## The CLI

```
smith build "<description>" --out <source-uri> [--max-refine <n>] [--overwrite] [--push]
```

- `<description>` (required) — the natural-language description of the spec.
- `--out` (required) — source URI to publish to: `folder:<path>` or `git:<url>[@ref]`
  ([[spec-sources]]).
- `--max-refine` — max refinement rounds (default **3**).
- `--overwrite` — replace an existing same-named pack instead of colliding.
- `--push` — push the git commit to the remote (default: **local commit only**).

Exit codes: **0** when the pack is published (report to stdout), **1** when blocked (the stage and
reasons to stdout), **2** on usage errors. The model endpoint is env-driven ([[model-endpoint]]).

## The pipeline

`BuilderService.build(...)` runs the stages in order; a block at any stage stops the build and
names the stage in the report. Nothing is published when a gate fails.

1. **Compile → validate → write** (`BuilderAgent`). The description is compiled to a draft,
   validated for type bindings and prompt references, and — only if valid — written to a scratch
   dir and re-validated. An invalid draft is **never** written ([[validate-before-write]]). A
   draft with compiler issues (e.g. an ambiguous description) blocks at `build`.
2. **Eval → bounded refine** (`RefinementLoop`). The pack is deployed and run against the eval
   task set; the score is the fraction of passing tasks. Below the gate (default **1.0**) the
   terminal action's prompt is rewritten and the loop repeats, up to `--max-refine`. A rewrite that
   fails validation is discarded. Still below the gate → block at `eval` ([[eval-guided-refinement]]).
3. **Security gate** (`AgentSpecSecurityScanner`). A static scan of the pack's prompts; a
   high-severity (critical) finding blocks at `security` ([[spec-security-scanning]]).
4. **Lifecycle** (`LifecycleStamper`). The pack is stamped with a content hash, the baseline eval
   pass-rate, the initial state `incubating` (the `incubating → active` promotion is owned by
   change 05), and a timestamp, written as a `lifecycle.yml` sidecar ([[spec-lifecycle]]).
5. **Publish** (`FolderPublisher` / `GitPublisher`). The pack + `lifecycle.yml` is written to the
   target and committed (git) or written (folder).

## Publish semantics

- **`folder:<path>`** — the pack is written to `<path>/<pack-name>/` (manifest, goal, steps, types,
  plus `lifecycle.yml`). A same-named pack is a collision unless `--overwrite`.
- **`git:<url>[@ref]`** — the source is cloned into a scratch work tree, the pack is written as a
  subdirectory named after the pack, and committed (author `Smith Builder`). `--push` pushes;
  without it the commit stays local. Re-publishing an unchanged pack stages nothing and skips the
  commit. All git runs non-interactively (`GIT_TERMINAL_PROMPT=0`, `core.askpass=false`).

The build report names the eval score, the security findings, the lifecycle state + hash, and the
publish path on success; on a block it names the stage and the reasons.

## Tests

Offline tests cover the `BuilderAgent` compile/validate/write, the `RefinementLoop`, the
`AgentSpecSecurityScanner` verdicts, the `LifecycleStamper`, and the publishers (collision, no-push).
The live build (which calls the model) is endpoint-gated.
