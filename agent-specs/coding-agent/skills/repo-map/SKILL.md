---
name: repo-map
description: "Where things live in an unfamiliar working directory and what to open first"
---

# Orienting in the working directory

Work from the top down and stop as soon as the request's own path is in hand. Two
openings is the budget; a third is only worth it if the first two disagreed.

## Order to look in

1. `README.md` at the root, and `docs/index.md` if that root listing names one — these
   say what the directory is for and where the durable material sits.
2. The build file at the root: `pom.xml`, `build.gradle`, `package.json`, `pyproject.toml`
   or `Makefile`. Whichever exists names the module or package directories, which is the
   fastest way to learn the layout without walking it.
3. The source roots those files point at — commonly `src/main/`, `src/`, `app/`, or the
   per-module subdirectories of a repository root. List a directory before opening
   anything inside it.
4. `docs/` for explanation, `test/` or `src/test/` for the behaviour a change has to
   keep. A test that names the thing the request names is usually the exact file wanted.

## What to skip

Do not open, and do not list, anything under `target/`, `build/`, `dist/`,
`node_modules/`, `.git/`, or `out/` — those are generated copies, and a hit there reads
as a second place the same thing lives when it is not.

## Reporting

Report paths relative to the working directory, one per line, each with one clause on
what it holds. Prefer the narrowest path that answers: a named function in a named file
beats a directory listing. If the request's subject is nowhere to be found, say which
roots were searched rather than guessing at a path.
