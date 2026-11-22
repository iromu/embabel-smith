---
title: "Model endpoint and endpoint-gating"
type: "concept"
status: "active"
language: "default"
source_paths:
  - "smith-common/src/main/java/com/embabel/smith/common/config/ModelEndpointConfig.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/rest/StatusController.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/config/StudioConfiguration.java"
  - "docs/04-poc-run-results.md"
  - "poc/poc-1-no-code-runtime/src/main/resources/application.yml"
  - "poc/poc-1-no-code-runtime/src/main/java/com/embabel/smith/runtime/NoCodeRuntime.java"
updated_at: "2026-11-22"
---

# Model endpoint and endpoint-gating

No model endpoint is hardcoded anywhere. A run is pointed at a model at run time via environment
variables (design D4), and which backend it talks to is **detected** from the environment rather than
configured. Three providers are supported, in first-match order: `openai-custom` (any OpenAI-compatible
endpoint — LiteLLM, OpenRouter, Ollama), `openai`, and `anthropic`; `provider()` reports which one won,
and `NONE` means nothing in the environment named one. This is what lets the project be scaffolded and
built before any endpoint is fixed, and run against different backends without code changes.

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

The other two providers need no base URL and no model list: `OPENAI_API_KEY` or `ANTHROPIC_API_KEY`
registers Embabel's bundled catalog for that vendor, and the model is then chosen with
`EMBABEL_MODELS_DEFAULT_LLM` alone. `ModelEndpointConfig.detect(...)` takes the five values in that
precedence order — anything set among the three `OPENAI_CUSTOM_*` variables wins, then `OPENAI_API_KEY`,
then `ANTHROPIC_API_KEY` — so a machine holding more than one key uses the first of them.

## The "clear configuration error" contract

`ModelEndpointConfig` (in `smith-common`) is shared by all POC launchers so the behaviour is
consistent. `isConfigured()` is `configError() == null`, and what it demands is **provider-relative**:
each `Provider` carries its own `requiredEnvVars()`, so `openai-custom` needs `baseUrl` + `apiKey` +
`models`, while `openai` and `anthropic` need their single key plus `EMBABEL_MODELS_DEFAULT_LLM`. When
nothing at all is set, the message names all three routes rather than guessing one; otherwise it names
the missing variables of the provider that won — a **clear message**, not an opaque model error.

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

The one place `prod/` does look at *which* provider won is `config/StudioConfiguration.java`, which
builds the bean with `ModelEndpointConfig.detect(...)`, and `rest/ModelsController.java`, which behaves
differently for `OPENAI_CUSTOM` (the model list comes from the endpoint's own variables) than for the
catalog providers (it comes from Embabel's bundled catalog). Each `prod/` module's `application.yml`
binds `OPENAI_API_KEY` and `ANTHROPIC_API_KEY` to a placeholder default (`sk-smith-no-endpoint`) —
Embabel's provider starters refuse to boot without a key at all, so the placeholder is what lets a
module start and fail at the gate instead of failing at context startup.

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

The code is scaffolded and the offline halves are tested; the endpoint-gated halves are marked rather
than assumed. In `prod/` the marker is the JUnit `live` tag — excluded by the build's default
`test.excludedGroups`, selected only by `make test-live` — and in the POCs it is the launcher's endpoint
check, which fails the run fast when the variables are missing. Which of them were actually run is
recorded in `docs/04-poc-run-results.md`. See [[what-is-embabel-smith]] and [[getting-started]].
