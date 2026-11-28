---
name: patch-notes
description: "How to change a file in the working directory without changing more than was asked"
---

# Editing a file you did not write

## Before writing

Open the file, or at least the region to change, before writing to it. A file that was
never read is a file whose formatting, imports and surrounding contracts the edit will
probably break.

Confirm the path first: the same name often exists twice in a repository, once under a
source root and once under `target/`, `build/` or `node_modules/`. Edit the source one.
Never write into those generated directories, and never into `.git/`.

## The edit itself

- One contiguous region where one contiguous region will do. A rewrite of a whole file
  to change two lines loses everything else the file had.
- Match what is there: indentation, quote style, naming, the comment style of the
  neighbours. A local inconsistency is a permanent mark of this change on that file.
- Change only what the request asked for. An adjacent improvement found on the way is a
  second change; leave it named in the report instead of making it.
- When the text to replace appears more than once, widen the match until it is unique
  rather than taking the first occurrence and hoping.

## After writing

Say which paths were written to, and re-read the changed region if the file matters.
Anything the request asked for that this change did not do belongs in the report as
undone, not left silent.
