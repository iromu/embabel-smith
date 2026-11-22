# Design

## Context

Publishing is a two-destination act already, and the two destinations behave differently. `Publisher` has
one method — `publish(Publishable unit, boolean overwrite, boolean push)` — with two implementations
selected by `PublishTarget.parse`: `FolderPublisher` writes a subdirectory under a folder and reports that
directory as the location; `GitPublisher` clones the target repository into a temporary work dir, writes
the artifact as a subdirectory there, `git add -A`, commits when something is staged, pushes only with
`--push`, and reports `git:<url>#<name>` precisely because the clone is deleted by `close()`. The studio's
`StudioConfiguration.publisherFor` and the builder's `SkillBuilderService.publisherFor` both make the same
choice from the same URI grammar.

Where they meet the loop, `LoopPublishPolicy.publish` forces `overwrite = true` for both. Against
`FolderPublisher` that is destructive: `checkCollision` passes, `deleteTree(unitDir)` removes the previous
artifact, and the new one is written in its place. Against `GitPublisher` it is not destructive of
anything durable — the delete happens inside a throwaway clone, and the previous publish is a commit in
the repository the clone came from.

The database is the third destination and the least equipped. `PackEntity` maps the `packs` table with
`@Id @Column(name = "name") private String name`, alongside `goal`, `steps`, `saved_at` and `content`. The
pack name is the primary key, so the table's shape forbids two versions of one pack: the second save is an
update of the same row, and the first content is gone. Note that the payload is already there — `content`
holds the pack — so what is missing is the key, not the storage.

## Goals / Non-Goals

**Goals**

- A republish by the loop keeps the previous artifact readable, in the database and in history.
- The loop's configured destination is a durable one, not the system temporary directory.
- `docs/06-spec-sources.md` describes what the code does, including what the loop does.

**Non-Goals**

- Removing `--overwrite` from `smith build`. An operator who names a folder and asks to replace it means
  it.
- A second versioning mechanism on top of git.
- Making the loop push by default — `loop-publish-push` settled that, and this change does not reopen it.
- Versioning `TraceEntity` or `DiscoveryEntity`. Only the published artifact is in scope.

## Decisions

**1. Fix the loop and the store; leave the CLI flag alone.**

The flag is not the bug. `CollisionException`'s message — "already exists at `<path>`; pass `--overwrite`
to replace it" — is a human being asked to confirm a replacement, which is a reasonable thing to ask a
human. The loop is not a human, and it asks on every iteration. So `LoopPublishPolicy` stops forcing the
flag and the store grows enough shape to hold both versions; `Publisher` and both implementations keep
their signatures and semantics. **The builder was expected to be untouched by this change; decision 2's
finding makes that conditional** — if the loop is to publish at an incremented version, the increment
belongs in the builder, because the repo's own rule keeps core logic out of the studio.

**2. The versioned copy lives in `packs`, and the name stops being the primary key.**

`content` already persists the pack, so the table is one column and one key away from holding versions
rather than a single current row. The natural key is `(name, version)`, with the version carried by the
artifact rather than invented at the boundary: an agent-spec pack already declares a version in its
manifest, so the version published *is* the version stored, and a republish of the same manifest version
is an update of that version rather than a third thing. Rejected: a synthetic monotonic counter, which
would put a number in the database that means nothing anywhere else in the system and would disagree with
the manifest the artifact carries.

**Verified 2026-11-19 while settling task 1.1 — and it breaks the key for the one caller that matters.**
The three `Publishable` implementations do not all carry a version:

- `AgentSpecPublishable` wraps an `AgentSpec`, which answers `version()` at
  `smith-common/.../model/AgentSpec.java:50` by delegating to `AgentSpecManifest.version` — a `String`,
  and **`null` when the manifest is absent**.
- `ProcessPublishable` wraps a `ProcessSpec` with the identical shape (`ProcessSpec.java:76`, delegating
  to `ProcessSpecManifest.version`), same `null` when the manifest is absent.
- `SkillPublishable` wraps `builder/skill/Skill.java:23`, which is
  `record Skill(String name, String description, String body)` — **no version field at all**, and
  `SkillMdValidator` never mentions one. A SKILL.md *can* carry `metadata.version`, and the evaluator's
  `VersionValidator` reads exactly that key (`VersionValidator.java:100`) while deliberately ignoring a
  legacy top-level `version` and treating the label as optional — but the builder never parses it, so no
  skill this repo builds declares a version today.

**What a publishable with no declared version stores: `1.0.0`.** Refusing it is the wrong answer, because
it would break a shipped feature — `smith build --target skill` publishes today and its artifact can never
declare a version, so a refusal means skills cannot be published at all. `1.0.0` says plainly "this is the
first and only version the artifact claims", and the consequence is honest rather than hidden: a skill
republished without ever declaring a version updates that same row, because nothing in the artifact
distinguishes the two — the caller that the ruling was actually about, the loop, publishes agent-spec packs
and does have a version to bump. Same applies to a pack whose manifest is absent, which the validator does
**not** reject: `AgentSpecManifest`'s javadoc claims a version is required, and
`smith-common/src/main/java/com/embabel/smith/common/validation/` never checks it, so the `null` case is
reachable at publish time rather than theoretical. Reading `metadata.version` from the SKILL.md text at
publish time was considered and left out of scope — it would make the store's key depend on parsing a
document the builder's own model does not represent, which is a bigger change than the one asked for.

**And the loop's candidate inherits the version it is improving.** Two classes pass the manifest through,
and only one of them is the loop — a detail this page first got wrong:

- The **loop** refines through `studio/improve/Refiner.java:63`,
  `new AgentSpec(current.manifest(), current.goal(), steps, current.types())`, reached from
  `ImprovementLoop.java:206`; that loop constructs its own `Refiner` at line 115.
- `builder/eval/RefinementLoop.java:113` has the identical pass-through and belongs to the **build** path
  (`BuilderService.refine`, `EvalController`) — the `--max-refine` route, which never publishes. So the candidate and the baseline share *both* key
columns, and a `(name, version)` store answers the loop's second publish with an update of the same row:
**the previous version is still lost, by the mechanism introduced to save it.** This is not a corner case.
It is the change's stated goal failing quietly, and it is invisible to any test that only checks that two
*different* versions coexist.

Two ways out, and the change has to pick one in the open rather than discover it at review:

1. **The loop publishes the candidate at an incremented version** — one patch bump, applied where the
   improvement becomes an artifact, not inside `RefinementLoop` (which also serves `--max-refine` builds
   that never publish and must keep the input's identity). Then `(name, version)` works as designed and
   "highest version" means what decision 3 assumes it means.
2. **A second discriminator on the row** — a publish sequence or the existing `saved_at` — which abandons
   decision 2's premise that the version comes from the artifact, and puts a column in the schema that no
   manifest agrees with.

Option 1 is **ruled, 2026-11-19** — the loop publishes the candidate at an incremented version. It keeps
the artifact's own declaration authoritative, which is the whole argument for this key. It also states what
an improvement *is*: a rewritten prompt is a new version of a pack, which is the claim the loop has been
making with its `overwrite` flag all along without ever saying so. The increment happens **builder-side**,
where the improvement becomes an artifact — not inside `RefinementLoop`, which also serves `--max-refine`
builds that never publish and must keep the input's identity untouched.

**3. A name resolves to its highest version. Ruled.**

Once `packs` can hold several rows per name, every existing reader that asks for a pack by name has to
mean one of them. Ruled on 2026-11-19: **the highest version**. Two alternatives were on the table and
both were declined, and the reasons are worth keeping because they are the constraints a later change
would trip on. Resolving to the highest *non-retired* version would make the storage layer depend on
lifecycle state, which today is a gate the runtime applies when it runs a pack, not a property the studio
consults when it lists one — that is a real coupling and it should be a decision of its own. An explicit
per-pack `current` pointer would enable promote and rollback, but nothing has asked for either, and a
column nobody writes is exactly the "flexibility that fails later" this repo's working rules name.

The reason the simple rule is safe here rather than merely cheap: the loop publishes a candidate only when
it beat the baseline, so for loop-published packs newest-is-best holds by construction, and if that
construction ever stops holding, the fix is the pointer decision with evidence for it rather than a
placeholder column now. Verify the rule is stated once in `docs/06-spec-sources.md` and that the spec
delta has one scenario for it, not a menu.

**4. The loop's default destination becomes a durable one; the temporary-directory fallback goes.**

`StudioConfiguration.loopPublisher` currently falls back to `FolderPublisher` rooted at
`System.getProperty("java.io.tmpdir")`. That is the worst of both worlds for an improving loop: a folder
publisher, so overwrite deletes, in a directory the operating system is free to discard. It is also the
path a user gets by configuring nothing. The fallback should be the configured improve source — which is
what the `loopPublisher` bean already prefers when the source is configured — and where no source is
configured the loop should refuse to publish and say why, the same way `canPublish()` already refuses a
git source with pushing off. Silently publishing improvements into `/tmp` and then deleting them is worse
than not publishing.

**5. Do not build versioning on top of the git path — it already has it.**

For a `git:` improve source, two publishes of one name are two commits, and `overwrite` deletes only the
working-tree copy inside the clone. Adding a version column's worth of bookkeeping there would duplicate
what the repository already does better. So the git path keeps its current behaviour, and the database
copy is what makes the artifact addressable without cloning. The two mechanisms are named in the spec
delta rather than unified behind an abstraction, because they answer different questions — history versus
lookup.

**6. `canPublish()` stays, and the interaction is stated rather than smoothed over.**

`LoopPublishPolicy.canPublish()` returns `!gitSource || push`, and its exception text explains that a git
commit without a push would live only in the temporary clone. Choosing git as the loop's durable
destination therefore makes pushing a precondition rather than a preference, and that is the same trade
`loop-publish-push` already put in front of the human and settled as refuse-plus-opt-in. This change does
not soften that message; if anything, making the durable path the default makes it fire more often, and
the message is already specific enough to act on.

**7. The store and the publish never meet, and that is what decides task 2.4.**

Written 2026-11-19 after counting the readers for task 1.3 rather than recalling them, because the search
turned up a smaller and more awkward surface than this page assumed.

*What is actually there.* `storage/entity/PackEntity.java` is `@Table(name = "packs")` with
`@Id @Column(name = "name") private String name` — the name really is the primary key, as decision 2
assumed. Its payload is `goal`, `steps` (an `int`), `saved_at` (an `Instant`) and `content`, declared
`@JdbcTypeCode(SqlTypes.LONGVARCHAR)` and deliberately **not** `@Lob`: the class javadoc records that a
CLOB maps to Postgres' `oid` large-object type, and the portable types are what let one model drive both
the H2 test profile and the Postgres development database. A new key column has to stay portable for the
same reason. `storage/repository/PackRepository.java` declares **no methods at all** —
`interface PackRepository extends JpaRepository<PackEntity, String>` — so there is no `@Query`, no JPQL, no
native SQL and no derived finder for this table anywhere in the repo. Every reader and writer is in one
class, `store/ProcessStore.java`: `savePack` → `packs().save(e)` at line 60 (the only writer), `listPacks` →
`findAll()` at line 74, and `loadPack` → `findById(name)` at line 89 (the only name-keyed read). Its callers
are `rest/StoreController` and `rest/DiscoverController`; the bean is wired at
`config/StudioConfiguration.java:253`. No other module touches the table.

*What is not there.* **The publish path never writes `packs`, and neither does the loop.**
`LoopPublishPolicy.publish` ends at `publisher.publish(candidate.candidate(),
candidate.proposedLifecycle(), overwrite, this.push)` — a `Publisher`, which is `FolderPublisher` or
`GitPublisher`. `FolderPublisher.publish` resolves `Path.of(this.target.location()).resolve(unit.name())`:
the directory is named by `Publishable.name()` alone, **with no version in the path**, and `Publishable`
has no version accessor to add one (`publish/Publishable.java` declares `name()`, `baselinePassRate()` and
`writeTo()`). `ProcessStore.savePack` is reached only from the studio's REST save endpoints — a human action,
not a publish.

Two consequences, and both invalidate a section of this change as first written:

1. **Re-keying `packs` cannot stop the loop losing a version.** The destroy-on-republish the ruling is
   about is `FolderPublisher` calling `deleteTree` on `<destination>/<name>`. The table is nowhere in that
   path. Section 2 is necessary for the ruling's database half and does nothing at all for its loop half.
2. **Bumping the manifest version does not stop the collision either.** With `overwrite = false` and the
   directory still named `<name>`, `checkCollision` throws on the loop's second publish — precisely the
   failure mode task 3.2 warns about, produced by the change intended to prevent it. Making the folder hold
   versions means putting a version in the directory name, which changes what the one-level-deep discovery
   in `docs/06-spec-sources.md` reads, and renames every artifact the studio can currently find.

*Chosen: durability moves to the store; the loop's publish keeps its flag.* After a publish succeeds, the
loop saves the candidate through the studio's existing store path, and `packs` — keyed by `(name, version)`
per decision 2, with the candidate at its incremented version per decision 1 — holds every version the loop
ever approved. The folder destination keeps being replaced, and `LoopPublishPolicy`'s forced `overwrite`
stays, because durability no longer depends on it: the previous version survives in a row addressed by
version, and on a `git:` destination it also survives as a commit (decision 5).

This is the cheapest of the three shapes that could satisfy the ruling, and it is cheap for a specific
reason: `ProcessStore.savePack(AgentSpec)` already takes the whole pack and can read `pack.version()` from
it, so the version needs no new plumbing and no new dependency — the builder gains nothing, and the studio
records a version beside a save it already knows how to make. The alternatives were: version the folder
path (most invasive: it renames published artifacts and reopens the discovery contract), or lean on git
alone (honours the ruling's second half and leaves its database half unimplemented for the loop).

**What this withdraws, stated plainly:** task 3.2's flag flip is withdrawn — it would make every loop
publish after the first throw a collision, and with the store holding the versions it buys nothing. The
increment moves to the loop's candidate before it is published, which is the studio (`improve/Refiner` or
`ImprovementLoop`), and the builder's `eval/RefinementLoop` must **not** bump: it serves `--max-refine`
builds that never publish and must return the input's identity. That placement is what decision 1's
"builder-side" wording assumed and the search disproved; the repo's `studio is a UI utility` rule now
conflicts with where the loop's refinement actually lives, and the versioning follows the loop. `docs/12`'s
open item about the forced `overwrite` is re-worded to say what the loop does now rather than closed as an
absence, because its `Check:` command greps for a string that this design keeps on purpose.

## Risks / Trade-offs

**There is no migration mechanism, and this one is load-bearing.** `StorageBinding` configures the studio's
persistence with `hibernate.hbm2ddl.auto = "update"` — verified: that is the only schema setting in the
repo, and there is no Flyway, no Liquibase, and no `.sql` file anywhere. `hbm2ddl update` adds tables and
columns; it does not drop or re-key a primary key. So changing `PackEntity`'s key does **not** migrate an
existing `packs` table — an already-deployed studio would come up with a table still keyed by `name`
underneath an entity that expects `(name, version)`, which fails at runtime rather than at compile time,
in exactly the deployment where the rows matter most. Section 2 therefore cannot assume a tool will do
this: it has to say who moves the table, and task 2.2 is that decision rather than a formality. The rows
are worth saving precisely because `content` may be the only surviving copy of artifacts whose folder was
already overwritten by a loop pass.

**The versioned write crosses the builder/studio line.** Nothing in the publish path touches the database
today — `Publisher` writes folders and commits, and the only persistence is `ProcessStore.savePack`
studio-side. The repo's own rule is that the studio is a UI utility and core logic belongs in the builder,
and the builder has no JPA dependency. So "keep a versioned copy in the database" forces a choice the
change must make explicitly rather than by accident: give the builder a persistence dependency, or have
the studio record the version it observes being published. The second keeps the rule and puts the write
next to the reader; it is the shape task 2.4 takes.

**`CollisionException` is unchecked and the loop does not catch it.** It extends `RuntimeException`, so it
escapes the `throws IOException` signatures along the path. `ImprovementLoop.publishCandidate` catches only
`IOException`, and `LoopService.approve` has branches for `LoopPushDisabledException` and
`IllegalArgumentException` but none for a collision. That means task 3.2 cannot simply stop forcing the
flag: until the loop has a versioned write path, every loop publish would throw, improvements would stop
landing, and the queue would keep holding them without an error code. Section 3 is ordered so the store
can hold a version before the loop stops replacing one.

**Two destinations can disagree.** A publish that reaches the database but not git, or the reverse, leaves
a version with no history and a commit with no row. Ordering matters and the tasks make the database write
the one a reader can see, so a partial failure leaves an artifact that exists but is not yet browseable
rather than one that is browseable and missing.

**`docs/06` forbids versioned subdirectories, and this design honours that.** Its
`## Discovery is one level deep, deliberately` section is explicit that a pack is an immediate
subdirectory and deeper nesting is not a v1 feature. So versions live in the store, not as
`pack/name/v2/` folders inside a source — the folder keeps holding exactly one current directory per
name, and the history is the database row plus, for a git source, the commits.

**The git publisher is a singleton with a lifetime it was not designed for.**
`StudioConfiguration.loopPublisher` is a Spring singleton and `GitPublisher` clones in its constructor,
with `close()` deleting the clone — and nothing closes a singleton. So a git improve source today means
one clone taken at bean creation, reused for the process lifetime, never refreshed. This change does not
fix that, but any change to the loop's default destination has to avoid making it worse: a publisher that
publishes into a stale clone silently commits against an outdated tree.
