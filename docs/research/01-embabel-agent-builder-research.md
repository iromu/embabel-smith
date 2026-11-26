# Embabel "Agent Builder" — Design Document & Research Findings

Date: 2026-11-13 (rewritten; supersedes the 2026-10-30 edition)
Status: RESEARCH + DESIGN COMPLETE. This is the hypothesis the POC suite is read against
(`docs/04`), not a build log.

VERDICT (one line): FEASIBLE, not useless. Embabel already ships ~70% of the pieces —
a declarative YAML agent module that ingests specs without compilation, an NL→spec LLM
compiler, and an LLM eval harness. The missing piece is the builder UI itself.

Read that verdict with one caveat this edition exists to record: the YAML module is **not**
stable in the sense the 2026-10-30 edition claimed. It is a `1.5.4-SNAPSHOT` tracking a `1.5.3`
platform, and no `1.5.4` release artifact of it exists (§2.2). The 70% is real and it runs; it is
not yet pinned.

What this edition adds over the first: a second, larger body of evidence. Embabel has **two**
declarative-agent contracts, not one, and they sit at opposite ends of the maturity spectrum —
`embabel-agent-spec` (small, JVM, no-compilation, still a SNAPSHOT) and the **Embabel realm
spec** (large, production, shipped as the vendor's own appliance format, and now formally
TypeScript-first with YAML demoted to legacy). A builder that reads only the first will build
something the ecosystem is walking away from; a builder that reads only the second will build
something that cannot run on the JVM platform this project targets. Section 2 evidences both
against source; Section 3 designs for both.

Every claim below was checked against source on 2026-11-13 — the two upstream checkouts named in
Section 6, and the `embabel-agent-api` 1.5.4 **sources** jar rather than `javap` on class files.
Claims inherited from the 2026-10-30 edition that did not survive that check are called out
inline as **[corrected]**, because a design document that quietly keeps a wrong fact is worse
than one that never had it.

## SECTION 1 — WHAT EMBABEL IS (the concepts the builder maps onto)

Embabel (github.com/embabel/embabel-agent, Apache-2.0, from the creator of Spring) models agentic
flows as:

  Actions    steps with typed inputs (IoBindings "name:Type"), typed outputs,
             optional SpEL pre/post conditions, cost, value, canRerun,
             toolGroups, and an implementation (LLM prompt or code).
  Goals      what the agent achieves; name an output type + preconditions.
             Goal descriptions are what an LLM uses to pick goals.
  Conditions named predicates re-evaluated after each action.
  Domain     strongly-typed objects: JVM classes (JvmType) or pure data
             definitions (DynamicType — NO JVM class; this is what makes
             YAML-only agents possible).
  Plan       computed by a NON-LLM planner (default GOAP/A*), replanned after
             every action (OODA loop).

Authoring models that exist today:
  1. Annotations: @Agent class with @Action/@AchievesGoal/@Condition.
  2. Kotlin DSL:   agent { action { ... } goal { ... } }.
  3. YAML specs:   embabel-agent-spec  <-- the format a JVM builder emits.
  4. Realm packs:  embabel-worlds/realm-spec <-- the format an appliance builder emits.

Execution:
  AgentPlatform.deploy(agent | agentScope) — runtime deployment (the key fact:
  agents do NOT have to be compiled into the jar).
  runAgentFrom(agent, ProcessOptions, bindings) — start a process;
  ProcessOptions.withPlannerType(...) selects the planner per run.
  Modes: Focused (call a specific agent) / Closed (LLM picks an agent) /
         Open (LLM picks a goal, platform assembles an agent).
  Subagents: an @Action can spawn nested processes (asSubProcess); in YAML
  terms, exported goals (export: true) become callable goals.

Planners (verified against `com/embabel/agent/api/common/PlannerType.kt:22-51` in the 1.5.4
sources jar — the enum has exactly four members):

  GOAP       default; deterministic goal-oriented A* over pre/postconditions.
             needsGoals = true.  Best: business processes with defined outputs.
  UTILITY    picks highest-value available action each step; no fixed goal.
             needsGoals = false. Best: exploration / event-driven systems.
  HYBRID     utility-style picking, exits when a goal is satisfied.
             needsGoals = true.  Best: "gather context, then synthesize".
             Pair an unsatisfiable goal (NIRVANA) with the real terminal goal.
  SUPERVISOR LLM-orchestrated composition from type schemas.
             needsGoals = true.  Best: flexible multi-step workflows.

**[corrected]** The 2026-10-30 edition listed `@State` as a `PlannerType` value. It is not one —
the enum has only the four above. `@State` is a separate, annotation-level mechanism: annotating a
class hides the previous state object from the blackboard and restricts planning to actions from
the new state. A builder that writes `planner: STATE` into a manifest will fail to parse. The
`needsGoals` flag on each enum constant is the field a UI should read to decide whether to require
a goal, not a heuristic.

`ProcessOptions` (verified at `core/ProcessOptions.kt:236-327`): `plannerType` defaults to `GOAP`
(:251), `withPlannerType(...)` (:312) returns a copy, and `ProcessOptions.DEFAULT` (:327) is the
shared instance. `Budget` defaults are cost 2.0, 50 actions, 1,000,000 tokens (:165-172) — a
builder's "run" button should surface these rather than inherit them silently.

Tools & MCP:
  * toolGroups on actions (e.g. CoreToolGroups.WEB); MCP client config in
    application.yml (Spring AI MCP); agents can be EXPOSED as an MCP server
    (embabel-agent-starter-mcpserver). `@Export(remote = true)` on a goal
    auto-publishes it as an MCP tool.
  * @Tool / @LlmTool on domain-object methods exposes business logic to the LLM.
  * A2A protocol integration.
  * embabel-agent-remote (experimental): external HTTP servers register
    code-backed actions into GOAP planning. **[corrected]** Registration is
    PUSH, not pull: the remote calls the Embabel server's
    `POST /api/v1/remote/register` (README.md:110-134); the three endpoints
    (`GET /api/v1/actions`, `GET /api/v1/types`, `POST /api/v1/actions/execute`)
    are served *by the remote*, not by Embabel. `GET /api/v1/remote` on the
    Embabel side lists what is currently registered.

Guardrails (three orthogonal mechanisms, all verified in the 1.5.4 sources jar):
  * Content: `UserInputGuardRail` / `AssistantMessageGuardRail`
    (`com.embabel.agent.api.validation.guardrails`), Java interfaces. Per-call
    via `GuardRailConfiguration.withGuardRails(...)` (GuardRailConfiguration.kt:34),
    or GLOBAL via `embabel.agent.guardrails.user-input` /
    `.assistant-message`, comma-separated FQCNs — read by
    `spi/support/guardrails/GlobalGuardRailsRegistry.kt:49-52`. A CRITICAL
    violation throws `GuardRailViolationException`, which the retry policy
    deliberately does not retry (`spi/support/LlmDataBindingProperties.kt:59`).
    NOT YAML-declarable.
  * Plan-level: SpEL pre/post on actions, goal preconditions, Budget /
    EarlyTerminationPolicy in ProcessOptions. Declarable.
  * Access: `@SecureAgentTool` SpEL authority checks. Not YAML-declarable.
  * `PromptCondition` (`com.embabel.agent.experimental.primitive`,
    PromptCondition.kt:39-68) is the one LLM-evaluated condition: it costs
    `1.0` by construction ("as expensive as it can get") and takes a
    `(OperationContext) -> String` lambda plus an `LlmCall`, so it is
    programmatic, not declarable. It is also in an `experimental` package.

## SECTION 2 — THE DECISIVE EVIDENCE

### 2.1 Two contracts, two horizons

| | `embabel-agent-spec` | Embabel **realm** spec |
|---|---|---|
| Repo | `embabel/embabel-agent-experimental` | `embabel-worlds/realm-spec` |
| Runs on | the JVM, inside your Spring Boot app | an Embabel **host**/appliance (`ghcr.io/embabel/assistant`) |
| Unit | one YAML file per action/goal in a directory | a git repo (`realm-*`) of declarative files |
| Size | 15 main Kotlin files, ~1,560 lines | 5,041-line normative README + 6 companion specs, ~17,800 lines |
| Status | `1.5.4-SNAPSHOT`, built against `embabel-agent` **1.5.3** | living draft, actively edited (HEAD 2026-11-13) |
| Format | YAML only | `realm.ts` preferred; **YAML is legacy** |
| Planning | Embabel GOAP/Utility/Hybrid/Supervisor | host planner over typed graph + signals |
| Builder today | this project (`smith-common` AgentSpec) | nothing here |

They are not competitors and they do not subsume each other. `embabel-agent-spec` is the ingestion
engine for an agent **you run**; a realm is a capability pack **a host installs**. A builder aimed
at "declare and build agents without code" needs to treat the realm spec as the ceiling of what
"declarative" is allowed to mean, and `embabel-agent-spec` as the floor it can actually execute
today.

### 2.2 embabel-agent-spec — the no-compilation ingestion engine

**[corrected — material]** The previous edition titled this section "STABLE 1.5.4" and said the
module was "verified in the 1.5.4 RELEASE jar (repo.embabel.com libs-release; released alongside
the 1.5.4 platform — no longer 'experimental only')". That is wrong on the point that matters for
a builder's risk register:

* The upstream repo's root `../../pom.xml` is version `1.5.4-SNAPSHOT` and sets
  `<embabel-agent.version>1.5.3</embabel-agent.version>` — so the spec module's HEAD is built
  against the **1.5.3** platform, not 1.5.4.
* The module's own `../../pom.xml` depends on `embabel-agent-api` (managed by the
  `embabel-agent-dependencies` BOM at 1.5.3), `jackson-dataformat-yaml`,
  `jackson-module-kotlin`. Its test scope is `embabel-agent-test-internal`. Nothing Spring.
* In this machine's Maven repository, `embabel-agent-spec/1.5.3/` contains a real jar, and
  `embabel-agent-spec/1.5.4/` contains **only `.lastUpdated` marker files** — the record of a
  lookup that found nothing. There is no 1.5.4 release of this artifact. The builds in use are
  `1.5.4-SNAPSHOT` daily snapshots (the newest here is `1.5.4-20261113.054833-72`).

The practical consequence is narrower than it sounds: the class sets and the
`PromptedActionSpec` constructor of the published 1.5.3 release jar are **identical** to the
current snapshot, so the format is not actually churning. But a builder must not describe its
ingestion engine as "stable 1.5.4". It should say: *pinned to a 1.5.x release, tracking a
snapshot*, and isolate the spec layer behind an interface.

#### The contract, field by field (verified against the 2026-11-12 checkout)

`StepSpec` (`model/StepSpec.kt:46-54`) is one interface and one method:

    @JsonTypeInfo(use = Id.CUSTOM, include = As.PROPERTY, property = "stepType")
    @JsonTypeIdResolver(NameOrClassTypeIdResolver::class)
    @JsonSubTypes(Type(PromptedActionSpec::class, "action"),
                  Type(GoalSpec::class,        "goal"))
    interface StepSpec<T : AgentSystemStep> : NamedAndDescribed {
        val stepType: String
        fun emit(stepContext: StepSpecContext): T
    }

The discriminator is extensible without touching the module: `NameOrClassTypeIdResolver` resolves
in three steps — short names seeded from `@JsonSubTypes`, then `registerSubtypes()`/`@JsonTypeName`,
then **any FQN on the classpath via `Class.forName`** (`support/NameOrClassTypeIdResolver.kt:76-97`).
An unknown id throws naming the known names and telling you FQNs are accepted. A builder can
therefore ship custom step kinds as `stepType: com.example.MyStep` with no upstream change.

`PromptedActionSpec` (`model/PromptedActionSpec.kt:36-55`) — all seventeen fields, in order:

| Field | Type | Default | Note |
|---|---|---|---|
| `name` | String | — | also the emitted filename |
| `description` | String | — | read by the planner |
| `llm` | LlmOptions | `LlmOptions()` | per-action model/temperature |
| `inputTypeNames` | Set\<String\> | — | type names, not bindings |
| `outputTypeName` | String | — | `"void"` is a valid value |
| `pre` | List\<String\> | empty | condition strings — see §2.3 |
| `post` | List\<String\> | empty | condition strings |
| `cost` | ZeroToOne | 0.0 | steers GOAP A* |
| `value` | ZeroToOne | 0.0 | steers utility planning |
| `canRerun` | Boolean | false | loop behaviour |
| `prompt` | String | — | Jinja2 |
| `toolGroups` | List\<String\> | empty | matched on the group's **role** |
| `tools` | List\<String\> | empty | resolved by name against `StepSpecContext.tools` |
| `references` | List\<String\> | empty | resolved against `availableReferences` |
| `nullable` | Boolean | false | null output drives replanning |
| `export` | Boolean | false | true ⇒ auto-goal callable by the chat LLM |
| `stepType` | String | `"action"` | discriminator |

**[corrected]** The previous edition's constructor listing omitted `tools` and `references` and
recorded `canRerun` as a `String`. Both fields exist and both are wired: `emit()` resolves
`tools` by name against `stepContext.tools` and `references` against
`stepContext.availableReferences` (`PromptedActionSpec.kt:57-70`), and the runner passes them to
the prompt runner as `withTools(...)` / `withReferences(...)`
(`support/PromptedActionSpecAction.kt:105-110`). A builder UI that only exposes `toolGroups` is
leaving two capability surfaces on the floor.

`GoalSpec` (`model/GoalSpec.kt:32-59`) is `name`, `description`, `outputTypeName`, `export`,
`stepType`. Its `emit` is where the callable-unit story actually lives:

* the output type is looked up in the `DataDictionary`, and **falls back to a bare
  `DynamicType(name = outputTypeName)`** if absent (:43-44) — an undeclared goal type silently
  becomes a property-less dynamic type rather than an error;
* `export: true` becomes `Export(local = true, startingInputTypes = setOf(UserInput::class.java))`
  (:50-56), which is what makes the goal appear as a `GoalTool` the chat LLM can call.
  `Export` itself (`core/Goal.kt:170-175`) also carries `remote` and `name` — **the YAML format
  exposes neither**, so an exported goal cannot be published as an MCP tool from YAML alone.
  That is a real gap for a builder whose selling point is "publish this agent as an MCP server".

`StepSpecAgentScopeBuilder` (`StepSpecAgentScopeBuilder.kt:31-82`) is the whole ingestion story in
48 lines: read every spec, `emit` each against a `StepSpecContext`, and return

    AgentScope(name, description, actions, goals, conditions = emptySet())

**Three consequences a builder must design around:**

1. **Conditions are hard-coded empty.** `AgentScope`'s factory accepts `conditions`, `opaque`,
   `stuckHandler` and `referenceTypes` (`core/AgentScope.kt:128-152`); this builder passes none.
   Declarative named `Condition`s, a custom `StuckHandler`, and opacity are unreachable from YAML.
2. **Prefer `createAgent` + `deploy(agent)` over `deploy(scope)`.** The default
   `AgentPlatform.deploy(agentScope)` synthesises an `Agent` itself, dropping `stuckHandler` and
   `opaque` and stamping the provider as `Constants.EMBABEL_PROVIDER`
   (`core/AgentPlatform.kt:54-70`). `AgentScope.createAgent(name, provider, description)`
   (`core/AgentScope.kt:89-104`) preserves them. See §7.1.
3. **The `DataDictionary` you pass is the entire type universe.** Every emitted action is
   constructed with `domainTypes = stepContext.dataDictionary.domainTypes`
   (`PromptedActionSpec.kt:66`), and the scope's types are the union of its actions' types
   (`core/AgentScope.kt:64-65`). Project `DynamicType`s that are absent from the dictionary are
   not in the agent, and a `DynamicType` output then dies at *runtime* with
   `"Output type 'X' not found in agent schema types"`, not at load.

**[corrected — material] There is no DynamicType ingestion in this module.** The previous edition's
§7.4 sketched a launcher reading `types/*.yml` through `YmlStepSpecRepository`. That does not
work and never did. `YmlStepSpecRepository.findAll()` deserialises each file as a `StepSpec`, and
`StepSpec` is polymorphic on `stepType` — a `DynamicTypeDef` document has no `stepType`, so it
fails to deserialise, and `findAll` **catches the exception, logs a warning, and skips the file**
(`yml/YmlStepSpecRepository.kt:65-81`). A types directory pointed at this repository is silently
ignored. The builder must parse type files itself, map them to `DynamicType(name, description,
ownProperties, parents, creationPermitted)` (`core/DynamicType.kt:28-34`), and inject them via
`platform.plus(DataDictionary.fromDomainTypes("project", types))`. That is exactly what
`poc-1-no-code-runtime`'s `AgentSpecTypeMapper` + `SpecRunner:82-83` already do — the previous
edition's sample was wrong and the working code in this repo was right.

#### Repository mechanics that bite

`YmlStepSpecRepository(dir, additionalSubtypes)` (`yml/YmlStepSpecRepository.kt:35-82`):

* loads **`.yml` only** — `file.extension == "yml"` (:71). A `.yaml` file is invisible, silently.
* one file per entity, written as `${entity.name}.yml` (:59) — two steps sharing a name
  overwrite each other with no error. Names are the primary key; enforce uniqueness upstream.
* `FAIL_ON_UNKNOWN_PROPERTIES` is **disabled** (:47). A typo'd field (`toolgroup`, `canRun`,
  `outPutTypeName`) is silently dropped and the step loads with the default. This is the single
  most dangerous property of the module for a generator, and it is the strongest argument for the
  builder owning a validator (§3.6) rather than trusting round-tripping.

#### Execution mechanics (verified in `support/PromptedActionSpecAction.kt`)

Input binding: the blackboard variable is the decapitalised simple type name —
`PromptedActionSpec.variableNameFor` is `typeName.substringAfterLast('.').decapitalize()`
(`model/PromptedActionSpec.kt:74-77`).

**[corrected]** The previous edition said "property references also feed DynamicType property
discovery (`referencedInputProperties`)". The override returns `emptySet()` unconditionally
(`PromptedActionSpecAction.kt:213-215`). Nothing is discovered from the prompt; property
validation must be done by the builder.

**[corrected]** The previous edition said a `DynamicType` output is "parse[d] the JSON into a
Map-based `DomainInstance`". It is not a `DomainInstance`. The runner generates a JSON schema,
appends a fixed prompt contributor demanding bare JSON, calls `generateText`, and parses into a
plain `Map` that is written straight to the blackboard
(`PromptedActionSpecAction.kt:148-184`). Three consequences:

* the value carries **no type identity**, so anything downstream that checks
  `DomainInstance.domainType` sees nothing;
* the schema is produced by a hand-rolled `JsonSchemaGenerator` (:222-246) that is **flat only** —
  it throws `IllegalArgumentException` on any `DomainTypePropertyDefinition`, so a nested
  intermediate type is a hard runtime failure, not a degradation; and it marks **every** property
  required, so an optional field is not expressible;
* it is `generateText` with a "Return only JSON. DO NOT INCLUDE \`\`\`" instruction, **not** native
  structured output. Only the `JvmType` branch gets `createObject(prompt, clazz)` (:144), i.e.
  real structured decoding. A builder should therefore prefer a JVM output type where one exists
  and warn whenever a `DynamicType` output is chosen.

`outputTypeName: void` is supported and puts the raw generated **String** on the blackboard under
the output variable name (:66-70).

Prompt rendering (`templateModel`, :187-210) is more generous than the spec implies and more
fragile than it looks:

* the **entire blackboard** is seeded as the template model
  (`blackboard.expressionEvaluationModel()`), so a prompt can reference a variable the action does
  not declare — and the builder's validator will not catch the undeclared reference, but a
  different pack that renames a type will break it at runtime;
* declared inputs are then overlaid and **throw** if missing (:194-196);
* a `DomainInstance` input is flattened to its `.properties` map (:204) so
  `{{hubSpotContactCreated.email}}` resolves — pinned by
  `PromptedActionSpecActionDomainInstanceTemplateTest`, which records that without this Jinja
  "silently substitutes blank";
* `userInput` is special-cased to `blackboard.last<UserInput>()` (:207-209).

#### Test coverage — what is and is not proven

Every test in the module is a `mockk` unit test: parsing (`StepSpecParsingTest`, five YAML
fixtures), emission and binding names (`StepSpecEmitTest`), scope building
(`StepSpecAgentScopeBuilderTest`, mocked platform and dictionary), template model
(`PromptedActionSpecActionTest`, `…DomainInstanceTemplateTest`), and the compiler
(`NaturalLanguageCompilerTest`, mocked `Ai`/`PromptRunner`). There is **no live-LLM test and no
end-to-end GOAP run** in the module, and `embabel-experimental-integration-tests` contains no
reference to `com.embabel.agent.spec` at all.

So the module proves the format parses and the objects are shaped correctly. It does **not** prove
that a YAML-defined agent plans and executes. That proof lives in this project — POC 1
(no-compilation ingestion) and POC 3 (builder emission) in `docs/04`. A builder's own CI should
keep an end-to-end run, because upstream does not have one.

### 2.3 The condition grammar, and the silent-failure ladder

This is the highest-value finding of the re-research and it is invisible from either README.

`pre` and `post` are `List<String>`. How those strings are interpreted is decided at run time by
`BlackboardWorldStateDeterminer.determineCondition` (`core/support/BlackboardWorldStateDeterminer.kt:65-176`),
which tries five branches **in order** and falls through silently:

| # | Shape | Branch | If wrong |
|---|---|---|---|
| 1 | starts `spel:` | SpEL evaluation (:68-71) | — |
| 2 | contains `:` | parsed as a **binding** check `variable:type` (:74-107) | a stray colon silently turns a rule into a presence test |
| 3 | `hasRun` prefix | blackboard run flag (:110-124) | — |
| 4 | matches a known agent `Condition` name (FQN or short) | evaluate it (:127-148) | unreachable — YAML emits no conditions (§2.2) |
| 5 | **anything else** | look up an explicitly-set blackboard flag; **if unset ⇒ `false`** (:154-165) | the action is never plannable |

The SpEL branch requires the literal prefix `spel:` — `SpelLogicalExpressionParser` returns `null`
for anything else (`spi/expression/spel/SpelLogicalExpressionParser.kt:28-36`,
`PREFIX = "spel:"` at :37).

Consequence, and it is a live defect upstream: the module's own fixture
`src/test/resources/yml/action-with-conditions.yml` writes

    pre:
      - "#order.total > 0"
      - "#order.items.size() > 0"
    post:
      - "#validatedOrder.status == 'validated'"

None of those start with `spel:`. None contains `:`. None is a known condition. Branch 5 applies,
the blackboard flag is unset, and **every one of those conditions evaluates to `false`** — the
action would never be plannable. `StepSpecParsingTest` only asserts the strings *parse*, so the
module's green test suite hides it.

Meanwhile the NL compiler's prompts teach the *correct* form — `"spel:email.sender.contains('Richard
Beck')"`, `"spel:order.total > 100"` (`prompts/compiler/compile_action.jinja`,
`compile_agent.jinja`). So the fixtures and the prompts disagree, and only the prompts are right.

The builder rule that follows is cheap and pays for itself:

> **Every `pre`/`post` string must begin with a recognised prefix (`spel:`, the `hasRun` prefix, or
> a declared condition name) or be rejected at authoring time.** A condition that reaches branch 5
> is a dead action, and a condition containing a stray `:` is a different rule than the author wrote.

Two more silent-failure traps in the same family, both from the source above:

* `satisfiesType` is skipped entirely when the bound value is a `Map` — branch 2 returns `true` on
  name presence alone (:81-85, with an upstream `TODO may want to add type checking here`). Since
  `DynamicType` outputs are stored as raw `Map`s (§2.2), **every binding check against a
  dynamic-typed value passes on presence**. Type-mismatched chains will plan, run, and produce
  nonsense.
* `findTypeByNameOrSimpleName` (`core/AgentScope.kt:179-182`) matches by FQN **or simple name**.
  Two project types called `Order` in different packages resolve to whichever is found first.

### 2.4 The NL compiler — what it can and cannot emit

`DefaultNaturalLanguageCompiler(ai, llmOptions = LlmOptions(temperature = 0.1))`
(`compile/DefaultNaturalLanguageCompiler.kt:161-164`) implements `NaturalLanguageCompiler`
(`compile/NaturalLanguageCompiler.kt:134-218`): `compileAction`, `compileGoal`, `compileAgent`,
plus default `compile` (delegates to `compileAgent`) and `compileAll`.

**[corrected]** The previous edition said `compileAgent` "runs Embabel's three compiler prompts".
It runs **one**. `compileAgent` makes a single `createObject(CompiledAgent::class.java, ...)` call
against `compiler/compile_agent` (:276-288); `compile_action.jinja` and `compile_goal.jinja` serve
the *per-step* `compileAction`/`compileGoal` methods only. This matters for cost, latency, and for
where a builder intervenes: one call, one retry, one diff.

What `compileAgent` does around that call, all of it useful and none of it upstream-documented:

* `resolveShortTypeNames` (:354-375) maps a simple name to the FQN of an existing type by
  simple-name index — so the LLM may say `TelegramMessage` and get `com.example.TelegramMessage`.
* `validateTypeClashes` (:337-351) rejects an intermediate type that collides with an existing
  domain type, returning `errors` and **empty** actions.
* `inferMissingIntermediateTypes` (:377-392) auto-stubs any referenced-but-undefined type with
  **no properties** and the description `"Auto-inferred type"`, logging a warning. This is the
  failure mode a review UI must surface loudly: an invented type with zero properties compiles
  "successfully" and every `{{type.field}}` in the prompt then resolves to nothing.
* the goal is **derived**, not compiled: `GoalSpec(name = "$name-goal", description =
  compiled.goalDescription, outputTypeName = lastAction.outputTypeName)` (:304-308).

The hard limits — what the compiler is structurally incapable of emitting — are set by the shape of
`CompiledChainAction` (:104-123), which lacks `cost`, `value`, `canRerun`, `tools`, `references`
and `export`, and by `compileAgent` never setting `llm` on the actions it returns (:410-422).
`compileAction` explicitly sets `llm = LlmOptions()` with the comment "Use default LLM options at
runtime" (:200). So:

> An NL-compiled agent is a **subset** of the format. No per-action model, no cost/value steering,
> no per-action tools or references, and — because the derived goal never sets `export` — **a
> compiled agent is not callable as a tool until a human sets `export: true` on its goal.**

That last point is a product decision disguised as a default. A builder that offers "describe it in
a sentence" must set `export: true` itself or the artifact will deploy and never be reachable.

What the prompts do well, and are worth imitating in any custom generator (`compile_action.jinja`,
`compile_agent.jinja`):

* **Prefer an existing domain type as output over `void`** — outputting a type an existing platform
  action consumes chains the new action into it automatically ("outputting a message type to
  trigger delivery"). Stated in both prompts.
* **Extract "when X / if Y" into SpEL `pre`, never into a Jinja `if`.** `compile_action.jinja`
  carries an explicit WRONG/CORRECT pair and the reason: a precondition prevents the LLM call, a
  Jinja branch pays for it. This is a cost invariant, not a style rule.
* `compile_action.jinja` steers notification-shaped actions to
  `com.embabel.agent.spec.model.BackgroundMessage` — a class that exists precisely so an action can
  reach the user (`model/BackgroundMessage.kt:31-51`, `@JsonClassDescription("…Use to notify user
  if that appears to be the goal of the action")`, requiring non-empty content or text parts).

### 2.5 The realm spec — what "declarative" is allowed to mean

`embabel-worlds/realm-spec` HEAD (2026-11-13) is the vendor's production format: the default
install for Embabel.Me ships agents as realm repos. Its `../../README.md` (5,041 lines) is normative;
`GLOSSARY.md` fixes the vocabulary; `TYPESCRIPT_REALMS.md`, `HOSTED_EXECUTION.md`,
`VIRTUAL_CYPHER.md`, `DECLARING_TYPES.md`, `LABELS_AND_COMPOSITION.md`, `REALM_COMPOSITION.md`,
`BINDING_REALMS.md`, `PUBLISHERS.md`, `OPEN_STANDARDS.md` and `EXTERNAL_DOCUMENTS.md` are
companion specs.

**The headline, and it changes the design.** `TYPESCRIPT_REALMS.md:3-4`:

> `realm.ts` is the preferred realm definition format. YAML realms remain supported as legacy,
> without synthesis from TypeScript.

and `:34`: **"YAML is legacy and receives no new features."** The precedence table at `:23-31` is
unambiguous — `realm.ts` alone is evaluated; `realm.ts` **plus** YAML uses only the TypeScript and
reports the ignored paths without merging; an invalid `realm.ts` with valid YAML is **refused, with
no fallback to YAML**; only the absence of `realm.ts` reads the legacy folders. `GLOSSARY.md`
confirms: "Realm source: `realm.ts` and its captured definition imports… YAML-only realms are
legacy."

A builder that emits realm YAML is emitting into a frozen format. A builder aimed at realms must
emit `realm.ts` — `export default defineRealm({...})` from `@embabel/realm-types`
(`TYPESCRIPT_REALMS.md:173-189`) — which is *still* no-code in the sense that matters: no JVM
bytecode, no host config, no build artifact committed. Definition evaluation must be deterministic
and side-effect-free (no files, env, clock, randomness, network, processes — `:80-90`) and must
reduce to finite JSON-like data.

A realm's shape (`README.md:60-130`, `:132-190`): a repo named `realm-*` with `realm.yml`
(`:211`; `name`, `description`, `author`, `version`, `url`, `category`, `tags`, `maturity`, `icon`,
`host`), plus any of `actions/`, `goals/`, `types/`, `producers/`, `views/`, `lenses/`,
`sources.yml`, `reference/`, `apis/`, `keys.yml`, `credentials.yml`, `src/`+`tests/`, `mcp/`,
`commands/`, `webhooks/`, `events/`, `channels/`, `agents/`, `decorations/`, `apps/`,
`artifacts.yml`, `prompts/`, `skills/`, `notebook/`, `personalities/`, `focuses/`, `hints/`,
`seed/`. Three shapes: **declarative-only** (default, "no build step, no runtime code path"),
**handlers-driven** (TypeScript under `src/`), **captured** (`realm.ts`, owner-admitted,
per-capability approval).

Capability, not code, is the unit:

* `actions/` (`:381-536`) has three forms: `stepType: action` (a typed LLM call), the
  LLM-judged `AttentionVerdict` producer, and a **deterministic no-LLM rule** declared by host
  extension FQN with `whenExpr` — the last being the thing `embabel-agent-spec` cannot express at
  all. Policies and LLM judgments compose on cost/value so "the planner picks this before any LLM
  judgment on the same signal type" (`:479-481`).
* `types/` + `producers/` (`:558-1209`) cover graph-backed persistence, deterministic population
  from an external system with no code, on-demand virtual joins with declared predicate **pushdown**
  and pagination, `identityBridge:` and `resolve:` identity chains.
* `sources.yml` (`:1488-1856`) declares collection behaviour — `Coverage` completeness as a graph
  fact, `visibility: public`, `trigger: on-install|scheduled|manual`, `lane: documents` for
  chunked/embedded content, and a `sync.watch` cheap-read that promotes a run.
* `apis/` (`:1884-2140`) vendors an OpenAPI/GraphQL spec into a typed `gateway.<name>.*` namespace,
  with `tags`/`operation-ids` filtering, `capability-tags` (`web-search`, `web-fetch` — the only two
  defined) for host-side selection without an LLM, five auth modes, and a full OAuth2 block whose
  client credentials live in the host's org vault, **never** in the pack.
* `keys.yml` / `credentials.yml` (`:2141-2386`) let a realm *declare* a secret and never hold one;
  `validate` names one of the realm's own operations and the **host** makes the call, so "the realm
  never sees the value" (`:2200-2243`), with four outcomes of which only `refused` is a verdict.

**Agents themselves** (`README.md:3613-3760`) — the part a builder most wants, and the part with
the clearest limit. An `agents/<name>.yml` entry is a *proposal*: `name`, `job`, `routing`,
`persona`, `conversation{realms,builtins,skills,llm,sampling,authority}`, `prompt`, `asks`,
`routines[]` (`name`, `description`, `match.signalType`, `schedule`, `spec.kind|source|module`),
`duties[]` *(forward-looking)*, `sweeps[]` (`name`, `view`, `budgetPerTick`), `state: retired`.

Two findings a builder must not get wrong:

1. **No subagents.** There is no field, and no `builtins` category, by which one realm agent
   references or nests another. `builtins` categories are exactly `code`, `graph`, `views`, `data`,
   `memory`, `documents`, `attachments`, `images`, `artifacts`, `apps`, `host` (`:3793`) — no
   `agents`. `colleagues` appears only under "What an agent may ask for — *forward-looking*"
   (`:3731-3743`), where the spec says plainly: "Each is a request; none is honoured by being
   written down." Inter-agent work is a **world/host** concern reached through `routing`, or exposed
   as an action/view/skill. A "multi-agent decomposition" feature must not pretend otherwise.
2. **A realm never declares authority.** `sponsor`, `owners`, `operators`, agent stage and routine
   stage are the world's, "and a host ignores them in a realm's agent file. A realm that could name
   who answers for its own agent would be vouching for itself" (`:3656-3660`). A realm agent arrives
   off duty, unsponsored and unsigned; installing starts nothing (`:3705-3708`). Any builder that
   emits a realm agent and claims it is "live on install" is wrong about the contract.

**What a realm may never contain** (`README.md:4986-4996`, verbatim in substance): no code running
with host privileges — "No JVM bytecode, no native libraries, no classpath contributions"; no host
components or configuration changes; no user credentials ("Realms carry credential references,
never values"); no world/context/principal-owned state. If a capability needs real code it ships as
sandboxed handlers, `actions/`, `mcp/`, or a host-level extension out of band.

**Conventions and versioning** (`:4998-5011`): lowercase-hyphenated ids, UpperCamelCase type names,
"write descriptions assuming an LLM planner is the primary reader", and — the one that costs money
to learn late — **"changing a `name` is a breaking change for any installed world that wired
against it."** `version` is "informational"; the contract is at the directory-and-field level.

**Validation is a container, not a linter.** `.github/workflows/validate-realm.yml` is a reusable
workflow that runs "the validator that ships in the appliance server image, so the answer is the one
an appliance at that image would give", with the checkout mounted read-only, no network to declared
sources, and no secret needed (`:1-8` of the workflow). It takes `path`, `with` (sibling realms to
install beside it, `owner/repo`, `owner/repo@ref` or `./dir`), `image`
(default `ghcr.io/embabel/assistant:latest`) and `strict` (fail on warnings). A builder that emits
realms should run it in CI and treat `strict: true` as the default — because `sources.yml`
validation is explicitly strict about vocabulary ("`visibility: publc`… is an error at that field —
reported, never quietly read as the default, because the default is the dangerous reading",
`README.md:1846-1850`), while `embabel-agent-spec` is explicitly lax about unknown properties
(§2.2). Same generation problem, opposite failure policy.

**[corrected]** The first pass of this edition described the two fixtures as "a types+producers
realm and a types-only realm". The second is not types-only. `realm-widgets/types/widget.yml`
declares `Widget`, then declares a **second type it does not own**:

```yaml
# A join into the other realm, declared with joins only: declarations of one type name merge.
- name: Thing
  virtualJoins:
    - { anchorLabel: Widget, relationship: MADE_OF, keyField: thingKey,
        recordKeyField: key, producer: thingsByKey }
```

That fixture is the whole cross-realm composition rule in six lines, and it is why the `with` input
exists: **declarations of one type name merge across installed realms**, so a realm can enrich
another realm's type with a join without owning or importing it. The join distinguishes `keyField`
(on the anchor, `Widget`) from `recordKeyField` (on the fetched record, `Thing`). Consequently the
validator resolves producer references **across the installed set**, and the failure when they are
absent is `unknown producer 'thingsByKey'`.

Two more things the CI is evidence for, both directly reusable here:

* **The check is itself tested to be neither vacuous nor wrong.**
  `.github/workflows/test-validate-realm.yml` runs the passing case with `strict: true`
  (`passes-beside-its-dependency`, `:20-26`) and asserts the failing case exits 1 *and names the
  cause*: `grep -q "unknown producer 'thingsByKey'" report.txt` (`:44`). A green validator that
  passes everything is worse than none — this is the guard against that, and §3.6's validator needs
  the same meta-test.
* **Validation provably touches nothing external.** `realm-things/producers/things.yml` is
  `{ name: thingsByKey, kind: feed, url: https://example.invalid/things.xml }` — an RFC-reserved
  `.invalid` TLD that still validates, which is the concrete proof of the header's "Nothing is
  fetched from any source the realm declares, no secret is needed, and the checkout is mounted
  read-only" (`:4-6`). The CI-hardening rule at `:56-57`: "Inputs reach the shell as
  environment, never spliced into the script: a value from a caller's workflow is data." Any
  builder CI the project writes should copy that discipline verbatim.

**The documents lane, where "no code" is strongest.** `EXTERNAL_DOCUMENTS.md` splits a store by
*content type*, not by system: **live ops** (volatile operational data — "Ingesting these
manufactures staleness"), **KG entities** (Virtual Cypher producers, including file *metadata*), and
**ingested documents** (long-form content — vector and keyword search, chunking, agentic RAG). One
store usually spans all three: a Drive listing is KG, a Doc's *content* is ingested, a live sheet's
cells are live ops (`:20-22`). The builder-relevant sentence is "Declaring instead of coding"
(`:24-28`): when the content is text the realm can render from data the world already holds, or
pages the host can fetch unaided, **no handler is needed** — a `lane: documents` source in
`sources.yml` is enough, and the host ingests, versions, refreshes and prunes it. `ctx.ingest.*` is
only for sources needing the realm's own code: OAuth, change feeds, multi-step exports.

The `uri` contract is the part a generator gets wrong, and the failure is silent-adjacent — it
duplicates rather than errors (`:80-86`): `uri` is the store's **immutable** id, because "an
unstable uri duplicates instead of refreshing"; one uri = one document, so a container (Notion
database, wiki space) needs one ingest per page; and `file://`, `upload://`, `http://`, `https://`
are **refused** — reserved to the assistant's own ingestion paths. Freshness is entirely realm-side:
compare the live version token against `ingest.status().sourceModifiedAt` and re-ingest only on
drift; the protocol compares tokens and never interprets them. What the spec forbids emitting is as
important as what it allows — no volatile operational data, no structured records as prose, and
never "enumerate-and-ingest a store wholesale".

**The three-way boundary — the most actionable thing the realm spec offers a builder.**

| The builder may emit | Needs hand-written code | Host-only, and cannot be emitted |
|---|---|---|
| `realm.ts` + definition fragments; the `types` record; spine/`hub:` wiring; `producers` with `keyArgument`, `joins[]`, `pushdown[]`; `virtualJoins` incl. onto another realm's type; `handlers` *metadata* (namespace, verb, input/output JSON Schema, cron `schedule`); `goals`; `dataPipes`/`triggers`; `apps` manifest; `apis` + a **vendored** OpenAPI doc; `credentials` *declarations*; `dependencies` with `sha256`; `lane: documents` | `wasm/handlers.ts` bodies — every `ctx.gateway.*` call, cursor logic, `{rows,next}` paging; a real OpenAPI document (the upstream service authors it); the module build; SQL init/migrations; the handler that *answers* an imported ontology | Owner identity/Wallet, World, installation **revision**, artifact/capture digest, source-grant revision — "Realm declarations and guest arguments cannot assign them"; every approval (adopt, read/write grants, `cypher_query`, view alias, watch, app, model grant); the operator module allowlist; which labels accept write proposals; the model behind each role and its credential; the secret itself |

Two rules from that table are worth isolating because they bound what any builder can promise. A
`sha256` in `dependencies` "is a claim, not a build" — a generator can name a dependency, never
produce it. And a `credentials` entry may carry kind, provider, scheme and scopes but never a value;
`value`/`env`/`tokenEnv`/`walletItem` are refused outright, and the spec's own caveat is that
"Revocation cannot undo a request already accepted by the provider."

**Read the companion specs as proposal as often as contract.** This is the single biggest risk in
aiming a builder at the realm format, and the spec is honest about it in a way a reader skimming
file names will not be. `BINDING_REALMS.md:3-4` opens "**Status: proposal.** Nothing here is
implemented in the reference host yet." `TYPESCRIPT_REALMS.md:1505-1509` closes by warning that the
composition examples and dependency declarations are "authoring examples, not evidence that every
host implements these rules", that the field-by-field conformance fixture suite "remains future
work", and that `from.realm` is **reserved and explicitly refused today** — so cross-realm
*imports* are not a thing, only cross-realm *merging by shared type name*. `HOSTED_EXECUTION.md`'s
egress/CIDR block, `gateway.channel.read`, the `bundle.json` descriptor, and the
`@embabel/realm-types` SDK behaviours (Standard Schema adapters, inferred handler types, `.batch()`)
are all non-normative. The sentence a builder document should end on is the spec's own instruction
to hosts (`TYPESCRIPT_REALMS.md:1508-1509`):

> A host must publish its supported profile and report unsupported behavior rather than claiming it
> ran successfully.

Swap "host" for "builder" and it is also this project's obligation: a builder that emits realm YAML
must say which profile it targets, and must not imply that an emitted realm is running.

### 2.6 Supporting ecosystem

**Testing against a live LLM — `embabel-agent-eval`.** Drives multi-turn conversations with a
running agent over its REST API as a persona, scores per task against `acceptanceCriteria` with an
LLM judge, then checks side-effects. Composite score, verified against `README.md:66-84`:

    overall = (timing + taskScore * 2 + assertionScore * 4) / 7 - failurePenalty
    timing          = min(1.0, aspirationalAverage / actualAverage)
    failurePenalty  = min(failureCount * 0.3, 0.5)

Assertions are Kotlin expressions over `objectContext` or Cypher against Neo4j, each weighted;
each evaluation runs **twice**, once withholding ground-truth `facts` and once supplying them.
Job YAML is `evaluator{name,voice,signoff,temperature}`, `target{user,chatbot}`,
`aspirationalAverage`, `greetings[]`, `tasks[]{type,task,acceptanceCriteria}`, `facts[]`,
`assertions[]{expression|cypher,expected,weight}`, plus optional `seeds[]` (text or conversation).
Run: `mvn spring-boot:run -Dfile=… -Dmodel=… -Dverbose=true`.

Two honest caveats the previous edition understated: the target is a **chatbot** (`target.chatbot`)
and assertions are **Neo4j Cypher** — so a goal-shaped, graph-less agent needs `AgentInvocation`
wired into the runner and has no Cypher to assert on. It is not a batch metric framework; the README
says so itself and points at RAGAS for that.

**vaadin-components** — read-only display widgets for a live platform (AgentsSection, ActionsSection,
GoalsSection, McpSection, SkillsSection, ApisSection, DomainTypesSection, AwaitableRenderer/
FormRenderer for HITL). Right for a "preview exactly what the runtime sees" pane; not an editor.

**agent-gen** — the vendor's earlier meta-agent, which generated *annotated Kotlin*. Their stated
insight, "Full automation from prompt → running agent is unrealistic", is the reason the spec module
exists instead. **project-creator** is CLI scaffolding — the pre-existing "builder" emits code, not
data. **embabel-agent-sandbox** offers Docker/process isolation for generated snippets. A Quarkus
extension exists, so the runtime is not Spring-only. **hub.embabel.com** is an Embabel-powered doc
Q&A agent that is itself an MCP server — usable as an in-builder assistant.

**Runtime model endpoint.** Any OpenAI-compatible endpoint works. Config under
`embabel.agent.platform.models.openai.custom.{api-key,base-url,models}` plus
`OPENAI_CUSTOM_API_KEY` / `_BASE_URL` / `_MODELS` and `EMBABEL_MODELS_DEFAULT_LLM`. On this machine
the working endpoint and model are recorded in the project wiki (`concepts/model-endpoint.md`), and
the POC suite runs green against it.

## SECTION 3 — SYSTEM DESIGN

### 3.1 The design decision this section rests on

Emit **one project model, two targets.** The authoring surface, the validator, the NL compiler
wrapper and the eval loop are shared. Only the emitter forks:

  * **Target J — JVM step pack.** `embabel-agent-spec` YAML + project-owned `types/`, ingested by
    `YmlStepSpecRepository` + `StepSpecAgentScopeBuilder` into a fixed launcher. Runs today. This
    is what `smith-common` already owns.
  * **Target R — realm pack.** `defineRealm({...})` in `realm.ts` (YAML only as an explicit
    legacy toggle), installed into an Embabel host. Cannot run on this project's JVM runtime; it is
    the export path for "put this agent in a world".

Both are data. Neither regenerates compiled code. The builder's own code is the only compiled code
in the system.

### 3.2 High-level architecture

```text
  +--------------------------------------------------------------------+
  |                    AGENT BUILDER  (the product)                    |
  |                                                                    |
  |  +----------------+  +----------------+  +----------------------+  |
  |  | EDITOR PANE    |  | ADVISORY PANE  |  | TEST & IMPROVE PANE  |  |
  |  | agents/actions |  | planner        |  | smoke run            |  |
  |  | prompts/types  |  | advisor (LLM)  |  | eval jobs (YAML)     |  |
  |  | tools/MCP/refs |  | "suggest"      |  | LLM-as-judge scores  |  |
  |  | guardrails     |  | shape heurists |  | prompt rewrite loop  |  |
  |  | goals (export) |  |                |  | diff view, re-run    |  |
  |  +-------+--------+  +-------+--------+  +----------+-----------+  |
  |          |                    |                       |            |
  |          v                    v                       v            |
  |  +----------------------------------------------------------------+|
  |  |      PROJECT MODEL (files, git-friendly) + VALIDATOR           ||
  |  |  manifest.yml  agents/*/steps/*.yml  types/*.yml               ||
  |  |  mcp.yml  guardrails.yml  evals/*.yml                          ||
  |  |  ^ the only place silent-failure traps can be caught (§3.6)   ||
  |  +---------------------------+------------------------------------+|
  |                             |  emit / sync  (no compilation)       |
  +-----------------------------+--------------------------------------+
                 |                                        |
        Target J |                                Target R |
                 v                                        v
  +------------------------------+      +--------------------------------+
  | EMBABEL JVM RUNTIME          |      | EMBABEL HOST / APPLIANCE       |
  |  fixed launcher              |      |  realm.ts via defineRealm      |
  |   +-> YmlStepSpecRepository  |      |   actions/goals/types/         |
  |   +-> project types --> Data-|      |   producers/views/apis/        |
  |   |     Dictionary           |      |   sources/agents/skills/       |
  |   +-> scope.createAgent()    |      |  host validates + admits;      |
  |   +-> platform.deploy(agent) |      |  world sets stage & authority  |
  |   +-> AgentPlatform (GOAP…)  |      +--------------------------------+
  |   +-> MCP client + server    |
  |   +-> global guardrails      |
  +------------------------------+
```

### 3.3 Data flow: describe → compile → edit → validate → test → deploy

```text
  user text ──> NaturalLanguageCompiler (ONE LLM call) ──> AgentCompilationResult
                      | actions[], goal(derived, export=false),
                      | intermediateTypes[], errors[]
                      v
              DRAFT REVIEW (UI cards)   ← user edits prompts, types, tool
                      |                   groups, pre/post, planner, export
                      v
              VALIDATE  (builder's own; §3.6)
                      |  unknown keys, condition prefixes, prompt bindings,
                      |  dynamic-type shape, name collisions, .yml-only
                      v
              DEPLOY to throwaway AgentPlatform → plan is reachable?
                      v
              TEST & IMPROVE
                1. smoke: runAgentFrom with sample inputs
                2. eval : embabel-agent-eval job
                3. scores + failures
                4. LLM rewrites prompt from failures
                5. diff → accept/retry, keep best
                      v
              EXPORT  → Target J zip/git  |  Target R realm repo (realm.ts)
```

### 3.4 Project layout (Target J output)

```text
  <project>/
    manifest.yml          # name, version; per-agent: planner (GOAP|UTILITY|
                          #   HYBRID|SUPERVISOR), execution mode, model
    agents/<name>/
      steps/*.yml         # PromptedActionSpec, one per action
      goal.yml            # GoalSpec — builder sets export: true
    types/*.yml           # DynamicTypeDef — builder-owned format (§2.2)
    tools/
      mcp.yml             # MCP client connections → application.yml
      remote/*.yml        # optional remote action servers
    guardrails.yml        # class names → embabel.agent.guardrails.*
    evals/*.yml           # embabel-agent-eval job files
    launcher/             # FIXED template; never regenerated per agent
```

`types/` is deliberately the builder's own schema, not a `StepSpec` — the upstream module cannot
read it (§2.2).

### 3.5 UI panes

1. **Agents & subagents** — per agent: name, description, planner (four values + Suggest),
   execution mode, exported goals. For Target R, the agent card maps to the `agents/` field set and
   must expose `conversation.builtins` as a closed choice over the eleven categories, defaulting to
   the narrowest useful set — the spec's own guidance is that "omitted means all, which is almost
   never what an agent should have".
2. **Action editor** (core screen) — name, description, Jinja2 prompt with `{{var}}` autocomplete
   from declared inputs, input types, output type, pre/post, cost/value, canRerun, toolGroups,
   **tools**, **references**, per-action `llm`, `nullable`, `export`. Live emit-and-validate before
   save.
3. **NL draft assistant** — sentence → `compileAgent` → editable cards. Must set `export: true` on
   the derived goal, and must flag auto-inferred property-less types loudly (§2.4).
4. **Planner advisor** — LLM over the action graph plus the deterministic heuristics: one
   well-typed goal + typed chain → GOAP; `needsGoals=false` and no fixed end → UTILITY;
   opportunistic research then a terminal goal → HYBRID (with NIRVANA); ambiguous multi-step where
   the LLM should choose → SUPERVISOR; explicit stages → `@State`, which is **not** a planner value.
5. **Tools & MCP** — MCP connections; tool groups per action; named tools and LLM references;
   remote action servers. Note the `Export.remote` gap (§2.2): MCP publication of an exported goal
   needs launcher code, not YAML.
6. **Guardrails** — pick bundled guardrail classes → `embabel.agent.guardrails.*`; edit SpEL pre/post
   (with the prefix rule enforced); Budget / EarlyTerminationPolicy.
7. **Test & improve** — smoke run showing blackboard history and the plan; generated eval job;
   composite score, per-task judge scores, assertion failures; improve = failures + current prompt
   → LLM → diff → re-run.
8. **Preview** — vaadin-components bound to the live test platform.
9. **Export** — Target J zip/git; Target R realm repo with a validation workflow wired in.

### 3.6 The validator is the product's load-bearing component

Everything in §2.2–§2.3 has one common shape: **the upstream format fails quietly.** Jackson drops
unknown keys; a `.yaml` file is invisible; a duplicate step name overwrites; an unprefixed condition
evaluates to `false`; a binding check on a `Map` passes on presence; an auto-inferred type has no
properties; a `DynamicType` output loses its type identity. None of these produce an error at load.
A generator that does not validate will ship agents that deploy, plan nothing, and look empty rather
than broken.

Minimum rule set, in order, with the upstream fact that motivates each:

| # | Rule | Why |
|---|---|---|
| 1 | Reject unknown keys against the declared field list | Jackson has `FAIL_ON_UNKNOWN_PROPERTIES` disabled |
| 2 | Files must be `.yml`, step names unique in the pack | `.yaml` invisible; `save()` is `${name}.yml` |
| 3 | Every `pre`/`post` starts `spel:` / `hasRun` / a declared condition | branch 5 ⇒ `false` ⇒ dead action |
| 4 | No `:` in a condition outside the `spel:` prefix | branch 2 ⇒ silent binding check |
| 5 | Every `{{var}}` / `{{var.prop}}` bound by a declared input; `prop` exists on the declared type | `referencedInputProperties` returns `emptySet()`; whole blackboard is visible so it works by accident |
| 6 | Every input/output type is framework-provided or declared in `types/` | goal emit silently invents a property-less `DynamicType` |
| 7 | A `DynamicType` output must be flat, all-optional-in-practice, and never nested | `JsonSchemaGenerator` throws on `DomainTypePropertyDefinition`, marks all required |
| 8 | Prefer a JVM output type where one exists | only the `JvmType` branch gets native structured output |
| 9 | Type names unique by **simple** name across the pack | `findTypeByNameOrSimpleName` matches simple names |
| 10 | `export: true` present on exactly one goal | a compiled agent is unreachable without it |
| 11 | Warn when a name is reused after a version bump | realm rule: renaming is a breaking change |
| 12 | Realm target only: every `producer:` reference resolves across the installed set; every document `uri` is a store-immutable id on a non-reserved scheme | upstream fails as `unknown producer 'x'` at validate, and a drifting `uri` **duplicates instead of refreshing** — no error at all |

Rules 1–5, 9 and 12 are cheap string checks that need no platform. Rules 6–8 need the type model.
Then — and only then — deploy to a throwaway `AgentPlatform` and ask whether a plan to the goal
exists, because that is the question upstream has never automated.

One structural obligation this list creates, taken from the realm spec rather than invented: a
validator must be tested **in both directions**, or it is worthless. `.github/workflows/
test-validate-realm.yml` is a workflow whose only job is to prove the validator is neither vacuous
nor wrong — a passing fixture under `strict: true`, and a broken one asserted to exit 1 *and to name
its cause* (`grep -q "unknown producer 'thingsByKey'" report.txt`). A validator with no failing
fixture is an untested validator, and §3.6's argument is that the validator is the product. So
ship a fixture pair per rule above, each asserting the rule fires and names the offending field.

## SECTION 4 — LIMITATIONS & RISKS (honest)

Works well today (Target J, buildable now): LLM-prompt-only agents with typed chains, GOAP planning,
tool groups, MCP, exported goals, eval, prompt-improvement loop, planner suggestion. Covers the
large majority of agent use cases: research, summarisation, triage, drafting, classification,
routing, RAG flows.

Gaps that need Java, or a host, or a 2.0 feature:

* Content guardrails are Java interfaces, not declarable. Only SpEL conditions, budgets and
  (programmatically) `PromptCondition` are. Mitigation: ship a guardrail class library in the
  launcher; the UI picks from it.
* No declarative HITL in YAML specs. `WaitFor.formSubmission` is annotation-side.
* No declarative deterministic code actions on the JVM. `whenExpr` policy actions exist **in the
  realm spec**; the nearest JVM equivalent is a remote action server (experimental).
* `StuckHandler`, custom `Condition`s, `opaque`, per-action `@SecureAgentTool`: unreachable from
  YAML — `StepSpecAgentScopeBuilder` passes no conditions and `AgentPlatform.deploy(scope)` drops
  the rest (§2.2).
* `Export.remote` and `Export.name` are not settable from YAML, so "publish this goal as an MCP
  tool" needs launcher code.
* `DynamicType` agents lose the type safety that is Embabel's core pitch: outputs are raw `Map`s,
  binding checks pass on presence, nested types throw.
* Nested intermediate types are unrepresentable — `JsonSchemaGenerator` throws.
* No builder UI exists anywhere in the ecosystem; vaadin-components are display-only. The editor is
  net-new work and is the product.
* The eval module is chatbot-and-Neo4j-shaped; adapting it to goal-shaped agents is a bounded but
  real task.
* The realm spec marks much of its agent surface *forward-looking* (`duties`, `colleagues`,
  `authority`, `qos`, `battery`, activation) and says a host that does not read a field ignores it.
  A builder must not present a forward-looking field as a working feature.
* Stronger version of the same risk, and the main argument for keeping the realm target behind a
  flag: **whole companion specs are unimplemented.** `BINDING_REALMS.md:3-4` is "Status: proposal.
  Nothing here is implemented in the reference host yet"; `from.realm` cross-realm imports are
  reserved and *refused today*; the field-by-field conformance fixture suite is "future work"
  (`TYPESCRIPT_REALMS.md:1505-1509`). A file existing in the spec repo is not evidence a host reads
  it. Mitigation is the spec's own rule for hosts, adopted for the builder: publish a supported
  profile, and report an unsupported field rather than claiming it ran.

Risks:

* **Version reality.** The ingestion engine is a snapshot tracking a 1.5.3 platform (§2.2). Pin,
  and isolate the spec layer behind an interface so a shift is one module.
* **Silent failure is the dominant failure mode.** Mitigated only by §3.6; a generator that trusts
  round-tripping will ship empty agents.
* **LLM-authored specs can be wrong** — bindings, schemas, invented types. The draft-review +
  validate-before-save loop is mandatory, not a nicety.
* **Prompt quality is the value axis**; the eval loop is where differentiation lives.
* **Format drift.** `embabel-agent-spec` is young; the realm spec explicitly freezes YAML. Emit
  `realm.ts` for Target R or accept a format with no future.

## SECTION 5 — BEST PRACTICES (from the ecosystem)

* Prefer an existing domain type as an action output over `void` — the platform chains your action
  into the existing consumer of that type automatically. Enforced in `compile_agent.jinja`.
* Extract "when X / if Y" into SpEL `pre`, never a Jinja `if`. Saves the LLM call entirely.
* Keep one goal per agent; the exported goal is the callable surface. Set `export: true` — the
  compiler will not.
* Use `cost` to steer GOAP and `value` to steer utility planning.
* `nullable: true` enables replanning instead of hard failure; `canRerun: true` only for loops.
* Mix models per action via `llm` — cheap local for extraction, strong for synthesis. The NL
  compiler cannot emit this; only the editor can.
* Ship agents as git repos (the realms model): reviewable, versioned, distributable.
* Determinism is the point: the planner is non-LLM, only action bodies are LLM calls. That is what
  makes eval scores mean anything.
* For realms: descriptions are routing signal for a planner, not prose; keep `prompts/` a one-line
  pointer and put workflow guidance in a `skills/` SKILL.md (paid only when activated); declare
  every pushdown rule; declare joins under the type they *produce*; never normalize a shared key on
  your own side; and treat an untested view as unshipped — a broken join is an empty result, not an
  error.

## SECTION 6 — KEY SOURCES (all verified 2026-11-13)

* `github.com/embabel/embabel-agent-experimental` @ `318ef9f` (2026-11-12) — `embabel-agent-spec`
  read in full: `../../pom.xml`, `README.md`, all 15 main Kotlin files, all 8 test files, all 5 YAML
  fixtures, all 3 Jinja prompts. Also `embabel-agent-eval/README.md`,
  `embabel-agent-remote/README.md`.
* `github.com/embabel-worlds/realm-spec` @ `9b889e9` (2026-11-13) — `../../README.md` 1–2390 and
  3613–3760 read line by line; `GLOSSARY.md` in full; `TYPESCRIPT_REALMS.md` §Source selection,
  §Realm metadata and the closing host-profile instruction (`:1505-1509`); `EXTERNAL_DOCUMENTS.md`
  §lanes, §"Declaring instead of coding" and §uri rules; `.github/workflows/validate-realm.yml` and
  `test-validate-realm.yml`; both `.github/fixtures/validate-realm/` fixtures; root `realm.yml`.
  Still to read before asserting detail from it: `BINDING_REALMS.md` (**proposal — nothing is
  implemented**, `:3-4`), `HOSTED_EXECUTION.md`, `VIRTUAL_CYPHER.md`, `DECLARING_TYPES.md`,
  `LABELS_AND_COMPOSITION.md`, `REALM_COMPOSITION.md`, `PUBLISHERS.md`, `OPEN_STANDARDS.md`. §2.5
  flags which of these are contract and which are proposal; treat anything in the second class as
  unimplemented until checked against a host profile.
* `~/.m2/.../embabel-agent-api-1.5.4-sources.jar` — read, not `javap`'d:
  `PlannerType.kt`, `ProcessOptions.kt`, `AgentScope.kt`, `AgentPlatform.kt`, `Goal.kt`,
  `DynamicType.kt`, `DataDictionary.kt`, `BlackboardWorldStateDeterminer.kt`,
  `SpelLogicalExpressionParser.kt`, `Condition.kt`, `PromptCondition.kt`,
  `GuardRailConfiguration.kt`, `GlobalGuardRailsRegistry.kt`.
* Local Maven repository state for `embabel-agent-spec` — 1.5.3 jar present, 1.5.4 directory
  contains only `.lastUpdated` markers, 1.5.4-SNAPSHOT builds current to 2026-11-13.
* `github.com/embabel/vaadin-components`, `agent-gen`, `project-creator`, `me.embabel.com`.
* This project: `../agent-spec-format.md` (the `smith-common` format),
  `poc/poc-1-no-code-runtime` (working DynamicType injection), `docs/04-poc-run-results.md`
  (the end-to-end proof upstream does not have).

## SECTION 7 — IMPLEMENTATION RECOMMENDATIONS & CODE SAMPLES

Java 21, Embabel 1.5.x. Samples are corrected against the source read in Section 2.

### 7.1 Minimal launcher — the only compiled Java per runtime

```java
@SpringBootApplication
public class Launcher implements CommandLineRunner {

    private final AgentPlatform platform;
    private final String packDir;
    private final String input;
    private final PlannerType planner;

    public Launcher(AgentPlatform platform,
                    @Value("${builder.pack-dir:}") String packDir,
                    @Value("${builder.input:}") String input,
                    @Value("${builder.planner:GOAP}") String planner) {
        this.platform = platform;
        this.packDir = packDir;
        this.input = input;
        this.planner = PlannerType.valueOf(planner.toUpperCase());
    }

    @Override
    public void run(String... args) {
        // 1. Types. The spec module has NO type ingestion: parse types/*.yml ourselves
        //    and put them in the DataDictionary, which is the agent's entire type universe.
        List<DynamicType> projectTypes = new DynamicTypeLoader().load(Path.of(packDir, "types"));
        DataDictionary types = platform.plus(
            DataDictionary.fromDomainTypes("builder-types", projectTypes));

        // 2. Ingest step YAML. Directory must contain .yml (not .yaml) files.
        StepSpecAgentScopeBuilder builder = new StepSpecAgentScopeBuilder(
            "builder-agent", platform,
            new YmlStepSpecRepository(Path.of(packDir, "steps").toString(), List.of()),
            types,
            platform.getToolGroupResolver().availableToolGroups(),
            List.of());

        // 3. createAgent + deploy(agent), NOT deploy(scope): the AgentPlatform default
        //    for an AgentScope drops stuckHandler and opaque and overwrites the provider.
        AgentScope scope = builder.createAgentScope();
        Agent agent = scope.createAgent("builder-agent", "embabel-smith", "YAML-defined agent");
        platform.deploy(agent);

        // 4. Run with the selected planner.
        AgentProcess process = platform.runAgentFrom(
            agent,
            ProcessOptions.DEFAULT.withPlannerType(planner),
            Map.of("userInput", new UserInput(input)));

        // 5. Report. A DynamicType output lands on the blackboard as a raw Map, not a
        //    DomainInstance, so read it by binding name rather than by type.
        process.lastResult() instanceof BackgroundMessage m
            ? System.out.println(m.getContent())
            : System.out.println("No BackgroundMessage. Status: " + process.getStatus());
    }
}
```

### 7.2 application.yml

```yaml
embabel:
  agent:
    platform:
      models:
        openai:
          custom:
            api-key: ${OPENAI_CUSTOM_API_KEY:}
            base-url: ${OPENAI_CUSTOM_BASE_URL:}
            models: ${OPENAI_CUSTOM_MODELS:}
    logging:
      personality: starwars
spring:
  main:
    web-application-type: none
```

### 7.3 Sample pack (what the builder emits)

`steps/extractTopic.yml`
```yaml
stepType: action
name: extractTopic
description: "Extract the topic, audience and tone from the user's request"
inputTypeNames: [com.embabel.agent.domain.io.UserInput]
outputTypeName: TopicBrief
prompt: |
  From the user request below, extract topic, audience and tone.
  If audience or tone is unspecified, choose sensible defaults.
  User request: {{userInput.content}}
```

`steps/writeExplainer.yml` — note the JVM output type, so this gets native structured decoding
rather than "return only JSON".
```yaml
stepType: action
name: writeExplainer
description: "Write the explainer for the extracted brief"
inputTypeNames: [TopicBrief]
outputTypeName: com.embabel.agent.spec.model.BackgroundMessage
llm: { model: gpt-4.1-mini, temperature: 0.4 }
pre:
  - "spel:topicBrief.topic.length() > 0"      # prefix required — see §2.3
cost: 0.3
prompt: |
  Write a ~150-word explainer about {{topicBrief.topic}}
  Audience: {{topicBrief.audience}}  Tone: {{topicBrief.tone}}
```

`goal.yml` — the builder sets `export: true`; the NL compiler does not.
```yaml
stepType: goal
name: explainerComplete
description: "A complete explainer has been produced for the user"
outputTypeName: com.embabel.agent.spec.model.BackgroundMessage
export: true
```

`types/TopicBrief.yml` — builder-owned schema, loaded by `DynamicTypeLoader`, **not** by
`YmlStepSpecRepository`.
```yaml
name: TopicBrief
description: "The extracted topic, audience and tone"
ownProperties:
  - { name: topic,    type: string, description: "What to explain" }
  - { name: audience, type: string, description: "Who it is for" }
  - { name: tone,     type: string, description: "Voice and style" }
parents: []
creationPermitted: true
```

### 7.4 NL draft service

```java
@Service
public class AgentDraftService {

    private final DefaultNaturalLanguageCompiler compiler;
    private final DataDictionary types;
    private final List<ToolGroupDescription> toolGroups;

    public AgentDraftService(Ai ai, AgentPlatform platform, DynamicTypeLoader loader, Path packDir) {
        this.compiler = new DefaultNaturalLanguageCompiler(
            ai, LlmOptions.withModel("gpt-4.1-mini").withTemperature(0.1));
        this.types = platform.plus(DataDictionary.fromDomainTypes("draft", loader.load(packDir)));
        this.toolGroups = platform.getToolGroupResolver().availableToolGroups();
    }

    public AgentCompilationResult draft(String name, String description) {
        AgentCompilationResult r = compiler.compileAgent(
            name, description,
            new StepSpecContext(name, types, toolGroups, List.of(), List.of()));

        // The compiler derives the goal with export left at its default, which makes the
        // agent undeployable-as-a-tool. The builder owns that decision.
        return r;   // caller sets export=true on the goal and runs the §3.6 validator
    }
}
```

Surface `result.errors()` and, separately, any `intermediateTypes` whose description is
`"Auto-inferred type"` — those were invented with zero properties (§2.4) and every `{{type.field}}`
in a prompt that references them resolves to nothing.

### 7.5 Planner suggestion

```java
public PlannerType suggestPlanner(List<Action> actions, Set<Goal> goals) {
    if (goals.size() == 1 && actions.stream().allMatch(a -> a.getInputs().size() <= 2)) {
        return PlannerType.GOAP;
    }
    if (goals.isEmpty()) {
        return PlannerType.UTILITY;          // UTILITY is the only needsGoals=false member
    }
    if (actions.size() > 5) {
        return PlannerType.HYBRID;
    }
    String response = ai.withDefaultLlm().generateText("""
        Actions: %s
        Goals:   %s
        Recommend GOAP, UTILITY, HYBRID or SUPERVISOR with a one-line rationale.
        """.formatted(describe(actions), describe(goals)));
    return PlannerType.valueOf(response.trim().toUpperCase());   // no STATE — see §1
}
```

### 7.6 Eval job

```yaml
evaluator:
  name: "Tester"
  voice: "You are a careful QA engineer"
  temperature: 0.3
  signoff: "END"
target: { user: test-user, chatbot: builder-agent }
aspirationalAverage: 5000
tasks:
  - type: task
    task: "Ask for an explainer on how a coffee machine works, for a 12-year-old"
    acceptanceCriteria: "Clear, upbeat, ~150 words, one analogy"
facts: [ "The user prefers simple language and analogies" ]
assertions:
  - expression: "objectContext.resources.size >= 1"
    weight: 3
```

`target.chatbot` and Cypher assertions assume a conversational, graph-backed agent; a goal-shaped
agent needs `AgentInvocation` wired into the runner and should use `expression` assertions only.

### 7.7 Guardrails

```yaml
embabel:
  agent:
    guardrails:
      user-input: com.example.guardrails.LengthValidator,com.example.guardrails.TopicAllowlist
      assistant-message: com.example.guardrails.PiiRedaction
```

Per-action plan-level policy in the step YAML, always with the `spel:` prefix:
```yaml
pre:  [ "spel:userInput.content.length() < 5000" ]
post: [ "spel:backgroundMessage.content.contains('analogy')" ]
```

### 7.8 Dependencies

```xml
<properties>
  <embabel-agent.version>1.5.4</embabel-agent.version>
  <!-- spec/eval/remote are NOT released at 1.5.4: they exist only as 1.5.3 releases
       and 1.5.4-SNAPSHOT builds of embabel-agent-experimental. Pin deliberately. -->
  <embabel-agent-experimental.version>1.5.4-SNAPSHOT</embabel-agent-experimental.version>
  <java.version>21</java.version>
</properties>
<!-- com.embabel.agent: embabel-agent-starter, embabel-agent-starter-openai,
     embabel-agent-spec, embabel-agent-eval, (opt) embabel-agent-remote,
     (opt) com.embabel:vaadin-components -->
```

This is exactly the split the root `../../pom.xml` of this repository already encodes
(`embabel-agent.version` 1.5.4 vs `embabel-agent-experimental.version` 1.5.4-SNAPSHOT).

### 7.9 Module boundaries

```
embabel-builder/
├── builder-runtime/     # fixed Launcher + application.yml; never regenerated
├── builder-core/        # project model, YAML I/O, DynamicTypeLoader, VALIDATOR (§3.6)
├── builder-nl/          # NaturalLanguageCompiler wrapper + prompt tuning
├── builder-eval/        # embabel-agent-eval integration, job generation
├── builder-ui/          # panes 1-9; preview via vaadin-components
└── builder-export/      # Target J zip/git; Target R realm repo + validate workflow
```

`builder-core`'s validator is not a utility — it is the component that stands between a generator
and a fleet of agents that deploy, plan nothing, and look empty rather than broken.
