---
title: "Model endpoint and endpoint-gating"
type: "concept"
status: "active"
language: "default"
source_paths:
  - "smith-common/src/main/java/com/embabel/smith/common/config/ModelEndpointConfig.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/rest/StatusController.java"
  - "poc/poc-1-no-code-runtime/src/main/resources/application.yml"
  - "poc/poc-1-no-code-runtime/src/main/java/com/embabel/smith/runtime/NoCodeRuntime.java"
updated_at: "2026-11-15"
---

# Model endpoint and endpoint-gating

No model endpoint is hardcoded anywhere. Every POC is pointed at an **OpenAI-compatible** endpoint
(LiteLLM / OpenAI / Ollama) at run time via environment variables (design D4). This is what lets
the project be scaffolded and built before any endpoint is fixed, and run against different
backends without code changes.

## The four variables

```bash
export OPENAI_CUSTOM_BASE_URL=http://localhost:4000
export OPENAI_CUSTOM_API_KEY=sk-...
export OPENAI_CUSTOM_MODELS=my-model
export EMBABEL_MODELS_DEFAULT_LLM=my-model
```

These feed Embabel's `openai.custom` config, declared in each POC's `application.yml` as
`${OPENAI_CUSTOM_BASE_URL:}` etc. (the `:` means "empty default"). See
`poc/poc-1-no-code-runtime/src/main/resources/application.yml`.

## The "clear configuration error" contract

`ModelEndpointConfig` (in `smith-common`) is shared by all POC launchers so the behaviour is
consistent. `isConfigured()` requires all three of `baseUrl`, `apiKey`, and `models` to be
non-blank. `configError()` returns `null` when fully configured, otherwise a **clear message
naming the missing variables** — not an opaque model error.

Every launcher checks this in `run()` before doing anything else:

```java
String configError = endpoint.configError();
if (configError != null) {
    throw new IllegalStateException(configError);
}
```

So starting an endpoint-gated POC without the env vars fails fast with a helpful message. This is
covered by `poc/poc-1-no-code-runtime/.../ModelEndpointConfigTest.java`.

## In `prod/`: the gate only, never the model name

Since the `prod-model-layer` change, `prod/` classes hold `ModelEndpointConfig` for exactly two
reasons: asking **whether** a model-backed call can run (`isConfigured()` / `configError()`, which is
how the REST endpoints produce the structured `MODEL_NOT_CONFIGURED` response) and, in
`rest/StatusController.java`, reporting the configured default LLM for the UI's readiness chip.
Nothing in `prod/` picks a model to call — the model is whatever Embabel's platform configuration
selects (`EMBABEL_MODELS_DEFAULT_LLM` and the provider roles), which is also what makes per-role model
choices possible at all. The deleted raw callers used to do `models.split(",")[0]` themselves. See
[[model-calls-go-through-ai]].

The POC launchers are different: several of them still build their own OpenAI-compatible HTTP caller,
because that raw arm is what some POCs are measuring (POC 24, POC 25). That is deliberate — the guard
tests are `prod/`-only.

## Offline vs. endpoint-gated

This is the project's central split:

- **Offline** (always runs in CI via `mvn -q verify`): format round-trip, validation, the
  builder's validate/write steps, the eval scoring/composite, the spec-format surface, planner
  resolution, type mapping, step-spec assembly. These are pure Java and testable without a model.
- **Endpoint-gated** (requires a live endpoint): anything that actually calls the LLM — the
  builder's `compileFromDescription`, the runtime's goal run, the NL-compile, the eval invocation,
  and the full build→run loop.

The code is scaffolded and the offline halves are tested; the endpoint-gated halves are marked
**(needs live endpoint)** in `openspec/changes/embabel-agent-builder/tasks.md` and are reported as
gated, not assumed. See [[what-is-embabel-smith]] and [[getting-started]].
