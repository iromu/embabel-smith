# Building Agentic Applications with Specs, Not Agents
### A research survey (arXiv-primary, with industry grounding)
*Compiled 2026-10-30*

---

## 0. TL;DR

The approach of building agentic apps by **composing specs on one general-purpose agent** rather than **spinning up a fleet of specialized agents** is now the dominant direction in both industry and the academic literature. The term for it in the literature is **Agent Skills** (a.k.a. the "skill abstraction layer"), and it is formalized around three ideas:

1. **Progressive disclosure** — a spec is a folder with a `SKILL.md`; only its name+description (~100 tokens) is always in context, and the rest loads on demand.
2. **Specs are composable, versioned, portable packages** — like an "org standard library," not bespoke agents.
3. **Specs extend a general agent without retraining** — capability is added by loading files + running code, not by fine-tuning or by adding another agent.

The "advanced" layer on top (where the 2025→2026 research is concentrated) is **how the spec library itself is built, retrieved, evolved, and governed** — spec acquisition via RL and autonomous discovery, hierarchical spec bases, self-evolving libraries, retrieval at scale, and security/trust governance. That is the part that turns "just use specs" from a neat trick into a production technique.

The honest caveats: spec libraries **degrade as they grow** (spec shadowing, up to ~21% pass-rate drop), **LLM-authored specs still don't reliably help** (human-authored ones do, by ~16pp on SkillsBench), and **~26% of community specs carry vulnerabilities**. "Only specs, no agents" is a strong default, not an absolute rule — read-centric parallel work still benefits from sub-agents.

---

## 1. The core idea: specs over agents

The naive way to build agentic apps in 2024–2025 was **multi-agent orchestration**: a planner, a researcher, a coder, a reviewer — each a bespoke agent with its own prompt, context window, and toolset. The more advanced stance is:

> **Run one general-purpose agent (with a filesystem + code execution), and give it capabilities by loading specs on demand.**

This is exactly how the two major vendors frame it:

- **Anthropic** (Agent Skills, Oct 2025): *"Instead of building fragmented, custom-designed agents for each use case, anyone can now specialize their agents with composable capabilities by capturing and sharing their procedural knowledge."* A spec "transform[s] general-purpose agents into specialized agents that fit your needs."
- **OpenAI** (Skills in the API): specs are for *procedures* you want *reused across prompts/agents*, *versioned*, and *invoked only when needed* (not baked into every system prompt). They call it an **"org standard library"** pattern for keeping system prompts slim.

So "not creating agents" doesn't mean the system has no agentic loop — it means you stop hand-crafting a new agent per capability and instead **express each capability as a spec** that one orchestrating agent discovers and loads. The model, not you, decides which spec to invoke.

### Why this is genuinely "advanced," not just simpler

| Dimension | Multi-agent fleet | One agent + specs |
|---|---|---|
| **Context** | Each agent carries its own context; handoffs lose or duplicate it | One continuous context; specs load only the slice you need (progressive disclosure) |
| **Reliability** | Conflicting *implicit decisions* across agents compound (Cognition's core warning) | Single thread → no cross-agent decision conflicts |
| **Cost** | ~15× token multiplier vs single-agent on multi-tool tasks | No per-agent overhead; unused specs cost ~0 tokens |
| **Composability** | Agents are hard to share/version across teams | Specs are portable files; versionable; auditable |
| **Debugging** | Distributed tracing across agents | One trace |
| **Orchestration** | You hardcode agent handoffs | The model routes to specs at runtime |

The theoretical backbone is **context engineering** — "the #1 job of engineers building AI agents" (Cognition). Specs are a *mechanism* for context engineering: they let you keep the always-on context tiny while making arbitrarily large procedural knowledge available on demand.

---

## 2. What a spec actually is (architecture)

Source: Anthropic engineering post; survey §3 (arXiv:2602.12430).

A spec = a **directory** containing a required `SKILL.md` (YAML frontmatter + markdown body) plus optional `scripts/`, `references/`, `assets/`.

```
pdf-processing/
  SKILL.md          # frontmatter (name, description) + procedural body
  FORMS.md          # detailed form-filling guide
  REFERENCE.md      # API reference
  scripts/fill_form.py   # deterministic code Claude runs via bash
```

### Three-level progressive disclosure (the defining innovation)

| Level | When loaded | Token cost | Content |
|---|---|---|---|
| **1 — Metadata** | Always (startup) | ~100 tokens/spec | `name` + `description` from frontmatter |
| **2 — Instructions** | When triggered | < ~5k tokens | `SKILL.md` body (workflows, procedures) |
| **3 — Resources/code** | As needed | 0 until accessed | Bundled files; scripts run via bash (only *output* enters context) |

Because an agent with a filesystem + bash can read only what it needs, **the amount of knowledge you can bundle is effectively unbounded** — the rest sits on disk at zero context cost. This is what makes "many specs, one agent" cheaper than "many agents."

### Specs vs tools vs MCP (the distinction that matters)

- **A tool** executes and returns a result.
- **A spec** *prepares the agent* — it injects procedural knowledge, reshapes execution context, and enables progressive disclosure. A spec "modifies the agent's preparation, not its output directly."
- **MCP** (Model Context Protocol) standardizes how models reach external tools/resources; **specs complement MCP** by teaching the agent *complex multi-step workflows* that span external tools. Anthropic: specs "can complement MCP servers by teaching agents more complex workflows that involve external tools and software."
- Advanced tooling (Anthropic, Nov 2025) deepens this: a **Tool Search Tool** (cuts token overhead up to 85% on large tool registries), **Programmatic Tool Calling** (model runs tools via code instead of JSON; 79.5% → 88.1% accuracy on Opus 4.5), and **Tool Learning** (model studies tool docs to improve invocation).

---

## 3. The advanced techniques (where the research is)

The "more advanced techniques" layer is where the 2025→2026 literature concentrates. It clusters into six families.

### 3.1 Spec acquisition — the agent learns specs itself

- **SAGE — "Reinforcement Learning for Self-Improving Agent with Skill Library"** (arXiv:2512.17102). Prior skill libraries relied on LLM prompting, which is inconsistent. SAGE uses **RL** so the agent *learns, validates, and applies* new skills and improves its skill library across deployment, not just via prompting.
- **SEAgent — "Self-Evolving Computer Use Agent with Autonomous Learning from Experience"** (arXiv:2508.04700). A computer-use agent that **autonomously discovers and writes specs from its own interaction traces** with unfamiliar software, no human annotations.
- **Compositional spec synthesis** (survey axis ii): combining/deriving new specs from existing ones rather than authoring from scratch.

This is the most "advanced" reading of your idea: the agent doesn't just *use* specs you wrote — it **acquires and grows** them from experience.

### 3.2 Hierarchical spec bases

Flat, single-resolution spec files are a known limitation. Two representative designs:

- **SkillX — "Automatically Constructing Skill Knowledge Bases for Agents"** (arXiv:2604.04804). Distills raw trajectories into a **three-tier hierarchy** (strategic plans → functional skills → atomic skills), then iteratively refines and *explosively expands* the library. The resulting "SkillKB" is **plug-and-play** — it lifts weaker base agents on AppWorld, BFCL-v3, τ²-Bench.
- **SkillLens — "Adaptive Multi-Granularity Skill Reuse"** (arXiv:2605.08386). Organizes skills into a **four-layer graph** (policies → strategies → procedures → primitives) and retrieves at *mixed granularity*: reuse compatible sub-skills directly, adapt only the locally-mismatched parts. Theoretical result: mixed-granularity adaptation is **sublinear cost** under sparse mismatch. Results: ALFWorld success 45.0% → 51.3%; +6.31pp Acc@1 on bug localization.

### 3.3 Self-evolving / spec optimization (specs as "parameters")

- **SkillGrad — "Optimizing Agent Skills Like Gradient Descent"** (arXiv:2605.27760). Treats the skill package as a **structured parameter to optimize**: task runs give trajectory-level "loss," automatic diagnosis gives **text-based gradients**, a momentum agent accumulates recurring patterns, and a patcher applies layer-aware edits. +6.7pp over the strongest *training-based* baseline on SpreadsheetBench Verified.
- **TrackRL — "Trackable Skill Library Lifecycle Management for Agentic RL"** (arXiv:2610.25126). In GRPO groups, turns grouped sampling into **online randomized controlled experiments** to isolate each skill's *causal* contribution, then manages each skill's **lifecycle** (incubation → retention → retirement/removal) with a soft capacity bound.
- **SkillAxe — "Sharpening LLM-Authored Agent Skills Through Evaluation-Guided Self-Refinement"** (arXiv:2606.10546). Fully unsupervised; iteratively diagnoses and refines the model's *own* skills. (See the key caveat in §5.)
- **SE-GoS — "Self-Evolving Graph-of-Skills for Skill Library at Scale"** (arXiv:2609.08228). Treats the retrieval graph as an **index, not a learned representation**: updates topology, edge-weights, and node-descriptions from execution traces while the model and library stay fixed. One evolution round lifts SkillsBench reward 52.4% → 59.4% (above full-library loading) at ~⅓ the input tokens, and the improvement **transfers to a held-out split** (52.9% → 58.3%) — so it generalizes rather than memorizes.

### 3.4 Retrieval at scale (the bottleneck when you have many specs)

- **SE-GoS** (above) and **SkillTrace — "Traversing a Query-Skill Graph for Composable LLM Agents"** (arXiv:2608.02356): the challenge is not retrieving *individually* relevant skills but identifying a **complete, executable skill composition** — solved as a 3-level query-skill graph (compositional relations → query↔candidate similarity → dependencies among selected candidates).
- **SkillRAE — "Agent Skill-Based Context Compilation for Retrieval-Augmented Execution"** (arXiv:2605.10114): compiles the relevant skill context for artifact-rich tasks as libraries grow.

### 3.5 Specs as executable program functions (not just advice)

- **HASP — "Harnessing LLM Agents with Skill Programs"** (arXiv:2605.17734). Textual skills are "largely advisory." HASP upgrades skills into **executable Program Functions (PFs)** — guardrails that *activate on failure-prone states* and modify the next action or inject corrective context. Usable at inference time, during post-training, or for self-improvement. Inference-time PFs alone give **+25% over ReAct** on web-search reasoning; post-training + controlled evolution give +30.4% over Search-R1.

### 3.6 Context engineering as the enabling discipline

- **ACE — "Agentic Context Engineering"** (arXiv:2510.04618). Treats contexts as **evolving playbooks** (generate → reflect → curate) that accumulate and organize strategies without collapsing; +10.6% on agents, +8.6% on finance; matches the top AppWorld agent with a *smaller* open model.
- **ECS — "Evolutionary Context Search for Automated Skill Acquisition"** (arXiv:2602.16113). Searches *combinations* of context using accuracy on a small dev set (inference-only, no weight updates); finds non-obvious context pairings. +27% on BackendBench, +7% on τ-bench airline; **model-agnostic** (evolved with Gemini, transfers to Claude/DeepSeek).

---

## 4. Empirical evidence (the numbers)

| Technique | Paper (arXiv) | Headline result |
|---|---|---|
| Skills vs no-skill benchmark | SkillsBench (2602.12670) | 87 tasks / 8 domains / 18 model-harness conditions; standardizes measuring whether skills help |
| Human vs LLM-authored skills | SkillAxe (2606.10546) | **Human-authored skills: +16.2pp** pass rate; **LLM-authored: no measurable gain** (before their self-refinement) |
| Retrieval / self-evolving graph | SE-GoS (2609.08228) | 52.4% → 59.4% in one round; ~⅓ input tokens of full-library; transfers to held-out split |
| Hierarchical base (plug-and-play) | SkillX (2604.04804) | Lifts weaker base agents on AppWorld, BFCL-v3, τ²-Bench |
| Multi-granularity reuse | SkillLens (2605.08386) | ALFWorld 45.0% → 51.3%; +6.31pp bug-localization Acc@1 |
| Gradient-style skill optimization | SkillGrad (2605.27760) | +6.7pp over best training-based baseline (SpreadsheetBench Verified) |
| Executable skill programs | HASP (2605.17734) | +25% over ReAct (inference-time PFs); +30.4% over Search-R1 (post-training) |
| Evolving context | ACE (2510.04618) | +10.6% agents / +8.6% finance; matches top AppWorld agent w/ smaller model |
| Evolutionary context search | ECS (2602.16113) | +27% BackendBench; +7% τ-bench airline; model-agnostic transfer |
| Library scaling (negative result) | More Skills, Worse Agents? (2605.24050) | **Up to −21%** pass rate scaling to a 202-skill library; driven by *skill shadowing*, not context overhead |
| RL with skill library | SAGE (2512.17102) | RL-based self-improving skill library vs prompting-only |
| Autonomous skill discovery | SEAgent (2508.04700) | CUA autonomously learns skills from experience on unfamiliar software |
| Lifelong discovery/evolution | SkillFlow (2604.17308) | 166 tasks / 20 families; tests discover-from-experience + repair + coherent-library maintenance |

---

## 5. Caveats and failure modes (read before committing)

1. **Spec shadowing — more specs can be worse.** (arXiv:2605.24050) As libraries grow, the agent **selects the wrong spec more often** (spec shadowing), causing up to a 21% pass-rate drop at 202 specs. The enlarged context itself is *not* the bottleneck — **selection failure is.** Implication: "only use specs" requires a **good retrieval/routing layer**, not just a big folder.
2. **LLM-authored specs don't reliably help yet.** (SkillAxe, 2606.10546) On SkillsBench, human-authored specs add +16.2pp but LLM-authored specs add ~0 — until you add evaluation-guided self-refinement. So *who* writes the specs matters; automated authoring is an open problem.
3. **Security is a first-class problem.** The survey reports **26.1% of 42,447 community specs contain vulnerabilities**; confirmed malicious actors operate at scale; and specs enable "trivially simple" prompt injection (Schmotz et al.) — once loaded, a spec's instructions are treated as authoritative, and a benign "don't ask again" approval can carry over to harmful actions. Related: **SkillHarm** (2606.02540, lifecycle-aware spec attacks), **Stealth Apart, Harm Together** (2609.30383, spec *cascading* attacks), **Behavioral Integrity Verification** (2605.11770), **SkillTester** (2603.28815).
4. **Specs decay.** **SpecOps** (2605.13716) and **"Skill Drift Is Contract Violation"** (2605.10990): specs silently break as the APIs/packages/services they reference change — you need proactive maintenance, not just install-time vetting.
5. **"No agents" is a default, not an absolute.** Cognition's principles (share full context; actions carry implicit decisions) argue against *parallel write-centric* sub-agents. But **read-centric** work (research, parallel analysis) still parallelizes cleanly — Anthropic's own Research feature uses an orchestrator-worker for *reading* and a single agent for the final *synthesis/write*. So: default to one agent + specs; add sub-agents only for embarrassingly-parallel, read-only, context-isolable sub-tasks.

---

## 6. How to build it (practical guidance)

From Anthropic's engineering post + OpenAI's docs + the survey:

1. **Start with evaluation, not specs.** Run your agent on representative tasks, find where it fails, then build specs incrementally to close those gaps.
2. **Keep `SKILL.md` lean; split the rest.** Put the table of contents in the frontmatter `description` (this is what the model matches against to decide whether to trigger). Move detailed/rarely-co-located context into separate `references/` files. Keep mutually-exclusive context on separate paths.
3. **Use code for the deterministic parts.** Bundle scripts the agent runs via bash — only the *output* enters context. Sorting, parsing, validation: do it in code, not token generation.
4. **Write the `description` to say both *what* and *when*.** The model triggers on this. Watch real trajectories; iterate on name/description.
5. **Iterate with the agent.** Ask it to capture its successful approaches and mistakes into the spec; have it self-reflect on what went wrong. This reveals what context it actually needs.
6. **Treat the spec library like an internal standard library:** versioned, audited, shared across agents/teams; pin versions in production.
7. **Add a retrieval/routing layer as the library grows** (see §3.4) — this is where "only specs" starts to fail without care.
8. **Govern provenance.** Apply least-privilege by trust tier (survey's T1–T4 model): unvetted community spec → instructions-only + full tool isolation; vendor-certified → full capabilities. Audit bundled code/dependencies and any instructions to reach untrusted network sources before installing.

### Decision rule (single agent + specs vs multi-agent)

- **One goal, bounded scope, fits one context window, one trust domain →** single agent + specs. (Default.)
- **Read-centric, parallelizable, independent sub-tasks →** sub-agents OK (isolate context, return summaries).
- **Crosses security/compliance boundaries, multiple teams, or exceeds one context window →** multi-agent (Microsoft's Cloud Adoption Framework decision tree).

---

## 7. Open challenges (from the survey, arXiv:2602.12430)

Seven, including: cross-platform spec portability; capability-based permission models; reliable spec selection at scale (shadowing); trustworthy automated spec authoring; lifecycle/drift management; security governance; and self-improving spec ecosystems where agents create/edit/evaluate their own specs.

---

## 8. Timeline (how fast this moved)

- **2023** — Voyager: spec library for an embodied LLM agent (the original "agent writes its own specs" idea).
- **Aug 2025** — SEAgent (autonomous CUA spec learning); "Don't Build Multi-Agents" (Cognition).
- **Oct 2025** — **Anthropic launches Agent Skills** across Claude products.
- **Nov 2025** — Advanced Tool Use (Tool Search, Programmatic Tool Calling, Tool Learning).
- **Dec 2025** — Agent Skills released as an **open standard** (agentskills.io).
- **Dec 2025–Feb 2026** — SAGE (RL spec library); the field-organizing **survey** (2602.12430); **SkillsBench** (2602.12670).
- **2026 (spring–fall)** — explosion: SkillX, SkillLens, SkillGrad, TrackRL, SE-GoS, SkillAxe, SkillTrace, SkillRAE, SkillFlow, SpecOps, SkillDrift, SkillMe, plus a security cluster (SkillHarm, cascading attacks, integrity verification).

---

## 9. Primary sources

**arXiv (read these first)**
- **2602.12430** — Agent Skills for LLMs: Architecture, Acquisition, Security, and the Path Forward *(the survey; start here)* — https://arxiv.org/abs/2602.12430
- **2602.12670** — SkillsBench — https://arxiv.org/abs/2602.12670
- **2605.24050** — More Skills, Worse Agents? (skill shadowing) — https://arxiv.org/abs/2605.24050
- **2604.04804** — SkillX — https://arxiv.org/abs/2604.04804
- **2605.08386** — SkillLens — https://arxiv.org/abs/2605.08386
- **2605.27760** — SkillGrad — https://arxiv.org/abs/2605.27760
- **2610.25126** — TrackRL — https://arxiv.org/abs/2610.25126
- **2609.08228** — SE-GoS — https://arxiv.org/abs/2609.08228
- **2608.02356** — SkillTrace — https://arxiv.org/abs/2608.02356
- **2606.10546** — SkillAxe — https://arxiv.org/abs/2606.10546
- **2605.17734** — HASP (skill programs) — https://arxiv.org/abs/2605.17734
- **2510.04618** — ACE (Agentic Context Engineering) — https://arxiv.org/abs/2510.04618
- **2602.16113** — ECS (Evolutionary Context Search) — https://arxiv.org/abs/2602.16113
- **2512.17102** — SAGE (RL + skill library) — https://arxiv.org/abs/2512.17102
- **2508.04700** — SEAgent (autonomous skill discovery) — https://arxiv.org/abs/2508.04700
- **2604.17308** — SkillFlow (lifelong discovery/evolution benchmark) — https://arxiv.org/abs/2604.17308
- **2605.13716 / 2605.10990** — SpecOps / Skill Drift (maintenance)
- **2606.02540 / 2609.30383 / 2605.11770 / 2603.28815** — security cluster (SkillHarm, cascading attacks, integrity verification, SkillTester)
- **2605.10114** — SkillRAE; **2605.27955** — Skill-as-Pseudocode; **2608.10906** — GitSkills (dataset); **2606.16523** — SkillWiki

**Industry (grounding)**
- Anthropic — *Equipping agents for the real world with Agent Skills* — anthropic.com/engineering/equipping-agents-for-the-real-world-with-agent-skills
- Anthropic docs — Agent Skills overview (progressive disclosure, API usage) — console.anthropic.com/docs
- OpenAI — *Skills in the API* / *Build skills* — developers.openai.com
- Cognition — *Don't Build Multi-Agents* (context-engineering principles) — cognition.ai/blog/dont-build-multi-agents
- Open standard — agentskills.io
- Survey resource repo — github.com/scienceaix/agentskills

*Note: Semantic Scholar citation counts were not retrievable (rate-limited, HTTP 429); most of these are 2026 papers, so citation counts are low by nature. Impact above is judged from abstracts, venues, and cross-referencing, not citation counts.*
