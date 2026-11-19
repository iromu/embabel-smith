# Spec Sources and Publishing

A **spec source** is where agent specs are read from and written to. Reads live in
`prod/embabel-smith-specsource` (`com.embabel.smith.specsource`); writes live in the builder's
`publish/` package. The format of what it resolves is
in [`agent-spec-format.md`](agent-spec-format.md); who consumes it is in
[`07-runtime.md`](07-runtime.md) and [`08-builder.md`](08-builder.md).

## The URI grammar

| Scheme | Meaning |
| --- | --- |
| `folder:<path>` | a directory whose immediate subdirectories are candidate packs |
| `git:<path-or-url>[@ref]` | a git repository, optionally pinned to a branch, tag or commit |

An unknown scheme is rejected by `SpecSource.parse` with an `IllegalArgumentException` naming the
offending URI — it does not list the accepted schemes. `SpecSourceException` is reserved for
materialisation failures (unreachable remote, unknown ref, missing folder); the publish path is the
one place that names the accepted schemes (`publish/PublishTarget.parse`).

**Discovery is one level deep, deliberately.** A folder source scans immediate subdirectories and
treats a directory as a pack when it has a `manifest.yml` *and* it validates — discovery reuses
`smith-common`'s validator rather than re-implementing a check, so the two can never disagree.
Deeper nesting is a future concern, not a v1 feature. An invalid candidate is reported as a warning
and skipped; an empty source is a warning, not an error.

## Git resolution

The implementation shells out to the system `git` CLI rather than using JGit. The CLI brings SSH
support, credential helpers and partial clones for free; JGit would add a heavyweight dependency
with its own credential configuration for no gain.

Consequences worth knowing:

- Every invocation is **non-interactive** (`-c core.askpass=false`, `GIT_TERMINAL_PROMPT=0`), so a
  clone can never hang waiting for a password prompt. The read path clones with `clone -- <url>`
  and discovers the default branch afterwards via `symbolic-ref`; an explicit `-b` appears only on
  the publishing path.
- The cache lives at `~/.smith/spec-sources/<uri-hash>` — one subdirectory per source, keyed by a
  SHA-256 of the URI, so two repositories can never collide. Re-resolving is `fetch` + `checkout`;
  a cache directory with no `.git` is treated as corrupt and replaced by a fresh clone.
- **A pinned commit SHA is immutable.** A pinned *branch or tag* is not: each resolve runs
  `fetch --all --prune`, checks the ref out, and — when `refs/remotes/origin/<ref>` exists — resets
  hard onto it, so a branch pin follows that branch's tip, exactly like an unpinned source (which
  follows the default branch). Pin a SHA when you need reproducibility.
- Local paths are cloned from disk, so the whole path is testable offline. The test suite uses
  `git init --bare` repositories rather than any network remote.

## Read-only, by design

The source layer **reads**. Publishing is a separate write path (`publish/` in the builder). Keeping
them apart avoids cache and write races when the studio runs both sides of the loop in one process.

## Content hashes

`PackContentHasher` computes a SHA-256 over the pack's format-owned files in sorted order, so the
hash is independent of directory listing order and changes only when content changes. The same value
is exposed on `ResolvedSpec`.

**This is not the hash the lifecycle's drift detection consumes.** Drift is assessed against the
builder's `SpecContentHash`, a semantic hash over the parsed model (goal, steps, types). The two
quantities differ by construction and never match each other; `SourceService` records that using
`PackContentHasher` on the sidecar would produce permanent false drift. Keep them straight when
reading [`10-self-improvement.md`](10-self-improvement.md).

## Publishing

Publishing writes a pack into a source. It is shared by all three kinds of publishable unit — an
agent spec, a skill folder, an N-pack process — because the publisher was generalized to *"a named
directory of files"* rather than staying spec-specific. Note that only `agent-spec` and `skill` are
selectable with `--target`; the process is generated alongside every agent-spec build, not chosen.
That is why the collision gate and the no-push rule are stated once here instead of three times.

| Behaviour | Rule | Why |
| --- | --- | --- |
| Target grammar | the same `folder:` / `git:` grammar as reads | one grammar for the whole system |
| Name collision | refused unless `--overwrite` is passed | a silent overwrite of someone else's pack is the worst possible default |
| Git publish | **commit, never push** by default; `--push` is opt-in | pushing is a shared-state action needing credentials and intent |
| Commit message | `smith: publish <unit> (baseline <score>)` | the baseline the improvement loop later compares against is in history |
| Reported location | `folder:` → the directory it wrote; `git:` → `git:<repo>#<unit>` | a location has to still be true when it is read. The git publisher clones into a temporary working tree and `close()` deletes it, so a path inside that tree names nothing; the repository and the unit's path within it is where the artifact actually lives |

The location is one value, stated in the source's own terms, and every surface that reports it
prints it unchanged: `BuildResult.report()` and `skill/SkillBuildResult.report()` print it, and the
studio's `PublishInfo` and the loop's approve response carry the same string. To read the files of a
git-published artifact, read them from the source — that is what a git source is.

Implementation: `publish/FolderPublisher`, `publish/GitPublisher`, `publish/PublishTarget`, with
`publish/CollisionException` for the gate.

## Failure behaviour

Failures are explicit and actionable, never a stack trace from the transport layer: unreachable
remote, unknown ref and missing folder each get their own `SpecSourceException` naming what was
attempted and what would fix it. An unknown scheme is the exception to that pattern — it is an
`IllegalArgumentException` from parsing that names the URI but not the accepted schemes.
