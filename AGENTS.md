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

1. `README.md` — introduction
2. `.wiki/index.md` — wiki with all the knowledge you need to know
3. `docs/index.md` — detailed information, wiki and README.md should link here, only read if there is something you could not find

---
