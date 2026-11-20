# Tasks

## 1. Make pushing a configured value

- [x] 1.1 Add a `push` field to `ImprovementLoop`
      (`prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/improve/ImprovementLoop.java`),
      on both constructors, beside the existing `autoPublish`, and use it in `publishCandidate` at line
      291 in place of the trailing `true` literal, keeping the `overwrite` literal unchanged. Verify with
      `make test-module MODULE=prod/embabel-smith-studio` that the existing `ImprovementLoopTest` still
      passes after its constructor calls gain the argument.
      *(Done as a `LoopPublishPolicy` parameter rather than a bare `push` boolean, so `LoopService`
      applies the same rule without duplicating it. `make test-module
      MODULE=prod/embabel-smith-studio`: 125 tests, 0 failures.)*
- [x] 1.2 Add the same value to `LoopService`
      (`prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/loop/LoopService.java` — the
      `studio/loop/` package, not `studio/improve/`) and use it in its `publish` method at line 188 in
      place of the trailing `true`. That line is a second, independent call site: it publishes the
      candidates approved through `LoopService.approve` and the auto-publish path, so leaving it would
      keep pushing for exactly the operator-triggered route. Verify `LoopServiceTest` passes and that
      `grep -rn 'proposedLifecycle(), true, true' prod/embabel-smith-studio/src/main/java/` returns
      nothing. *(Verified: the grep returns nothing, and so does `grep -rn 'push=true'`.)*
- [x] 1.3 Supply it in `StudioConfiguration.improvementLoop` with
      `@Value("${smith.studio.loop.push:false}")`, next to the existing
      `@Value("${smith.studio.loop.auto-publish:false}")` parameter, and pass the improve-source URI the
      `loopPublisher` bean already resolves. Verify the bean method has no other new parameter and that
      the context still starts in the studio's existing context tests.
      *(Deviation, deliberate: the `@Value` and the URI resolution live in one new
      `loopPublishPolicy` bean, injected into `improvementLoop` and into `LoopService`, so the
      improve-source URI is resolved in exactly one place instead of twice. `improvementLoop` gained
      one parameter, the policy, and nothing else. Context tests (`ModelNotConfiguredTest`,
      `StorageContextTest`) boot green.)*
- [x] 1.4 Name the value at both call sites rather than passing a bare boolean (a named local or a
      field read with a one-line comment). Verify by reading the two methods that a reader can tell
      which literal was `push` and which was `overwrite`.
      *(Neither call site passes a boolean any more — both call
      `publishPolicy.publish(publisher, candidate)`; inside the policy `overwrite` is a named local and
      `this.push` is named.)*

## 2. Never lose an approved improvement

- [x] 2.1 When pushing is off and the configured improve source parses to a `git:` target, do not
      attempt the publish: throw a named failure that says the improvement was not written and names
      `smith.studio.loop.push`. Rely on `ApprovalQueue.approve`'s publish-before-remove so the candidate
      stays pending. Verify with an offline `ImprovementLoopTest` case that asserts the publisher fake
      was never called and the candidate is still in `loop.queue().pending()`.
      *(Verified by `aGitSourceWithPushingOffIsNotPublishedAndTheCandidateStaysPending`; proven
      non-vacuous by mutation M3 — `canPublish()` returning `true` made it red.)*
- [x] 2.2 Return the refusal through `LoopService.approve`'s existing failure channel as
      `ApproveResponse(false, null, message)`. Verify with a `LoopServiceTest` case that the response is
      `false`, carries a message naming the setting, and that the candidate is still listed by
      `candidates()`.
      *(Verified by `approvingAGitCandidateWithoutPushingIsRefusedAndStaysPending`.)*
- [x] 2.3 Make the auto-publish path report the same refusal rather than swallowing it, so an
      auto-publish iteration over a `git:` source with pushing off records that it published nothing.
      Verify with a `LoopServiceTest` case on the auto-publish branch that the iteration reports the
      refusal.
      *(Verified by `anAutoPublishIterationReportsThePushRefusalInsteadOfAPublish`: no published specs,
      the spec queued, `errorCode` `PUSH_DISABLED`, error naming the setting.)*
- [x] 2.4 Prove the `folder:` path is unchanged: an offline test that a `folder:` improve source
      publishes with pushing off and returns the publish location. Verify it passes and that it fails if
      the refusal is applied to folder sources too (mutation check).
      *(Verified by `aFolderSourcePublishesWithPushingOff`. Mutation M1
      (`canPublish()` → `this.push`) turned it red:
      `ImprovementLoopTest.aFolderSourcePublishesWithPushingOff:237`.)*
- [x] 2.5 Prove the default is no-push: an offline test that the value reaching the publisher is
      `false` when the property is unset, and `true` only when it is set. Verify both cases, and verify
      the pushing-on case for a `git:` source reaches the publisher with `push = true`.
      *(Verified by `publishDoesNotPushByDefault`, `publishPushesOnlyWhenPushingIsEnabled` and
      `aGitSourceWithPushingEnabledIsPublishedWithPush`. Mutation M2 (push hard-coded back to `true`)
      turned the first red: `ImprovementLoopTest.publishDoesNotPushByDefault:172`.)*

## 3. Record the decision

- [x] 3.1 Replace the "Unlike a manual build, the loop publishes with `push = true`" paragraph in
      `docs/10-self-improvement.md` with the rule as shipped: the loop obeys commit-never-push, pushing
      is `smith.studio.loop.push` and defaults off, and a `git:` source with pushing off is refused
      rather than committed into a deleted clone. Verify the page names a property that exists in
      `StudioConfiguration` and no longer claims the loop pushes by default.
- [x] 3.2 Add the property to the studio configuration notes in `docs/09-studio.md`. Verify the page's
      property name matches the `@Value` key character for character.
      *(New "Loop settings" section; `smith.studio.loop.push` appears identically in the page and in the
      `@Value` key.)*
- [x] 3.3 Mark the Loop item in `docs/12-backlog.md` closed with the resolution, keeping the reasoning
      for the refusal. Verify the item's `Check:` command now reports no hard-coded push at either call
      site.
      *(The item's Check command is now the `proposedLifecycle(), true, true` grep, which returns
      nothing.)*
- [x] 3.4 Record in `docs/12-backlog.md` that the loop still forces `overwrite = true`, so the collision
      rule stays open as its own item. Verify the backlog names the remaining literal.
      *(The new Open item's Check command locates `boolean overwrite = true` in
      `LoopPublishPolicy.java`.)*
