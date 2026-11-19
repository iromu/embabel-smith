# Spec Delta

## Purpose

Gives the product a view of the remote-action sidecars an operator configured: whether each answers,
which actions it advertises, and the output of an action run on request — while claiming nothing about
a process the product did not start.

## ADDED Requirements

### Requirement: Configured bridges are listed with their reachability
The product SHALL list the remote bridges named in its configuration and SHALL report, for each,
whether it answered its own catalog endpoint, so a person can tell a dead process from a misconfigured
one.

#### Scenario: A sidecar is running
- **WHEN** the bridge list is requested and a configured sidecar answers its catalog endpoint
- **THEN** that bridge is reported reachable together with the actions it advertised

#### Scenario: A sidecar is not running
- **WHEN** a configured bridge's host refuses the connection or the request times out
- **THEN** that bridge is reported unreachable with the URL that was probed and the failure

#### Scenario: Nothing is configured
- **WHEN** the installation names no bridges
- **THEN** the list is empty and states that no bridge is configured

### Requirement: Advertised actions come from the sidecar, not from the product
The actions offered for a bridge SHALL be exactly those its own catalog returned, named as the sidecar
named them, and the product SHALL NOT advertise an action it invented.

#### Scenario: A sidecar advertises two actions
- **WHEN** a reachable bridge's catalog lists two actions
- **THEN** the pane offers those two by those names, and no others

#### Scenario: A sidecar advertises none
- **WHEN** a reachable bridge returns an empty catalog
- **THEN** the bridge is reported reachable with no actions, and nothing is offered to run

### Requirement: An advertised action is executed with one text input
The product SHALL run a named action on a named bridge by sending the operator's text as that action's
`UserInput` content, and SHALL return the output the sidecar produced, and only that output.

#### Scenario: An action is run
- **WHEN** a person names a reachable bridge, an advertised action and some input text
- **THEN** the product posts the action name with the text as its `UserInput` content and returns the
  content the sidecar replied with

#### Scenario: An action's inputs are not a single UserInput
- **WHEN** an advertised action declares inputs other than one `UserInput`
- **THEN** it is listed with its declared input types and marked as not runnable from this pane

#### Scenario: The sidecar rejects the action name
- **WHEN** the sidecar answers the execution with a non-success status
- **THEN** the response reports the status and the body the sidecar returned, without inventing a result

### Requirement: An unreachable bridge is a state, not a server error
A bridge that does not answer SHALL be reported through a successful response carrying its unreachable
state, and the product SHALL NOT surface an absent sidecar as an unhandled server error.

#### Scenario: Execution is attempted against a dead bridge
- **WHEN** an action is requested on a bridge that is not running
- **THEN** the caller receives the unreachable state naming the bridge, the URL probed and the reason

### Requirement: The product does not start or stop a remote process
The product SHALL NOT launch, stop, restart or supervise a sidecar or a container, and the view SHALL
present an absent bridge as one the operator starts outside the product.

#### Scenario: A bridge is unreachable
- **WHEN** a person opens the remote view and a bridge is unreachable
- **THEN** the view says the process is not running and offers no control that would start it

#### Scenario: A change proposes a start button
- **WHEN** a proposal adds process launching to the product
- **THEN** it is refused unless it also specifies port allocation, log capture and orphan handling

### Requirement: An unprobed transport is not reported as working
Where the product lists a remote transport it has not exercised, it SHALL report that transport as
configured and unprobed, and SHALL NOT present a derived or inferred signal as proof that it works.

#### Scenario: A configured MCP endpoint is shown
- **WHEN** the view shows a configured MCP endpoint
- **THEN** it is labelled as configured and not invoked, and no reachable badge is shown for it

### Requirement: Bridge addresses come from configuration
Bridge names and URLs SHALL be read from the product's own configuration, and a request SHALL be able
to select among the configured bridges but never supply an address the configuration does not contain.

#### Scenario: A request selects a configured bridge
- **WHEN** a request names one of the configured bridges
- **THEN** the product contacts that bridge's configured address

#### Scenario: A request carries an unknown or invented address
- **WHEN** a request names a bridge that configuration does not define
- **THEN** the request is refused and no outbound request is made
