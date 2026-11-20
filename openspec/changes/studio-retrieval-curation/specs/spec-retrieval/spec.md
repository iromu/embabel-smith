# Spec Delta

## Purpose

Ranks the packs in a source against a natural-language task through a replaceable scorer, curates
that ranking with each pack's recorded lifecycle state, and shows the result with the scorer that
produced it — offline, with no model call.

## ADDED Requirements

### Requirement: Packs are ranked through a replaceable scorer
Retrieval SHALL rank a source's packs by a scorer reached through a single-method seam, and SHALL name
the active scorer in every response, so that replacing the scorer changes no endpoint, no response
shape and no view.

#### Scenario: A task is submitted
- **WHEN** a person submits a task query against a source holding several packs
- **THEN** the response lists the top-ranked packs in descending score order and names the scorer
  that ranked them

#### Scenario: A different scorer is wired
- **WHEN** a build supplies a different scorer implementation
- **THEN** the same endpoint and response shape serve the new ranking, with the new scorer named

#### Scenario: The requested count exceeds the library
- **WHEN** a query asks for more results than the source holds packs
- **THEN** every pack is returned and nothing is padded or repeated

### Requirement: An index entry carries the pack's meaning, not only its types
The text a pack is matched on SHALL be built from the pack's goal description and its step
descriptions together with its input and output type names, because a natural-language task is
worded in meaning words rather than type names.

#### Scenario: A task is worded in domain language
- **WHEN** a query shares vocabulary with a pack's goal or step descriptions but names no type
- **THEN** that pack is scored on the match and can be returned in the top-k

#### Scenario: A pack has no descriptions
- **WHEN** a pack's goal and steps carry no description text
- **THEN** the pack is still ranked, on its name and its type names

### Requirement: Retrieval is curation-aware
Retrieval SHALL drop a pack whose recorded lifecycle state is retired from the results and SHALL
reduce the score of a pack whose state is degraded, so the library is curated rather than merely
scored.

#### Scenario: A retired pack matches the query
- **WHEN** a retired pack would otherwise rank in the top-k
- **THEN** it is absent from the results

#### Scenario: A degraded pack matches the query
- **WHEN** a degraded pack is ranked
- **THEN** its score is reduced below the score the scorer gave it, and the state is reported with it

#### Scenario: A healthy pack matches
- **WHEN** a pack whose state is neither retired nor degraded is ranked
- **THEN** its score is the scorer's score, unchanged

### Requirement: A pack without a lifecycle record is treated as healthy
A pack that carries no lifecycle sidecar SHALL be surfaced at full score, because a pack authored but
not yet evaluated has no recorded contract to fail.

#### Scenario: A newly authored pack is retrieved
- **WHEN** a source contains a pack with no lifecycle sidecar and it matches the query
- **THEN** it is returned with the scorer's own score and a state reported as unknown

### Requirement: Retrieval makes no model call
Ranking SHALL be computed from the pack metadata already on disk and SHALL NOT call a model, so that
retrieval works on an installation with no endpoint configured.

#### Scenario: Retrieval with no endpoint configured
- **WHEN** a query is submitted and no model endpoint is configured
- **THEN** ranked results are returned rather than a configuration error

### Requirement: Degenerate retrieval input yields no results, not a failure
An empty query, and a source that holds no readable packs, SHALL produce an empty result set naming
the active scorer, and SHALL NOT be reported as an error.

#### Scenario: An empty query is submitted
- **WHEN** a query is submitted with no text
- **THEN** the response carries no results and names the scorer

#### Scenario: A source holds no packs
- **WHEN** a query is submitted against a source that resolves to no readable packs
- **THEN** the response carries no results

#### Scenario: A source URI cannot be parsed
- **WHEN** a request names a source the studio cannot resolve
- **THEN** the request is refused with the same error the other source-backed panes return

### Requirement: The scale benchmark stays out of the product
The product SHALL NOT offer a live Precision@1 benchmark pane, and the view at the scale route SHALL
keep presenting itself as POC-only reference, because that pane measures selector methods over a
synthetic library rather than answering a question about the operator's own packs.

#### Scenario: A person opens the scale route
- **WHEN** the scale pane is opened in the studio
- **THEN** it states that it is POC-only reference and offers no benchmark run

#### Scenario: A change proposes wiring the benchmark
- **WHEN** a proposal adds a scale endpoint to the product
- **THEN** it is refused unless it can rank the operator's own library rather than a generated one
