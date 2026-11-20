# Design

## Context

See `proposal.md` for motivation; `specs/loop-publish-policy/spec.md` for the contract.

Facts this design has to work with, each read from the tree:

- The publisher already parameterises pushing. `Publisher.publish(Publishable unit, boolean overwrite,
  boolean push)`; the agent-spec convenience is `publish(AgentSpec pack, LifecycleMetadata lifecycle,
  boolean overwrite, boolean push)`. `PublishResult` is `record PublishResult(<location>, boolean
  pushed)` — the location's type is in flight (see the note below), which this change does not depend on;
  only `pushed` matters here. Nothing in the builder needs to change.
- `GitPublisher` clones into `Files.createTempDirectory("smith-git-publish-")`, commits there, and
  `close()` calls `deleteTree(this.workDir)`. Without `git push origin HEAD` the commit exists only
  inside that directory. That is the whole reason the loop forces `push = true`.
- `FolderPublisher.publish(Publishable, boolean overwrite, boolean push)` takes `push` and ignores it —
  the flag is documented as "(git only) … ignored by the folder publisher".
- `ApprovalQueue.approve` "Publishes first and removes only on success, so a failed publish leaves the
  candidate pending (recoverable) rather than silently losing it." A throw from the publisher therefore
  already preserves the candidate.
- `LoopService.approve` maps outcomes onto `ApproveResponse(true, location, null)` /
  `ApproveResponse(false, null, message)`, so there is an existing channel for a refusal message.
- The studio's configuration idiom is `@Value("${smith.studio.<name>:<default>}")` on a bean method in
  `StudioConfiguration`; `improvementLoop(...)` already carries
  `@Value("${smith.studio.loop.auto-publish:false}") boolean autoPublish`.
- `ImprovementLoop` is plain Java with injected collaborators and is unit-tested offline
  (`ImprovementLoopTest`, `LoopServiceTest`), which `docs/10-self-improvement.md` attributes to exactly
  this shape.

**Concurrent change, noted 2026-11-18.** Another change (`builder-publish-location`) is editing
`PublishResult` in the same working tree, replacing the `location` component's type with a `String` so a
`git:` publish stops naming a directory inside the work tree `close()` deletes, and adjusting
`LoopService.approve` to match. The two push call sites this change targets are **unchanged** by it —
`ImprovementLoop.java:291` and `studio/loop/LoopService.java:188` both still read
`publish(candidate.candidate(), candidate.proposedLifecycle(), true, true)`, re-verified after that edit
landed. The overlap is cosmetic (`result.location().toString()` in `approve`) and the two changes touch
disjoint lines; whoever implements this one should re-read `approve` first and expect a `String` location.

## Goals / Non-Goals

**Goals**

- An unattended loop cannot write to a shared remote unless someone asked for it.
- The default configuration matches the documented invariant, so a fresh checkout behaves as `docs/05`
  and `docs/06` say it does.
- Turning pushing off never turns a published improvement into a silently discarded one.
- A `folder:` loop keeps working exactly as it does today, with no new step.

**Non-Goals**

- Not changing the loop's forced `overwrite = true`. Same call site, different decision, and folding
  it in would make an unrelated behaviour change ride along on a security-posture fix.
- Not adding per-candidate or per-source push control, no UI toggle for pushing, no "dry-run publish".
  The backlog asked for one gate; one setting is the whole requirement.
- Not making the loop push by other means (a long-lived clone, a work tree, a PR). That is a larger
  publishing redesign, not a policy fix.
- Not touching the manual build path, which already honours `--push`.

## Decisions

**1. A property, not a code change either way.**
The loop's push becomes `smith.studio.loop.push`, default `false`.
*Alternative — keep forcing push and amend the docs to say the loop is exempt.* Rejected: it keeps the
worst property of the status quo, which is that the only person who can stop a write to the shared
remote is whoever edits Java. The docs already flag the deviation as needing a decision rather than a
blessing.
*Alternative — remove pushing from the loop entirely.* Rejected: `docs/10` records a real constraint —
for a `git:` source the commit dies with the publisher's throwaway clone — so an operator who has
deliberately pointed the loop at a git source and accepts pushing has no way to say so, and the loop
becomes useless for that source.

**2. Default off, because the invariant is the default.**
The default is the state that matches the stated rule. An operator opts *into* shared-state writes,
which is the direction `--push` already goes for a manual build.

**3. Refuse, rather than commit-into-a-deleted-clone, when the source is git and pushing is off.**
The setting alone would create a new silent failure: publish "succeeds", returns a location inside a
work tree, and `close()` deletes it. So when the improve source is a `git:` source and pushing is off,
the publish is not attempted; the operator is told the setting name and the candidate stays pending.
*Alternative — publish anyway and report `pushed: false`.* Rejected: `PublishResult.pushed == false`
reads as "committed locally, not yet pushed", which for this publisher means "committed nowhere". The
report would be true and useless.
*Alternative — keep the clone alive so the commit survives.* Rejected: that invents a durable publish
state machine to serve a configuration combination that should simply be refused.
**How the git-ness is known.** `StudioConfiguration.publisherFor` already branches on it:
`PublishTarget target = PublishTarget.parse(uri);` then `switch (target.type()) { case FOLDER -> …;
case GIT -> …; }`. Reuse that — pass the loop the configured improve-source URI (the same
`smith.studio.improve-source` / first-of-`smith.studio.sources` value `loopPublisher` resolves) and let
the loop ask `PublishTarget.parse(...)`. Rejected: testing `publisher instanceof GitPublisher`, which
couples loop policy to a bean type the loop has no reason to know.

**4. Thread it like `auto-publish`.**
A constructor parameter on `ImprovementLoop` supplied by `StudioConfiguration` keeps the loop
constructor-injected and offline-testable, per the shape `docs/10` documents. Unlike `auto-publish` it
is *not* runtime-toggleable: pushing is the credential-and-intent decision, and making it switchable
mid-session from the UI would reintroduce the unattended write this change removes.

## Risks / Trade-offs

- **An operator running the loop against a `git:` source loses the ability to auto-publish until they
  set the flag.** Deliberate, and it fails loudly with a named setting rather than quietly.
- **A `git:` loop with pushing off can never empty its approval queue** — every approve is refused, so
  candidates accumulate. Accepted: an accumulating queue is visible, a vanished improvement is not. It
  also matches the queue's existing behaviour under a publish failure.
- **The two `true` literals sit next to each other, so a future reader may flip the wrong one.**
  Mitigated by naming the value at the call site rather than passing a bare boolean.
- **The forced `overwrite = true` remains**, so the loop still overwrites a same-named published unit
  without the documented `--overwrite` intent. Left open on purpose and reported upward rather than
  silently changed.
