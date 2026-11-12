# AURORA
## Global Pharmaceutical Cold-Chain Distribution, Inventory & Incident Management System

**Document type:** Business Process and Operational System Specification  
**System name:** AURORA (Advanced Unified Operations, Risk & Allocation)  
**Version:** 1.0  
**Classification:** Operational Process Specification  
**Scope:** Global pharmaceutical distribution network

---

# 1. System Overview

AURORA is a centralised operational management system responsible for receiving pharmaceutical orders, validating regulatory requirements, allocating inventory, coordinating warehouse operations, arranging transportation, monitoring environmental conditions, managing delivery exceptions, and maintaining a complete chain of custody.

The system operates across multiple countries, distribution centres, pharmaceutical manufacturers, logistics providers, hospitals, pharmacies, and regulatory authorities.

Its primary responsibility is to ensure that every pharmaceutical shipment:

1. Contains the correct products and quantities.
2. Uses legally authorised suppliers and distribution channels.
3. Meets the destination country's regulatory requirements.
4. Has sufficient inventory allocated and reserved.
5. Is packaged according to the product's environmental requirements.
6. Travels using an approved transportation method.
7. Maintains required temperature and environmental conditions.
8. Is delivered to an authorised recipient.
9. Has a complete and verifiable chain-of-custody record.
10. Is correctly invoiced and financially reconciled.

AURORA must manage normal operations automatically while escalating decisions involving regulatory uncertainty, financial risk, patient safety, inventory shortages, or compromised product integrity.

The system must never treat a shipment as successfully delivered simply because a carrier reports that it has reached its destination.

A successful delivery requires confirmation of physical receipt, recipient authorisation, product condition, and documentation completeness.

# 2. Organisational Roles

The system coordinates the following operational roles.

### 2.1 Customer

Hospitals, pharmacies, clinics, research facilities, and authorised distributors.

Responsibilities:
- Submit purchase orders.
- Specify delivery requirements.
- Provide receiving information.
- Confirm delivery.
- Report shortages, damage, or discrepancies.

### 2.2 Order Management Team

Responsible for:
- Reviewing incoming orders.
- Resolving incomplete information.
- Validating commercial conditions.
- Coordinating order amendments.
- Managing customer communication.

### 2.3 Regulatory Compliance Team

Responsible for:
- Verifying customer authorisations.
- Checking product-specific restrictions.
- Validating import/export documentation.
- Approving exceptional regulatory situations.
- Managing regulatory holds.

### 2.4 Inventory Management Team

Responsible for:
- Monitoring stock levels.
- Managing reservations.
- Tracking expiry dates.
- Identifying suitable inventory.
- Managing stock transfers and shortages.

### 2.5 Warehouse Operations

Responsible for:
- Picking products.
- Verifying batches.
- Packaging shipments.
- Preparing temperature-control materials.
- Recording handover to carriers.

### 2.6 Logistics Operations

Responsible for:
- Selecting transportation providers.
- Booking shipments.
- Managing routes.
- Monitoring delivery progress.
- Coordinating alternative transportation.

### 2.7 Quality Assurance Team

Responsible for:
- Investigating temperature excursions.
- Reviewing damaged shipments.
- Approving or rejecting affected products.
- Managing quarantine and release.
- Recording quality incidents.

### 2.8 Finance Department

Responsible for:
- Credit validation.
- Payment authorisation.
- Invoice generation.
- Refunds.
- Financial reconciliation.

### 2.9 Incident Management Team

Responsible for:
- Managing critical operational incidents.
- Coordinating multiple departments.
- Prioritising patient-critical deliveries.
- Authorising emergency interventions.
- Recording incident resolution.

# 3. System Entry Points

AURORA can be activated by several independent events.

**Event A: New purchase order**

A customer submits a purchase order through the customer portal, an electronic ordering interface, or an authorised sales representative.

**Event B: Inventory replenishment**

A warehouse reports that inventory has fallen below its replenishment threshold.

**Event C: Environmental alert**

A shipment monitoring device reports that temperature, humidity, or another environmental parameter has exceeded an acceptable threshold.

**Event D: Shipment delay**

A carrier reports that a shipment is delayed, diverted, or potentially lost.

**Event E: Delivery discrepancy**

A customer reports missing products, incorrect quantities, damaged packaging, or an unauthorised delivery.

**Event F: Regulatory notification**

A regulatory authority issues a product recall, distribution restriction, import suspension, or other notification affecting existing or planned shipments.

**Event G: Expiry notification**

Inventory management identifies products approaching their expiry date.

Each event creates an operational case with a unique identifier, timestamp, originating entity, priority, and current status.

Events affecting an existing order or shipment must be linked to its original operational case rather than creating an unrelated duplicate.

# 4. Main Process: Purchase Order to Delivery

## 4.1 Order Reception

When a new purchase order arrives, AURORA creates an order record.

The system extracts:

- Customer identity.
- Customer authorisation identifier.
- Destination address.
- Requested delivery date.
- Product identifiers.
- Product quantities.
- Required delivery conditions.
- Purchase order reference.
- Billing information.
- Any special handling instructions.

The order is assigned one of four priorities:

| Priority | Definition |
|---|---|
| Critical | Immediate patient-care requirement |
| High | Time-sensitive clinical or operational requirement |
| Standard | Normal commercial order |
| Low | Planned replenishment or non-urgent stock |

Priority may be requested by the customer but must be validated against the actual clinical and operational justification.

### Initial validation

The system checks whether all mandatory fields are present.

If information is missing:

1. The order enters the INFORMATION_REQUIRED state.
2. A request for clarification is sent to the customer.
3. The order remains open.
4. A response timer begins.
5. If the customer responds, the missing information is incorporated and validation resumes.
6. If no response arrives within four business hours, a reminder is issued.
7. If no response arrives within 24 hours, the order is escalated to Order Management.
8. If no response arrives within 72 hours, the order is marked CUSTOMER_UNRESPONSIVE and closed unless it has critical priority.

Critical orders must never be automatically closed because of an unanswered information request. They are escalated to the Incident Management Team.

## 4.2 Customer and Regulatory Validation

After initial information validation, two independent checks must run in parallel.

**Process A: Customer authorisation**

The system verifies:

- Customer registration.
- Operating licence.
- Authorised purchasing status.
- Permitted product categories.
- Delivery location eligibility.
- Account restrictions.

**Process B: Regulatory and product validation**

The system verifies:

- Whether the product can legally be distributed to the destination.
- Whether import or export permissions are required.
- Whether the product is subject to special handling restrictions.
- Whether the destination requires additional documentation.
- Whether the product is currently subject to a recall or distribution suspension.

The results of both processes must be received before the order can proceed.

### Decision logic

If both checks pass, proceed to commercial validation.

If customer authorisation fails, place the order on REGULATORY_HOLD and notify the Compliance Team.

If product distribution is prohibited, reject the affected product lines.

If additional documentation is required, request it from the customer and suspend the affected lines.

If the regulatory result is uncertain, escalate to a Compliance Officer.

If one product line is prohibited but the remaining lines are valid, the system may continue processing the valid lines if partial fulfilment is permitted.

A rejected product line must not prevent unrelated, compliant products from being processed.

A Compliance Officer can:

- Approve the order.
- Reject the order.
- Request additional evidence.
- Approve specific product lines.
- Place the entire order on hold.

All manual regulatory decisions require a reason and an audit record.

## 4.3 Commercial Validation

Once regulatory validation is complete, the system checks:

1. Customer credit status.
2. Outstanding invoices.
3. Account spending limits.
4. Product pricing.
5. Contractual discounts.
6. Delivery charges.
7. Currency conversion where applicable.
8. Purchase order value against the approved commercial agreement.

### Financial decision

If the customer has sufficient credit and the order value is valid, continue.

If the customer exceeds its credit limit, the order enters CREDIT_REVIEW.

Finance receives a notification and may:

- Approve a temporary credit exception.
- Request partial payment.
- Require full prepayment.
- Reject the order.

If prepayment is required, the system generates a payment request and waits for confirmation.

If payment is not received within the agreed period, the order remains suspended.

If the customer disputes the price, the order enters COMMERCIAL_DISPUTE and is assigned to Order Management.

Commercial disputes affecting only certain product lines should not unnecessarily suspend the remaining approved lines.

# 5. Inventory Allocation

After successful validation, AURORA determines how the order can be fulfilled.

The system searches all authorised distribution centres for suitable inventory.

Each stock candidate is evaluated using:

- Product identifier.
- Batch number.
- Quantity available.
- Expiry date.
- Storage conditions.
- Quality release status.
- Regulatory eligibility.
- Warehouse location.
- Reserved quantities.
- Stock ownership restrictions.

### 5.1 Batch selection

Inventory is selected using FEFO (First Expired, First Out), subject to product-specific minimum remaining shelf-life requirements.

A batch must not be selected if:

- It is expired.
- It is quarantined.
- It has been recalled.
- It has insufficient remaining shelf life.
- It is not authorised for the destination.
- Its environmental history is incomplete or unacceptable.

### 5.2 Allocation scenarios

**Scenario A: Full availability**

All required quantities are available at one distribution centre.

Reserve the required quantities and continue.

**Scenario B: Distributed availability**

Required quantities are available across multiple distribution centres.

The system calculates whether a consolidated shipment or multiple shipments provide the best operational outcome.

The calculation considers:

- Delivery deadline.
- Transportation costs.
- Product stability.
- Available packaging.
- Transit time.
- Risk of delay.

If multiple shipments are selected, each shipment receives its own identifier but remains linked to the same order.

**Scenario C: Partial availability**

Only some quantities are available.

The system calculates the available quantity and identifies the shortage.

It then checks whether:

- Partial fulfilment is permitted.
- The customer accepts split delivery.
- The missing stock can be replenished before the requested deadline.
- An alternative authorised distribution centre can supply the shortage.

If partial fulfilment is accepted, available stock is reserved and the missing quantity enters BACKORDER.

If partial fulfilment is not accepted, the entire order waits for replenishment unless an authorised alternative is approved.

**Scenario D: No availability**

The system checks alternative suppliers and authorised distribution centres.

If stock is found elsewhere, a transfer or alternative fulfilment plan is proposed.

If stock cannot be found, the order enters STOCK_SHORTAGE.

Inventory Management must provide an estimated replenishment date.

Critical orders are escalated immediately to Incident Management.

### 5.3 Reservation expiry

Inventory reservations expire after 60 minutes if warehouse processing has not started.

The system sends a warning 15 minutes before expiry.

If the reservation expires:

1. Release the reserved quantities.
2. Recalculate inventory availability.
3. Attempt a new reservation.
4. Notify Order Management if the original allocation cannot be recovered.

Critical orders require an explicit reservation extension from Inventory Management.

# 6. Warehouse Fulfilment

When stock is successfully allocated, AURORA creates warehouse fulfilment tasks.

Each task contains the products, batches, quantities, packaging requirements, and dispatch deadline.

Warehouse processing follows these stages.

### Stage 1: Picking

A warehouse operator receives a picking instruction.

For each product, the operator must scan:

- Product barcode.
- Batch identifier.
- Expiry date.
- Picked quantity.

The system compares the scanned information against the approved allocation.

If the scan is incorrect, picking is blocked.

If the product is unavailable at its recorded location, the task enters PICKING_EXCEPTION.

Inventory Management investigates the discrepancy.

The system must not silently substitute another batch.

### Stage 2: Independent verification

A second operator verifies the picked products.

The verifier checks:

- Product identity.
- Quantity.
- Batch number.
- Expiry date.
- Packaging integrity.
- Product condition.

A discrepancy sends the task back to picking or inventory investigation.

The original operator cannot independently approve their own discrepancy resolution.

### Stage 3: Packaging assessment

AURORA determines the packaging configuration based on product requirements and expected journey conditions.

Possible configurations include:

- Ambient packaging.
- Refrigerated packaging.
- Frozen packaging.
- Validated insulated containers.
- Active temperature-controlled containers.

The packaging calculation considers:

- Product temperature range.
- Expected transit duration.
- Ambient weather conditions.
- Route characteristics.
- Expected customs delays.
- Packaging validation period.
- Availability of temperature monitoring devices.

If the required packaging is unavailable, the shipment cannot proceed.

The system searches alternative packaging locations.

If no suitable packaging exists, Warehouse Operations must request replenishment or Logistics must propose an approved alternative facility.

### Stage 4: Packing and documentation

The operator packs the shipment and records:

- Packaging type.
- Packaging serial number.
- Temperature monitoring device identifier.
- Initial temperature.
- Packing timestamp.
- Operator identity.
- Product and batch contents.
- Shipment weight and dimensions.

AURORA generates the required shipment documentation.

This may include commercial invoices, packing lists, temperature instructions, regulatory permits, customs declarations, and chain-of-custody documents.

Documentation completeness must be verified before dispatch.

Missing mandatory documents block shipment release.

### Stage 5: Quality release

A quality release check confirms that:

- All products are eligible for distribution.
- Batch records are valid.
- Packaging is approved.
- Monitoring equipment is functional.
- Temperature conditions are acceptable.
- Documentation is complete.

If all checks pass, the shipment enters READY_FOR_DISPATCH.

If any check fails, the shipment enters QUALITY_HOLD.

Only authorised Quality Assurance personnel can release a quality hold.

# 7. Transportation Planning

After warehouse release, Logistics Operations selects a transportation provider.

The system compares available carriers using:

- Approved carrier status.
- Route availability.
- Estimated transit time.
- Temperature-control capability.
- Historical reliability.
- Cost.
- Customs handling capability.
- Delivery deadline compatibility.

A carrier with the lowest price must not be selected if it cannot meet the shipment's handling requirements.

### 7.1 Carrier selection

If an approved carrier meets all requirements, AURORA requests a booking.

If no carrier is available, the system:

1. Searches alternative approved carriers.
2. Checks alternative departure times.
3. Evaluates alternative distribution centres.
4. Recalculates expected delivery.
5. Requests customer approval if the delivery deadline changes.

Critical shipments are escalated to Logistics Management after the first unsuccessful booking attempt.

### 7.2 Export and import clearance

For international shipments, customs documentation must be validated before dispatch.

The system checks:

- Export authorisation.
- Import authorisation.
- Product classification.
- Customs declarations.
- Country-specific permits.
- Required supporting documentation.

If customs approval is unavailable, the shipment remains CUSTOMS_PENDING.

If customs authorities request additional information, the system creates a documentation task for Compliance.

If clearance is refused, Logistics must evaluate whether the shipment can legally be redirected.

No shipment may be redirected to an unauthorised destination.

### 7.3 Dispatch

Before carrier handover, the warehouse operator and carrier representative independently confirm:

- Shipment identity.
- Package count.
- Seal numbers.
- Temperature monitor status.
- Physical condition.
- Handover timestamp.

Both parties record confirmation.

The shipment then enters IN_TRANSIT.

# 8. Real-Time Shipment Monitoring

AURORA monitors shipments continuously from dispatch until confirmed receipt.

The system receives updates from carrier tracking systems and environmental monitoring devices.

Monitoring includes:

- Current shipment location.
- Expected arrival time.
- Actual transit time.
- Temperature readings.
- Humidity readings where applicable.
- Device connectivity.
- Route deviations.
- Customs status.
- Carrier exception notifications.

Each product has its own permitted environmental operating range.

The monitoring system evaluates both individual readings and the duration of any deviation.

### 8.1 Temperature monitoring

The system applies the product's approved environmental limits and alert thresholds.

There are three levels of alert.

**Level 1: Warning**

A reading approaches an operating limit but has not breached the product's approved tolerance.

Actions:
- Record the event.
- Increase monitoring frequency.
- Notify Logistics Operations.
- Request confirmation of packaging integrity.

**Level 2: Excursion**

A reading exceeds an approved environmental limit.

Actions:
1. Create a quality incident.
2. Notify Quality Assurance immediately.
3. Mark the affected shipment as POTENTIAL_EXCURSION.
4. Request the complete environmental history.
5. Identify affected product batches.
6. Determine whether the shipment should be diverted to a controlled facility.
7. Prevent delivery release until Quality Assurance has made a decision.

**Level 3: Critical excursion**

The deviation exceeds a predefined critical threshold or persists beyond an approved duration.

Actions:
- Declare a critical quality incident.
- Notify Quality Assurance Management.
- Notify Incident Management.
- Evaluate emergency intervention.
- Identify all potentially affected shipments from the same batch or packaging process.
- Suspend further distribution of affected inventory if required.

### 8.2 Temperature excursion investigation

Quality Assurance receives:

- Full temperature history.
- Duration of deviation.
- Maximum and minimum readings.
- Sensor calibration information.
- Product stability data.
- Packaging validation data.
- Transit history.
- Batch information.

Quality Assurance may:

1. Release the shipment.
2. Maintain quarantine.
3. Request laboratory or manufacturer assessment.
4. Reject affected products.
5. Authorise partial release if individual product units can be independently assessed.

No affected product may be returned to available inventory without documented approval.

### 8.3 Route deviation

If the carrier deviates from the approved route:

The system determines whether the deviation affects delivery time, environmental exposure, customs compliance, or security.

Minor deviations are recorded and monitored.

Material deviations create a logistics incident.

Potentially unsafe deviations trigger Quality Assurance review.

Suspected theft or loss triggers Security and Incident Management.

# 9. Delay and Delivery Exception Management

AURORA continuously compares the estimated delivery time against the customer's required delivery deadline.

When a delay is detected, the system calculates the severity.

### Minor delay

The shipment is expected to arrive within the permitted delivery window.

Actions:
- Update the estimated arrival time.
- Notify the customer if the delay is material.
- Continue monitoring.

### Significant delay

The shipment is expected to miss the requested delivery window.

Actions:
1. Notify Logistics Operations.
2. Identify the cause.
3. Check alternative routes.
4. Request carrier recovery options.
5. Evaluate packaging validity for the extended journey.
6. Recalculate the expected delivery time.
7. Notify the customer.

If the revised arrival time remains acceptable, continue.

Otherwise, request customer instructions.

### Critical delay

The delay creates a potential clinical supply interruption.

Actions:
- Escalate to Incident Management.
- Check alternative distribution centres.
- Check whether replacement stock is available.
- Evaluate emergency transportation.
- Contact the receiving facility.
- Prepare a replacement shipment if authorised.

If a replacement shipment is created, the original shipment remains tracked.

The system must prevent the customer from being invoiced twice for the same order quantity.

### Lost shipment

If a carrier cannot locate a shipment:

1. Declare a missing shipment incident.
2. Freeze the affected shipment record against normal delivery completion.
3. Initiate a carrier investigation.
4. Notify Security and Logistics Management.
5. Assess potential product integrity and security risks.
6. Check whether a replacement is required.
7. Continue monitoring for recovery.

If the shipment is subsequently found, it must undergo quality and security assessment before it can be delivered.

# 10. Delivery and Receipt Confirmation

When the carrier reports arrival, AURORA does not immediately close the shipment.

It initiates the receiving process.

The receiving facility must confirm:

- Shipment identity.
- Package count.
- Recipient identity.
- Recipient authorisation.
- Seal integrity.
- Physical packaging condition.
- Temperature indicator status.
- Delivery timestamp.

The recipient must provide an electronic delivery confirmation.

### Delivery decision

If all checks pass:

1. Confirm receipt.
2. Record the receiving party.
3. Record delivery conditions.
4. Close the physical delivery process.
5. Initiate invoice processing.

If packaging is damaged, the recipient must record the damage and supporting evidence.

The shipment enters DELIVERY_DISCREPANCY.

If the temperature indicator reports an excursion, the shipment enters QUALITY_HOLD even if physical receipt is confirmed.

If the recipient is unauthorised, delivery must not be accepted as valid.

The carrier must retain custody and contact Logistics Operations.

If the facility is closed, the carrier must not leave pharmaceutical products unattended unless an approved delivery arrangement explicitly permits it.

### Partial delivery

If only some packages are received:

- Record received package identifiers.
- Record missing package identifiers.
- Confirm the quantities actually received.
- Keep missing packages in transit or investigation.
- Create a delivery discrepancy.
- Notify Order Management.

The received and missing quantities must remain independently traceable.

# 11. Returns and Product Rejection

A customer may request a return because of:

- Incorrect product.
- Incorrect quantity.
- Damaged packaging.
- Temperature excursion.
- Product recall.
- Unwanted order.
- Delivery discrepancy.
- Expired product.

All return requests create a return case linked to the original order and shipment.

### 11.1 Return authorisation

The system checks:

- Original purchase order.
- Shipment record.
- Delivery confirmation.
- Product identity.
- Batch number.
- Return reason.
- Time since delivery.
- Applicable return restrictions.

A return may be automatically authorised for eligible commercial discrepancies.

Quality-related returns require Quality Assurance approval.

Controlled or restricted products require additional regulatory review.

### 11.2 Return collection

Once authorised:

1. Generate a return authorisation number.
2. Select an approved collection provider.
3. Specify packaging requirements.
4. Generate return documentation.
5. Arrange collection.
6. Track the return shipment.

Returned products must be transported under the appropriate environmental conditions.

### 11.3 Returned inventory

Upon arrival, returned products enter QUARANTINE.

They must never automatically return to saleable inventory.

Quality Assurance assesses:

- Product condition.
- Packaging condition.
- Environmental history.
- Chain of custody.
- Batch identity.
- Original reason for return.

Possible outcomes:

- RELEASE_TO_STOCK.
- RETURN_TO_MANUFACTURER.
- DESTROY.
- CONTINUE_QUARANTINE.
- REQUEST_FURTHER_INVESTIGATION.

Every outcome requires an authorised decision.

# 12. Product Recall Management

A regulatory authority or manufacturer may issue a recall affecting one or more products or batches.

This event has priority over normal fulfilment operations.

When a recall notification arrives, AURORA creates a recall incident.

### 12.1 Immediate containment

The system identifies:

- All affected products.
- All affected batches.
- All distribution centres holding affected stock.
- All open purchase orders containing affected products.
- All allocated but undispatched stock.
- All shipments currently in transit.
- All completed deliveries containing affected batches.

It then applies the appropriate containment measures.

For inventory:
- Block further allocation.
- Quarantine affected stock.
- Cancel or suspend affected picking tasks.

For open orders:
- Suspend affected product lines.
- Notify Order Management.
- Prevent dispatch.

For in-transit shipments:
- Notify Logistics Operations.
- Request controlled interception where legally and operationally possible.
- Prevent normal delivery release.

For completed deliveries:
- Identify the receiving customers.
- Issue recall notifications.
- Request confirmation of affected quantities.
- Initiate return or disposal instructions.

### 12.2 Recall reconciliation

The system maintains a complete quantity reconciliation:

Total recalled quantity must equal:

- Quantity held in warehouses.
- Quantity in transit.
- Quantity delivered to customers.
- Quantity returned.
- Quantity destroyed.
- Any remaining unexplained quantity.

The recall cannot be marked CLOSED while unexplained quantities remain.

The Compliance Team must approve final closure.

# 13. Inventory Replenishment

AURORA monitors inventory levels independently of customer orders.

Each product has:

- Minimum stock threshold.
- Target stock level.
- Maximum stock level.
- Replenishment lead time.
- Safety stock.
- Expiry constraints.
- Supplier restrictions.

When stock falls below its replenishment threshold, the system creates a replenishment proposal.

### 13.1 Supplier selection

The system identifies approved suppliers and compares:

- Product authorisation.
- Available quantity.
- Price.
- Lead time.
- Batch expiry.
- Historical delivery reliability.
- Supplier quality status.

If an approved supplier is available, a purchase requisition is created.

If multiple suppliers are suitable, the system ranks them according to operational suitability rather than price alone.

### 13.2 Purchase approval

Purchase requisitions follow financial approval limits.

- Below £5,000: automated approval if within an approved contract.
- £5,000–£25,000: Inventory Manager approval.
- £25,000–£100,000: Procurement Manager approval.
- Above £100,000: Finance Director approval.

If the requisition exceeds the approved budget, it must be escalated regardless of its value.

Critical replenishment may use an emergency procurement route, but emergency procurement still requires documented authorisation.

### 13.3 Supplier delivery

Upon supplier dispatch, the inbound shipment is tracked.

When stock arrives, the warehouse performs:

- Product identification.
- Batch verification.
- Quantity verification.
- Expiry verification.
- Environmental history review.
- Packaging inspection.
- Required quality checks.

New stock enters QUARANTINE_PENDING until required quality release conditions are satisfied.

Only released stock becomes available for customer orders.

# 14. Expiry and Inventory Risk Management

AURORA runs an expiry assessment every day.

Products are grouped into:

- More than 180 days remaining.
- 90–180 days remaining.
- 30–90 days remaining.
- Less than 30 days remaining.
- Expired.

The system identifies stock at risk of expiry.

Possible interventions include:

- Prioritising suitable batches for new orders.
- Transferring stock to higher-demand locations.
- Requesting supplier returns.
- Offering eligible customers alternative delivery schedules.
- Requesting manufacturer support.

Expiry-related transfers must not override regulatory or minimum shelf-life requirements.

Expired products are automatically blocked from allocation and moved into the appropriate quarantine or disposal process.

# 15. Operational Incident Management

AURORA uses a central incident register.

An incident may originate from any operational process.

Examples include:

- Temperature excursion.
- Product recall.
- Missing shipment.
- Regulatory suspension.
- Cybersecurity-related shipment data integrity concern.
- Major carrier disruption.
- Warehouse power failure.
- Packaging failure.
- Supplier failure.
- Inventory discrepancy.

### 15.1 Incident severity

| Level | Description | Response |
|---|---|---|
| P1 | Immediate patient safety or major regulatory risk | Immediate escalation |
| P2 | Significant operational disruption | Urgent investigation |
| P3 | Limited operational impact | Normal investigation |
| P4 | Administrative issue | Routine resolution |

### 15.2 Incident lifecycle

Every incident follows this lifecycle:

1. Detection.
2. Registration.
3. Classification.
4. Assignment.
5. Containment.
6. Investigation.
7. Corrective action.
8. Verification.
9. Approval.
10. Closure.

An incident cannot be closed merely because its immediate symptoms have disappeared.

The responsible team must document the cause, corrective action, and evidence that the problem has been resolved.

If an incident affects multiple departments, the Incident Manager coordinates the response.

Individual departments retain responsibility for their own operational actions.

### 15.3 Escalation

If an assigned incident is not acknowledged within its response deadline, it is escalated to the relevant department manager.

If it remains unacknowledged, it is escalated to the Incident Manager.

P1 incidents are escalated immediately and must not wait for normal acknowledgement timers.

If the Incident Manager is unavailable, the designated deputy assumes responsibility.

# 16. Notifications and Communication

AURORA sends notifications based on event type, severity, and recipient role.

Notifications include:

- Order accepted.
- Additional information required.
- Regulatory hold.
- Credit hold.
- Stock shortage.
- Shipment dispatched.
- Shipment delayed.
- Temperature warning.
- Temperature excursion.
- Delivery completed.
- Delivery discrepancy.
- Recall notification.
- Return authorised.
- Incident escalated.
- Invoice generated.

Customers must not receive internal quality investigations, security assessments, or confidential supplier information unless specifically authorised.

Critical notifications require acknowledgement.

If an acknowledgement is not received within the required period, the system uses an alternative contact channel.

Every notification is recorded with:

- Recipient.
- Timestamp.
- Message category.
- Delivery status.
- Acknowledgement status.

# 17. Billing and Financial Reconciliation

Invoice generation begins only after valid delivery confirmation or another explicitly authorised billing event.

AURORA compares:

- Original purchase order.
- Approved order amendments.
- Allocated quantities.
- Dispatched quantities.
- Confirmed delivered quantities.
- Contract pricing.
- Approved delivery charges.
- Applicable taxes.
- Credit adjustments.

If quantities differ, the invoice must reflect the accepted quantity rather than the original requested quantity.

If a shipment has an unresolved delivery discrepancy, billing is suspended for the affected quantity.

For split shipments, separate delivery records are reconciled against the original order.

The system must ensure that:

- Each delivered quantity is billed no more than once.
- Undelivered quantities are not invoiced prematurely.
- Approved discounts are applied.
- Returns generate the appropriate credit process.
- Disputed invoices are excluded from automatic collection.

After invoice generation, the system monitors payment.

If payment is overdue:

1. Send a payment reminder.
2. Recheck payment status.
3. Escalate to Accounts Receivable.
4. Apply account restrictions only according to the customer's contractual and regulatory status.

Critical patient-supply operations must not be automatically interrupted by a payment reminder workflow without authorised review.

# 18. System-Wide Rules and Constraints

The following rules apply across all workflows.

**Rule 1: Regulatory precedence**

Regulatory restrictions override commercial decisions, delivery deadlines, and inventory availability.

**Rule 2: Product integrity**

No product with unresolved quality status may be released for distribution.

**Rule 3: Traceability**

Every product movement must be traceable to a batch, quantity, location, timestamp, and responsible party.

**Rule 4: Separation of duties**

The same person must not independently perform and approve a controlled quality or financial exception where independent approval is required.

**Rule 5: No silent substitutions**

Products, batches, suppliers, carriers, and destinations cannot be substituted without the required validation.

**Rule 6: Partial fulfilment**

Partial fulfilment is permitted only when the customer agreement and product restrictions allow it.

**Rule 7: Exception preservation**

An operational exception must never be erased by a later successful event. It must be resolved, closed, or explicitly superseded with an audit record.

**Rule 8: Human authority**

Automated workflows may recommend actions but cannot override mandatory human approval requirements.

**Rule 9: Duplicate event handling**

Repeated carrier updates, customer submissions, or monitoring alerts must not create duplicate orders, shipments, invoices, or incidents.

**Rule 10: Emergency prioritisation**

Patient-critical requirements may change operational priorities but never override safety, quality, or regulatory restrictions.

# 19. Main Operational States

AURORA maintains independent states for orders, shipments, inventory, returns, and incidents.

### Order states

- RECEIVED
- INFORMATION_REQUIRED
- VALIDATING
- REGULATORY_HOLD
- CREDIT_REVIEW
- COMMERCIAL_DISPUTE
- APPROVED
- STOCK_SHORTAGE
- PARTIALLY_ALLOCATED
- ALLOCATED
- FULFILMENT_IN_PROGRESS
- PARTIALLY_DISPATCHED
- DISPATCHED
- PARTIALLY_DELIVERED
- DELIVERED
- CANCELLED
- CLOSED

### Shipment states

- CREATED
- PACKING
- QUALITY_HOLD
- READY_FOR_DISPATCH
- BOOKING_PENDING
- CUSTOMS_PENDING
- IN_TRANSIT
- DELAYED
- POTENTIAL_EXCURSION
- CRITICAL_INCIDENT
- DELIVERY_PENDING
- DELIVERY_DISCREPANCY
- QUARANTINED
- DELIVERED
- LOST
- RETURN_IN_PROGRESS
- CLOSED

### Inventory states

- AVAILABLE
- RESERVED
- PICKING
- QUARANTINE_PENDING
- QUARANTINED
- RECALLED
- EXPIRED
- RETURNED
- DESTROYED

### Incident states

- DETECTED
- REGISTERED
- CLASSIFIED
- ASSIGNED
- CONTAINMENT_IN_PROGRESS
- INVESTIGATION
- AWAITING_APPROVAL
- CORRECTIVE_ACTION
- VERIFICATION
- RESOLVED
- CLOSED

A transition must be authorised by the applicable process and must satisfy its required conditions.

For example, a shipment in QUALITY_HOLD cannot transition directly to IN_TRANSIT, even if the carrier booking has already been completed.

# 20. End-to-End Process Completion

An order is considered operationally complete only when:

1. All authorised product quantities have been processed.
2. All shipments have a final delivery or approved alternative disposition.
3. All delivery discrepancies have been resolved.
4. All relevant quality incidents have been closed or formally transferred to an independent investigation.
5. Required chain-of-custody records are complete.
6. Financial reconciliation is complete.
7. All invoices and credit adjustments have been generated.
8. The customer has received the required documentation.

The system then marks the order CLOSED.

A closed order remains available for historical audit, recall investigation, financial review, and regulatory inspection.

# 21. Performance and Operational Objectives

AURORA measures its operational performance through the following indicators.

| Indicator | Target |
|---|---|
| Order validation | Within 30 minutes |
| Critical order escalation | Immediate |
| Standard inventory allocation | Within 15 minutes |
| Warehouse fulfilment | Within agreed order SLA |
| Critical excursion notification | Immediate |
| Shipment tracking | Continuous |
| Delivery confirmation | Within 30 minutes of receipt |
| Recall containment initiation | Within 15 minutes |
| Critical incident acknowledgement | Within 5 minutes |
| Standard incident acknowledgement | Within 4 business hours |
| Invoice generation | Within 2 hours of eligible delivery confirmation |

These are operational targets, not permission to bypass mandatory checks.

# 22. Example Complex Scenario

The following scenario demonstrates how several processes interact.

A hospital in Spain places an urgent order for 500 units of a temperature-sensitive pharmaceutical product.

The requested delivery deadline is 36 hours.

The system performs the following operations:

**Step 1: Order intake**

The order is received and classified as HIGH priority.

The system detects that the customer has an existing commercial account.

**Step 2: Parallel validation**

Customer authorisation and product regulatory validation begin simultaneously.

Both checks pass.

Commercial validation identifies that the order exceeds the customer's available credit limit.

The order enters CREDIT_REVIEW.

**Step 3: Financial intervention**

Finance approves a temporary credit exception.

The order proceeds.

**Step 4: Inventory allocation**

The nearest distribution centre has 300 eligible units.

A second distribution centre has another 200 units.

The system calculates that shipping from both facilities would meet the deadline, while transferring the second allocation to the first facility would cause an unacceptable delay.

The system proposes two shipments.

**Step 5: Warehouse processing**

Both warehouses begin picking in parallel.

The first warehouse completes picking successfully.

The second warehouse discovers that 40 units from the allocated batch have an insufficient remaining shelf life.

Those units are blocked.

Inventory Management identifies an alternative eligible batch.

The allocation is recalculated and picking resumes.

**Step 6: Packaging**

Both shipments require validated refrigerated packaging.

The first warehouse has sufficient packaging.

The second warehouse has insufficient packaging materials.

The system identifies another approved warehouse with the required materials and arranges an internal transfer.

**Step 7: Transportation**

The first shipment receives a carrier booking.

The second shipment cannot obtain a suitable booking within the required delivery window.

Logistics evaluates alternative carriers and obtains an acceptable booking at a higher cost.

An authorised Logistics Manager approves the additional expense.

**Step 8: In-transit exception**

The first shipment experiences a temperature warning.

The monitoring system increases the sampling frequency and notifies Logistics.

The temperature remains within the approved range.

The warning is recorded and the shipment continues.

**Step 9: Critical excursion**

The second shipment experiences a temperature excursion during a customs delay.

The shipment is placed on quality hold.

Quality Assurance receives the complete temperature history and requests a manufacturer stability assessment.

The hospital is notified that delivery may be delayed.

**Step 10: Contingency fulfilment**

Inventory Management confirms that another 200 units are available at a third distribution centre.

Incident Management authorises an emergency replacement shipment, subject to the normal regulatory, quality, and financial controls.

The replacement shipment is dispatched.

The original shipment remains quarantined.

**Step 11: Delivery**

The first shipment arrives and is accepted.

The replacement shipment arrives later and is also accepted.

The hospital confirms receipt of the full 500 units.

The original shipment remains unresolved because of its temperature excursion.

**Step 12: Quality disposition**

The manufacturer assessment determines that the original affected products cannot be released.

Quality Assurance authorises their destruction.

The system records the affected batch quantities and updates inventory.

**Step 13: Financial reconciliation**

The invoice is generated for the 500 accepted units.

The additional emergency transportation cost is reconciled against the authorised exception.

The quarantined and destroyed products are excluded from customer billing.

**Step 14: Closure**

The delivery and financial processes are complete.

The original shipment's quality incident remains independently tracked until destruction is verified.

The customer order can be closed once all applicable operational and financial conditions have been satisfied, with the incident retained as a linked record for audit.

---

# 23. Expected Behaviour of the System

AURORA is not a simple linear order-processing workflow.

It is a coordinated network of workflows with:

- Multiple independent entry points.
- Parallel validation processes.
- Conditional routing.
- Human approval gates.
- Time-based escalations.
- Event-driven triggers.
- Inventory allocation loops.
- Retry mechanisms.
- Partial completion.
- Multiple shipments per order.
- Multiple orders affected by a single recall.
- Independent shipment and order lifecycles.
- Cross-department incident management.
- Compensation and recovery processes.
- Audit and reconciliation requirements.

The same operational entity may participate in several workflows simultaneously.

For example, a shipment may be delayed while a quality incident is being investigated, while its associated order is awaiting partial delivery and its invoice is awaiting reconciliation.

These activities must remain coordinated without incorrectly blocking unrelated operations.

The system must preserve dependencies between processes while allowing independent activities to proceed whenever doing so is safe and permitted.

**End of specification.**
