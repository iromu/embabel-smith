# Tasks

## 1. Core types

- [x] 1.1 Define `SkillSource` interface (`resolve() → ResolvedSkillSource`) and `ResolvedSkillSource` / `ResolvedPack` models in `prod/embabel-smith-skillsource`
- [x] 1.2 Implement the source-URI parser (`folder:` / `git:` + `@ref`) with unit tests (well-formed, default ref, malformed scheme, empty)

## 2. Folder source

- [x] 2.1 Implement `FolderSkillSource` (one-level pack discovery, `SkillPackValidator`-gated, warnings for invalid subdirs, empty-folder warning)
- [x] 2.2 Unit tests: discovery, invalid-pack warning, empty folder, missing root error

## 3. Git source

- [x] 3.1 Implement `GitSkillSource` (clone-on-first-use, fetch-on-reresolve, ref checkout, cache under `~/.smith/skill-sources/<uri-hash>`, corrupt-cache recovery) using the system `git` CLI non-interactively
- [x] 3.2 Unit/integration tests against a local bare repo: first clone, fetch on re-resolve, branch/tag/SHA pinning, unpinned follows branch tip, unknown-ref error, unreachable-remote error

## 4. Content hashing

- [x] 4.1 Implement per-pack content hashing (SHA-256 over sorted format-owned files)
- [x] 4.2 Tests: hash stable across resolutions, hash changes when a step file edits, hash ignores non-format files

## 5. Verify + docs

- [x] 5.1 `mvn -pl prod/embabel-smith-skillsource test` green offline (no network, no model)
- [x] 5.2 Write `prod/embabel-smith-skillsource/README.md` (usage, URI grammar, cache layout, test story)
