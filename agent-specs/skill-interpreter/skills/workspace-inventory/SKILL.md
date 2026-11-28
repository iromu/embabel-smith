---
name: workspace-inventory
description: Produces exact figures about a set of files - byte counts, line counts, content digests, duplicates - by running a script. Use when a task needs to know how many files there are, how big they are, which ones are identical, or when a count must be auditable rather than estimated.
---

# Workspace inventory

Counting files by eye is how a report goes wrong. The numbers below are cheap to compute
exactly and expensive to guess, so this skill does not guess them: it runs a script and
quotes what the script printed.

## What this skill needs from the host

Everything below comes from running a script, so it is available only where the host has
script execution switched on. Where it is not switched on, nothing in this skill is callable:
there is no script tool to call, and every figure has to be gathered by reading instead. That
is a property of the host, not of the task — the same question asked on a host with scripts
on gets auditable numbers, and on a host without them gets read-off ones.

## Workflow

1. Decide which files the question is about. If you do not already know, list the working
   directory once with the file tools.
2. Call the script tool that belongs to this skill — the one built from `scripts/inventory.py`
   — passing those files as its input files. Pass an extension such as `.md` as an argument to
   restrict the run to one kind of file.
3. Quote the figures the script printed. Do not add them up again, round them, or sanity
   check them yourself - if a figure looks wrong, say so and run the script again rather
   than replacing it with an estimate.
4. If no script tool is offered, say that plainly and report which figures you got by
   reading instead. Never present a read-off count as a scripted one.

## What the script reports

For each file, sorted by name: its size in bytes, its line count, and a short digest of its
content. Then, over the whole set: totals by file extension, groups of files that are
byte-identical, the largest file, and one digest over the whole set so two runs can be
told apart.

The report is deterministic. The same files produce the same output, so a second run that
differs means the files changed, not that the script is uncertain.

## If the script reports nothing staged

It says so and stops. That means no files reached it, not that there is nothing to count:
pass the files as input files and run it once more.
