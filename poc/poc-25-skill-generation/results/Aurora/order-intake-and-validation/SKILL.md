---
name: order-intake-and-validation
description: Handles the initial reception, data extraction, priority assignment, and multi-stage validation (regulatory and commercial) of incoming purchase orders from portal, EDI, or sales reps. Use this skill when processing new orders, managing missing information loops, or resolving validation holds.
---

# Order Intake and Validation

This skill governs the end-to-end processing of incoming purchase orders, from initial record creation through regulatory and commercial validation. The goal is to ensure only valid, authorized, and commercially viable orders proceed to fulfillment.

## Core Workflow

### 1. Order Reception and Data Extraction
Create an order record from the incoming source (Portal, EDI, or Sales Rep). Extract mandatory fields:
- Customer ID
- Product SKUs/IDs
- Quantities
- Delivery conditions (address, date, special handling)

**Action:** If any mandatory field is missing or ambiguous, immediately transition the order to the **Information Required** state (see Section 3).

### 2. Priority Assignment
Assign a priority level: **Critical**, **High**, **Standard**, or **Low**.

**Rule:** Base priority on clinical or operational justification, NOT solely on customer request or urgency claims.
- **Critical:** Life-threatening situations, immediate patient safety risks, or contractual SLA breaches with severe penalties.
- **High:** Operational disruptions, time-sensitive but non-life-threatening needs.
- **Standard/Low:** Routine replenishment, non-urgent requests.

### 3. Information Required Loop
If data is missing, initiate the clarification loop:
1. **Request Clarification:** Send a structured request to the customer/sales rep specifying exactly what is missing.
2. **Set Timers:**
   - **4 Hours:** Send a reminder if no response.
   - **24 Hours:** Escalate to the account manager or sales rep.
   - **72 Hours:** Auto-close the order **UNLESS** the priority is **Critical**.
3. **Handle Responses:**
   - If data is provided: Resume validation.
   - If data is insufficient: Repeat the loop or escalate if the customer is unresponsive.

**Constraint:** Critical orders must NEVER auto-close due to missing information. They remain open until resolved or manually cancelled by an authorized user.

### 4. Parallel Validation Checks
Execute the following checks simultaneously once data is complete:

#### A. Customer Authorization
- Verify customer registration status.
- Validate active license.
- Confirm the customer is permitted to purchase the specific product categories requested.

#### B. Regulatory/Product Validation
- Check legal distribution rights for the destination.
- Verify import/export permissions.
- Screen for active product recalls.
- Validate special handling requirements (e.g., cold chain, hazardous materials).

### 5. Handling Validation Outcomes
Process the results of the parallel checks:

- **All Checks Pass:** Proceed to Commercial Validation (Section 6).
- **Regulatory Failure:**
  - If the violation is clear (e.g., banned product, no license): Place order on **REGULATORY_HOLD** or reject specific lines.
  - If the result is uncertain or ambiguous: **Escalate to Compliance Officer** for manual review. Do not proceed until resolved.
- **Customer Authorization Failure:** Reject the order or specific lines if the customer is not authorized for those categories.

**Constraint:** Regulatory restrictions override all commercial decisions. A commercially valid order cannot proceed if it violates regulatory rules.

### 6. Commercial Validation
If regulatory checks pass, execute commercial checks:

1. **Credit Status:** Check if the customer is in good standing.
2. **Spending Limits:** Verify the order value does not exceed approved limits.
3. **Pricing & Contracts:** Validate pricing against active contracts and price lists.

**Handling Exceptions:**
- **Credit Exceptions:** If credit is insufficient or blocked, request **Finance Approval**. Do not proceed without it.
- **Prepayment Requests:** If credit is low but not blocked, request prepayment. Hold order until payment is confirmed.
- **Commercial Disputes:** If pricing or terms are disputed, escalate to the Account Manager for resolution.

### 7. Partial Fulfilment Logic
If only some lines pass validation:
- **Do not block valid lines** due to invalid ones.
- Split the order: Proceed with valid lines to fulfillment.
- Place invalid lines on hold or reject them with a clear reason code.
- Notify the customer of the partial acceptance.

## Gotchas

- **Critical Orders & Auto-Closure:** A Critical order will hang indefinitely in the 'Information Required' state if you rely on the 72-hour auto-close rule. You must manually intervene or ensure the escalation path is active for Critical items.
- **Regulatory vs. Commercial:** Never let a commercial override (e.g., "VIP customer," "strategic deal") bypass a regulatory hold. Regulatory holds are absolute.
- **Line-Level Validation:** Validation failures often apply to specific lines, not the whole order. Ensure your logic supports partial acceptance. Rejecting the entire order because one line is invalid is a failure of this process.
- **Uncertain Regulatory Results:** Do not guess on ambiguous regulatory checks. Always escalate to a Compliance Officer. Proceeding on a "maybe" is a compliance risk.
- **Priority Justification:** Customers often mark everything as "Critical." You must validate the clinical/operational justification. If the justification is weak, downgrade the priority to Standard/High to prevent resource misallocation.
- **Timer Management:** Ensure timers are reset if the customer provides partial information. A new request for clarification restarts the 4h/24h/72h cycle.
