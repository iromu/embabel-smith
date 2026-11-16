# embabel-smith-specsource

Spec sources: `git:` and `folder:` URIs, pack discovery, ref pinning, local cache, and
content hashing. Shared by the runtime (reads packs from a source) and the builder
(publishes packs into a source).

## URI grammar

```
folder:<absolute-or-relative-path>
git:<local-path-or-remote-url>[@<ref>]
```

- `folder:` — the path is a *library* of packs: each immediate subdirectory holding a
  `manifest.yml` is a pack candidate. Deeper nesting is not scanned.
- `git:` — a git repository (local path or remote URL). `@<ref>` pins a branch, tag, or commit
  SHA; without a ref, the repository's default branch is followed. The `@` is split on its last
  occurrence, so URLs containing `user@host` parse correctly.

Unknown schemes, empty URIs, and empty paths/refs are rejected with an error naming the
offending URI.

## Usage

```java
SpecSource source = SpecSource.parse("git:https://example.com/org/skills.git@v1.2");
ResolvedSpecSource resolved = source.resolve();

resolved.origin();   // Origin(uri, materializedRoot)
resolved.packs();    // List<ResolvedSpec(root, name, contentHash, validationOk)>
resolved.warnings(); // non-fatal findings (invalid packs, empty source)
```

`ResolvedSpecSource.packs()` holds only packs that pass `AgentSpecValidator` structure
validation (from `smith-common`); candidates that fail are excluded and named in `warnings`.
A source with no valid packs resolves to an empty pack set plus a warning, not an error.

Materialization failures (missing folder, unreachable remote, unknown ref, non-git directory)
throw `SpecSourceException` with a message naming the offending source and the underlying
failure.

### Folder sources

`FolderSpecSource` scans the root in place — nothing is copied or cloned.

### Git sources

`GitSpecSource` materializes the repository in a local cache:

```
<cacheRoot>/
  <sha-256 of the full source URI>/   ← one directory per source
    .git/
    <worktree at the checked-out ref>
```

The cache root defaults to `~/.smith/spec-sources/` and is overridable via the constructor.
First resolution clones; re-resolution fetches and re-checks-out the pinned ref (branch pins
follow the remote branch tip; tag and SHA pins are immutable). A missing or corrupt cache
directory (no `.git`) is replaced by a fresh clone. All git commands run non-interactively
against the system `git` CLI (`GIT_TERMINAL_PROMPT=0`, no credential prompts).

The source is read-only: publishing packs into a git source is the builder's separate write
path (change 04).

## Content hashes

Each resolved pack carries a SHA-256 content hash computed over its format-owned files —
`manifest.yml`, `goal.yml`, and the YAML files under `steps/` and `types/` (the same files
`smith-common` reads). Files are fed in sorted relative-path order, so the hash is
order-independent, stable for identical content, and changes when any format-owned file
changes. Non-format files (READMEs, scratch files, `types/.gitkeep`) do not affect it. This is
the quantity POC 18's drift detection consumes.

## Tests

`mvn -pl prod/embabel-smith-specsource test` — 29 tests, fully offline (no network, no model):

- **URI parser** — well-formed `folder:`/`git:` URIs, default ref, last-`@` split, malformed
  scheme, empty URI/path/ref.
- **Folder source** — one-level discovery, invalid-pack exclusion with a warning naming the
  directory and the validation issue, empty-folder warning, missing-root error.
- **Git source** — against a local bare repository: clone on first use, fetch on re-resolution
  (an untracked marker proves no re-clone), branch/tag/SHA pinning, default-branch following,
  unknown-ref error, unreachable-remote error, non-git-directory error, corrupt-cache recovery.
- **Content hashing** — stable for identical content (including across directories), sensitive
  to step/manifest/goal edits, blind to non-format files.
