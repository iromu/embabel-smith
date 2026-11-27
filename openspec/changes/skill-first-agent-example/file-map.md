# File map

Draft of what the change touches. A guess, refined by the apply phase — the paths are real, the class
names are proposals.

Legend: `+` created · `~` modified · `·` test or note only · `=` deliberately untouched

```
embabel-smith/
├── pom.xml                                                    ~  dependencyManagement for the new artifacts
├── Makefile                                                   =  build/test/live targets already cover a new module
├── prod/
│   ├── pom.xml                                                ~  <module>embabel-smith-tools</module>; the POC ban is inherited, untouched
│   │
│   ├── embabel-smith-tools/                                   +  the addon aggregator, no code of its own
│   │   ├── pom.xml                                            +  parent; inherits the enforcer ban from prod/pom.xml
│   │   └── embabel-smith-tools-coding/                        +  the first family
│   │       ├── pom.xml                                        +  depends on embabel-agent-api and spring-boot-autoconfigure only
│   │       ├── README.md                                      +  roles it registers, each one's default and switch, the root, what it refuses
│   │       └── src/
│   │           ├── main/java/com/embabel/smith/tools/coding/
│   │           │   ├── CodingToolRoles.java                   +  the role names: "file", "shell"
│   │           │   ├── CodingToolsAutoConfiguration.java      +  one bean per group, @ConditionalOnProperty with matchIfMissing at its documented default
│   │           │   ├── CodingToolsProperties.java             +  per-group switches and defaults, expose map, root, writes switch, timeout, cap
│   │           │   ├── ToolGroupCatalog.java                  +  capability entry to factory call: FileTools.readOnly/readWrite, PatternSearch, CommandTool
│   │           │   ├── CodingToolGroups.java                  +  publishes read/write/edit/search as the "file" group
│   │           │   ├── CommandTool.java                       +  the authored shell tool: bash -lc, cwd, timeout, exit code
│   │           │   ├── ToolGroupFactory.java                  +  the signature-resolved ToolGroupMetadata recipe
│   │           │   └── PathBoundaries.java                    +  what upstream's resolveAndValidateFile leaves out: symlink, the message naming the root, any method that reaches plain resolvePath
│   │           ├── main/resources/META-INF/spring/
│   │           │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports  +
│   │           └── test/java/.../coding/
│   │               ├── CodingToolGroupsTest.java              +  a booted context resolves "file" with nothing configured, and lists the tools
│   │               ├── CodingAddonEnabledTest.java            +  default / switched off / switched on / switch naming an absent group: four bean sets
│   │               ├── ExposedGroupsTest.java                 +  operator-named role, unknown entry refused, class name refused, MCP role collision reported
│   │               ├── WriteSwitchTest.java                   +  nothing configured: reads offered, no write tool anywhere; writes on: they appear
│   │               ├── CommandToolTest.java                   +  exit code, timeout kill, truncation, escaping cwd
│   │               ├── FileConfinementTest.java               +  traversal, absolute, symlink all refuse and name the root
│   │               ├── EditSemanticsTest.java                 +  unique, absent, ambiguous
│   │               └── SearchBoundsTest.java                  +  cap reached is stated, not silent
│   │
│   ├── embabel-smith-runtime/
│   │   ├── pom.xml                                            ~  declares the coding addon and embabel-agent-skills (builder-only today); file resolves with no property, shell does not
│   │   ├── src/main/resources/application.yml                 ~  states the per-group defaults, the root, the script engine (none), the guardrail properties; switches nothing on or off here
│   │   └── src/
│   │       ├── main/java/com/embabel/smith/runtime/
│   │       │   ├── core/RuntimeCore.java                      ~  one line at the scope-builder seam uses the wrapper; passes configured ProcessOptions where it passes DEFAULT today
│   │       │   ├── core/ReferenceAwareStepSpecRepository.java +  wraps findAll(); emit(ctx) delegates with ctx.copy(..., refs)
│   │       │   ├── core/PackSkills.java                       +  Skills per agent definition plus the folder's shared set; the reading surface built with SkillFilter.WITHOUT_SCRIPTS
│   │       │   ├── core/PackReferences.java                   +  the carried set: skills/ folders plus an optional references.yml (directory, document, brief), built through the platform's own reference types
│   │       │   ├── core/ScriptEngineConfig.java               +  smith.tools.scripts.engine → none / the platform's process, docker, podman engines; the process one via confinedTo(root); a container choice with no daemon refuses at start-up
│   │       │   ├── core/RunGuardrails.java                    +  one guardrail class, registered through the platform's global registry by property; bounds come from ProcessOptions, not here
│   │       │   ├── core/RunOutcome.java                       ~  carries what was offered, what failed to resolve, and a blocked-for-decision stop
│   │       │   ├── core/RunReport.java                        +  the lines a reader sees: each role and how it arrived, refused skills, the script switch and engine, the planner that actually planned
│   │       │   ├── core/RunPosture.java                       +  the pre-run print: tier roles, lifecycle exclusions, guardrails and error policy, budget, tool-loop ceiling, tool-not-found policy
│   │       │   └── cli/RunCommand.java                        ~  prints the posture, then the report
│   │       └── test/java/com/embabel/smith/runtime/
│   │           ├── core/RuntimeCoreTest.java                  ·  unchanged cases stay green: the no-skills regression claim, and ProcessOptions defaults identical to before
│   │           ├── core/ReferenceAwareStepSpecRepositoryTest.java +  the delegate receives the references in its context
│   │           ├── core/PackSkillsTest.java                   +  own, shared, shadowing, refused folder named
│   │           ├── core/PackReferencesTest.java               +  directory/document/brief resolve; a fourth kind refused; dangling name refused; prefix collision refused
│   │           ├── core/ScriptExecutionTest.java              +  off by default offers nothing; engine named; non-zero exit returned as data; sleep past the limit stopped and later steps run; runaway output truncated and stated; path outside the root refused
│   │           ├── core/ScriptSurfaceTest.java                +  WITHOUT_SCRIPTS on the reading step, mutation-checked by removing the filter
│   │           ├── core/GuardrailWiringTest.java              +  a blocking intervention is an intervention, retries visible, not slow steps
│   │           ├── core/BoundTerminationTest.java             +  a run ended by a budget or the tool-loop ceiling names the bound
│   │           ├── core/BlockedForDecisionTest.java           +  the stop carries question, evidence, continuation command; nothing beyond the block executed
│   │           ├── core/RunReportTest.java                    +  unresolved role named; the script switch state stated with no tier set; the planner named
│   │           ├── core/ResolvedToolGroupsTest.java           +  the pinned default set from a booted runtime, the MCP flip, and the builder's six roles
│   │           ├── core/PlannerReportTest.java                +  SUPERVISOR reports the GOAP algorithm as the one that planned; a run flag overrides a pack default and says so
│   │           └── core/LiveSkillReferenceTest.java           +  @Tag("live"): offered= before called=
│   │
│   ├── embabel-smith-studio/                                  ·  no behavior of its own changes
│   │   ├── src/main/java/com/embabel/smith/studio/rest/dto/StepInfo.java    ~  a step now reports its references
│   │   ├── src/main/java/com/embabel/smith/studio/rest/TrustController.java ·  declared-role response already covers the gate
│   │   └── frontend/src/
│   │       ├── compose/model.ts                               ~  the step shape round-trips references
│   │       ├── panes/ComposePane.vue                          ~  references visible beside toolGroups
│   │       └── __tests__/compose.model.test.ts                ·  the YAML round-trip case
│   │
│   ├── embabel-smith-a2a-starter/
│   │   └── src/.../SpecSourceSkills.java                      ·  code unchanged; new case: a card filter against a registered group
│   │
│   ├── embabel-smith-builder/                                 =  its PublishedSkillHost and validate group stay as they are
│   ├── embabel-smith-specsource/                              =
│   └── embabel-smith-skillevaluator/                          =
│
├── smith-common/
│   └── src/
│       ├── main/java/com/embabel/smith/common/
│       │   ├── model/ActionStepSpec.java                      ~  + references component, null-to-empty like toolGroups
│       │   ├── model/AgentSpec.java                           ~  the layout javadoc gains skills/ and references.yml
│       │   ├── io/AgentSpecWriter.java                        ~  serializes references; skills are files, not model data
│       │   └── validation/AgentSpecValidator.java             ~  skills layout, the dangling-reference code, an unknown reference kind, a name pair that sanitizes to one prefix, a references: on a goal step, and an unknown step key
│       └── test/java/com/embabel/smith/common/
│           ├── io/AgentSpecRoundTripTest.java                 ·  references survive write-read-write
│           └── validation/AgentSpecValidatorTest.java          ·  own / shared / shadow / misspelled / no-skills-dir control; each new check mutation-checked both ways, incl. an invented per-step approval key
│
├── agent-specs/
│   ├── coding-agent/                                          +  the main example, pure data
│   │   ├── skills/repo-map/SKILL.md                           +  folder-shared skill
│   │   ├── survey/{manifest.yml,goal.yml,steps/locate.yml,types/}   +
│   │   ├── change/{manifest.yml,goal.yml,steps/patch.yml,types/}    +
│   │   ├── change/skills/patch-notes/SKILL.md                 +  definition-local, proves the shadow rule
│   │   ├── verify/{manifest.yml,goal.yml,steps/check.yml,types/}    +
│   │   └── (no process.yml — a folder of definitions, not an ordered agents-pack)
│   ├── skill-interpreter/                                     +  the generic pack, pure data, its own source
│   │   ├── skills/<one skill>/SKILL.md                        +  what it carries; the throwaway skill for 9.3 is authored separately
│   │   ├── references.yml                                     +  optional: name, kind, path — skills folder, directory, document or brief
│   │   ├── orient/{manifest.yml,goal.yml,steps/orient.yml,types/}  +  RunBrief in, nothing else out
│   │   ├── select/  ... + goal.yml, steps/select.yml          +  activates one skill -> SkillDirective (tools, scripts, resources, decisions)
│   │   ├── plan/    ... + goal.yml, steps/plan.yml            +  ActionPlan plus the criteria the skill itself stated
│   │   ├── act/     ... + goal.yml, steps/act.yml             +  toolGroups: [file]; shell only where the tier allows -> WorkLog
│   │   ├── script/  ... + goal.yml, steps/script.yml          +  the one definition with a script-bearing reference -> ScriptEvidence
│   │   ├── verify/  ... + goal.yml export: true, steps/verify.yml +  the exported goal: its preconditions are Verification (and so is the only step whose post-conditions they use); the answer type is TaskReport
│   │   └── escalate/ ... + goal.yml, steps/escalate.yml       +  blocked branch -> OpenQuestion, with the continuation command in its output
│   ├── reference-heavy/                                       ~  wired as a document reference or rewritten: today it declares no references: and its references/domain.md is never staged
│   └── arithmetic/                                            +  the no-addon example: proves the channel alone
│       ├── manifest.yml                                       +
│       ├── goal.yml                                           +  export: true
│       ├── steps/compute.yml                                  +  toolGroups: [math], no references
│       └── types/.gitkeep                                     +
│
├── docs/
│   ├── agent-spec-format.md                                   ~  layout, optional keys (references:, references.yml), invariant list, issue codes — including that the format has no ordering, loop or branch construct, so a pack's progression is types
│   ├── 03-embabel-spec-agent-builder-design.md                ~  the guardrail row corrected: it lists an annotation absent from the 1.5.4 artifacts
│   ├── 05-architecture.md                                     ~  module map gains the tools tree; gating taxonomy unchanged
│   ├── 07-runtime.md                                          ~  what a run offers, the posture it prints, the three run commands, the script switch and what it does not gate, the planner each strategy actually is, the measured strategy table, and that a stop is not a pause
│   ├── 12-backlog.md                                          ~  closes the unresolved-role item and the "smith run is offered nothing" reader item
│   └── 04-poc-run-results.md                                  ·  the dated live claims, or the example's own note
│
├── docker/
│   ├── smith-runner.sh                                        ~  exports the switches the example's command-running leg needs
│   └── Dockerfile                                             ·  runner image unchanged; a note if the example is baked
│
└── poc/                                                       =  frozen; the shell tool and the group recipe are extracted, not depended on
```

## What each area is there to satisfy

```
  area                          spec capability             task group
  ---------------------------------------------------------------------
  smith-common model+writer     agent-spec-skill-references 2
  smith-common validator        agent-spec-skill-references 2, 7.2, 8.4
                                + pack-carried-references
  tools-coding module           coding-tool-addon           3
  tools-coding autoconfig       tool-addon-modules          3
  runtime core wrapper          runtime-skill-hosting       4
  runtime PackReferences        pack-carried-references     7
  runtime ScriptEngineConfig    skill-script-execution      6
  runtime guardrails + bounds   runtime-guardrails          8
  runtime report + CLI          tool-addon-modules          3, 4, 8.1, 9.4
                                + runtime-skill-hosting
                                + runtime-guardrails
                                + planning-strategy-choice
  runtime ResolvedToolGroups    tool-addon-modules          1
  agent-specs/coding-agent      coding-agent-example        5
  agent-specs/arithmetic        coding-agent-example        5.5, 5.6
  agent-specs/skill-interpreter skill-interpreter-pack      9
  studio + a2a touchpoints      tool-addon-modules          3.8
  docs                          all                         landed with each group
```

## Files that look like they should change and will not

- `prod/embabel-smith-runtime/.../core/TrustTier.java` — the role names it already lists are the ones the
  addon registers. Changing the tier vocabulary would change what existing packs are allowed to declare.
- `prod/embabel-smith-runtime/.../core/StepSpecAssembler.java` — it copies step YAML verbatim, which is
  exactly what makes a new key survive to the upstream reader.
- `prod/embabel-smith-runtime/.../trace/TraceRecord*.java` — the report is run output; the trace record
  rejects unknown fields and the loop reads it as-is.
- `prod/embabel-smith-builder/.../skill/PublishedSkillHost.java` — the builder's route stays; the runtime
  route is a second reader of the same artifact, not a moved one.
- `prod/embabel-smith-a2a-starter/.../SpecSourceSkills.java` — the card's filter and the run gate are
  already one predicate; advertising skills is an open question, not this change.
- `prod/embabel-smith-runtime/.../core/PlannerResolver.java` — `--planner` and a pack's `defaultPlanner`
  already reach `ProcessOptions`. What lands is a measurement of the interpreter pack under each strategy
  and a report line naming the algorithm that planned; a fifth strategy would need a platform subclass,
  since `DefaultAgentPlatform` hardcodes the factory.
- The platform's own `ProcessOptions` defaults and `skills.script` engine classes — configured here, never
  forked. The engine is selected by a property mapped to the upstream class; the guardrail is one class
  registered through the platform's registry.
- No guardrail wiring class. `GlobalGuardRailsRegistry` is a `@Component` under `com.embabel.agent.spi`,
  which upstream's `AgentPlatformAutoConfiguration` already component-scans through `ScanConfiguration`, so
  the bean is created with the properties unset. Only the guardrail class and the property values are ours.
- `smith-common/io/AgentSpecWriter.java` and the studio's compose model gain no `references.yml` round-trip.
  It is a pack file the runtime reads; making it a model object is a later decision, and a writer that
  rewrote a pack it could not fully represent would be worse than one that leaves it alone.
- No flow keys in the format: no `repeat`, `until`, `iterate`, `requires-approval`, `retry` or `timeout`
  step key. The upstream reader disables `FAIL_ON_UNKNOWN_PROPERTIES`, so each of those would read as
  configured and do nothing; the validator refuses them instead, and the interpreter pack's progression is
  carried by named types.
- `poc/**` — frozen, and the enforcer's ban is what keeps it that way for the new modules too.
