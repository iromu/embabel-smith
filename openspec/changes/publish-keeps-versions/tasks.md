# Tasks

Order matters: section 1 decides the two things section 3 cannot proceed without, and section 2 is the
schema the rest sits on.

## 1. Decisions to settle first

- [x] 1.1 Confirm the version that gets stored is the one the artifact declares. For an agent-spec pack
      that is the manifest version; check what `SkillPublishable` and `ProcessPublishable` carry and say in
      `design.md` decision 2 what happens to a publishable that declares none — refuse it, or store a
      version of `1.0.0`, and pick one rather than letting the code decide by omission. Verify by naming
      the class and field that supplies the version for each of the three `Publishable` implementations.
      Done 2026-11-19, and the answer is not uniform. `AgentSpecPublishable` → `AgentSpec.version()`
      (`smith-common/.../model/AgentSpec.java:50`) → `AgentSpecManifest.version`, a `String` that is
      **`null` when the manifest is absent**. `ProcessPublishable` → `ProcessSpec.version()`
      (`ProcessSpec.java:76`) → `ProcessSpecManifest.version`, same `null` shape.
      `SkillPublishable` → `builder/skill/Skill.java:23`, `record Skill(String name, String description,
      String body)` — **no version field**, and `SkillMdValidator` never mentions one, so no skill this
      repo builds declares a version. Chosen rule: store `1.0.0` for anything that declares none, because
      refusing would make `--target skill` unpublishable; reasoning and the declined alternative in
      decision 2. Also found: `common/validation/` never checks version, so the manifest javadoc's
      "requires a name and a version" is documentation rather than enforcement, and the `null` path is
      reachable at publish time.
- [x] 1.2 **Asked and answered.** Once `packs` holds several rows per name, a reader that asks for a pack
      by name gets **its highest version**. Ruled 2026-11-19; the reasoning and the two declined
      alternatives are in `design.md` decision 3. It carries two obligations the sections below have to
      honour: the rule is stated once, in `docs/06-spec-sources.md` (task 5.1), and every name-keyed
      reader from 1.3 resolves through it rather than inventing its own ordering (task 3.1).
- [x] 1.3 Count the readers before changing the key: every place that queries `packs` by name. Verify the
      list is produced by a search rather than recalled, and that each entry is named in task 3.1.
      Done 2026-11-19 by search, and the list is **three call sites in one class**, not the spread the plan
      expected. `storage/repository/PackRepository.java` declares no methods at all
      (`extends JpaRepository<PackEntity, String>`), so there is no `@Query`, JPQL, native SQL or derived
      finder for this table anywhere in the repo. All access is `store/ProcessStore.java`:
      **`savePack` → `packs().save(e)` at line 60** (the only writer), `listPacks` → `findAll()` at line 74
      (not name-keyed, but a re-key makes one pack list as several entries — decide whether the listing
      collapses to highest-version-per-name or shows each version), and **`loadPack` → `findById(name)` at
      line 89** (the only name-keyed read; with a composite key `findById` cannot take a bare name).
      Callers: `rest/StoreController`, `rest/DiscoverController`; bean wired at
      `config/StudioConfiguration.java:253`. No other module touches the table. The bigger finding — that
      the publish path and the loop never write `packs` at all — is in `design.md` decision 7 and rewrites
      sections 2.4 and 3.
- [x] 1.4 **Blocking decision, found while doing 1.1.** The loop's candidate carries the manifest of the
      pack it improves — `builder/eval/RefinementLoop.java:113` passes `pack.manifest()` straight into the
      new `AgentSpec` — so candidate and baseline agree on *both* `(name, version)` and the store answers
      the loop's second publish with an update of the same row. **A `(name, version)` key therefore does
      not keep both versions for the one caller this change exists for.** Decide between the two ways out
      written in `design.md` decision 2: publish the candidate at an incremented version (recommended;
      belongs in the builder, not inside `RefinementLoop`, which also serves non-publishing `--max-refine`
      builds), or add a second discriminator to the row and give up the premise that the version comes from
      the artifact. Verify by naming which one the change implements, and by making task 3.2's test
      reproduce the collision through the **real refinement path** — two loop passes over one pack — rather
      than two hand-authored versions, because a hand-authored pair passes against the broken design.
      **Asked and answered 2026-11-19: option 1, the version bump.** Ruled by the human on the recommendation,
      with the increment placed builder-side. The implementation obligation is task 3.2a; the real-refinement-path
      test is now part of 3.2 and is not optional.

## 2. The versioned store

- [ ] 2.1 Give `PackEntity` a key that is not the bare name — `(name, version)` per decision 2 — keeping
      `goal`, `steps`, `saved_at` and `content` as the version's payload. Verify a test saves two versions
      of one name and reads both back, and that the assertion would fail against the current
      name-as-primary-key mapping.
- [ ] 2.2 Decide who moves an existing `packs` table, because **nothing will do it for you**: the studio
      sets `hibernate.hbm2ddl.auto = "update"` in `storage/StorageBinding`, and there is no Flyway, no
      Liquibase and no `.sql` file in the repo. `update` adds columns; it does not re-key a primary key, so
      an already-deployed studio would keep a name-keyed table underneath an entity expecting
      `(name, version)` — a runtime failure in exactly the deployment whose rows matter. Choose one: a
      start-up check that detects the old key and rebuilds the table, or a declared re-create that accepts
      losing dev rows. Verify by running the studio's persistence against a database holding the OLD shape
      and asserting table and entity agree afterwards — not by asserting the new mapping alone.
- [ ] 2.3 Make a republish of the same name and same version an update of that version, and a republish at
      a new version an added row. Verify both in one test, and verify the same-version case does not
      duplicate.
- [ ] 2.4 **Decided by `design.md` decision 7: the studio's existing store path writes the versioned row;
      the builder gains no persistence.** The search for 1.3 found that the publish path never touches
      `packs` — `LoopPublishPolicy.publish` ends at a `Publisher` writing to a folder or a git clone — so
      "the studio records the version alongside the publish it observes" is not a smaller alternative to
      builder persistence, it is the only shape available without giving the builder a database. Implement
      it by saving the candidate through the path that already exists, `ProcessStore.savePack(AgentSpec)`,
      which already receives the whole pack and can read `pack.version()` from it. Verify: a builder pom
      check that no JPA, `spring-data` or JDBC dependency was added; a test that a loop publish leaves a row
      whose version is the candidate's, readable afterwards; and that the folder destination's content is
      unchanged by the new write (the save is additive, not a replacement of publishing).
- [ ] 2.5 Decide what `listPacks` means once a name has several rows — `store/ProcessStore.java:74` returns
      `findAll()`, which will list every version. Either collapse to the highest version per name (decision
      3's rule, applied in one place) or show versions as separate rows; pick one and say which the studio
      UI is showing, because the listing is what a human uses to tell whether an improvement landed.

## 3. The loop keeps its versions — in the store, not in the folder name

- [ ] 3.1 Update the one name-keyed reader found in 1.3 — `store/ProcessStore.java:89`,
      `packs().findById(name)` — to resolve a version explicitly using the answer from 1.2, and deal with
      `listPacks` at line 74 (task 2.5). Verify it compiles against the new key and that no reader is left
      relying on the name being unique. There is no second reader: the repository declares no finders, and
      no other module queries the table.
- [ ] 3.2 **Withdrawn by `design.md` decision 7 — do not remove the flag.** The warning below is why, and
      the search found a second reason: the loop's overwrite happens in `FolderPublisher`, which deletes
      `<destination>/<name>`, and `FolderPublisher.publish` resolves the directory from
      `Publishable.name()` with **no version in the path** (`Publishable` has no version accessor). So with
      `overwrite = false` the loop's second publish throws `CollisionException` on the same directory even
      at an incremented manifest version — `CollisionException extends RuntimeException`, so it escapes the
      `throws IOException` signatures; `ImprovementLoop.publishCandidate` catches only `IOException` and
      `LoopService.approve` has no collision branch. That is the exact failure this task was written to
      avoid, produced by the change itself. Durability moves to the store (2.4), so the folder is allowed to
      keep being replaced. Keep the line, and keep its comment saying why.
      ~~Remove the forced `boolean overwrite = true` from `LoopPublishPolicy.publish` and make the loop
      publish a new version instead of replacing.~~
- [ ] 3.2a **Implement the version increment the ruling chose** (1.4), now placed by `design.md` decision 7
      **in the studio, on the loop's candidate, before it is published** — the builder gains no database and
      its `eval/RefinementLoop` must **not** bump. The loop refines in `studio/improve/Refiner.java:63`
      (`new AgentSpec(current.manifest(), current.goal(), steps, current.types())`), reached from
      `ImprovementLoop.java:206`; the builder's `eval/RefinementLoop.java:113` has the same pass-through and
      belongs to the `--max-refine` build path, which never publishes. A loop publish therefore writes the
      candidate at one patch above the baseline it improved, and the store row is keyed on that version.
      Verify: the saved row's version is one patch above the baseline's; a `--max-refine` build with no
      publish still reports the input version (assert the refinement result's manifest equals the input's —
      in **both** refineries, since both pass the manifest through); and the folder's content and the row's
      `content` are the same pack at the same version. The folder path does **not** carry the version (that
      is decision 7's trade-off, because the discovery contract reads one-level-deep names), so do not
      assert a versioned directory. The manifest is a Java record —
      `smith-common/src/main/java/com/embabel/smith/common/model/AgentSpecManifest.java`,
      `record AgentSpecManifest(name, version, defaultPlanner, defaultModel)`, with no `withVersion` — so the
      increment constructs `new AgentSpecManifest(name, bumped, planner, model)` and leaves the baseline
      object intact, because the loop's next baseline is what it reads.
- [ ] 3.3 **Add the assertion that does not exist today, and make it pin the value decision 7 chose.** No
      test in the repo pins the loop's `overwrite`: the loop tests inject a `Publisher` lambda that ignores
      the argument it is handed, so changing the flag breaks nothing either way. After 2.4 lands, add a test
      that a loop publish leaves a versioned row, and verify it by mutating two things in turn — flipping
      `overwrite` to `false` and the save-out to a no-op — watching the tests fail for the right reason.
- [ ] 3.4 Keep `CollisionException`'s message reachable for a human at the CLI: `smith build` without
      `--overwrite` still refuses a same-named folder, and `BuildController`/`ComposeController` still
      surface the refusal. Verify the existing CLI and controller tests pass untouched — that behaviour is
      explicitly not changing — and note that `BuildController` reports a publish collision under stage
      `build` rather than `publish`, which is pre-existing and out of scope unless the human wants it.

## 4. A durable destination instead of a temporary one

- [ ] 4.1 Drop `StudioConfiguration.loopPublisher`'s `folder:` + system-temp-dir fallback: with no improve
      source configured, the loop refuses to publish and names the setting to change, in the voice of the
      existing `LoopPushDisabledException`. Verify a test asserts the refusal and that nothing was written
      under the temp dir.
- [ ] 4.2 Confirm the git path needs no versioning machinery of its own (decision 5): two publishes of one
      name are two commits in the target repository. Verify with a test against a local bare repository
      that the first publish's content is still readable from history after the second one overwrites the
      working copy in the clone.
- [ ] 4.3 Leave `canPublish()` and its message alone (decision 6). Verify its test still passes, and that
      making the durable path the default did not quietly widen when publishing is allowed.

## 5. Documentation

Four pages speak about this rule, and a change that lands without updating all four leaves three of them
describing a behaviour that no longer exists. Each is named with what it currently says.

- [ ] 5.1 `docs/06-spec-sources.md` — rewrite the `## Publishing` table row "Name collision | refused
      unless `--overwrite` is passed" to say what the code now does: the CLI still refuses without the flag,
      and the loop still replaces its folder destination but keeps every published version as a row keyed by
      `(name, version)`. Check whether `## Discovery is one level deep, deliberately` needs a clause
      confirming versions are **not** nested directories — decision 7 keeps the folder named by the pack
      alone, so that section is now the reason the folder cannot hold versions, and the page should say so.
      Verify by reading every rule on the page back out of the classes it names.
- [ ] 5.2 `docs/10-self-improvement.md` — it currently ends its publish paragraph with "The loop's forced
      `overwrite` is a separate decision, still open in `12-backlog.md`". That sentence becomes false the
      moment this change lands. Replace it with what the loop does now: it still replaces the folder, and the
      improvement survives in the store at an incremented version.
- [ ] 5.3 `docs/12-backlog.md` — there is already an item reading "Open — the loop still forces
      `overwrite = true`", with `Check: grep -rn 'boolean overwrite = true' prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/improve/LoopPublishPolicy.java`.
      **Do not close it as an absence: decision 7 keeps that line on purpose.** Rewrite the item in the
      page's `Closed —` form around what actually changed — the loop no longer *depends* on the folder for
      durability — and change its `Check:` to something that is true afterwards (e.g. that the loop's
      publish leaves a versioned row). Leaving the original check pointing at a string that still exists
      would make a closed item look open, and deleting the item would lose the reason the flag survived.
- [ ] 5.4 `docs/05-architecture.md` carries "Name collisions need an explicit `--overwrite`" and
      `docs/08-builder.md` lists `[--overwrite]` on the build command — both still true for the CLI, so
      verify rather than edit, and only change them if the wording implies the loop behaves the same way.
      **Watch the second `[--overwrite]` on that `docs/08` page**: it belongs to the blueprint command,
      whose refusal is a different gate in `BlueprintGenerator` that throws `IllegalStateException` and is
      explicitly out of scope here. Verify you edited the right one by checking which command each line
      documents.
- [ ] 5.5 Re-run the wiki lint for the pages touched and note the Pages publish as owed, as the other
      changes in this repo do.
