# Design

## Context

- The git publisher's working tree is temporary by construction: `GitPublisher` line 40
  `this.workDir = Files.createTempDirectory("smith-git-publish-");`, it clones into it in the
  constructor, and `close()` (line 80) deletes it (`deleteTree(this.workDir);`). Every caller
  publishes inside try-with-resources — `SkillBuilderService` does
  `try (Publisher publisher = publisherFor(target)) { … }` — so the tree is gone before the report is
  rendered.
- The publish result is a two-component record: `publish/PublishResult.java:18`
  `public record PublishResult(Path location, boolean pushed) {`, with the javadoc promise
  "the directory where the published pack now lives".
- `FolderPublisher` line 34/41 computes `Path unitDir = Path.of(this.target.location()).resolve(unit.name());`
  and returns `new PublishResult(unitDir, false)` — for a folder source that path is real and stays
  real. Only the git case lies.
- Four consumers surface the value: the two build reports (`BuildResult.java:100`,
  `skill/SkillBuildResult.java:81`, both `sb.append("  published:   ").append(this.published.location())`)
  and the studio's two `.toString()` call sites (`rest/dto/PublishInfo.java:20`,
  `loop/LoopService.java:157`). None of them resolves files from it; they stringify it.
- No consumer outside the builder's own tests needs a `Path` after publish. The tests that do resolve
  files either use a folder target (`SkillPublishableTest`, `PublisherTest`'s folder cases) or, for
  git, already re-clone the bare repository to check the commit
  (`PublisherTest`: `Path verify = tmp.resolve("verify"); runGit("clone", "--quiet", bare.toString(), verify.toString());`).
  The one in-lifetime git file assertion is `assertThat(local.location().resolve("manifest.yml")).isRegularFile()`.
- One test constructs the record directly: `cli/BuildCommandTest.java:280`
  `new PublishResult(Path.of("sample-skill"), false)`.
- A skill over `git:` is unexercised: `GitPublisher` appears in no skill test, and the only git
  publish test publishes `SampleAgentSpecs.explainer()`, a pack.
- `docs/06-spec-sources.md` §Publishing is where the publish rules are stated once for all targets
  (its table row: `| Git publish | **commit, never push** by default; `--push` is opt-in |`).

## Goals / Non-Goals

Goals:

- What a publish reports is true after the publisher is closed, and reads as a place a human can go.
- The same rule for both artifact shapes (pack, skill) and both targets (folder, git), stated once.
- A skill published over `git:` proven by an offline test against a local bare repository.

Non-Goals:

- No change to the working tree's lifetime, the collision gate, the commit message, or push.
- No commit SHA in the location: the artifact path within the source is what a reader needs, and a
  SHA would make the string change on every republish for a value the report has no use for.
- No new publisher, target kind, or abstraction over "where an artifact lives".

## Decisions

**D1 — `PublishResult` carries one location, expressed as the source states it.** A `folder:` publish
reports the written directory; a `git:` publish reports the repository it committed to with the
artifact's path inside it. Rejected: keep `Path location` and add a second `String` field for display
— two notions of "where it lives", one of which is a lie after close, and every future consumer has
to know which to print; the trap stays. Rejected: keep the git working tree alive so the path stays
resolvable — that leaves a full clone on the user's disk per publish, and the tree exists precisely
to be thrown away. Rejected: report a path *inside the remote* as a `Path` — a repository URL is not a
path and the type would be a fiction. Rejected: leave the record alone and patch only the report line
— the studio's `PublishInfo` and the loop's approve response would still carry the dangling path,
and the fix belongs at the source of the value.

**D2 — The git-skill test asserts the committed tree, not the publisher's copy.** It re-clones the
bare repository the way the pack test already does, and additionally asserts the reported location
does not resolve under the system temporary directory. Rejected: asserting the in-lifetime path,
which is what let the pack test pass while the reported value was already unusable.

**D3 — The rule is documented in `docs/06-spec-sources.md`, not in the builder page.** That page owns
publish semantics for every target; the builder page links to it.

## Risks / Trade-offs

- **The `Path` component is load-bearing in tests and is being retyped.** The change touches
  `PublisherTest`, `SkillPublishableTest` and one construction in `BuildCommandTest`; each is a
  comparison against a string instead of a path. The blast radius is four test files and two studio
  call sites, all of them stringifying already.
- **A location string cannot be opened programmatically.** Nothing does today; a future consumer that
  needs the files of a git artifact should read them from the source (that is what a git source is),
  not from a publish report.
- **Losing the in-lifetime path removes an assertion the pack test currently makes.** It is replaced
  by a stronger one on the re-cloned tree, which is what the commit actually contains.
- **The string form must stay parseable for the UI.** The studio displays it and does not parse it;
  the shape is `folder:<path>` / `git:<url>#<path>` so it is recognizable in a report line.
