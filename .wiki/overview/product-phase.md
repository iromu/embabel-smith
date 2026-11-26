---
title: "The product phase: from POCs to prod/ modules"
type: "overview"
status: "active"
language: "default"
source_paths:
  - "prod/pom.xml"
  - "pom.xml"
  - "prod/embabel-smith-specsource/README.md"
  - "prod/embabel-smith-runtime/README.md"
  - "prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/core/RuntimeCore.java"
  - "prod/embabel-smith-builder/README.md"
  - "prod/embabel-smith-studio/README.md"
  - "docs/05-architecture.md"
  - "docs/06-spec-sources.md"
  - "docs/07-runtime.md"
  - "docs/08-builder.md"
  - "docs/09-studio.md"
  - "docs/10-self-improvement.md"
  - "docs/12-backlog.md"
updated_at: "2026-11-26"
---

# The product phase

The POCs under `poc/` are **frozen evidence** — each proves one load-bearing claim, nothing more.
On 2026-11-05 the project moved to the **product phase**: a new `prod/` tree that turns the proven POC
logic into a working product — a CLI builder, a CLI runtime, and a self-improving studio. It began as
four modules and has since grown to **six**. The POCs are not deleted; they are the source the product
*extracts from*.

## The extract-don't-depend rule

Product modules **copy and generalize** proven POC logic; they must never depend on a `poc-*`
artifact. This is enforced live by a Maven enforcer `bannedDependencies` rule in `prod/pom.xml`
(execution `ban-poc-dependencies`, excluding `org.iromu.embabel.smith:poc-*` — `prod/pom.xml:47`),
inherited by every child. A product module that imports a POC fails the build. The POCs keep working
exactly as before; the product is a clean re-derivation of their logic against `smith-common` (the
format owner) and Embabel directly.

The ban was verified live (change 01, task 3.3, re-verified 2026-11-08): with a temporary
`poc-1-no-code-runtime` dependency added to `embabel-smith-specsource`, the build failed at the
`validate` phase with `BannedDependencies` naming the banned artifact. That captured console output is
no longer reproducible word for word — the groupId was renamed to `org.iromu.embabel.smith`
(`pom.xml:7`) and the ban was repointed with it, so the failing coordinate today reads
`org.iromu.embabel.smith:poc-1-no-code-runtime:jar:0.1.0-SNAPSHOT`, not the
`com.embabel.smith:` form this page used to quote.

The temporary dependency was removed afterwards; the reactor is green again.

## The six modules

`prod/pom.xml` now lists six children. The last two arrived after the numbered phase, which is why
the original account of "four modules" no longer matches the tree. The durable module map is
[`docs/05-architecture.md`](../../docs/05-architecture.md) — `README.md` no longer carries one.

| Module | What it is | Status |
| --- | --- | --- |
| `prod/embabel-smith-specsource` | Spec sources: `folder:` / `git:` URIs, pack discovery, ref pinning, cache, content hash. Shared by runtime (reads) and builder (publishes). | done (change 02) |
| `prod/embabel-smith-runtime` | The runtime product: `smith run` CLI — ingest packs from a source, run an exported goal, enforce trust tiers, refuse a pack the lifecycle retired at ingest (`RuntimeCore.java:113` — it is not deployed, and a source left empty by that refusal fails), record a JSONL trace. | done (change 03) |
| `prod/embabel-smith-builder` | The builder product: `smith build` CLI — NL description → two candidates (single pack / multi-agent process), or a `SKILL.md` with `--target skill` → eval → security gate → lifecycle stamp → publish the winner. `smith build` also takes `--flow` with four values — `java` (the shipped default), `pack`, `agentic` and `harness` — that differ only in who issues the generation call, three of them the builder's own packs under `src/main/resources/specs/` [`prod/embabel-smith-builder/README.md`](../../prod/embabel-smith-builder/README.md), [[smith-builder]]). | done (change 04, extended by the two builder changes) |
| `prod/embabel-smith-studio` | The self-improving studio: the self-improvement loop (change 05) exposed as a web product — REST surface + Vue UI + loop scheduling (change 06). | done (change 06) |
| `prod/embabel-smith-a2a-starter` | The A2A surface as a starter library — five operations over protocol 0.3, with beans conditional on `smith.a2a.enabled` and off by default, so depending on it opens no port. `prod/embabel-smith-studio` is its servlet host when a run sets that flag; the plain CLI cannot host it, because the starter depends on the runtime. An A2A run is the same run `smith run` makes. | added later ([[a2a-endpoint]]) |
| `prod/embabel-smith-skillevaluator` | The Tier 1 skill validator: a static, zero-model check pipeline the builder's `inspect` gate is the only production consumer of. | added later ([[skill-output-target]]) |

See [[spec-sources]], [[smith-runtime]], [[smith-builder]], and [[smith-studio]] for each module's
behaviour.

## The six changes

The product is built in six numbered OpenSpec changes (01–06). Their folders and the main specs they
created were consolidated into `docs/` on 2026-11-15, so the paths below no longer exist — the durable
statement of each is the linked documentation page:

1. **01-product-module-foundation** — the `prod/` aggregator + the four skeleton modules + the
   enforcer ban. POCs frozen. **Archived 2026-11-08**; now [`docs/05-architecture.md`](../../docs/05-architecture.md).
2. **02-spec-sources-git-folder** — the `specsource` module (folder + git sources, content hash).
   **Archived 2026-11-08**; now [`docs/06-spec-sources.md`](../../docs/06-spec-sources.md) ([[spec-sources]]).
3. **03-smith-runtime-product** — the runtime: multi-pack ingest, per-run planner, trust tiers,
   JSONL trace. **Archived 2026-11-09**; now [`docs/07-runtime.md`](../../docs/07-runtime.md) ([[smith-runtime]]).
4. **04-smith-builder-product** — the builder: the full build pipeline and publish to a source.
   **Archived 2026-11-09**; now [`docs/08-builder.md`](../../docs/08-builder.md) ([[smith-builder]]).
5. **05-smith-self-improvement-loop** — the self-improvement loop (the `incubating →
   active` promotion and re-eval cycle that owns the lifecycle transitions). **Archived
   2026-11-09**; now [`docs/10-self-improvement.md`](../../docs/10-self-improvement.md) ([[smith-studio]]).
6. **06-smith-studio-product** — the studio's REST surface and web UI (thin controllers over the
   production cores, a Vue 3 + Quasar front end, and loop scheduling). **Archived 2026-11-09**; now
   [`docs/09-studio.md`](../../docs/09-studio.md). See [[smith-studio-web]].

All six changes (01–06) are implemented, committed, and archived. The product phase is **not**
finished, though — the work continued past the numbered set under its own change names (see below).

## After the six numbered changes

Follow-on work kept using the same OpenSpec process but dropped the numbering. The six changes below
landed and were archived in that window; their folders were consolidated into `docs/` on 2026-11-15,
and so were the main specs they created — the "new main spec" names in the table are lineage, not live
paths.

| Change | Landed | What it did |
| --- | --- | --- |
| `2026-11-12-agent-spec-rename` | 2026-11-12 | Renamed the vocabulary from "skill-pack" to **agent spec** across the repo — the unit is a spec, not a skill. Renamed the `skillsource` module to `specsource` and the `skill-*` main specs to `agent-spec-*` / `spec-sources`. |
| `2026-11-13-smith-studio-postgres-storage` | 2026-11-13 | Postgres-backed pack/discovery/trace storage with a hot-swappable binding → the `studio-storage` contract ([[smith-studio-storage]]). |
| `2026-11-14-builder-generation-engine` | 2026-11-14 | Moved generation out of the studio into the builder as the `AgentSpecGenerator` / `GenerationTechnique` seam, and added the `--technique` CLI option ([[smith-builder]], [[generation-approaches]]). |
| `2026-11-14-smith-studio-process-workspace` | 2026-11-14 | The Author-a-process home workspace and the Compose pane ([[studio-process-workspace]], [[studio-compose-editor]]). |
| `2026-11-14-builder-skill-target` | 2026-11-14 | `--target skill`: a second build output that emits a `SKILL.md` folder gated on with/without-skill lift → the skill build-gate contract ([[skill-output-target]]). |
| `2026-11-15-builder-process-target` | 2026-11-15 | The **two-candidate build** — one description, a single-agent spec and a multi-agent process, both scored, one recommended ([[two-candidate-build]]). |

The pack flows also moved where the improvement loop's mutable boundary sits. Of the builder's eight
`.jinja` prompts, five serve generation — `process_to_flow`, `process_to_flow_multi`,
  `business_process_discovery`,
`generate_skill`, `generate_evals` — and are the surface an improvement loop rewrites; `generate_skill`
is now on that mutable side and asks the model to author `scripts/*.js` beside the `SKILL.md`. The other
three are the scoring instrumentation — `grade_eval` plus the two eval arms `agent_baseline` and
`agent_with_skill` — and stay off the mutable surface **by placement**: they are classpath resources
under `src/main/resources/prompts`, while the builder's flow also lives as packs under
`prod/embabel-smith-builder/src/main/resources/specs/` — classpath resources too now, which is the half
that moved. The boundary is directory rather than class membership: `prompts/` holds the referee and
`specs/` the surface an improvement loop rewrites, and each pack-driving generator names its pack as
`core/BuilderPackPaths.directory(<pack>)` over `smith-common`'s `SpecFolders.classpath` instead of a
`Path.of` resolved against the working directory. So
the split is by directory and by kind of surface at once. That
placement is why `skill/ScorerPromptBoundaryTest` can hold the line at all — a mutation
confined to pack files has no route to the referee, and what the test still has to catch is someone
copying the referee's own wording into a step prompt.

**`prod-model-layer` is finished and settled.** It unified the production modules' model layer on
Embabel's `Ai`, deleting the hand-rolled OpenAI-compatible callers the POC-era code carried into
`prod/`. The rule is now stated in [`docs/05-architecture.md`](../../docs/05-architecture.md)
("Every model call under `prod/` goes through Embabel's `Ai` layer"), enforced by two
`NoDirectModelRequestTest` guards in the builder and the studio. See [[model-calls-go-through-ai]].

## What the contract looks like now

There is no specs tree to read any more. Archiving with plain `openspec archive` regenerates capability
specs under `openspec/specs/` as it promotes each change's delta into them, so after the 2026-11-15 purge
the trees had refilled to 140 files — 25 capability specs and 20 archived folders, nothing active — and
`docs/` absorbed and deleted them a second time on 2026-11-25. At HEAD `openspec/` holds only
`config.yaml` and two `.gitkeep` placeholders; the check for whether another purge is owed is
`find openspec/changes openspec/specs -type f | wc -l`, where 3 is clean.
[`docs/12-backlog.md`](../../docs/12-backlog.md) §Re-creating a planning change owns that account,
including why the workflow survives the deletion: `make setup` still installs the OpenSpec CLI, so
starting a change is still valid and the capability specs it would edit are these `docs/` pages.

So the capability names this page used to enumerate are lineage, not paths: they are the promoted
requirements of changes that shipped, carried now by the `docs/` pages they were folded into, where they
overlap the reference prose rather than replacing it. `docs/` remains the durable reference and the code
outranks both where they disagree. Nothing remains open — the four boxes that were still unchecked when
the trees went are recorded in [`docs/12-backlog.md`](../../docs/12-backlog.md) §Pending right now, and
none of them is code.

## How it maps to the POCs

- `specsource` generalizes POC 18's content-hash / drift input and the pack-reading the POCs did
  ad hoc ([[spec-lifecycle]], [[agent-spec-format]]).
- `runtime` extracts POC 1's no-code ingest + run and generalizes it to **multi-pack** (POC 6),
  adds POC 10's trust tiers and a per-run JSONL trace ([[runtime-run]], [[trust-tiers]]).
- `builder` extracts POC 2/3 (build), POC 5 (eval), POC 9 (refine), POC 21 (security gate), and
  POC 18 (lifecycle stamp) into one deterministic pipeline ([[builder-pipeline]],
  [[eval-guided-refinement]], [[spec-security-scanning]]).
- `studio` runs the self-improvement loop over the runtime's traces: it detects failure / regression
  / drift, refines the failing steps, gates the candidate (validate → security → re-eval → strict
  baseline), and republishes through the builder's publishers ([[smith-studio]], [[spec-lifecycle]]).
  The engine behind that bullet moved into the builder on 2026-11-21 (`builder/improve`), so the studio
  now drives the loop rather than containing it; nothing about the loop's behaviour changed, and this
  page's account of the original change 05 split still describes what the code does.

The format contract is unchanged — the product reads and writes the same YAML agent specs the POCs
do ([[agent-spec-format]], [[validate-before-write]]).
