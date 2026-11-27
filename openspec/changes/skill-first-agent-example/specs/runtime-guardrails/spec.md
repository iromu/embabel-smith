# Spec Delta

## Purpose

States what guards a run of a generic pack, where each guard lives, and what is deliberately not claimed.
A pack that follows any skill cannot be trusted to stay inside a task by its own wording, so the guards are
the deployment's; this delta says which ones exist, which are reachable from a declarative pack, and which
are stated as absent rather than invented as keys.

## ADDED Requirements

### Requirement: A run states what gates it

A run SHALL print, before it executes, the checks that will apply to it: the trust tier and what that tier
allows, the lifecycle states excluded, any content guardrail registered, and the budgets in force. A
reader deciding whether a pack is safe to run should not have to know the defaults.

#### Scenario: Defaults, nothing configured

- **WHEN** a run starts with no configuration
- **THEN** the report names the tier and its allowed roles, the excluded lifecycle states, no guardrails
  with the default stated, and the default budget

#### Scenario: A guardrail registered

- **WHEN** a guardrail is registered by configuration
- **THEN** the report names it and whether an error in it fails the run or is let through

#### Scenario: A budget is not the default

- **WHEN** a budget is set by configuration
- **THEN** the report shows the set value rather than the platform's default

### Requirement: Content guardrails are registered by configuration and their action is visible

The runtime SHALL support the platform's global guardrails — for user input and for assistant output —
selected by configuration, and a run SHALL record when one intervened: which guardrail, on what content,
what it did, and whether the platform retried as a result.

#### Scenario: A guardrail intervenes

- **WHEN** a registered guardrail rejects content
- **THEN** the record names the guardrail and the action taken, and any platform retries that followed are
  visible rather than appearing as slow steps

#### Scenario: No guardrail is registered

- **WHEN** a run starts with none configured
- **THEN** the report says no content guardrail is active, rather than implying content is checked

#### Scenario: A guardrail errors

- **GIVEN** a guardrail that throws rather than deciding
- **WHEN** the run meets it
- **THEN** the run's behavior matches the stated error policy and the report says which policy that was

#### Scenario: A named guardrail never loaded

- **GIVEN** a configuration that names a guardrail class the platform cannot instantiate, with the error
  policy left at its permissive default
- **WHEN** the run reports its posture
- **THEN** the guardrail is listed as absent rather than as active, because the report states what the
  registry holds and not what the configuration asked for

### Requirement: Bounds a step cannot declare are set by the deployment

Because a step cannot declare a tool-call limit, a retry count or a timeout, the deployment SHALL be able to
set the bounds the platform does support — action, cost and token budget, the tool-loop iteration ceiling,
and what happens when a model calls a tool that does not exist — and a run SHALL say which bound ended it
when a bound ends a run.

#### Scenario: A run ends on a budget

- **WHEN** a run exhausts its action or token budget
- **THEN** the outcome names the budget that ran out, not a generic failure

#### Scenario: A tool loop hits its ceiling

- **WHEN** a step's tool loop reaches the configured ceiling
- **THEN** the run records the ceiling as the reason the step stopped

#### Scenario: A model calls a tool that does not exist

- **WHEN** a model names a tool that was not offered
- **THEN** the configured policy applies and the record shows it, rather than the loop quietly consuming its
  remaining iterations on corrections

### Requirement: An action that cannot be taken safely stops with a reason

Where a step's own check decides that an action needs something the run does not have — a capability that is
not registered, a tier that forbids it, an approval the deployment does not grant — the run SHALL end that
path with a named reason and what would unblock it, and SHALL NOT attempt the action to see what happens.

#### Scenario: A blocked path is reported as blocked

- **WHEN** a step determines it cannot proceed safely
- **THEN** the run records the reason and the specific unblock, and no partial effect was made

#### Scenario: The unblock is actionable

- **WHEN** the report states what would unblock the path
- **THEN** it names the switch, tier or capability, in the form an operator can use

### Requirement: Human involvement stops the run with a question, and that is stated

The runtime SHALL NOT pause a run waiting for a person, and a pack that needs approval SHALL end with a
blocked-for-decision outcome that names the decision, the evidence the pack gathered for it, and the exact
command to continue. Durable pause and resume SHALL be recorded as out of scope for this change with the
reason, rather than implied by a step key.

#### Scenario: Approval is needed

- **WHEN** following a skill requires a decision the pack is not allowed to make
- **THEN** the run ends with the question, the evidence and the continuation command, and nothing further
  was executed

#### Scenario: No pause is pretended

- **WHEN** a pack author writes a step key meaning wait for approval
- **THEN** validation reports it as not implemented, because the runtime has no pause and no resume surface

#### Scenario: The boundary is documented

- **WHEN** a reader looks for what human involvement costs
- **THEN** the documentation states that a synchronous run cannot hold a process open for a person, and what
  a durable version would require

### Requirement: Guardrails do not substitute for the tier

A guardrail SHALL NOT be presented as a way to widen or narrow what a pack may reach. The tier check and the
lifecycle exclusion remain the gates on reach; a guardrail inspects content and bounds work, and a run
SHALL report the two kinds of stop separately.

#### Scenario: A blocked declaration and a rejected content

- **WHEN** one run is blocked by the tier and another is stopped by a guardrail
- **THEN** the two outcomes are described differently, and neither is reported as the other

#### Scenario: A guardrail cannot grant a role

- **WHEN** a pack declares a role the tier forbids and a guardrail is registered
- **THEN** the run is still blocked by the tier
