# Design

## Context

The builder's compile step is a single Embabel path today: `BuilderAgent.compileFromDescription`
calls the `AgentSpecCompiler` seam (`DefaultAgentSpecCompiler` →
`DefaultNaturalLanguageCompiler.compileAgent`), then `DraftToAgentSpec` maps the result to the
shared `AgentSpec`. The studio, meanwhile, carries its own single-shot generator
(`ProcessFlowGenerator`: a `process_to_flow` prompt → model call → JSON parse → normalize) plus the
embabel path (`NlCompilerFlowGenerator`, which already reuses the builder's `AgentSpecCompiler` /
`DraftToAgentSpec`). The studio's `generateFn` dispatches on a `technique` field that the builder
does not have. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- The builder generates via single-shot (default) or embabel, selected per build.
- Generation logic lives in the builder; the studio delegates to it and no longer owns a generator.
- The downstream pipeline (validate → eval → security → lifecycle → publish) is unchanged — both
  techniques emit the same `AgentSpec`.

**Non-Goals:**
- Multi-agent / process generation (that is change C).
- Agent Skill output (that is change B).
- Changing the studio's user-facing generate behavior (selector, prompts surfaced in the UI).

## Decisions

- **Technique-level seam returning the shared format.** Introduce an `AgentSpecGenerator` seam
  whose result is the builder's own `CompiledDraft` — a pack name, the shared `AgentSpec`, and the
  issues the generator surfaced — so no Embabel-specific type (`AgentCompilationResult`) crosses
  the seam. The draft record, rather than a bare `AgentSpec`, is what keeps the existing
  compiler-issue channel (an ambiguous description blocks at `build` with the draft retained).
  Two implementations:
  `SingleShotAgentSpecGenerator` (prompt → model → parse → normalize) and
  `EmbabelAgentSpecGenerator` (wraps the existing `DefaultAgentSpecCompiler` + `DraftToAgentSpec`,
  so the `void` → `BackgroundMessage` goal normalization is preserved). `BuilderAgent` holds the
  generators and selects by technique; `BuilderService` passes `options.technique()`.
  - *Alternative considered:* keep the existing `AgentSpecCompiler` (Embabel-only) and add a
    separate single-shot path. Rejected — it keeps the Embabel-specific type in the seam and leaves
    the two techniques shaped differently, which is exactly the asymmetry this change removes.

- **Single-shot reuses the builder's `Ai` model layer.** The single-shot generator renders the
  `process_to_flow` prompt (moved into the builder's resources) and passes it to the existing
  `Ai`/endpoint the builder already uses for the embabel technique — no new raw HTTP caller or
  endpoint config. The JSON answer is parsed (fence-stripped, unknown properties ignored) and
  normalized (manifest, exported goal, canonical prompt vars) to an `AgentSpec`, mirroring the
  proven studio/POC 24 logic.
  - *Alternative considered:* a raw OpenAI-compatible caller (as in POC 24 / the studio's
    `LlmFlowPromptCaller`). Rejected for this change — it would add a second model path to the
    builder; `Ai` already covers the call and keeps the builder on one model layer.

- **Studio delegates.** `StudioConfiguration.generateFn` routes both techniques to the builder's
  engine; the studio's `ProcessFlowGenerator` (and, with it, the generate-only `FlowPromptCaller` /
  `LlmFlowPromptCaller` if nothing else uses them) and `NlCompilerFlowGenerator` are removed. The
  `GenerateRequest.technique` field now drives the builder's generator directly.

- **The eval invoker runs the pack's own goal.** Found during verification: the single-shot
  technique could never pass the eval gate. `AgentSpecInvokerFactory` invoked the built pack with
  `AgentInvocation.create(platform, BackgroundMessage.class)`, which only matches a pack whose goal
  outputs `BackgroundMessage`; the `process_to_flow` prompt asks the model for the *deliverable*
  type (e.g. `CoffeeMachineExplanation`), so no agent was ever found and the build aborted. Only
  the embabel path survived, and only when `DraftToAgentSpec` happened to normalize a degenerate
  goal to `BackgroundMessage`. The invoker now runs the deployed pack's own goal through
  `platform.runAgentFrom` — the same path the product runtime uses — so a pack is evaluated
  whatever type its goal produces, and a goal that is not achieved returns no output, which fails
  the eval task and lets the refinement loop act on it instead of aborting the build.

## Risks / Trade-offs

- [Single-shot is non-deterministic and weaker on multi-agent] → This change only adopts single-shot
  for the single-pack path (its strongest case per POC 24); multi-agent is deferred to change C
  with its own validator. The deterministic downstream gate (eval) still bounds the result.
- [The embabel technique cannot publish a multi-step pack] → Embabel's NL compiler returns its
  actions with no intermediate types — it omits any type a step already outputs — so step and goal
  output types reach the validator undeclared and the build blocks at `build` with
  `DANGLING_TYPE_REF` (POC 24: 0/6 valid). Accepted for this change: the technique stays selectable
  and its output is still the shared `AgentSpec`, but `LiveBuildIntegrationTest` asserts it at the
  generation seam while the single-shot default is asserted through to a published pack, and the
  README records the limitation. Synthesizing a declaration for each undeclared output would let it
  publish, at the cost of `types/` entries carrying names with no properties — deferred rather than
  done silently.
- [Changing the compile seam signature touches the studio] → The studio is refactored in the same
  change, and it already depends on the builder; the studio's observable generate behavior is
  unchanged, so no studio spec delta is required.
- [Two model paths in one build (embabel uses `Ai`; single-shot also uses `Ai`)] → Both go through
  `Ai`, so there is one model path; endpoint-gating and offline-testability (fake generator) are
  preserved.

## Migration Plan

No data migration. The default technique changes from embabel to single-shot; a user who relies on
the embabel behavior selects `--technique embabel` (or the studio selector). Rollback is reverting
the change (the embabel path is untouched and remains the available technique).

## Open Questions

(None — the technique set, default, and studio delegation are settled by the proposal.)
