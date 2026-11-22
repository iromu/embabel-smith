# Design

## Context

The builder can already run packs: `eval/AgentSpecInvokerFactory` drives `AgentPlatform` and
`StepSpecAgentScopeBuilder` directly, and `com.embabel.smith.runtime` appears zero times in builder
`src/main`. Three builder packs exist and load, validate and deploy
(`builder-agent-specs/`, `core/BuilderAgentSpecPackTest`). What does not exist is a build that is *driven*
by one: `BuildCommand` news `BuilderAgent` (`:112`) and `BuilderService` calls its three actions in written
order (`:134,141,146`), while `SmithBuilderApplication:40` keeps the `@Agent` class off the component scan
so the planner never sees it.

Two facts from the preceding change constrain the design. Task 8.1 measured that a step's declared
`toolGroups` string reaches the compiled action as a `ToolGroupRequirement` role and is resolved only at
prompt time by the platform's resolver — so a group name is a *request*, and whether it yields tools is a
live-platform question. And `@LlmTool`/`@Tool` count zero in this module, so today a builder step could only
ever name upstream's built-in groups (`web`, `math`, `maps`, `github`, `browser_automation`), none of which
is a builder service.

## Goals / Non-Goals

**Goals:** the shipped single-spec build runs from a pack that can call the validator; the deterministic
gates are provably identical on both flows; the default changes only against recorded numbers.

**Non-goals:** making the builder's flow multi-step; deleting the Java stages; measuring whether
decomposition helps generation (POC 24 answered that for generation, and this change does not reopen it).

## Decisions

### D1 — Agency is what a step can call, not how many steps there are

The shipped single-spec build is one model call. Expressed as a pack, it stays one model call: a single
step, one prompt, and a declared tool group that lets that step call `AgentSpecValidator` and re-draft
against the findings it gets back. That is the whole argument of this change, and it is deliberately not the
argument "let a planner sequence the builder's stages".

The distinction matters because the two are easy to conflate and the objections differ. A multi-step pack
pays N calls and buys decomposition, which POC 24 measured as a loss for generation. A one-step pack with
tools pays the same call and buys self-repair. Conflating them would let POC 24's result be cited against
this change, where it does not apply.

*Rejected*: treating the multi-step `builder-single-spec` pack as the shipped path. It is the agentic
decomposition, it costs five calls, and it is the wrong artifact to make load-bearing for `smith build`.

### D2 — The shipped flow gets its own pack

`builder-spec-one-shot/` sits beside the three landed packs, transcribed from the same
`defaultPrompt()` the shipped generator reads, with the rules folded into one step prompt rather than
redistributed. It is a fourth directory rather than a mode of `builder-single-spec` because the two encode
different claims — one says "this is what the build does", the other says "this is how the work decomposes"
— and a flag that silently changes a pack's granularity would make a mutation diff meaningless: the mutable
surface of a one-step pack is one prompt, and a generation editing it should not also be editing a pack that
means something else.

The same transcription discipline as the landed packs applies: no gate, scan or write step; every step
output a named type from the pack's own `types/`; every optional field written out, empty where it is empty.

### D3 — One flag, landed off; the default flips in a separate commit

`--flow java|pack`, defaulting to `java`. Generation technique (`--technique`) is a different axis and stays
independent: the pack flow drives the same generator the Java flow drives today, so a pack flow with the
embabel technique must either work or refuse loudly, not quietly fall back.

The capability and the default are separate commits on purpose. Landing `--flow pack` changes nobody's
build; flipping the default changes everybody's, and it should be possible to point at the parity and cost
numbers when it happens.

*Rejected*: `--flow pack` as the default immediately — it would make an unmeasured behaviour change the
product's behaviour. *Rejected*: a `smith.builder.flow` configuration property — a flag is per-invocation
and visible in the transcript, which is what a comparison needs.

### D4 — No fallback

If the pack flow fails, the build fails with the pack flow's error. A fallback to the Java flow would
produce a build that succeeded and a record that says a pack flow ran, which is the worst of both: it hides
the failure rate this change is trying to measure and it makes every parity number untrustworthy. The one
exception is a refusal *before* any call is spent — an unresolvable tool group, a pack that fails to load —
which stops with a named reason rather than starting work and then choosing to abandon it.

### D5 — Registering `BuilderAgent` with the platform is not the switch

Removing the exclude filter at `SmithBuilderApplication:40` would make GOAP plan over the builder's
Java-declared actions in roughly the order `BuilderService` already hardcodes, changing the trace without
changing what is mutable or what is reachable. It is agency in name: nothing becomes editable by a
generation, and the agent still has no tool to call. It is also the version of this that would be easiest
to claim as done, which is why it is named here as rejected rather than left as an obvious shortcut.

### D6 — Four numbers decide the default flip

1. **Resolution.** The step's agent can call at least one deterministic builder tool on a booted platform.
   After D7 that is a property of a declared group backed by a registered `ToolGroup` bean, since the
   driver's tool list turns out to be unreachable from a step. Without this, `--flow pack` is the same single
   call with a decoration, and D1's whole claim fails.
2. **Parity.** On the same task set, the pack flow's validated-output rate is not worse than the Java flow's,
   reported as two numbers and a difference, not as a verdict.
3. **Cost.** Calls per build, expected to be equal to the Java flow plus the validator calls the step
   actually makes — and a validator call the agent never makes is itself a finding.
4. **Containment.** The scorer-out guard is green, so widening a step's reach cannot reach the three prompts
   that decide whether a generated artifact is good.

*Rejected*: flipping on 1 alone. Resolution without parity is a mechanism nobody measured, and the parity
gate is the one that would catch a regression POC 24 already showed to be plausible in the neighbouring
case.

### D7 — Two routes give a step a tool; only one of them a step can actually use

Task 1.2 found no stock group name that resolves to a builder tool, and then found that a name can be made.
Both routes are therefore open and the choice between them is a real decision, not a workaround.

**Route A, declarable:** register a builder-owned `ToolGroup` bean using the recipe this repo already
contains in frozen POC 7 evidence (`ShellToolGroupFactory` resolves the mangled Kotlin companion factory by
signature through reflection, `ToolGroup.Companion.ofTools` does the rest, `ShellToolGroup` publishes it with
`@Bean`), then have the step declare that name. This is the route the format was designed for and the one
the scanner can see.

**Route B, host-wired:** pass `Tool.fromInstance(new AgentSpecValidationTool())` into the
`List<Tool>` argument `StepSpecAgentScopeBuilder` already takes. No reflection, and reach becomes a property
of the driver — identical for every pack, and outside the directories a generation edits.

**Decision: A, superseding an earlier choice of B.** This page first picked B, on the containment argument
below. That choice was made before either route was measured, and the measurement killed B rather than
weakening it: upstream `PromptedActionSpec.emit` builds an action's tool list by iterating the step's *own*
`tools:` names and resolving each against the scope's `Tool` list, dropping a miss, and `smith-common`'s
`ActionStepSpec` has thirteen components with no `tools` among them — so a pack has no field with which to
select a host-wired tool. Measured both ways on one live case: scope-wired plus `toolGroups: [validate]` gave
`starting tool loop []` with `WARN ToolConsumer - Could not resolve tool group with role='validate'`;
registering the group gave `starting tool loop [validatePack]`. B is not a safer route, it is a route that
does nothing, and `eval/AgentSpecInvokerFactory`'s scope tool list is empty again.

What the containment worry predicted is now true and is bounded rather than dismissed. Reach moved into pack
text for the two steps that declare `validate` (`builder-spec-one-shot/steps/draftAgentSpec.yml`,
`builder-single-spec/steps/assembleAgentSpec.yml`), so what a pack's agent is *offered* is partly a pack
declaration. Three things keep that short: `Refiner.withPrompt` copies the other twelve components, so no
candidate *adds* a group; registering the group moved no trust tier, because `validate` is in none of
`TrustTier`'s allowlists and both packs still read `T4_PLATFORM` through T4's `*`; and the shipped path still
calls the Java generators, because 5.1 declined the default flip on 4.2's and 4.3's numbers. So no candidate
changes what an ordinary `smith build` does — now because the default did not move, which is a different
dependency than the one this paragraph originally claimed.

Route A's own cost is stated plainly in `tools/ValidationToolGroup`: the metadata factory is found by
signature through reflection, so an Embabel bump turns it into a named `IllegalStateException` rather than a
silent loss — and because that throw happens in a `@Bean` factory, it fails the Spring context for *every*
builder command, `--flow java` and `smith blueprint` included, not only the pack flow. It also needs a
GraalVM reflection entry for `ToolGroupMetadata$Companion`; the builder's current
`META-INF/native-image/.../reachability-metadata.json` carries only a resource glob for the class, so a
native builder would fail at boot until that entry exists.

*Rejected*: route B, which this page first chose. It is not a containment mechanism, it is an inoperative
one, and a flow that silently offers no tools is worse than one that offers tools a reviewer can see
declared.
*Rejected*: both at once (register the group *and* wire the tools), which would make it impossible to tell
from a pack which mechanism served a call and would double the ways a step can silently lose its tool.
*Rejected*: a Kotlin source file added to the builder to author the bean cleanly. It buys route A's
declarability at the price of a second language in a Java module, and POC 7's signature lookup already does
it in Java.

## Risks / Trade-offs

- **A tool group that resolves to nothing makes the pack flow indistinguishable from the Java flow**, and the
  offline test suite cannot tell you. Mitigated by gate 1 being a live-platform assertion, and by the
  preceding change's finding that a misspelled group is carried verbatim and rejected nowhere.
- **No stock tool-group name reaches a builder tool, and the tier moves anyway.** The `default-llm`
  hypothesis this page first carried is dead: `@LlmTool` has no group member and `api/annotation/ToolGroup`
  is read by nothing in the 1.5.4 jars. A name *can* be registered — POC 7's reflective factory proves the
  recipe — and D7 registers exactly such a bean for the role the shipped packs declare, so the declaration is
  the mechanism for `validate` and still a statement of ambition for any other name, a misspelling included. Worse than the missing capability is what the scanner makes of it:
  `TrustTierPolicy.check` compares declared *strings*, so any declaration moves
  `AgentSpecSecurityScanner.recommendedTier()` — an unresolvable one to `T4_PLATFORM`, since T4 allows `*`.
  A pack can therefore look maximally privileged while reaching nothing, which is why containment here rests
  on gate 1 and on the scorer-out guard rather than on the tier, and why a tier's movement must never be
  cited as evidence that a declaration works.
- **Two flows means two paths through one product**, and they can drift apart silently — particularly the
  gates. Mitigated by task 3.3: the gates are the same calls in the same order, asserted by a test that
  runs both flows over one fixture and compares the gate sequence, not the answers.
- **The one-step pack duplicates the shipped prompt**, so a generator prompt edit and a pack edit can
  diverge. This is the same duplication the landed packs already carry; the transcription diff is recorded
  in the task file and a parity test is what keeps it honest rather than a claim that they match.
- **`--flow` invites a third value later** (per-stage flows, hybrid). Not designed for; adding a value is a
  change with its own argument.
