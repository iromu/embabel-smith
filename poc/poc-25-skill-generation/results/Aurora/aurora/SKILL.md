---
name: aurora
description: Manage end-to-end pharmaceutical cold-chain distribution, inventory allocation, and incident response. Use when processing purchase orders, validating regulatory compliance, allocating FEFO inventory, coordinating warehouse fulfillment, monitoring temperature excursions, handling delivery exceptions, or managing product recalls.
---

# AURORA Operational Procedure

## 1. Core Workflow: Order to Delivery

### Step 1: Order Reception & Validation
1. Create an order record upon receiving a purchase order (portal, e-interface, or sales rep).
2. Extract customer ID, authorization, destination, products, quantities, and special instructions.
3. Assign priority (Critical, High, Standard, Low) based on clinical/operational justification, not just customer request.
4. **Validation Gate:** Check for mandatory fields.
   - If missing: Set state to `INFORMATION_REQUIRED`. Send clarification request.
   - **Escalation Rules:**
     - No response in 4 business hours: Send reminder.
     - No response in 24 hours: Escalate to Order Management.
     - No response in 72 hours: Mark `CUSTOMER_UNRESPONSIVE` and close, **UNLESS** priority is Critical.
     - **CRITICAL RULE:** Never auto-close Critical orders due to non-response. Escalate immediately to Incident Management.

### Step 2: Parallel Regulatory & Customer Validation
Run two independent checks simultaneously:
- **Process A (Customer):** Verify registration, license, purchasing status, permitted categories, and location eligibility.
- **Process B (Regulatory):** Verify legal distribution rights, import/export permissions, special handling restrictions, and recall/suspension status.

**Decision Logic:**
- Both Pass: Proceed to Commercial Validation.
- Customer Auth Fails: Place order on `REGULATORY_HOLD`; notify Compliance Team.
- Product Prohibited: Reject affected lines. Continue with valid lines if partial fulfillment is permitted.
- Uncertain Result: Escalate to Compliance Officer for manual decision (Approve, Reject, Hold, or Request Evidence).
- **Constraint:** A rejected line must not block unrelated compliant products.

### Step 3: Commercial Validation
Check credit status, outstanding invoices, spending limits, pricing, and contract terms.
- **Credit Limit Exceeded:** Enter `CREDIT_REVIEW`. Finance may approve exception, request payment, or reject.
- **Price Dispute:** Enter `COMMERCIAL_DISPUTE`. Assign to Order Management. Do not suspend unrelated approved lines unnecessarily.

### Step 4: Inventory Allocation
Search authorized distribution centers for suitable stock.
- **Selection Rule:** Use FEFO (First Expired, First Out), subject to minimum shelf-life requirements.
- **Exclusions:** Never select expired, quarantined, recalled, or unauthorized batches.
- **Scenarios:**
  - **Full Availability:** Reserve stock; proceed.
  - **Distributed Availability:** Calculate consolidated vs. multiple shipments based on cost, stability, and deadline. Link all shipment IDs to the single order.
  - **Partial Availability:** Check if split delivery is accepted. If yes, reserve available stock and put remainder in `BACKORDER`. If no, wait for replenishment unless alternative source is approved.
  - **No Availability:** Check alternative suppliers. If none, enter `STOCK_SHORTAGE`. Provide estimated replenishment date. **Escalate Critical orders immediately to Incident Management.**
- **Reservation Expiry:** Reservations expire after 60 minutes if processing hasn't started. Warn at 15 mins. Release stock and recalculate if expired. Critical orders require explicit extension from Inventory Management.

### Step 5: Warehouse Fulfillment
1. **Picking:** Operator scans barcode, batch, expiry, and quantity. System blocks picking if scan mismatches allocation. **Never silently substitute batches.**
2. **Verification:** A *second* operator verifies identity, quantity, batch, and condition. The original picker cannot approve their own discrepancy resolution.
3. **Packaging:** Determine configuration (ambient, refrigerated, frozen, active temp control) based on product range, transit duration, and weather. If required packaging is unavailable, shipment cannot proceed. Search alternatives or request replenishment.
4. **Documentation:** Record packaging serials, monitor IDs, initial temps, and weights. Generate invoices, permits, and chain-of-custody docs. Missing mandatory docs block dispatch.
5. **Quality Release:** Confirm product eligibility, batch validity, packaging approval, and functional monitors. Pass = `READY_FOR_DISPATCH`. Fail = `QUALITY_HOLD` (requires QA release).

### Step 6: Transportation & Dispatch
- **Carrier Selection:** Compare approved carriers on reliability, temp capability, and route. **Do not select lowest price if handling requirements are unmet.**
- **Customs:** For international shipments, validate export/import clearance. If pending, hold shipment in `CUSTOMS_PENDING`. Do not redirect to unauthorized destinations.
- **Handover:** Warehouse and carrier independently confirm identity, seals, and condition. Both must record confirmation before entering `IN_TRANSIT`.

### Step 7: Monitoring & Delivery
- **Continuous Monitoring:** Track location, temp, humidity, and connectivity.
- **Delivery Confirmation:** Carrier arrival report is **NOT** sufficient for completion.
  - Recipient must confirm: Identity, package count, seal integrity, physical condition, and temp indicators.
  - **Valid Receipt:** Requires electronic confirmation from an authorized recipient.
  - **Exceptions:**
    - Damage: Record evidence; enter `DELIVERY_DISCREPANCY`.
    - Temp Excursion: Enter `QUALITY_HOLD` even if physically received.
    - Unauthorized Recipient: Do not accept delivery. Carrier retains custody.
    - Partial Delivery: Trace received vs. missing packages independently.

## 2. Decision Points & Branching Rules

| Condition | Action |
| :--- | :--- |
| **Missing Order Info** | State `INFORMATION_REQUIRED`. Timer starts. Escalate per timeline (4h/24h/72h). |
| **Regulatory Uncertainty** | Escalate to Compliance Officer. Manual decision required with audit trail. |
| **Credit Limit Breach** | State `CREDIT_REVIEW`. Finance decides: Exception, Payment, or Rejection. |
| **Stock Shortage** | State `STOCK_SHORTAGE`. Estimate replenishment. Escalate Critical orders to Incident Mgmt. |
| **Batch Scan Mismatch** | Block picking. Investigate via Inventory Mgmt. No silent substitution. |
| **Temp Warning (L1)** | Increase monitoring frequency. Notify Logistics. Check packaging integrity. |
| **Temp Excursion (L2)** | Create Quality Incident. State `POTENTIAL_EXCURSION`. Prevent delivery release until QA decision. |
| **Critical Excursion (L3)** | Declare Critical Incident. Notify QA Mgmt & Incident Mgmt. Suspend further distribution of affected batch. |
| **Shipment Delay** | Minor: Update ETA. Significant: Evaluate routes/packaging validity. Critical: Escalate to Incident Mgmt; consider replacement shipment. |
| **Lost Shipment** | Freeze record. Initiate investigation. Security review. If found later, requires quality/security assessment before delivery. |

## 3. Escalation & Exception Rules

1. **Patient Safety / P1 Incidents:** Immediate escalation to Incident Management. Do not wait for standard acknowledgement timers.
2. **Regulatory Holds:** Only Compliance Officers can resolve. All decisions require documented reasons.
3. **Quality Holds:** Only authorized QA personnel can release. No product with unresolved quality status may be distributed.
4. **Financial Exceptions:** Credit exceptions or emergency procurement costs require specific financial approvals (Inventory Manager, Procurement Manager, or Finance Director based on value).
5. **Unresponsive Customers:** Standard orders close after 72 hours. Critical orders escalate to Incident Management instead of closing.
6. **Incident Acknowledgement:** If an assigned incident is not acknowledged within its deadline, escalate to Department Manager, then Incident Manager.

## 4. Gotchas

- **Delivery is NOT done when the carrier arrives.** A successful delivery requires physical receipt confirmation, recipient authorization, product condition check, and documentation completeness. The carrier's arrival report is merely a trigger for the receiving process.
- **No Silent Substitutions.** You cannot swap batches, suppliers, carriers, or destinations without explicit validation and approval. If a batch fails scanning, stop and investigate; do not pick a different one automatically.
- **Separation of Duties.** The person who picks the goods cannot verify them. The person who creates a financial exception cannot approve it. Independent verification is mandatory for controlled quality and financial actions.
- **FEFO is not FIFO.** Select stock based on expiration date first, but always respect minimum remaining shelf-life requirements for the destination. A batch that expires too soon is invalid even if it's the oldest.
- **Partial Fulfillment is Conditional.** You can only split deliveries if the customer agreement and product restrictions allow it. Otherwise, the whole order waits.
- **Exception Preservation.** A later success does not erase a prior exception. If a shipment had a delay but was delivered on time, the delay incident must still be resolved/closed with an audit record. It cannot be ignored because the outcome was good.
- **Recall Priority.** Recall notifications override normal fulfillment. Immediately block allocation, quarantine stock, and intercept in-transit shipments where possible. Do not mark a recall closed until all quantities are reconciled (warehouse + transit + delivered + returned + destroyed).
- **Billing Suspension.** Do not invoice quantities with unresolved delivery discrepancies. Invoice only confirmed delivered quantities. Ensure no double-billing for split shipments or replacement orders.
- **Critical Orders Never Auto-Close.** Even if the customer doesn't respond to information requests, a Critical order must be escalated to Incident Management, not closed.
- **Temperature Excursions Halt Distribution.** At Level 2 (Excursion), prevent delivery release entirely until Quality Assurance makes a decision. Do not assume the product is safe just because it arrived.
- **Emergency Procurement Needs Authorization.** Even in critical shortages, emergency purchases require documented authorization according to financial limits. They are not exempt from controls.
