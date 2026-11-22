# business-process-ingestion Specification

## Purpose
Turns business-process documents — a directory of specifications, or a repository holding them — into
the description a build consumes, so that documents are a first-class input to the builder rather
than something only a user interface can read.

## Requirements

### Requirement: Business-process documents load from a folder or a repository
The system SHALL load business-process documents from a local directory, or from a repository
shallow-cloned when a repository URL is given, and SHALL return each document keyed by its path
relative to the loaded root.

#### Scenario: Documents loaded from a directory
- **WHEN** documents are requested for a directory that holds markdown documents
- **THEN** each document is returned keyed by its relative path, in a deterministic order

#### Scenario: Documents loaded from a repository
- **WHEN** documents are requested with a repository URL and no directory
- **THEN** the repository is shallow-cloned and its documents are returned the same way

#### Scenario: A location with no documents
- **WHEN** documents are requested for a location that holds none
- **THEN** the request fails with a message naming that location

#### Scenario: Neither input supplied
- **WHEN** documents are requested with neither a directory nor a repository URL
- **THEN** the request fails with a message naming both accepted inputs

### Requirement: A document tree yields one bounded generation corpus
The system SHALL assemble the documents under a directory into a single blueprint corpus, labelling
each contribution with its relative path, and SHALL stop admitting documents once a size cap is
reached so that an oversized tree cannot exceed the model's context.

#### Scenario: Corpus assembled from documents
- **WHEN** a corpus is resolved for a directory of documents
- **THEN** the corpus carries each document's text with the path it came from

#### Scenario: Corpus past the cap
- **WHEN** the documents under a directory exceed the size cap
- **THEN** the corpus states the cap and the first document omitted, and admits nothing past it

#### Scenario: Blueprint supplied as text
- **WHEN** blueprint text is supplied with no directory
- **THEN** that text is the corpus, unaltered

### Requirement: A corpus extracts to a process description
The system SHALL turn a blueprint corpus into a business-process description by running the discovery
prompt through the platform's model layer, and SHALL treat the answer as plain text intended as build
input rather than as a specification to parse.

#### Scenario: Description extracted
- **WHEN** a corpus is submitted for extraction
- **THEN** the model's answer is returned as the description

#### Scenario: Extraction on a named model
- **WHEN** extraction is requested with a model named
- **THEN** the extraction call runs on that model

#### Scenario: Ingestion never bypasses the model layer
- **WHEN** the ingestion sources are checked for direct model transport calls
- **THEN** none is found, every call having gone through the platform's model layer

### Requirement: Documents are accepted as build input
The builder SHALL accept a business-process document location as input to a build, resolving and
extracting it into a description before generation begins, and SHALL continue to accept a plain
description in place of documents.

#### Scenario: Build from documents
- **WHEN** a build is requested with a documents location
- **THEN** the build runs on the description extracted from those documents and reports the same
  stages as any other build

#### Scenario: Build from a description
- **WHEN** a build is requested with a description
- **THEN** the build proceeds exactly as it did before documents were accepted

#### Scenario: Extraction yields nothing usable
- **WHEN** a build from documents cannot obtain a description
- **THEN** the build stops before generation and names ingestion as the stage that stopped it

### Requirement: The ingestion steps stay separately callable
The system SHALL make document loading, corpus assembly and description extraction available as steps
that a caller can invoke on their own, so that an interactive caller can show documents, then a
description, then a generated specification without any step forcing the next.

#### Scenario: Steps taken one at a time
- **WHEN** a caller loads documents, then extracts a description, then generates from it
- **THEN** each step is available on its own and none of them runs the following step

#### Scenario: Existing callers are unaffected
- **WHEN** an existing caller invokes the ingestion endpoints with the request shapes it uses today
- **THEN** the responses it receives are unchanged
