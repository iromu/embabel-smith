# Proposal

## Why

`docs/06-spec-sources.md` states that republishing a name is refused unless `--overwrite`. The loop does
not ask: `LoopPublishPolicy.publish` sets `boolean overwrite = true` on the line before it calls the
publisher, under the comment "overwrite stays forced as it always was; only push is now a configured
value". So the documented rule and the loop's behaviour disagree, and the disagreement is not cosmetic —
`FolderPublisher.publish` answers an overwrite with `deleteTree(unitDir)` before writing the new one. The
previous artifact is gone from disk.

That matters specifically because of who is doing the overwriting. The self-improvement loop publishes a
candidate that beat a baseline, then the next iteration's baseline is what is on disk. Forcing overwrite
means the loop deletes the comparison point it just used, on every improvement, and `docs/12-backlog.md`
records that the trace and the published artifact are the loop's only evidence. The refusal rule exists to
protect exactly that, and the loop is the one caller that never honours it.

Asked which side was the bug, the ruling was that **neither is**: the collision should not happen at all.
A republish keeps a versioned copy in the database, and where publish writes a folder at all, that folder
is git-managed rather than overwritten. Both artifacts survive, one as a row with a version, one in
history — which removes the question the flag was answering instead of choosing a winner.

The repo already knows about this. `docs/12-backlog.md` carries "Open — the loop still forces
`overwrite = true`", with a `Check:` command that greps for the literal, and
`docs/10-self-improvement.md` ends its publish paragraph by deferring to that backlog item.
`LoopPublishPolicy`'s own class javadoc says the forced overwrite "is a separate, still-open decision".
Three documents are pointing at the same hole; this change is the one that closes it.

Why the collision is routine rather than incidental, and so could not be left to a refusal: the loop's
candidate is `refinement.candidate()` — the same pack with rewritten prompts — so it carries the existing
pack's name, and the improve source is the same source the loop reads packs from. The target directory
therefore always already exists. `Publishable.name()` is simultaneously the directory name, the collision
key and the `packs` primary key, which is the single fact this change has to work around.

## What Changes

- **The loop keeps the version it replaced, in the store.** `LoopPublishPolicy` continues to force
  `overwrite = true`, because the folder it publishes into is named by the pack alone
  (`FolderPublisher` resolves `location.resolve(unit.name())`, and `Publishable` has no version accessor),
  so clearing the flag would make the loop's second publish collide on that directory rather than improve
  on it. What changes is where durability lives: the loop stores each candidate it publishes as a distinct
  version, so the version the folder lost is addressable afterwards by name and version. See `design.md`
  decision 7, which rewrote this bullet after the code was searched rather than recalled.
- **The published artifact gets a versioned home in the database.** `PackEntity` today is
  `@Table(name = "packs")` with `@Id @Column(name = "name") private String name` plus `goal`, `steps`,
  `saved_at` and `content` — so the key is the name and a second save of the same pack *replaces* the
  first. The key changes so a name can hold several versions, and the content already stored becomes the
  versioned payload rather than a single overwritten blob.
- **The loop's folder default stops being the default.** `StudioConfiguration.loopPublisher` falls back to
  `new FolderPublisher(PublishTarget.parse("folder:" + <system temp dir>))`. Writing to a temporary
  directory and then overwriting it is the least durable option available and it is the one the loop gets
  when nothing is configured. A git improve source is already first-class — `publisherFor` selects
  `GitPublisher` for a `git:` target — and for a git source the history the ruling asks for already
  exists.
- **`--overwrite` stays for the CLI, with its documented meaning.** This change is about the loop and the
  store, not about removing the flag from `smith build`.

## Impact

- Affected specs: `publish-versioning` (new).
- Affected code: `prod/embabel-smith-studio` — `storage/entity/PackEntity` and its key,
  `storage/repository/PackRepository` (which declares no methods today, so the composite key needs a key
  type as well as a column), `store/ProcessStore` (the only reader and writer of the table: `savePack` at
  line 60, `listPacks` at 74, `loadPack` at 89), `improve/Refiner` or `improve/ImprovementLoop` for the
  version increment, and `config/StudioConfiguration` for the destination.
  `improve/LoopPublishPolicy` keeps its forced `overwrite` and needs only its javadoc, which currently calls
  the forced overwrite "a separate, still-open decision". `prod/embabel-smith-builder`'s publish package
  needs **no** change: `Publisher.publish(Publishable, overwrite, push)` keeps its signature, both
  publishers keep their semantics, and the builder gains no database dependency.
- Affected docs: `docs/06-spec-sources.md` (the publish rules page), `docs/07-runtime.md` or
  `docs/08`-adjacent pages only if they restate the overwrite rule, `docs/12-backlog.md`.
- Risk carried by this change: the versioned key is a schema change against a table the studio browses by
  name, so every reader of `packs` has to state which version it wants. That is the work, and it is the
  reason this is not a flag flip.
- Interaction to respect: `LoopPublishPolicy.canPublish()` already refuses to publish to a git source when
  pushing is disabled, because the commit would live only in `GitPublisher`'s temporary clone and be
  deleted with it. Any change to the loop's default source has to keep that refusal honest.
