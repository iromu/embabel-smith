# Proposal

## Why

`docs/05-architecture.md` states the invariant under Cross-cutting invariants: **"Publishing commits;
it never pushes. Pushing is a shared-state action needing credentials and intent, so it takes an
explicit `--push`."** `docs/06-spec-sources.md` repeats it in the publishing table: "**Git publish |
commit, never push by default; `--push` is opt-in**".

The improvement loop breaks it, unconditionally, at two independent call sites:
`prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/improve/ImprovementLoop.java:291`,
inside `publishCandidate`, and
`prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/loop/LoopService.java:188`, inside its
private `publish`. Both read
`return this.publisher.publish(candidate.candidate(), candidate.proposedLifecycle(), true, true);`, and
per `Publisher.publish(Publishable, boolean overwrite, boolean push)` the trailing literal is `push`.
They are not redundant: the `ImprovementLoop` site is the loop's own auto-publish route, the
`LoopService` site is the candidates an operator approves through `LoopService.approve` plus the
iteration-level auto-publish. Fixing one and not the other would still push on the human-triggered path.
`docs/10-self-improvement.md` records the deviation and asks for exactly this decision: "an unattended
loop writes to the git remote by default. That is a deliberate deviation from the product rule — treat it
as a policy decision to revisit, not an oversight to silently fix."

So today a scheduled loop with `auto-publish` on — or an operator clicking Approve — writes to the
shared remote with no credential check, no intent and no way to turn it off. This change takes the
decision the docs ask for: the invariant wins, and pushing becomes configuration.

## What Changes

- **`smith.studio.loop.push`, default `false`.** A new property threaded into `ImprovementLoop` and
  `LoopService` exactly the way `smith.studio.loop.auto-publish` already is — a constructor parameter
  supplied by `StudioConfiguration` with `@Value("${smith.studio.loop.push:false}")`. The two hard-coded
  `true` literals become that value.
- **A `git:` improve source with pushing off does not publish.** `GitPublisher` commits into a
  temporary clone (`Files.createTempDirectory("smith-git-publish-")`) that `close()` deletes, which is
  the only reason the loop forces pushing at all. So turning push off without more would commit an
  approved improvement into a directory that is then removed — the work would vanish silently, which is
  worse than the status quo. The publish is therefore refused with a message naming the setting, and
  the candidate stays pending.
- **`folder:` sources are unaffected.** `FolderPublisher` ignores the flag; a local write is already
  persistent, and that is the loop's common case today.
- **The refusal is visible.** Approving a candidate that cannot be published returns a failure naming
  `smith.studio.loop.push`, and the candidate remains in the approval queue — the same recoverable
  behaviour `ApprovalQueue.approve` already implements ("Publishes first and removes only on success,
  so a failed publish leaves the candidate pending (recoverable) rather than silently losing it").
- **The loop's forced `overwrite` is left alone.** The same call also passes `overwrite = true`, which
  equally deviates from the documented collision rule ("refused unless `--overwrite` is passed"). It is
  a separate decision and is deliberately not folded in here; see the question raised in the change
  discussion.

## Capabilities

### New Capabilities

- `loop-publish-policy`: how the self-improvement loop writes an approved candidate back to its spec
  source — whether the write reaches a shared remote, which setting decides that, what happens to a
  candidate when the write cannot be made durable, and the guarantee that no loop configuration can
  lose an approved improvement without saying so.

### Modified Capabilities

None. There are no capability specs under `openspec/specs/` — they were consolidated into `docs/` on
2026-11-15 — so there is no existing requirement text to modify. The durable home for the rule is
`docs/10-self-improvement.md`, whose paragraph "Unlike a manual build, the loop publishes with
`push = true`" is replaced; `docs/12-backlog.md` loses the open Loop item. The invariant pages
themselves (`docs/05`, `docs/06`) need no edit — after this change the loop simply obeys them.

## Impact

- **`prod/embabel-smith-studio`**: `improve/ImprovementLoop` gains a `push` field on both constructors
  and uses it at line 291 in `publishCandidate`; `loop/LoopService` uses it at line 188 in its private
  `publish`; `config/StudioConfiguration` supplies it in the `improvementLoop` bean alongside the existing
  `auto-publish` parameter.
- **No change to the builder's publishers.** `Publisher`, `GitPublisher` and `FolderPublisher` already
  take `push` as a parameter and already report it back on `PublishResult.pushed`. The deviation lives
  entirely in the two loop call sites.
- **Behaviour change for anyone running the loop against a `git:` source**: an approve or an
  auto-publish iteration that used to land on the remote now reports that it did not, until the
  operator sets `smith.studio.loop.push=true`. This is the intended direction of the change and is the
  reason the refusal is explicit rather than quiet.
- **Tests**: `ImprovementLoopTest` and `LoopServiceTest` construct the loop with fakes today, so the new
  parameter and the refusal are covered offline; no endpoint is needed.
- **Docs**: `docs/10-self-improvement.md` (the deviation paragraph becomes the rule), `docs/09-studio.md`
  (the property), `docs/12-backlog.md` (the Loop item closes).
