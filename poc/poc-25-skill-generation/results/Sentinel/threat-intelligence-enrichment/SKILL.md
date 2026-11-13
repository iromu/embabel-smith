---
name: threat-intelligence-enrichment
description: Enriches security indicators with external threat intelligence context, validates IP/domain reputation, identifies known malware/vulnerabilities, and assesses emerging campaigns. Use when analyzing suspicious indicators, verifying threat reports, or providing new indicators to WATCHTOWER for detection rule improvements.
---

# Threat Intelligence Enrichment

This skill governs the process of augmenting security indicators with external context, validating their maliciousness, and managing the flow of intelligence to and from other security operations components.

## Core Workflow

1. **Indicator Intake**: Receive indicators (IPs, domains, URLs, file hashes, CVEs) from initial detection alerts or manual analyst requests.
2. **Contextual Enrichment**: Query multiple threat intelligence feeds and reputation services to gather external context.
3. **Validation & Classification**: Determine if the indicator is known malicious, suspicious, or benign based on aggregated data.
4. **Confidence Assignment**: Assign a confidence level to the finding based on source reliability and corroboration.
5. **Distribution**: 
   - Push verified new indicators to WATCHTOWER for detection rule updates.
   - Provide enriched context to INVESTIGATOR for case analysis.
6. **Feedback Loop**: Ingest confirmed attack data from INVESTIGATOR to refine future enrichment logic.

## Decision Points & Rules

### 1. Source Reliability Assessment
- **Verified Intelligence**: Data from trusted, high-fidelity sources (e.g., paid commercial feeds, government CERTs, verified internal telemetry). Treat as high confidence.
- **Unconfirmed Reports**: Data from open-source, community-driven, or single-source reports. Treat as low-to-medium confidence until corroborated.
- **Rule**: Never treat a single unconfirmed report as definitive proof of maliciousness. Require corroboration from at least one independent source or behavioral evidence before escalating to "Malicious" status.

### 2. Confidence Level Assignment
Assign one of the following confidence levels to every enriched indicator:
- **High**: Multiple trusted sources agree; or confirmed by internal INVESTIGATOR feedback; or matches known malware signatures with high fidelity.
- **Medium**: Single trusted source; or multiple low-confidence sources agree; or matches known patterns but lacks specific attribution.
- **Low**: Single low-confidence source; or ambiguous data; or recent emergence with no historical precedent.
- **Unknown**: No data found in any queried feed.

### 3. Handling Emerging Campaigns
- If an indicator is associated with a newly observed campaign (no historical data):
  - Flag as "Emerging Threat."
  - Assign "Medium" confidence initially.
  - Monitor for rapid adoption by other sources.
  - Do not auto-block; recommend WATCHTOWER create a "monitor-only" rule if volume is significant.

### 4. Interaction with WATCHTOWER
- **Action**: Submit new, verified indicators to WATCHTOWER.
- **Constraint**: Only submit indicators with "High" confidence or those explicitly confirmed by INVESTIGATOR.
- **Why**: Prevents false positives in detection rules. WATCHTOWER rules should be precise; enrichment provides the precision.

### 5. Interaction with INVESTIGATOR
- **Input**: Receive feedback on confirmed attacks (e.g., "This IP was confirmed as C2 in Case #123").
- **Action**: Update internal knowledge base with confirmed indicators. Elevate confidence of related indicators to "High."
- **Output**: Provide enriched context (reputation, malware family, campaign name) to INVESTIGATOR to speed up triage.

## Escalation & Exceptions

- **Conflicting Data**: If sources conflict (e.g., one says malicious, another says benign), default to the more conservative stance (treat as suspicious/medium confidence) and flag for manual review by a senior analyst.
- **No Data Found**: If no intelligence is found, do not assume benign. Mark as "Unknown" and recommend further behavioral analysis by INVESTIGATOR.
- **Critical Vulnerabilities (CVEs)**: If a CVE is identified, check for active exploitation in the wild. If active exploitation is confirmed, escalate to "High" confidence immediately and notify WATCHTOWER for urgent rule creation.

## Gotchas

- **A delivery is not done until the recipient confirms**: Enrichment is not complete when data is retrieved; it is complete when the confidence level is assigned and the data is successfully handed off to WATCHTOWER or INVESTIGATOR. Log the handoff.
- **Reputation is not static**: An IP/domain reputation can change rapidly. Always check the timestamp of the intelligence data. If data is older than 24 hours, treat it as stale and re-query if possible.
- **Do not make internal risk decisions**: This skill provides *context*, not *decisions*. Do not decide whether to block an IP or isolate a host. That is the responsibility of the SOC/IR team. Your output is "This IP is likely malicious with High confidence," not "Block this IP."
- **Unconfirmed reports are not facts**: A common error is treating a single blog post or tweet as verified intelligence. Always distinguish between *reported* and *verified*. If it is not verified, it is a hypothesis, not a fact.
- **WATCHTOWER feedback is critical**: If INVESTIGATOR confirms an attack, you *must* update your internal records. Failing to do so leads to repeated manual investigation of known threats.
- **False Positives in Feeds**: Commercial feeds can have false positives. If an indicator is marked malicious by a feed but INVESTIGATOR has previously cleared it, trust the internal INVESTIGATOR confirmation over the external feed. Flag the feed for quality review.
