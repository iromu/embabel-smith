---
name: inventory-allocation-and-replenishment
description: Manages stock availability determination, reservation lifecycle, and procurement replenishment. Use when checking inventory for orders, handling allocation scenarios (full, partial, distributed, none), managing reservations, triggering replenishment, or assessing expiry risks.
---

# Inventory Allocation and Replenishment

This skill governs the end-to-end process of determining stock availability, reserving inventory, and managing procurement replenishment. It ensures compliance with FEFO (First Expired, First Out), shelf-life, and regulatory constraints while optimizing for cost, time, and risk.

## Core Workflow: Stock Availability & Allocation

### 1. Search & Eligibility Screening
Search authorized distribution centers for suitable inventory. Apply strict eligibility filters before considering allocation:
- **FEFO & Shelf-Life:** Prioritize batches with the earliest expiry dates that still meet minimum shelf-life requirements.
- **Status Checks:** Exclude any stock marked as `EXPIRED`, `QUARANTINED`, or `RECALLED`.
- **Regulatory & Environmental:** Verify regulatory eligibility and environmental history compliance.

**Rule:** Never substitute batches silently. If a specific batch is unavailable, do not swap it for another without explicit process adherence or customer approval if required by policy.

### 2. Allocation Scenario Decision
Based on search results, determine the allocation scenario:

#### A. Full Availability
- **Action:** Reserve the required quantity immediately.
- **Next Step:** Proceed to order fulfillment.

#### B. Distributed Availability
- **Context:** Stock is available but split across multiple locations or batches.
- **Action:** Calculate the optimal strategy by comparing:
  - Consolidated shipment (single source, potentially higher cost/time).
  - Split shipments (multiple sources, potentially lower cost/faster, but higher risk/complexity).
- **Decision:** Select the option that best balances cost, time, and risk.

#### C. Partial Availability
- **Action:** Check if the customer accepts split deliveries.
  - **If Accepted:** Reserve available stock and initiate backorders for the remainder.
  - **If Not Accepted:** Treat as No Availability or escalate for alternative sourcing.

#### D. No Availability
- **Action:** Check for approved alternatives (substitutes).
- **Escalation:** If no alternatives exist and the order is classified as **Critical**, escalate immediately to Incident Management.

## Reservation Lifecycle Management

Reservations are temporary holds on inventory. Manage them strictly:

1. **Standard Expiry:** Reservations expire after **60 minutes**.
2. **Warning System:** Issue a warning at **15 minutes** before expiry.
3. **Expiry Handling:** Upon expiry, release the reservation and recalculate availability.
4. **Critical Orders:** For orders marked **Critical**, apply explicit extensions to prevent premature release.

## Replenishment & Procurement

Replenishment is triggered independently of order allocation when stock thresholds are breached.

### 1. Trigger & Supplier Selection
- **Monitor:** Continuously monitor stock levels against defined thresholds.
- **Select Supplier:** Choose approved suppliers based on **operational suitability** (reliability, lead time, quality), not just price.

### 2. Purchase Requisition & Approval
Create purchase requisitions and route them for financial approval based on value tiers:
- **≤ £5k:** Standard approval.
- **> £5k and ≤ £25k:** Mid-tier approval.
- **> £25k and ≤ £100k:** Senior approval.
- **> £100k:** Executive approval.

### 3. Inbound Delivery Tracking
- Track supplier deliveries until receipt.
- **Critical Rule:** All inbound stock enters the `QUARANTINE_PENDING` status upon arrival. It cannot be allocated or sold until formally released by Quality Assurance.

## Expiry Risk Management

Perform a **daily assessment** of inventory expiry risk:
1. **Group:** Categorize stock by remaining days to expiry.
2. **Intervene:** For high-risk groups, initiate interventions such as:
   - Inter-warehouse transfers to locations with higher demand.
   - Supplier returns if contractually permitted.

## Gotchas & Hard Constraints

- **Ineligible Stock:** Expired, quarantined, or recalled stock is **never** eligible for allocation, regardless of demand urgency.
- **Silent Substitution:** Never substitute batches silently. Any deviation from the requested batch must be documented and approved.
- **Emergency Procurement:** Requires **documented authorization**. Do not bypass approval tiers for emergency buys without written sign-off.
- **Quarantine Gate:** Inbound stock is **not** available for sale until quality release. Do not allocate `QUARANTINE_PENDING` stock.
- **Reservation Expiry:** Reservations are not permanent. If a reservation expires, the stock is released. Always check reservation status before proceeding with fulfillment.
- **Critical Escalation:** Critical shortages with no alternatives must be escalated to Incident Management. Do not attempt to resolve critical stockouts via standard replenishment timelines.
