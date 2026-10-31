# Embabel Skills-First Agent Builder
## Synthesis: "Skills, Not Agents" Paradigm + Embabel Architecture
*Compiled 2026-10-30*

---

## 0. Executive Summary

**Thesis:** The Embabel architecture *already embodies* the "skills, not agents" paradigm from the agent-skills literature — it just calls them **exported goals** and **tool groups** instead of "skills." Building a skills-first agent builder on Embabel means:

1. **Surface the existing primitives as skills**: `export: true` on a GoalSpec = skill; a tool group = skill bundle; an MCP server = external skill.
2. **Add the missing "skills layer"**: a **Skill Registry** (discovery, versioning, trust tiers), **Skill Composer** (chaining exported goals into workflows), and **Skill Runtime Adapter** (CLI/script invocation via Embabel's existing `toolGroups`, `remote` actions, and MCP).
3. **Use Embabel's non-LLM planner (GOAP)** as the *skill router* — it already plans over typed pre/postconditions, which is exactly the "skill selection" problem the literature identifies as the bottleneck (skill shadowing).

**Result:** You get a **single general-purpose Embabel agent** that loads skills on demand (progressive disclosure via YAML ingestion), executes them via deterministic planning, and can invoke arbitrary CLIs/scripts through three battle-tested bridges — **no multi-agent orchestration required.**

---

## 1. Mapping: Agent Skills Literature ↔ Embabel Primitives

| Agent Skills Concept (arXiv:2602.12430, Anthropic, OpenAI) | Embabel Primitive (v1.5.3) | Status |
|---|---|---|
| **Skill** = folder with `SKILL.md` (metadata + instructions + scripts) | **Exported Goal** (`export: true`) + **PromptedActionSpec** + **DynamicType** | ✅ Native |
| **Progressive disclosure** (L1: metadata, L2: instructions, L3: scripts) | **YAML ingestion**: StepSpecRepository loads only referenced specs; DynamicTypes on demand; scripts via `toolGroups`/`remote` | ✅ Native |
| **Skill = composable, versioned, portable package** | **Realm** = git repo of YAML steps + types + launcher; versioned via git | ✅ Native |
| **Skill extends agent without retraining** | **Runtime deployment**: `AgentPlatform.deploy(scope)` ingests YAML at startup — no compile | ✅ Native |
| **Skill acquisition (agent learns skills)** | **NaturalLanguageCompiler** (NL → YAML draft) + **eval loop** (LLM rewrites prompts from failures) | ✅ Built-in |
| **Hierarchical skill base** | **DynamicType inheritance** + **GOAP planning over typed graph** (actions = edges, types = nodes) | ✅ Emergent |
| **Skill programs / executable guardrails (HASP)** | **SpEL pre/post conditions** + **PromptCondition** (LLM-evaluated) + **remote actions** (deterministic code) | ✅ Partial |
| **Skill retrieval at scale** | **DataDictionary** (type registry) + GOAP planner (selects actions by pre/post match) | ⚠️ Needs skill index layer |
| **Skill shadowing (selection failure at scale)** | **GOAP planner picks by type matching** — but no semantic retrieval; fails when many skills share types | 🔴 Gap |
| **Security / trust tiers (T1–T4)** | **Guardrails** (global + per-action SpEL) + **@SecureAgentTool** (Java only) | ⚠️ Partial |
| **Skill decay / lifecycle** | **No native lifecycle** — but eval harness + git versioning gives audit trail | 🔴 Gap |

**Key insight:** Embabel *is* a skills runtime. The "agent" in Embabel is just a **typed planning graph over skills** (exported goals + actions). The "skills, not agents" paradigm maps 1:1 to **Embabel's single AgentPlatform + many exported goals**.

---

## 2. The Missing Layer: Skills-First Builder Architecture

The Embabel builder design (doc 1) emits YAML that *already works*. To make it **skills-first**, add three components:

```
+------------------------------------------------------------------+
|                    SKILLS-FIRST BUILDER                          |
|                                                                  |
|  +----------------+  +----------------+  +--------------------+  |
|  | SKILL REGISTRY |  | SKILL COMPOSER |  | SKILL RUNTIME      |  |
|  | (discovery,    |  | (chain exported|  | ADAPTER            |  |
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

### 2.1 Skill Registry (the "skill library" layer)

**Problem it solves:** Skill shadowing (arXiv:2605.24050) — when you have 100+ skills, the agent picks the wrong one because GOAP only matches on *types*, not semantics.

**Embabel-native solution:** Build a **semantic index** over `GoalSpec.description` + `PromptedActionSpec.description` + `DynamicType.description` using embeddings, and wrap the GOAP planner with a **pre-filter** that narrows candidates before planning.

```java
// SkillRegistry.java — thin wrapper over Embabel's DataDictionary + vector index
public class SkillRegistry {
    private final DataDictionary dictionary;
    private final VectorIndex<SkillEntry> index;  // e.g., HNSWLib, pgvector
    private final TrustTierPolicy trustPolicy;    // T1–T4 from survey
    
    // Register a skill (exported goal + its action chain)
    public void register(Skill skill) { ... }
    
    // Semantic retrieval: "skills relevant to 'summarize PDF'"
    public List<Skill> retrieve(String query, int k) { ... }
    
    // Trust-gated: only return skills the current context is allowed to use
    public List<Skill> retrieveAllowed(String query, Principal principal) { ... }
    
    // Emit YAML for the selected skills (for StepSpecAgentScopeBuilder)
    public void emitSelected(Collection<Skill> skills, Path stepsDir) { ... }
}

record SkillEntry(String skillId, String name, String description, 
                  List<String> inputTypes, String outputType,
                  TrustTier tier, String sourceHash) {}
```

**Trust tiers (from survey §6):**
- **T1 (Community)** — instructions only, no tool access, full isolation
- **T2 (Verified)** — instructions + read-only tools (search, web fetch)
- **T3 (Partner)** — instructions + write tools (file write, API calls) + MCP
- **T4 (Platform)** — full capabilities, including remote code execution

The builder UI shows tier badges; the launcher enforces via **tool group allowlists** per tier.

### 2.2 Skill Composer (chaining exported goals)

**Problem it solves:** The literature's "skill composition" (SkillTrace, SkillX) — finding a complete, executable chain of skills.

**Embabel-native solution:** GOAP *already does this* — it plans a path from `UserInput` → ... → `Goal.outputType` over action pre/postconditions. The composer just **surfaces this as a visual skill-chain editor** and lets users:
- Drag exported goals into a canvas
- See the GOAP-computed plan (or edit it manually)
- Export as a **new composite skill** (a new exported goal whose action chain *is* the plan)

```yaml
# Composite skill = exported goal whose plan calls other exported goals
stepType: goal
name: researchAndDigest
description: "Research a topic, verify facts, write a digest"
outputTypeName: Digest
export: true
# The planner will chain: extractTopic -> searchNews -> verifyFacts -> writeDigest
# Each step is an exported goal (skill) from the registry
```

### 2.3 Skill Runtime Adapter (CLI / script / MCP invocation)

**Problem it solves:** "How do skills invoke scripts or CLIs to behave like a normal agent harness?"

**Embabel has THREE bridges already** — the builder just needs to wire them:

| Bridge | Mechanism | Use For |
|---|---|---|
| **`toolGroups`** | Action declares `toolGroups: [cli, shell]` → platform injects tools | Standard CLI tools (grep, jq, curl, git, custom scripts in `$PATH`) |
| **`remote` actions** (experimental) | HTTP server implements 3 endpoints (`/actions`, `/types`, `/execute`) → GOAP plans over them | Custom code-backed tools, sandboxed execution, polyglot scripts |
| **MCP client/server** | `application.yml` configures MCP servers; agents can *be* MCP servers | Interop with Claude, VS Code, any MCP client; expose Embabel skills as MCP tools |

**The "normal agent harness" pattern:** A skill that needs to run a CLI does this:

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

The builder's **Skill Runtime Adapter** generates this boilerplate; the user just points at a script/CLI and the builder:
1. Wraps it in a `CliCommand`/`CliResult` DynamicType pair
2. Creates the `runCli` action with `toolGroups: [shell]`
3. Marks the *calling* goal as `export: true` (so it's a skill)
4. Registers it in the Skill Registry

**For polyglot/non-shell CLIs** (Python, Node, Go binaries): use **`remote` actions**. The builder spins up a sidecar HTTP server (or the user provides one) that implements:
```
GET  /api/v1/actions     → returns OpenAPI-like action specs
GET  /api/v1/types       → returns JSON schemas for input/output types
POST /api/v1/actions/execute → runs the CLI, returns typed output
```
The builder emits a `remote/*.yml` descriptor; the launcher's `application.yml` registers it. GOAP then plans over these remote actions *exactly like local ones*.

**For MCP-native tools:** The builder's **Tools & MCP pane** (doc 1, §3.4, pane 5) already manages MCP server connections. A skill that needs an MCP tool just declares `toolGroups: [mcp:<server-name>]` — the platform handles the rest.

---

## 3. Revised Builder Architecture (Skills-First)

Update the builder's **project layout** (doc 1, §3.3) to be skills-centric:

```
<project>/
  manifest.yml
  skills/                    # ← NEW: skill catalog (replaces agents/<name>/)
    <skill-id>/
      spec.yml               # GoalSpec (export: true) + action chain refs
      steps/*.yml            # PromptedActionSpec(s) for this skill
      types/*.yml            # DynamicType(s) for this skill
      scripts/               # Optional: bundled CLI scripts
      remote/                # Optional: remote action server descriptor
      mcp/                   # Optional: MCP tool references
      evals/*.yml            # Skill-specific eval jobs
      metadata.yml           # version, tier, author, dependencies, hash
  skills-index.yml           # Generated: semantic index + dependency graph
  tools/
    mcp.yml                  # MCP client connections
    remote/                  # Remote action servers (shared)
    shell/                   # Shell tool config (allowed commands, env)
  guardrails.yml
  evals/                     # Project-level eval jobs
  launcher/                  # FIXED template
```

**Builder panes (updated from doc 1, §3.4):**

| Pane | Skills-First Role |
|---|---|
| **1. Skill Catalog** | Browse/install skills from registry (local + remote); filter by tier; see dependency graph |
| **2. Skill Composer** | Drag skills onto canvas → GOAP shows computed chain → export as new composite skill |
| **3. Skill Editor** | Edit a single skill: prompt, types, pre/post, tool groups, CLI/MCP/remote bridges |
| **4. NL → Skill** | "Create a skill that extracts JSON from PDF" → NaturalLanguageCompiler → draft skill |
| **5. Planner Advisor** | Recommends GOAP/UTILITY/HYBRID for the *skill's internal plan* |
| **6. Runtime Bridges** | Configure shell allowlist, remote servers, MCP connections per skill/project |
| **7. Trust & Security** | Set skill tier (T1–T4); audit bundled scripts; view guardrail coverage |
| **8. Test & Improve** | Smoke run skill in isolation; eval job per skill; LLM prompt rewrite loop |
| **9. Export** | Zip/git a **skill pack** (realm) — drop into any Embabel runtime |

---

## 4. Making It Behave Like a "Normal Agent Harness"

The user's mental model of an "agent harness": **you give it a task, it figures out which tools/scripts to call, runs them, returns a result.** Embabel + skills does this *exactly* — the wiring:

### 4.1 The "Single Agent" Loop (what the user sees)

```
User: "Research competitors for Acme Corp and write a 2-page brief"
       │
       ▼
┌─────────────────────────────────────────────────────────────┐
│  Embabel AgentPlatform (single instance, boots once)        │
│                                                              │
│  1. SkillRegistry.retrieve("research competitors write brief")│
│     → returns [research-web, extract-entities, write-brief]  │
│                                                              │
│  2. StepSpecAgentScopeBuilder emits selected skills' YAML   │
│     → deploys as a temporary AgentScope                      │
│                                                              │
│  3. GOAP Planner plans:                                      │
│     UserInput → research-web → extract-entities → write-brief│
│     (each step is an exported goal = skill)                  │
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

**No sub-agents. No orchestration code. Just:** skill retrieval → GOAP plan → execution via tool bridges.

### 4.2 CLI/Script Invocation in Detail

**Option A: Shell Tool Group (simplest, built-in)**
- Builder configures `shell` tool group with allowlist: `["python", "node", "jq", "pandoc", "custom-script"]`
- Skill declares `toolGroups: [shell]`
- Prompt uses `{{cliCommand}}` templating; Embabel's `ShellTool` executes
- **Pros:** Zero extra infrastructure; works for any CLI in PATH
- **Cons:** No sandboxing; limited to stdout/stdin JSON

**Option B: Remote Action Server (polyglot, sandboxed, typed)**
- Builder generates a sidecar server (or user provides one) per skill pack:
  ```python
  # skills/extract-entities/remote/server.py (FastAPI)
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
- Builder emits `skills/extract-entities/remote/remote.yml`:
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
- Skill declares `toolGroups: [mcp:filesystem]` or `mcp:github`
- Embabel's MCP client (Spring AI) handles protocol
- Skill can *also* be exposed as an MCP server (`embabel-agent-starter-mcpserver`) — other agents/Claude can call it

### 4.3 The "Harness" Feel: REPL + Observability

The builder's **Test & Improve pane** (pane 7) *is* the harness UI:

```
$ embabel-builder test --skill research-web --input "Acme Corp competitors"
> Deploying skill pack... done
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

| Caveat (from agent-skills-research.md) | Embabel Mitigation |
|---|---|
| **Skill shadowing** (selection failure at scale) | **SkillRegistry semantic pre-filter** + GOAP type-based planning; trust tiers limit candidate set |
| **LLM-authored skills don't help** | **NaturalLanguageCompiler** produces *drafts*; builder forces **human review + eval loop** before register (SkillAxe pattern) |
| **Security: 26% vuln rate** | **Trust tiers (T1–T4)** enforced by tool-group allowlists; guardrails at platform level; script audit in UI |
| **Skill drift/decay** | **Eval harness per skill** + git versioning; builder shows "last eval date / score" badge; SkillOps-style proactive re-eval job |
| **No agents = default, not absolute** | **Subagents = nested processes** (`asSubProcess`) — available but discouraged; builder UI hides by default |
| **Read-centric parallelism** | **UTILITY/HYBRID planner** for parallel skill execution; GOAP for sequential chains |

---

## 6. Implementation Roadmap (Builder MVP → Skills-First)

| Phase | Scope | Embabel Modules | New Code |
|---|---|---|---|
| **0. Foundation** | YAML agent builder (doc 1 design) | `embabel-agent-spec`, `embabel-agent-eval`, `vaadin-components` | Builder UI (panes 1–9), launcher template, export |
| **1. Skills Layer** | Skill Registry + Composer + Runtime Adapter | + `embabel-agent-remote` (for CLI bridging) | `SkillRegistry`, `SkillComposer`, `RemoteActionBridge`, `ShellToolGroupConfig` |
| **2. Trust & Governance** | T1–T4 tiers, guardrail library, drift detection | + global guardrails config | `TrustTierPolicy`, `GuardrailLibrary`, `DriftWatcher` (scheduled eval) |
| **3. Scale & Intelligence** | Semantic retrieval, SkillGrad-style self-improvement, SE-GoS graph evolution | (custom) | Vector index, `SkillOptimizer` (LLM rewrites prompts from eval failures), `SkillGraph` |
| **4. Ecosystem** | Remote skill marketplace, MCP skill server, A2A skill mesh | + `embabel-agent-starter-mcpserver`, A2A | Skill pack registry (git/HTTP), MCP server mode, A2A skill discovery |

**MVP (Phase 0 + 1)** is **buildable today** with Embabel 1.5.3. The "skills, not agents" paradigm is not a research project — it's *how Embabel already works*; the builder just needs to surface it.

---

## 7. Code Sketch: Skills-First Launcher

The fixed launcher template (doc 1, §7.1) extended for skills-first runtime:

```java
// SkillsFirstLauncher.java — the ONLY compiled code per deployment
@SpringBootApplication
public class SkillsFirstLauncher implements CommandLineRunner {

    private final AgentPlatform platform;
    private final SkillRegistry skillRegistry;        // NEW
    private final String skillsDir;
    private final String query;                       // user task
    private final Principal principal;                // for trust tier

    @Override
    public void run(String... args) {
        // 1. Load skill index (built at export time)
        skillRegistry.loadIndex(Paths.get(skillsDir, "skills-index.yml"));
        
        // 2. Semantic retrieval + trust gating
        List<Skill> selected = skillRegistry.retrieveAllowed(query, principal, 10);
        
        // 3. Emit selected skills' YAML to temp dir for StepSpecAgentScopeBuilder
        Path tempSteps = Files.createTempDirectory("skills-");
        skillRegistry.emitSelected(selected, tempSteps);
        
        // 4. Deploy (same as before)
        DataDictionary types = platform.plus(
            DataDictionary.fromDomainTypes("project", skillRegistry.collectTypes(selected))
        );
        StepSpecAgentScopeBuilder builder = new StepSpecAgentScopeBuilder(
            "skills-agent", platform,
            new YmlStepSpecRepository(tempSteps.toString(), List.of()),
            types,
            skillRegistry.resolveToolGroups(selected),  // shell, mcp, remote
            skillRegistry.resolveRemoteActions(selected)
        );
        AgentScope scope = builder.deploy();
        Agent agent = scope.createAgent("skills-agent", "builder", "Skills-first agent");
        platform.deploy(agent);
        
        // 5. Run with GOAP (plans over the skill chain)
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
- **SkillRegistry** does retrieval + trust filtering + YAML emission
- **Tool groups & remote actions** resolved per-skill from metadata
- **GOAP plans over the retrieved skill chain** — the "harness" behavior

---

## 8. Conclusion

**The "skills, not agents" paradigm is not a constraint on Embabel — it's Embabel's native architecture.** 

- An **Embabel agent** = a planning graph over **exported goals** (skills)
- **Progressive disclosure** = YAML ingestion on demand (StepSpecRepository)
- **Skill scripts/CLIs** = `toolGroups: [shell]` + `remote` actions + MCP
- **Skill selection at scale** = GOAP + semantic SkillRegistry pre-filter
- **Skill composition** = GOAP planning over typed pre/postconditions
- **Skill evolution** = NaturalLanguageCompiler + eval loop + git versioning
- **Security/governance** = trust tiers via tool-group allowlists + guardrails

**The builder's job:** Make this visible. Replace "create an agent" with "compose skills." The runtime is already a **single general-purpose agent that loads skills on demand** — exactly what the literature recommends.

**Start with Phase 0 (YAML builder)** — it emits runnable skill packs today. Add the SkillRegistry and Runtime Adapter in Phase 1. The result is a skills-first agent builder that behaves like a normal harness, runs on Embabel's deterministic planner, and invokes any CLI/script/MCP tool through battle-tested bridges.

---

## Appendix: Key Cross-References

| Concept | Agent Skills Doc (doc 2) | Embabel / Builder Doc |
|---|---|---|
| Skill definition | §2 (SKILL.md, progressive disclosure) | doc 1 §2.1 (PromptedActionSpec, GoalSpec, DynamicType) |
| Skill retrieval | §3.4 (SkillTrace, SE-GoS) | doc 1 §1 (DataDictionary + GOAP) + this doc §2.1 (SkillRegistry) |
| Skill execution | §3.5 (HASP, skill programs) | doc 1 §1 (SpEL pre/post, remote actions, toolGroups) |
| Skill security | §5.3 (trust tiers, 26% vuln) | doc 1 §1 (guardrails) + doc 1 §4 (gaps) + this doc §2.1 (TrustTierPolicy) |
| Skill lifecycle | §5.4 (SkillOps, SkillDrift) | this doc §2.3 (eval harness) + git versioning |
| CLI invocation | — | this doc §2.3 (toolGroups, remote, MCP) + this doc §4.2 |
| Builder UI | — | doc 1 §3.4 (9 panes) + this doc §3 (updated panes) |
| Export format | Agentskills.io standard | doc 1 §3.3 (realm git repo) + this doc §3 (skills/ layout) |