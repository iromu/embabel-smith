# Embabel Smith

A **GOAP-based Embabel agent builder** — a general-purpose builder agent that emits deployable,
no-code *agent specs* (declarative YAML step definitions), plus the fixed no-code runtime that
ingests them. Greenfield POC multi-module project on **Embabel 1.5.4 / Java 21**.

The core idea: a builder *agent* turns a natural-language description into an **agent spec** (a
pure-data YAML directory of Embabel step definitions — goals and actions), validates it before
writing, and emits it. A separate **no-code runtime** ingests that agent spec with zero compilation
and runs a GOAP plan over its exported goal. See
[`docs/agent-spec-format.md`](docs/agent-spec-format.md) for the format and
[`docs/03-embabel-spec-agent-builder-design.md`](docs/03-embabel-spec-agent-builder-design.md)
for the design.

## Modules

| Module | Purpose |
| --- | --- |
| `smith-common` | The shared agent-spec format: data model, YAML I/O, validation, sample specs. Framework-free, fully offline-testable. |
| `poc-1-no-code-runtime` | **POC 1 (keystone).** Fixed launcher that ingests an agent spec via `YmlStepSpecRepository` + `StepSpecAgentScopeBuilder.deploy()` and runs a GOAP plan over the exported goal. |
| `poc-2-nl-compile` | **POC 2.** Natural-language → draft via `NaturalLanguageCompiler.compileAgent`; reports the draft's goal-shape quality (the `DraftQuality` classifier — doc 02 caveat #2). |
| `poc-3-builder-agent` | **POC 3.** The builder as a code-backed `@Agent` (GOAP): `compileFromDescription → validateDraft → writeAgentSpec`; logs the draft quality before and after `normalizeGoalOutput`. |
| `poc-4-agent-spec-format` | **POC 4.** Exported-goal-as-spec surface + on-demand load of a single goal's action chain. |
| `poc-5-eval` | **POC 5.** Adapts `embabel-agent-eval` (conversational-oriented) to a goal-type spec via `AgentInvocation`; composite score + (stretch) prompt rewrite. |
| `poc-6-agent-spec-composition` | **POC 6.** Spec composition: loads two independent agent specs into one platform and runs the final goal, so the GOAP planner chains an action from each spec through their shared intermediate type. |
| `poc-7-runtime-adapter` | **POC 7.** A *second* runtime adapter: an agent spec invokes a CLI via a `shell` tool group, proving specs are not locked to a single execution substrate (doc 03 §5.2). |
| `poc-8-retrieval` | **POC 8.** Retrieval at scale: a lexical spec retriever over 14 same-typed specs, quantifying the "spec shadowing" risk and its mitigation (doc 03 §5.1). |
| `poc-9-quality-refinement` | **POC 9.** LLM-authored spec quality + eval-guided refinement: a weak prompt is scored by the eval, rewritten from the failing tasks, and re-scored (the SkillAxe loop, doc 03 §5). |
| `poc-10-trust-tiers` | **POC 10.** Trust tiers + guardrails: per-tier tool-group allow-lists (a community spec can't reach `shell`) and a globally-registered user-input guardrail — a normalized multi-pattern matcher — that blocks prompt injection, including obfuscated variants (doc 03 §5.2). |
| `poc-11-progressive-disclosure` | **POC 11.** Progressive-disclosure stress test: the plan context stays constant while a reference file grows 100×, proving the context-cost model of doc 03 §4.2. |
| `poc-12-semantic-retrieval` | **POC 12.** Semantic retrieval at scale: extends POC 8's pre-filter to a 200+ same-typed library, comparing the lexical baseline against a dense feature-hashing retriever and measuring how near-duplicate (shadowing) specs collapse selection (doc 02 §3.4, doc 03 §2.1). |
| `poc-13-remote-mcp` | **POC 13.** Remote / MCP invocation: an exported goal (`@Action` + `@AchievesGoal` + `@Export(remote = true)`) is auto-published as an MCP tool over Streamable HTTP and invoked by a separate MCP client over the network — the remote half of the "normal agent harness" (doc 03 §2.3). |
| `poc-14-composer` | **POC 14.** The spec Composer (headless model of the visual spec-chain editor): specs on a canvas with type-checked connections, the composed chain, and export as a single composite agent spec that runs end-to-end (doc 03 §2.2). |
| `poc-15-composer-ui` | **POC 15.** The visual spec-chain editor (the builder UI): a Spring Boot web app serving a browser drag-and-drop canvas that loads the spec palette, snaps only type-compatible connections, live-validates the chain, exports the composite agent spec over REST, and carries an **author pane** (compile a natural-language description into a draft + show its `DraftQuality` verdict, endpoint-gated), a curation-aware **retrieval pane** (rank the palette by relevance to a query — the POC 8/12/20 `SpecScorer` seam, lexical by default, the POC 20 embedding a drop-in — now applying POC 18's lifecycle: retired specs dropped, degraded ones de-ranked), an **eval/refinement pane** (run a POC 5 eval over tasks and, on failure, offer a POC 9 prompt rewrite + re-score, endpoint-gated), and a **curate re-eval** (re-score a spec's live pass-rate and move it through the POC 18 lifecycle, endpoint-gated) — the pixels on top of POC 14's model (doc 01 §VERDICT, doc 03 §2.2). |
| `poc-16-remote-action` | **POC 16.** The remote-action bridge: a separate sidecar process exposes an action over HTTP; the agent pulls it in as a `RestAction` and a GOAP plan plans over it and executes it over the network, exactly like a local action — the third of doc 03 §2.3's runtime bridges (shell → POC 7, MCP → POC 13, remote action → POC 16). Fully deterministic (no LLM call). |
| `poc-17-gliner-retrieval` | **POC 17.** GLiNER2 transformer spec classification: a real zero-shot transformer (GLiNER2 via GLiNER4j over ONNX Runtime) classifies the task query against a small candidate set and selects the top hit, run on POC 12's shadowing scenario to test whether a model that *understands* meaning breaks the tie lexical and dense text-similarity cannot. A **local-model-gated** benchmark (one-time ONNX bundle download; offline tests mock the classifier). |
| `poc-18-agent-spec-lifecycle` | **POC 18.** Spec lifecycle / drift detection: a spec's contract is its eval pass-rate, and a SpecOps-style proactive re-eval compares the spec's content hash and pass-rate against the recorded baseline, moving it through `incubating → active → degraded → retired` — catching both a silent prompt edit (content drift) and a full score collapse (doc 03 §1 "Spec decay / lifecycle", doc 02 §5 caveat 4). Content-hash / drift / state-machine halves are pure and offline-tested; the live re-eval is endpoint-gated. |
| `poc-19-small-llm-selector` | **POC 19.** Small-LLM spec selection: a 0.6B model fine-tuned for single-label decisions (`iromu/Qwen3-0.6B-openjev`, Q8_0 GGUF) as a spec selector on POC 17's shadowing scenario — under its in-distribution decision prompt and an out-of-distribution free-form prompt — alongside the GLiNER2, lexical, and dense baselines. The model is served in a **Docker container (llama.cpp) whose lifecycle Spring Boot Dev Services manages** from the module's `compose.yml`. A **docker-gated** benchmark (one-time GGUF download; offline tests stub the endpoint). |
| `poc-20-semantic-embedding` | **POC 20.** The real semantic `EmbeddingService` doc 03 §2.1 calls for: a genuine dense sentence-encoder (`all-MiniLM-L6-v2` BERT via ONNX Runtime + the HuggingFace tokenizer, mean-pooled + L2-normalised) behind POC 12's `SpecScorer` seam, on POC 12's 200+ same-typed shadowing scenario. It retrieves cleanly at the distinct scale (100% at 191) and, under shadowing, beats the lexical (17%) and dense feature-hashing (0%) stand-ins (33%) — but does not fully cure the near-duplicate collapse, confirming curation (POC 18) is the real fix. A **local-model-gated** benchmark (one-time ONNX download; offline tests use a stub embedder). |
| `poc-23-remote-embedding-scale` | **POC 23.** Spec selection at scale with a **remote** `EmbeddingService`: the real `nomic-embed-text-v2` OpenAI-compatible `POST /embeddings` endpoint (a llama.cpp server, proxied by LiteLLM) behind POC 12's `SpecScorer` seam. On POC 12/20's identical 200+ same-typed shadowing library it retrieves cleanly at the distinct scale (100% at 191) and, under shadowing, ties lexical (17%) and beats dense (0%) — an honest data point that the specific embedding model matters. On a generated library to **1000** specs, Precision@1 holds at 100% while steady-state per-query retrieval (cosine over cached vectors, no model call) stays 0.48 ms → 1.27 ms; the one-time embedding index is paid once and amortised, vs *n* model calls per query for a per-candidate selector. An **endpoint-gated** benchmark (offline tests use a stub transport / a local `HttpServer`). |

Sample agent spec: [`agent-specs/explainer`](agent-specs/explainer)
(`UserInput → extractTopic → TopicBrief → writeExplainer → BackgroundMessage`). Composition samples:
[`agent-specs/compose-extract`](agent-specs/compose-extract) +
[`agent-specs/compose-explain`](agent-specs/compose-explain) (share `TopicBrief`).
More samples: [`agent-specs/shell-runner`](agent-specs/shell-runner)
(POC 7, invokes a CLI) and [`agent-specs/reference-heavy`](agent-specs/reference-heavy)
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
Use an **absolute** `BUILDER_PACK_DIR` (the relative default `agent-specs/explainer` does not
resolve from the module's working directory).

```bash
# POC 1 — no-code runtime (endpoint-gated): run the explainer spec over an input.
BUILDER_PACK_DIR="$PWD/agent-specs/explainer" \
BUILDER_INPUT="Explain how a coffee machine works" BUILDER_PLANNER=GOAP \
    mvn -pl poc/poc-1-no-code-runtime spring-boot:run

# POC 2 — NL-compile (endpoint-gated): compile a description into a draft.
BUILDER_DESCRIPTION="An agent spec that explains a topic in plain language and posts it as a background message" \
    mvn -pl poc/poc-2-nl-compile spring-boot:run

# POC 3 — builder agent (endpoint-gated): build a validated agent spec from a description.
BUILDER_DESCRIPTION="An agent spec that explains a topic in plain language and posts it as a background message" \
BUILDER_OUTPUT_DIR="$PWD/built-specs" \
    mvn -pl poc/poc-3-builder-agent spring-boot:run

# POC 4 — agent-spec format (offline surface + endpoint-gated run): resolve + run the exported goal on demand.
BUILDER_PACK_DIR="$PWD/agent-specs/explainer" BUILDER_INPUT="Explain how a coffee machine works" \
    mvn -pl poc/poc-4-agent-spec-format spring-boot:run

# POC 5 — eval (endpoint-gated): score the explainer spec on a set of tasks. By default runs the
#   eval 5× and reports eval-score stability (set BUILDER_EVAL_RUNS=1 for just the composite).
BUILDER_PACK_DIR="$PWD/agent-specs/explainer" \
    mvn -pl poc/poc-5-eval spring-boot:run

# POC 6 — spec composition (endpoint-gated): chain two independent specs via GOAP.
BUILDER_PACK_DIRS="$PWD/agent-specs/compose-extract,$PWD/agent-specs/compose-explain" \
BUILDER_TARGET_GOAL=explainerDone BUILDER_INPUT="Explain how a coffee machine works" \
    mvn -pl poc/poc-6-agent-spec-composition spring-boot:run

# POC 7 — runtime adapter (endpoint-gated): a spec invokes a CLI via the `shell` tool group.
BUILDER_PACK_DIR="$PWD/agent-specs/shell-runner" \
BUILDER_INPUT="Run the command: uname -s, and report the result" \
    mvn -pl poc/poc-7-runtime-adapter spring-boot:run

# POC 8 — retrieval (offline, no endpoint): benchmark the spec retriever over same-typed specs.
mvn -pl poc/poc-8-retrieval exec:java

# POC 9 — quality refinement (endpoint-gated): score a weak spec, rewrite its prompt, re-score.
mvn -pl poc/poc-9-quality-refinement spring-boot:run

# POC 10 — trust tiers / guardrails (endpoint-gated for the guardrail demo):
#   offline tier checks always run; the guardrail run needs the endpoint env vars.
mvn -pl poc/poc-10-trust-tiers spring-boot:run

# POC 11 — progressive disclosure (offline, no endpoint): show plan context stays constant as a
#   reference file grows.
mvn -pl poc/poc-11-progressive-disclosure exec:java

# POC 12 — semantic retrieval at scale (offline, no endpoint): lexical vs dense over a 200+ spec
#   library, measuring the near-duplicate shadowing curve.
mvn -pl poc/poc-12-semantic-retrieval exec:java

# POC 14 — spec Composer (offline, no endpoint): chains compose-extract + compose-explain on a
#   type-checked canvas and exports a single composite agent spec (target/composed/compose-composite).
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

# POC 15 — the visual spec-chain editor (offline, no endpoint): a web app. Boot it, then open the
#   canvas in a browser — drag specs onto the canvas, connect ports, and Export the composite.
SERVER_PORT=18080 mvn -pl poc/poc-15-composer-ui spring-boot:run
#   → open http://localhost:18080/

# POC 16 — the remote-action bridge. Boots the platform, starts a deterministic sidecar
#   (default port 18090), and runs a GOAP plan that executes the sidecar's action over HTTP.
#   The plan is fully deterministic (no LLM call); the endpoint is only needed for platform boot.
mvn -pl poc/poc-16-remote-action spring-boot:run -Dbuilder.input="Explain how a remote worker pool runs a task"

# POC 17 — GLiNER2 transformer spec classification (local-model-gated, no endpoint): the
#   three-method shadowing benchmark. Needs the one-time ONNX bundle download first (see
#   poc/poc-17-gliner-retrieval/README.md); without it the launcher prints a clear missing-model
#   error and exits non-zero.
mvn -pl poc/poc-17-gliner-retrieval exec:java

# POC 18 — spec lifecycle / drift detection (endpoint-gated): a SpecOps-style proactive
#   re-eval. Runs the deterministic lifecycle walkthrough, then (with a live endpoint) re-evaluates
#   the healthy vs. regressed spec and moves it through incubating → active → degraded → retired.
mvn -pl poc/poc-18-agent-spec-lifecycle spring-boot:run

# POC 19 — small-LLM spec selection (docker-gated): the 0.6B decision model is served in a
#   Docker container (llama.cpp) that Spring Boot Dev Services starts/stops with the app from
#   the module's compose.yml (port 18081). Needs the one-time GGUF download first (see
#   poc/poc-19-small-llm-selector/README.md); without it (or without Docker) the launcher prints
#   a clear error and exits non-zero.
mvn -pl poc/poc-19-small-llm-selector spring-boot:run

# POC 20 — real semantic EmbeddingService (local-model-gated, no endpoint): a real ONNX sentence
#   encoder (all-MiniLM-L6-v2) behind POC 12's SpecScorer seam, on POC 12's 200+ shadowing scenario.
#   Needs the one-time ONNX download first (see poc/poc-20-semantic-embedding/README.md); without
#   it the launcher prints a clear missing-model error and exits non-zero.
mvn -pl poc/poc-20-semantic-embedding exec:java
```

## Full loop (task 7.1)

`run-loop.sh` chains the two endpoint-gated steps into one command: it builds a spec with the
builder, captures the written spec path, and runs that spec in the no-code runtime.

```bash
# One command — description → built agent spec → goal output.
./run-loop.sh "An agent spec that explains a topic in plain language and posts it as a background message" \
              "Explain how a coffee machine works"

# Override the planner / output dir:
PLANNER=UTILITY BUILDER_OUTPUT_DIR=./built-specs ./run-loop.sh
```

It fails fast with a clear message if the endpoint env vars are unset, and surfaces the builder's
output if the built draft fails validation (design D5 — an invalid spec is never written).

## Endpoint-gated work

The offline build verifies all non-LLM logic (format round-trip, validation, the builder's
validate/write steps, the eval scoring/composite, the agent-spec format surface, the retrieval
benchmark, the progressive-disclosure measurement, the trust-tier allowlist, and the
prompt-injection guardrail). The end-to-end runs that call the model — the builder's
`compileFromDescription`, the runtime's goal run, the NL-compile, the eval invocation, the
composition run, the CLI-spec run (POC 7), the eval-guided refinement loop (POC 9), the
guardrail demo's clean run (POC 10), and the remote/MCP goal run (POC 13) — are scaffolded with
working code but require a live endpoint to execute. POC 8, POC 11, POC 12, and POC 14 (the
Composer's canvas + composite export) are fully offline and need no endpoint; running POC 14's
*exported* composite through the runtime is the one endpoint-gated step. POC 15 (the visual builder
UI) is a web app whose canvas, palette, and curation-aware retrieval pane are fully offline (they
manipulate the same offline composer model + POC 18's lifecycle), while its author, eval/refinement,
and curate re-eval panes are endpoint-gated. POC 16 (the remote-action bridge) needs the endpoint
only to boot the platform — its GOAP plan over the remote action is fully deterministic and never
calls the model. POC 17 (GLiNER2 spec classification)
introduces a **third category — local-model-gated**: it needs no endpoint, but runs the benchmark
only when the one-time ONNX bundle is present (offline tests mock the classifier, so the build stays
green without it). POC 18 (spec lifecycle / drift detection) is endpoint-gated for the live
re-eval, but its content-hash / drift / lifecycle state machine is pure and pinned by offline tests,
so `mvn test` stays green without a model.
