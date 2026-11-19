# Spec Delta

## Purpose

Serves the no-code runtime to other agents over the A2A protocol — send message, send streaming
message, get task, cancel task, and the agent card — while reusing the Embabel A2A module
for the wire format, and keeps every request on the same run path as the CLI: same trust gate, same
planner, same trace record.

## ADDED Requirements

### Requirement: The wire format comes from the reused library
The A2A request and response types, the JSON-RPC envelope, the endpoint registration and the agent
card document shape SHALL come from the reused Embabel A2A module and the protocol model classes it
brings, and this repository SHALL NOT define its own task, message, part, artifact or agent-card
serializations.

#### Scenario: A response is produced
- **WHEN** an A2A operation returns a result
- **THEN** the serialized object is an instance of a class from the reused protocol model, not a
  locally declared type

#### Scenario: Someone proposes a local data class
- **WHEN** a change adds a repository-owned class that stands in for a protocol object
- **THEN** it is refused, because the reuse constraint exists precisely to stop the wire format
  drifting from the specification

### Requirement: The protocol version is pinned and declared
The endpoint SHALL serve the A2A protocol version the reused library implements, SHALL declare that
version in the agent card, and SHALL record in this change where it diverges from the current
published specification.

#### Scenario: A client reads the card
- **WHEN** a client fetches the agent card
- **THEN** the card states the protocol version the endpoint actually speaks

#### Scenario: A client expects the newest specification
- **WHEN** a client sends the PascalCase method names and expects discovery at the newest
  well-known path
- **THEN** the divergence is expected and documented, not treated as a defect in the endpoint

### Requirement: The network surface is a separate launcher
The A2A surface SHALL be hosted by a dedicated launcher module that is the only product module
carrying the A2A server dependency, and neither the command-line runtime nor the studio SHALL expose
an A2A endpoint.

#### Scenario: The command-line runtime is started
- **WHEN** `smith run` is executed
- **THEN** the application boots as a non-web application, performs the run and opens no listening
  port

#### Scenario: The studio is started
- **WHEN** the studio runs with the runtime on its classpath
- **THEN** no A2A endpoint is registered and a test asserts it

### Requirement: One send-message request is one run
Handling a send-message request SHALL invoke the same run service the command line uses, with the
configured trust tier, and SHALL produce exactly one trace record for the attempt.

#### Scenario: A message names a goal
- **WHEN** a valid send-message request arrives
- **THEN** one run is performed through the run service and one trace record is appended carrying
  the tier the run executed under

#### Scenario: The run fails for any reason
- **WHEN** the goal is unknown, the tier blocks a pack, or the run aborts
- **THEN** a trace record is still appended and the failure is reported as a failed task rather than
  as an HTTP error

### Requirement: A task is identified by the run it represents
An A2A task id SHALL be the identifier of the trace record written for the run that produced it, and
a run whose trace record could not be written SHALL be reported as a failure naming that cause.

#### Scenario: A task is fetched by id
- **WHEN** a client asks for a task by the id it was given
- **THEN** the task is reconstructed from the trace record with that run id

#### Scenario: No record exists for the id
- **WHEN** a client asks for an unknown id
- **THEN** the response names the id as unknown rather than returning an empty task

### Requirement: Listing tasks is out of scope
The endpoint SHALL NOT advertise a task-listing operation. The reused library dispatches a fixed
method set with no `tasks/list` branch and rejects that method before any host handler is consulted,
so listing cannot be served from behind it; this change adds no route of its own to work around that.
The deviation SHALL be recorded, together with the response a client actually receives, rather than
presented as supported behaviour.

#### Scenario: The published operation set omits it
- **WHEN** the surface is documented or its deviations are listed
- **THEN** five operations are named, not six, and the omission of listing is stated with its cause

#### Scenario: A client calls it anyway
- **WHEN** a client sends a `tasks/list` request
- **THEN** no host handler runs, and the recorded deviation explains what the client gets instead of
  a task list

### Requirement: Cancel reports what it can actually do
Cancelling a task SHALL return a task whose status states the real outcome, and SHALL NOT report a
cancelled state for a run that has already finished or that the runtime cannot interrupt.

#### Scenario: The run already finished
- **WHEN** a cancel request names a task in a terminal state
- **THEN** the response refuses and states that the task is already terminal

#### Scenario: The run cannot be interrupted
- **WHEN** a cancel request names a run the runtime has no way to stop
- **THEN** the returned status says so instead of claiming the plan was stopped

### Requirement: The agent card is derived from the configured source
The agent card SHALL be built from the packs of the configured spec source, listing exactly one skill
per exported goal that the configured trust tier allows, SHALL NOT be maintained as a separate
hand-written description nor read from the goals the platform currently holds, and a card request SHALL
fail rather than serve an empty skill list when the configured source cannot be read.

#### Scenario: A card is fetched before any run
- **WHEN** the endpoint serves a card and nothing has been run yet
- **THEN** the card lists the goals of the configured source rather than being empty

#### Scenario: The same source is run repeatedly
- **WHEN** a card is served after more than one run of the configured source
- **THEN** each exported goal still appears once

#### Scenario: A pack is disallowed by the tier
- **WHEN** a pack's goal is not permitted at the configured tier
- **THEN** that goal is absent from the card and attempting it anyway returns a failed task naming
  the tier

#### Scenario: The configured source cannot be read
- **WHEN** a card request cannot read the configured source
- **THEN** the request fails naming the source, and a later request succeeds once it is readable again

### Requirement: Streaming reports only what the run produced
The streaming operation SHALL emit the task, its artifact and a terminal status over the event
stream, and SHALL NOT emit progress events for work the runtime has not observed.

#### Scenario: A streamed run completes
- **WHEN** a client requests a streaming send and the run finishes
- **THEN** the stream carries the task, the artifact and the terminal status, then closes

#### Scenario: Step-level progress is requested
- **WHEN** a change proposes emitting a live event per plan step
- **THEN** it is refused unless the runtime first gains a per-step callback, because the run's steps
  are known only after it completes

### Requirement: No model call is added
The A2A surface SHALL reach the model only by executing actions on the agent platform through the run
service, and the new module SHALL NOT open its own connection to a model endpoint.

#### Scenario: The new module is inspected for model access
- **WHEN** the module's sources are examined
- **THEN** it contains no direct model request and the existing architecture guard's rationale for
  excluding the runtime still holds

### Requirement: The pinned dependency is established before implementation
No part of the A2A surface SHALL be implemented until the server dependency resolves, the protocol
model version it pulls is settled, and a request round-trip through the library's mapper is proven.

#### Scenario: The dependency does not resolve
- **WHEN** the pinned coordinates cannot be resolved from a configured repository
- **THEN** the change is reported blocked and the fallback to a hand-written protocol is escalated as
  a decision rather than taken silently

#### Scenario: Two protocol model versions compete
- **WHEN** the dependency graph offers two versions of the protocol model
- **THEN** the winning version is recorded from the dependency report before any code depends on it
