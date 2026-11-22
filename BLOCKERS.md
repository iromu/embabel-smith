# Blockers

Things that are actually blocking, re-verified against this tree on 2026-11-22. Each names the check
that shows it, so a reader can re-run it instead of trusting this page. History and reasoning live in
[`docs/12-backlog.md`](docs/12-backlog.md) and in memory, not here.

## 1. The wiki is corrected locally; the published site is not

Blocks the publish half of `builder-executes-its-own-packs` 5.3 and `poc27-improves-builder-agentspecs`
9.3. Locally the wiki is clean — 45 pages, 0 broken links, 0 dead `source_paths` — but the site at
https://iromu.github.io/embabel-smith/ still says the improvement loop lives in the studio while the
repo says it lives in the builder.

This needs a human, and "push `poc/iteration-2`" is not sufficient: `wiki-pages.yml` fires on a push to
`main` that touches `.wiki/**`, plus manual dispatch. Origin's HEAD is `main`, and this branch's wiki
commits are not on it, so a branch push deploys nothing. Either merge the wiki pages to `main` and push,
or run the workflow by hand.

Check: `sed -n '9,20p' .github/workflows/wiki-pages.yml` for the trigger, and
`git ls-remote --symref origin HEAD` for the default branch.

## 2. A2A protocol 1.0 waits on an upstream release

The reused library speaks 0.3: the served card reports `protocolVersion: 0.3.0`, dispatch keys off the
0.3 method names, and discovery is at `/a2a/.well-known/agent.json`, not the current specification's
`/.well-known/agent-card.json` — a 1.0 client will not find this endpoint. Reaching 1.0 means
`io.github.a2asdk` 1.0.0 leaving Alpha, or hand-writing the binding, which the reuse ruling already
rejected. Not work this repo can do.

Check: `grep -rn 'embabel-agent-starter-a2a' prod/*/pom.xml` returns the one module, and
`prod/embabel-smith-runtime-a2a/README.md` carries the `Non-goal: protocol 1.0` line against the
discovery-path deviation.

## 3. `/scale` is a decision, not a task

`ScalePane.vue` still renders `PocOnlyPane.vue`. What POC 12 and POC 23 present there is a measurement,
not a capability a user invokes, so wiring the pane means inventing a feature to hang under the name —
which is the failure mode the backlog entry warns against. It stays a stub until someone says what the
pane should do.

Check: `grep -rln PocOnlyPane prod/embabel-smith-studio/frontend/src` prints `panes/ScalePane.vue` only.

## 4. Whether the product should ever print a third-party install hint

`SkillspectorSecurityScanner` tells users to `uv tool install` from an NVIDIA release URL; the other
binary-backed scanners carry their own (`gitleaks`, Bandit, Semgrep). On the default path none of those
strings is reachable — the builder requests the binary-free checks only — and `--full-inspection` is the
single route that can surface one. The four tools are already sanctioned as-is, so what is left is a
supply-chain judgement about the *hint text*, which belongs to the human.

Check: `grep -n 'BINARY_FREE_CHECKS = '
prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillInspector.java` and the
`fullInspection` parameter on `SkillInspector.inspect`.

---

## Decided, so nobody reopens them

- **Sidecars are never spawned from the browser.** Reported, never started: a browser-reachable tool
  that launches host processes is a remote-code-execution surface.
- **POC 27 ships its `not proven` verdict as its result.** Its task 10.2 was never an endpoint blocker —
  the candidate prompt never contained the eval input, so six "different" tasks sent six identical
  requests.
- **`publish-keeps-versions` 3.2 is withdrawn** by its own design decision 7 (keep the `--overwrite`
  flag), and `builder-live-example-tests` 3.1 is struck through. Two of the unchecked boxes in
  `openspec/changes/` are therefore not pending work.

## Cleared while this page was being written (2026-11-22)

- **`docs/08-builder.md` is no longer held.** It carried 19 uncommitted lines — the `### As a native
  binary` section — from the session doing the native-image work, which is what kept
  `builder-executes-its-own-packs` 5.2 and `poc27-improves-builder-agentspecs` 9.2's third clause open.
  That work landed as `a4200e2` and reached this branch through the merge of `origin/poc/iteration-2`, so
  `grep -c 'As a native binary' docs/08-builder.md` prints 1. The two tasks are ordinary work now, not
  blocked: 9.2's third clause (add the builder packs to that page) is open to whoever takes it, and 5.2's
  own note already says there is no new default to write.
- **The native reflection entry for `ToolGroupMetadata$Companion` exists** — `builder-native-hints`
  registers it with `allDeclaredMethods`, and its comment names the exact startup failure the note
  predicted: the factory's JVM name contains a hyphen, so no source line names it and a tracing run
  alone would come back empty.
- **The studio frontend typechecks.** `npx vue-tsc --noEmit` exits 0 with no output at this HEAD, so the
  two `compose.model.test.ts` errors that gated POC 27's task 10.1 are gone, and the frontend half of
  `builder-owns-improvement-loop` 6.2 describes shipped code.
- **`builder-owns-improvement-loop` has no unchecked task left**, including 6.1 and 6.2, both settled by
  command rather than claim.
