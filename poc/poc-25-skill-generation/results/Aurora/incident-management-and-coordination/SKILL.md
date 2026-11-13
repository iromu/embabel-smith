---
name: incident-management-and-coordination
description: Manages the central incident register, severity classification, and cross-departmental coordination for critical operational failures. Use this skill when registering incidents (e.g., temperature excursions, recalls, cyber issues), classifying severity (P1-P4), enforcing escalation timers, coordinating multi-department responses, or verifying closure integrity.
---

# Incident Management and Coordination

This skill governs the lifecycle of critical operational failures, ensuring proper registration, severity classification, escalation, and verified closure. It acts as the central coordination point for cross-departmental incidents.

## Core Workflow

Follow this sequence for every incident:

1.  **Detection & Registration**: Log the incident in the central register immediately upon detection from any source (e.g., temperature excursion, recall, missing shipment, regulatory suspension, cyber/data integrity, carrier disruption).
2.  **Classification**: Assign a severity level (P1, P2, P3, or P4) based on the criteria below.
3.  **Assignment**: Assign the incident to the relevant department(s) for investigation and containment.
4.  **Containment**: Ensure immediate actions are taken to stop the spread or impact of the issue.
5.  **Investigation**: Departments conduct root cause analysis.
6.  **Corrective Action**: Define and implement steps to prevent recurrence.
7.  **Verification**: Confirm that corrective actions have resolved the root cause.
8.  **Approval**: Obtain formal approval for closure.
9.  **Closure**: Close the incident only after all documentation and approvals are complete.

## Severity Classification Rules

Classify incidents strictly according to these definitions:

*   **P1 (Critical)**: Patient safety risk or Major regulatory risk.
    *   *Action*: Immediate escalation required. Bypasses all normal acknowledgement timers.
*   **P2 (High)**: Significant disruption to operations.
    *   *Action*: Urgent investigation required.
*   **P3/P4 (Medium/Low)**: Minor issues or routine problems.
    *   *Action*: Follow normal/routine processing paths.

## Escalation Protocol

Enforce strict acknowledgement deadlines to prevent incidents from being ignored.

1.  **Monitor Acknowledgement**: Track whether the assigned department acknowledges the incident within the defined deadline.
2.  **First Escalation**: If unacknowledged within the deadline, escalate to the **Department Manager**.
3.  **Second Escalation**: If still unacknowledged, escalate to the **Incident Manager**.
4.  **P1 Exception**: P1 incidents **must not** wait for acknowledgement timers. Escalate immediately upon registration/classification if not instantly acknowledged.

## Multi-Department Coordination

When an incident affects multiple departments:

1.  **Central Coordination**: The **Incident Manager** coordinates the overall response and timeline.
2.  **Operational Responsibility**: Individual departments retain full operational responsibility for their specific actions and investigations.
3.  **Distinct Actions**: Ensure that while the response is coordinated, each department documents its own distinct actions and findings. Do not merge departmental responsibilities into a single ambiguous entry.

## Closure Integrity

An incident is **not** closed when symptoms disappear. Closure requires:

1.  **Documented Cause**: Root cause must be identified and recorded.
2.  **Corrective Action**: Specific actions taken to fix the root cause must be documented.
3.  **Evidence of Resolution**: Proof that the corrective action worked must be attached.
4.  **Formal Approval**: A designated approver must sign off on the closure.
5.  **Audit Trail**: All steps, communications, and approvals must be preserved in the register.

## Gotchas

*   **P1 Timer Bypass**: Never allow a P1 incident to sit in a queue waiting for a standard acknowledgement timer. If it is not acknowledged instantly, escalate immediately. Waiting for the timer to expire is a violation of protocol.
*   **Symptom vs. Cause**: Do not close an incident just because the immediate problem (symptom) is gone. If the root cause is not documented and fixed, the incident remains open.
*   **Shared Responsibility**: In multi-department incidents, do not let departments blame each other or assume the other side is handling it. The Incident Manager must ensure each department has a distinct, assigned action item.
*   **Approval is Mandatory**: Self-closure by the investigating department is invalid. Formal approval from the Incident Manager or designated authority is required to change the status to Closed.
*   **Source Agnostic**: Register incidents from *any* source. Do not filter out incidents because they came from a carrier, a cyber alert, or a regulatory notice; all are treated with the same registration rigor.
