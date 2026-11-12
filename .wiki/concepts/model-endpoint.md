---
title: "Model endpoint and endpoint-gating"
type: "concept"
status: "active"
language: "default"
source_paths:
  - "smith-common/src/main/java/com/embabel/smith/common/config/ModelEndpointConfig.java"
  - "poc/poc-1-no-code-runtime/src/main/resources/application.yml"
  - "poc/poc-1-no-code-runtime/src/main/java/com/embabel/smith/runtime/NoCodeRuntime.java"
updated_at: "2026-11-01"
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
