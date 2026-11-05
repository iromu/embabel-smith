# Proposal

## Why

The POC phase is complete (21 POCs green, POC 22 nearly done): every capability is proven, but each lives inside a standalone POC launcher under `poc/`. The product — a runtime that runs skills from a git repo or folder, a standalone builder, and a self-improving runtime+builder — has nowhere to live. This change creates the production module skeleton and the dependency rules that keep the product clean while the POCs stay frozen as evidence.

## What Changes

- New `prod/` Maven aggregator (`embabel-smith-prod`), mirroring the `poc/` aggregator, registered in the root POM.
- Four empty-but-valid module skeletons under `prod/`:
  - `prod/embabel-smith-skillsource` — skill sources (git + folder), pack discovery, content hashing (filled by change 02).
  - `prod/embabel-smith-runtime` — the runtime product (filled by change 03).
  - `prod/embabel-smith-builder` — the standalone builder product (filled by change 04).
  - `prod/embabel-smith-studio` — the self-improving runtime+builder web product (filled by changes 05–06).
- Root POM `dependencyManagement` entries for the four new modules.
- Enforced dependency direction: production modules may depend on `smith-common`, Embabel starters, and sibling `prod/` modules, but MUST NOT depend on any `poc-*` app module (enforced with `maven-enforcer-plugin` banned dependencies).
- POC freeze: `poc/` modules are frozen evidence — product changes extract logic from them (copy + generalize) rather than depending on them or modifying them.

## Capabilities

### New Capabilities

- `product-module-layout`: the production module layout — the `prod/` aggregator, the four module boundaries, the dependency-direction rule, and the extract-don't-depend / POCs-frozen conventions.

### Modified Capabilities

(none)

## Impact

- `pom.xml` (root): new `<module>prod</module>`, four `dependencyManagement` entries, enforcer plugin in `pluginManagement`.
- New files: `prod/pom.xml`, `prod/embabel-smith-skillsource/pom.xml`, `prod/embabel-smith-runtime/pom.xml`, `prod/embabel-smith-builder/pom.xml`, `prod/embabel-smith-studio/pom.xml` (+ minimal `src/` and `README.md` per module).
- No POC module is modified. No runtime behavior changes; the offline test baseline stays green.
