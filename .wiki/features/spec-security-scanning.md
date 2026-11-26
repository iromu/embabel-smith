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
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/security/AgentSpecSecurityScanner.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/security/Finding.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillBuilderService.java"
updated_at: "2026-11-24"
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

## What the builder added to it

This scanner vets documents. The prod scanner vets one artifact this POC never saw: the JavaScript a skill
publishes. `security/AgentSpecSecurityScanner.scanSkill(name, texts, scriptsByPath)`
(`prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/security/AgentSpecSecurityScanner.java:190`)
takes the scripts keyed by the path each publishes to, iterates them through a `TreeMap` (`:192`) so two
identical builds print the same report, and reads each as code rather than as text. Three CRITICAL codes
carry it — `SCRIPT_HOST_ACCESS`, `SCRIPT_NETWORK`, `SCRIPT_PATH_ESCAPE`
(`prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/security/Finding.java:49,56,63`) —
fired by marker lists for the module loader and host namespaces (`java.type(`, `require(`, `child_process`,
`globalthis.`, `eval(`), for network APIs (`fetch(`, `xmlhttprequest`, `websocket`, `http.request`) and
for the parent-directory path (`../`) (`AgentSpecSecurityScanner.java:79-91`), emitted per script at
`:280-284` with the evidence naming the file and the marker that hit (`:306`). Matching there is lowercased
but deliberately *not* normalized the way prompt text is, on the ground that these are API names: an
obfuscated `f e t c h` is not a call the engine can make either.

The call site is `skill/SkillBuilderService.java:283`, at stage 7 of the skill build — after the two eval
arms and the lift between them (`:238`) and before the lifecycle stamp and the publish (`:312`, `:316`) —
so a script reaching for the host, for the network, or for a path above the skill's own folder blocks the
build inside the builder rather than being discovered by the reader's runtime
(`docs/08-builder.md:362-366,395`). A scriptless skill scans to exactly the report the text-only call
produced, which is what keeps this an addition rather than a replacement.

## Honest limit

Same as POC 10's guardrail: a normalized pattern matcher over *declared* content, not a semantic detector,
and a novel rephrasing outside the pattern set can slip through. Read that as the limit of this POC's
static YAML scan specifically. The hole it left — a spec smuggling capability through a bundled
`scripts/` file — is closed for the builder by the script half above, and closed in the same static way:
three marker lists over script source, so the same class of limit applies there too, against API names
rather than against phrases. What neither scan closes is a script that stays inside the sandbox's promised
surface and computes something wrong; that is a quality problem, and the enforcement that bites is the
engine's own ([[trust-tiers]]). It is a complement to, not a replacement for, sandboxing and dependency
auditing. See [[what-the-pocs-prove]].
