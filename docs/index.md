# Embabel Smith — Documentation

Documents that research, ground, synthesize, and report a GOAP-based agent builder on the Embabel
platform (v1.5.4). Pages 01–04 are the design history and the POC record; 05–13 and the unnumbered
pages are the current reference, and each claim in them is checkable against the tree. Where a
history page and a reference page disagree, the reference page and the code win.

## Reference — how the system works now

- [The agent spec format](agent-spec-format.md) — the on-disk unit of capability: a directory of pure
  YAML, its six invariants, and the multi-pack process spec.
- [05 — Architecture and build invariants](05-architecture.md) — module map, extract-don't-depend and
  the enforcer ban, the POC freeze, the gating taxonomy, the `Ai`-only model layer, cross-cutting
  invariants, local state, build entry points.
- [06 — Spec sources and publishing](06-spec-sources.md) — the `folder:` / `git:` grammar, discovery,
  the git cache and ref pinning, content hashes, and the publish rules stated once for all targets.
- [07 — The no-code runtime](07-runtime.md) — ingest and run with zero compilation, trust tiers, the
  trace requirement and its pluggable store, the CLI.
- [08 — The builder](08-builder.md) — the generation techniques and why single-shot won, the
  validate-before-write invariant, eval and refinement bounds, the skill and process targets, and the
  upstream limit that keeps the `embabel` technique from publishing.
- [09 — The studio](09-studio.md) — the route table, the two rules the frontend is built on, the
  process workspace, hot-swappable storage, and what was deliberately left POC-only.
- [10 — The self-improvement loop](10-self-improvement.md) — detect, refine, gate, publish; gate
  order; strict-baseline rationale; what is advisory in v1.
- [11 — Spec-selection POCs](11-spec-selection-pocs.md) — the shared benchmark methodology and gating
  for POCs 17, 19 and 23, and the caveats that must travel with any number taken from them.
- [12 — Backlog](12-backlog.md) — every known open or deliberately-accepted gap, with the reason and
  the check that confirms it is still open.
- [13 — JavaScript for skill scripts](13-skill-script-javascript.md) — the surface a generated skill's
  `scripts/*.js` is given, the portability subset that runs unchanged under both the in-JVM GraalJS
  engine and `node`, and the idioms that stay on it.

## Research and design

- [01 — Embabel Agent Builder: Research & Design](research/01-embabel-agent-builder-research.md) —
  what Embabel is and already ships: the architecture, the decisive evidence (verified against the
  1.5.4 source), the system design, and implementation code samples. Verdict: feasible — about 70% of
  the pieces exist; the missing piece was the builder UI itself.
- [02 — Agent Skill Research](research/02-skill-research.md) — the "skills, not agents" paradigm: an
  arXiv-primary survey (progressive disclosure, composable/versioned/portable skills, extending a
  general agent without retraining) plus the advanced layer — retrieval at scale, self-evolving skill
  libraries, security and trust governance.
- [03 — Spec-First Agent Builder: Design](03-embabel-spec-agent-builder-design.md) — the synthesis:
  how the "specs, not agents" paradigm maps onto Embabel's primitives, and the revised spec-first
  architecture (Spec Registry, Spec Composer, Runtime Adapter, GOAP planning over ingested specs).

## Results

- [04 — POC Conclusions vs. the Theory](04-poc-run-results.md) — the POC suite (the 2026-11-02 run,
  fully re-run 2026-11-03, extended through POC 26) read against docs 01–03: what each proved, what
  remains unproven, the LLM-authored-quality caveat with its refinement fix, and the shared results
  sections (§7) that are the authoritative numbers for selection at scale, the runtime bridges,
  security, and skill scripts.

## Where knowledge lives

Three layers, deliberately not merged. `README.md` is the short introduction and overview; the run
commands live in these pages — [`05-architecture.md`](05-architecture.md) for the build and test entry
points and [`04-poc-run-results.md`](04-poc-run-results.md) §8 for re-running a POC. These pages are
the durable reference. [`.wiki/index.md`](../.wiki/index.md) is the orientation map with 46 pages of
cross-links (47 markdown files under `.wiki/` — the index is the one file no link names), published to
GitHub Pages. The `poc/poc-N-*/README.md` files are per-claim evidence and
are frozen. No planning tree is part of that stack: the OpenSpec specs and change folders were folded
into these pages and purged, and `12-backlog.md` §Re-creating a planning change says what remains and
how to tell whether another purge is owed.
