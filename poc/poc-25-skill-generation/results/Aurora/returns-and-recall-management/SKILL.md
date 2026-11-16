---
name: returns-and-recall-management
description: Manages reverse logistics for customer returns and regulatory recalls, including return authorization, quarantine assessment, recall containment, and quantity reconciliation. Use when handling return requests, processing returned inventory, or executing recall procedures.
---

# Returns and Recall Management

This skill governs the reverse logistics workflow for customer returns and the containment of regulatory recalls. Follow these procedures strictly to ensure compliance and inventory integrity.

## 1. Processing Customer Returns

### Step 1: Validate Return Request
Before authorizing any return, validate the request against three criteria:
1. **Original Order**: Confirm the product was purchased from this entity.
2. **Delivery Confirmation**: Verify the product was successfully delivered.
3. **Time Limits**: Ensure the request is within the allowed return window.

If any validation fails, reject the request with a specific reason code.

### Step 2: Determine Authorization Path
Route the request based on the return reason and product type:
- **Commercial Returns** (e.g., wrong item, changed mind): Authorize automatically.
- **Quality-Related Returns** (e.g., damaged, defective): Require QA approval before authorization.
- **Controlled Products**: Require Regulatory Review before authorization.

### Step 3: Generate Authorization and Arrange Collection
1. Generate a Return Authorization (RA) number.
2. Arrange collection of the returned goods.
   - **Constraint**: Collection must occur under appropriate environmental conditions (e.g., cold chain for temperature-sensitive items).
3. Track the return shipment until receipt.

## 2. Managing Returned Inventory

### Step 1: Initial Quarantine
**Critical Rule**: All returned products must enter **QUARANTINE** status immediately upon receipt. 
- **Why**: Returned items may be damaged, tampered with, or expired. They cannot be assumed saleable.
- **Constraint**: Never bypass quarantine. Do not place returned items directly into saleable inventory.

### Step 2: QA Assessment
QA must assess the following attributes:
- Physical condition of the product.
- Integrity of packaging.
- Product history (e.g., batch numbers, expiration dates).

### Step 3: Determine Disposition
Based on QA assessment, assign one of the following outcomes:
1. **Release to Stock**: Only if QA explicitly approves. This is the *only* path back to saleable inventory.
2. **Return to Manufacturer**: For defective batches or supplier liability.
3. **Destroy**: For unsafe, expired, or unsalvageable items.
4. **Continue Quarantine**: If further investigation is needed.

## 3. Managing Recalls

### Step 1: Notification and Identification
Upon receiving a recall notification:
1. Identify all affected stock across all locations:
   - Warehouse inventory.
   - In-transit shipments.
   - Delivered units (if traceable).

### Step 2: Apply Containment Measures
Execute these actions immediately. **Recalls have priority over all normal operations.**
1. **Block Allocation**: Prevent new orders from allocating recalled stock.
2. **Quarantine Stock**: Move all identified warehouse stock to quarantine.
3. **Suspend Picking**: Halt all picking activities for affected SKUs.
4. **Notify Customers**: Inform customers who received affected products.
5. **Intercept In-Transit**: Attempt to intercept shipments currently in transit where logistics partners allow.

## 4. Recall Reconciliation and Closure

### Step 1: Maintain Quantity Balance
Track the following quantities for the recall:
- **Held**: Stock in warehouse quarantine.
- **In-Transit**: Stock currently moving.
- **Delivered**: Stock confirmed delivered to customers.
- **Returned**: Stock received back from customers.
- **Destroyed**: Stock disposed of.

**Formula**: 
`Total Recalled = Held + In-Transit + Delivered + Returned + Destroyed`

### Step 2: Resolve Discrepancies
- If the sum of tracked quantities does not equal the total recalled quantity, the recall **cannot** be closed.
- Investigate and resolve all unexplained quantities before proceeding.

### Step 3: Final Closure
- Submit the reconciled quantity report to the Compliance Team.
- **Constraint**: Only the Compliance Team can approve final closure. Do not close the recall without their explicit sign-off.

## Gotchas

- **Quarantine is Mandatory**: Returned products must *never* bypass quarantine. Even if a customer claims the item is unused, it must be assessed by QA before any disposition.
- **No Automatic Restocking**: There is no automatic return to saleable inventory. "Release to Stock" is a specific QA decision, not a default state.
- **Recall Priority**: During a recall, normal picking and allocation processes must be suspended for affected items. Do not prioritize standard order fulfillment over recall containment.
- **Closure Requires Reconciliation**: A recall cannot be closed if there are unexplained quantities. The balance sheet must be perfect before Compliance approval.
- **Environmental Conditions**: Returns of controlled or sensitive products must be collected under specific environmental conditions. Failure to do so may invalidate the return or create safety hazards.
- **Regulatory Review for Controlled Products**: Do not auto-authorize returns for controlled products. These require explicit regulatory review, which may take longer than standard returns.
