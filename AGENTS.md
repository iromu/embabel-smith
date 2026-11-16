# AGENTS.md

Guidance for AI agents working on this project.

---
## Working Principles

### Simplicity First

**Minimum code that solves the problem. Nothing speculative.**
**(This is the principle we care about most.)**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes,
simplify.

## Subagent Delegation

* Always delegate to specialized subagents rather than doing everything yourself. This is the primary productivity
multiplier.
* If you can select a model for the subagents, use `instruct`, `flash`, `tool`, etc. variants, with no thinking.
* Finally review the work done by the subagents.

## What to Read First

1. `README.md` — introduction, module table, run book, and the documentation index
2. `.wiki/index.md` — wiki with all the knowledge you need to know
3. `docs/index.md` — the durable reference; one page per durable concern
   (architecture, spec format, sources, runtime, builder, studio, improvement loop,
   POC evidence, backlog). Only read here if the wiki and README did not answer.

## Documentation status

The OpenSpec planning trees were consolidated into `docs/` and removed on 2026-11-15.
Do not go looking for `openspec/specs/` or a change folder for a capability — the
`docs/` page is the contract, and the code outranks it where they disagree. The
scaffolding still works (`openspec/config.yaml`, the CLI from `make setup`), so
starting a new planning change is still valid; it would edit these pages, not specs.

Durable rules worth knowing before editing: `poc/` is frozen evidence and product work
never touches it; no model call under `prod/` may bypass Embabel's `Ai` layer; a
generated spec is validated before it is written; publishing commits and never pushes
by default. Details and the why are in `docs/05-architecture.md`.

`make setup` installs the pre-commit hooks, including `make lint`. Checkstyle runs in
Maven's `validate` phase, so a style violation fails `make build` and `make test` —
and the reactor stops at the first failing module, hiding everything behind it.

A vocabulary change is a documentation change: the wiki cross-links 44 pages and is
published to GitHub Pages, so it needs a wiki lint and a re-publish afterwards.

---
