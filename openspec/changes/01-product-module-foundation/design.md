# Design

## Layout

```
embabel-smith/
├── smith-common/                (existing — format owner, unchanged)
├── prod/                        NEW aggregator: embabel-smith-prod (packaging pom)
│   ├── embabel-smith-skillsource/   skill sources: git + folder, discovery, content hash
│   ├── embabel-smith-runtime/       deliverable 1: run skills from a source (+ traces)
│   ├── embabel-smith-builder/       deliverable 3: standalone builder (build → publish)
│   └── embabel-smith-studio/        deliverable 2: runtime + builder + self-improvement + UI
└── poc/                         frozen evidence — no changes
```

## Decisions

### `prod/` aggregator (mirrors `poc/`)
The POC reorg (2026-11-01) nested the POCs under `poc/pom.xml` once the count grew past a handful. The product will grow the same way (three deliverables + shared skill-source + later additions). A `prod/` aggregator keeps the root POM flat and makes "product vs evidence" visually obvious. Alternative rejected: flat top-level `smith-*` modules — works, but loses the evidence/product separation at a glance.

### Extract, don't depend
Product modules copy-and-generalize proven POC logic instead of depending on `poc-*` app modules. Rationale:
- POC modules are Spring Boot launchers (fat: web starters, launchers, demo mains). Pulling them into a product's dependency tree drags launchers and demo config into the product.
- POCs are frozen evidence; depending on them would make the product's fate entangled with files that must never change.
- Extraction is cheap: the POC logic is already plain classes (e.g. `SkillRunner`, `BuilderAgent`) sitting next to thin launchers.

Enforcement: `maven-enforcer-plugin` with `bannedDependencies` on the `prod/` aggregator POM — ban the `com.embabel.smith:poc-*` groupId:artifactId pattern for all child modules. A banned dependency fails the build with the artifact named.

### Module boundaries
- `embabel-smith-skillsource`: the only shared new piece — both the runtime (reads packs from a source) and the builder (publishes packs into a source) need it. Contains no Embabel agent code, only file/git logic; fully offline-testable (git tests use local bare repos).
- `embabel-smith-runtime`: runtime core (ingest + plan + run, multi-pack, trust gate) + trace emission + CLI. Non-launcher packages are plain classes so `embabel-smith-studio` reuses them.
- `embabel-smith-builder`: build core (compile → validate → write → eval → refine, security gate, lifecycle metadata) + publish (folder / git commit) + CLI.
- `embabel-smith-studio`: depends on all three; hosts the self-improvement loop (change 05), the REST surface, and the UI (change 06).

### Skeletons are empty but valid
Each skeleton gets: POM (correct parent, dependencies its fill-change will need — kept minimal for now), empty source trees, and a README stating which change fills it. Empty modules keep the reactor green and let later changes land independently.

## Root POM changes

- `<modules>`: add `<module>prod</module>`.
- `dependencyManagement`: add the four `com.embabel.smith:embabel-smith-*` artifacts at `${project.version}`.
- `pluginManagement`: add `maven-enforcer-plugin` (version property), configured in `prod/pom.xml` with the banned `poc-*` pattern.

## Sequencing

This change is the base for 02–06. It lands first and alone; each later change fills exactly one (or two, for 05→06) of the skeletons and adds no new top-level structure.
