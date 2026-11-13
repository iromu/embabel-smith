---
name: central-orchestration-and-coordination
description: Use this skill to manage central security operations coordination, including alert intake, case creation, priority assignment, agent task dependency resolution, stalled workflow detection, conflict resolution, and enforcing incident lifecycle transitions.
---

# Central Orchestration and Coordination

## Core Workflow

1. **Receive Alert**: Ingest the incoming security alert.
2. **Create Case**: Generate a new operational case record with a unique identifier.
3. **Assign Priority**: Determine investigation priority based on severity and impact.
4. **Coordinate Agents**: Dispatch tasks to specialist agents, managing parallel processes.
5. **Resolve Dependencies**: Ensure prerequisite tasks are completed before dependent tasks start.
6. **Track Completion**: Monitor progress of all active tasks within the case.
7. **Handle Exceptions**: Detect stalls, timeouts, or failures; apply recovery strategies.
8. **Resolve Conflicts**: Evaluate incompatible findings from agents.
9. **Enforce Lifecycle**: Validate state transitions and prevent premature closure.

## Decision Points & Rules

### Task Assignment & Dependencies
- **Parallelism**: Assign independent tasks to multiple agents simultaneously to reduce latency.
- **Dependency Check**: Before dispatching a task, verify that all its predecessor tasks have reached a terminal success state. If not, hold the task in a "waiting" state.

### Exception Handling (Stalls, Timeouts, Failures)
- **Detection**: Continuously monitor task status. A task is considered stalled if it exceeds the defined timeout threshold without progress updates.
- **Recovery Strategy** (Apply in order):
  1. **Retry**: If the failure appears transient (e.g., network error), retry the task up to the maximum retry limit.
  2. **Reassign**: If retries fail or the agent is unresponsive, reassign the task to an alternative specialist agent with similar capabilities.
  3. **Escalate**: If reassignment fails or the issue is systemic, escalate to human oversight for manual intervention.

### Conflict Resolution
- **Trigger**: When two or more agents provide incompatible findings for the same aspect of an incident.
- **Evaluation Criteria**:
  - **Evidence Quality**: Prefer findings backed by raw logs, packet captures, or direct system access over heuristic-based conclusions.
  - **Confidence**: Compare the confidence scores reported by the agents.
  - **Data Freshness**: Prefer findings based on the most recent data timestamps.
- **Resolution Logic**:
  - If one finding clearly outperforms others on the above criteria, adopt that finding and mark the conflicting ones as superseded.
  - If findings remain ambiguous or high-impact, escalate to a human Incident Manager for final adjudication.

### Incident Lifecycle Enforcement
- **Transition Validation**: Only allow state changes (e.g., Open -> Investigating -> Resolved) if all mandatory preconditions are met.
- **Closure Block**: An incident MUST NOT be closed if any mandatory task remains incomplete, failed, or pending escalation.
- **Concurrency Safety**: The completion of one process must not automatically close unrelated concurrent processes. Each process tracks its own completion state independently until the overall incident closure criteria are met.

## Escalation Rules

| Condition | Action |
|-----------|--------|
| Agent timeout/failure after max retries and reassignment | Escalate to Human Oversight |
| High-impact conflict between agent findings | Escalate to Human Incident Manager |
| Mandatory task blocked indefinitely | Escalate to Human Oversight |

## Gotchas

- **Do Not Auto-Close Concurrent Processes**: Completing one sub-process does not imply the entire incident is resolved. Always check for other active, unrelated processes before considering the case ready for closure.
- **Mandatory Tasks Are Non-Negotiable**: Even if an incident appears resolved, it cannot be closed if a mandatory task (e.g., forensic evidence collection) is still open. This prevents missing critical follow-ups.
- **Conflict Resolution Is Not Voting**: Do not use majority vote among agents. Instead, evaluate the *quality* of evidence. A single high-confidence, fresh, well-evidenced finding outweighs multiple low-quality speculative findings.
- **Timeouts Require Active Detection**: Do not wait for agents to report failure. Proactively poll or set watchdog timers to detect stalled workflows early.
- **Reassignment Must Preserve Context**: When reassigning a failed task to a new agent, ensure the new agent receives all prior context and partial results to avoid redundant work.
