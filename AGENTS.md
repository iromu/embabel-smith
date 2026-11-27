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
0. `graphify-out/graph.json` Before grepping, globbing, or reading raw source files to answer architecture, dependency, or "how does X work" questions, check if `graphify-out/graph.json` exists.
1. `README.md` — short introduction: what the project does, what ships, one command to run it
2. `.wiki/index.md` — wiki with all the knowledge you need to know
3. `docs/index.md` — the durable reference; one page per durable concern
   (architecture, spec format, sources, runtime, builder, studio, improvement loop,
   POC evidence, backlog, and the run commands). Read the README for orientation, then here:
   the README is an overview and deliberately holds no detail.

---

## graphify
This project has a knowledge graph at `graphify-out/` with god nodes, community structure, and cross-file relationships.

Rules:
- ALWAYS read `graphify-out/GRAPH_REPORT.md` before reading source files, running searches, or answering codebase questions.
- Use CLI commands like `graphify query`, `graphify path`, or `graphify explain` for cross-module relationship questions.
- Update the graph with `graphify update .` after modifying code.
