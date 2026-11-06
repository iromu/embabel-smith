---
title: "The product phase: from POCs to prod/ modules"
type: "overview"
status: "active"
language: "default"
source_paths:
  - "prod/pom.xml"
  - "prod/embabel-smith-skillsource/README.md"
  - "prod/embabel-smith-runtime/README.md"
  - "prod/embabel-smith-builder/README.md"
  - "prod/embabel-smith-studio/README.md"
  - "openspec/changes/01-product-module-foundation/proposal.md"
updated_at: "2026-11-06"
---

# The product phase

The POCs under `poc/` are **frozen evidence** — each proves one load-bearing claim, nothing more.
On 2026-11-05 the project moved to the **product phase**: a new `prod/` tree of four modules that
turn the proven POC logic into a working product — a CLI builder, a CLI runtime, and (next) a
self-improving studio. The POCs are not deleted; they are the source the product *extracts from*.

## The extract-don't-depend rule

Product modules **copy and generalize** proven POC logic; they must never depend on a `poc-*`
artifact. This is enforced live by a Maven enforcer `bannedDependencies` rule in `prod/pom.xml`
(excluding `com.embabel.smith:poc-*`), inherited by every child. A product module that imports a POC
fails the build. The POCs keep working exactly as before; the product is a clean re-derivation of
their logic against `smith-common` (the format owner) and Embabel directly.

## The four modules

| Module | What it is | Status |
| --- | --- | --- |
| `prod/embabel-smith-skillsource` | Skill sources: `folder:` / `git:` URIs, pack discovery, ref pinning, cache, content hash. Shared by runtime (reads) and builder (publishes). | done (change 02) |
| `prod/embabel-smith-runtime` | The runtime product: `smith run` CLI — ingest packs from a source, run an exported goal, enforce trust tiers, record a JSONL trace. | done (change 03) |
| `prod/embabel-smith-builder` | The builder product: `smith build` CLI — NL description → validated pack → eval → security gate → lifecycle stamp → publish. | done (change 04) |
| `prod/embabel-smith-studio` | The self-improving studio: hosts runtime + builder, the self-improvement loop, REST surface, web UI. | empty skeleton (changes 05–06) |

See [[skill-sources]], [[smith-runtime]], and [[smith-builder]] for each module's behaviour.

## The six changes

The product is built in six numbered OpenSpec changes under `openspec/changes/` (01–06):

1. **01-product-module-foundation** — the `prod/` aggregator + the four skeleton modules + the
   enforcer ban. POCs frozen.
2. **02-skill-sources-git-folder** — the `skillsource` module (folder + git sources, content hash).
3. **03-smith-runtime-product** — the runtime: multi-pack ingest, per-run planner, trust tiers,
   JSONL trace.
4. **04-smith-builder-product** — the builder: the full build pipeline and publish to a source.
5. **05-smith-self-improvement-loop** — the studio's self-improvement loop (the `incubating →
   active` promotion and re-eval cycle that owns the lifecycle transitions).
6. **06-smith-studio-product** — the studio's REST surface and web UI.

Changes 01–04 are implemented and committed; 05 and 06 fill in the `studio` module.

## How it maps to the POCs

- `skillsource` generalizes POC 18's content-hash / drift input and the pack-reading the POCs did
  ad hoc ([[skill-lifecycle]], [[skill-pack-format]]).
- `runtime` extracts POC 1's no-code ingest + run and generalizes it to **multi-pack** (POC 6),
  adds POC 10's trust tiers and a per-run JSONL trace ([[runtime-run]], [[trust-tiers]]).
- `builder` extracts POC 2/3 (build), POC 5 (eval), POC 9 (refine), POC 21 (security gate), and
  POC 18 (lifecycle stamp) into one deterministic pipeline ([[builder-pipeline]],
  [[eval-guided-refinement]], [[skill-security-scanning]]).

The format contract is unchanged — the product reads and writes the same YAML skill-packs the POCs
do ([[skill-pack-format]], [[validate-before-write]]).
