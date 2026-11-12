# unified-builder-ui Specification

## Purpose

The unified builder UI: a single browser surface that exposes the proven Embabel Smith POC
capabilities (run, author, compose, retrieve, eval, lifecycle, security, trust, remote, scale) over
a thin REST surface, with per-pane offline / endpoint / docker gating.

## Requirements

### Requirement: Every proven capability is exposed as a pane
The unified builder UI SHALL expose each proven POC capability — run, author, compose, retrieve,
eval, lifecycle, security, trust, remote, and scale — as a distinct pane, each backed by a
corresponding server endpoint that the pane invokes and whose response the pane renders.

#### Scenario: A pane drives its backing endpoint
- **WHEN** the user submits an action in the run pane
- **THEN** the pane calls its run endpoint and renders the returned plan output

#### Scenario: All ten panes are reachable
- **WHEN** the UI loads
- **THEN** all ten panes are reachable from the navigation and each is backed by a distinct endpoint

### Requirement: Offline panes work without a model endpoint
The compose, retrieve (default lexical scorer), security, trust, and scale panes SHALL function
fully without a model endpoint configured.

#### Scenario: Offline panes run with no endpoint
- **WHEN** no model endpoint is configured and the user scans a pack in the security pane
- **THEN** the scan returns its findings and verdict without requiring a model

### Requirement: Endpoint-gated panes degrade gracefully
A pane whose action requires a model endpoint SHALL, when no endpoint is configured, present a
clear "endpoint not configured" state instead of failing.

#### Scenario: Author pane without an endpoint
- **WHEN** no model endpoint is configured and the user submits a description in the author pane
- **THEN** the pane shows an endpoint-not-configured message and does not error

### Requirement: The canvas snap is type-checked
A connection between two specs SHALL be valid only when the source spec's output type is one of
the target spec's input types; an incompatible connection SHALL be flagged with the type-mismatch
reason.

#### Scenario: Compatible connection snaps
- **WHEN** the user connects a spec whose output type is an input of the target spec
- **THEN** the connection is valid and the composed chain updates

#### Scenario: Incompatible connection is flagged
- **WHEN** the user connects a spec whose output type is not an input of the target spec
- **THEN** the connection is flagged invalid with the type-mismatch reason

### Requirement: Retrieval is curation-aware
Retrieval results SHALL drop retired specs entirely and de-rank degraded specs, and SHALL stamp
each returned hit with its lifecycle state.

#### Scenario: A retired spec is excluded
- **WHEN** the library contains a retired spec matching the query
- **THEN** the retired spec is absent from the retrieval results

#### Scenario: Each hit carries its lifecycle state
- **WHEN** the user runs a retrieval query
- **THEN** every returned hit is stamped with its lifecycle state

### Requirement: The retrieval scorer is pluggable
The retrieve pane SHALL report which scorer is active, and a denser scorer (embedding, transformer,
or small-LLM) SHALL be substitutable for the default lexical scorer through the shared scorer seam
without changing the pane's interface.

#### Scenario: A denser scorer is active
- **WHEN** a denser scorer is wired in place of the lexical default
- **THEN** the retrieve pane reports the denser scorer as active and ranks by it

### Requirement: Remote panes are gated on the backing process
A remote pane (MCP tool or remote action) SHALL present a "backing process not running" state when
its sidecar or MCP server is unreachable, and SHALL drive the remote invocation and render the
result when it is reachable.

#### Scenario: Sidecar down
- **WHEN** the sidecar is not running and the user invokes the remote action
- **THEN** the pane shows a not-running state and does not crash

#### Scenario: Sidecar up
- **WHEN** the sidecar is running and the user invokes the remote action
- **THEN** the pane renders the remote action's result
