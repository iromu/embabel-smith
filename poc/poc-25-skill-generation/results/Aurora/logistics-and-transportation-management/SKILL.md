---
name: logistics-and-transportation-management
description: Manages end-to-end logistics operations including carrier selection, booking, customs clearance, route deviation handling, and delay escalation. Use this skill when handling transportation provider selection, international shipment compliance, logistics incidents, or supply chain disruptions.
---

# Logistics and Transportation Management

This skill governs the selection of transportation providers, management of bookings, customs clearance for international shipments, handling of route deviations, and response to delivery delays.

## Core Workflow

### 1. Carrier Selection
Select transportation providers based on a multi-factor evaluation. **Lowest price is not the sole criterion; handling requirements are paramount.**

**Selection Criteria (in order of priority):**
1. **Approved Status:** Carrier must be on the approved vendor list.
2. **Handling Requirements:** Must meet specific needs (e.g., temperature control, hazardous materials).
3. **Route Availability:** Must service the required origin-destination pair.
4. **Transit Time:** Must meet the required delivery window.
5. **Reliability:** Historical performance metrics.
6. **Cost:** Evaluate only after all above criteria are met.

### 2. Booking Management
Request bookings from approved carriers. If the primary carrier is unavailable:
1. Search for alternative approved carriers.
2. Check alternative transit times.
3. Evaluate alternative Distribution Centers (DCs) if applicable.
4. Recalculate delivery dates based on new parameters.

**Escalation Rule:**
- If a **Critical** shipment booking fails on the first attempt, escalate immediately to **Logistics Management**.

### 3. Customs Clearance (International Shipments)
For international shipments, validate all compliance elements before dispatch.

**Validation Steps:**
1. Verify export/import authorizations.
2. Confirm product classification (HS codes).
3. Ensure all required permits are attached.

**Handling CUSTOMS_PENDING States:**
- Monitor for documentation requests from Compliance.
- Respond promptly to prevent shipment stagnation.
- **Hard Constraint:** Prevent redirection to unauthorized destinations. If a carrier attempts to reroute to an unapproved location, halt the shipment and investigate.

### 4. Route Deviation Management
Assess any deviation from the planned route based on impact on time, environment, customs, and security.

**Decision Matrix:**
- **Minor Deviation:** Record in the shipment log. No further action required.
- **Material Deviation:** Create a **Logistics Incident** for review.
- **Unsafe Deviation:** Trigger **QA Review** immediately.
- **Suspected Theft/Loss:** Trigger **Security/Incident Management** immediately.

### 5. Delay Management
Calculate delay severity and execute the corresponding response protocol.

**Severity Levels & Actions:**

| Severity | Definition | Action |
| :--- | :--- | :--- |
| **Minor** | Within acceptable tolerance | Monitor; no active intervention required. |
| **Significant** | Exceeds tolerance but not critical | 1. Check alternative routes.<br>2. Verify packaging validity (ensure integrity is maintained). |
| **Critical** | Clinical supply interruption or major breach | 1. Escalate to **Incident Management**.<br>2. Check for replacement stock availability.<br>3. Prepare emergency shipments. |

## Gotchas & Hard Constraints

- **Arrival vs. Delivery:** A carrier reporting "arrival" does **not** equal delivery. Delivery is only confirmed upon recipient acknowledgment. Do not close the shipment record based on carrier status alone.
- **Lost Shipments:** If a shipment is lost, **freeze all related records** immediately to prevent data corruption, then initiate a formal investigation. Do not attempt to manually adjust inventory until the investigation concludes.
- **Double Invoicing Prevention:** When issuing replacement shipments due to loss or critical delay, ensure the original invoice is flagged or voided appropriately. Replacement shipments must **not** result in double invoicing for the same goods.
- **Customs Redirection:** Never allow a shipment to be redirected to a destination not explicitly authorized in the customs documentation. This is a compliance violation.
- **Critical Booking Failure:** Do not attempt multiple retries for Critical shipments without escalating to Logistics Management after the first failure. Time is critical.
