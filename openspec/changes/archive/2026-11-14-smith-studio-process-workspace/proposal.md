# Proposal

## Why

The studio's landing surface was the 13-pane POC sidebar — every POC demoted to a nav item,
which does not express the product's primary purpose. That purpose is to **author an agentic
process from business documents**: describe (or point at) a process, have the model propose the
step decomposition as a GOAP skill-pack, see it as an editable flow, dry-run it, and publish it.
This change makes that the home screen and wires the generate / simulate / discover / publish
pipeline behind it.

## What Changes

- **Home workspace** — a new `ProcessWorkspace` becomes the `/` landing page (the author-a-process
  flow); the existing panes are demoted to a secondary "Operations" nav group.
- **Generate** — `POST /api/generate` (blueprint text, docs path, or repo URL) runs the
  `process_to_flow` prompt against the model and returns a `SkillPack` (one exported goal + action
  steps + intermediate types) plus validation issues. The model proposes the step split.
- **Prompt-var canonicalisation** — the generator rewrites each step's `{{var}}` to the runtime's
  decapitalized input-type-name contract (e.g. `{{technical_report}}` → `{{technicalReport}}`),
  so a generated pack always passes `SkillPackValidator` and is discoverable by `FolderSkillSource`.
- **Simulate** — `POST /api/simulate` (pack + sample input) writes the pack to a throwaway folder
  source and runs it through the change-03 runtime core, returning a per-step trace and the goal
  output. Nothing is published.
- **Discover** — `POST /api/discover` (local docs dir or repo URL) loads the business-process docs
  (git-cloning a URL to a local copy) and returns them for the UI; an auto/manual toggle controls
  whether generation runs straight after.
- **Frontend** — the workspace renders the pack as a Vue Flow layered graph (topological layout:
  parallel steps fan out in a column, the goal sits rightmost), offers a step/goal side editor, a
  simulate panel with the trace, and publish (via the existing compose endpoint).

**Out of scope for this pass** (deferred): model-based *extraction* of the process from a raw
codebase via `business_process_discovery.jinja` (discover currently loads existing docs, it does not
run the model to extract the process), and a durable store for generated packs (they are per-run
and ephemeral).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `studio-web`: adds the author-a-process home workspace — generate a GOAP pack from a blueprint or
  discovered docs, render it as an editable layered flow, simulate a run, and publish the pack — plus
  the generate/simulate/discover REST endpoints and the generated prompt-variable canonicalisation.

## Impact

- `prod/embabel-smith-studio/`:
  - Backend: `generate/` (FlowPromptCaller, LlmFlowPromptCaller, BlueprintLoader,
    ProcessFlowGenerator, FlowValidator), `discover/` (BusinessProcessDiscovery),
    `rest/` (GenerateController, SimulateController, DiscoverController + DTOs),
    `config/StudioConfiguration` beans, `prompts/process_to_flow.jinja`.
  - Frontend: `panes/ProcessWorkspace.vue`, `compose/model.ts` (`packToNodes`), `router.ts`,
    `App.vue` (nav), `api/types.ts` + `api/client.ts`.
- Depends on `embabel-smith-runtime` (SkillRunService / RuntimeCore), `embabel-smith-skillsource`
  (FolderSkillSource), `smith-common` (SkillPack model, SkillPackWriter/Reader, SkillPackValidator).
- A live model endpoint is required for generate and simulate; without it the endpoints return the
  structured `MODEL_NOT_CONFIGURED` error and the app still boots.
