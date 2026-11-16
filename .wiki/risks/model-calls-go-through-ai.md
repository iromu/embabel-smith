---
title: "Every prod model call goes through Embabel's Ai"
type: "risk"
status: "active"
language: "default"
source_paths:
  - "prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/NoDirectModelRequestTest.java"
  - "prod/embabel-smith-studio/src/test/java/com/embabel/smith/studio/NoDirectModelRequestTest.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/AiSpecPromptCaller.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/eval/AiPromptRewriter.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/config/StudioConfiguration.java"
  - "openspec/changes/prod-model-layer/proposal.md"
updated_at: "2026-11-15"
---

# Production model calls go through `Ai`, never the open endpoint

The rule from the `prod-model-layer` change: **every model call in `prod/` goes through Embabel's
`Ai` service** (`com.embabel.agent.api.common.Ai`). No production class opens its own HTTP connection
to the model endpoint. The POCs grew several hand-rolled OpenAI-compatible callers on the way here;
the product consolidated them onto one layer.

## Why it is load-bearing

With a raw call, a class needs its own base URL, API key and **model name**, so it reads
`ModelEndpointConfig` and picks `models.split(",")[0]`. That means the endpoint env vars have two
masters: Embabel's own role config (`EMBABEL_MODELS_DEFAULT_LLM`, per-provider roles) and whatever
the raw caller guessed. Change the model in one place and the other silently keeps calling the old
one. Routing through `Ai` also gives the product role-based model selection, retry, and cost tracking
for free — none of which a hand-rolled `HttpClient` gets.

## How it is enforced

Two offline tests walk the source tree and fail on a forbidden import — one per module that talks to
a model, `builder/NoDirectModelRequestTest` and `studio/NoDirectModelRequestTest`:

```java
offenders = files.filter(p -> p.toString().endsWith(".java"))
                 .filter(this::importsHttpClient)   // contains "import java.net.http."
```

So a violation is caught by `make test`, in the same place as everything else, with no endpoint
needed. Only these two modules import `Ai`; `runtime` and `specsource` carry no guard because they
hold no model call — the runtime reaches the model only by executing actions on the platform.

The call sites that migrated:

| Was (raw `HttpClient`) | Now (through `Ai`) |
| --- | --- |
| `builder/eval/LlmPromptRewriter` (deleted) | `builder/eval/AiPromptRewriter` |
| `studio/generate/FlowPromptCaller` + `LlmFlowPromptCaller` (deleted) | the builder's `core/SpecPromptCaller` → `core/AiSpecPromptCaller` |

`StudioConfiguration` now builds those once as beans: `specPromptCaller(Ai)` (12 000 tokens,
temperature 0.2 — the budget the deleted `LlmFlowPromptCaller` carried) for generate and extract, and
`promptRewriter(Ai)` for the eval refiner and the self-improvement loop. `BuildCommand` lost its
`baseUrl`/`apiKey`/`model` constructor arguments along with the env lookups in
`SmithBuilderApplication` that fed them.

## Two behaviour changes that came with it

- **The system prompt is a system prompt.** `AiSpecPromptCaller` used to concatenate
  `system + "\n\n---\n\n" + user` into one user message to imitate the raw arm POC 24 measured
  ([[generation-approaches]]). It now uses `PromptRunner.withSystemPrompt`, and skips it when blank
  (locked by `AiSpecPromptCallerTest`).
- **Raw error strings are gone.** Messages like `"Rewrite LLM call failed: HTTP 429 …"` no longer
  exist; `LiveBuildIntegrationTest` asserts the caller-visible outcome instead. Don't re-assert on
  wire-level message text.

## Do not

- Do not add a `java.net.http` (or other direct-endpoint) import under any `prod/*/src/main/java`.
  Add a collaborator on `Ai` instead — the tests above will fail the build otherwise.
- Do not read a model name out of `ModelEndpointConfig` in `prod/`. It survives there only as the
  endpoint gate ([[model-endpoint]]) and as the configured-default readout in
  `rest/StatusController.java`; the platform chooses the model.
- The guard is deliberately **prod-only**: the POCs keep raw callers on purpose (POC 24's raw-prompt
  arm, POC 25's `RawChatCaller`, POC 23's remote embedder). Don't "fix" them.

Related: [[smith-builder]], [[smith-studio-web]], [[studio-process-workspace]], [[model-endpoint]].
