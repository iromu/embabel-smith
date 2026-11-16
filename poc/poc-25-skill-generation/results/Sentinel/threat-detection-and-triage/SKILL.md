---
name: threat-detection-and-triage
description: Ingests, normalizes, and classifies security events from network, endpoint, identity, cloud, and email sources to determine severity and routing. Use this skill when processing raw security telemetry, triaging initial alerts, correlating related events into cases, or requesting initial threat enrichment.
---

# Threat Detection and Triage

This skill governs the initial handling of security events. The goal is to reduce noise, accurately classify severity, and route valid threats to the appropriate investigation queue without performing deep forensic analysis or containment actions.

## Core Workflow

1.  **Ingest and Normalize**
    *   Collect raw events from all supported sources: Network, Endpoint, Identity, Cloud, and Email.
    *   Normalize field names and formats to the standard schema.
    *   **Deduplicate**: Identify and merge identical events occurring within the same short time window (e.g., < 5 minutes) from the same source and asset to prevent alert fatigue.

2.  **Initial Classification**
    *   Evaluate each normalized event against three factors:
        1.  **Baselines**: Does the activity deviate from established normal behavior for this asset/user?
        2.  **Asset Criticality**: Is the affected asset high-value (e.g., production DB, admin workstation)?
        3.  **Known Indicators**: Does the event match known IOCs (IPs, hashes, domains) or TTPs?
    *   Assign a severity level: **Informational**, **Low**, **Medium**, **High**, or **Critical**.

3.  **Decision Point: Dismiss, Alert, or Link**
    *   **Dismiss**: If the event is benign, matches a known false positive pattern, or is purely informational with no risk, close it immediately.
    *   **Create New Alert**: If the event is novel, suspicious, or matches a high-severity indicator, create a new alert.
    *   **Link to Existing Case**: If the event is related to an open case (same asset, same user, same IOC, or same attack chain), link it to that case rather than creating a new one.

4.  **Enrichment and Correlation**
    *   **Enrichment**: For new alerts classified as **Medium** or higher, submit an initial threat enrichment request to the **INTEL** team. Include raw event data and initial classification rationale.
    *   **Correlation**: Review new alerts against open cases. If multiple alerts share common attributes (e.g., same source IP targeting multiple assets, or same user account triggering multiple endpoint alerts), correlate them into a single case. Keep unrelated alerts separate.

## Severity Classification Rules

*   **Informational**: Routine system logs, successful logins from known locations, benign policy violations with no security impact.
*   **Low**: Minor deviations from baseline, failed logins from unknown locations (without success), low-confidence IOC matches.
*   **Medium**: Successful logins from suspicious locations, moderate-confidence IOC matches, policy violations with potential security impact, unusual data access patterns.
*   **High**: Confirmed IOC matches, lateral movement indicators, privilege escalation attempts, high-value asset compromise indicators.
*   **Critical**: Confirmed breach, ransomware behavior, data exfiltration, critical asset compromise, active exploitation.

## Escalation and Exceptions

*   **Immediate Escalation**: Any event classified as **Critical** must be immediately flagged for the Incident Response team. Do not wait for enrichment results.
*   **INTEL Dependency**: If INTEL enrichment is unavailable or delayed, proceed with classification based on local baselines and asset criticality. Mark the alert as "Pending Enrichment" and retry later.
*   **Ambiguous Cases**: If classification is unclear between Medium and High, err on the side of caution and classify as **High** to ensure it receives attention.

## Gotchas

*   **Deduplication is not Correlation**: Deduplication merges *identical* events (same source, same target, same action, same time). Correlation links *related* but distinct events (e.g., same attacker IP hitting different targets). Do not merge distinct attack steps into one event.
*   **Asset Criticality Drives Severity**: A "Low" severity event on a critical asset may be treated as "Medium" or "High" depending on context. Always check asset criticality before finalizing severity.
*   **Enrichment is Initial Only**: This skill only requests *initial* enrichment. Do not wait for deep threat intelligence analysis. If INTEL provides conflicting data, trust the local baseline and asset context for initial triage, but flag the discrepancy.
*   **Do Not Contain**: This skill does not perform containment (e.g., isolating hosts, blocking IPs). If containment is needed, escalate to the Incident Response team after creating the alert/case.
*   **False Positives are Not Dismissed Immediately**: If an event looks like a false positive but involves a high-value asset or critical user, do not dismiss it. Classify as Low/Medium and link to a case for review. Dismissal should be reserved for clearly benign, low-risk activity.
*   **Time Window Matters**: Deduplication windows are short. Events occurring minutes apart are likely distinct actions, not duplicates. Do not over-aggregate events into a single "case" if they represent distinct stages of an attack; keep them linked but separate for visibility.
