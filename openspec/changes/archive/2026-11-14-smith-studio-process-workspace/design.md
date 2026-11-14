# Design

## Decisions

### D1 — One pack, not skills + tools
The whole process is a single `SkillPack` (one exported goal + action steps + intermediate types).
The steps *are* the "skills". "Tools" are deferred: the no-code skill-pack format has no LLM-tool
concept, and "tool" already means a trust-tier group in this codebase. Introducing a second artifact
type would add a generative process with no consumer.

### D2 — The model proposes the split
The step decomposition is not user-specified; the model decides it from the blueprint. The
`process_to_flow` prompt asks for the *core* end-to-end process, one step per meaningful stage,
wired by typed data (a step's `outputTypeName` is a later step's `inputTypeName`; first steps take
`UserInput`; the goal's `outputTypeName` must be produced by some step). This keeps the user's job
to *describe*, not *decompose*.

### D3 — Prompt-var canonicalisation is a generator responsibility, not a validator change
The runtime contract (encoded in `SkillPackValidator`) binds a step's prompt variables to the
*decapitalized short-name* of each declared input type (`{{userInput}}`, `{{technicalReport}}`).
Models habitually emit snake_case (`{{technical_report}}`), which fails `PROMPT_UNDECLARED_INPUT`
and silently drops the pack from `FolderSkillSource` (surfacing as "contains no skill-packs").
Rather than loosen the shared validator, the generator normalises: after parsing, each `{{var}}` is
matched (case/underscore-insensitively) to a declared input type and rewritten to its canonical form.
Unrecognised variables are left untouched so a genuine error still fails validation. The prompt is
also strengthened to state the convention explicitly.

### D4 — Discover loads docs now; model extraction is deferred
`/api/discover` is the second blueprint source: a local docs directory or a repo URL (git-cloned to a
local copy, depth 1). It returns the docs so the UI can show what it will feed the model. Running
`business_process_discovery.jinja` to *extract* the process from a raw codebase is a separate,
deferred step — the auto/manual toggle is already in the UI so the extraction can slot in without a
UI change.

### D5 — Simulate is a throwaway run, isolated from published state
Simulate writes the pack to a fresh temp folder (the pack lives in a named subdirectory, because
`FolderSkillSource` scans immediate subdirectories for `manifest.yml`), runs it via the change-03
runtime core on an isolated platform (`RuntimeCore.ingest` is isolated per call), and cleans up.
Nothing is published, so a dry-run cannot affect the real source.

### D6 — Layered flow layout is derived, not stored
The graph is computed from the pack (`packToNodes`): a topological layering where a step's column is
one past its producers' max column, rows spread vertically, and the goal sits rightmost. Edges are
derived from type-name wiring (handle ids `out:<Type>` / `in:<Type>`). Nothing is persisted — the
layout is a pure function of the pack, so edits re-derive it.

### D7 — Model-backed endpoints are endpoint-gated
Generate and simulate require a model endpoint. Without one they return the structured
`MODEL_NOT_CONFIGURED` error (not a crash), the app still boots, and the UI shows a "no endpoint"
state. This matches the existing studio REST contract.

### D8 — Extraction reuses the model seam and the discovery prompt
Model-based process extraction (task 5.1) is a *second* prompt run, not a new model client. It
reuses the existing `FlowPromptCaller` seam (the same raw chat-completions call as generation) with
the `business_process_discovery.jinja` system prompt. The input is the *loaded docs corpus* (the
`/api/discover` output concatenated), and the output is a business-process description (markdown)
— not JSON — so no pack parsing is involved. It is a separate endpoint (`POST /api/extract`) from
generate, endpoint-gated like it, and the extracted description becomes the generation blueprint
(the UI hands it to `/api/generate` as `blueprint` instead of re-reading the raw docs).

### D9 — The durable store derives packs from disk; history is append-only
The durable store (task 5.2) roots at a configurable directory (`smith.studio.store-dir`, default
`~/.smith-studio/store`). Saved packs are full pack directories written by the shared
`SkillPackWriter` under `<root>/packs/<name>/`; `list` derives each record by reading the pack
(manifest name, goal, step count, dir mtime) so there is no separate index to drift from. Discovery
history is an append-only `discovery-history.jsonl` under the root — one record per successful
`/api/discover` (timestamp, source type, location, doc count). Reading back a saved pack uses the
shared `SkillPackReader`, so a reloaded pack is byte-identical to what was saved.

## Testing

- `ProcessFlowGeneratorTest` (offline): the real model answer (with its snake_case vars) is parsed and
  canonicalised into a pack that passes `SkillPackValidator` on disk and is discoverable by
  `FolderSkillSource`; asserts the vars are rewritten and no snake_case remains.
- `ProcessExtractorTest` (offline): a stub `FlowPromptCaller` returns a fixed description; the
  extractor returns it verbatim and threads the discovery prompt through as the system message.
- `ProcessStoreTest` (offline): save → list → load round-trips a pack (the loaded pack equals the
  saved one); list reflects multiple packs; discovery history appends and returns most-recent-first.
- Frontend `client.test.ts`: the new `extract`, `listStoredPacks`, `loadStoredPack`, `savePack`, and
  `listDiscoveries` calls hit the right endpoints and parse the responses.
- Live E2E (Playwright): home loads → load docs → generate renders the layered flow → simulate
  returns a per-step trace. Layout verified programmatically (node positions + edge count), since the
  environment has no image/vision bridge.
