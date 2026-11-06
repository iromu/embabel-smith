# Embabel Agent Builder — Documentation

Documents that research, ground, synthesize, and report a skills-first agent
builder on the Embabel platform (v1.5.4). Read in order:

## Research & design

- [01 — Embabel Agent Builder: Research & Design](01-embabel-agent-builder-research.md) — what Embabel
  is and already ships: the architecture, the decisive evidence (verified against the 1.5.4 source),
  the system design, and implementation code samples. Verdict: feasible — ~70% of the pieces exist;
  the missing piece is the builder UI itself.
- [02 — Agent Skills Research](02-agent-skills-research.md) — the "skills, not agents" paradigm: an
  arXiv-primary survey of the agent-skills literature (progressive disclosure, composable/versioned/
  portable skills, extending a general agent without retraining) plus the "advanced" layer —
  retrieval at scale, self-evolving libraries, and security/trust governance.
- [03 — Skills-First Agent Builder: Design](03-embabel-skills-agent-builder-design.md) — the
  synthesis: how the "skills, not agents" paradigm maps onto Embabel's primitives (exported goals,
  tool groups, MCP), and the revised skills-first builder architecture (Skill Registry, Composer,
  Runtime Adapter, with GOAP as the skill router).

## Results

- [04 — POC Conclusions vs. the Theory](04-poc-run-results.md) — the 2026-11-02 run of all
  twenty-one POCs (full re-run 2026-11-03) read against docs 01–03: what each POC proved (no-compile
  ingestion, skill composition, CLI invocation, retrieval at scale, eval-guided refinement, trust
  tiers, progressive disclosure, remote/MCP + remote-action invocation, the skill Composer + visual
  builder UI, GLiNER2 transformer skill classification, skill lifecycle / drift detection, small-LLM
  skill selection, the real semantic `EmbeddingService`, and install-time skill security scanning),
  what remains unproven/unbuilt, the confirmed LLM-authored-quality caveat (with its proven
  refinement fix), and the shadowing remedy.

## Reference

- [The skill-pack format](skill-pack-format.md) — the on-disk unit of capability: a directory of pure
  YAML (`manifest.yml`, `goal.yml`, `steps/*.yml`, `types/*.yml`), owned by `smith-common` and mapped
  onto Embabel's runtime types by the POC modules.
