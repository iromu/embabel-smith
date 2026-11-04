---
title: "Trust tiers and guardrails (POC 10) + install-time scanning (POC 21)"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "poc/poc-10-trust-tiers/src/main/java/com/embabel/smith/trust/TrustTier.java"
  - "poc/poc-10-trust-tiers/src/main/java/com/embabel/smith/trust/TrustTierPolicy.java"
  - "poc/poc-10-trust-tiers/src/main/java/com/embabel/smith/trust/InputSanitizerGuardRail.java"
  - "poc/poc-10-trust-tiers/src/main/java/com/embabel/smith/trust/TrustRunner.java"
  - "poc/poc-21-skill-security/src/main/java/com/embabel/smith/security/SkillSecurityScanner.java"
updated_at: "2026-11-04"
---

# Trust tiers and guardrails (POC 10)

POC 10 is the **security** half (doc 03 §2.1, doc 02 caveat #3 — the 26% vuln rate). It has two
parts: a **per-tier tool-group allowlist** that gates high-privilege skills (e.g. `shell`) behind a
trust tier, and a **globally-registered user-input guardrail** that blocks prompt injection before
the LLM runs.

## The tiers (`TrustTier`)

Each tier allows a set of tool-group roles; a skill may only run under a tier whose allowlist
covers every tool group the skill declares.

| Tier | Allowed tool groups |
| --- | --- |
| `T1_COMMUNITY` | none (instructions only, full isolation) |
| `T2_VERIFIED` | `search`, `web` (read-only) |
| `T3_PARTNER` | `search`, `web`, `file`, `shell` (write + MCP) |
| `T4_PLATFORM` | `*` (everything, including host execution) |

**`TrustTierPolicy`** enforces this: `declaredToolGroups(skill)` collects the distinct tool-group
roles a skill's actions declare, and `check(skill, tier)` returns whether the skill is allowed and
which groups are not. So the `shell-runner` skill (which declares `shell`) is **refused under
T1** and **allowed under T3**, while a plain skill (no tool groups) is allowed under T1. This is
the offline, pure half — the tier checks always run.

## The input guardrail (`InputSanitizerGuardRail`)

A `UserInputGuardRail` registered globally via `embabel.agent.guardrails.user-input` in
`application.yml`. It normalizes the input (lowercase, punctuation/whitespace stripped via
`normalize()`) and matches a set of known injection phrases (`ignorepreviousinstructions`,
`disregardtheabove`, `revealyoursystemprompt`, …) with `ValidationSeverity.CRITICAL`, which stops
the LLM operation before it runs. Because matching happens on the normalized string, it catches
obfuscated variants — extra spacing, punctuation, mixed case — that a plain substring match on the
literal marker would miss. `TrustRunner` distinguishes a normal goal achievement from a run
**blocked by a guardrail** (walking the cause chain for a `GuardRailViolation`).

## What it proves

Per-tier allowlists and a global guardrail work: a clean run is allowed, and a prompt-injection is
blocked with a CRITICAL `GuardRailViolationException`. The caveat (see [[what-the-pocs-prove]]):
the guardrail is a **normalized pattern matcher**, not a semantic detector — it now catches
obfuscated variants (spacing/punctuation/case and other known rephrasings) a plain substring match
missed, but a *novel* rephrasing outside the pattern set can still slip through. A blocking
(CRITICAL) guardrail triggers platform **retries** — the injection run retried ~5× over ~4 min
before surfacing. Seven offline tests pin the matcher (classic marker, obfuscated spacing,
punctuation, other phrases, clean pass). The live run is in `docs/04-poc-run-results.md` §5. See
[[runtime-adapter]] for the `shell` group this policy gates.

## The install-time complement (POC 21)

POC 21 ([[skill-security-scanning]]) is the **install-time** half of the same security hypothesis:
a static scanner vets a pack's declared prompts and tool groups *before* it is loaded, and maps
each pack to the minimum POC 10 trust tier. The scanner's "load at T3+" is exactly what POC 10
enforces at runtime. Both halves share the same honest limit — a normalized pattern matcher over
declared content, not a semantic detector. See `docs/04-poc-run-results.md` §7.3.
