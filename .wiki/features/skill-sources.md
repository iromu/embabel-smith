---
title: "Skill sources (folder: and git:)"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "prod/embabel-smith-skillsource/src/main/java/com/embabel/smith/skillsource/SkillSource.java"
  - "prod/embabel-smith-skillsource/src/main/java/com/embabel/smith/skillsource/FolderSkillSource.java"
  - "prod/embabel-smith-skillsource/src/main/java/com/embabel/smith/skillsource/GitSkillSource.java"
  - "prod/embabel-smith-skillsource/src/main/java/com/embabel/smith/skillsource/PackContentHasher.java"
  - "prod/embabel-smith-skillsource/README.md"
updated_at: "2026-11-06"
---

# Skill sources (folder: and git:)

`prod/embabel-smith-skillsource` is how the product names a *library* of skill-packs. Both the
runtime (which reads packs to run) and the builder (which publishes packs) speak this one grammar,
so a `folder:` or `git:` URI is the single unit of "where do skills come from / go to". It is a
plain, framework-free module — no Embabel, no Spring — fully offline-testable.

## URI grammar

```
folder:<absolute-or-relative-path>
git:<local-path-or-remote-url>[@<ref>]
```

- **`folder:`** — the path is a library: each immediate subdirectory holding a `manifest.yml` is a
  pack candidate. Deeper nesting is not scanned. `FolderSkillSource` scans in place — nothing is
  copied.
- **`git:`** — a git repo (local path or remote URL). `@<ref>` pins a branch, tag, or commit SHA;
  without it, the default branch is followed. The `@` splits on its **last** occurrence, so URLs
  containing `user@host` parse correctly.

Unknown schemes, empty URIs, and empty paths/refs are rejected with an error naming the offending
URI.

## Resolve

```java
SkillSource source = SkillSource.parse("git:https://example.com/org/skills.git@v1.2");
ResolvedSkillSource resolved = source.resolve();
resolved.packs();    // List<ResolvedPack(root, name, contentHash, validationOk)>
resolved.warnings(); // non-fatal findings (invalid packs, empty source)
```

`packs()` holds only packs that pass `SkillPackValidator` structure validation (from
`smith-common`); candidates that fail are excluded and named in `warnings`. A source with no valid
packs resolves to an **empty** pack set plus a warning, not an error. Materialization failures
(missing folder, unreachable remote, unknown ref, non-git directory) throw
`SkillSourceException` naming the source and the underlying failure.

## Git materialization + cache

`GitSkillSource` materializes the repository in a local cache — one directory per source, keyed by
the SHA-256 of the full source URI, under `~/.smith/skill-sources/` (constructor-overridable):

```
<cacheRoot>/<sha-256 of the URI>/.git + worktree at the checked-out ref
```

First resolution clones; re-resolution fetches and re-checks-out the pinned ref (branch pins follow
the remote tip; tag and SHA pins are immutable). A missing or corrupt cache directory (no `.git`)
is replaced by a fresh clone. All git runs non-interactively against the system `git` CLI
(`GIT_TERMINAL_PROMPT=0`). The source is read-only here — publishing into a git source is the
builder's separate write path ([[smith-builder]]).

## Content hash

Each resolved pack carries a **SHA-256 content hash** computed over its format-owned files —
`manifest.yml`, `goal.yml`, and the YAML under `steps/` and `types/` (the same files `smith-common`
reads). Files are fed in sorted relative-path order, so the hash is order-independent, stable for
identical content, and changes when any format-owned file changes. Non-format files (READMEs,
scratch, `types/.gitkeep`) do not affect it. This is the quantity POC 18's drift detection
consumes ([[skill-lifecycle]]).

## Tests

`mvn -pl prod/embabel-smith-skillsource test` — fully offline: the URI parser (well-formed URIs,
default ref, last-`@` split, malformed scheme, empty fields), the folder source (one-level
discovery, invalid-pack exclusion with a warning, empty-folder warning, missing-root error), the git
source (clone on first use, fetch on re-resolution, branch/tag/SHA pinning, default-branch
following, unknown-ref / unreachable / non-git errors, corrupt-cache recovery), and content hashing
(stable, sensitive to edits, blind to non-format files).
