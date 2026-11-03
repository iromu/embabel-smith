# Proposal

## Why

POC 17 proved a real transformer zero-shot classifier (GLiNER2, 205M-param BERT) breaks the
skill-shadowing tie that text similarity cannot — **83%** Precision@1 vs lexical **17%** and
dense **0%** on the same scenario. The next mechanism to test is a *small LLM*: a 0.6B model
fine-tuned specifically for single-label decisions (`iromu/Qwen3-0.6B-openjev` — question +
options in, one label letter out; 84.9% on its own validation split, 0% for the untrained base).
Skill selection is exactly that task shape. This POC asks whether a sub-billion-parameter LLM
serves as a skill selector on the same shadowing scenario, and how it compares to the BERT
classifier — and it proves a new, reusable pattern: a POC that **serves its own model in a
Docker container whose lifecycle Spring Boot Dev Services manages**.

## What Changes

- Add a new POC module `poc/poc-19-small-llm-selector` (**POC 19**) to the `poc/pom.xml`
  aggregator.
- **Self-contained model serving:** a `compose.yml` in the module defines a
  `ghcr.io/ggml-org/llama.cpp:server` container serving the
  `iromu/Qwen3-0.6B-openjev-GGUF` **Q8_0** weights (mounted from a gitignored `models/` dir,
  one-time `hf download`). `spring-boot-docker-compose` (Spring Boot Dev Services) starts the
  container at app start, waits for readiness, and stops it at shutdown; if it is already
  running the app just connects. The FP8 checkpoint is not used — this machine's 2080 Ti
  (Turing) cannot run vLLM modelopt-FP8, which requires Hopper+.
- Add an `LlmSelector` implementing POC 17's `SkillSelector` seam over the container's
  OpenAI-compatible `/v1/chat/completions`, in **two prompt formats**: the model's
  in-distribution typed-decision format (question + A..N options, single-letter answer) and an
  out-of-distribution free-form prompt ("reply with the skill id") — the ID/OOD gap is itself
  a finding.
- **Reuse** POC 17's `ShadowingRunner`, the `LexicalSelector`/`DenseSelector`/`GlinerSelector`
  baselines, and POC 12's `ScaleSkillLibrary` (POC 19 depends on `poc-17-gliner-retrieval`),
  reporting one Precision@1 table with the small-LLM as a fourth method under both prompt
  formats.
- Introduce a **new runtime category — docker-gated**: the benchmark runs when the Docker
  daemon is reachable and the weights are present (clear error naming the one-time
  `hf download` command otherwise); offline unit tests stub the `/v1` endpoint so the module
  builds green without Docker.
- Document POC 19 in `README.md` (module table + run command), `docs/04-poc-run-results.md`,
  and a POC README mermaid diagram per the project convention.

## Capabilities

### New Capabilities
- `small-llm-skill-classification`: Select one skill from a same-typed candidate set using a
  small local LLM served in a Docker container managed by Spring Boot Dev Services, and
  measure its selection accuracy against the lexical, dense, and GLiNER2 baselines on the
  skill-shadowing scenario, under the model's in-distribution decision prompt format and an
  out-of-distribution free-form format.

### Modified Capabilities
(none)

## Impact

- New module `poc/poc-19-small-llm-selector` registered in `poc/pom.xml`; no changes to
  existing POCs.
- New dependencies for the module: `spring-boot-starter`, `spring-boot-docker-compose`,
  `poc-17-gliner-retrieval` (transitively `poc-12-semantic-retrieval`).
- Runtime requirements: Docker daemon; port **18081** (8080 is held by an unrelated service,
  18080 is POC 13's); one-time ~845 MB image pull + ~640 MB weights download; CPU-only
  serving is sufficient (spike: sub-second answers on CPU).
- Docs: `README.md`, `docs/04-poc-run-results.md`, POC README mermaid diagram.
