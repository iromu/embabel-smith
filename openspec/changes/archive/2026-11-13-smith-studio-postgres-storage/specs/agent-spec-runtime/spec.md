# Spec Delta

## MODIFIED Requirements

### Requirement: Trace store is file-based
The trace store SHALL be pluggable behind a `TraceStore` interface, with the
JSONL file store (one JSON object per line, one per run) in a configurable
directory as the default implementation, defaulting to
`~/.smith/traces/runs.jsonl`; the directory is created on first write.
Alternative stores may be provided without changing the trace record shape,
and the CLI contract (`--trace-dir`) SHALL keep selecting the JSONL store.

#### Scenario: Append semantics
- **WHEN** two runs complete
- **THEN** the trace file contains exactly two lines, in completion order

#### Scenario: Corrupt line tolerated on read
- **WHEN** the trace file contains a malformed line
- **THEN** readers skip that line and report it as a warning rather than failing

#### Scenario: Default is the JSONL store
- **WHEN** the runtime runs with no alternative trace store configured
- **THEN** traces are written to the JSONL file exactly as before this change

#### Scenario: Alternative store selected
- **WHEN** an alternative `TraceStore` implementation is configured
- **THEN** runs are recorded through it with the same record shape and no change to run behavior
