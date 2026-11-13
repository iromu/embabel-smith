# Proposal

## Why

POC 25 showed that a business-process description compiled into an **Agent Skill** (a `SKILL.md` in
the Agent Skills format) measurably improves a general-purpose agent's handling of that process —
the with-skill pass rate beat the no-skill baseline in every cell (+25% to +58%). The builder today
only emits Embabel AgentSpec packs. This change adds the Agent Skill as a **second output target**,
validated by the with/without-skill lift, so the builder can package process knowledge as a portable,
loadable skill — not just an executable spec.

## What Changes

- **Builder**: a new `target` build option (`agent-spec` default | `skill`). The skill target runs a
  separate build path: generate `SKILL.md` (name fixed by the description; description and body
  model-supplied), validate the frontmatter, run the with/without-skill eval (LLM-as-judge against
  verifiable expectations), gate publication on the skill lift, and publish a skill folder
  (`SKILL.md` + `evals.json`).
- **Builder**: the skill path reuses the builder's publish (folder/git) and, where applicable, the
  security and lifecycle stages, so a published skill carries the same collision / no-push /
  lifecycle semantics as a published spec.
- A new capability spec `agent-skill-builder`.

## Capabilities

### New Capabilities

- `agent-skill-builder`: the builder generates an Agent Skill from a description, validates it by
  comparing with-skill versus no-skill performance, gates on the skill lift, and publishes a skill
  folder.

### Modified Capabilities

(none)

## Impact

- `prod/embabel-smith-builder`: a skill-generation + with/without-skill-eval + publish path, a
  `target` build option, and a `--target` CLI flag.
- `openspec/specs/`: a new `agent-skill-builder` spec.
