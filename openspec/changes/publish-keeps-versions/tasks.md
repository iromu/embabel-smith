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

- [x] 2.1 Give `PackEntity` a key that is not the bare name — `(name, version)` per decision 2 — keeping
      `goal`, `steps`, `saved_at` and `content` as the version's payload. Verify a test saves two versions
      of one name and reads both back, and that the assertion would fail against the current
      name-as-primary-key mapping.
      Done 2026-11-19. `@IdClass(PackEntityId.class)` with `version` as a second `@Id`; `PackRepository`
      becomes `JpaRepository<PackEntity, PackEntityId>` and still declares **no** finders. A POJO IdClass,
      not a record, because Hibernate materialises the key itself and needs the no-arg constructor.
      `twoVersionsOfOneNameAreStoredAsTwoRows` stores `1.0.0` and `1.1.0` and asserts both; under the old
      mapping the second `save` is a merge onto the one row, so it fails twice over — one row, and no
      `version` column to select.
- [x] 2.2 Decide who moves an existing `packs` table, because **nothing will do it for you**: the studio
      sets `hibernate.hbm2ddl.auto = "update"` in `storage/StorageBinding`, and there is no Flyway, no
      Liquibase and no `.sql` file in the repo. `update` adds columns; it does not re-key a primary key, so
      an already-deployed studio would keep a name-keyed table underneath an entity expecting
      `(name, version)` — a runtime failure in exactly the deployment whose rows matter. Choose one: a
      start-up check that detects the old key and rebuilds the table, or a declared re-create that accepts
      losing dev rows. Verify by running the studio's persistence against a database holding the OLD shape
      and asserting table and entity agree afterwards — not by asserting the new mapping alone.
      Done 2026-11-19: **the detector, not the re-create** — `storage/PacksKeyMigration` reads the key
      through JDBC metadata and, when it is not `(name, version)`, adds the column, backfills each row from
      the version its own stored `content` declares (default `1.0.0`), sets it `NOT NULL`, drops the old
      constraint by the name metadata reports and adds the composite key. Rows are never deleted. It is
      called from `StorageBinding.create` **before** the `EntityManagerFactory` is built, which is the whole
      point: `update` would otherwise add the missing key column as `NOT NULL` to a populated table.
      `PacksKeyMigrationTest` hand-creates the old shape (name-only primary key, no `version` column, two
      seeded rows, one declaring no version), binds the real `StorageContext`, then asserts through metadata
      that the key is `(name, version)`, that both rows survived with the versions their content declared,
      and that a new version writes onto the migrated table. A second test binds twice to prove idempotence;
      the run logged exactly two rebuilds for three binds, so the second bind left the new key alone.
      **Residual, narrowed 2026-11-20 — and narrowing it found a real bug.** The DDL now runs a second time,
      against H2 in PostgreSQL mode with lower-cased identifiers (`PacksKeyMigrationTest
      .aNameKeyedTableIsRebuiltWhenIdentifiersAreStoredLowerCase`), which is the identifier casing the deployed
      database uses. That test failed first, and the failure was the migration's, not the test's: it reads the
      primary-key constraint name out of JDBC metadata and re-issued it **unquoted**, and a case-folding
      connection folds the statement's identifier and misses the constraint the name came from — H2 stores a
      generated key name upper case (`CONSTRAINT_6`) even when it stores table names lower case. Probed directly
      against the H2 jar: the same rebuild fails unquoted and succeeds quoted, in both dialects. The drop is now
      quoted with the name metadata reports, which is the name the database stores, and the class javadoc says
      which identifiers are quoted and why. **What is still unverified:** the Postgres-only forms —
      `DROP CONSTRAINT packs_pkey`, `ALTER COLUMN … SET NOT NULL` — have never been executed against a real
      Postgres, and Postgres is the deployment with a real name-keyed table in the persistent `studio-pgdata`
      volume. H2's PostgreSQL mode is a casing check, not a Postgres.
- [x] 2.3 Make a republish of the same name and same version an update of that version, and a republish at
      a new version an added row. Verify both in one test, and verify the same-version case does not
      duplicate.
      Done 2026-11-19 by `republishOfTheSameVersionUpdatesThatRow`: one row at `1.0.0` after two saves of it,
      the goal updated in place, then a save at `1.1.0` adds a second row.
- [x] 2.4 **Decided by `design.md` decision 7: the studio's existing store path writes the versioned row;
      the builder gains no persistence.** The search for 1.3 found that the publish path never touches
      `packs` — `LoopPublishPolicy.publish` ends at a `Publisher` writing to a folder or a git clone — so
      "the studio records the version alongside the publish it observes" is not a smaller alternative to
      builder persistence, it is the only shape available without giving the builder a database. Implement
      it by saving the candidate through the path that already exists, `ProcessStore.savePack(AgentSpec)`,
      which already receives the whole pack and can read `pack.version()` from it. Verify: a builder pom
      check that no JPA, `spring-data` or JDBC dependency was added; a test that a loop publish leaves a row
      whose version is the candidate's, readable afterwards; and that the folder destination's content is
      unchanged by the new write (the save is additive, not a replacement of publishing).
      Done 2026-11-19. The builder pom gained nothing — `grep -nE 'jpa|jdbc|spring-data|hibernate|postgres|h2'
      prod/embabel-smith-builder/pom.xml` returns nothing, and the only builder-side file this change touched
      is a test. The save lives in `LoopPublishPolicy.publish`, **after** `publisher.publish(...)` returns, so
      a refused or failed publish leaves no row; it hands the publisher the *same* `AgentSpec` instance it
      saves, so the destination and the row cannot report success at two versions. `ProcessStore` gained
      `savePackWhenConfigured`, which records nothing while no database is bound — the studio stays usable
      with no storage. Verified by `ImprovementLoopTest.aLoopPublishLeavesARowAtTheCandidateVersion`, which
      publishes through a real `FolderPublisher` into a temp dir, asserts the stored row is at `1.0.1`, and
      re-reads the folder with `AgentSpecReader` to confirm the artifact written there is the same pack at the
      same version. `LoopServiceTest.aLoopPublishRecordsTheCandidateAtItsVersion` covers the service path, and
      its refusal/failure tests now assert `never()` on the store.
- [x] 2.5 Decide what `listPacks` means once a name has several rows — `store/ProcessStore.java:74` returns
      `findAll()`, which will list every version. Either collapse to the highest version per name (decision
      3's rule, applied in one place) or show versions as separate rows; pick one and say which the studio
      UI is showing, because the listing is what a human uses to tell whether an improvement landed.
      Done 2026-11-19: **collapsed to the highest version per name**, through the same `highestPerName` the
      loader uses, so the listing and `loadPack` cannot disagree about what a bare name means — a listing
      showing three rows of which only one is what clicking that name returns would be the worst possible
      thing for a human checking whether an improvement landed. `version` was deliberately **not** added to
      `PackRecord`/`StoredPackInfo` or the frontend: the DTO has no version column today, so "versions as
      separate rows" would render three visually identical entries, and showing a version is a UI change
      outside this change.

## 3. The loop keeps its versions — in the store, not in the folder name

- [x] 3.1 Update the one name-keyed reader found in 1.3 — `store/ProcessStore.java:89`,
      `packs().findById(name)` — to resolve a version explicitly using the answer from 1.2, and deal with
      `listPacks` at line 74 (task 2.5). Verify it compiles against the new key and that no reader is left
      relying on the name being unique. There is no second reader: the repository declares no finders, and
      no other module queries the table.
      Done 2026-11-19. `findById(name)` cannot compile against a composite key, so `loadPack` fetches and
      narrows in Java through `highestPerName` — the same helper the listing uses, so decision 3's rule lives
      in exactly one place. **The rule was mutation-checked, not argued:** replacing the numeric
      `compareVersions` with `String.compareTo` and re-running the module failed exactly one of 153 tests,
      `highestVersionComparesNumericallyNotLexically`, with `expected: "1.10.0" but was: "1.9.0"`. That test
      saves the higher version first, so it also fails against "most recently saved wins". The single
      failure is itself the evidence that no other reader silently depends on the ordering.
      Note for whoever writes the next reader here: a derived finder is unavailable in this module — the
      ANTLR query parser is shadowed by an older copy bundled in a transitive Kotlin compiler jar, as
      `TraceRepository` and `DiscoveryRepository` both record.
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
- [x] 3.2a **Implement the version increment the ruling chose** (1.4), now placed by `design.md` decision 7
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
      Done 2026-11-19. `studio/improve/Refiner` now builds its candidate as
      `new AgentSpec(nextVersion(current.manifest()), …)`; `nextVersion` copies the record, bumping only the
      version, and `nextPatch` treats a null or blank declaration as `PackEntity.DEFAULT_VERSION`, increments
      the last dot-separated segment numerically, and returns a non-numeric declaration unchanged rather than
      throwing. Placing the bump in the refiner, not the publisher, is what makes the approval queue, the gate
      and the store agree on one version. The builder's `eval/RefinementLoop` was left alone and is now pinned:
      `RefinementLoopTest.aRefinedPackKeepsTheVersionItWasGiven` refines a pack through the real loop, asserts
      one refinement happened, and asserts the result still names `test-spec` at `1.0.0`.
- [x] 3.3 **Add the assertion that does not exist today, and make it pin the value decision 7 chose.** No
      test in the repo pins the loop's `overwrite`: the loop tests inject a `Publisher` lambda that ignores
      the argument it is handed, so changing the flag breaks nothing either way. After 2.4 lands, add a test
      that a loop publish leaves a versioned row, and verify it by mutating two things in turn — flipping
      `overwrite` to `false` and the save-out to a no-op — watching the tests fail for the right reason.
      Done 2026-11-19, with the mutation pair changed to match decision 7: flipping `overwrite` proves nothing
      now that keeping it `true` is the ruling, so the two mutations run were the **save-out** and the
      **increment** — the two behaviours this change actually adds. Removing
      `packStore.savePackWhenConfigured(...)` failed exactly the two predicted tests
      (`ImprovementLoopTest.aLoopPublishLeavesARowAtTheCandidateVersion`,
      `LoopServiceTest.aLoopPublishRecordsTheCandidateAtItsVersion`) and nothing else. Replacing
      `prefix + (patch + 1)` with `prefix + patch` failed three
      (`RefinerTest.candidateIsOnePatchAboveThePackItImproves`,
      `RefinerTest.candidateOfAPackDeclaringNoVersionStartsAboveTheStoredDefault`, and the loop test above) —
      the third is the one that matters, because it shows a lost bump reaches the stored row rather than being
      caught only by a unit test on the bump itself. Both mutations reverted; the module is green at 159 tests.
      The pre-existing fact this task opened remains true: no test pins `overwrite`, by design.
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
      Done 2026-11-20. The fallback is gone; the bean now hands back a publisher that refuses, throwing the
      existing `LoopPushDisabledException` with a message naming `smith.studio.improve-source` (held as
      `IMPROVE_SOURCE_SETTING`, per the `PUSH_SETTING` precedent) and saying the candidate stays pending. It is
      a lambda over `Publisher`'s one abstract method, so no class was added, and it surfaces through the two
      catches that already exist — `LoopService.trigger` and `LoopService.approve` — with no new catch.
      `StudioConfigurationLoopPublisherTest` has both directions: the refusal, and a configured source still
      getting a publisher that writes, so a publisher that refused unconditionally could not pass. **Mutation-
      checked:** restoring the temp-directory line fails the refusal test and nothing else.
      The test is worth a note on how it verifies "nothing was written". Its first version asserted that
      `<java.io.tmpdir>/<pack name>` did not exist — a global path, which made the suite order-dependent on
      history: after the mutation had run, the real `/tmp` held the pack the mutation published, and the green
      code failed. It now redirects `java.io.tmpdir` to a `@TempDir` and asserts that directory is still empty,
      which is both hermetic and still catches the mutation, because the fallback resolved the same property.
- [x] 4.2 Confirm the git path needs no versioning machinery of its own (decision 5): two publishes of one
      name are two commits in the target repository. Verify with a test against a local bare repository
      that the first publish's content is still readable from history after the second one overwrites the
      working copy in the clone.
      Done 2026-11-20 by `PublisherTest.aGitSourceKeepsEveryPublishOfOneNameAsACommit`: two publishes of one
      name into a `git init --bare` repository leave `rev-list --count HEAD` at **2**, the working copy holds
      the second publish, and `git show HEAD~1:<name>/SKILL.md` still returns the first publish's content and
      not the second's. Both publishes push, because a `GitPublisher` commit without a push lives only in the
      clone the test is about to close. The two publishes carry **different** content on purpose: the publisher
      skips a commit when the staged diff is empty, so identical content would make one commit and the test
      would pass against a publisher that kept nothing. Offline — bare repo, clone and working tree all under
      the temp dir. No production change: decision 5 was that the git path needs nothing added, and nothing
      was added.
- [x] 4.3 Leave `canPublish()` and its message alone (decision 6). Verify its test still passes, and that
      making the durable path the default did not quietly widen when publishing is allowed.
      Done 2026-11-20. `LoopPublishPolicy` is not in the change set at all — `git diff` for that file is empty,
      so `canPublish()` (`!gitSource || push`), its message and `PUSH_SETTING` are byte-for-byte what they were.
      Its tests pass unchanged. Nothing widened: the new refusal fires on a *narrower* condition than
      `canPublish()` (no improve source configured at all), it does not consult `canPublish()`, and a source
      that was publishable before is publishable now.

## 5. Documentation

Four pages speak about this rule, and a change that lands without updating all four leaves three of them
describing a behaviour that no longer exists. Each is named with what it currently says.

- [x] 5.1 `docs/06-spec-sources.md` — rewrite the `## Publishing` table row "Name collision | refused
      unless `--overwrite` is passed" to say what the code now does: the CLI still refuses without the flag,
      and the loop still replaces its folder destination but keeps every published version as a row keyed by
      `(name, version)`. Check whether `## Discovery is one level deep, deliberately` needs a clause
      confirming versions are **not** nested directories — decision 7 keeps the folder named by the pack
      alone, so that section is now the reason the folder cannot hold versions, and the page should say so.
      Verify by reading every rule on the page back out of the classes it names.
      Done 2026-11-20. The table row now separates the two callers: a CLI publish is refused without
      `--overwrite`, the loop passes it and its destination directory is replaced, with the reason — the flag is
      a human being asked to confirm a replacement, which is an ask the loop cannot answer for. The store rule
      decision 2 required to be stated once is stated here: `FolderPublisher` names the directory from
      `Publishable.name()`, the version is nowhere in the path, and `ProcessStore.savePackWhenConfigured` is
      where the replaced version survives, keyed `(name, version)`, `1.0.0` for an artifact declaring none, a
      bare name meaning the highest version compared segment by segment in Java rather than a query.
      `## Discovery is one level deep, deliberately` gained the clause: that section is why a destination holds
      one directory per pack name and never `pack/v2/` nesting.
- [x] 5.2 `docs/10-self-improvement.md` — it currently ends its publish paragraph with "The loop's forced
      `overwrite` is a separate decision, still open in `12-backlog.md`". That sentence becomes false the
      moment this change lands. Replace it with what the loop does now: it still replaces the folder, and the
      improvement survives in the store at an incremented version.
      Done 2026-11-20. That sentence is gone; the paragraph now says the loop still passes the collision flag
      and why clearing it would collide rather than improve, and that the improvement survives in the
      `(name, version)` store at one patch above the pack improved, resolving by bare name through the rule in
      `06-spec-sources.md`. **One sentence on the same page is still not true and is not this task's to fix:**
      "A `folder:` source is unaffected — a local write is already durable" holds for a configured folder, not
      for the unconfigured case that `StudioConfiguration.loopPublisher` still routes to the system temp
      directory. Task 4.1 owns that fallback; re-read the sentence when 4.1 lands.
- [x] 5.3 `docs/12-backlog.md` — there is already an item reading "Open — the loop still forces
      `overwrite = true`", with `Check: grep -rn 'boolean overwrite = true' prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/improve/LoopPublishPolicy.java`.
      **Do not close it as an absence: decision 7 keeps that line on purpose.** Rewrite the item in the
      page's `Closed —` form around what actually changed — the loop no longer *depends* on the folder for
      durability — and change its `Check:` to something that is true afterwards (e.g. that the loop's
      publish leaves a versioned row). Leaving the original check pointing at a string that still exists
      would make a closed item look open, and deleting the item would lose the reason the flag survived.
- [x] 5.4 `docs/05-architecture.md` carries "Name collisions need an explicit `--overwrite`" and
      `docs/08-builder.md` lists `[--overwrite]` on the build command — both still true for the CLI, so
      verify rather than edit, and only change them if the wording implies the loop behaves the same way.
      **Watch the second `[--overwrite]` on that `docs/08` page**: it belongs to the blueprint command,
      whose refusal is a different gate in `BlueprintGenerator` that throws `IllegalStateException` and is
      explicitly out of scope here. Verify you edited the right one by checking which command each line
      documents.
      Done 2026-11-20, verified and **not edited**, which is what the task asked for. `docs/05-architecture.md`
      states the rule as a CLI invariant and names the flag only `BuildCommand`/`BlueprintCommand` take
      (`BuildCommand` → `BuildOptions.overwrite()` → `BuilderService`), so it makes no claim about the loop; the
      loop's exception is documented on the three pages above. Both `[--overwrite]` lines on `docs/08-builder.md`
      were checked against the command each documents — the build one and the blueprint one, whose refusal is the
      separate `BlueprintGenerator` gate — and neither was touched.
- [x] 5.5 Re-run the wiki lint for the pages touched and note the Pages publish as owed, as the other
      changes in this repo do.
      Done 2026-11-20: the lint reports 45 pages, **0 broken links, 0 orphans, 0 stale**. One wiki page was
      actually wrong about the schema — `features/smith-studio-storage.md` recorded `packs (name PK …)` — and now
      records the `(name, version)` key, the bare-name rule and `PacksKeyMigration`, with the two new source
      files added to its `source_paths`. The 19 `source_paths` the lint reports as missing are pre-existing and
      unrelated: deleted `openspec/specs/` and archive paths, plus moved studio `generate/`/`discover/` files.
      **The GitHub Pages re-publish is owed, not done**: `wiki-pages.yml` deploys on a push to the default
      branch, and this work is on `poc/iteration-2`, so the site still shows the pre-change wiki.
