# Embabel Spec-First Agent Builder
## Synthesis: "Specs, Not Agents" Paradigm + Embabel Architecture
*Compiled 2026-10-30*

---

## 0. Executive Summary

**Thesis:** The Embabel architecture *already embodies* the "specs, not agents" paradigm from the agent-spec literature — it just calls them **exported goals** and **tool groups** instead of "skills." Building a spec-first agent builder on Embabel means:

1. **Surface the existing primitives as specs**: `export: true` on a GoalSpec = spec; a tool group = spec bundle; an MCP server = external spec.
2. **Add the missing "specs layer"**: a **Spec Registry** (spec selection, versioning, trust tiers), **Spec Composer** (chaining exported goals into workflows), and **Spec Runtime Adapter** (CLI/script invocation via Embabel's existing `toolGroups`, `remote` actions, and MCP).
3. **Use Embabel's non-LLM planner (GOAP)** as the *spec router* — it already plans over typed pre/postconditions, which is exactly the "spec selection" problem the literature identifies as the bottleneck (spec shadowing).

**Result:** You get a **single general-purpose Embabel agent** that loads specs on demand (progressive disclosure via YAML ingestion), executes them via deterministic planning, and can invoke arbitrary CLIs/scripts through three battle-tested bridges — **no multi-agent orchestration required.**

---

## 1. Mapping: Agent Skills Literature ↔ Embabel Primitives

| Agent Skills Concept (arXiv:2602.12430, Anthropic, OpenAI) | Embabel Primitive (v1.5.4) | Status |
|---|---|---|
| **Skill** = folder with `SKILL.md` (metadata + instructions + scripts) | **Exported Goal** (`export: true`) + **PromptedActionSpec** + **DynamicType** | ✅ Native |
| **Progressive disclosure** (L1: metadata, L2: instructions, L3: scripts) | **YAML ingestion**: StepSpecRepository loads only referenced specs; DynamicTypes on demand; scripts via `toolGroups`/`remote` | ✅ Native |
| **Skill = composable, versioned, portable package** | **Realm** = git repo of YAML steps + types + launcher; versioned via git | ✅ Native |
| **Skill extends agent without retraining** | **Runtime deployment**: `AgentPlatform.deploy(scope)` ingests YAML at startup — no compile | ✅ Native |
| **Skill acquisition (agent learns skills)** | **NaturalLanguageCompiler** (NL → YAML draft) + **eval loop** (LLM rewrites prompts from failures) | ✅ Built-in |
| **Hierarchical skill base** | **DynamicType inheritance** + **GOAP planning over typed graph** (actions = edges, types = nodes) | ✅ Emergent |
| **Skill programs / executable guardrails (HASP)** | **SpEL pre/post conditions** + **PromptCondition** (LLM-evaluated) + **remote actions** (deterministic code) | ✅ Partial |
| **Skill retrieval at scale** | **DataDictionary** (type registry) + GOAP planner (selects actions by pre/post match) | ⚠️ Needs spec index layer |
| **Spec shadowing (selection failure at scale)** | **GOAP planner picks by type matching** — but no semantic retrieval; fails when many specs share types | 🔴 Gap |
| **Security / trust tiers (T1–T4)** | **Guardrails** (global + per-action SpEL) + **@SecureAgentTool** (Java only) | ⚠️ Partial |
| **Spec decay / lifecycle** | **No native lifecycle** — but eval harness + git versioning gives audit trail | 🔴 Gap |

**Key insight:** Embabel *is* a specs runtime. The "agent" in Embabel is just a **typed planning graph over specs** (exported goals + actions). The "specs, not agents" paradigm maps 1:1 to **Embabel's single AgentPlatform + many exported goals**.

---

## 2. The Missing Layer: Spec-First Builder Architecture

The Embabel builder design (doc 1) emits YAML that *already works*. To make it **spec-first**, add three components:

```
+------------------------------------------------------------------+
|                     SPEC-FIRST BUILDER                           |
|                                                                  |
|  +----------------+  +----------------+  +--------------------+  |
|  | SPEC REGISTRY  |  | SPEC COMPOSER  |  | SPEC RUNTIME       |  |
|  | (spec selection|  | (chain exported|  | ADAPTER            |  |
|  |  versioning,   |  |  goals into    |  | (CLI/MCP/script   |  |
|  |  trust tiers)  |  |  workflows)    |  |  invocation)       |  |
|  +-------+--------+  +-------+--------+  +--------+-----------+  |
|          |                    |                   |             |
|          v                    v                   v             |
|  +----------------------------------------------------------------+|
|  |           EMBABEL CORE (unchanged)                             ||
|  |  AgentPlatform + GOAP + YmlStepSpecRepository + MCP           ||
|  +----------------------------------------------------------------+|
+------------------------------------------------------------------+
```

### 2.1 Spec Registry (the "spec library" layer)

**Problem it solves:** Spec shadowing (arXiv:2605.24050) — when you have 100+ specs, the agent picks the wrong one because GOAP only matches on *types*, not semantics.

**Embabel-native solution:** Build a **semantic index** over `GoalSpec.description` + `PromptedActionSpec.description` + `DynamicType.description` using embeddings, and wrap the GOAP planner with a **pre-filter** that narrows candidates before planning.

```java
// SpecRegistry.java — thin wrapper over Embabel's DataDictionary + vector index
public class SpecRegistry {
    private final DataDictionary dictionary;
    private final VectorIndex<SpecEntry> index;  // e.g., HNSWLib, pgvector
    private final TrustTierPolicy trustPolicy;    // T1–T4 from survey

    // Register a spec (exported goal + its action chain)
    public void register(Spec spec) { ... }

    // Semantic retrieval: "specs relevant to 'summarize PDF'"
    public List<Spec> retrieve(String query, int k) { ... }

    // Trust-gated: only return specs the current context is allowed to use
    public List<Spec> retrieveAllowed(String query, Principal principal) { ... }

    // Emit YAML for the selected specs (for StepSpecAgentScopeBuilder)
    public void emitSelected(Collection<Spec> specs, Path stepsDir) { ... }
}

record SpecEntry(String specId, String name, String description,
                 List<String> inputTypes, String outputType,
                 TrustTier tier, String sourceHash) {}
```

**Trust tiers (from survey §6):**
- **T1 (Community)** — instructions only, no tool access, full isolation
- **T2 (Verified)** — instructions + read-only tools (search, web fetch)
- **T3 (Partner)** — instructions + write tools (file write, API calls) + MCP
- **T4 (Platform)** — full capabilities, including remote code execution

The builder UI shows tier badges; the launcher enforces via **tool group allowlists** per tier.

### 2.2 Spec Composer (chaining exported goals)

**Problem it solves:** The literature's "skill composition" (SkillTrace, SkillX) — finding a complete, executable chain of specs.

**Embabel-native solution:** GOAP *already does this* — it plans a path from `UserInput` → ... → `Goal.outputType` over action pre/postconditions. The composer just **surfaces this as a visual spec-chain editor** and lets users:
- Drag exported goals into a canvas
- See the GOAP-computed plan (or edit it manually)
- Export as a **new composite spec** (a new exported goal whose action chain *is* the plan)

```yaml
# Composite spec = exported goal whose plan calls other exported goals
stepType: goal
name: researchAndDigest
description: "Research a topic, verify facts, write a digest"
outputTypeName: Digest
export: true
# The planner will chain: extractTopic -> searchNews -> verifyFacts -> writeDigest
# Each step is an exported goal (spec) from the registry
```

### 2.3 Spec Runtime Adapter (CLI / script / MCP invocation)

**Problem it solves:** "How do specs invoke scripts or CLIs to behave like a normal agent harness?"

**Embabel has THREE bridges already** — the builder just needs to wire them:

| Bridge | Mechanism | Use For |
|---|---|---|
| **`toolGroups`** | Action declares `toolGroups: [cli, shell]` → platform injects tools | Standard CLI tools (grep, jq, curl, git, custom scripts in `$PATH`) |
| **`remote` actions** (experimental) | HTTP server implements 3 endpoints (`/actions`, `/types`, `/execute`) → GOAP plans over them | Custom code-backed tools, sandboxed execution, polyglot scripts |
| **MCP client/server** | `application.yml` configures MCP servers; agents can *be* MCP servers | Interop with Claude, VS Code, any MCP client; expose Embabel specs as MCP tools |

**The "normal agent harness" pattern:** A spec that needs to run a CLI does this:

```yaml
# steps/runCli.yml
stepType: action
name: runCli
description: "Execute a shell command and return structured output"
inputTypeNames:
  - CliCommand
outputTypeName: CliResult
toolGroups: [shell]          # <-- BRIDGE 1: injects shell tool
prompt: |
  Run this command and return the result as JSON:
  Command: {{cliCommand.cmd}}
  Args: {{cliCommand.args}}
  Timeout: {{cliCommand.timeoutMs}}
---
# types/CliCommand.yml
name: CliCommand
ownProperties:
  - name: cmd; type: string
  - name: args; type: array<string>
  - name: timeoutMs; type: integer; default: 30000
---
# types/CliResult.yml
name: CliResult
ownProperties:
  - name: exitCode; type: integer
  - name: stdout; type: string
  - name: stderr; type: string
```

The builder's **Spec Runtime Adapter** generates this boilerplate; the user just points at a script/CLI and the builder:
1. Wraps it in a `CliCommand`/`CliResult` DynamicType pair
2. Creates the `runCli` action with `toolGroups: [shell]`
3. Marks the *calling* goal as `export: true` (so it's a spec)
4. Registers it in the Spec Registry

**For polyglot/non-shell CLIs** (Python, Node, Go binaries): use **`remote` actions**. The builder spins up a sidecar HTTP server (or the user provides one) that implements:
```
GET  /api/v1/actions     → returns OpenAPI-like action specs
GET  /api/v1/types       → returns JSON schemas for input/output types
POST /api/v1/actions/execute → runs the CLI, returns typed output
```
The builder emits a `remote/*.yml` descriptor; the launcher's `application.yml` registers it. GOAP then plans over these remote actions *exactly like local ones*.

**For MCP-native tools:** The builder's **Tools & MCP pane** (doc 1, §3.4, pane 5) already manages MCP server connections. A spec that needs an MCP tool just declares `toolGroups: [mcp:<server-name>]` — the platform handles the rest.

---

## 3. Revised Builder Architecture (Spec-First)

Update the builder's **project layout** (doc 1, §3.3) to be spec-centric:

```
<project>/
  manifest.yml
  agent-specs/               # ← NEW: spec catalog (replaces agents/<name>/)
    <spec-id>/
      spec.yml               # GoalSpec (export: true) + action chain refs
      steps/*.yml            # PromptedActionSpec(s) for this spec
      types/*.yml            # DynamicType(s) for this spec
      scripts/               # Optional: bundled CLI scripts
      remote/                # Optional: remote action server descriptor
      mcp/                   # Optional: MCP tool references
      evals/*.yml            # Spec-specific eval jobs
      metadata.yml           # version, tier, author, dependencies, hash
  agent-specs-index.yml      # Generated: semantic index + dependency graph
  tools/
    mcp.yml                  # MCP client connections
    remote/                  # Remote action servers (shared)
    shell/                   # Shell tool config (allowed commands, env)
  guardrails.yml
  evals/                     # Project-level eval jobs
  launcher/                  # FIXED template
```

**Builder panes (updated from doc 1, §3.4):**

| Pane | Spec-First Role |
|---|---|
| **1. Spec Catalog** | Browse/install specs from registry (local + remote); filter by tier; see dependency graph |
| **2. Spec Composer** | Drag specs onto canvas → GOAP shows computed chain → export as new composite spec |
| **3. Spec Editor** | Edit a single spec: prompt, types, pre/post, tool groups, CLI/MCP/remote bridges |
| **4. NL → Spec** | "Create a spec that extracts JSON from PDF" → NaturalLanguageCompiler → draft spec |
| **5. Planner Advisor** | Recommends GOAP/UTILITY/HYBRID for the *spec's internal plan* |
| **6. Runtime Bridges** | Configure shell allowlist, remote servers, MCP connections per spec/project |
| **7. Trust & Security** | Set spec tier (T1–T4); audit bundled scripts; view guardrail coverage |
| **8. Test & Improve** | Smoke run spec in isolation; eval job per spec; LLM prompt rewrite loop |
| **9. Export** | Zip/git an **agent spec** (realm) — drop into any Embabel runtime |

---

## 4. Making It Behave Like a "Normal Agent Harness"

The user's mental model of an "agent harness": **you give it a task, it figures out which tools/scripts to call, runs them, returns a result.** Embabel + specs does this *exactly* — the wiring:

### 4.1 The "Single Agent" Loop (what the user sees)

```
User: "Research competitors for Acme Corp and write a 2-page brief"
       │
       ▼
┌─────────────────────────────────────────────────────────────┐
│  Embabel AgentPlatform (single instance, boots once)        │
│                                                              │
│  1. Spec sources resolve folder:/git: URIs → find spec dirs, │
│     validate, exclude invalid with warnings; runtime ingests │
│     the valid specs (no runtime selection: GOAP does not do  │
│     runtime spec discovery — spec selection is a build-time  │
│     experiment, POCs 8/12/17/19/20/23)                       │
│                                                              │
│  2. StepSpecAgentScopeBuilder deploys the ingested specs     │
│     as a temporary AgentScope                                │
│                                                              │
│  3. GOAP Planner plans:                                      │
│     UserInput → research-web → extract-entities → write-brief│
│     (each step is an exported goal = spec)                   │
│                                                              │
│  4. Execution:                                               │
│     research-web.action  → toolGroups: [web, search]        │
│     extract-entities.action → toolGroups: [shell]           │
│         → runs `python scripts/extract.py` via shell tool   │
│     write-brief.action → toolGroups: [shell]                │
│         → runs `pandoc` to generate PDF                     │
│                                                              │
│  5. Result: BackgroundMessage (the brief) returned to user  │
└─────────────────────────────────────────────────────────────┘
```

**No sub-agents. No orchestration code. Just:** spec ingestion → GOAP plan → execution via tool bridges.

### 4.2 CLI/Script Invocation in Detail

**Option A: Shell Tool Group (simplest, built-in)**
- Builder configures `shell` tool group with allowlist: `["python", "node", "jq", "pandoc", "custom-script"]`
- Spec declares `toolGroups: [shell]`
- Prompt uses `{{cliCommand}}` templating; Embabel's `ShellTool` executes
- **Pros:** Zero extra infrastructure; works for any CLI in PATH
- **Cons:** No sandboxing; limited to stdout/stdin JSON

**Option B: Remote Action Server (polyglot, sandboxed, typed)**
- Builder generates a sidecar server (or user provides one) per spec pack:
  ```python
  # agent-specs/extract-entities/remote/server.py (FastAPI)
  from fastapi import FastAPI
  from pydantic import BaseModel
  
  class ExtractIn(BaseModel): text: str
  class ExtractOut(BaseModel): entities: list[str]
  
  app = FastAPI()
  
  @app.get("/api/v1/actions")
  def actions():
      return [{"name": "extract", "input": "ExtractIn", "output": "ExtractOut"}]
  
  @app.get("/api/v1/types")
  def types():
      return {"ExtractIn": ExtractIn.schema(), "ExtractOut": ExtractOut.schema()}
  
  @app.post("/api/v1/actions/execute")
  def execute(req: dict):
      # Run the actual CLI/script here
      result = subprocess.run(["python", "extract.py"], input=req["input"]["text"], capture_output=True, text=True)
      return {"output": {"entities": json.loads(result.stdout)}}
  ```
- Builder emits `agent-specs/extract-entities/remote/remote.yml`:
  ```yaml
  name: extract-entities-remote
  baseUrl: http://localhost:8081
  actions:
    - name: extract
      inputType: ExtractIn
      outputType: ExtractOut
  ```
- Launcher's `application.yml` registers it; GOAP plans over it natively.

**Option C: MCP (interop, standardized)**
- Spec declares `toolGroups: [mcp:filesystem]` or `mcp:github`
- Embabel's MCP client (Spring AI) handles protocol
- Spec can *also* be exposed as an MCP server (`embabel-agent-starter-mcpserver`) — other agents/Claude can call it

### 4.3 The "Harness" Feel: REPL + Observability

The builder's **Test & Improve pane** (pane 7) *is* the harness UI:

```
$ embabel-builder test --spec research-web --input "Acme Corp competitors"
> Deploying spec pack... done
> GOAP Plan: UserInput → searchWeb → extractEntities → summarize
> Step 1/3: searchWeb (toolGroups: [web]) → 12 results
> Step 2/3: extractEntities (toolGroups: [shell]) → python scripts/extract.py → 47 entities
> Step 3/3: summarize (toolGroups: [llm]) → BackgroundMessage
> RESULT: "Acme Corp's top 5 competitors are..."
> EVAL SCORE: 0.92/1.0 (passes acceptance criteria)
```

**Blackboard history** (Embabel's built-in trace) shows every step's input/output/precondition check — full debuggability.

---

## 5. Addressing the Literature's Caveats in Embabel

| Caveat (from agent-spec-research.md) | Embabel Mitigation |
|---|---|
| **Spec shadowing** (selection failure at scale) | **SpecRegistry semantic pre-filter** (build-time selection experiment, POCs 8/12/17/19/20/23) + GOAP type-based planning over ingested specs; trust tiers limit candidate set |
| **LLM-authored specs don't help** | **NaturalLanguageCompiler** produces *drafts*; builder forces **human review + eval loop** before register (SkillAxe pattern) |
| **Security: 26% vuln rate** | **Trust tiers (T1–T4)** enforced by tool-group allowlists; guardrails at platform level; script audit in UI |
| **Spec drift/decay** | **Eval harness per spec** + git versioning; builder shows "last eval date / score" badge; SpecOps-style proactive re-eval job |
| **No agents = default, not absolute** | **Subagents = nested processes** (`asSubProcess`) — available but discouraged; builder UI hides by default |
| **Read-centric parallelism** | **UTILITY/HYBRID planner** for parallel spec execution; GOAP for sequential chains |

---

## 6. Implementation Roadmap (Builder MVP → Spec-First)

| Phase | Scope | Embabel Modules | New Code |
|---|---|---|---|
| **0. Foundation** | YAML agent builder (doc 1 design) | `embabel-agent-spec`, `embabel-agent-eval`, `vaadin-components` | Builder UI (panes 1–9), launcher template, export |
| **1. Specs Layer** | Spec Registry + Composer + Runtime Adapter | + `embabel-agent-remote` (for CLI bridging) | `SpecRegistry`, `SpecComposer`, `RemoteActionBridge`, `ShellToolGroupConfig` |
| **2. Trust & Governance** | T1–T4 tiers, guardrail library, drift detection | + global guardrails config | `TrustTierPolicy`, `GuardrailLibrary`, `DriftWatcher` (scheduled eval) |
| **3. Scale & Intelligence** | Semantic spec selection (build-time, POCs 8/12/17/19/20/23), SkillGrad-style self-improvement, SE-GoS graph evolution | (custom) | Vector index, `SpecOptimizer` (LLM rewrites prompts from eval failures), `SpecGraph` |
| **4. Ecosystem** | Remote spec marketplace, MCP spec server, A2A spec mesh | + `embabel-agent-starter-mcpserver`, A2A | Spec pack registry (git/HTTP), MCP server mode, A2A spec discovery |

**MVP (Phase 0 + 1)** is **buildable today** with Embabel 1.5.4. The "specs, not agents" paradigm is not a research project — it's *how Embabel already works*; the builder just needs to surface it.

---

## 7. Code Sketch: Spec-First Launcher

The fixed launcher template (doc 1, §7.1) extended for spec-first runtime:

```java
// SpecsFirstLauncher.java — the ONLY compiled code per deployment
@SpringBootApplication
public class SpecsFirstLauncher implements CommandLineRunner {

    private final AgentPlatform platform;
    private final SpecRegistry specRegistry;         // NEW
    private final String specsDir;
    private final String query;                       // user task
    private final Principal principal;                // for trust tier

    @Override
    public void run(String... args) {
        // 1. Load spec index (built at export time)
        specRegistry.loadIndex(Paths.get(specsDir, "agent-specs-index.yml"));

        // 2. Source resolution: folder:/git: URIs → find spec dirs, validate,
        //    exclude invalid with warnings — no intelligence. Spec selection
        //    (POCs 8/12/17/19/20/23) is a build-time experiment, never wired
        //    into this runtime; the runtime ingests specs and plans over them
        List<Spec> ingested = specRegistry.loadFromSources(specsDir);

        // 3. Emit ingested specs' YAML to temp dir for StepSpecAgentScopeBuilder
        Path tempSteps = Files.createTempDirectory("specs-");
        specRegistry.emit(ingested, tempSteps);

        // 4. Deploy (same as before)
        DataDictionary types = platform.plus(
            DataDictionary.fromDomainTypes("project", specRegistry.collectTypes(ingested))
        );
        StepSpecAgentScopeBuilder builder = new StepSpecAgentScopeBuilder(
            "specs-agent", platform,
            new YmlStepSpecRepository(tempSteps.toString(), List.of()),
            types,
            specRegistry.resolveToolGroups(ingested),  // shell, mcp, remote
            specRegistry.resolveRemoteActions(ingested)
        );
        AgentScope scope = builder.deploy();
        Agent agent = scope.createAgent("specs-agent", "builder", "Spec-first agent");
        platform.deploy(agent);

        // 5. Run with GOAP (plans over the ingested spec chain)
        AgentProcess process = platform.runAgentFrom(
            agent,
            ProcessOptions.DEFAULT.withPlannerType(PlannerType.GOAP),
            Map.of("userInput", new UserInput(query))
        );
        
        // 6. Result
        BackgroundMessage result = process.lastResult() instanceof BackgroundMessage m ? m : null;
        System.out.println(result != null ? result.getContent() : "No result: " + process.getStatus());
    }
}
```

**What changed from doc 1, §7.1:**
- Input is a **task query**, not a fixed input binding
- **SpecRegistry** does spec selection (a build-time experiment, POCs 8/12/17/19/20/23) + trust filtering + YAML emission; at runtime, **source resolution** (folder:/git: URIs) finds spec dirs, validates, and excludes invalid ones with warnings
- **Tool groups & remote actions** resolved per-spec from metadata
- **GOAP plans over the ingested spec chain** — no runtime selection step, the "harness" behavior

---

## 8. Conclusion

**The "specs, not agents" paradigm is not a constraint on Embabel — it's Embabel's native architecture.** 

- An **Embabel agent** = a planning graph over **exported goals** (specs)
- **Progressive disclosure** = YAML ingestion on demand (StepSpecRepository)
- **Spec scripts/CLIs** = `toolGroups: [shell]` + `remote` actions + MCP
- **Spec selection at scale** = semantic SpecRegistry pre-filter (build-time experiment, POCs 8/12/17/19/20/23); the runtime ingests specs and plans over them
- **Spec composition** = GOAP planning over typed pre/postconditions
- **Spec evolution** = NaturalLanguageCompiler + eval loop + git versioning
- **Security/governance** = trust tiers via tool-group allowlists + guardrails

**The builder's job:** Make this visible. Replace "create an agent" with "compose specs." The runtime is already a **single general-purpose agent that loads specs on demand** — exactly what the literature recommends.

**Start with Phase 0 (YAML builder)** — it emits runnable spec packs today. Add the SpecRegistry and Runtime Adapter in Phase 1. The result is a spec-first agent builder that behaves like a normal harness, runs on Embabel's deterministic planner, and invokes any CLI/script/MCP tool through battle-tested bridges.

---

## Appendix: Key Cross-References

| Concept | Agent Skills Doc (doc 2) | Embabel / Builder Doc |
|---|---|---|
| Spec definition | §2 (SKILL.md, progressive disclosure) | doc 1 §2.1 (PromptedActionSpec, GoalSpec, DynamicType) |
| Spec retrieval | §3.4 (SkillTrace, SE-GoS) | doc 1 §1 (DataDictionary + GOAP) + this doc §2.1 (SpecRegistry) |
| Spec execution | §3.5 (HASP, spec programs) | doc 1 §1 (SpEL pre/post, remote actions, toolGroups) |
| Spec security | §5.3 (trust tiers, 26% vuln) | doc 1 §1 (guardrails) + doc 1 §4 (gaps) + this doc §2.1 (TrustTierPolicy) |
| Spec lifecycle | §5.4 (SpecOps, SkillDrift) | this doc §2.3 (eval harness) + git versioning |
| CLI invocation | — | this doc §2.3 (toolGroups, remote, MCP) + this doc §4.2 |
| Builder UI | — | doc 1 §3.4 (9 panes) + this doc §3 (updated panes) |
| Export format | Agentskills.io standard | doc 1 §3.3 (realm git repo) + this doc §3 (agent-specs/ layout) |