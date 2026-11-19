# loop-publish-push

Stop the self-improvement loop from pushing to the git remote by default. `ImprovementLoop` and
`LoopService` call the publisher with `push` hard-coded to `true`, which contradicts the product
invariant "publishing commits; it never pushes". Make pushing a configured opt-in that defaults to
off, and — so that switching it off never silently discards an approved improvement — refuse the
publish for a `git:` improve source while pushing is off, instead of committing into the publisher's
throwaway clone that `close()` deletes.
