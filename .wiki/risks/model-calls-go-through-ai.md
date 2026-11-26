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
  - "prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/remote/RemoteActionBridge.java"
  - "docs/05-architecture.md"
updated_at: "2026-11-26"
---

# Production model calls go through `Ai`, never the open endpoint

**Every model call in `prod/` goes through Embabel's `Ai` service** (`com.embabel.agent.api.common.Ai`).
No production class opens its own HTTP connection to a model endpoint. The invariant's settled wording —
why it exists, what the guard covers, that it is deliberately `prod`-only, and the two behaviour changes
the migration carried — is `docs/05-architecture.md` §The model layer. This page records the enforcement
mechanics and the places where the rule is easy to misread.

## Why it is load-bearing

With a raw call, a class needs its own base URL, API key and **model name**, so it reads
`ModelEndpointConfig` and picks `models.split(",")[0]`. Then the endpoint env vars have two masters:
Embabel's role config (`EMBABEL_MODELS_DEFAULT_LLM`, per-provider roles) and whatever the raw caller
guessed — change the model in one place and the other keeps calling the old one. Routing through `Ai`
also buys role-based selection, retry and cost tracking for free. `grep -rn 'models.split'
prod/*/src/main/java` is empty at HEAD, which is what that claim rests on.

## How it is enforced

Two offline tests walk the source tree and fail on a forbidden import — one per module that talks to a
model, `builder/NoDirectModelRequestTest` and `studio/NoDirectModelRequestTest`:

```java
offenders = files.filter(p -> p.toString().endsWith(".java"))
                 .filter(this::importsHttpClient)   // contains "import java.net.http."
```

Each walks its own module's `src/main/java` (`Path.of("src","main","java")`) and carries no `@Tag`, so
`make test` catches a violation with no endpoint needed. `prod/embabel-smith-runtime` is the only
`prod/` module that imports `java.net.http` in production sources at all, and it has **no** guard: its
`remote/RemoteActionBridge.java` legitimately calls a remote-action sidecar's catalog and execute
endpoints — a different endpoint, not a model call. `a2a-starter`, `specsource` and `skillevaluator`
likewise carry no guard because they hold no model call. **Do not add a guard to `a2a-starter`**: it is
a transport, and a guard there would imply it has a model call to route.

The call sites that migrated (`LlmPromptRewriter`, `FlowPromptCaller`, `LlmFlowPromptCaller` are all
absent from the tree):

| Was (raw `HttpClient`) | Now (through `Ai`) |
| --- | --- |
| `builder/eval/LlmPromptRewriter` (deleted) | `builder/eval/AiPromptRewriter` |
| `studio/generate/FlowPromptCaller` + `LlmFlowPromptCaller` (deleted) | the builder's `core/SpecPromptCaller` → `core/AiSpecPromptCaller` |

`StudioConfiguration` builds those once as beans: `specPromptCaller(Ai)` (`LlmOptions.withDefaults()
.withTemperature(0.2).withMaxTokens(12000)` — the budget the deleted `LlmFlowPromptCaller` carried) for
generate and extract, and `promptRewriter(Ai)` for the eval refiner and the self-improvement loop.
`BuildCommand` lost its `baseUrl`/`apiKey`/`model` constructor arguments along with the env lookups that
fed them.

The two behaviour changes are stated in `docs/05-architecture.md`; the locks are here: the system prompt
now goes through `PromptRunner.withSystemPrompt` and is skipped when blank, verified by
`AiSpecPromptCallerTest` (`verify(runner).withSystemPrompt(...)`, and `never()` for a blank one); and no
`prod` test asserts wire-level message text any more — `LiveBuildIntegrationTest` asserts the
caller-visible outcome.

## A second interface, still one model layer

`AiSpecPromptCaller` implements two seams: `SpecPromptCaller`, and
`core/ReferenceAwarePromptCaller` — a `@FunctionalInterface` whose one method is
`call(system, user, LlmReference)`. `skill/EvalRunner` uses it for the with-skill arm, where the reference
is the skill's `Skills` and attaching it is what puts the pack's script tools in the request beside the
prompt. The property the arrangement depends on is stated in the class javadoc: the reference attaches to
the same `PromptRunner` a plain call builds, so a reference-carrying call differs by the reference and
nothing else. That is why the seam does not weaken the rule — an `LlmReference` contributes capability
*tools*, not a choice of model, so there is still no second decision point about which model answers.
`EvalRunner` takes the seam or refuses; a caller that cannot carry a reference raises rather than
degrading to the plain call, which would leave the with-skill arm answering as though nothing were
attached.

## Do not

- Do not add a `java.net.http` (or other direct-**model**-endpoint) import under a `prod/*/src/main/java`
  that carries one of the guard tests. The ban is per-module, not repo-wide, so nothing fails the build if
  you route a *model* call through a raw client inside the runtime — that is exactly why this rule needs a
  human reviewer and not only a green reactor. Add a collaborator on `Ai` instead.
- Do not read a model name out of `ModelEndpointConfig` in `prod/`. It survives there only as the endpoint
  gate ([[model-endpoint]]) and as the configured-default readout in `rest/StatusController.java`; the
  platform chooses the model.
- The guard is deliberately **prod-only**: the POCs keep raw callers on purpose (POC 24's raw-prompt arm,
  POC 25's `RawChatCaller`, POC 23's remote embedder). Don't "fix" them.

Related: [[smith-builder]], [[smith-studio-web]], [[studio-process-workspace]], [[model-endpoint]].
