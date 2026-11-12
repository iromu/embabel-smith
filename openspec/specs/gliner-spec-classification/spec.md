# gliner-spec-classification Specification

## Purpose

Select a single spec from a same-typed candidate set using a transformer-based zero-shot
classifier, and measure its selection accuracy against lexical and dense baselines on the
spec-shadowing scenario.

## Requirements

### Requirement: Select one spec from a candidate set

The classifier SHALL select the single most relevant spec from a provided candidate set for a
given task query, ranking the candidates by the classifier's relevance signal and returning the
top hit.

#### Scenario: Selects the top-ranked candidate
- **WHEN** a task query is classified against a candidate set containing the canonical spec and related near-duplicate specs
- **THEN** the classifier returns exactly one selected spec — the top-ranked candidate

#### Scenario: Empty candidate set
- **WHEN** a task query is classified against an empty candidate set
- **THEN** the classifier returns no selection

### Requirement: Report selection accuracy across methods on identical candidate sets

The benchmark SHALL evaluate the transformer classifier, the lexical baseline, and the dense
baseline on the same candidate sets, and report Precision@1 for each method across the benchmark
targets so their results are directly comparable.

#### Scenario: One Precision@1 per method
- **WHEN** the benchmark runs over its six targets
- **THEN** it reports a Precision@1 value for each of the three methods (transformer, lexical, dense)

#### Scenario: Methods scored on identical candidates
- **WHEN** the three methods are evaluated for a given target
- **THEN** each is scored against the identical candidate set for that target

### Requirement: Measure the shadowing scenario in two candidate-set variants

The benchmark SHALL measure selection accuracy under within-topic shadowing in two candidate-set
variants: a POC-12-matching set that includes every near-duplicate of a target, and a strict set
that excludes the near-duplicate that is a near-exact duplicate of the canonical spec.

#### Scenario: POC-12-matching variant
- **WHEN** the POC-12-matching variant is measured
- **THEN** each target's candidate set includes the canonical spec and every one of its near-duplicates

#### Scenario: Strict variant
- **WHEN** the strict variant is measured
- **THEN** each target's candidate set excludes the near-duplicate that is a near-exact duplicate of the canonical spec

### Requirement: Run as a local-model-gated benchmark

The benchmark SHALL run end-to-end only when the transformer model is available locally, SHALL
report a clear, actionable error when the model is absent, and SHALL allow its non-model logic to
be verified offline without the model.

#### Scenario: Model present
- **WHEN** the benchmark is launched with the model available locally
- **THEN** it runs the full three-method comparison and reports the results

#### Scenario: Model absent
- **WHEN** the benchmark is launched without the model
- **THEN** it reports a clear error identifying that the model bundle is missing and how to obtain it, rather than an opaque failure

#### Scenario: Offline verification without the model
- **WHEN** the offline test suite runs without the model
- **THEN** the selection, benchmark, and baseline logic are verified with the classifier mocked, and the build stays green
