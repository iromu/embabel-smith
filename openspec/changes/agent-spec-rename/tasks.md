# Tasks

## 1. Main spec surgery (openspec/specs/)

- [x] 1.1 Rename the six capability directories: `skill-pack-format` → `agent-spec-format`,
  `skill-runtime` → `agent-spec-runtime`, `skill-builder` → `agent-spec-builder`,
  `skill-sources` → `spec-sources`, `gliner-skill-classification` → `gliner-spec-classification`,
  `small-llm-skill-classification` → `small-llm-spec-classification`; verify with
  `openspec list --specs` (all six new ids resolve)
- [x] 1.2 Delete `skill-pack-runtime/` and `skill-builder-agent/` (subsumed per design.md D3);
  verify `openspec list --specs` shows 11 capabilities
- [x] 1.3 Reword the bodies of all 11 remaining main specs — "skill-pack" → "agent spec",
  "skill" → "spec" (including Purpose lines), keeping requirement headers that contain no
  "skill" verbatim; verify `openspec validate` passes on all 11 specs
- [x] 1.4 Confirm the change's delta specs (2 ADDED in `agent-spec-runtime`, 1 ADDED in
  `agent-spec-builder`) pass `openspec validate` and will merge into the renamed capabilities
  at archive time (not create new files)

## 2. smith-common

- [x] 2.1 Rename `SkillPack` → `AgentSpec`, `SkillPackManifest` → `AgentSpecManifest`,
  `SkillPackReader`/`SkillPackWriter`/`SkillPackValidator` → `AgentSpecReader`/`AgentSpecWriter`/
  `AgentSpecValidator`, `SampleSkillPacks` → `SampleAgentSpecs`; reword javadoc; verify
  `mvn -pl smith-common compile` passes
- [x] 2.2 Rename the tests (`SkillPackRoundTripTest` → `AgentSpecRoundTripTest`,
  `SkillPackValidatorTest` → `AgentSpecValidatorTest`) and update assertions/error strings;
  verify `mvn -pl smith-common test` is green

## 3. embabel-smith-skillsource → embabel-smith-specsource

- [x] 3.1 Rename the module directory and artifactId; update `prod/pom.xml` (module entry),
  root `pom.xml` (`dependencyManagement`), and every `<dependency>` reference; verify
  `mvn -pl prod/embabel-smith-specsource compile` passes
- [x] 3.2 Rename `SkillSource` → `SpecSource`, `FolderSkillSource`/`GitSkillSource` →
  `FolderSpecSource`/`GitSpecSource`, `ResolvedSkillSource`/`ResolvedPack` →
  `ResolvedSpecSource`/`ResolvedSpec`, `SkillSourceException` → `SpecSourceException`;
  reword javadoc ("one-level discovery" stays accurate — it is source resolution); verify
  `mvn -pl prod/embabel-smith-specsource test` is green with renamed tests
  (`FolderSkillSourceTest` → `FolderSpecSourceTest`, `GitSkillSourceTest` → `GitSpecSourceTest`,
  `SkillSourceParseTest` → `SpecSourceParseTest`)

## 4. embabel-smith-runtime

- [x] 4.1 Rename `SkillRunService` → `AgentSpecRunService`, `SkillPackTypeMapper` →
  `AgentSpecTypeMapper`; reword "skill-pack"/"skill source" in javadoc, error messages
  (e.g. "contains no skill-packs" → "contains no agent specs"), and CLI strings; verify
  `mvn -pl prod/embabel-smith-runtime compile` passes
- [x] 4.2 Rename `SkillRunServiceTest` → `AgentSpecRunServiceTest` and
  `SkillPackTypeMapperTest` → `AgentSpecTypeMapperTest`; verify
  `mvn -pl prod/embabel-smith-runtime test` is green

## 5. embabel-smith-builder

- [x] 5.1 Rename `SkillCompiler`/`DefaultSkillCompiler` → `AgentSpecCompiler`/
  `DefaultAgentSpecCompiler`, `DraftToSkillPack` → `DraftToAgentSpec`, `SkillPackRef` →
  `AgentSpecRef`, `SkillSecurityScanner` → `AgentSpecSecurityScanner`, `SkillInvoker` →
  `AgentSpecInvoker`, `AgentSkillInvokerFactory` → `AgentSpecInvokerFactory`,
  `SkillContentHash` → `SpecContentHash`; reword `BuilderService`/`BuilderAgent` javadoc and
  CLI strings; verify `mvn -pl prod/embabel-smith-builder compile` passes
- [x] 5.2 Rename the builder tests (`DraftToSkillPackTest` → `DraftToAgentSpecTest`,
  `SkillSecurityScannerTest` → `AgentSpecSecurityScannerTest`) and update assertions; verify
  `mvn -pl prod/embabel-smith-builder test` is green

## 6. embabel-smith-studio (backend + frontend)

- [x] 6.1 Backend: reword "skill" in DTO javadoc, error strings, and `StudioConfiguration`
  comments; keep endpoint paths (`/api/sources`, `/api/packs`) unchanged; verify
  `mvn -pl prod/embabel-smith-studio compile` passes
- [x] 6.2 Frontend: rename `useSourceSkill.ts` → `useSourceSpec.ts`
  (`SourceSkillState` → `SourceSpecState`), update every pane that imports it, and change UI
  labels "skill" → "spec" / "agent spec"; update `client.test.ts`; verify `npm test` in
  `prod/embabel-smith-studio/frontend` is green
- [x] 6.3 Verify `mvn -pl prod/embabel-smith-studio test` is green

## 7. POC modules

- [x] 7.1 Rename the four module directories + artifactIds: `poc-4-skill-format` →
  `poc-4-agent-spec-format`, `poc-6-skill-composition` → `poc-6-agent-spec-composition`,
  `poc-18-skill-lifecycle` → `poc-18-agent-spec-lifecycle`, `poc-21-skill-security` →
  `poc-21-agent-spec-security`; update `poc/pom.xml` module entries, root `pom.xml`
  `dependencyManagement`, and the two POC dependents (`poc-15-composer-ui`,
  `poc-22-unified-ui`); verify `mvn -q -DskipTests compile` on the full reactor passes
- [x] 7.2 Rename `Skill*` classes to `Spec*`/`AgentSpec*` in the remaining POCs
  (poc-1, 3, 5, 8, 12, 14, 15, 17, 19, 20, 22, 23 — e.g. `SkillRegistry` → `SpecRegistry`,
  `SkillSelector` → `SpecSelector`, `SkillNode` → `SpecNode`, `SkillCatalog`/`SkillCuration` →
  `SpecCatalog`/`SpecCuration`); reword READMEs ("skill shadowing" → "spec shadowing")
  keeping the mermaid-diagram and Conclusion/shared-results sections intact; verify the full
  reactor `mvn test` is green (30 modules)

## 8. Docs, wiki, and top-level files

- [x] 8.1 Reword `README.md`, `BLOCKERS.md`, and the root `pom.xml` `<description>`
  ("skills-first ... skill-packs" → "GOAP agent builder that emits agent specs — declarative
  YAML step definitions"); verify no "skill" remains in those files outside quoted
  historical references
- [x] 8.2 Reword `docs/01`–`docs/04` (including the `docs/03` title
  "embabel-skills-agent-builder-design" → agent-spec wording) and fix the "discovery"
  wording per design.md D6 (source resolution / business-docs loading / build-time spec
  selection); verify `grep -ri skill docs/` returns only intentional hits
- [x] 8.3 Reword the 34 `.wiki/` pages and their cross-links (page renames only where a page
  title contains "skill"); update pages that cite the old spec ids
  (`skill-pack-format` etc.); verify the wiki lint passes
- [x] 8.4 Rename `built-skills/` → `built-specs/` (the pack's internal generated name stays —
  it is content, not vocabulary); verify the runtime still resolves it: `smith run` against
  `folder:./built-specs` lists the goal
- [x] 8.5 Grep audit across the repo: `grep -ril skill` (excluding `node_modules`, `.git`,
  `target`, and documented exceptions such as generated pack names) returns no unexpected
  hits; fix `.idea/` run configs and `run-loop.sh` module-path references if present

## 9. Final verification (integration)

- [x] 9.1 Full reactor build from a clean tree: `mvn clean test` green (32 modules incl. parent
  poms, enforcer ban still effective)
- [x] 9.2 `openspec validate` passes for the change and all 11 main specs;
  `openspec list --specs` shows 11 capabilities, none with "skill" in the id
- [ ] 9.3 Re-publish the wiki to GitHub Pages and verify the published pages use the new
  vocabulary
- [x] 9.4 Commit the change as a single rename-heavy commit (git detects renames); update the
  project memory entries that cite the old vocabulary

## Workflow follow-up

- Archive `agent-spec-rename` after the review requirements are satisfied — the archive
  merges the 3 ADDED delta requirements into `agent-spec-runtime` (→ 10 reqs) and
  `agent-spec-builder` (→ 8 reqs).
- After archiving, verify the inventory: 11 capabilities; `agent-spec-runtime` has 10
  requirements, `agent-spec-builder` has 8.
- The unarchived `smith-studio-process-workspace` change (26/26) is independent — archive it
  whenever convenient; its delta targets `studio-web`, which this change rewords in place but
  does not rename.
