# Design

## Context

See proposal.md for motivation. Constraints that shape the approach:

- **POC 17's architecture is the reuse target.** `SkillSelector` is a one-method seam
  (task query + candidate set → top-1 selection); `ShadowingRunner` prints a Precision@1
  table for any number of named selectors; `ScaleSkillLibrary` (POC 12) supplies the six
  targets and their near-duplicates. POC 19 depends on `poc-17-gliner-retrieval` and adds
  the LLM as a fourth method — no changes to existing POCs.
- **Hardware.** This machine has an RTX 2080 Ti (Turing, sm75). The
  `iromu/Qwen3-0.6B-openjev-FP8` checkpoint (ModelOpt FP8) cannot be served here: vLLM's
  modelopt-FP8 path requires Hopper (sm90)+. The published siblings are
  `iromu/Qwen3-0.6B-openjev` (BF16) and `iromu/Qwen3-0.6B-openjev-GGUF`
  (Q4_K_M / Q5_K_M / Q8_0 / BF16 GGUF).
- **The model is a typed-decision contract.** Fine-tuned so that a prompt of the form
  `<question> + lettered options + "Answer with the single letter of the correct option."`
  yields exactly one label letter as the first generated token, then EOS (84.9% on its own
  validation split; the untrained base emits 0% letters). The decision chat template ships
  in the GGUF metadata, so the OpenAI-compatible endpoint renders it.
- **Container lifecycle must come from Spring Boot Dev Services** (user requirement), per
  docs.spring.io/spring-boot/reference/features/dev-services.html. That page offers two
  mechanisms: the Docker Compose dev service (`spring-boot-docker-compose`, main classpath,
  `docker compose up` at start / `stop` at shutdown, TCP-connect readiness by default,
  connect-only when the container already runs) and the Testcontainers dev service
  (`spring-boot-testcontainers`, test classpath, `GenericContainer` bean +
  `DynamicPropertyRegistrar`, launched via `spring-boot:test-run`).
- **Ports.** 8080 is held by an unrelated long-running service; 18080 is POC 13's. The
  spike used 18081 — it is free.
- **Spike (2026-11-02, validated).** `ghcr.io/ggml-org/llama.cpp:server` (845 MB) + Q8_0
  GGUF (640 MB) on CPU: container up in seconds; decision-format prompt → correct letter in
  0.41 s (2 completion tokens, `finish_reason: stop`); free-form prompt → correct number in
  0.26 s. Args passthrough to the entrypoint (`llama-server`) confirmed.

## Goals / Non-Goals

**Goals:**
- A POC that is self-contained about its model: the app boots, the model server exists, the
  benchmark runs, the container stops — all in one `mvn spring-boot:run`.
- A directly comparable 4-method (× 2-format for the LLM) Precision@1 table on POC 17's
  shadowing scenario.
- Docker-gated runtime with actionable errors; offline-green tests without Docker.

**Non-Goals:**
- GPU serving, FP8/NVFP4 checkpoints, or multi-model serving.
- Production-grade serving (auth, scaling, persistence) — this is a POC.
- Changes to POC 17/12 or to any other module.
- Training or re-quantizing anything.

## Decisions

1. **Serve the Q8_0 GGUF with `ghcr.io/ggml-org/llama.cpp:server`, CPU-only.**
   The FP8 checkpoint is excluded by hardware (Decision context above). Alternatives:
   vLLM + BF16 (works on the 2080 Ti but a ~10 GB image for a 0.6B model, and couples the
   POC to the GPU); Ollama (needs a GGUF import step the registry does not cover for this
   model). llama.cpp is the lightest path, is the serving path documented on the model's
   own pages, and the 0.6B runs comfortably on CPU — keeping the container free of GPU
   runtime dependencies. Q8_0 over Q4_K_M: near-lossless for a model this size, and the
   spike validated the behavior on Q8_0.

2. **Docker Compose dev service, not the Testcontainers dev service.** The Compose variant
   keeps the whole POC on the main classpath (POC 17-style single launcher via
   `mvn spring-boot:run`); the `compose.yml` is an inspectable artifact, and the model
   server can be run standalone with plain `docker compose up`. Its connect-only-when-
   already-running behavior makes prompt iteration cheap (re-run the benchmark without
   reloading the model). The Testcontainers variant requires the test-classpath
   "test-launchable application" pattern (`spring-boot:test-run` with the app in
   `src/test`), which is awkward for a POC whose entry point is the benchmark itself, and
   starts a fresh container on every launch.

3. **Fixed port 18081, plain `llm.base-url` property, no service-connection label.**
   The llama.cpp image is not in dev services' supported-connection list, so no connection
   details bean is created for it; the app reads `llm.base-url`
   (default `http://localhost:18081/v1`) from configuration. A fixed mapping makes that
   trivial and the server reachable standalone; readiness uses dev services' default
   TCP-connect wait, which the spike proved sufficient (the port binds only once the model
   is loaded).

4. **`LlmSelector` as one `SkillSelector` per prompt format.** Two selector instances —
   in-distribution (decision format, lettered options, parse the single letter) and
   out-of-distribution (free-form, parse the referenced candidate) — are registered as
   separate named methods in `ShadowingRunner`'s table. This reuses POC 17's seam without
   modifying it, and makes the ID/OOD gap a first-class row in the results.

5. **Plain JDK `HttpClient` against `/v1/chat/completions`.** No new client dependency:
   one JSON request, one JSON response, temperature 0, `max_tokens` small (the contract
   answer is one token + EOS). Reply parsing: ID format → first letter token mapped to the
   candidate; OOD format → first numeric/label token mapped to the candidate; anything
   else → miss (spec: "Reply does not name a candidate").

6. **Gitignored `models/` with a one-time `hf download`, gated at launch.** Same
   convention as POC 17's ONNX bundle: `models/Qwen3-0.6B-openjev-Q8_0.gguf` is fetched
   once (`hf download iromu/Qwen3-0.6B-openjev-GGUF Qwen3-0.6B-openjev-Q8_0.gguf
   --local-dir models`) and mounted read-only into the container. The launcher checks
   Docker reachability and weight presence before starting, with actionable errors
   naming the missing piece and the exact command to obtain it.

## Risks / Trade-offs

- [0.6B quality on the OOD format may be poor] → That is the measurement, not a defect;
  the miss-handling requirement keeps the table honest (a non-answer counts as a miss, not
  a random guess).
- [Subtle drift between the reconstructed ID prompt and the exact training-time
  rendering] → The ID prompt follows the format pinned by the training project's
  regression test (`tests/test_decision_template.py` in the NeMo project) and the spike
  confirmed clean single-letter + EOS output on it; if the ID/OOD gap looks anomalous,
  diff the prompt against the training template before drawing conclusions.
- [Upstream image or weights change under us] → Pin the image tag used by the spike
  (`ghcr.io/ggml-org/llama.cpp:server` digest recorded in the run results) and the exact
  GGUF filename in `compose.yml`/the gate.
- [Port 18081 taken at run time] → The gate reports it clearly; the port is a module
  constant, trivial to change.
- [Dev services is a development-time feature] → The POC *is* a development-time
  artifact; nothing is packaged for deployment, so no escape-hatch is needed.
- [llama.cpp entrypoint assumptions] → The spike already proved args passthrough
  (`-m ... --port 8080 -c 2048`); the compose `command:` uses the same arg shape,
  verified in the first implementation task.

## Migration Plan

No migration — new module only. Rollout: register `poc-19-small-llm-selector` in
`poc/pom.xml`, run the one-time weights download, `mvn -pl poc/poc-19-small-llm-selector
spring-boot:run`. Rollback: delete the module and its aggregator line; no shared state is
touched.

## Open Questions

- Whether the GGUF's embedded chat template is the decision template or the stock Qwen3
  template (the packaging script copies the decision template into the merged model dir, so
  it should be embedded) — verifiable by dumping GGUF metadata during implementation; the
  spike's clean single-letter behavior already indicates the rendering is correct either
  way.
