# Embabel Smith

A **skills-first Embabel agent builder** — a general-purpose builder agent that emits deployable,
no-code *skill-packs*, plus the fixed no-code runtime that ingests them. Greenfield POC
multi-module project on **Embabel 1.5.3 / Java 21**.

The core idea: a builder *agent* turns a natural-language description into a **skill-pack** (a
pure-data YAML directory), validates it before writing, and emits it. A separate **no-code
runtime** ingests that skill-pack with zero compilation and runs a plan over its exported goal.
See [`docs/skill-pack-format.md`](docs/skill-pack-format.md) for the format and
[`docs/03-embabel-skills-agent-builder-design.md`](docs/03-embabel-skills-agent-builder-design.md)
for the design.

## Modules

| Module | Purpose |
| --- | --- |
| `smith-common` | The shared skill-pack format: data model, YAML I/O, validation, sample packs. Framework-free, fully offline-testable. |
| `poc-1-no-code-runtime` | **POC 1 (keystone).** Fixed launcher that ingests a skill-pack via `YmlStepSpecRepository` + `StepSpecAgentScopeBuilder.deploy()` and runs a GOAP plan over the exported goal. |
| `poc-2-nl-compile` | **POC 2.** Natural-language → draft via `NaturalLanguageCompiler.compileAgent`; reports the draft's goal-shape quality (the `DraftQuality` classifier — doc 02 caveat #2). |
| `poc-3-builder-agent` | **POC 3.** The builder as a code-backed `@Agent` (GOAP): `compileFromDescription → validateDraft → writeSkillPack`; logs the draft quality before and after `normalizeGoalOutput`. |
| `poc-4-skill-format` | **POC 4.** Exported-goal-as-skill surface + on-demand load of a single goal's action chain. |
| `poc-5-eval` | **POC 5.** Adapts `embabel-agent-eval` (conversational-oriented) to a goal-type skill via `AgentInvocation`; composite score + (stretch) prompt rewrite. |
| `poc-6-skill-composition` | **POC 6.** Skill composition: loads two independent skill-packs into one platform and runs the final goal, so the GOAP planner chains an action from each pack through their shared intermediate type. |
| `poc-7-runtime-adapter` | **POC 7.** A *second* runtime adapter: a skill pack invokes a CLI via a `shell` tool group, proving skills are not locked to a single execution substrate (doc 03 §5.2). |
| `poc-8-retrieval` | **POC 8.** Retrieval at scale: a lexical skill retriever over 14 same-typed skills, quantifying the "skill shadowing" risk and its mitigation (doc 03 §5.1). |
| `poc-9-quality-refinement` | **POC 9.** LLM-authored skill quality + eval-guided refinement: a weak prompt is scored by the eval, rewritten from the failing tasks, and re-scored (the SkillAxe loop, doc 03 §5). |
| `poc-10-trust-tiers` | **POC 10.** Trust tiers + guardrails: per-tier tool-group allow-lists (a community skill can't reach `shell`) and a globally-registered user-input guardrail — a normalized multi-pattern matcher — that blocks prompt injection, including obfuscated variants (doc 03 §5.2). |
| `poc-11-progressive-disclosure` | **POC 11.** Progressive-disclosure stress test: the plan context stays constant while a reference file grows 100×, proving the context-cost model of doc 03 §4.2. |
| `poc-12-semantic-retrieval` | **POC 12.** Semantic retrieval at scale: extends POC 8's pre-filter to a 200+ same-typed library, comparing the lexical baseline against a dense feature-hashing retriever and measuring how near-duplicate (shadowing) skills collapse selection (doc 02 §3.4, doc 03 §2.1). |
| `poc-13-remote-mcp` | **POC 13.** Remote / MCP invocation: an exported goal (`@Action` + `@AchievesGoal` + `@Export(remote = true)`) is auto-published as an MCP tool over Streamable HTTP and invoked by a separate MCP client over the network — the remote half of the "normal agent harness" (doc 03 §2.3). |
| `poc-14-composer` | **POC 14.** The skill Composer (headless model of the visual skill-chain editor): skills on a canvas with type-checked connections, the composed chain, and export as a single composite skill-pack that runs end-to-end (doc 03 §2.2). |
| `poc-15-composer-ui` | **POC 15.** The visual skill-chain editor (the builder UI): a Spring Boot web app serving a browser drag-and-drop canvas that loads the skill palette, snaps only type-compatible connections, live-validates the chain, exports the composite skill-pack over REST, and carries an **author pane** (compile a natural-language description into a draft + show its `DraftQuality` verdict, endpoint-gated), a **retrieval pane** (rank the palette by relevance to a query — the POC 8/12/20 `SkillScorer` seam, lexical by default, the POC 20 embedding a drop-in), and an **eval/refinement pane** (run a POC 5 eval over tasks and, on failure, offer a POC 9 prompt rewrite + re-score, endpoint-gated) — the pixels on top of POC 14's model (doc 01 §VERDICT, doc 03 §2.2). |
| `poc-16-remote-action` | **POC 16.** The remote-action bridge: a separate sidecar process exposes an action over HTTP; the agent pulls it in as a `RestAction` and a GOAP plan plans over it and executes it over the network, exactly like a local action — the third of doc 03 §2.3's runtime bridges (shell → POC 7, MCP → POC 13, remote action → POC 16). Fully deterministic (no LLM call). |
| `poc-17-gliner-retrieval` | **POC 17.** GLiNER2 transformer skill classification: a real zero-shot transformer (GLiNER2 via GLiNER4j over ONNX Runtime) classifies the task query against a small candidate set and selects the top hit, run on POC 12's shadowing scenario to test whether a model that *understands* meaning breaks the tie lexical and dense text-similarity cannot. A **local-model-gated** benchmark (one-time ONNX bundle download; offline tests mock the classifier). |
| `poc-18-skill-lifecycle` | **POC 18.** Skill lifecycle / drift detection: a skill's contract is its eval pass-rate, and a SkillOps-style proactive re-eval compares the skill's content hash and pass-rate against the recorded baseline, moving it through `incubating → active → degraded → retired` — catching both a silent prompt edit (content drift) and a full score collapse (doc 03 §1 "Skill decay / lifecycle", doc 02 §5 caveat 4). Content-hash / drift / state-machine halves are pure and offline-tested; the live re-eval is endpoint-gated. |
| `poc-19-small-llm-selector` | **POC 19.** Small-LLM skill selection: a 0.6B model fine-tuned for single-label decisions (`iromu/Qwen3-0.6B-openjev`, Q8_0 GGUF) as a skill selector on POC 17's shadowing scenario — under its in-distribution decision prompt and an out-of-distribution free-form prompt — alongside the GLiNER2, lexical, and dense baselines. The model is served in a **Docker container (llama.cpp) whose lifecycle Spring Boot Dev Services manages** from the module's `compose.yml`. A **docker-gated** benchmark (one-time GGUF download; offline tests stub the endpoint). |
| `poc-20-semantic-embedding` | **POC 20.** The real semantic `EmbeddingService` doc 03 §2.1 calls for: a genuine dense sentence-encoder (`all-MiniLM-L6-v2` BERT via ONNX Runtime + the HuggingFace tokenizer, mean-pooled + L2-normalised) behind POC 12's `SkillScorer` seam, on POC 12's 200+ same-typed shadowing scenario. It retrieves cleanly at the distinct scale (100% at 191) and, under shadowing, beats the lexical (17%) and dense feature-hashing (0%) stand-ins (33%) — but does not fully cure the near-duplicate collapse, confirming curation (POC 18) is the real fix. A **local-model-gated** benchmark (one-time ONNX download; offline tests use a stub embedder). |

Sample skill-pack: [`skill-packs/explainer`](skill-packs/explainer)
(`UserInput → extractTopic → TopicBrief → writeExplainer → BackgroundMessage`). Composition samples:
[`skill-packs/compose-extract`](skill-packs/compose-extract) +
[`skill-packs/compose-explain`](skill-packs/compose-explain) (share `TopicBrief`).
More samples: [`skill-packs/shell-runner`](skill-packs/shell-runner)
(POC 7, invokes a CLI) and [`skill-packs/reference-heavy`](skill-packs/reference-heavy)
(POC 11, carries a large `references/` file).

## Build & test (offline)

```bash
mvn -q verify
```

Every module's offline tests are green without a model endpoint. Tests that exercise the model are
**endpoint-gated** and are not run offline.

To run a POC launcher, install the modules once (so `smith-common` and sibling modules resolve from
the local repo), then run a single module by `-pl`:

```bash
mvn install -DskipTests          # one-time (re-run after changes)
```

## Model endpoint (env-driven, design D4)

No endpoint is hardcoded. Point any POC at an OpenAI-compatible endpoint (LiteLLM / OpenAI /
Ollama) via environment variables:

```bash
export OPENAI_CUSTOM_BASE_URL=http://localhost:4000
export OPENAI_CUSTOM_API_KEY=sk-...
export OPENAI_CUSTOM_MODELS=my-model
export EMBABEL_MODELS_DEFAULT_LLM=my-model
```

Starting an endpoint-gated POC without these yields a **clear configuration error**, not an opaque
model error.

## Per-POC run commands

Each command runs the POC's launcher for a single module (deps resolve from the local repo after
`mvn install`). The endpoint-gated ones need the env vars above.

Per-run inputs are **OS environment variables**, not `-D` Maven properties: `spring-boot:run`
forks the app with the module dir as its working directory and does **not** forward `-D` system
properties into it, so `-Dbuilder.*` would silently fall back to the defaults. Env vars *are*
inherited by the forked JVM, and Spring's relaxed binding maps `builder.X` → `BUILDER_X`.
Use an **absolute** `BUILDER_PACK_DIR` (the relative default `skill-packs/explainer` does not
resolve from the module's working directory).

```bash
# POC 1 — no-code runtime (endpoint-gated): run the explainer skill over an input.
BUILDER_PACK_DIR="$PWD/skill-packs/explainer" \
BUILDER_INPUT="Explain how a coffee machine works" BUILDER_PLANNER=GOAP \
    mvn -pl poc/poc-1-no-code-runtime spring-boot:run

# POC 2 — NL-compile (endpoint-gated): compile a description into a draft.
BUILDER_DESCRIPTION="A skill that explains a topic in plain language and posts it as a background message" \
    mvn -pl poc/poc-2-nl-compile spring-boot:run

# POC 3 — builder agent (endpoint-gated): build a validated skill-pack from a description.
BUILDER_DESCRIPTION="A skill that explains a topic in plain language and posts it as a background message" \
BUILDER_OUTPUT_DIR="$PWD/built-skills" \
    mvn -pl poc/poc-3-builder-agent spring-boot:run

# POC 4 — skill format (offline surface + endpoint-gated run): resolve + run the exported goal on demand.
BUILDER_PACK_DIR="$PWD/skill-packs/explainer" BUILDER_INPUT="Explain how a coffee machine works" \
    mvn -pl poc/poc-4-skill-format spring-boot:run

# POC 5 — eval (endpoint-gated): score the explainer skill on a set of tasks. By default runs the
#   eval 5× and reports eval-score stability (set BUILDER_EVAL_RUNS=1 for just the composite).
BUILDER_PACK_DIR="$PWD/skill-packs/explainer" \
    mvn -pl poc/poc-5-eval spring-boot:run

# POC 6 — skill composition (endpoint-gated): chain two independent skills via GOAP.
BUILDER_PACK_DIRS="$PWD/skill-packs/compose-extract,$PWD/skill-packs/compose-explain" \
BUILDER_TARGET_GOAL=explainerDone BUILDER_INPUT="Explain how a coffee machine works" \
    mvn -pl poc/poc-6-skill-composition spring-boot:run

# POC 7 — runtime adapter (endpoint-gated): a skill invokes a CLI via the `shell` tool group.
BUILDER_PACK_DIR="$PWD/skill-packs/shell-runner" \
BUILDER_INPUT="Run the command: uname -s, and report the result" \
    mvn -pl poc/poc-7-runtime-adapter spring-boot:run

# POC 8 — retrieval (offline, no endpoint): benchmark the skill retriever over same-typed skills.
mvn -pl poc/poc-8-retrieval exec:java

# POC 9 — quality refinement (endpoint-gated): score a weak skill, rewrite its prompt, re-score.
mvn -pl poc/poc-9-quality-refinement spring-boot:run

# POC 10 — trust tiers / guardrails (endpoint-gated for the guardrail demo):
#   offline tier checks always run; the guardrail run needs the endpoint env vars.
mvn -pl poc/poc-10-trust-tiers spring-boot:run

# POC 11 — progressive disclosure (offline, no endpoint): show plan context stays constant as a
#   reference file grows.
mvn -pl poc/poc-11-progressive-disclosure exec:java

# POC 12 — semantic retrieval at scale (offline, no endpoint): lexical vs dense over a 200+ skill
#   library, measuring the near-duplicate shadowing curve.
mvn -pl poc/poc-12-semantic-retrieval exec:java

# POC 14 — skill Composer (offline, no endpoint): chains compose-extract + compose-explain on a
#   type-checked canvas and exports a single composite skill-pack (target/composed/compose-composite).
mvn -pl poc/poc-14-composer exec:java

#   ... then run that exported composite through the no-code runtime (endpoint-gated):
BUILDER_PACK_DIR="$PWD/target/composed/compose-composite" \
BUILDER_INPUT="Explain how a coffee machine works" \
    mvn -pl poc/poc-1-no-code-runtime spring-boot:run

# POC 13 — remote / MCP invocation (endpoint-gated): boots the MCP server and invokes the
#   published goal over Streamable HTTP from a separate client. Pick a free port (8080 may be
#   taken by another local service); the client points at the same port.
SERVER_PORT=18080 BUILDER_MCP_URL="http://localhost:18080" \
BUILDER_TOPIC="how a coffee machine works" \
    mvn -pl poc/poc-13-remote-mcp spring-boot:run

# POC 15 — the visual skill-chain editor (offline, no endpoint): a web app. Boot it, then open the
#   canvas in a browser — drag skills onto the canvas, connect ports, and Export the composite.
SERVER_PORT=18080 mvn -pl poc/poc-15-composer-ui spring-boot:run
#   → open http://localhost:18080/

# POC 16 — the remote-action bridge. Boots the platform, starts a deterministic sidecar
#   (default port 18090), and runs a GOAP plan that executes the sidecar's action over HTTP.
#   The plan is fully deterministic (no LLM call); the endpoint is only needed for platform boot.
mvn -pl poc/poc-16-remote-action spring-boot:run -Dbuilder.input="Explain how a remote worker pool runs a task"

# POC 17 — GLiNER2 transformer skill classification (local-model-gated, no endpoint): the
#   three-method shadowing benchmark. Needs the one-time ONNX bundle download first (see
#   poc/poc-17-gliner-retrieval/README.md); without it the launcher prints a clear missing-model
#   error and exits non-zero.
mvn -pl poc/poc-17-gliner-retrieval exec:java

# POC 18 — skill lifecycle / drift detection (endpoint-gated): a SkillOps-style proactive
#   re-eval. Runs the deterministic lifecycle walkthrough, then (with a live endpoint) re-evaluates
#   the healthy vs. regressed skill and moves it through incubating → active → degraded → retired.
mvn -pl poc/poc-18-skill-lifecycle spring-boot:run

# POC 19 — small-LLM skill selection (docker-gated): the 0.6B decision model is served in a
#   Docker container (llama.cpp) that Spring Boot Dev Services starts/stops with the app from
#   the module's compose.yml (port 18081). Needs the one-time GGUF download first (see
#   poc/poc-19-small-llm-selector/README.md); without it (or without Docker) the launcher prints
#   a clear error and exits non-zero.
mvn -pl poc/poc-19-small-llm-selector spring-boot:run

# POC 20 — real semantic EmbeddingService (local-model-gated, no endpoint): a real ONNX sentence
#   encoder (all-MiniLM-L6-v2) behind POC 12's SkillScorer seam, on POC 12's 200+ shadowing scenario.
#   Needs the one-time ONNX download first (see poc/poc-20-semantic-embedding/README.md); without
#   it the launcher prints a clear missing-model error and exits non-zero.
mvn -pl poc/poc-20-semantic-embedding exec:java
```

## Full loop (task 7.1)

`run-loop.sh` chains the two endpoint-gated steps into one command: it builds a skill with the
builder, captures the written pack path, and runs that pack in the no-code runtime.

```bash
# One command — description → built skill-pack → goal output.
./run-loop.sh "A skill that explains a topic in plain language and posts it as a background message" \
              "Explain how a coffee machine works"

# Override the planner / output dir:
PLANNER=UTILITY BUILDER_OUTPUT_DIR=./built-skills ./run-loop.sh
```

It fails fast with a clear message if the endpoint env vars are unset, and surfaces the builder's
output if the built draft fails validation (design D5 — an invalid skill is never written).

## Endpoint-gated work

The offline build verifies all non-LLM logic (format round-trip, validation, the builder's
validate/write steps, the eval scoring/composite, the skill-format surface, the retrieval
benchmark, the progressive-disclosure measurement, the trust-tier allowlist, and the
prompt-injection guardrail). The end-to-end runs that call the model — the builder's
`compileFromDescription`, the runtime's goal run, the NL-compile, the eval invocation, the
composition run, the CLI-skill run (POC 7), the eval-guided refinement loop (POC 9), the
guardrail demo's clean run (POC 10), and the remote/MCP goal run (POC 13) — are scaffolded with
working code but require a live endpoint to execute. POC 8, POC 11, POC 12, POC 14 (the
Composer's canvas + composite export), and POC 15 (the visual builder UI — a web app that only
manipulates the same offline composer model) are fully offline and need no endpoint; running
POC 14's *exported* composite through the runtime is the one endpoint-gated step. POC 16 (the
remote-action bridge) needs the endpoint only to boot the platform — its GOAP plan over the remote
action is fully deterministic and never calls the model. POC 17 (GLiNER2 skill classification)
introduces a **third category — local-model-gated**: it needs no endpoint, but runs the benchmark
only when the one-time ONNX bundle is present (offline tests mock the classifier, so the build stays
green without it). POC 18 (skill lifecycle / drift detection) is endpoint-gated for the live
re-eval, but its content-hash / drift / lifecycle state machine is pure and pinned by offline tests,
so `mvn test` stays green without a model.
