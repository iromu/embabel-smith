# Proposal

## Why

**The builder's shipped path is not agentic, and the reason it is not is configuration rather than
capability.** `core/BuilderAgent.java` carries `@Agent(planner = PlannerType.GOAP)`, but
`SmithBuilderApplication.java:40` excludes that class from component scanning and
`cli/BuildCommand.java:112` constructs it with `new`, so no platform ever registers it and no planner ever
runs. `BuilderService.java:134,141,146` then calls `compileFromDescription`, `validateDraft` and
`writeAgentSpec` in numbered order — the plan the planner would have produced, written as Java. The
declaration is inert.

**The cost objection people expect here does not apply to the shipped flow.** A pack build is often
dismissed as paying one model call per step, which for a decomposed flow is true and is why POC 24 measured
single-shot ahead of multi-agent generation. The shipped single-spec build is already **one** model call,
so expressing it as a one-step pack that declares a tool group keeps the call count exactly where it is and
adds the one behaviour agency is actually for: the step's agent can call the validator, read its own
findings, and re-draft. Agency is a property of what a step can call, not of how many steps there are —
this change is built on that distinction, and the multi-step packs already landed under
`builder-agent-specs/` are a separate flow, not this one.

**The preceding change made this possible and deliberately did not use it.**
`poc27-improves-builder-agentspecs` landed three builder packs that load, validate and deploy
(`core/BuilderAgentSpecPackTest`), and its design D8 kept `cli/BuildCommand` on the Java generators on the
grounds that the packs are where the agentic form is *measured*. That was the right call while nothing could
be pointed at — `@LlmTool` and `@Tool` appear zero times in this module — and it stops being the right call
as soon as the validator is exposed as a tool with a group name that resolves. This change is the flip.

## What Changes

- A **one-step pack**, `builder-agent-specs/builder-spec-one-shot/`, transcribed from the same
  `SingleShotAgentSpecGenerator.defaultPrompt()` the shipped generator reads today, declaring the validate
  tool group on its single step. It is the shipped build expressed in the format, not a decomposition of it.
- `smith build` gains `--flow java|pack`, one flag on one axis, defaulting to `java` when this change lands.
- The pack flow deploys through `eval/AgentSpecInvokerFactory` — the builder already does this for its
  scorer with no dependency on `embabel-smith-runtime` — and every deterministic gate (validate, security
  scan, lifecycle stamp, publish) runs identically and in the same order, in host Java, on either flow.
- The **default flips only when the gates pass**, in its own commit, with the parity and cost numbers
  recorded in this change's task file. Landing the capability and flipping the default are separate acts.
- **No silent fallback.** A pack flow that fails reports failure; falling back to the Java flow would
  destroy exactly the comparison this change exists to make.

## Out of scope

- Deleting the Java stages in `BuilderService`. They stay as the `java` flow until a later change retires
  them, and this change does not pretend otherwise.
- Making a multi-step pack the shipped default. POC 24's live result argues against it and this change does
  not re-litigate it.
- Any change to `embabel-smith-runtime`, the studio's REST surface, or the publish path.
- Registering `BuilderAgent` with the platform so its Java-declared actions get planned. Design D5 states
  why that is agency in name only.

## Impact

- **Spec:** new capability `builder-flow-execution` (which driver runs a build, and what may differ between
  them). The durable account lives in `docs/08-builder.md`, which this change updates when the default flips.
- **Code:** `cli/BuildCommand`, a pack-driven flow beside the Java one, one new pack directory. No new
  module dependency: the builder already deploys packs.
- **Depends on:** `poc27-improves-builder-agentspecs` tasks 8.2 (the validator exposed as a tool), 8.3 (the
  group name on a builder step) and 8.5 (all three packs deployed in one scope).
