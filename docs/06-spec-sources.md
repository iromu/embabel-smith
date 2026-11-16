# Spec Sources and Publishing

A **spec source** is where agent specs are read from and written to. One module owns both sides:
`prod/embabel-smith-specsource` (`com.embabel.smith.specsource`). The format of what it resolves is
in [`agent-spec-format.md`](agent-spec-format.md); who consumes it is in
[`07-runtime.md`](07-runtime.md) and [`08-builder.md`](08-builder.md).

## The URI grammar

| Scheme | Meaning |
| --- | --- |
| `folder:<path>` | a directory whose immediate subdirectories are candidate packs |
| `git:<path-or-url>[@ref]` | a git repository, optionally pinned to a branch, tag or commit |

An unknown scheme is rejected with an actionable error naming the accepted ones. Parsing lives in
`SpecSource`; failures surface as `SpecSourceException`.

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

- Every invocation is **non-interactive** (`-c core.askpass=false`, explicit `-b` /
  `checkout <ref>`), so a clone can never hang waiting for a password prompt.
- The cache lives at `~/.smith/spec-sources/<uri-hash>` — one subdirectory per source, keyed by a
  SHA-256 of the URI, so two repositories can never collide. Re-resolving is `fetch` + `checkout`;
  a cache directory with no `.git` is treated as corrupt and replaced by a fresh clone.
- **A pinned ref is immutable.** Once resolved to a commit SHA it stays there. An unpinned source
  follows the branch tip.
- Local paths are cloned from disk, so the whole path is testable offline. The test suite uses
  `git init --bare` repositories rather than any network remote.

## Read-only, by design

The source layer **reads**. Publishing is a separate write path (`publish/` in the builder). Keeping
them apart avoids cache and write races when the studio runs both sides of the loop in one process.

## Content hashes

`PackContentHasher` computes a SHA-256 over the pack's format-owned files in sorted order, so the
hash is independent of directory listing order and changes only when content changes. The same value
is exposed on `ResolvedSpec`.

The hash is deliberately *the same quantity* the lifecycle's drift detection consumes, so the
improvement loop can compare a running pack against its recorded baseline without recomputing
anything. See [`10-self-improvement.md`](10-self-improvement.md).

## Publishing

Publishing writes a pack into a source. It is shared by all three build targets — an agent spec, a
skill folder, an N-pack process — because the publisher was generalized to *"a named directory of
files"* rather than staying spec-specific. That is why the collision gate and the no-push rule are
stated once here instead of three times.

| Behaviour | Rule | Why |
| --- | --- | --- |
| Target grammar | the same `folder:` / `git:` grammar as reads | one grammar for the whole system |
| Name collision | refused unless `--overwrite` is passed | a silent overwrite of someone else's pack is the worst possible default |
| Git publish | **commit, never push** by default; `--push` is opt-in | pushing is a shared-state action needing credentials and intent |
| Commit message | `smith: publish <unit> (baseline <score>)` | the baseline the improvement loop later compares against is in history |

Implementation: `publish/FolderPublisher`, `publish/GitPublisher`, `publish/PublishTarget`, with
`publish/CollisionException` for the gate.

## Failure behaviour

Failures are explicit and actionable, never a stack trace from the transport layer: unreachable
remote, unknown ref, missing folder, and an unknown scheme each get their own message naming what
was attempted and what would fix it.
