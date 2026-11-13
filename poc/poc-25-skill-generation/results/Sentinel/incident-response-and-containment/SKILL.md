---
name: incident-response-and-containment
description: Executes technical containment, eradication, and recovery of security incidents. Use when handling active threats requiring endpoint isolation, credential revocation, or indicator blocking, and when coordinating evidence preservation with forensics before destructive actions.
---

# Incident Response and Containment

This skill governs the technical lifecycle of an active security incident, from initial containment through eradication and recovery. It focuses on executing technical actions to stop threat activity while preserving evidence and adhering to strict approval workflows for high-impact changes.

## Core Workflow

### 1. Assessment and Planning
- Identify the scope of the incident (affected endpoints, accounts, and network segments).
- Determine the required containment actions (e.g., isolate host, disable user, block IP/domain).
- Classify the action impact:
  - **Low-Impact**: Pre-approved actions (e.g., isolating a single non-critical endpoint, blocking a known malicious IP). These can be executed immediately.
  - **Controlled/High-Impact**: Actions affecting critical systems, multiple users, or core services. These require human authorization.

### 2. Evidence Preservation Coordination
- **Before** executing any destructive or disruptive action (e.g., wiping a drive, rebooting a server, deleting logs), you MUST coordinate with the **FORENSICS** team.
- Confirm that volatile memory, disk images, or relevant logs have been captured.
- Do not proceed with destructive containment steps until Forensics confirms evidence preservation is complete.

### 3. Execution of Containment
- **Low-Impact Actions**: Execute immediately using pre-approved tools/scripts.
- **High-Impact Actions**: 
  - Submit a containment plan for approval.
  - Wait for explicit human authorization from the Incident Commander or designated approver.
  - Execute only after approval is received.
- Verify that the containment action has taken effect (e.g., confirm endpoint is isolated, credential is disabled).

### 4. Eradication and Recovery
- Once containment is verified, proceed to eradication (removing malware, closing backdoors).
- Initiate recovery tasks: restore services from clean backups, re-enable accounts, or re-image systems.
- Validate that the system is clean and functioning normally.

### 5. Verification and Handoff
- Confirm that the threat activity has ceased.
- Document all actions taken, approvals received, and verification results.
- Hand off to the reporting team for final regulatory reporting (this skill does not handle regulatory reporting).

## Decision Points

### Is the action destructive or disruptive?
- **Yes**: STOP. Contact FORENSICS. Do not execute until they confirm evidence is preserved.
- **No**: Proceed to impact classification.

### Is the action Low-Impact or High-Impact?
- **Low-Impact**: Execute immediately. Log the action.
- **High-Impact**: STOP. Submit for human approval. Do not execute until authorized.

### Has containment been verified?
- **No**: Re-assess. The threat may still be active. Do not proceed to eradication.
- **Yes**: Proceed to eradication and recovery.

## Escalation Rules
- **Forensics Conflict**: If Forensics cannot preserve evidence in time, escalate to the Incident Commander to decide between preserving evidence or stopping the threat immediately.
- **Approval Delay**: If high-impact action approval is delayed and the threat is spreading, escalate to the Incident Commander for emergency authorization.
- **Containment Failure**: If containment actions do not stop the threat, escalate to the Incident Commander for a revised strategy.

## Gotchas
- **Forensics First**: Never execute destructive actions (reboot, wipe, delete) without explicit confirmation from Forensics. A reasonable assumption that "the threat is urgent" does not override evidence preservation requirements.
- **Approval is Mandatory for High-Impact**: Do not assume that "critical" means "act now." High-impact actions require human authorization. Executing without approval is a policy violation.
- **Containment vs. Eradication**: Containment stops the bleeding; eradication removes the cause. Do not skip verification of containment before starting eradication. If containment fails, eradication will fail.
- **Scope Creep**: Only act on the identified scope. Do not isolate unrelated systems without explicit approval, even if they seem suspicious.
- **No Regulatory Reporting**: This skill ends at technical recovery. Do not draft or submit regulatory reports; hand off to the compliance/reporting team.
