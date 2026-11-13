---
name: shipment-monitoring-and-quality-assurance
description: Manages real-time shipment monitoring, temperature excursion investigations, quality dispositions, and delivery receipt verification. Use when handling shipment tracking alerts, investigating environmental deviations, making release/quarantine decisions, or validating physical delivery conditions.
---

# Shipment Monitoring and Quality Assurance

This skill governs the lifecycle of a shipment from dispatch to final quality disposition. It covers continuous environmental monitoring, escalation of temperature excursions, investigation protocols, and strict delivery receipt verification.

## Core Workflow

### 1. Continuous Monitoring
Monitor the following parameters from dispatch until receipt:
- Location
- Transit time
- Temperature
- Humidity
- Device connectivity

### 2. Temperature Alert Management
Classify alerts based on severity and execute the corresponding protocol:

**Level 1 (Warning)**
- **Action:** Increase monitoring frequency.
- **Notification:** Notify Logistics team.
- **Status:** Shipment remains active; no hold applied.

**Level 2 (Excursion)**
- **Action:** Create a quality incident record.
- **Status:** Mark shipment as `POTENTIAL_EXCURSION`.
- **Constraint:** Prevent delivery release immediately.

**Level 3 (Critical)**
- **Action:** Declare a critical incident.
- **Notification:** Notify QA Management and Incident Management.
- **Constraint:** Suspend distribution of all affected inventory.

### 3. Excursion Investigation
When an excursion is confirmed or suspected, QA must perform the following:
1. **Gather Data:** Collect full sensor history, sensor calibration records, stability data, and transit information.
2. **Determine Disposition:** QA must select one of the following outcomes:
   - **Release:** Product is safe for distribution.
   - **Quarantine:** Product is held pending further review.
   - **Request Lab Assessment:** Product requires external testing.
   - **Reject:** Product is unsuitable for use.
   - **Partial Release:** Only specific lots or units are released.
3. **Approval Constraint:** No product may return to inventory without documented approval from QA.

### 4. Delivery Receipt Verification
Physical receipt is not complete until all verification steps are passed. Verify:
- Shipment identity matches order.
- Package count matches manifest.
- Recipient is authorized to accept delivery.
- Seal integrity is intact.
- Temperature indicators show no excursion.

**Decision Logic:**
- **If temperature indicators show excursion:** Enter `QUALITY_HOLD` status, even if the shipment is physically received.
- **If recipient is unauthorized:** The carrier retains custody. Do not accept the shipment.

### 5. Handling Delivery Discrepancies
If discrepancies are found during receipt:
1. **Record:** Log all damaged or missing items.
2. **Investigation:** Keep missing packages in an active investigation state.
3. **Notification:** Notify Order Management of the discrepancy.

## Escalation Rules
- **Level 1 Alerts:** Escalate to Logistics for increased oversight.
- **Level 2 Alerts:** Escalate to QA for incident creation and hold enforcement.
- **Level 3 Alerts:** Escalate to QA Management and Incident Management for critical incident declaration and inventory suspension.

## Gotchas
- **Carrier Status is Insufficient:** A carrier's 'delivered' status does not constitute completion. Physical receipt requires confirmation of condition (seals, temperature) and authorization.
- **Quality Holds Block Finance:** Any quantity under `QUALITY_HOLD` is blocked from financial reconciliation. Do not process invoices for held items.
- **Unauthorized Recipients:** If the person accepting the delivery is not on the authorized list, the carrier must retain custody. Do not force acceptance.
- **Excursion vs. Receipt:** A shipment can be physically received but still be in `QUALITY_HOLD` if temperature indicators show an excursion. Physical presence does not override quality status.
- **No Silent Returns:** Never return quarantined or rejected product to inventory without explicit, documented QA approval.
