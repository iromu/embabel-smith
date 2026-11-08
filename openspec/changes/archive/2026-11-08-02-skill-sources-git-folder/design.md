# Design

## Shape

```
SkillSource (interface)
  ├── FolderSkillSource        scan <root>/* for valid manifest.yml
  └── GitSkillSource           git clone/fetch → cache dir → checkout ref → scan like folder

SkillSource.parse(uri) → SkillSource          (URI grammar, pure)
source.resolve()     → ResolvedSkillSource
  ResolvedSkillSource {
    origin        : source URI + materialized root
    packs         : List<ResolvedPack>
    warnings      : List<String>
    ResolvedPack  : { root, name, contentHash, validationOk }
  }
```

## Decisions

### Shell out to the system `git`, not JGit
`GitSkillSource` invokes the `git` CLI via `ProcessBuilder`. Rationale: the CLI handles SSH, credential helpers, and partial clones for free; JGit would be a new heavyweight dependency with its own credential configuration. The machine already has git (the project is a git repo). All git commands are non-interactive (`-c core.askpass=false`, explicit `-b`/`checkout <ref>`), so nothing hangs waiting for input.

### Cache layout
Default cache root: `~/.smith/skill-sources/` (overridable via a constructor arg / property). One subdirectory per source, keyed by a hash of the source URI (so two different repos never collide). Inside: a bare-ish clone (normal clone, single worktree) + the checked-out ref. Re-resolution = `git fetch` + `git checkout <ref>`; if the cache dir is absent or corrupt (`.git` missing), it is replaced by a fresh clone.

### Discovery reuses `smith-common`
A candidate directory is a pack iff it contains `manifest.yml` AND `SkillPackValidator` accepts its structure. One level deep only (a source root is a *library* of packs, not a nested tree) — keeps resolution predictable; deeper nesting is a future concern, not a v1 feature.

### Content hash
SHA-256 over the sorted relative paths + file bytes of the pack's `manifest.yml`, `goal.yml`, `steps/*`, `types/*` (the format-owned files per `smith-common`). Sorted traversal makes the hash order-independent. This is the same quantity POC 18's drift detection consumes, so change 05 can compare hashes without recomputing.

### URI parsing
A small pure function: split on the first `:`, validate the scheme, split the rest on the last `@` only for `git:` (folder paths may legally contain `@`… but not in practice — still, `@ref` is only meaningful for git, so folder URIs never parse a ref). No regex acrobatics; two string splits and a validation pass.

## Testability

Everything is offline:
- Folder source: temp dirs with valid/invalid packs (reuse `smith-common` test fixtures).
- Git source: `git init --bare` a temp "remote", push a pack into it, resolve against the local path — covers clone, fetch, ref pinning (branch + tag + SHA), unknown-ref failure, and hash stability across resolutions.
- URI parser: pure unit tests.

## Boundaries

- This module does NOT run skills, build skills, or talk to a model. It produces pack roots + hashes + origin metadata and nothing else.
- Publishing into a git source (builder, change 04) is a *separate* write path: `GitSkillSource` is read-only; the builder performs its own commit into the working tree it resolved from (or a fresh clone). Keeping the source layer read-only avoids cache/write races when studio runs both sides.
