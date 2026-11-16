---
title: "The product phase: from POCs to prod/ modules"
type: "overview"
status: "active"
language: "default"
source_paths:
  - "prod/pom.xml"
  - "prod/embabel-smith-specsource/README.md"
  - "prod/embabel-smith-runtime/README.md"
  - "prod/embabel-smith-builder/README.md"
  - "prod/embabel-smith-studio/README.md"
  - "openspec/changes/archive/2026-11-08-01-product-module-foundation/proposal.md"
  - "openspec/specs/product-module-layout/spec.md"
  - "openspec/specs/spec-sources/spec.md"
  - "openspec/specs/agent-spec-runtime/spec.md"
  - "openspec/specs/agent-spec-builder/spec.md"
  - "openspec/specs/agent-spec-process/spec.md"
  - "openspec/specs/agent-skill-builder/spec.md"
  - "openspec/specs/self-improvement/spec.md"
  - "openspec/specs/studio-web/spec.md"
  - "openspec/specs/studio-storage/spec.md"
  - "openspec/changes/prod-model-layer/tasks.md"
updated_at: "2026-11-15"
---

# The product phase

The POCs under `poc/` are **frozen evidence** — each proves one load-bearing claim, nothing more.
On 2026-11-05 the project moved to the **product phase**: a new `prod/` tree of four modules that
turn the proven POC logic into a working product — a CLI builder, a CLI runtime, and a
self-improving studio. The POCs are not deleted; they are the source the product *extracts from*.

## The extract-don't-depend rule

Product modules **copy and generalize** proven POC logic; they must never depend on a `poc-*`
artifact. This is enforced live by a Maven enforcer `bannedDependencies` rule in `prod/pom.xml`
(excluding `com.embabel.smith:poc-*`), inherited by every child. A product module that imports a POC
fails the build. The POCs keep working exactly as before; the product is a clean re-derivation of
their logic against `smith-common` (the format owner) and Embabel directly.

The ban was verified live (change 01, task 3.3): with a temporary `poc-1-no-code-runtime` dependency
added to `embabel-smith-specsource`, the build fails at the `validate` phase naming the banned
artifact (2026-11-08 re-verification):

```
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-enforcer-plugin:3.6.3:enforce
        (ban-poc-dependencies) on project embabel-smith-specsource:
[ERROR] Rule 0: org.apache.maven.enforcer.rules.dependency.BannedDependencies failed with message:
[ERROR] com.embabel.smith:embabel-smith-specsource:jar:0.1.0-SNAPSHOT
[ERROR]    com.embabel.smith:poc-1-no-code-runtime:jar:0.1.0-SNAPSHOT <--- banned via the exclude/include list
```

The temporary dependency was removed afterwards; the reactor is green again.

## The four modules

| Module | What it is | Status |
| --- | --- | --- |
| `prod/embabel-smith-specsource` | Spec sources: `folder:` / `git:` URIs, pack discovery, ref pinning, cache, content hash. Shared by runtime (reads) and builder (publishes). | done (change 02) |
| `prod/embabel-smith-runtime` | The runtime product: `smith run` CLI — ingest packs from a source, run an exported goal, enforce trust tiers, record a JSONL trace. | done (change 03) |
| `prod/embabel-smith-builder` | The builder product: `smith build` CLI — NL description → two candidates (single pack / multi-agent process), or a `SKILL.md` with `--target skill` → eval → security gate → lifecycle stamp → publish the winner. | done (change 04, extended by the two builder changes) |
| `prod/embabel-smith-studio` | The self-improving studio: the self-improvement loop (change 05) exposed as a web product — REST surface + Vue UI + loop scheduling (change 06). | done (change 06) |

See [[spec-sources]], [[smith-runtime]], [[smith-builder]], and [[smith-studio]] for each module's
behaviour.

## The six changes

The product is built in six numbered OpenSpec changes under `openspec/changes/` (01–06):

1. **01-product-module-foundation** — the `prod/` aggregator + the four skeleton modules + the
   enforcer ban. POCs frozen. **Archived 2026-11-08**; its spec is now the main spec
   `openspec/specs/product-module-layout/spec.md`.
2. **02-spec-sources-git-folder** — the `specsource` module (folder + git sources, content hash).
   **Archived 2026-11-08**; its spec is now the main spec `openspec/specs/spec-sources/spec.md`.
3. **03-smith-runtime-product** — the runtime: multi-pack ingest, per-run planner, trust tiers,
   JSONL trace. **Archived 2026-11-09**; its spec is now the main spec
   `openspec/specs/agent-spec-runtime/spec.md`.
4. **04-smith-builder-product** — the builder: the full build pipeline and publish to a source.
   **Archived 2026-11-09**; its spec is now the main spec
   `openspec/specs/agent-spec-builder/spec.md`.
5. **05-smith-self-improvement-loop** — the studio's self-improvement loop (the `incubating →
   active` promotion and re-eval cycle that owns the lifecycle transitions). **Archived
   2026-11-09**; its spec is now the main spec
   `openspec/specs/self-improvement/spec.md`.
6. **06-smith-studio-product** — the studio's REST surface and web UI (the loop as a Spring Boot
   app: thin controllers over the production cores, a Vue 3 + Quasar front end, and loop
   scheduling). **Archived 2026-11-09**; its spec is now the main spec
   `openspec/specs/studio-web/spec.md`. See [[smith-studio-web]].

All six changes (01–06) are implemented, committed, and archived. The product phase is **not**
finished, though — the work continued past the numbered set under its own change names (see below).

## After the six numbered changes

Follow-on work kept using the same OpenSpec process but dropped the numbering. Under
`openspec/changes/archive/`:

| Change | Landed | What it did |
| --- | --- | --- |
| `2026-11-12-agent-spec-rename` | 2026-11-12 | Renamed the vocabulary from "skill-pack" to **agent spec** across the repo — the unit is a spec, not a skill. Renamed the `skillsource` module to `specsource` and the `skill-*` main specs to `agent-spec-*` / `spec-sources`. |
| `2026-11-13-smith-studio-postgres-storage` | 2026-11-13 | Postgres-backed pack/discovery/trace storage with a hot-swappable binding → new main spec `studio-storage` ([[smith-studio-storage]]). |
| `2026-11-14-builder-generation-engine` | 2026-11-14 | Moved generation out of the studio into the builder as the `AgentSpecGenerator` / `GenerationTechnique` seam, and added the `--technique` CLI option ([[smith-builder]], [[generation-approaches]]). |
| `2026-11-14-smith-studio-process-workspace` | 2026-11-14 | The Author-a-process home workspace and the Compose pane ([[studio-process-workspace]], [[studio-compose-editor]]). |
| `2026-11-14-builder-skill-target` | 2026-11-14 | `--target skill`: a second build output that emits a `SKILL.md` folder gated on with/without-skill lift → new main spec `agent-skill-builder` ([[skill-output-target]]). |
| `2026-11-15-builder-process-target` | 2026-11-15 | The **two-candidate build** — one description, a single-agent spec and a multi-agent process, both scored, one recommended → new main spec `agent-spec-process` ([[two-candidate-build]]). |

Still under `openspec/changes/` (not yet archived): **`prod-model-layer`** — unifies the production
modules' model layer on Embabel's `Ai`, deleting the three hand-rolled OpenAI-compatible callers the
POC-era code carried into `prod/`. As of 2026-11-15 all 10 of its tasks are checked off and the code
is in `prod/`; only the archive step is outstanding, so its delta (against the existing
`product-module-layout` spec) is not yet folded into `openspec/specs/`. See
[[model-calls-go-through-ai]].

The current contract set is **14 main specs** under `openspec/specs/`: `agent-skill-builder`,
`agent-spec-builder`, `agent-spec-format`, `agent-spec-process`, `agent-spec-runtime`,
`gliner-spec-classification`, `product-module-layout`, `remote-embedding-scale`,
`self-improvement`, `small-llm-spec-classification`, `spec-sources`, `studio-storage`, `studio-web`,
`unified-builder-ui`.

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

The format contract is unchanged — the product reads and writes the same YAML agent specs the POCs
do ([[agent-spec-format]], [[validate-before-write]]).
