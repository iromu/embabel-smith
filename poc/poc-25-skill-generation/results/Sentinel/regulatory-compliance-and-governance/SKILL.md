---
name: regulatory-compliance-and-governance
description: Assesses legal and regulatory obligations for security incidents, including data exposure evaluation, reporting deadlines, and compliance validation. Use when determining if an incident triggers mandatory notifications, preparing regulatory reports, or validating security operations against legal standards.
---

# Regulatory Compliance and Governance

This skill governs the assessment of legal and regulatory obligations arising from security incidents. It focuses on determining reporting requirements, evaluating data exposure, and ensuring compliance with organizational policies and legal standards. It does not perform technical investigation or containment execution.

## Core Workflow

1. **Trigger Assessment**: Initiate compliance assessment upon receipt of incident details indicating potential data exposure, policy violation, or regulatory impact.
2. **Evaluate Data Exposure**:
   - Identify the types of personal data involved (e.g., PII, PHI, financial data).
   - Determine the volume of affected records and the sensitivity of the data.
   - Assess whether the data was encrypted, anonymized, or otherwise protected at the time of exposure.
3. **Determine Reporting Requirements**:
   - Map the identified data types and incident characteristics to applicable regulations (e.g., GDPR, CCPA, HIPAA, local laws).
   - Identify mandatory notification thresholds and specific reporting bodies.
   - Calculate strict deadlines for notification based on the time of discovery or confirmation of the breach.
4. **Review Evidence Handling**:
   - Verify that evidence collection procedures followed by technical teams meet legal admissibility standards.
   - Ensure chain-of-custody documentation is complete and unbroken.
   - Confirm that no evidence has been altered or destroyed in a way that violates legal hold requirements.
5. **Validate Policy Compliance**:
   - Check if security operations (containment, eradication) adhered to organizational policies and legal standards.
   - Identify any deviations that could constitute a compliance failure independent of the initial breach.
6. **Prepare Regulatory Reports**:
   - Draft notification content required by regulators and affected individuals.
   - Include required elements: nature of the breach, categories of data affected, likely consequences, and measures taken.
7. **Coordinate Notifications**:
   - Schedule and execute mandatory notifications to regulators and affected parties within the determined deadlines.
   - Log all notifications sent, including timestamps and recipient acknowledgments.

## Decision Points

- **Is the data exposure confirmed?**
  - **Yes**: Proceed to determine reporting requirements.
  - **No**: Monitor for further technical confirmation; do not trigger mandatory notifications yet unless there is a high probability of exposure and a regulatory requirement to notify on suspicion.
- **Does the incident meet the threshold for mandatory reporting?**
  - **Yes**: Calculate deadlines and prepare reports immediately.
  - **No**: Document the rationale for non-reporting in the incident record for audit purposes.
- **Is the obligation clear and unambiguous?**
  - **Yes**: Proceed with standard reporting procedures.
  - **No**: Escalate to a human Compliance Officer for interpretation.

## Escalation Rules

- **Uncertain Obligations**: If the applicability of a regulation is unclear, or if the incident involves cross-border data transfers with conflicting legal requirements, escalate immediately to a human Compliance Officer.
- **Evidence Integrity Issues**: If evidence handling appears to violate legal standards (e.g., broken chain of custody), escalate to Legal Counsel and the Compliance Officer.
- **Deadline Conflicts**: If multiple regulations impose conflicting deadlines, escalate to the Compliance Officer to determine the most conservative approach.

## Gotchas

- **Discovery vs. Confirmation**: Regulatory clocks often start at the time of *discovery* of the breach, not the time of *confirmation* of the extent. Do not wait for full technical analysis to start the deadline countdown if the initial discovery indicates a likely breach.
- **Encryption Safe Harbor**: Data exposure may not trigger mandatory notification if the data was encrypted and the key was not compromised. However, you must verify the strength of the encryption and the security of the key management system before applying this exemption.
- **Third-Party Processors**: If the breach occurred at a third-party vendor, you are still responsible for assessing your own regulatory obligations. Do not assume the vendor will handle all notifications; verify their compliance status and your contractual obligations.
- **Evidence Admissibility**: Technical teams may prioritize speed over legal rigor. Always verify that evidence collection methods (e.g., memory dumps, log preservation) were performed in a way that preserves legal admissibility. A technically sound investigation can still fail legally if evidence is mishandled.
- **Silent Failures**: Not all compliance failures are breaches. Failure to follow internal security policies (e.g., not patching a known vulnerability) can be a compliance violation even if no data was exposed. Validate policy adherence separately from breach impact.
- **Jurisdictional Nuances**: "Personal data" definitions vary by jurisdiction. Data that is not considered personal in one country may be protected in another. Always assess the location of the data subjects, not just the location of the servers.
