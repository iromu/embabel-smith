---
title: "The studio's process workspace: author a GOAP flow from documents"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "prod/embabel-smith-studio/frontend/src/panes/ProcessWorkspace.vue"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/rest/GenerateController.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/rest/SimulateController.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/rest/DiscoverController.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/rest/ExtractController.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/generate/BlueprintLoader.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/SpecPromptCaller.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/generate/FlowValidator.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/generate/ProcessExtractor.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/discover/BusinessProcessDiscovery.java"
  - "prod/embabel-smith-studio/src/main/resources/prompts/business_process_discovery.jinja"
  - "examples/blueprints"
updated_at: "2026-11-15"
---

# The studio's process workspace

The studio's **home page** (`/`, aliased `/process`) is not a dashboard — it is a four-stage
wizard that takes you from a pile of business documents to a published agent spec
([[agent-spec-format]]). It is the product counterpart of the POC 2 / POC 3 NL-authoring path,
re-pointed at the production cores ([[smith-builder]], [[smith-runtime]]).

The stages: **Discover → Generate → Simulate → Publish**.

## Discover — find the processes in your documents

`POST /api/discover` (`rest/DiscoverController.java:49`) runs
`discover/BusinessProcessDiscovery.java` over a **docs path**, a **repo URL** (cloned), or pasted
**text** — the three source modes the UI offers. It uses the
`prompts/business_process_discovery.jinja` prompt and returns the business processes it found. The
controller is **offline-safe** and records each run to the discovery history best-effort, so a
history-write failure never fails the request. History is readable at `GET /api/store/discoveries`.

`POST /api/extract` (`rest/ExtractController.java:54`, `generate/ProcessExtractor.java`) is the
adjacent step: run the same discovery prompt over the documents and pull the process *detail* out as
markdown, which is the blueprint Generate consumes. It calls the model through the builder's
`SpecPromptCaller` seam (`builder/core/AiSpecPromptCaller` in production) — the studio's own
raw-endpoint caller was removed with the `prod-model-layer` change ([[model-calls-go-through-ai]]) —
and, like Generate, returns the structured `MODEL_NOT_CONFIGURED` error with no endpoint configured.

## Generate — documents to a GOAP flow

`POST /api/generate` (`rest/GenerateController.java:55`) is **endpoint-gated**: without
`OPENAI_CUSTOM_*` it returns the structured `MODEL_NOT_CONFIGURED` error rather than failing
([[model-endpoint]]). It reaches the model through a narrow `GenerateFn` seam, so the controller is
offline-testable.

The pipeline: `generate/BlueprintLoader.java` reads the blueprint documents, the builder's generator
for the chosen technique asks the model for the pack, and `generate/FlowValidator.java` checks the
produced flow before the UI ever renders it (`config/StudioConfiguration.java` `generateFn`).

**The technique selector matters.** The request carries `technique`, and the UI offers
*Single-shot* and *Embabel NL compiler* (`ProcessWorkspace.vue:494-498`). `StudioConfiguration`
dispatches over the builder's `Map<GenerationTechnique, AgentSpecGenerator>`, built with
`AgentSpecGenerators.production(ai, platform)`. The studio owns **no** generator of its own. The two
techniques are what POC 24 measured head to head; see [[generation-approaches]].

## Simulate and Publish

`POST /api/simulate` (`rest/SimulateController.java:49`, a `SimulateFn` seam) dry-runs the flow so
you see the plan before committing it. `Publish` sends the pack to a chosen source
(`folder:` or `git:`) through the builder's publishers — the same `builder.publish.*` classes the CLI
uses ([[smith-builder]], [[spec-sources]]). Saving a pack to the durable store goes through
`POST /api/store/packs` ([[smith-studio-storage]]).

## Where the sample blueprints live

`examples/blueprints/` holds three worked examples the workspace is designed against: **Aurora**
and **Sentinel** (each a single `system_specification.md`) and **TradingAgents** (a multi-document
set: overview, actors, rules, states, workflows, exceptions). Feed one of these directories to
Discover as a docs path.

**Tests:** `GenerateControllerTest`, `ProcessExtractorTest`, `StudioConfigurationGenerateFnTest`,
plus the front-end Vitest suite. Related: [[studio-compose-editor]], [[smith-studio-web]].
