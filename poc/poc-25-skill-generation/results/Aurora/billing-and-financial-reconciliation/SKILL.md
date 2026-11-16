---
name: billing-and-financial-reconciliation
description: Manages invoice generation, payment monitoring, and financial closure for orders. Use when handling billing triggers, reconciling delivered vs. ordered quantities, applying financial rules, managing payment workflows, or finalizing order closure.
---

# Billing and Financial Reconciliation

This skill governs the financial lifecycle of an order, from invoice generation to final closure. It ensures that billing is accurate, compliant with contract terms, and synchronized with physical delivery and quality status.

## Core Workflow

### 1. Trigger Invoice Generation
Do not generate invoices automatically upon dispatch. Invoices must only be triggered by one of the following valid events:
- **Valid Delivery Confirmation**: The recipient has explicitly confirmed receipt of goods.
- **Authorized Billing Event**: A specific contractual milestone (e.g., milestone completion) has been verified and authorized.

**Action**: Verify the trigger event exists in the system before proceeding to quantity reconciliation.

### 2. Reconcile Quantities
Compare all quantity records to determine the billable amount. The invoice must reflect **accepted quantities**, not requested or dispatched quantities.

**Steps**:
1. Retrieve the Original Purchase Order (PO) quantities.
2. Retrieve any Amendment quantities.
3. Retrieve Allocated, Dispatched, and Confirmed Delivered quantities.
4. **Rule**: Invoice only the quantity that has been *accepted* by the customer. If a delivery was rejected or partially rejected, exclude those units.
5. **Split Shipments**: If an order was split into multiple shipments, reconcile each shipment against the original order line items to ensure the total billed does not exceed the accepted total.

### 3. Handle Discrepancies and Holds
Before finalizing the invoice, check for blocking conditions.

**Decision Points**:
- **Unresolved Delivery Discrepancies**: If there is a mismatch between dispatched and confirmed delivered quantities that is not yet resolved, **suspend billing** for those specific quantities.
- **Quality Holds**: If goods are under a quality hold or investigation, **suspend billing** for those items until the hold is released.
- **Replacement Shipments**: Ensure no double billing occurs. If a replacement was sent, verify that the original defective item was returned or credited. Do not bill for both the original and the replacement unless explicitly authorized by contract.

### 4. Apply Financial Rules
Apply all financial adjustments to the reconciled quantities.

**Steps**:
1. **Pricing**: Apply contract-specific pricing. Do not use list price unless no contract exists.
2. **Discounts**: Apply any agreed-upon volume or promotional discounts.
3. **Taxes**: Calculate and apply appropriate taxes based on the delivery location and product type.
4. **Credit Adjustments**: Apply any existing credit memos or adjustments to the invoice total.

### 5. Manage Payment Workflow
Monitor the status of issued invoices.

**Steps**:
1. **Monitoring**: Track payment due dates.
2. **Reminders**: Send payment reminders for overdue accounts.
   - **Exception**: Do not send automatic reminders for **Critical Patient Supply** orders without a manual review. Interrupting supply for these items due to payment issues requires human oversight.
3. **Escalation**: Escalate significantly overdue accounts to **Accounts Receivable (AR)**.
4. **Account Restrictions**: Apply account restrictions (e.g., credit holds) **only** if permitted by the customer's contract or regulatory status. Do not apply restrictions arbitrarily.

### 6. Ensure Order Closure
An order can only be marked as **CLOSED** when all of the following conditions are met:
- All quantities are processed (billed, credited, or cancelled).
- All delivery discrepancies are resolved.
- All quality incidents are closed or transferred to the appropriate department.
- Chain-of-custody documentation is complete.
- Financial reconciliation is complete (invoice issued, payment received or credit applied).

## Escalation and Exceptions

- **Disputed Invoices**: If a customer disputes an invoice, **exclude it from automatic collection processes**. Route to a human agent for review and resolution.
- **Returns**: Returns must trigger a credit process. Do not simply reverse the invoice; generate a formal credit memo linked to the return authorization.
- **Critical Patient Supply**: Never interrupt supply or send aggressive collection actions for critical patient supply items without explicit review by a supervisor or account manager.

## Gotchas

- **Accepted vs. Dispatched**: A common error is billing for dispatched quantities. Always bill for **accepted** quantities. If the customer rejects a shipment, it is not billable.
- **Split Shipments**: Reconciling split shipments requires aggregating all partial deliveries against the original order line. Ensure the sum of billed quantities across all splits does not exceed the accepted total.
- **Double Billing Replacements**: When a replacement is issued, the original item must be accounted for (returned/credited). Failing to do so results in double billing, which is a major compliance and customer satisfaction risk.
- **Quality Holds Block Billing**: Goods under quality hold are not billable, even if they have been physically delivered. Wait for the quality release before invoicing.
- **Critical Supply Safety**: Automatic payment reminders can trigger account holds that stop shipments. For critical patient supply, this is unacceptable. Always review these accounts manually before taking collection actions.
- **Disputes Stop Automation**: Disputed invoices must be removed from automated dunning/collection cycles immediately to prevent customer escalation and legal risk.
