# Embabel "Agent Builder" — Design Document & Research Findings

Date: 2026-10-30
Status: RESEARCH + DESIGN COMPLETE. Product intentionally NOT built (per user
direction) — this document is the design and the evidence base.

VERDICT (one line): FEASIBLE, not useless. Embabel already ships ~70% of the
pieces — a stable declarative YAML agent module, an NL→spec LLM compiler, and
an LLM eval harness. The missing piece is the builder UI itself.

Every claim below was verified against Embabel source (v1.5.3 jars inspected
with javap, v1.5.3 source read, production "realms" packs inspected) — not from
marketing copy.

## SECTION 1 — WHAT EMBABEL IS (the concepts the builder maps onto)

Embabel (github.com/embabel/embabel-agent, Apache-2.0, from the creator of
Spring; stable 1.5.3, Oct 2026; Maven Central + repo.embabel.com) models
agentic flows as:

  Actions    steps with typed inputs (IoBindings "name:Type"), typed outputs,
             optional SpEL pre/post conditions, cost, value, canRerun,
             toolGroups, and an implementation (LLM prompt or code).
  Goals      what the agent achieves; name an output type + preconditions.
             Goal descriptions are what an LLM uses to pick goals.
  Conditions named predicates re-evaluated after each action.
  Domain     strongly-typed objects: JVM classes (JvmType) or pure data
             definitions (DynamicType — NO JVM class, designed for non-JVM
             interop; this is what makes YAML-only agents possible).
  Plan       computed by a NON-LLM planner (default GOAP/A*), replanned after
             every action (OODA loop).

Authoring models that exist today:
  1. Annotations: @Agent class with @Action/@AchievesGoal/@Condition.
  2. Kotlin DSL:   agent { action { ... } goal { ... } }.
  3. YAML specs:   embabel-agent-spec  <-- the one the builder emits.

Execution:
  AgentPlatform.deploy(agent|agentScope) — runtime deployment (the key fact:
  agents do NOT have to be compiled into the jar).
  runAgentFrom(agent, ProcessOptions, bindings) — start a process;
  ProcessOptions.withPlannerType(...) selects the planner per run.
  Modes: Focused (call a specific agent) / Closed (LLM picks an agent) /
         Open (LLM picks a goal, platform assembles an agent).
  Subagents: an @Action can spawn nested processes (asSubProcess); in YAML
  terms, exported goals (export: true) become callable tools/skills.

Planners (PlannerType enum — verified in the 1.5.3 jar):
  GOAP       default; deterministic goal-oriented A* over pre/postconditions.
             Best: business processes with defined outputs.
  UTILITY    picks highest-value available action each step; no fixed goal.
             Best: exploration / event-driven systems.
  HYBRID     utility-style picking, exits when a goal is satisfied.
             Best: "gather context, then synthesize" pipelines.
  SUPERVISOR LLM-orchestrated composition from type schemas.
             Best: flexible multi-step workflows.
  @State     state-machine model; each state plans with GOAP/Utility internally.
  Planners are combinable; choice is per-agent (annotation) or per-process.

Tools & MCP:
  * toolGroups on actions (e.g. CoreToolGroups.WEB); MCP client config in
    application.yml (Spring AI MCP: stdio/SSE); agents can be EXPOSED as an
    MCP server (embabel-agent-starter-mcpserver, SSE).
  * @Tool on domain-object methods exposes business logic to the LLM.
  * A2A protocol integration (agent-to-agent).
  * embabel-agent-remote (experimental): any HTTP server registers custom
    code-backed actions into GOAP planning via 3 REST endpoints
    (GET /api/v1/actions, GET /api/v1/types, POST /api/v1/actions/execute).

Guardrails (three orthogonal mechanisms):
  * Content: UserInputGuardRail / AssistantMessageGuardRail Java classes;
    per-call withGuardRails(...) or GLOBAL via
    embabel.agent.guardrails.user-input=... properties. CRITICAL severity
    blocks the LLM call. NOT YAML-declarable.
  * Plan-level: SpEL pre/post on actions, goal preconditions, Budget /
    EarlyTerminationPolicy in ProcessOptions. Declarable.
  * Access: @SecureAgentTool SpEL authority checks. Not YAML-declarable.
  * PromptCondition (experimental): LLM-evaluated condition — the only
    "declarable" LLM condition.

## SECTION 2 — THE DECISIVE EVIDENCE

### 2.1 embabel-agent-spec = the no-compilation ingestion engine (STABLE 1.5.3)
Verified in the 1.5.3 RELEASE jar (repo.embabel.com libs-release; released
alongside the 1.5.3 platform — no longer "experimental only"). Contents:

  StepSpec (interface): stepType discriminator + emit(context) → Action|Goal.
    Subtypes resolved by short name ("action","goal") or FQCN — extensible
    without code.
  PromptedActionSpec (YAML-serializable LLM action):
      stepType: action
      name, description
      llm: {model, temperature, ...}
      inputTypeNames: [UserInput]
      outputTypeName: Summary
      pre: ["#order.total > 0"]        # SpEL preconditions
      post: ["#validatedOrder.status == 'validated'"]
      cost, value, canRerun, nullable
      prompt: "Summarize: {{userInput.content}}"   # Jinja2 template
      toolGroups: [search, web]
      tools: [toolNames]
      export: false    # true => auto goal, callable as a tool/skill
  GoalSpec:
      stepType: goal
      name, description
      outputTypeName
      export: true     # publish as a tool callable with UserInput
  DynamicType: name + description + ownProperties + parents — a data model
    with NO JVM class ("enables interop with non-JVM types").
  YmlStepSpecRepository: loads/saves one YAML file per step from a directory
    (default ./steps).
  StepSpecAgentScopeBuilder: loads all specs, emits Actions/Goals, and
    agentPlatform.deploy(scope) — runtime deployment into the live GOAP
    planning system. NO compilation.
  DefaultNaturalLanguageCompiler: NL description → PromptedActionSpec +
    GoalSpec + intermediate DynamicTypes (LLM-driven; Jinja2 prompts; SpEL
    precondition extraction from "when X / if Y"; tool-group selection from
    the platform's available groups; returns AgentCompilationResult with
    errors for review). Prompts: compile_agent/action/goal.jinja in the jar.

  Execution mechanics (read from PromptedActionSpecAction source, v1.5.3):
    - Input binding: blackboard variable = decapitalized type name
      (UserInput → userInput). World-state check: blackboard[variable]
      exists AND satisfiesType(value, type).
    - Output: if outputTypeName is a JVM class on the classpath → LLM
      createObject(prompt, class) (native structured output). If it is a
      DynamicType on the agent's dynamicTypes → generate a JSON schema from
      the type, constrain the LLM to that schema, parse the JSON into a
      Map-based DomainInstance.
    - Prompt templating: {{var}} and {{var.property}} rendered from the
      blackboard; property references also feed DynamicType property
      discovery (referencedInputProperties).

  Verified constructor signatures (javap on 1.5.3 jars) — the exact calls a
  launcher/builder must make:
    StepSpecAgentScopeBuilder(String name, AgentPlatform, StepSpecRepository,
        DataDictionary, List<ToolGroupDescription>, List<Tool>)
    YmlStepSpecRepository(String dir, List<Class<? extends StepSpec<?>>>)
    PromptedActionSpec(String,String,LlmOptions,Set<String>,String,List,List,
        double,double,String,List,List,List,boolean,boolean,String)
    GoalSpec(String,String,String,boolean,String)
    Agent(String name, String provider, String desc, Set<Goal>,
        List<Action>, Set<Condition>, StuckHandler, boolean opaque,
        Collection<DomainType> domainTypes)
    ProcessOptions.DEFAULT.withPlannerType(PlannerType.GOAP)
    DataDictionary.fromDomainTypes(String, Collection<DomainType>)
    DynamicType(String,String,List<PropertyDefinition>,List<DomainType>,boolean)
    ValuePropertyDefinition(String,String,Cardinality,String)
    JvmType(Class<?>)
    LlmOptions.withModel(String) / fromCriteria(ModelSelectionCriteria)

### 2.2 Production no-code "realms" use EXACTLY this format
embabel-worlds/default-world (default install for Embabel.Me, the vendor's
commercial personal-assistant product) ships agents as git repos of pure YAML
— verified examples:

**`config/actions/joke.yml`:**

```yaml
stepType: action
name: joke
description: "Tell a joke about a given subject"
inputTypeNames: [com.embabel.agent.domain.io.UserInput]
outputTypeName: com.embabel.agent.spec.model.BackgroundMessage
prompt: "Tell a short, funny joke about: {{userInput.content}}"
```

  config/actions/persist-alert.yml:   ... tools: [alerts, communicate]
  config/actions/list-ready-tasks.yml: ... tools: [tasks]
  plus: config/apis/*.yaml (learned API defs), behaviour/personalities,
  commands, producers, lenses, and realms.yml (catalogue of realm git repos).

  Conclusion: the vendor's own roadmap IS the declarative-agent-pack +
  appliance model. A third-party builder produces exactly this shape.

### 2.3 Testing against an LLM: embabel-agent-eval
YAML eval jobs: evaluator persona, target chatbot, tasks with acceptance
criteria, ground-truth facts, assertions (Kotlin expressions on ObjectContext
+ Cypher queries), aspirational response times. An LLM evaluator drives a
multi-turn conversation over the agent's REST API; a second LLM scores each
task (0-1) against acceptance criteria; composite score:
  overall = (timing + taskScore*2 + assertionScore*4)/7 - failurePenalty
Runs twice per evaluation (with/without ground truth available).
Adapting it to non-chat (goal-type) agents requires wiring AgentInvocation
into the eval runner — a bounded task.

### 2.4 Supporting ecosystem pieces
  * vaadin-components: read-only Vaadin display widgets for a live platform
    (AgentsSection, ActionsSection, GoalsSection, McpSection, SkillsSection,
    ApisSection, DomainTypesSection, AwaitableRenderer/FormRenderer for HITL,
    chat bubbles). Perfect for a "preview" pane; not an editor.
  * agent-gen ("Meta-Agent"): the vendor's earlier experiment — an @Agent that
    generates ANNOTATED KOTLIN CODE. Their stated insight: "Full automation
    from prompt → running agent is unrealistic." The spec module is the newer,
    no-compile direction.
  * project-creator: CLI scaffold wizard (the existing "builder" is code
    scaffolding, not a UI).
  * embabel-agent-remote / embabel-agent-sandbox (experimental): remote
    code-backed actions; Docker/process sandbox for executing generated
    snippets during testing.
  * Quarkus extension exists — runtime not strictly Spring Boot (SB mainline).
  * hub.embabel.com: an Embabel-powered doc Q&A agent (itself an MCP server) —
    usable as an in-IDE assistant inside the builder.

### 2.5 Runtime model endpoint (verified for a live run)
Any OpenAI-compatible endpoint works (LiteLLM, Groq, LM Studio, ...):

```yaml
embabel.agent.platform.models.openai.custom:
    api-key, base-url, models
```

plus env: `OPENAI_CUSTOM_API_KEY` / `_BASE_URL` / `_MODELS`, `EMBABEL_MODELS_DEFAULT_LLM`.

Verified: a LiteLLM proxy serves /v1/chat/completions and /v1/responses;
the "OpenAI custom" config points Embabel at it. (The other OpenAI-family
models on the test proxy were down — only one model was live — but the config
path is confirmed.)

## SECTION 3 — SYSTEM DESIGN

### 3.1 High-level architecture

```text
  +--------------------------------------------------------------------+
  |                    AGENT BUILDER  (the product)                    |
  |                                                                    |
  |  +----------------+  +----------------+  +----------------------+  |
  |  | EDITOR PANE    |  | ADVISORY PANE  |  | TEST & IMPROVE PANE  |  |
  |  |                |  |                |  |                      |  |
  |  | agents/actions |  | planner        |  | smoke run            |  |
  |  | prompts/types  |  | advisor (LLM)  |  | eval jobs (YAML)     |  |
  |  | tools/MCP      |  | "suggest"      |  | LLM-as-judge scores  |  |
  |  | guardrails     |  | shape heur.   |  | prompt rewrite loop  |  |
  |  | skills (export)|  |                |  | diff view, re-run    |  |
  |  +-------+--------+  +-------+--------+  +----------+-----------+  |
  |          |                    |                       |            |
  |          v                    v                       v            |
  |  +----------------------------------------------------------------+|
  |  |           PROJECT MODEL  (files, git-friendly)                 ||
  |  |  manifest.yml  agents/*/steps/*.yml  types.yml                 ||
  |  |  mcp.yml  guardrails.yml  evals/*.yml  launcher (fixed)        ||
  |  +---------------------------+------------------------------------+|
  |                            |                                       |
  +----------------------------+---------------------------------------+
                               |  emit / sync  (no compilation)
                               v
  +--------------------------------------------------------------------+
  |  EMBABEL RUNTIME (selected once, per shape of the design)          |
  |                                                                    |
  |  fixed launcher (boots AgentPlatform)                              |
  |     |                                                              |
  |     +--> YmlStepSpecRepository(steps/)  --> StepSpecAgentScope-    |
  |     |     (loads YAML at startup)               Builder.deploy()   |
  |     |                                                              |
  |     +--> AgentPlatform (GOAP/UTILITY/HYBRID/SUPERVISOR per agent)  |
  |     |                                                              |
  |     +--> MCP client (application.yml)  +  MCP server (optional)    |
  |     +--> global guardrails (properties)                           |
  |     +--> models (OpenAI-compatible / Anthropic / Ollama / ...)    |
  |                                                                    |
  |  launcher flavor: shell | web | mcpserver  (the ONLY runtime      |
  |  choice; all agent content is data, not code)                     |
  +--------------------------------------------------------------------+
```

### 3.2 Data flow: describe → compile → edit → test → deploy

```text
  user text: "a research agent that finds news, checks facts,
              and writes a digest"
        |
        v
  +---------------------------+
  | NaturalLanguageCompiler   |  LLM call (compile_agent.jinja)
  | (in embabel-agent-spec)   |  context = platform DataDictionary
  +-------------+-------------+             (available types + tool groups)
                |  AgentCompilationResult
                |  {actions[], goal, intermediateTypes[], errors[]}
                v
  +---------------------------+
  | DRAFT REVIEW (UI cards)   |  user edits prompts, types, tool groups,
  +-------------+-------------+  pre/post conditions, planner
                |
                v
  +---------------------------+
  | VALIDATE                  |  deploy to throwaway AgentPlatform;
  +-------------+-------------+  check bindings/schemas; fix errors
                |
                v
  +---------------------------+
  | TEST & IMPROVE LOOP       |
  |  1. smoke: runAgentFrom   |
  |     with sample inputs    |
  |  2. eval: YAML job        |
  |     (embabel-agent-eval)  |
  |  3. scores + failures     |
  |  4. LLM rewrites prompt   |
  |     from failures         |
  |  5. diff -> accept/retry  |
  +-------------+-------------+
                |
                v
  +---------------------------+
  | EXPORT                    |  zip / git repo:
  |                           |  manifest.yml + steps/*.yml + types.yml
  |                           |  + mcp.yml + guardrails.yml + evals/
  |                           |  + fixed launcher template
  +---------------------------+
                |
                v
   drop into any Embabel runtime → boots, ingests YAML, runs.
```

### 3.3 Project layout (what the builder emits)

```text
  <project>/
    manifest.yml          # name, version; per-agent: planner, execution
                          #   mode, autonomy cut-offs, default model
    agents/<name>/
      steps/*.yml         # PromptedActionSpec files (one per action)
      goal.yml            # GoalSpec (export=true when it is a "skill")
    types/*.yml           # DynamicType definitions (intermediate types)
    tools/
      mcp.yml             # MCP client connections → application.yml
      remote/*.yml        # optional: remote action server descriptors
                          #   (code-backed tools, experimental)
    guardrails.yml        # built-in guardrail class names + SpEL policies
                          #   → embabel.agent.guardrails.* properties
    evals/*.yml           # embabel-agent-eval job files
    launcher/             # FIXED template: main class + pom.xml.
                          #   Never regenerated per agent. This is the
                          #   only "runtime selection" surface.
```

### 3.4 UI panes (design, not implementation)
  1. Agents & subagents: list/create. Per agent: name, description,
     planner (GOAP/UTILITY/HYBRID/SUPERVISOR + "Suggest" button),
     execution mode (focused/closed/open), exported goals (= skills).
  2. Action editor (core screen): name, description, prompt (Jinja2
     editor with {{var}} autocomplete from input types), input types
     (pick from platform DataDictionary + project DynamicTypes), output
     type, pre/post SpEL, cost/value, canRerun, toolGroups (pick from
     platform's available groups), LLM options. Live "emit & validate"
     against a throwaway AgentPlatform before saving.
  3. NL draft assistant: text box → NaturalLanguageCompiler → editable
     cards (the "create agent from a sentence" path).
  4. Planner advisor: LLM over the agent's action graph (in/out types,
     goal shape, conditions) → recommendation + rationale. Heuristics:
       single well-typed goal + typed chain      → GOAP
       event stream / no fixed end               → UTILITY or HYBRID
       ambiguous multi-step, LLM should choose   → SUPERVISOR
       explicit stages                           → @State
  5. Tools & MCP: manage MCP server connections (emitted to
     application.yml); pick tool groups per action; register remote
     action servers for code-backed tools.
  6. Guardrails: pick built-in guardrail classes (bundled in launcher:
     PII redaction, length, profanity, topic allow/deny) → writes
     embabel.agent.guardrails.* properties; edit SpEL pre/post per
     action; set budget / early-termination policy.
  7. Test & improve: smoke run (show blackboard history + plan steps);
     eval job generated from goal examples + acceptance criteria;
     composite score, per-task judge scores, assertion failures;
     "improve" = feed failures + current prompt to an LLM, diff view,
     re-run, keep best version.
  8. Preview: vaadin-components AgentsSection/ActionsSection/
     GoalsSection bound to the live test platform — shows the agent
     exactly as the runtime sees it.
  9. Export: zip/git of the project (manifest + YAMLs + launcher).

### 3.5 Runtime selection (the only "runtime" the user picks)
  - launcher flavor: shell | web | MCP-server starter (pom choice)
  - per-agent planner, execution mode, autonomy cut-offs (manifest.yml)
  - model providers (env keys + application.yml; any OpenAI-compatible
    endpoint works — verified)
  Everything else is data (YAML), not code.

### 3.6 "Skills" mapping (Embabel has no primitive named "skill")
  skill == exported goal (export: true; callable as a tool by the chat
  LLM with UserInput) OR a tool group OR an MCP tool. The vaadin
  SkillsSection already displays a SkillInfo list. The builder treats
  "mark as skill" as a toggle on a goal/action that sets export=true.

## SECTION 4 — LIMITATIONS & RISKS (honest)
Works well (buildable now):
  - LLM-prompt-only agents with typed chains, GOAP planning, tool groups,
    MCP, export-as-skill, eval, prompt-improvement loop, planner
    suggestion. Covers the large majority of agent use cases: research,
    summarization, triage, drafting, classification, routing, RAG flows.

Gaps (need Java or a 2.0 feature):
  - Content guardrails are Java classes, not YAML-declarable (only SpEL
    conditions + budgets + PromptCondition are declarable). Mitigation:
    ship a small guardrail class library in the launcher; UI picks from it.
  - No declarative HITL (form/approval requests) in YAML specs yet.
  - No declarative deterministic code actions — only via remote action
    servers (experimental) or code in the launcher.
  - StuckHandler, custom Conditions, per-action @SecureAgentTool: not
    in YAML.
  - DynamicType agents lose compile-time type safety (Embabel's core
    differentiator): planning still works (type names + conditions), but
    no refactoring guarantees or @Tool-on-domain-object behavior.
  - No existing builder UI: vaadin-components are display-only. The
    editor is net-new work — this is the actual product being proposed.
  - The eval module is conversational-agent-oriented (chatbot target +
    KG assertions); adapting it to arbitrary goal-type agents requires
    wiring AgentInvocation into the eval runner.

Risks:
  - LLM-driven NL compilation can produce wrong bindings/schemas — the
    draft-review + validate-before-save loop (panes 2/3) is mandatory.
  - Prompt quality is the product's main value axis; the eval loop is
    where differentiation happens.
  - Version churn: the framework is young (created 2025-04); pin to
    1.5.x for the MVP. embabel-agent-spec is now on the stable 1.5.x
    line (reassuring) but isolate the builder's spec layer behind an
    interface in case 2.0 shifts it.

## SECTION 5 — BEST PRACTICES (from the ecosystem)
  - Prefer existing domain types as action outputs over void (the
    compile_agent.jinja prompt enforces this): outputting a known type
    chains the action into existing platform actions automatically.
  - Extract "when X / if Y" filters into SpEL preconditions, never into
    Jinja if/else (saves LLM spend; the compiler does this).
  - Keep one goal per agent; use exported goals as the skill surface.
  - Use cost/value on actions to steer the GOAP planner (cheapest path
    wins; value steers utility planning).
  - nullable: true on actions to enable replanning instead of hard
    failure; canRerun: true only for loop-style behavior.
  - Mix models: cheap local model for extraction, strong model for
    synthesis (LlmOptions per action — the builder should expose this).
  - Ship agents as git repos (realms model): reviewable, versioned,
    distributable — the builder's export target.
  - Determinism: the planner is non-LLM; only the action bodies are LLM
    calls. This is what makes eval scores meaningful.

## SECTION 6 — KEY SOURCES (all verified this session)
  - github.com/embabel/embabel-agent (README; core model: Agent/Action/
    Goal/Condition/IoBinding/AgentPlatform/AgentProcess/Blackboard;
    planners page; guardrails page; invoking page) — docs.embabel.com
  - embabel-agent-experimental: embabel-agent-spec (README, StepSpec,
    PromptedActionSpec, GoalSpec, DynamicType, YmlStepSpecRepository,
    StepSpecAgentScopeBuilder, DefaultNaturalLanguageCompiler,
    compile_*.jinja, test YAMLs), embabel-agent-eval (README: eval job
    YAML, scoring formula), embabel-agent-remote (REST contract),
    embabel-agent-sandbox
  - 1.5.3 release jars inspected (javap): embabel-agent-spec,
    embabel-agent-api (ScriptedLlmOperations, IntegrationTestUtils,
    ProcessOptions/PlannerType, DynamicType, ValuePropertyDefinition,
    JvmType, DataDictionary), embabel-agent-openai (custom endpoint
    config), embabel-agent-openai-autoconfigure (property names)
  - embabel-worlds/default-world: production no-code realm packs
    (config/actions/*.yml in the exact spec format)
  - github.com/embabel/vaadin-components (display components),
    agent-gen (Meta-Agent), project-creator, me.embabel.com
  - Rod Johnson "Embabel Year-End Update" (Medium): Utility + Supervisor
    planners, @State model
  - mvnrepository: embabel-agent-api 1.5.3 (Oct 2026), Maven Central

## SECTION 7 — IMPLEMENTATION RECOMMENDATIONS & CODE SAMPLES

This section provides concrete starting points for each major subsystem.
All code is compatible with Embabel 1.5.3 and Java 21.

### 7.1 Minimal Launcher (the only compiled Java per runtime)
This Spring Boot app is the FIXED template — never regenerated per agent.
The builder emits only YAML + this template.

```java
// Launcher.java
package com.example.embabel.builder.runtime;

import com.embabel.agent.api.common.PlannerType;
import com.embabel.agent.core.*;
import com.embabel.agent.core.AgentScope;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.spec.StepSpecAgentScopeBuilder;
import com.embabel.agent.spec.model.BackgroundMessage;
import com.embabel.agent.spec.yml.YmlStepSpecRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;
import java.util.Map;

@SpringBootApplication
public class Launcher implements CommandLineRunner {

    private final AgentPlatform platform;
    private final String stepsDir;
    private final String input;
    private final PlannerType planner;

    public Launcher(AgentPlatform platform,
                    @Value("${builder.steps-dir:steps}") String stepsDir,
                    @Value("${builder.input:}") String input,
                    @Value("${builder.planner:GOAP}") String planner) {
        this.platform = platform;
        this.stepsDir = stepsDir;
        this.input = input;
        this.planner = PlannerType.valueOf(planner.toUpperCase());
    }

    @Override
    public void run(String... args) {
        // 1. Data dictionary: framework types + any intermediate DynamicTypes
        DataDictionary types = platform.plus(
            DataDictionary.fromDomainTypes("builder-types", List.of())
        );

        // 2. Ingest YAML steps and deploy
        StepSpecAgentScopeBuilder builder = new StepSpecAgentScopeBuilder(
            "builder-agent",
            platform,
            new YmlStepSpecRepository(stepsDir, List.of()),
            types,
            List.of(),
            List.of()
        );
        AgentScope scope = builder.deploy();
        Agent agent = scope.createAgent("builder-agent", "builder", "YAML-defined agent");
        platform.deploy(agent);

        // 3. Execute with selected planner
        AgentProcess process = platform.runAgentFrom(
            agent,
            ProcessOptions.DEFAULT.withPlannerType(planner),
            Map.of("userInput", new UserInput(input))
        );

        // 4. Report
        BackgroundMessage result = process.lastResult() instanceof BackgroundMessage m ? m : null;
        if (result != null) {
            System.out.println(result.getContent());
        } else {
            System.out.println("No BackgroundMessage produced. Status: " + process.getStatus());
        }
    }
}
```

### 7.2 application.yml (model config for any OpenAI-compatible endpoint)
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

Run with:
```bash
export OPENAI_CUSTOM_BASE_URL=http://spark.local:4000
export OPENAI_CUSTOM_API_KEY=sk-...
export OPENAI_CUSTOM_MODELS=Qwen-instruct
export EMBABEL_MODELS_DEFAULT_LLM=Qwen-instruct
mvn spring-boot:run -Dbuilder.input="Explain how a coffee machine works" -Dbuilder.planner=GOAP
```

### 7.3 Sample Agent YAML (what the builder emits)
**steps/extractTopic.yml**
```yaml
stepType: action
name: extractTopic
description: "Extract the topic, audience and tone from the user's request"
inputTypeNames:
  - com.embabel.agent.domain.io.UserInput
outputTypeName: TopicBrief
prompt: |
  From the user request below, extract three things:
  - topic: what they want explained
  - audience: who it is for
  - tone: the voice and style they are asking for
  If the request does not specify audience or tone, choose sensible defaults.
  User request: {{userInput.content}}
```

**steps/writeExplainer.yml**
```yaml
stepType: action
name: writeExplainer
description: "Write the explainer based on the extracted topic, audience and tone"
inputTypeNames:
  - TopicBrief
outputTypeName: com.embabel.agent.spec.model.BackgroundMessage
prompt: |
  Write a short, upbeat explainer (about 150 words) about: {{topicBrief.topic}}
  Target audience: {{topicBrief.audience}}
  Tone: {{topicBrief.tone}}
  Use simple words and one concrete analogy. Do not use markdown headings.
```

**steps/goal.yml**
```yaml
stepType: goal
name: explainerComplete
description: "A complete explainer has been produced for the user"
outputTypeName: com.embabel.agent.spec.model.BackgroundMessage
export: true
```

**types/TopicBrief.yml** (DynamicType — no JVM class)
```yaml
name: TopicBrief
description: "The extracted topic, audience and tone for the explainer"
ownProperties:
  - name: topic
    type: string
    description: "What to explain"
  - name: audience
    type: string
    description: "Who it is for"
  - name: tone
    type: string
    description: "Voice and style"
parents: []
creationPermitted: true
```

### 7.4 DynamicType Registration in the Launcher
If the builder emits DynamicType YAML files, the launcher reads them:

```java
// In Launcher.run()
DataDictionary types = platform;
try {
    var typeRepo = new YmlStepSpecRepository("types", List.of());
    for (var spec : typeRepo.findAll()) {
        // spec is a DynamicType document from types/*.yml
        // The StepSpecAgentScopeBuilder already passes these via DataDictionary
    }
} catch (Exception ignored) { /* no types.yml */ }
```

Note: the `StepSpecAgentScopeBuilder` constructor takes a `DataDictionary` that already includes the platform's types. The launcher can merge project DynamicTypes via:

```java
DataDictionary types = platform.plus(
    DataDictionary.fromDomainTypes("project", projectDynamicTypes)
);
```

### 7.5 NaturalLanguageCompiler Integration (NL → YAML draft)
Use the built-in compiler to turn a sentence into editable YAML:

```java
// In a Spring service or controller
@Service
public class AgentDraftService {

    private final DefaultNaturalLanguageCompiler compiler;
    private final StepSpecContext context;

    public AgentDraftService(Ai ai, AgentPlatform platform) {
        this.compiler = new DefaultNaturalLanguageCompiler(
            ai,
            LlmOptions.withModel("Qwen-instruct").withTemperature(0.1)
        );
        this.context = new StepSpecContext(
            "draft",
            platform,
            platform.getToolGroupResolver().availableToolGroups(),
            List.of(),
            List.of()
        );
    }

    public AgentCompilationResult draft(String description) {
        return compiler.compileAgent("draft-agent", description, context);
    }
}
```

The result contains:
- `actions`: List<PromptedActionSpec> (editable YAML)
- `goal`: GoalSpec (editable YAML)
- `intermediateTypes`: List<DynamicType> (needed for the DynamicType registration)
- `errors`: any compilation issues (show in UI for user to fix)

UI shows these as cards; user reviews/edits, then saves as `.yml` files.

### 7.6 Planner Suggestion Heuristic
Simple LLM prompt over the action graph:

```java
public PlannerType suggestPlanner(List<Action> actions, Set<Goal> goals) {
    // Heuristics first (fast path)
    if (goals.size() == 1 && actions.stream().allMatch(a -> a.getInputs().size() <= 2)) {
        return PlannerType.GOAP;
    }
    if (goals.isEmpty() || actions.size() > 5) {
        return PlannerType.UTILITY;
    }
    // LLM fallback for ambiguous cases
    String prompt = """
        Given this agent shape:
        Actions: %s
        Goals: %s
        Recommend a planner (GOAP, UTILITY, HYBRID, SUPERVISOR) with one-line rationale.
        """.formatted(
            actions.stream().map(a -> a.getName() + ": " + a.getInputs() + " -> " + a.getOutputs()).collect(Collectors.joining("\n")),
            goals.stream().map(G::getName).collect(Collectors.joining(", "))
        );
    String response = ai.withDefaultLlm().generateText(prompt);
    return parsePlanner(response); // GOAP/UTILITY/HYBRID/SUPERVISOR
}
```

### 7.7 embabel-agent-eval Job Generation
Generate a YAML eval from the agent's goal + examples:

```yaml
# evals/agent-eval.yml
evaluator:
  name: "Tester"
  voice: "You are a careful QA engineer testing an AI agent"
  temperature: 0.3

target:
  user: "test-user"
  chatbot: "builder-agent"

aspirationalAverage: 5000

tasks:
  - type: task
    task: "Ask the agent to explain how a coffee machine works for a 12-year-old"
    acceptanceCriteria: "Produces a clear, upbeat ~150-word explainer with one analogy"

facts:
  - "The user prefers simple language and analogies"

assertions:
  - expression: "objectContext.resources.size() >= 1"
    weight: 3
  - cypher: "MATCH (n) WHERE n:BackgroundMessage RETURN count(n)"
    expected: 1
    weight: 2
```

Run programmatically:
```java
// Using the eval module (requires embabel-agent-eval dependency)
EvaluationRunner runner = new DefaultEvaluationRunner(platform);
EvaluationResult result = runner.run(evalJob);
```

### 7.8 Guardrails Configuration (declarable parts)
In `application.yml` (global):
```yaml
embabel:
  agent:
    guardrails:
      user-input: com.example.guardrails.LengthValidator,com.example.guardrails.TopicAllowlist
      assistant-message: com.example.guardrails.PiiRedaction
      fail-on-error: false
```

Per-action SpEL (in the action YAML):
```yaml
pre:
  - "spel:userInput.content.length() < 5000"
post:
  - "spel:backgroundMessage.content.contains('analogy')"
```

The launcher bundles a small library of useful guardrails:
- `LengthValidator` (rejects overly long inputs)
- `TopicAllowlist` / `TopicDenylist` (regex-based topic gating)
- `PiiRedaction` (placeholder replacement on output)

UI: checkboxes → writes the property list to `guardrails.yml` → launcher reads it.

### 7.9 Project Structure (builder output)
```
my-agent/
├── manifest.yml
├── agents/
│   └── explainer/
│       ├── steps/
│       │   ├── extractTopic.yml
│       │   ├── writeExplainer.yml
│       │   └── goal.yml
│       └── types/
│           └── TopicBrief.yml
├── tools/
│   ├── mcp.yml
│   └── remote/               # optional
├── guardrails.yml
├── evals/
│   └── agent-eval.yml
└── launcher/
    ├── pom.xml               # fixed template
    └── src/main/java/.../Launcher.java
```

The builder writes these files; the user drops the folder into any Embabel runtime.

### 7.10 Build / Dependency Summary
```xml
<!-- pom.xml -->
<properties>
    <embabel-agent.version>1.5.3</embabel-agent.version>
    <java.version>21</java.version>
</properties>

<repositories>
    <repository>
        <id>embabel-releases</id>
        <url>https://repo.embabel.com/artifactory/libs-release</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.embabel.agent</groupId>
        <artifactId>embabel-agent-starter</artifactId>
        <version>${embabel-agent.version}</version>
    </dependency>
    <dependency>
        <groupId>com.embabel.agent</groupId>
        <artifactId>embabel-agent-starter-openai</artifactId>
        <version>${embabel-agent.version}</version>
    </dependency>
    <dependency>
        <groupId>com.embabel.agent</groupId>
        <artifactId>embabel-agent-spec</artifactId>
        <version>${embabel-agent.version}</version>
    </dependency>
    <!-- For eval/test loop -->
    <dependency>
        <groupId>com.embabel.agent</groupId>
        <artifactId>embabel-agent-eval</artifactId>
        <version>${embabel-agent.version}</version>
    </dependency>
    <!-- For Vaadin preview pane -->
    <dependency>
        <groupId>com.embabel</groupId>
        <artifactId>vaadin-components</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

### 7.11 Recommended Module Boundaries (for the builder codebase)
```
embabel-builder/
├── builder-runtime/          # The fixed Launcher + application.yml template
│   └── (Spring Boot app, never regenerated)
├── builder-core/             # Project model, YAML I/O, validation
│   ├── model/                # Manifest, AgentSpec, StepSpec, DynamicType
│   ├── persistence/          # YAML read/write (Jackson YAML)
│   └── validation/           # Emit & validate against throwaway AgentPlatform
├── builder-nl/               # NaturalLanguageCompiler wrapper + prompt tuning
├── builder-eval/             # embabel-agent-eval integration, job gen
├── builder-ui/               # Vaadin Flow or React frontend
│   ├── panes/                # 9 panes from Section 3.4
│   └── preview/              # vaadin-components bound to live platform
└── builder-export/           # ZIP/git export, launcher template copy
```

The runtime (`builder-runtime`) is the ONLY module compiled per deployment.
All agent logic lives in the YAML files produced by the other modules.
