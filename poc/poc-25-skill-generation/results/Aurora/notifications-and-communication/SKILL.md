---
name: notifications-and-communication
description: Generates, routes, tracks, and logs system notifications for internal and external stakeholders based on specific business triggers. Use this skill when handling order status changes, regulatory/credit holds, stock issues, dispatch/delivery events, temperature alerts, recalls, returns, or incident escalations, ensuring strict data segregation and acknowledgement tracking.
---

# Notifications and Communication

This skill governs the end-to-end lifecycle of system notifications, ensuring the right information reaches the right stakeholders at the right time while maintaining strict confidentiality boundaries and audit trails.

## Core Workflow

1.  **Identify Trigger**: Determine the event type from the list of valid triggers (see *Notification Triggers* below).
2.  **Determine Recipients**: Apply recipient rules to select appropriate stakeholders (Customer, Internal Team, Regulator).
3.  **Filter Content**: Apply confidentiality filters to ensure no internal-only data is included in external communications.
4.  **Generate & Route**: Create the notification message and dispatch it via the primary channel.
5.  **Log Communication**: Record the notification details in the audit log immediately upon dispatch.
6.  **Monitor Acknowledgement**: For critical notifications, track acknowledgement status. If unacknowledged within the required period, trigger fallback channels.

## Notification Triggers

Generate notifications for the following events:
*   Order status changes
*   Regulatory holds
*   Credit holds
*   Stock shortages
*   Dispatch events
*   Delays
*   Temperature alerts
*   Delivery confirmations or discrepancies
*   Recalls
*   Returns
*   Incident escalations

## Recipient Rules & Data Segregation

**CRITICAL CONSTRAINT**: Information leakage of internal data to customers is a critical failure. You must strictly filter content based on the recipient type.

### 1. Customers
*   **Allowed**: Order status, dispatch info, delay notifications, delivery confirmations/discrepancies, recall notices, return instructions, temperature alerts (if affecting product integrity).
*   **PROHIBITED**: Internal quality investigations, security assessments, confidential supplier information, internal incident escalation details, credit hold internal notes.
*   **Rule**: If a notification involves a hold or incident, only send the *customer-facing* summary (e.g., "Order delayed due to compliance check") unless explicit authorization exists to share deeper details.

### 2. Internal Teams
*   **Allowed**: All operational data, including quality investigations, security assessments, supplier info, and full incident details.
*   **Rule**: Ensure relevant teams (Quality, Security, Supply Chain) are notified based on the trigger type.

### 3. Regulators
*   **Allowed**: Regulatory holds, recalls, and specific compliance-related incidents as mandated by law.
*   **Rule**: Use formal templates. Do not include internal speculation or unverified data.

## Handling Acknowledgements

Not all notifications require acknowledgement. Classify notifications as **Critical** or **Standard**.

### Critical Notifications
*   **Definition**: Notifications where failure to act poses significant risk (e.g., Recalls, Temperature Alerts, Regulatory Holds, Incident Escalations).
*   **Requirement**: Must receive explicit acknowledgement from the recipient.
*   **Fallback Protocol**:
    1.  Send via primary channel (e.g., Email/System Alert).
    2.  Monitor for acknowledgement within the defined SLA (Service Level Agreement).
    3.  **If no acknowledgement received within SLA**: Immediately escalate to alternative contact channels (e.g., SMS, Phone Call, Secondary Email).
    4.  Log the fallback attempt and its outcome.

### Standard Notifications
*   **Definition**: Informational updates (e.g., Dispatch confirmation, Standard status change).
*   **Requirement**: Delivery confirmation is sufficient; explicit acknowledgement is not required.

## Recording Communication (Audit Trail)

Every notification sent must be logged with the following fields. This log is immutable and auditable.

*   **Recipient**: Who received the notification.
*   **Timestamp**: Exact time of dispatch.
*   **Category**: The trigger type (e.g., "Recall", "Delay").
*   **Delivery Status**: Sent, Failed, Bounced.
*   **Acknowledgement Status**: Pending, Acknowledged, Timed Out, Fallback Triggered.

## Customer Communication Specifics

*   **Information Requests**: Respond to customer inquiries regarding order status or delays using only customer-safe data.
*   **Delay Notifications**: Proactively notify customers of delays. Include estimated new timelines if available. Do not disclose internal root causes if they involve confidential supplier or security issues.
*   **Delivery Confirmations**: Send upon carrier confirmation. If a discrepancy is detected (e.g., damaged goods, wrong item), notify the customer and initiate the internal discrepancy workflow simultaneously.

## Gotchas & Hard Constraints

*   **Internal Data Leakage**: Never include internal quality investigation notes, security assessment results, or confidential supplier names in customer-facing emails. If unsure, redact the detail and refer to a general status update. This is a critical compliance failure.
*   **Acknowledgement Fallbacks**: A critical notification is not considered "handled" until acknowledged. If the primary channel fails or is ignored, you **must** use alternative channels. Do not wait indefinitely for an email reply.
*   **Auditability**: Every communication, including failed deliveries and fallback attempts, must be logged. If a log entry is missing, the process is considered non-compliant.
*   **Regulatory Holds**: These often require specific regulator notifications in addition to internal alerts. Ensure the regulator is notified if the hold is legally mandated, not just internal.
*   **Temperature Alerts**: These are often time-sensitive. Treat them as Critical. If the recipient does not acknowledge, escalate immediately to prevent product spoilage or safety issues.
*   **Recalls**: These require multi-stakeholder notification (Customer, Internal, Regulator). Ensure all three streams are triggered and tracked separately in the audit log.
