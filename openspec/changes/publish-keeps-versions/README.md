# publish-keeps-versions

Stop the improvement loop destroying the artifact it is trying to improve. Today
`LoopPublishPolicy.publish` hard-codes `overwrite = true`, and `FolderPublisher` answers an overwrite by
deleting the previous directory — so the loop overwrites the very baseline it is supposed to compare the
next candidate against. Ruled on 2026-11-19: **neither the flag nor the documented refusal is right,
because the collision should not happen.** A republish keeps a versioned copy in the database, and where
it writes a folder, that folder is git-managed rather than overwritten.

What the investigation found changes the size of this change. **Half the ruling is already built.**
`GitPublisher` clones into a temporary work dir, commits there, and reports a durable location
(`git:<url>#<name>`), so publishing the same name twice to a git source already keeps both versions in
history — `overwrite` there deletes a directory *inside a throwaway clone*, not the artifact. The gap is
that the loop's publisher defaults to a folder in the system temporary directory, where overwrite really
does delete. And **the database cannot hold a version today for a structural reason**: `PackEntity`'s
primary key is the pack name, so a second save of the same pack replaces the first row rather than adding
one.

Two consequences worth reading before the tasks. A versioned store needs a key that is not the name, and
whatever reads `packs` by name has to say which version it means — settled: **a name means its highest
version**, because the loop only publishes candidates that beat a baseline, so newest is best by
construction. And `LoopPublishPolicy.canPublish()` already refuses a git source when pushing is off,
because the commit would die with the temporary clone — so "use git" for the loop is not a one-line
default change, it interacts with the push setting that `loop-publish-push` just settled.

**No phase-1 spike is required.** Every load-bearing claim here was read out of the shipped classes, and
the two that could have been wrong — whether the git path preserves history, and whether the `packs`
table can hold two versions — were checked in the source rather than assumed. Sections 2 onward are
implementable now.
