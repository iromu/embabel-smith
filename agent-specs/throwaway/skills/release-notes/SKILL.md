---
name: release-notes
description: Assembles release notes from a set of changed files - one bullet per change, a severity label on each, and the figures behind every claim. Use when a release is being assembled from files rather than from what someone remembers doing.
---

# Release notes from the files themselves

Release notes written from memory acquire claims nobody can check: "refactors the parsing
layer" when three files changed by hand. This skill writes the notes from the set of
changed files, so each bullet can name the files behind it and quote a figure someone else
could reproduce.

## What this skill needs from the host

Listing the working folder, so the changed set is seen rather than assumed. The script
below, which is available only where the host has script execution switched on. And a way
to write one file at the end; where no writer is offered, the notes are still produced -
see the last step.

## Workflow

1. List the working folder once and settle the set of files this release covers. If the set
   was given to you, use it as given. Do not widen it to files that merely look related.
2. Read `references/severity-rules.md`. That file holds the only labels a bullet may carry
   and the tie-breaks the labels need. Do not invent a label that is not in it, and do not
   carry over a label from a release you have written before.
3. Call the script tool built from `scripts/changes.py`, passing the changed files as its
   input files. It reports, per file, its size, its line count and which kind of file it
   is - source, documentation, test, configuration, other or unreadable - then totals and a
   digest over the whole set. Quote those figures. Do not count files again by eye, and do
   not restate a number the script printed as a rounder one.
4. Give each file one label from the rules file, then group the files by label. A file
   earns the label from what the rules file says to look at, not from how interesting it
   looks; when two labels fit, apply the tie-break in that file rather than picking.
5. Write one bullet per group, most severe first. Each bullet says what changed in plain
   terms, names the files behind it, and quotes the script's figure that sizes the claim.
   Then write the notes to `RELEASE_NOTES.md` in the working folder. Where no write tool is
   offered, say that in one line and give the notes in your reply instead - the notes are
   the deliverable, the file is not.

## What the notes look like

A short header naming the release and the file count the script reported. Then the bullets.
Then, only if something is unresolved, a final line naming what could not be decided from
the files and who has to decide it. No section for work that went smoothly.

## When the figures and the prose disagree

If the script says two files changed and the story needs fifty, the story is wrong: write
what the files show and name the disagreement. A script that ran and printed nothing means
nothing was passed to it, not that nothing changed - pass the files and run it once more.
