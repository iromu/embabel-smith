# Tasks

## 1. Module scaffold + dependencies

- [x] 1.1 Create the `poc/poc-19-small-llm-selector` module (pom.xml + `src/main/java` +
  `src/test/java`) and register it in the `poc/pom.xml` aggregator; verify the reactor
  recognizes it with `mvn -q -pl poc/poc-19-small-llm-selector validate`.
- [x] 1.2 Add the module dependencies — `spring-boot-starter`,
  `spring-boot-docker-compose` (both managed by the Spring Boot BOM already imported at the
  root), and `poc-17-gliner-retrieval` (which transitively brings
  `poc-12-semantic-retrieval`) — and verify the module compiles with
  `mvn -q -pl poc/poc-19-small-llm-selector -am compile`.

## 2. Model-serving container (Docker Compose dev service)

- [x] 2.1 Write `compose.yml` in the module: service `model` with
  `image: ghcr.io/ggml-org/llama.cpp:server`,
  `volumes: [./models:/models:ro]`,
  `command: ["-m", "/models/Qwen3-0.6B-openjev-Q8_0.gguf", "--port", "8080", "-c", "2048"]`,
  `ports: ["18081:8080"]`; verify by running `docker compose -f poc/poc-19-small-llm-selector/compose.yml up -d`
  from the repo root, hitting `http://localhost:18081/v1/models` (expect the served model
  listed), then `docker compose down` — this also confirms the entrypoint args passthrough
  and the readiness behavior (port binds only once the model is loaded).
- [x] 2.2 Download the weights once — `hf download iromu/Qwen3-0.6B-openjev-GGUF
  Qwen3-0.6B-openjev-Q8_0.gguf --local-dir poc/poc-19-small-llm-selector/models` — add a
  `models/` gitignore entry and a short setup note in the module; verify
  `git status --ignored` shows `models/` ignored and the GGUF is present.

## 3. LLM selector (in-distribution + out-of-distribution)

- [x] 3.1 Implement the two prompt builders (ID: question + lettered options +
  "Answer with the single letter of the correct option."; OOD: free-form "reply with the
  skill id") and the reply parsers (ID → first letter mapped to a candidate; OOD → first
  candidate reference; anything else → no selection), and verify each with unit tests
  covering a correct answer, a wrong-but-valid answer, and a non-answer (no selection).
- [x] 3.2 Implement `LlmSelector` implementing POC 17's `SkillSelector` over
  `/v1/chat/completions` with the JDK `HttpClient` (temperature 0, small `max_tokens`,
  endpoint from `llm.base-url`, default `http://localhost:18081/v1`), one instance per
  prompt format; verify with unit tests against a stubbed local HTTP endpoint (no Docker)
  that the top-1 contract and miss handling hold.

## 4. Docker-gated launcher

- [x] 4.1 Implement the `@SpringBootApplication` launcher: before running, gate on Docker
  daemon reachability and on the GGUF being present, each failing with a clear, actionable
  error naming the missing piece (and the exact `hf download` command for missing weights);
  verify the gate logic with unit tests (stubbed daemon/weight checks) and verify the real
  launch fails with the clear weights-missing error when `models/` is empty.
- [x] 4.2 Wire the benchmark: run POC 17's `ShadowingRunner` with five named methods —
  `gliner2`, `lexical`, `dense`, `llm-id`, `llm-ood` — over the six `ScaleSkillLibrary`
  targets; verify the method registration and table shape with a unit test using stubbed
  selectors (no Docker, no model).

## 5. Real benchmark run + results documentation

- [x] 5.1 Run the real benchmark with `mvn -pl poc/poc-19-small-llm-selector spring-boot:run`
  (weights present, Docker up); verify the app starts the container via dev services,
  prints the five-method Precision@1 table with real LLM results under both prompt formats,
  and stops the container on shutdown.
- [x] 5.2 Document POC 19 in `README.md` (module table row + run command + the POC README
  mermaid diagram per project convention) and add a POC 19 section to
  `docs/04-poc-run-results.md` with the measured numbers and the pinned image digest;
  verify the documented run command matches the launcher's actual invocation.

## Workflow follow-up

- Archive the change once the POC 19 benchmark has run and its results are documented.
