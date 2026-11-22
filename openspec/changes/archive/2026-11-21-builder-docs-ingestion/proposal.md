# Proposal

## Why

The builder is the project's process-to-capability compiler, but the only way to turn business-process
*documents* into something it can build runs through the studio: `BlueprintLoader`, `ProcessExtractor`
and `BusinessProcessDiscovery`, plus the discovery prompt, all sit behind the studio's REST layer.
Three consequences follow. `smith build` cannot build from a blueprint at all — it takes a
natural-language description and nothing else. The builder cannot be tested against its realistic
input, because the ingestion half of that input is not in its module. And `examples/blueprints/` —
three committed process specifications totalling ~4,200 lines (Aurora, Sentinel, TradingAgents) —
feeds nothing.

The placement is also contrary to a rule the project already states. `docs/09-studio.md` leads with
"Thin controllers, cores do the work", and `StudioConfiguration`'s own `generateFn` javadoc says
"the studio owns no generator". Ingestion is the exception that survived: it is the studio's **only**
prompt resource, and `ProcessExtractor` already imports the builder's `SpecPromptCaller`, so the
dependency direction is already studio-to-builder and moving these classes cannot introduce a cycle.

## What Changes

- Move `discover/BusinessProcessDiscovery`, `generate/BlueprintLoader` and `generate/ProcessExtractor`
  out of `prod/embabel-smith-studio` into `prod/embabel-smith-builder`, together with
  `prompts/business_process_discovery.jinja`. Behaviour-preserving: same file filters, same 300K and
  200K corpus caps, same `--- <relPath> ---` separators, same truncation marker, same shallow-clone
  semantics.
- The builder exposes them as **composable pre-stages** that end in a description.
  `BuilderService.build(String description, PublishTarget, BuildOptions)` keeps its signature:
  ingestion is a stage before the build, not a stage inside it. The build's gate pipeline is untouched.
- **BREAKING** (internal API, not the REST contract): `smith build` gains a docs entry point so a
  blueprint directory is accepted where a description is today, and the studio's
  `discover`/`generate` packages lose three classes.
- The studio keeps the endpoints and the three-step interaction (Discover, Extract, Generate) that its
  UI depends on; it delegates each step to the builder instead of implementing it. Its
  `src/main/resources/prompts/` directory becomes empty.
- `generate/FlowValidator` deliberately **stays** in the studio: it is static, offline, makes no model
  call, and its javadoc says it mirrors the Compose editor's client-side rule, which makes it UI
  validation rather than build core.

## Capabilities

### New Capabilities

- `business-process-ingestion`: loading business-process documents from a local folder or a git
  repository, assembling them into a bounded corpus, and extracting from that corpus a
  business-process description the builder can build — including the builder's ability to accept
  documents as build input rather than only a hand-written description.

### Modified Capabilities

None. There are no capability specs under `openspec/specs/` — they were consolidated into `docs/` on
2026-11-15 — so this change has no existing requirement text to modify. The durable home for this
capability's behaviour is `docs/08-builder.md` (gaining the ingestion stages) and `docs/09-studio.md`
(losing them), both updated when the change archives.

## Impact

- **`prod/embabel-smith-builder`**: new ingestion package, one new prompt resource, one new CLI option
  on `BuildCommand`, plus `README.md`.
- **`prod/embabel-smith-studio`**: loses `discover/BusinessProcessDiscovery`,
  `generate/BlueprintLoader`, `generate/ProcessExtractor` and `prompts/business_process_discovery.jinja`;
  `DiscoverController`, `ExtractController`, `GenerateController` and the `StudioConfiguration` beans
  (`blueprintLoader`, `processExtractor`, `extractFn`, `businessProcessDiscovery`) re-point at the
  builder; their tests follow. No REST path, request or response shape changes.
- **Dependencies**: none. The studio already depends on the builder; the direction is preserved, and
  the `prod` enforcer ban on `poc-*` artifacts is unaffected.
- **Unblocks**: `builder-live-example-tests`, which cannot exercise docs-to-spec generation from the
  builder's own module until this lands.
- **Out of scope**: `improve/` and `loop/` are the same misplaced-logic problem at ~1,590 lines and
  are named for a later change, not touched here.
