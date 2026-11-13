---
name: warehouse-fulfilment-and-dispatch
description: Executes the end-to-end warehouse fulfilment process, including picking, independent verification, packaging configuration, documentation, quality release, and dispatch handover. Use this skill when preparing physical shipments for release, handling picking exceptions, or managing quality holds and dispatch gates.
---

# Warehouse Fulfilment and Dispatch

This skill governs the physical preparation, verification, and release of shipments from the warehouse. Follow the steps below strictly to ensure compliance, accuracy, and safety.

## Core Workflow

### 1. Generate Picking Tasks
- Create picking tasks that explicitly include product identity, batch number, and required quantity.
- Ensure tasks are assigned to operators with appropriate access.

### 2. Execute Picking Stage
- **Scan Requirements**: Operators must scan the barcode, batch number, and expiry date for each item.
- **System Validation**: The system will block incorrect scans. Do not override system blocks.
- **Exception Handling**: If stock is missing at the designated location, trigger a `PICKING_EXCEPTION`.
  - **Rule**: Never perform silent substitutions. If the exact batch/location is unavailable, stop and escalate via the exception workflow.

### 3. Independent Verification
- **Second Operator Requirement**: A second operator must independently verify:
  - Product identity
  - Quantity
  - Batch number
  - Expiry date
  - Physical condition
- **Discrepancy Protocol**: If any discrepancy is found:
  - Return the item to the picking stage for correction OR initiate an inventory investigation.
  - **Constraint**: The original operator who picked the item cannot approve the resolution of their own discrepancy.

### 4. Determine Packaging Configuration
- Select the appropriate packaging type based on the following factors:
  - Product temperature range
  - Estimated transit duration
  - Current weather conditions
  - Potential customs delays
- **Options**: Ambient, Refrigerated, Frozen, or Active containers.
- **Availability Check**: Verify packaging availability.
  - If unavailable, search for compliant alternatives.
  - If no alternatives exist, request replenishment before proceeding.

### 5. Packing and Documentation
- **Record Data**: Log the following during packing:
  - Packaging serial numbers
  - Monitor IDs
  - Initial temperatures
- **Generate Documents**: Produce all mandatory documents, including:
  - Invoices
  - Customs declarations
  - Chain-of-custody forms
- **Hard Gate**: Block dispatch if any mandatory document is missing or incomplete.

### 6. Quality Release Check
- Perform a final check to confirm:
  - Product eligibility for shipment
  - Batch validity
  - Packaging approval status
  - Monitor functionality
- **Transition States**:
  - If all checks pass: Transition status to `READY_FOR_DISPATCH`.
  - If any check fails: Transition status to `QUALITY_HOLD`.

### 7. Dispatch Handover
- **Independent Confirmation**: Both the warehouse representative and the carrier must independently confirm:
  - Shipment identity
  - Seal integrity
  - Monitor status
- **Final Transition**: Only after mutual confirmation, transition the shipment status to `IN_TRANSIT`.

## Escalation and Exceptions

- **Picking Exceptions**: Any `PICKING_EXCEPTION` (e.g., missing stock) must be escalated to inventory management. Do not substitute products without explicit authorization.
- **Quality Holds**: Shipments in `QUALITY_HOLD` cannot be released by warehouse staff. They require an authorized Quality Assurance (QA) release.
- **Documentation Gaps**: Missing documentation is a hard stop. Escalate to the documentation/compliance team to resolve gaps before attempting dispatch.

## Gotchas

- **Separation of Duties**: Verification must be performed by a different operator than the one who picked the items. Self-approval is strictly prohibited to prevent fraud and errors.
- **No Silent Substitutions**: If the specific batch or location is empty, do not pick a similar item. Trigger an exception. Substitutions require explicit approval and may affect compliance.
- **Documentation is a Hard Gate**: You cannot dispatch a shipment even if the physical goods are ready if any mandatory document (invoice, customs, chain-of-custody) is missing. The system will block this transition.
- **Quality Hold Release**: Warehouse staff cannot clear a `QUALITY_HOLD`. Only authorized QA personnel can release these shipments.
- **Monitor Verification**: Initial temperatures and monitor IDs must be recorded *during* packing. If monitors are not functional or IDs are missing, the shipment cannot proceed to `READY_FOR_DISPATCH`.
- **Carrier Confirmation**: Dispatch is not complete until the carrier independently confirms seals and monitor status. Warehouse confirmation alone is insufficient for the `IN_TRANSIT` transition.
