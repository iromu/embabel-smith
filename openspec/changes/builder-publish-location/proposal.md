# Proposal

## Why

A `git:` publish tells the reader where to look at a directory that no longer exists.

`GitPublisher` clones into a temporary working tree — line 40
`this.workDir = Files.createTempDirectory("smith-git-publish-");` — writes the artifact into it at
line 50 `Path unitDir = this.workDir.resolve(unit.name());` and returns that path as the publish
location at line 76 `return new PublishResult(unitDir, pushed);`. The publisher is always closed:
`SkillBuilderService` publishes inside `try (Publisher publisher = publisherFor(target)) { … }`, and
`GitPublisher.close()` at lines 80-81 runs `deleteTree(this.workDir);`. What the location referred to
is gone before the caller can use it.

The value is not inert — it is printed and served:

- `skill/SkillBuildResult.java:81` — `sb.append("  published:   ").append(this.published.location());`
- `BuildResult.java:100` — the same line for a pack
- studio `rest/dto/PublishInfo.java:20` — `return new PublishInfo(result.location().toString(), result.pushed());`
- studio `loop/LoopService.java:157` — `return new ApproveResponse(true, result.location().toString(), null);`

So a `git:` build's report ends with a `/tmp/smith-git-publish-…/<name>` line, and the studio's
approve response hands the same string to the UI, for an artifact that in fact lives as a commit in
the named remote. `docs/06-spec-sources.md` states the publish rules for all targets, and the reader
of that report has no way to know the path is a corpse.

Second half of the same backlog item: nothing exercises a skill over `git:`. The only git publish
test is `publish/PublisherTest`'s `gitPublishCommitsAndPushesIntoALocalRepo`, which publishes
`SampleAgentSpecs.explainer()` — a pack. Grepping the skill tests for the publisher returns nothing
(`GitPublisher` appears in neither `publish/SkillPublishableTest` nor `skill/SkillBuilderServiceTest`),
and that pack test asserts `local.location().resolve("manifest.yml")).isRegularFile()` *inside* the
publisher's lifetime, so it never observes the dangling path either. The `Publishable` generalization
made a skill publishable to git; no test checked that it works.

## What Changes

- `PublishResult` reports **one location, in the source's own terms**: the written directory for a
  `folder:` source (as a string), and for a `git:` source the repository it committed to plus the
  artifact's path within it. The temporary working tree is no longer surfaced anywhere.
- Both build reports and both studio responses carry that string unchanged.
- A skill published to a local bare repository is covered offline, asserting the committed tree
  carries `SKILL.md`, `evals.json` and the lifecycle sidecar, and that the reported location is not
  under the publisher's temporary directory.
- `docs/06-spec-sources.md` records the rule once for all publish targets; the backlog item is struck.

## Capabilities

### New Capabilities

- `published-location`: what a publish is allowed to report as the place an artifact now lives — a
  location expressed in the source's own terms, true after the publisher has released its working
  storage, and identical wherever that report is surfaced; plus the requirement that both publishable
  artifact shapes are exercised against both publish targets.

### Modified Capabilities

None. There are no capability specs under `openspec/specs/` — consolidated into `docs/` on
2026-11-15. The durable home is `docs/06-spec-sources.md`, which already states the publish rules
for every target.

## Impact

- **`prod/embabel-smith-builder`**: `publish/PublishResult` (the `Path` component becomes the source's
  own location string), `publish/GitPublisher` and `publish/FolderPublisher` (each constructs it),
  `BuildResult.report()` and `skill/SkillBuildResult.report()` (no code change beyond the accessor's
  type — the reports already print `location()`).
- **`prod/embabel-smith-studio`**: two call sites drop a `.toString()` —
  `rest/dto/PublishInfo.java:20` and `loop/LoopService.java:157`.
- **Tests**: `publish/PublisherTest` and `publish/SkillPublishableTest` compare against the string
  form; `cli/BuildCommandTest.java:280` constructs `new PublishResult(Path.of("sample-skill"), false)`
  and becomes a string; one new git-skill case.
- **`docs/06-spec-sources.md`**: the publish section gains the location rule.
- **`docs/12-backlog.md`**: item 4 of *Builder — skill target* is struck.
- **Not touched**: no publisher's working-tree lifetime or cleanup changes, and no push behaviour.
