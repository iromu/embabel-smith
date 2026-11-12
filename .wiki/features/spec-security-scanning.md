---
title: "Install-time spec security scanning (POC 21)"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "poc/poc-21-agent-spec-security/src/main/java/com/embabel/smith/security/SpecSecurityScanner.java"
  - "poc/poc-21-agent-spec-security/src/main/java/com/embabel/smith/security/Finding.java"
  - "poc/poc-21-agent-spec-security/src/main/java/com/embabel/smith/security/ScanVerdict.java"
  - "poc/poc-21-agent-spec-security/src/main/java/com/embabel/smith/security/Severity.java"
updated_at: "2026-11-04"
---

# Install-time spec security scanning (POC 21)

POC 21 is the **install-time** complement to [[trust-tiers]] (POC 10's *runtime* policy). It
addresses doc 02 caveat #3 (26.1% vuln rate, prompt injection) and §6.8 ("audit before
installing"): an agent spec's static content is scanned for injection / exfiltration /
over-privilege risk **before** it is loaded. Fully **offline** — pure pattern analysis, no model.

## The five static rules

Each rule normalizes and matches an agent spec's declared prompts and tool groups:

| Rule | Severity | What it catches |
| --- | --- | --- |
| Prompt-injection in the spec's own prompt | CRITICAL | the spec hijacking the agent that loads it |
| Secret / credential exfiltration | CRITICAL | reading `api key` / `token` / `secret` and exfiltrating |
| Network reference | WARN | URL / `curl` / `fetch` in a prompt |
| High-privilege tool group | WARN | `shell` / `file` declared |
| Untrusted-input-to-shell chain | WARN | `UserInput` feeding a `shell` step |

## The verdict and tier mapping

Findings aggregate to a `ScanVerdict`: **BLOCK** on any critical, **REVIEW** on any warning, else
**SAFE**. Each pack is also mapped to the **minimum POC 10 trust tier** whose allowlist covers its
tool groups — so the scanner's "load at T3+" is exactly what POC 10 enforces at runtime.

## Result

Benign sample packs (`explainer`, `reference-heavy`, `compose-extract`, `compose-explain`) scan
**SAFE** at T1; `shell-runner` scans **REVIEW** at T3 (high-privilege tool + injection-to-shell
chain); a synthetic malicious pack (injection + API-key exfil + `curl` + `shell`) is **BLOCK**ed
with two criticals. Eleven offline tests pin every rule.

## Honest limit

Same as POC 10's guardrail: a normalized pattern matcher over *declared* content, not a semantic
detector. A novel rephrasing outside the pattern set can slip through, and a spec that smuggles
capability through a bundled `scripts/` file is beyond a static YAML scan. It is a complement to,
not a replacement for, sandboxing and dependency auditing. See [[what-the-pocs-prove]].
