---
name: threat-investigation-and-attribution
description: Executes deep-dive analysis for confirmed or suspected security threats by coordinating INTEL, FORENSICS, and SENTINEL-RISK inputs to determine root cause, affected assets, and final verdicts (Confirmed/Likely Compromise, False Positive, Inconclusive). Use when initiating or managing the investigation phase of a security incident, building attack timelines, or finalizing threat attribution.
---

# Threat Investigation and Attribution

This skill governs the deep-dive analysis of security threats. It focuses on evidence correlation, root cause analysis, and final verdict determination. It does **not** execute containment actions or make final regulatory decisions.

## Core Workflow

### 1. Initiate Investigation
- **Trigger**: A threat is confirmed or suspected.
- **Action**: Create a formal Investigation Plan.
- **Scope Definition**: Clearly define the suspected attack vectors, initial indicators of compromise (IOCs), and the business context.

### 2. Coordinate Parallel Specialist Tasks
Dispatch tasks to three distinct specialist streams. Track deadlines for each input.

- **INTEL (External Intelligence)**:
  - Request context on threat actors, TTPs (Tactics, Techniques, and Procedures), and campaign scope.
  - *Deadline Management*: Monitor for external intelligence delays.
- **FORENSICS (Digital Evidence)**:
  - Request log analysis, memory dumps, disk imaging, and artifact recovery.
  - *Deadline Management*: Monitor for evidence collection bottlenecks.
- **SENTINEL-RISK (Business Impact)**:
  - Request assessment of affected business processes, data sensitivity, and operational impact.
  - *Deadline Management*: Monitor for stakeholder availability.

### 3. Correlate Findings
- **Action**: Synthesize inputs from INTEL, FORENSICS, and SENTINEL-RISK.
- **Output**: Build a unified **Attack Timeline**.
  - Map technical events (Forensics) to threat actor behaviors (Intel) and business consequences (Sentinel-Risk).
  - Identify gaps in the timeline where evidence is missing.

### 4. Determine Root Cause and Scope
- **Root Cause**: Identify the initial entry point and the specific vulnerability or misconfiguration exploited.
- **Affected Assets**: List all systems, accounts, and data repositories touched by the attack.
- **Attack Vectors**: Define the methods used (e.g., phishing, SQL injection, lateral movement).

### 5. Finalize Verdict
Assign one of the four final conclusions based on the weight of evidence:

- **Confirmed Compromise**: Definitive evidence of unauthorized access or data exfiltration.
- **Likely Compromise**: Strong circumstantial evidence, but missing one critical piece of definitive proof (e.g., logs deleted, but behavior matches known attack pattern).
- **False Positive**: Evidence indicates benign activity or misconfigured alerts.
- **Inconclusive**: Insufficient evidence to determine compromise status. *Note: This is not a failure state; it is a valid outcome when evidence is unavailable.*

## Handling Evidence Limitations

When specialist inputs are unavailable or incomplete:
1. **Record Limitations**: Explicitly document what evidence is missing and why (e.g., "Logs rotated before collection," "Third-party vendor unresponsive").
2. **Assess Impact on Verdict**: Determine if the missing evidence prevents a "Confirmed" verdict. If so, downgrade to "Likely Compromise" or "Inconclusive" as appropriate.
3. **Do Not Block**: Proceed with the investigation using available data. Do not wait indefinitely for unavailable evidence.

## Escalation Rules

- **Deadline Breach**: If a specialist stream (INTEL, FORENSICS, SENTINEL-RISK) misses a critical deadline, escalate to the Incident Commander to adjust scope or accept limitations.
- **Critical Evidence Loss**: If evidence is destroyed or inaccessible due to negligence, escalate to Compliance/Legal for potential regulatory implications (note: this skill does *not* make the regulatory decision, only flags the issue).

## Gotchas

- **Correlation is not Causation**: Just because an alert fires and a user logs in at the same time does not mean they are related. Validate the timeline with forensic artifacts.
- **"Inconclusive" is a Valid Outcome**: Do not force a "Confirmed" or "False Positive" verdict if evidence is lacking. An "Inconclusive" verdict with documented limitations is more valuable than a speculative conclusion.
- **Sentinel-Risk is Not Forensics**: Do not rely on business impact assessments to prove technical compromise. Sentinel-Risk tells you *what* was hurt, Forensics tells you *how* it happened.
- **Intel Can Be Misleading**: External intelligence may suggest a specific threat actor, but if forensic evidence contradicts their known TTPs, trust the local forensic evidence over external attribution.
- **No Containment**: This skill analyzes and attributes. It does **not** isolate hosts, block IPs, or reset passwords. Those actions belong to the Containment phase.
