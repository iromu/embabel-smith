# SENTINEL
## Multi-Agent Autonomous Security Operations & Threat Response Platform

**Document Type:** Multi-Agent System and Process Specification  
**Version:** 1.0  
**System Classification:** Enterprise Security Operations  
**Architecture:** Distributed Multi-Agent Orchestration  
**Operating Model:** Autonomous execution with human oversight

---

# 1. System Overview

SENTINEL is an autonomous enterprise cybersecurity operations platform designed to detect, investigate, assess, contain, and respond to cybersecurity threats.

The system operates across an organisation's infrastructure, including:

- Cloud environments.
- Corporate networks.
- Employee endpoints.
- Identity and access management systems.
- Email infrastructure.
- Application servers.
- Databases.
- External threat intelligence sources.
- Security monitoring platforms.

SENTINEL uses multiple specialised AI agents rather than relying on one central agent to perform every task.

Each agent has its own responsibilities, process lifecycle, decision-making capabilities, and access permissions.

Agents communicate through a shared operational event system and a central orchestration service.

The system must support independent agent execution, parallel investigations, delegation, conflict resolution, and coordinated response.

The central orchestrator manages coordination but does not replace the specialist agents' responsibilities.

# 2. Agent Organisation

SENTINEL consists of eight specialised agents and one coordinating orchestrator.

## 2.1 ORCHESTRATOR — Central Coordination Agent

**Purpose:** Coordinate the overall security operation.

Responsibilities:

- Receive incoming security events.
- Determine which agents need to participate.
- Create operational cases.
- Assign investigation priorities.
- Coordinate parallel processes.
- Resolve agent task dependencies.
- Track process completion.
- Detect stalled workflows.
- Escalate unresolved decisions.
- Maintain the overall incident state.

The Orchestrator does not independently declare a security threat harmless or authorise high-impact containment actions.

It receives findings from specialist agents and coordinates the next steps.

## 2.2 WATCHTOWER — Threat Detection Agent

**Purpose:** Detect suspicious activity across the organisation.

Responsibilities:

- Monitor security event streams.
- Analyse authentication anomalies.
- Detect suspicious network activity.
- Identify abnormal user behaviour.
- Identify unusual system activity.
- Correlate events across multiple sources.
- Generate threat alerts.
- Assign preliminary severity.

WATCHTOWER operates continuously and can create security cases independently.

## 2.3 INVESTIGATOR — Threat Investigation Agent

**Purpose:** Determine what happened, how it happened, and what systems are affected.

Responsibilities:

- Investigate security alerts.
- Correlate related events.
- Build attack timelines.
- Identify affected accounts and devices.
- Determine likely attack vectors.
- Collect supporting evidence.
- Evaluate alternative explanations.
- Produce investigation reports.

INVESTIGATOR can request additional information from other agents.

## 2.4 INTEL — Threat Intelligence Agent

**Purpose:** Provide external threat intelligence and contextual analysis.

Responsibilities:

- Check threat intelligence feeds.
- Research suspicious IP addresses and domains.
- Identify known malware indicators.
- Identify relevant vulnerabilities.
- Assess emerging campaigns.
- Evaluate known attacker techniques.
- Identify relationships with previous incidents.
- Maintain threat intelligence records.

INTEL must distinguish verified intelligence from unconfirmed reports.

## 2.5 SENTINEL-RISK — Risk Assessment Agent

**Purpose:** Assess the potential business impact of detected threats.

Responsibilities:

- Identify affected business services.
- Determine data sensitivity.
- Estimate operational disruption.
- Assess regulatory exposure.
- Estimate financial impact.
- Determine potential customer impact.
- Recommend incident severity.
- Recommend containment priorities.

Risk assessments must account for business criticality rather than simply counting affected systems.

## 2.6 RESPONDER — Incident Response Agent

**Purpose:** Coordinate technical containment and remediation.

Responsibilities:

- Prepare containment plans.
- Isolate compromised endpoints.
- Disable compromised credentials.
- Block malicious indicators.
- Coordinate system remediation.
- Validate recovery.
- Maintain response records.

RESPONDER can execute pre-approved, low-impact actions.

High-impact actions require explicit authorisation.

## 2.7 FORENSICS — Digital Evidence Agent

**Purpose:** Preserve and analyse digital evidence.

Responsibilities:

- Collect relevant security logs.
- Preserve evidence.
- Record evidence provenance.
- Establish event timelines.
- Identify possible evidence tampering.
- Analyse affected files and processes.
- Maintain chain of custody.
- Produce forensic reports.

FORENSICS operates independently of the response process to ensure that containment does not destroy evidence.

## 2.8 COMPLIANCE — Governance and Regulatory Agent

**Purpose:** Ensure that security operations satisfy organisational policies and legal obligations.

Responsibilities:

- Evaluate regulatory reporting requirements.
- Identify potential personal data exposure.
- Assess notification deadlines.
- Review evidence handling.
- Validate authorisation requirements.
- Monitor policy compliance.
- Maintain regulatory documentation.
- Coordinate mandatory notifications.

COMPLIANCE must be consulted when an incident may involve personal data, regulated information, or reportable operational disruption.

# 3. Agent Communication Model

Agents communicate using structured operational messages.

Every message contains:

- Message identifier.
- Source agent.
- Destination agent or broadcast channel.
- Operational case identifier.
- Message type.
- Priority.
- Timestamp.
- Relevant findings.
- Supporting evidence references.
- Requested action.
- Deadline, if applicable.

Supported message types include:

- ALERT_CREATED.
- INVESTIGATION_REQUESTED.
- INVESTIGATION_COMPLETED.
- INTELLIGENCE_REQUESTED.
- INTELLIGENCE_UPDATED.
- RISK_ASSESSMENT_REQUESTED.
- RISK_ASSESSMENT_COMPLETED.
- FORENSIC_COLLECTION_REQUESTED.
- FORENSIC_COLLECTION_COMPLETED.
- CONTAINMENT_PROPOSED.
- APPROVAL_REQUESTED.
- APPROVAL_GRANTED.
- APPROVAL_REJECTED.
- CONTAINMENT_EXECUTED.
- RECOVERY_REQUESTED.
- RECOVERY_COMPLETED.
- INCIDENT_ESCALATED.
- INCIDENT_CLOSED.

Agents may communicate directly with one another when their responsibilities require it, but all operational messages must be recorded centrally.

An agent cannot silently modify another agent's findings.

If an agent disagrees with another agent, it must submit a conflicting assessment with supporting evidence.

# 4. PROCESS FLOW A — Continuous Threat Detection

**Primary agent:** WATCHTOWER  
**Supporting agent:** ORCHESTRATOR  
**Additional agents:** INTEL, INVESTIGATOR

This process runs continuously.

## 4.1 Event ingestion

WATCHTOWER receives events from:

- Network monitoring.
- Endpoint detection systems.
- Identity providers.
- Cloud audit logs.
- Email security systems.
- Application monitoring.
- Firewall logs.
- Vulnerability monitoring.

Events are normalised and assigned unique identifiers.

The agent checks whether the event is a duplicate of an existing event.

Duplicate events are linked to the original event rather than generating unnecessary new cases.

## 4.2 Initial classification

WATCHTOWER evaluates the event using:

- Event type.
- Source.
- Frequency.
- Historical baseline.
- User behaviour.
- Asset criticality.
- Known indicators.
- Previous alerts.

The event is classified as:

- INFORMATIONAL.
- LOW.
- MEDIUM.
- HIGH.
- CRITICAL.

If the event is informational, it is recorded and monitoring continues.

If suspicious, WATCHTOWER creates an alert and sends it to ORCHESTRATOR.

## 4.3 Threat enrichment

ORCHESTRATOR determines whether external intelligence is required.

If relevant, it delegates an intelligence task to INTEL.

INTEL checks the available intelligence sources and returns:

- Indicator reputation.
- Known campaigns.
- Related vulnerabilities.
- Confidence level.
- Supporting references.

WATCHTOWER incorporates the intelligence into the alert assessment.

## 4.4 Correlation

If several alerts involve related assets, identities, or indicators, ORCHESTRATOR groups them into a single security case.

If the events appear unrelated, separate cases are maintained.

If the correlation is uncertain, INVESTIGATOR receives a correlation task.

## 4.5 Alert disposition

The alert enters one of the following states:

- CONFIRMED_THREAT.
- SUSPECTED_THREAT.
- BENIGN_ACTIVITY.
- DUPLICATE.
- INSUFFICIENT_INFORMATION.

Confirmed and suspected threats are forwarded to the investigation process.

Benign activity is documented with the reason for dismissal.

Insufficient information triggers additional monitoring or investigation.

# 5. PROCESS FLOW B — Threat Investigation and Attribution

**Primary agent:** INVESTIGATOR  
**Supporting agents:** INTEL, FORENSICS, SENTINEL-RISK

This process begins when ORCHESTRATOR creates an investigation task.

## 5.1 Investigation planning

INVESTIGATOR receives:

- Original security alert.
- Affected systems.
- Initial threat classification.
- Available evidence.
- Relevant historical incidents.

It creates an investigation plan.

The plan identifies:

- Questions that need answering.
- Evidence required.
- Relevant assets.
- Potential attack vectors.
- Required specialist agents.
- Investigation deadlines.

## 5.2 Parallel investigation

Three independent tasks may begin simultaneously.

**Task A: External intelligence**

INTEL investigates:

- Known malicious infrastructure.
- Related attack campaigns.
- Known attacker techniques.
- Relevant vulnerabilities.
- Similar historical incidents.

**Task B: Digital evidence**

FORENSICS collects and analyses:

- Authentication records.
- System activity logs.
- Process execution history.
- Relevant network records.
- File modification history.
- Available endpoint evidence.

Evidence collection must preserve original records and their provenance.

**Task C: Business impact**

SENTINEL-RISK identifies:

- Critical affected systems.
- Sensitive information potentially exposed.
- Operational dependencies.
- Potential business disruption.
- Regulatory implications.

## 5.3 Evidence correlation

INVESTIGATOR waits for the required specialist findings.

If an agent fails to respond within its deadline:

1. ORCHESTRATOR checks the task status.
2. The task is retried where appropriate.
3. A different agent may be assigned.
4. If evidence remains unavailable, the investigation records the limitation.

INVESTIGATOR correlates all available findings and constructs an attack timeline.

## 5.4 Investigation conclusions

The investigation produces one of four conclusions:

- CONFIRMED_COMPROMISE.
- LIKELY_COMPROMISE.
- FALSE_POSITIVE.
- INCONCLUSIVE.

The conclusion includes confidence, supporting evidence, unresolved questions, and affected assets.

If a confirmed or likely compromise is identified, the case proceeds to risk assessment and response planning.

False positives are documented and returned to WATCHTOWER for detection-rule improvement.

Inconclusive cases remain open and may enter extended monitoring.

# 6. PROCESS FLOW C — Incident Risk Assessment and Prioritisation

**Primary agent:** SENTINEL-RISK  
**Supporting agents:** INVESTIGATOR, COMPLIANCE, ORCHESTRATOR

This process starts when a threat is confirmed or considered sufficiently credible to warrant risk assessment.

## 6.1 Business impact assessment

SENTINEL-RISK evaluates:

- Number of affected systems.
- Criticality of affected services.
- Data sensitivity.
- Potential data loss.
- Customer impact.
- Operational disruption.
- Financial exposure.
- Recovery complexity.

## 6.2 Regulatory assessment

COMPLIANCE independently evaluates:

- Whether personal data may be involved.
- Whether regulated information may have been exposed.
- Applicable reporting obligations.
- Relevant notification deadlines.
- Evidence retention requirements.
- Whether external legal or regulatory consultation is necessary.

## 6.3 Risk calculation

The agents provide their assessments to ORCHESTRATOR.

The incident is classified:

| Severity | Description |
|---|---|
| P1 Critical | Severe business, security, or regulatory impact |
| P2 High | Significant compromise or disruption |
| P3 Medium | Limited compromise with manageable impact |
| P4 Low | Limited exposure and minimal operational impact |

The classification considers likelihood, impact, confidence, and business criticality.

A low-confidence finding affecting a critical business service must not automatically be dismissed.

## 6.4 Priority decision

ORCHESTRATOR combines the assessments and determines the response priority.

If agents disagree on severity, ORCHESTRATOR requests clarification.

If disagreement remains, the higher credible severity is used temporarily until a human Incident Manager resolves the conflict.

P1 incidents are escalated immediately.

P2 incidents require urgent response planning.

P3 and P4 incidents enter normal response scheduling.

# 7. PROCESS FLOW D — Incident Response and Containment

**Primary agent:** RESPONDER  
**Supporting agents:** ORCHESTRATOR, INVESTIGATOR, FORENSICS, SENTINEL-RISK

This process coordinates technical actions.

## 7.1 Response planning

RESPONDER receives the confirmed investigation and risk assessment.

It generates a response plan containing:

- Proposed containment actions.
- Affected systems.
- Expected operational impact.
- Reversibility.
- Required permissions.
- Potential side effects.
- Recovery requirements.
- Required approvals.

Possible actions include:

- Disabling compromised accounts.
- Revoking sessions.
- Isolating endpoints.
- Blocking malicious network indicators.
- Disabling compromised application credentials.
- Restricting access to affected services.

## 7.2 Pre-containment evidence preservation

Before performing actions that could affect evidence, RESPONDER consults FORENSICS.

FORENSICS identifies the minimum evidence that must be preserved.

If evidence collection cannot be completed before containment, the Incident Manager decides whether immediate containment takes precedence.

Patient safety, critical service continuity, and ongoing compromise risk must be considered.

## 7.3 Approval assessment

The system categorises proposed actions.

**Category A: Pre-authorised actions**

Low-impact, reversible actions covered by existing security policies may execute automatically.

**Category B: Controlled actions**

Actions such as disabling an ordinary employee account require approval from the authorised Security Operations Manager unless an explicit emergency policy applies.

**Category C: High-impact actions**

Actions such as isolating a critical production environment or disabling privileged infrastructure require explicit human authorisation.

## 7.4 Containment execution

After the required approval is obtained:

1. RESPONDER executes the authorised actions.
2. Each action is recorded.
3. The affected system is checked.
4. Execution results are returned to ORCHESTRATOR.
5. INVESTIGATOR verifies whether the threat remains active.

If an action fails, RESPONDER evaluates an alternative approved action.

If no safe alternative exists, the incident is escalated.

## 7.5 Containment verification

INVESTIGATOR and RESPONDER independently verify the containment outcome.

Possible results:

- CONTAINMENT_SUCCESSFUL.
- PARTIALLY_CONTAINED.
- CONTAINMENT_FAILED.
- THREAT_REMAINS_ACTIVE.

If the threat remains active, the system returns to response planning.

If containment is successful, the incident proceeds to eradication and recovery.

# 8. PROCESS FLOW E — Digital Forensics and Evidence Management

**Primary agent:** FORENSICS  
**Supporting agents:** INVESTIGATOR, COMPLIANCE

This process can run independently of containment and recovery.

## 8.1 Evidence collection

FORENSICS receives an evidence collection request.

It determines:

- Evidence sources.
- Collection priority.
- Preservation requirements.
- Access permissions.
- Relevant time windows.

It collects authorised evidence and records its origin.

## 8.2 Evidence validation

Each evidence item is assigned:

- Unique evidence identifier.
- Source system.
- Collection timestamp.
- Collector identity.
- Integrity verification information.
- Chain-of-custody history.

If evidence integrity cannot be established, the evidence is marked UNVERIFIED.

It must not be presented as verified evidence.

## 8.3 Analysis

FORENSICS identifies:

- Suspicious execution activity.
- Unusual file changes.
- Possible persistence mechanisms.
- Authentication patterns.
- Lateral movement indicators.
- Potential data exfiltration.
- Signs of evidence manipulation.

## 8.4 Evidence reporting

FORENSICS generates a report for INVESTIGATOR and COMPLIANCE.

If new evidence identifies additional affected systems, INVESTIGATOR receives an investigation update.

If evidence suggests that a previously dismissed threat is genuine, ORCHESTRATOR reopens the case.

# 9. PROCESS FLOW F — Eradication, Recovery and Validation

**Primary agent:** RESPONDER  
**Supporting agents:** INVESTIGATOR, SENTINEL-RISK, COMPLIANCE

This process begins after successful containment.

## 9.1 Eradication planning

RESPONDER identifies the required remediation.

Possible tasks:

- Remove malicious files.
- Patch exploited vulnerabilities.
- Rotate compromised credentials.
- Remove unauthorised access mechanisms.
- Rebuild affected endpoints.
- Restore trusted configurations.
- Update security policies.

## 9.2 Parallel remediation

Where dependencies permit, remediation tasks are assigned to separate teams or execution agents.

For example:

- Endpoint remediation.
- Identity remediation.
- Cloud infrastructure remediation.
- Application remediation.

Each task reports completion independently.

ORCHESTRATOR waits for all mandatory remediation tasks before authorising recovery.

## 9.3 Recovery validation

INVESTIGATOR performs post-remediation verification.

SENTINEL-RISK evaluates whether restoring the affected service creates unacceptable residual risk.

COMPLIANCE checks whether required evidence and documentation remain complete.

If all checks pass, recovery is approved.

If validation fails, the system returns to eradication planning or containment.

## 9.4 Service restoration

RESPONDER coordinates service restoration.

Services are restored in order of business criticality and operational dependencies.

The system performs additional monitoring during a defined observation period.

If suspicious activity returns, the incident reopens and the response process is restarted.

# 10. PROCESS FLOW G — Regulatory Reporting and Governance

**Primary agent:** COMPLIANCE  
**Supporting agents:** INVESTIGATOR, FORENSICS, SENTINEL-RISK

This process begins when an incident may involve regulated data, mandatory reporting, or contractual notification obligations.

## 10.1 Reporting assessment

COMPLIANCE evaluates:

- Incident type.
- Affected jurisdiction.
- Data categories.
- Potentially affected individuals.
- Discovery timestamp.
- Applicable reporting requirements.
- Notification deadlines.

## 10.2 Reporting decision

The agent determines whether:

- No external notification is required.
- Internal notification is required.
- Customer notification is required.
- Regulatory notification may be required.
- Legal review is mandatory.

Uncertain reporting obligations must be escalated to a human Compliance Officer.

## 10.3 Report preparation

COMPLIANCE requests verified information from INVESTIGATOR and FORENSICS.

It prepares a draft report containing:

- Incident summary.
- Timeline.
- Affected systems.
- Known or suspected data exposure.
- Containment actions.
- Potential impact.
- Outstanding investigation items.

## 10.4 Approval and submission

A human Compliance Officer reviews the report.

If approved, the report is submitted through the authorised channel.

If rejected, it returns for revision.

The submission timestamp and confirmation reference are recorded.

## 10.5 Follow-up

If additional information becomes available, COMPLIANCE evaluates whether an updated notification is necessary.

Reporting obligations remain active until all required submissions and follow-ups are complete.

# 11. PROCESS FLOW H — Security Intelligence and Continuous Improvement

**Primary agent:** INTEL  
**Supporting agents:** WATCHTOWER, INVESTIGATOR, ORCHESTRATOR

This process operates continuously and independently of active incident response.

## 11.1 Intelligence ingestion

INTEL monitors:

- Threat intelligence feeds.
- Security advisories.
- Vulnerability disclosures.
- Malware intelligence.
- Known attacker infrastructure.
- Relevant industry security reports.

## 11.2 Intelligence validation

Each intelligence item is assessed for:

- Source reliability.
- Age.
- Confidence.
- Relevance.
- Supporting evidence.
- Potential false positives.

Unverified information is marked accordingly.

## 11.3 Internal relevance assessment

INTEL compares new intelligence against:

- Internal asset inventories.
- Existing vulnerabilities.
- Security alerts.
- Historical incidents.
- Network indicators.
- Application dependencies.

If an internal match is found, ORCHESTRATOR creates a proactive security assessment case.

## 11.4 Detection improvement

INTEL provides new indicators and techniques to WATCHTOWER.

WATCHTOWER evaluates whether existing detection rules should be updated.

Changes are tested before production deployment.

A detection rule must not be deployed automatically if it could create significant operational disruption.

## 11.5 Continuous feedback

INVESTIGATOR provides confirmed attack findings to INTEL.

INTEL updates its intelligence records.

WATCHTOWER receives relevant detection improvements.

The system records the relationship between the original intelligence, the detection, and the resulting incident.

# 12. Cross-Agent Coordination and Conflict Resolution

Agents may reach different conclusions.

For example:

- INVESTIGATOR identifies a probable compromise.
- INTEL reports that the external IP address has no known malicious reputation.
- FORENSICS identifies suspicious internal activity.
- SENTINEL-RISK identifies a critical affected service.
- COMPLIANCE identifies possible regulatory exposure.

ORCHESTRATOR must not automatically accept the most recent agent response.

It evaluates:

1. Agent responsibility.
2. Evidence quality.
3. Confidence.
4. Data freshness.
5. Supporting references.
6. Operational consequences.

If disagreement affects a high-impact decision, ORCHESTRATOR requests additional evidence or escalates to a human Incident Manager.

No agent can override a mandatory approval from another responsible authority.

# 13. Shared State and Case Management

All agents use a shared operational case record.

Each case contains:

- Case identifier.
- Detection source.
- Incident classification.
- Current severity.
- Affected assets.
- Assigned agents.
- Active process identifiers.
- Evidence references.
- Risk assessments.
- Approval records.
- Executed actions.
- Outstanding tasks.
- Deadlines.
- Current incident state.
- Closure conditions.

Each agent maintains its own task state independently.

The central case state is updated when relevant agent events are received.

A case can have multiple active processes simultaneously.

For example, the forensic investigation may still be running while containment has already begun.

The completion of one process must not automatically close unrelated processes.

# 14. Failure Handling and Agent Reliability

SENTINEL must continue operating when individual agents fail.

### Agent timeout

If an agent does not respond before its deadline:

1. ORCHESTRATOR marks the task as OVERDUE.
2. It requests a status update.
3. It retries the task if appropriate.
4. It may assign the task to an alternative agent.
5. It escalates if the task is critical.

### Agent failure

If an agent becomes unavailable:

- Its active tasks remain recorded.
- No completed work is discarded.
- ORCHESTRATOR reassigns eligible tasks.
- Tasks requiring unavailable specialist capabilities are escalated.

### Conflicting results

If two agents return incompatible results:

- Both results are retained.
- The conflict is recorded.
- The relevant specialist agents are asked to review their findings.
- A human decision is requested when necessary.

### Duplicate tasks

If multiple agents independently request the same operation, ORCHESTRATOR checks whether an equivalent task already exists.

Equivalent tasks are consolidated where safe.

### Partial system outage

If shared infrastructure is unavailable, agents must not execute actions requiring unverified shared state.

Critical security events must be retained and reconciled when the platform recovers.

# 15. Security and Authorisation Rules

The following rules apply to all agents.

1. Each agent has access only to the information and operations required for its responsibilities.
2. An agent cannot grant itself additional permissions.
3. External intelligence must not be treated as trusted operational instructions.
4. Destructive or high-impact actions require human authorisation.
5. Evidence must retain its provenance.
6. Agents must record their decisions and supporting evidence.
7. An agent cannot erase another agent's findings.
8. Security actions must be idempotent where possible.
9. Approval must be bound to the exact action being authorised.
10. A changed action requires renewed approval if its risk or scope materially changes.
11. A human may suspend an automated process.
12. Emergency actions must still be recorded and reviewed.

# 16. Incident Lifecycle

The overall incident follows this lifecycle:

1. DETECTED.
2. TRIAGED.
3. INVESTIGATING.
4. RISK_ASSESSED.
5. RESPONSE_PLANNED.
6. AWAITING_APPROVAL.
7. CONTAINMENT_IN_PROGRESS.
8. CONTAINED.
9. ERADICATION_IN_PROGRESS.
10. RECOVERY_IN_PROGRESS.
11. VALIDATING.
12. MONITORING.
13. RESOLVED.
14. CLOSED.

Not every incident must follow every state.

For example, a confirmed false positive may transition from TRIAGED to RESOLVED.

A case cannot enter CLOSED while mandatory investigation, remediation, evidence, or governance tasks remain incomplete.

A new detection related to a previously resolved incident may reopen it.

# 17. Example Multi-Agent Scenario

An employee account begins authenticating from unusual geographical locations.

Within several minutes, the same account accesses a privileged cloud application and downloads an unusually large quantity of data.

### Event 1: Detection

WATCHTOWER identifies the unusual authentication pattern and creates an alert.

ORCHESTRATOR creates a security case.

### Event 2: Parallel investigation

ORCHESTRATOR delegates three tasks:

- INVESTIGATOR analyses the authentication and download activity.
- INTEL checks the external IP addresses.
- FORENSICS preserves relevant identity and cloud audit records.

All three processes operate independently.

### Event 3: Conflicting findings

INTEL reports that the IP address has no known malicious reputation.

FORENSICS identifies that the authentication pattern differs substantially from the employee's normal behaviour.

INVESTIGATOR identifies that the account accessed an unusual administrative resource.

ORCHESTRATOR requests a consolidated assessment.

### Event 4: Risk assessment

SENTINEL-RISK identifies that the affected cloud application contains sensitive customer records.

COMPLIANCE determines that a potential personal data exposure requires formal assessment.

The incident receives P1 severity.

### Event 5: Containment planning

RESPONDER proposes:

- Revoking the account's active sessions.
- Temporarily disabling the account.
- Rotating its credentials.
- Restricting the affected cloud application.

FORENSICS confirms that the essential evidence has been preserved.

### Event 6: Human approval

Disabling the account requires authorisation because it belongs to a privileged administrator.

The Incident Manager approves the proposed action.

RESPONDER executes the approved actions.

### Event 7: Verification

INVESTIGATOR checks whether further suspicious activity is occurring.

WATCHTOWER increases monitoring of related accounts.

FORENSICS continues analysing the downloaded data and authentication records.

### Event 8: Eradication

RESPONDER coordinates credential rotation and access-policy remediation.

Separate infrastructure remediation tasks run in parallel.

### Event 9: Regulatory assessment

COMPLIANCE receives the forensic findings.

It prepares a notification assessment for the Compliance Officer.

The officer determines the applicable notification requirements and approves any required submission.

### Event 10: Recovery

The administrator's account is restored only after identity verification and security checks.

SENTINEL-RISK confirms that the remaining operational risk is acceptable.

WATCHTOWER continues enhanced monitoring.

### Event 11: Closure

INVESTIGATOR confirms that the attack path has been addressed.

FORENSICS completes its evidence report.

COMPLIANCE confirms that governance obligations have been fulfilled or formally transferred to ongoing follow-up.

ORCHESTRATOR verifies that all mandatory tasks are complete.

The incident is closed.

# 18. System Completion Conditions

SENTINEL must distinguish between:

- Agent task completion.
- Individual process completion.
- Incident resolution.
- Incident closure.

An agent completing its task does not mean that the entire process is complete.

A process completing does not mean that the incident is resolved.

An incident is resolved only when the threat has been addressed and the required recovery validation is successful.

An incident is closed only when:

1. All mandatory specialist investigations are complete.
2. Containment has been verified.
3. Eradication and recovery are complete where applicable.
4. Required evidence is preserved.
5. Mandatory governance obligations are satisfied or formally transferred.
6. Outstanding approvals are resolved.
7. The Incident Manager approves closure.

# 19. Expected System Characteristics

SENTINEL must function as a distributed multi-agent system.

It is expected to demonstrate:

- Eight autonomous specialist agents.
- One central orchestration agent.
- At least eight distinct process flows.
- Parallel agent execution.
- Inter-agent delegation.
- Agent-to-agent communication.
- Event-driven process activation.
- Conditional routing.
- Human approval gates.
- Time-based escalation.
- Shared case state.
- Independent task state.
- Concurrent incident handling.
- Agent failure recovery.
- Conflicting assessment resolution.
- Cross-process dependencies.
- Feedback loops.
- Process reopening.
- Independent completion criteria.

The Orchestrator coordinates the system but must not become a bottleneck through which every minor decision must pass.

Specialist agents should execute their assigned responsibilities independently and report meaningful events to the orchestration layer.

The architecture must support multiple simultaneous incidents, each with its own independent lifecycle and participating agents.

**End of specification.**
