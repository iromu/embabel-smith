# Proposal

## Why

The builder's only production generation technique is Embabel's NL compiler — the approach POC 24
measured as the worse one (0/6 valid on the live matrix, versus 4/6 for single-shot). The studio
already supports both techniques behind a selector, but the builder — the product's generation
engine — has only the weaker one. This change makes the builder's generation technique selectable,
defaulting to the better single-shot approach, and moves the generation logic into the builder so
the studio (a thin UI) delegates to it instead of carrying its own copy.

## What Changes

- **Builder**: add a single-shot generation technique (one model call emits the whole spec as JSON,
  parsed and normalized to the shared `AgentSpec`) alongside the existing Embabel NL compiler. The
  compile step becomes technique-selectable at the `AgentSpec` level.
- **Builder**: a new `technique` build option (`single-shot` default | `embabel`) and a matching
  `--technique` CLI flag. Both techniques feed the same downstream pipeline (validate → eval →
  security → lifecycle → publish).
- **Studio**: remove its own single-shot generator and route its generate step through the
  builder's engine, so generation logic lives in one place. The user-facing technique selector is
  unchanged; only where the work happens moves.
- Default behavior improves (single-shot becomes the default). The embabel technique remains
  available, so nothing is removed from the user's reach.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `agent-spec-builder`: the build's generation step becomes technique-selectable (single-shot
  default, embabel alternative); both techniques produce the shared agent-spec format, so the rest
  of the pipeline is unchanged.

## Impact

- `prod/embabel-smith-builder`: a single-shot generator + its prompt resource, a technique-level
  compile seam, `BuildOptions` (new `technique`), the CLI, and the `BuilderAgent`/`BuilderService`
  wiring.
- `prod/embabel-smith-studio`: drop the local single-shot generator and rewire the generate step to
  the builder's engine (implementation change; the studio's observable generate behavior is
  unchanged, so no studio spec delta).
- `openspec/specs/agent-spec-builder`: one added requirement.
