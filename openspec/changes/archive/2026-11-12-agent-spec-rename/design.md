# Design

## Context

See proposal.md for motivation. Current state that shapes the approach:

- The on-disk unit is a directory of Embabel `embabel-agent-spec` YAML: `goal.yml`
  (`stepType: goal` = GoalSpec), `steps/*.yml` (`stepType: action` = PromptedActionSpec),
  `types/*.yml` (DynamicTypeDef), plus a `manifest.yml` that is the only non-Embabel file.
  The runtime ingests it via `YmlStepSpecRepository` + `StepSpecAgentScopeBuilder.deploy()`
  and plans with GOAP. The YAML is already correctly named — only our wrapper vocabulary is wrong.
- 197 `*Skill*` Java files across `smith-common`, `prod/` (4 modules), `poc/` (23 modules);
  13 main spec capabilities (all contain "skill" in id or body); the frontend
  (`useSourceSkill.ts`, pane labels); docs/wiki/README.
- Two POC-era capabilities (`skill-pack-runtime`, `skill-builder-agent`) overlap the product
  capabilities (`skill-runtime`, `skill-builder`) that describe the same implementation.
  User-confirmed decisions: vocabulary **AgentSpec**, scope **everything incl. POC names**,
  overlap resolution **option A (merge)**.

## Goals / Non-Goals

**Goals:**
- Every name in the repo matches reality: the bundle is an *agent spec*, library units and
  sources are *specs*, the builder is a GOAP *agent-spec builder*.
- One spec capability per implementation (the double-spec pairs are merged, option A).
- The repo builds green (full reactor), `openspec validate` passes, and no "skill" remains
  outside the documented exceptions.

**Non-Goals:**
- No behavior change, no on-disk format change, no source-URI grammar change, no API endpoint
  change, no git-history rewrite (renames are new commits; git detects them).
- Not regenerating or renaming the content of existing built packs (a pack's generated name
  like `a-skill-that-explains-a-topic-in-plain-l` is model output, not our vocabulary).

## Decisions

### D1 — Target vocabulary

| Concept | Name |
|---|---|
| On-disk bundle (goal + actions + types + manifest) | agent spec (`AgentSpec`) |
| Individual YAML step definition | step spec (Embabel's own: `StepSpec`/`PromptedActionSpec`/`GoalSpec`) |
| URI → spec directories | spec source (`SpecSource`) |
| Library of agent specs | spec library / spec catalog |
| Build-time selection from a library | spec selection |
| Output directory | `built-specs/` |

Alternatives considered: "StepSpec bundle" (confusing — `StepSpec` in Embabel is one step),
"AgentDefinition" (neutral but loses the link to the `embabel-agent-spec` module the code is
built on). AgentSpec was chosen by the user.

### D2 — Spec surgery: direct edits + minimal delta

The delta spec format has no capability-rename idiom (`RENAMED` operates on requirement
names only; deltas must target existing capability paths and "do not move or rename the
capability"). Therefore:

- **Capability renames + body rewording** are direct edits to `openspec/specs/`, recorded as
  tasks. This is the same out-of-band path OpenSpec documents for Purpose edits.
- **The 3 folded-in guarantees are delta specs** (pure `ADDED`) — the only genuine
  spec-level additions in this change:
  - `specs/agent-spec-runtime/spec.md`: + *Model endpoint is pluggable*,
    + *Deterministic plan structure*
  - `specs/agent-spec-builder/spec.md`: + *Builder runs as a single agent*
- At archive time the deltas merge into the already-renamed main specs. The tasks rename the
  capabilities **before** archive, so the archive only appends; it never creates or deletes a
  capability file.

### D3 — Subsumption table (option A)

`skill-pack-runtime` (5 reqs) → retired into `agent-spec-runtime`:

| Old requirement | Disposition |
|---|---|
| Ingest skill-pack without compilation | subsumed by *Multi-pack ingest* + *Run a goal from a resolved source* |
| Run a plan over the exported goal | subsumed by *Run a goal from a resolved source* |
| Planner is selectable per run | duplicate of the product requirement — dropped |
| Model endpoint is pluggable | **folded in** (ADDED delta) |
| Deterministic structure, model-generated content | **folded in** (ADDED delta) |

`skill-builder-agent` (5 reqs) → retired into `agent-spec-builder`:

| Old requirement | Disposition |
|---|---|
| Compile a description into a draft skill | subsumed by *Build a validated pack from a description* |
| Validate before writing | subsumed (the D5 invariant lives there) |
| Write the skill-pack | subsumed (same requirement) |
| Score and rewrite prompts from failures | subsumed by *Evaluate and refine with a bound* |
| Builder runs as a single agent | **folded in** (ADDED delta) — verified still true: `BuilderAgent` is `@Agent(planner = GOAP)` with goal "a validated pack exists on disk" |

Resulting inventory: 13 capabilities → **11**: `agent-spec-format`, `agent-spec-runtime`
(10 reqs), `agent-spec-builder` (8 reqs), `spec-sources`, `gliner-spec-classification`,
`small-llm-spec-classification`, `product-module-layout`, `remote-embedding-scale`,
`self-improvement`, `studio-web`, `unified-builder-ui`.

### D4 — Rename map (code)

```
prod/embabel-smith-skillsource        -> prod/embabel-smith-specsource
  SkillSource, Folder/GitSkillSource, ResolvedSkillSource/ResolvedPack,
  SkillSourceException                -> SpecSource, Folder/GitSpecSource,
                                         ResolvedSpecSource/ResolvedSpec,
                                         SpecSourceException
smith-common
  SkillPack, SkillPackManifest, SkillPackReader/Writer/Validator, SampleSkillPacks
                                      -> AgentSpec, AgentSpecManifest,
                                         AgentSpecReader/Writer/Validator, SampleAgentSpecs
  (GoalStepSpec, ActionStepSpec, DynamicTypeDef already match Embabel — stay)
prod/embabel-smith-runtime
  SkillRunService, SkillPackTypeMapper -> AgentSpecRunService, AgentSpecTypeMapper
  "skill-pack"/"skill source" in javadoc, error messages, CLI strings -> agent-spec wording
prod/embabel-smith-builder
  SkillCompiler/DefaultSkillCompiler, DraftToSkillPack, SkillPackRef,
  SkillSecurityScanner, SkillInvoker, AgentSkillInvokerFactory, SkillContentHash
                                      -> AgentSpecCompiler/DefaultAgentSpecCompiler,
                                         DraftToAgentSpec, AgentSpecRef,
                                         AgentSpecSecurityScanner, AgentSpecInvoker,
                                         AgentSpecInvokerFactory, SpecContentHash
prod/embabel-smith-studio
  useSourceSkill.ts -> useSourceSpec.ts (SourceSkillState -> SourceSpecState),
  pane references, UI labels "skill" -> "spec" / "agent spec";
  PackInfo/SourceInfo types and endpoint paths already neutral — stay
POC modules (dir + artifactId + poc/pom.xml + root pom dependencyManagement)
  poc-4-skill-format        -> poc-4-agent-spec-format
  poc-6-skill-composition   -> poc-6-agent-spec-composition
  poc-18-skill-lifecycle    -> poc-18-agent-spec-lifecycle
  poc-21-skill-security     -> poc-21-agent-spec-security
  (poc-1/3/5/8/12/14/15/17/19/20/22/23 keep module names; their Skill* classes and
   READMEs rename to Spec*/agent-spec wording — e.g. SkillRegistry -> SpecRegistry,
   "skill shadowing" -> "spec shadowing")
built-skills/                 -> built-specs/
```

### D5 — Maven/build mechanics

- `embabel-smith-skillsource` → `embabel-smith-specsource`: directory, artifactId,
  `prod/pom.xml` module entry, root `pom.xml` `dependencyManagement`, and every
  `<dependency>` reference (runtime, builder, studio, their tests).
- The 4 POC renames touch: module directories, artifactIds, `poc/pom.xml` module entries,
  root `pom.xml` `dependencyManagement`, and the two POC dependents that reference the old
  artifactIds (`poc-15-composer-ui`, `poc-22-unified-ui`).
- The enforcer `bannedDependencies` rule is pattern-based (`com.embabel.smith:poc-*`), so it
  survives the POC artifactId changes untouched — verified by the reactor build.

### D6 — The "discovery" fix

Docs, wiki, and UI stop implying runtime skill discovery. The three real things, each named
for what it does:

1. **Source resolution** (spec source): `folder:`/`git:` URI → find spec directories,
   validate, exclude invalid with warnings. No intelligence.
2. **Business-process discovery** (studio `/api/discover`): load business docs from a path or
   repo URL to feed the generator.
3. **Spec selection** (POCs 8/12/17/19/20/23): a build-time experiment in choosing among a
   library of same-typed specs; never wired into the product runtime — GOAP plans over
   ingested step specs, no selection step.

### D7 — Ordering

1. Main-spec surgery (renames + rewording + deletions) — the contract moves first.
2. `smith-common` (shared model) → `embabel-smith-specsource` → runtime → builder → studio.
3. POC modules (leaves; nothing in prod depends on them — the enforcer bans it).
4. Frontend.
5. Docs / wiki / README / root POM description / `built-skills/`.
6. Verification (full reactor build + tests, `openspec validate`, grep audit, wiki lint).

## Risks / Trade-offs

- [Archive before the rename tasks run] The deltas target `agent-spec-runtime` /
  `agent-spec-builder`, which exist only after the rename tasks. If archived early, the
  archive would create incomplete capability files (2 and 1 requirements respectively)
  alongside the old ones. → Tasks are ordered so renames precede archive; the final
  verification task checks the post-archive inventory (11 capabilities; 10 and 8 requirement
  counts).
- [Unarchived `smith-studio-process-workspace` (26/26)] Its deltas target `studio-web`, which
  this change rewords in place but does not rename — no interaction. It may be archived
  before or after this change; if archived after, its delta merges into the reworded spec.
- [Bulk rename misses a reference] 197 files across 28 Maven modules; a missed reference
  breaks the build only if it is a compile-time name. String-level misses (javadoc, UI text,
  docs) break nothing. → Full reactor build catches the former; a final grep audit
  (`grep -ri skill` minus documented exceptions) catches the latter.
- [Wiki cross-links] 34 pages reference each other; renamed pages break links. → Wiki lint
  after rewording, then re-publish to GitHub Pages.
- [IDE run configs / scripts] `.idea/` and `run-loop.sh` may reference module paths. →
  Included in the grep audit; run configs are local and non-fatal if stale.

## Migration Plan

Single commit (rename-heavy; git detects renames, so history stays readable). Rollback is a
revert of that commit — no data migration exists, so nothing else is needed. The wiki
re-publish is the only external effect and is re-runnable.

## Open Questions

None — vocabulary, scope, and the merge (option A) were all confirmed with the user.
