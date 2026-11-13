---
name: risk-assessment-and-prioritization
description: Evaluates business impact and incident severity to recommend P1-P4 priority levels. Use when assessing affected services, data sensitivity, financial exposure, or customer impact to determine incident priority and escalation needs.
---

# Risk Assessment and Prioritization

This skill governs the evaluation of business impact and incident severity. It determines the priority level (P1-P4) for incidents based on likelihood, impact, and business criticality, providing input to the ORCHESTRATOR for priority decisions and escalation.

## Core Workflow

1. **Identify Affected Assets and Services**
   - Determine which business services are impacted.
   - Identify the specific data types involved (PII, PHI, financial, intellectual property, etc.).
   - Assess the operational disruption level (complete outage, degraded performance, background noise).

2. **Evaluate Impact Dimensions**
   - **Data Sensitivity**: Classify the sensitivity of exposed or compromised data.
   - **Operational Disruption**: Quantify the effect on business continuity.
   - **Financial Exposure**: Estimate direct and indirect financial losses.
   - **Customer Impact**: Assess the number of affected customers and the severity of their experience.

3. **Collaborate on Regulatory Exposure**
   - If data sensitivity or jurisdiction suggests potential regulatory violations, immediately engage COMPLIANCE.
   - Incorporate COMPLIANCE feedback regarding regulatory exposure into the impact assessment.

4. **Determine Incident Severity (P1-P4)**
   - Synthesize likelihood, impact, and business criticality.
   - Apply the severity matrix (see Decision Points below).
   - **Critical Constraint**: If findings are low-confidence but involve critical assets, do not dismiss them. Treat them as high-priority pending further validation.

5. **Provide Input to ORCHESTRATOR**
   - Submit the recommended severity level and justification to the ORCHESTRATOR.
   - Flag any need for escalation based on the severity recommendation.

## Decision Points

### Severity Classification
- **P1 (Critical)**: Immediate business stoppage, major data breach, or severe regulatory violation. Requires immediate escalation.
- **P2 (High)**: Significant degradation of critical services, moderate data exposure, or high financial risk. Requires rapid response.
- **P3 (Medium)**: Limited impact on non-critical services, low data sensitivity, or manageable financial exposure. Standard response.
- **P4 (Low)**: Minimal impact, no data exposure, or negligible financial/customer impact. Routine handling.

### Handling Low-Confidence Findings
- **Rule**: If an assessment involves a critical asset (e.g., core banking system, primary customer database) but the evidence is inconclusive or low-confidence, **do not downgrade the severity**.
- **Action**: Assign a provisional high severity (P1 or P2) and flag for immediate forensic validation. Dismissing low-confidence findings on critical assets is prohibited.

### Regulatory Exposure Trigger
- **Rule**: If data sensitivity includes regulated data (PII, PHI, PCI) or if the incident crosses jurisdictional boundaries, escalate to COMPLIANCE.
- **Action**: Wait for COMPLIANCE input on regulatory exposure before finalizing the severity recommendation if the regulatory impact could change the priority level.

## Escalation Rules

- **To ORCHESTRATOR**: Always provide the recommended severity and rationale. If the recommended severity is P1 or P2, explicitly request escalation review.
- **To COMPLIANCE**: Engage immediately when regulatory exposure is suspected. Do not attempt to assess regulatory risk independently.

## Gotchas

- **Low-Confidence on Critical Assets**: A common failure mode is downgrading severity due to lack of definitive proof. If a critical asset is potentially affected, assume the worst-case scenario until proven otherwise. The cost of under-prioritizing a critical asset breach far exceeds the cost of over-prioritizing a false positive.
- **Regulatory Blind Spots**: Do not assume that technical containment resolves regulatory risk. Even if data is not exfiltrated, unauthorized access to regulated data may constitute a reportable incident. Always consult COMPLIANCE.
- **Financial Exposure Estimation**: Financial impact is not just direct loss. Include remediation costs, potential fines, and reputational damage in the assessment. Underestimating financial exposure can lead to under-prioritization.
- **Scope Limitation**: This skill does **not** execute technical containment or forensic collection. Do not attempt to gather technical evidence yourself. Rely on provided data and escalate to technical teams if evidence is insufficient for a confident assessment.
- **Customer Impact vs. Technical Impact**: A technical outage may have low customer impact if it occurs during off-hours, while a minor data leak may have high customer impact. Prioritize based on business and customer impact, not just technical severity.
