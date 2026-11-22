---
title: "Generation approaches: single-shot vs the Embabel NL compiler"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "poc/poc-24-generation-approaches/README.md"
  - "poc/poc-24-generation-approaches/results/comparison.md"
  - "poc/poc-24-generation-approaches/src/main/java/com/embabel/smith/gencmp/GenerationComparisonApp.java"
  - "poc/poc-24-generation-approaches/src/main/java/com/embabel/smith/gencmp/SingleShotGenerator.java"
  - "poc/poc-24-generation-approaches/src/main/java/com/embabel/smith/gencmp/EmbabelGenerator.java"
  - "poc/poc-24-generation-approaches/src/main/java/com/embabel/smith/gencmp/CompilationResultToAgentSpec.java"
  - "poc/poc-24-generation-approaches/src/main/java/com/embabel/smith/gencmp/ProcessValidator.java"
  - "poc/poc-24-generation-approaches/src/main/java/com/embabel/smith/gencmp/ComparisonReport.java"
  - "poc/poc-24-generation-approaches/src/main/java/com/embabel/smith/gencmp/OpenAiLlmCaller.java"
  - "examples/blueprints"
updated_at: "2026-11-22"
---

# Generation approaches (POC 24)

Two ways to turn a business-process blueprint into a multi-agent GOAP flow. Which one the studio's
**Generate** technique selector exposes ([[studio-process-workspace]]) is decided here.

| | Mechanism | Class |
| --- | --- | --- |
| **single-shot** | one coordinated LLM call emits the *whole* process as JSON | `gencmp/SingleShotGenerator.java` |
| **embabel** | Embabel's production NL compiler compiles **each agent independently** | `gencmp/EmbabelGenerator.java` |

Both are driven by `GenerationComparisonApp` over the blueprints in `examples/blueprints/`, in two
modes — `flat` (one agent) and `multi` (a decomposed team) — and the report lands in
`results/comparison.md`. The directory now holds five corpora (Aurora, Sentinel, TradingAgents,
agentic-software-factory, deer-flow); the committed run covers the first three, which are the
`cmp.blueprints` default (`GenerationComparisonApp.java:65`).

## What each side actually does

**single-shot** asks for the entire process in one answer, then `normalize` + `extractJson` tidy the
model's output into the POC-local `ProcessSpec` (process name + its agents). Because the model sees
the whole process at once, it is *able* to make sibling agents agree on shared type names.

**embabel** calls `DefaultNaturalLanguageCompiler` (`com.embabel.agent.spec.compile`, built with
`LlmOptions.withDefaultLlm()` and a `StepSpecContext` from the platform, `EmbabelGenerator.java:95-97`)
once per agent, and `CompilationResultToAgentSpec` maps each `AgentCompilationResult` back into the
project's own `AgentSpec` ([[agent-spec-model]]). The compiler never sees the sibling agents.

`ProcessSpec` is **not** the agent-spec format — it is a comparison harness type. Only
`CompilationResultToAgentSpec` produces real format objects.

## The validator is the reusable part

`ProcessValidator` checks four things a generated flow can get wrong: a step input nothing produces,
a goal unreachable from `UserInput`, duplicate goal names, and an agent no earlier agent feeds.
`connected` means the last agent's goal is reachable from the `UserInput` entry; `valid` is simply
`issues.isEmpty()`. (The README's "VALID requires no issues *and* connected" is a reporting
convention in `ComparisonReport`, not something the validator enforces.) This is the class worth
lifting into the studio's repair loop.

## Measured result — single-shot wins, and the reason is structural

12 committed runs, same model (`Qwen-instruct`) and temperature (0.2) throughout, so the difference
is the **mechanism**, not the model.

- **Flat:** single-shot **3/3** valid + connected, with coherent 8–10 step agents. The NL compiler's
  flat output is degenerate (1–5 steps) and disconnected — it invents an entry type
  (`PurchaseOrder`, `SecurityEvent`, `RunConfiguration`) instead of taking `UserInput`.
- **Multi:** single-shot **1/3** in the committed run (Aurora valid at 8 agents / 7 shared types;
  Sentinel and TradingAgents each introduced a **circular dependency** where parallel branches
  merge). A second run gave **3/3**, but that exists only as prose in the README — no committed
  artifact backs it. The NL compiler was disconnected on **every run of every blueprint**: each
  sub-agent compiles in isolation, so the hand-off types never line up (`OrderRecord` vs
  `RawPurchaseOrder`) and the first agent never takes `UserInput`.

**The load-bearing finding:** `DefaultNaturalLanguageCompiler` compiles each agent against only its
own `StepSpecContext`, so it *cannot* coordinate shared type names across siblings. That is a
capability gap, not a prompt problem.

**Recommendation on record:** single-shot for multi-agent generation, paired with `ProcessValidator`
plus a repair step (it detects exactly the cycles single-shot introduces); keep the NL compiler for
single-agent authoring until it can coordinate cross-agent hand-offs.

## Two caveats worth keeping honest

- **Prompt asymmetry.** Single-shot's prompts are purpose-built for this POC; the compiler uses
  Embabel's production prompts. It compares mechanisms, not prompt quality.
- **Raw compiler output.** The embabel specs are shown unnormalized — the builder normally rewrites a
  `void` goal to `BackgroundMessage`. The multi-agent disconnection is independent of that.

## Running it

```bash
mvn -pl poc/poc-24-generation-approaches spring-boot:run \
  -Dspring-boot.run.arguments="--cmp.examples=/abs/path/examples/blueprints --cmp.blueprints=Aurora,Sentinel,TradingAgents --cmp.modes=flat,multi"
```

**Endpoint-gated** via `smith-common`'s `ModelEndpointConfig.configError()`, which checks
`OPENAI_CUSTOM_BASE_URL` / `_API_KEY` / `_MODELS` only; `EMBABEL_MODELS_DEFAULT_LLM` is read but not
gated ([[model-endpoint]]). `OpenAiLlmCaller` uses `max_tokens` 12000, temperature 0.2, 300 s timeout
(`:30-32,55-56,67`). Offline tests cover `SingleShotGenerator` and `ProcessValidator` only —
`EmbabelGenerator`, `ComparisonReport`, and `OpenAiLlmCaller` are untested. See
[[what-the-pocs-prove]].

**Productized 2026-11-15:** the single-vs-multi comparison this POC measured by hand is now a build
stage — the builder generates both shapes from one description, scores them on the same eval tasks,
and recommends one ([[two-candidate-build]]).
