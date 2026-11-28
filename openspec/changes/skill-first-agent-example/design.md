# Design

## Context

Two channels connect a declarative step to a capability, and today neither of them is fed.

`RuntimeCore.ingest` stages the pack's YAML and hands it to
`new StepSpecAgentScopeBuilder(name, platform, repository, types, List.of(), List.of())`
(`prod/embabel-smith-runtime/.../core/RuntimeCore.java:142-143`). Both trailing lists are empty, and the
builder's own constructor has six parameters — `name, AgentPlatform, StepSpecRepository, DataDictionary,
List<ToolGroupDescription>, List<Tool>` — with no reference channel at all; in `deploy()` it constructs
`StepSpecContext(name, dataDictionary, toolGroups, tools, null, 16, null)`, the bitmask meaning
"take the default", which for `availableReferences` is `emptyList()`. All four call sites in this repo
pass the same six arguments.

Upstream is ready for the other half. `PromptedActionSpec` carries `references: List<String>` and its
`emit` builds a name→`LlmReference` map from `StepSpecContext.getAvailableReferences()` and keeps the
entries its own `references` names. `toolGroups` is a separate channel resolved by role at prompt time
through the platform's `ToolGroupResolver`, which Spring fills from the `ToolGroup` beans in the context
that booted the platform — the mechanism `prod/embabel-smith-builder/.../tools/ToolGroups.java` already
uses for its `validate` group. `@LlmTool` on a method registers nothing by itself and `@ToolGroup` is
referenced by no other class in the 1.5.4 jars, so a group has to be published as a bean.

The tool surface mostly exists upstream as `@LlmTool` interfaces with a `root`: `FileReadTools`,
`FileWriteTools` (`createFile`, `writeFile`, `editFile`, `appendFile`, `createDirectory`, `delete`),
`PatternSearch` (`findPatternInProject`, `matchesGlob`), `LocalDirectory` (directory tools that are also
an `LlmReference`), all `DirectoryBased`. `tools/process/AgentProcessTools` is about agent processes, not
shell execution, so the command tool is the one piece this repo authors; `poc/poc-7-runtime-adapter` has
a working `bash -lc` tool and a reflective `ToolGroup` factory, and `poc/` is frozen, so both are
extracted, not imported. Nothing in `prod/` defines a file, search or command tool today.

The trust side is already written and already wrong in one respect: `runtime/core/TrustTier.java` names
`search`, `web`, `file` for `STANDARD` and `*` for `HIGH`, and `TrustTierPolicy` filters the *declared*
strings — so `agent-specs/shell-runner`'s `shell` declaration already trips the tier check while
resolving nothing. Registering the roles makes that check mean what it reads as.

## Goals / Non-Goals

**Goals:**

- One format-level way for a pack to say "this step may use this skill", honoured by `smith run`.
- Tool capabilities delivered as addons a deployment enables, with the role names the tiers already use, a
  documented default set, and a switch on every group.
- Every silent failure in the chain — a dangling reference, an unloadable skill, a role that resolves to
  nothing, an addon present but disabled, an unknown step key — reported by name.
- One runnable example that needs none of this repo's compiled code.
- A generic pack that follows a skill it has never seen, shipped beside the narrower coding instance, so
  the mechanism and one use of it can fail separately.
- Skill-carried scripts executable from `smith run`, through the engines Embabel already ships, off by
  default and confined to the same root as the file tools.
- References wider than skills: a step able to name a directory, a document or a brief the way it names a
  skill, with the silent-drop paths closed at validation.
- Guardrails and bounds stated where they actually live — the platform's global registry and the run's
  budget — rather than invented as step keys the reader would ignore.
- The planner choice documented honestly and measured against the generic pack, which already supports more
  than GOAP.

**Non-Goals:**

- Durable pause and resume for human approval: a synchronous, non-web runtime cannot hold a process open
  for a person, and the pieces that would (a resume surface behind an autonomy service, a JDBC snapshot
  store that does not exist at this version) are a separate change (D16).
- Per-step guardrails, budgets, retry counts or timeouts expressed as step keys. They are not reachable from
  a step spec, and the reader silently ignores unknown keys, so writing them would be a lie (D15).
- A custom `Planner` implementation: the platform's factory is hardcoded and would need a platform
  subclass, which is out of scope (D17).
- Per-run sandboxing beyond one root, a command timeout and output caps (D8).
- Skill reach through the A2A card, which publishes capability descriptions rather than loaders.
- Any change to `poc/`, to the builder's `PublishedSkillHost` route, or to which prompts are mutable.

## Decisions

### D1 — References reach a step by wrapping the repository, not by replacing the deploy path

`StepSpecAgentScopeBuilder` stays exactly as the runtime calls it. The runtime hands it a
`StepSpecRepository` whose `findAll()` returns each upstream `StepSpec` wrapped so that
`emit(context)` delegates to `spec.emit(context.copy(name, dataDictionary, toolGroups, tools,
referencesForThisPack))`. `StepSpec` is a plain public interface (no `PermittedSubclasses`), and
`StepSpecContext` has a public five-argument constructor and a public `copy`, so this is ordinary Java
against public API — no reflection, no fork, no duplicated deploy logic. Upstream keeps doing the name
filtering it already does, which is what keeps the per-step scoping honest: a step naming nothing gets
nothing even though the reference is in the context.

*Alternatives.* Asking upstream for a `references` constructor parameter is the cleanest fix and the
slowest; the wrapper is reversible to it, since only one expression changes. Emitting the steps ourselves
and assembling an `AgentScope` through `AgentScope.Companion.invoke` works — the factory is public — but
re-implements `deploy()` and starts drifting the first time upstream changes it. Passing `Skills.tools()`
through the builder's `tools` list does not reach a model: a step selects a scope-wired tool only by a
`tools:` name that this repo's `ActionStepSpec` cannot carry, which is the exact failure
`tools/AgentSpecValidationTool.java` documents for the builder. Registering `Skills.tools()` as a
`ToolGroup` bean would reach a model today, and is kept as the named fallback if a bump breaks the
wrapper — at the cost of making skill reach a property of the deployment instead of a declaration in the
pack, which is the thing this change is trying to avoid.

### D2 — Addons are auto-configured libraries, enabled by property; dynamic classloading is deferred

Each family is a library with a Spring `@AutoConfiguration` (registered through
`AutoConfiguration.imports`) that publishes `ToolGroup` beans. What decides registration is a per-group
switch — `smith.tools.<group>.enabled`, with `matchIfMissing` set to that group's documented default and
the group's settings under the same prefix. That is the shape Spring Boot uses for its own optional
features and the shape this repo already uses for `smith.a2a.enabled` and
`smith.builder.skill-scripts.enabled`, with one difference worth stating: the default is written down per
group instead of being "off" everywhere.

**The default set is the roles the default tier already names and an implementation exists for.**
`TrustTier.STANDARD` is `{search, web, file}`. Of those three, `file` has a class on the classpath and
`search` and `web` do not — they are `CoreToolGroups` names that stand up only on an MCP connection. So
`file` registers on its own, read-only until `smith.tools.coding.writes` says otherwise, and `shell` does
not, because `shell` is absent from the default tier's vocabulary and appears only under `HIGH`. The
ruling then has a rule behind it rather than a list: the tier says what a pack may ask for, the default set
says what the process can answer with no operator involved, and the two agree by construction wherever
there is something to answer with.

Two precedents keep this from being novel. The builder already publishes six `ToolGroup` beans with no
switch at all — `validate`, `process`, `blueprint`, `library`, `security`, `traces` — which is why a
builder pack declaring `toolGroups: [validate]` resolves today; this brings the runtime to the same
posture, with the switch the builder never got. And one group is already on by default in an untouched
runtime, chosen or not: upstream's `mathToolGroup()` bean registers `math` unconditionally. A claim that
nothing registers by default would be false on its first line, so the change says what does register and
gives `math` a switch too, which upstream does not offer.

The runtime still resolves by role at prompt time and imports no addon class, so an addon needs no host
change; the runtime module declares the coding addon so `smith run` and the container runner work from a
checkout with no flags at all.

*Alternatives.* Loading jars from a configured directory at boot with a `URLClassLoader` is genuinely
dynamic, and it costs more than it buys here: bean discovery, Spring Boot AOT and the container profile
all get a second classloader to explain, and the runtime ships JVM-only in a docker runner where adding a
jar was never the hard part. The property already gives the operator the lever that matters — an addon
present on the classpath is one variable away from on, and one away from off. Presence-without-enable is
the state the boot report must make visible, which is why the report is a requirement and not a nicety.

A previous version of this decision had everything off until named, on the reasoning that presence must
never become reach. That is superseded: an all-off default is not more secure here, it is more confusing,
because it leaves the tier allowlist promising `file` while the process answers nothing, and it is already
false for `math`. What protects the deployment is the narrower claim now in the spec — a role *outside*
the named default set cannot appear from a transitive bump — plus the pinned-set check, which now pins the
default set as a claim rather than an accident.

One caveat a switch inherits. In an AOT-processed or native build, `@ConditionalOnProperty` is decided
during the AOT pass, so a default-on group is frozen on and a runtime `SMITH_TOOLS_FILE_ENABLED=false`
will not remove it. The runtime ships JVM-only (native image applies to the builder), so the switch works
where this code actually runs; if the runtime ever gains a native image, the run report's "how each role
arrived" line is what makes a frozen build legible instead of surprising, and the honest fix is to build
the image with the switch rather than pretend the variable reached it.

### D3 — The roles are `file` and `shell`, and what a boot actually resolves is measured, not inferred

The coding addon registers the two roles the runtime's own tier enum already names. Upstream's
`CoreToolGroups` names (`web`, `maps`, `github`, `browser_automation`) are deliberately not claimed: a
measured probe in a builder context resolved only `math` besides the repo's own `validate`, so a role
name is not evidence of a backing tool. The first task is the ten-line `@SpringBootTest` that prints
`availableToolGroups()` and a `resolveToolGroup` result for each candidate in a booted *runtime* context,
and the registered set is then pinned by a test so a bump that auto-registers a process-backed group
fails the build.

One discrepancy the probe has to settle rather than assume away. `prod/embabel-smith-builder/.../tools/`
carries six `@Configuration` classes that publish groups — `validate`, `process`, `blueprint`, `library`,
`security`, `traces` — yet the earlier probe resolved only `validate` of them. Either five register only
in a context that component-scans them or behind a collaborator the probe did not start, or the probe
under-reported. The same probe answers it, and the answer matters here because the runtime is being brought
to that module's posture: if a builder `@Configuration` of this shape silently fails to register, the
runtime's default-on `file` group can fail the same way and the boot report is what catches it.

The jar-level reason for that measured set, read off 1.5.4 rather than inferred:
`AgentPlatformAutoConfiguration` has no condition at all and imports `ToolGroupsConfiguration`, whose
beans are `mathToolGroup()` — `MathTools`, a `ToolGroup` with role `math` — and four MCP-backed groups
(`web`, `maps`, `github`, `browser_automation`) each `@ConditionalOnMcpConnection`. There is no
`embabel-agent-tools-*` artifact: every tool class ships inside `embabel-agent-api`, and the file,
process and search classes there are `@LlmTool` interfaces that nothing publishes as a bean. So the
default set for a runtime boot is `math` plus the repo's own groups, and `AppleScript` only on a Mac.

`ToolGroupsConfiguration` is itself `@ConditionalOnClass(McpSyncClient)`, and that condition is
**satisfied here**: `embabel-agent-api` depends non-optionally on `spring-ai-starter-mcp-client:2.0.1`,
which brings `io.modelcontextprotocol.sdk:mcp`, so the class is on the runtime classpath and the
math bean really does register. The consequence is not cosmetic: `@ConditionalOnMcpConnection` matches on
the *presence of a property* — `spring.ai.mcp.client.stdio.connections.<name>.command` or the
`…sse.connections.<name>.url` — with no credential check in the condition. An operator who configures a
docker MCP connection therefore gains `web`, `maps`, `github` and `browser_automation` with no repo
change, and `web` is already inside `STANDARD`'s allowlist. That is the strongest argument for the
pinned-set requirement: reach can widen through machine configuration alone, and the build is the only
place left that can notice.

Measured on 1.5.4 by `runtime/core/ResolvedToolGroupsTest`, which boots the runtime rather than reading
jars, the probe's open questions close as follows.

A plain boot resolves exactly `[math]`; `file`, `shell`, `search`, `web`, `chat`, `AppleScript`,
`validate` and the five remaining builder roles each answer `No tool group matching role`, and the MCP
flip adds exactly `browser_automation`, `github`, `maps`, `web`. Every positive claim above is therefore
confirmed rather than inferred.

The builder discrepancy this decision flagged is a packaging fact, not a silent registration failure.
`embabel-smith-builder` and `embabel-smith-runtime` have no dependency edge in either direction, so the
builder's six `@Configuration` classes are absent from a runtime boot's classpath; and all six are
unconditional, with no `AutoConfiguration.imports` in that module, so the only route to them is
`SmithBuilderApplication`'s `@ComponentScan` — which reaches all six equally. "Only `validate` resolved"
was a property of that probe's context. The worry behind the question does not transfer to the runtime's
default-on group: a group is absent exactly when no bean of that type is in the context, and
`availableToolGroups()` says so in one line — which is what the boot report of D2 prints.

One collision to resolve before group 3 builds on a name. `file` and `shell` are free at the bean level in
both boots — no upstream group bean claims either. They are not free at the property level:
`embabel.agent.platform.tools.includes.shell.tools=anything` registers a working `shell` group in an
otherwise untouched boot, through upstream's own role-keyed map. So the precedence rule of D10 has to be
tested against `includes.shell` as well as `includes.file`, and the role that collides is the one the
`HIGH` tier gates.

### D4 — Skill scripts run through the platform's own engines, off by default

This reverses the decision this change first made, and the reversal is cheap because the engines are not
ours to write. `embabel-agent-skills` ships `SkillScriptExecutionEngine` with four implementations:
`NoOpExecutionEngine` — what `Skills` assembles with by default, which is why a script-bearing skill
currently offers no script tool — `ProcessSkillScriptExecutionEngine` spawning interpreters with a 30-second
default, an interpreter map, an environment allowlist and a Java-callable `confinedTo(root)` factory, and
`Docker`/`Podman` engines adding `workDir`, `user`, `memoryLimit`, `cpuLimit` and `networkEnabled`. So
"support and control scripts from skills" is a selection plus a property plus the same root confinement the
file tools already use, not a security subsystem.

What is genuinely ours: the property that selects an engine — `smith.tools.scripts.engine`, taking `none`
(the default), `process`, `docker` or `podman`, and mapped to the platform's engine classes rather than
accepting a class name from the value, since upstream chooses only by constructor or
`Skills.withScriptExecutionEngine(engine)` and offers no switch — the default that keeps execution off, and
the report line that says which engine answered. The builder's GraalJS engine is not reusable here — sibling
modules, and the reason `TraceRecord` went into framework-free `smith-common`, which cannot host a polyglot
engine either.

Two upstream facts shape the spec rather than the code. A script's callable name is
`<skill>_<file-basename>` formed **without** sanitization, so a hyphenated file name survives into the tool
name and then gains the reference prefix on the wire; and `Skills.tools()` includes the script tools of
**every loaded skill, activated or not**, because the prompt runner captures the tool list at start-up. That
second fact is why a reading step and an executing step cannot share one attached `Skills` object, and the
lever that separates them is the script execution engine, not `SkillFilter`: in 1.5.4 nothing in the jar
calls `SkillFilter.accept`, so `WITHOUT_SCRIPTS` filters nothing and the surface is decided solely by
whether an engine is attached. SKILL.md itself cannot help: the loader declares no script
metadata, the input schema is fixed at `args`/`stdin`/`inputFiles`, and language comes from the file
extension — so any per-script policy is authored here, on top of the engine's `validate()` hook.

One dependency fact comes with this: `embabel-agent-skills` is declared only by the builder's pom, so the
runtime gains it here. The jar ships no auto-configuration and no `AutoConfiguration.imports`, so
installing it activates nothing on its own — a `Skills` reference exists only because this repo constructs
it from a folder.

Two rulings landed during apply, written here because apply-phase channels cross: **6.3** — the twin a
step gets is selected by a `scripts: true` **field on the pack's `references.yml` entry** (kinds stay
directory/document/brief, same pattern as 7.3's `unfolding: true`); the step's own `references:` names
intersect the marked entries, and only the engine attachment differs between the two surfaces, since
`SkillFilter` is inert. A scripts/-directory marker is rejected because the platform scans it pack-wide,
which cannot pass the box's negative leg. **6.4** — the output cap (`smith.tools.scripts.output-cap`,
default 16000) lives in the recording engine, constructing the truncated `Success` through its public
name-mangled `copy` method resolved by shape (return type `Success`, parameters `String, String, int,
long, List`, `setAccessible`), never by the mangled string; if resolution ever fails the engine passes
through and the report says the cap could not be applied. A pin-test asserts the shape resolves against
the pinned jar, so a platform upgrade trips at build time.

A third ruling closes 6.3's split: the field-reading half is landed in `PackReferences` (strict parse,
`scriptsAskedFor()` for the step side, validator refusal of `scripts:` on any kind but `skills` — the
switch is dead wherever no script tool is handed out); the consumer half belongs to the step side, where
the intersection attaches the engine-bound twin to asking names and `scriptFree()` to the rest, with the
offering test's fixture gaining the declaration in the same stroke. The interpreter pack's own
`references.yml` experiment stays reverted: its twin demonstration lives in the reference test, the
offline staging design is 9.2's, and the file map lists that file as optional.

### D5 — One reference per agent definition, named from the agent

Each agent definition contributes a `Skills` reference named `<agent>-skills`; a folder's shared
directory contributes `shared-skills`. The reference name is load-bearing in a way that is easy to miss:
Embabel's naming strategy prefixes every callable name with the sanitized reference name, so
`<agent>_skills_<skill>_<script>` is what reaches the wire while `Skills.tools()` reports the unprefixed
form. Reference names are therefore fixed by test, and the example's skill text points at paths inside
the folder rather than at tool names — the hazard `12-backlog.md` §Builder — skill target already records
for hyphenated skills.

### D6 — Shared skills come from the pack's immediate parent

The shared set is `skills/` in the pack directory's parent, read by the same loader and reported by
absolute path. An agent's own `skills/` shadows a shared name and the run says which folder answered.
The immediate parent is also the source root when a source points at a folder of packs, so a stray
`agent-specs/skills/` would silently become global to every pack in it; the report and the pinned test are
the mitigation, and ancestors further up are never consulted.

### D7 — Groups are authored with the proven recipe; permissions are descriptive, the bounds are real

`ToolGroupDescription.create` directly, the five-argument `ToolGroupMetadata.Companion` factory resolved
by *signature* through `getMethods()` (the mangling is why), then `ToolGroup.Companion.ofTools` — plainly
named — and a plain `@Bean`. Upstream tool interfaces are published through their own self-publisher; the
command tool is repo-authored, extracted from POC 7 rather than imported. One upstream candidate is
rejected on the record: `embabel-agent-code:1.5.4` ships `BashTools(String workingDirectory)` with a single
`@LlmTool runBashCommand(String): String`. Nothing here depends on that artifact and it drags jgit,
javaparser and classgraph in with it, but the disqualifying part is the signature — a boot-time working
directory, no time limit, a bare string back. The bounds this change's own spec asks for (a timeout kill,
the exit code, stated truncation, a working directory checked against the root) are precisely what that
shape cannot express. `ToolGroupPermission` has two
values and enforces nothing, so it is metadata for a reader; the real limits are D8's root, the command
timeout and the output caps, plus the tier gate on the declaration.

### D8 — The run root is a boot-time property, not a per-run argument

The addon's file tools are constructed once, as beans, and upstream's `DirectoryBased` root is where
confinement is carried — `FileReadTools` and `PatternSearch` both extend `DirectoryBased`, which is nothing
but `getRoot()`, and the check against it is `FileUtilsKt.resolveAndValidateFile`, which normalizes and
compares. So the root is a property of the deployment (`smith.tools.coding.root`, defaulting to
the process's working directory). A per-run root would need per-run tool instances, and the platform
resolves tools from beans, not from the run. Making it per-run is an upstream conversation, recorded
below rather than worked around with a mutable bean — a bean whose root a run rewrites is shared state in
a process that the studio also embeds.

Two limits on that, measured rather than assumed (task 3.5). The validator is lexical, so a symlink inside
the root that points outside it is not caught by normalization and needs its own case; and both interfaces
call a plain `resolvePath` as well as the validating one, so which offered methods are actually confined is
read from the addon's test. Where a method is unconfined, the addon either withholds it from the group or
wraps it — it does not publish an unconfined reader under a role the tier allows.

### D9 — The example is a folder of agents, not one pack

`agent-specs/coding-agent/` holds several agent definitions — a reader that only declares `file`, a
changer that declares `file`, a verifier that declares `shell` — with a shared `skills/` folder carrying
the repository-orientation skill and one definition carrying its own. Multi-definition is the point: it
is what makes the shared-skill rule observable, and it exercises the existing rule that packs sharing a
source land in one platform and can chain. The members are named so that a chain between them is what the
planner chooses, not what a directory listing chose.

### D10 — Exposing a tool is a property, and the property names a curated entry, never a class to instantiate

Three property shapes can put a group in front of a resolver, and the change uses the first two.

`smith.tools.preset=coding` selects a **preset**: a curated bundle defined in the addon that sets the group
switches together. It is shorthand, not a second gate — a preset decides which groups are asked for, an
explicit `smith.tools.<group>.enabled` still wins over it, and the report says which source decided a role.
With neither preset nor switch present, the documented defaults of D2 apply.

`smith.tools.expose.<role>=<entry>[,…]` is the operator's own group: an entry names a capability the
addon knows how to build — `file-read`, `file-write`, `search`, `command` — and the addon resolves it
against a fixed map it owns. The upstream classes are reached through their own factories rather than by
reflection: `FileTools.Companion.readOnly(root, transformers)` and `readWrite(root, transformers)` are
public, and a `SelfToolPublisher`'s `tools()` feeds the same `ToolGroup.Companion.ofTools` call the preset
uses. So an operator can stand up a read-only file group with one line and no code:

```
smith.tools.preset=coding                        # shorthand for the switches below, not a separate gate
smith.tools.file.enabled=true                    # beats the preset; the report names the source
smith.tools.shell.enabled=false                  # on by default at HIGH, off until asked
smith.tools.expose.audit=file-read,search        # a role of the operator's own naming
smith.tools.coding.writes=false                  # the file group publishes readOnly, not readWrite
```

What is deliberately **not** supported: an entry that is a fully-qualified class name. Instantiating
whatever a config file names would let a property on a shared runner hand a model any class on the
classpath, which is a wider door than the tier system was designed around. An unrecognised entry is
refused at start-up and named, rather than skipped — a typo'd entry that silently registers nothing is
the same invisible failure this change is trying to eliminate for steps.

The third shape is upstream's own, `embabel.agent.platform.tools.includes.<role>`, and it stays in play
whether this repo likes it or not: it builds an **MCP-backed** group under whatever role key the property
uses. Two consequences are stated rather than discovered later. A `file` entry there produces a group
named `file` that competes with the addon's, so the boot report prints both sources for a role and the
precedence rule is that an addon-registered group wins and the shadowed one is named. And a machine with
an MCP connection configured gains `web`, `maps`, `github` and `browser_automation` with no repo change
at all, which is why the pinned role set is an expectation the build defends.

One packaging caveat travels with any of these, and it is the one stated in D2 rather than a new one: a
group behind `@ConditionalOnProperty` is decided during Spring's AOT pass in a native build, as the
builder's skill-script engine already demonstrates. The runtime ships JVM-only, so the switches are read
live today; if the runner image is ever AOT-processed, each group's default becomes a build-time decision
and the report's "how this role arrived" line is the only thing that will tell an operator the property was
ignored.

### D11 — A second example uses the arithmetic group, because it needs nothing from this change

`agent-specs/arithmetic/` is a pack whose step declares `toolGroups: [math]` and asks for a computed
answer. It exists because `math` is the one role an unmodified runtime already resolves: upstream's
`ToolGroupsConfiguration.mathToolGroup()` bean is there whether or not the addon is written, so this pack
demonstrates the *channel* — a declared group, resolved by role, offered to the step's model call — with
zero code from this change, no property, and no skill folder. When the coding example misbehaves, the
arithmetic one is what says whether the deploy path works and the addon doesn't.

It also forces a tier question that the coding example can dodge. `runtime/core/TrustTier.java` gives
`STANDARD` the roles `search`, `web` and `file`; `math` is not among them, so the pack is blocked at the
default tier and its run command says `--trust-tier high`. The tempting shortcut — add `math` to
`STANDARD` so the example runs with no flags — is declined: that changes what every existing pack is
allowed to declare under the default tier, for the comfort of a demo. A named tier in a documented
command is the honest cost, and it teaches the rule the coding example would otherwise hide.

One observation this example must record rather than assume: the group is `math`, registered by an
`UnfoldingTool`, so what the model is actually offered — one `math` tool or its unfolded operations — is
read from the `offered=` line of the live leg, not from the class name.

### D12 — The interpreter is a folder of type-chained definitions, because the format has no flow

The ceiling is measured, not assumed. The runtime ingests every immediate subdirectory of a source as a pack
and deploys all goals and actions into one agent, so ordering is entirely the planner's inference from
types. `AgentsPack` and its `process.yml` exist in `smith-common` with agents listed in run order, but a
grep for `process.yml` in the runtime's source returns nothing — that ordering is builder-side. There is no
loop construct: no `repeat`, `until` or `iterate` key exists anywhere in `smith-common/src/main`, `pre` and
`post` are opaque names with no declaration site, and `canRerun` is a nullable Boolean that no example and
no document uses.

So the pack's progression is expressed in types — `RunBrief` → `SkillDirective` → `ActionPlan` →
`WorkLog` / `ScriptEvidence` → `Verification` → `TaskReport`, with `OpenQuestion` as the blocked branch —
and the definitions are split so every hand-off is a named type rather than a position in a list. The
inventory: **orient** reads the task and lists what the folder offers; **select** activates one skill and
records what it requires — tools, scripts, resources, decisions; **plan** writes the intentions plus the
acceptance criteria the skill itself stated; **act** carries them out with the file tools, and `shell` only
where the tier allows; **script** is the single definition holding a script-bearing reference; **verify**
compares what happened against those criteria and is the only step whose post-conditions the exported goal
requires; **escalate** names the decision when something is blocked. The folder is run as its own source,
because a shared source lets the planner route through an unrelated pack once the tier permits — measured
behaviour in this repo — and the interpreter's behaviour must not depend on whatever else was in the folder.

Rejected: one pack with the steps in file order, since nothing reads that order; an `agents-pack` with
`process.yml`, which the runtime never opens, making it documentation pretending to be control; a loop key,
which the reader would silently ignore and a reader would silently trust.

### D13 — A step picks a skill inside one reference; it never attaches a reference

Among all 1.5.4 jars the only `withReference` call sites are inside `embabel-agent-api` itself, at
prompt-build time — there is no mechanism, interceptor or tool that attaches a reference mid-run. What *is*
model-driven is inside a reference already attached: `Skills.activate(name)`, `Skills.asIndividualReferences()`
exposing a tool surface per skill, and `withUnfolding()` revealing inner tools once the facade is used. The
interpreter's `select` step therefore chooses with `activate`, and the reference set deployed at ingest
stays fixed.

`asIndividualReferences` is rejected as the default because two reference names that sanitize to the same
prefix collide silently in the naming strategy's map, and a reference per skill multiplies that risk across
a folder nobody in this repo controls. Unfolding is offered as an option for a folder large enough that its
tool surface crowds out the step's own work — the cost the 60-second interaction ceiling makes concrete.

### D14 — Pack-carried references, of the kinds the runtime can construct

An `LlmReference` is a prompt contributor plus a prefixed tool list: `contribution()` emits exactly four
lines — name, description, tool prefix, notes — and `withReference` installs its tools through the
reference's own naming strategy. Implementations already on this classpath include `Skills`,
`LocalDirectoryReference`, `api/tools/file/LocalDirectory`, `SpringResource`, `LiteralText`, `WebPage` and
`UnfoldingReference`, and `LlmReferenceProviders.fromYmlFile` shows a file declaring references is an
upstream pattern rather than an invention here.

The runtime constructs three kinds from pack files — a skills folder, a directory, a document or brief —
named from a `references.yml` beside the pack's other files, and refuses any other kind at validation rather
than at run time. Two upstream facts become two validator checks: an unmatched declared name is dropped by
`emit` with a `map.get`/`ifnull` skip, and a prefix collision is a silent `Map.put` in which one
reference's tools overwrite another's. So validation refuses a dangling name, a missing backing path, and a
name pair that sanitizes to one prefix. `GoalSpec` carries no `references` field at all — only action steps
do — so a goal-only pack cannot reference anything, and the validator should say that rather than leave it
to a deploy.

### D15 — Guardrails where they are reachable, and no invented step keys

The per-interaction hooks exist and are unreachable from here: `LlmInteraction` exposes guard rails, tool-loop
inspectors, transformers, tool-call inspectors, a maximum iteration count and a tool-not-found policy, all
set from the Kotlin DSL or an `@Action` body, and `PromptedActionSpecAction`'s constant pool mentions
nothing but `toolGroups` and `ToolGroupRequirement`. What *is* reachable is narrower and real: the platform's
global registry through `embabel.agent.guardrails.user-input`, `.assistant-message` and
`.fail-on-error` with one class on the classpath — precedent already in `poc/poc-10-trust-tiers` — and the
run's own `ProcessOptions`, whose `Budget(cost, actions, tokens)` defaults of 50 actions, $2 and 1M tokens
`RuntimeCore` currently leaves untouched. `AgentProcessCallback` observes rather than gates, and
`@SecureAgentTool`, which `docs/03` lists as available "Java only", does not exist in any 1.5.4 jar.

Two details of that registry change what this task is. `GlobalGuardRailsRegistry` is a `@Component` in
`embabel-agent-api`'s `spi.support.guardrails` package, and upstream's `AgentPlatformAutoConfiguration`
imports a `ScanConfiguration` whose `@ComponentScan` covers `com.embabel.agent.spi` — so the bean is already
created in any Boot app that runs on the platform, including this runtime. There is nothing to wire: the
authored part is the guardrail class and the property values. And the registry reads its three properties
with `@Value`, splits the class lists on commas, instantiates them, and swallows a failure unless
`fail-on-error` is set, which it is not by default. A mistyped guardrail class name therefore produces a run
that believes it is guarded. That is the same silent-success trap as an unknown step key, and the posture
line answers it by printing the guardrails the registry reports as instantiated rather than echoing the
property, so a name that failed to load shows as absent.

The rule that follows is the one that matters most in a spec-driven runtime: **do not add a YAML key for
something the platform cannot honor.** `YmlStepSpecRepository` disables `FAIL_ON_UNKNOWN_PROPERTIES`, so an
invented `requires-approval: true` would be read, dropped, and then look like a control that was configured.
So the validator gains an unknown-step-key check, the runtime states its posture at start-up — tier and what
it allows, lifecycle exclusions, guardrails registered and their error policy, budget, tool-loop ceiling,
tool-not-found policy — and `docs/03`'s guardrail row is corrected in the same commit that lands the check.

### D16 — Human involvement is a stop with a question, not a pause

The HITL surface is tool objects — `Awaitable`, `ConfirmingTool`, `TypeRequestingTool`, the `WaitFor` DSL —
and resuming needs `Autonomy` plus a snapshot store, with `DefaultProcessCallbackTools` as the resume tools
and a JDBC snapshot store present only at 1.5.3, nothing at 1.5.4 in this build. The runtime sets
`spring.main.web-application-type: none`, runs synchronously, and `docs/07-runtime.md` already says its task
store holds only terminal tasks and `tasks/cancel` refuses because the run is synchronous.

So the interpreter's escalate step produces a blocked-for-decision outcome carrying the question, the
evidence gathered for it, and the exact command to continue. Durable pause and resume is a separate change
with its prerequisites named — a resume surface behind an autonomy service, a snapshot store that survives a
restart, and a runtime that is not synchronous — rather than a step key that would do nothing.

### D17 — The planner is already selectable; what is missing is honesty and one measurement

`RunCommand` already takes `--planner`, a manifest may declare `defaultPlanner`, `PlannerResolver` throws
listing `PlannerType.values()` for an unknown name, and `RuntimeCore` applies
`ProcessOptions.DEFAULT.withPlannerType(...)`. Because `SimpleAgentProcess` builds the planner per process
instance from `ProcessOptions`, one booted runtime can plan different runs differently — the seam exists, and
the fact that `StepSpecAgentScopeBuilder` takes no planner parameter is therefore beside the point.

Two things need correcting rather than building. `DefaultPlannerFactory` maps `SUPERVISOR` to
`AStarGoapPlanner`, so a report that prints the label would imply an algorithm that did not run; and no
UTILITY or HYBRID run is recorded anywhere in `docs/04` or the POC READMEs — every measured run in this repo
was GOAP. The change therefore measures the interpreter pack under each strategy — steps selected, whether
it terminated when the skill's criteria were met, model calls consumed — and records the result, a failure
to reach the goal included as a finding about the pairing rather than hidden. Iteration for an open-ended
skill is `ReplanningTool` / `ConditionalReplanningTool`, beside `handlePlanNotFound`'s "Process stuck" and the
`replanBlacklist` that stops the requesting step being chosen again, which is why a re-plan request is its
own event in the run record. A custom `Planner` is out of reach without a platform subclass, since
`DefaultAgentPlatform` hardcodes the factory. The default stays GOAP unless the measurement says otherwise,
and changing it is a stated decision, not a side effect.

## Risks / Trade-offs

- **The wrapper depends on two public upstream signatures** (`StepSpec.emit`, `StepSpecContext.copy`) →
  a deploy test asserts a declared reference actually reaches a `PromptRunner.withReference`-shaped
  offer; if a bump changes the shape the test fails at the wrapper, not inside a live run.
- **A reference that resolves to nothing is invisible** → validation refuses a dangling name, and the run
  reports the reference names it could not serve. The report is no longer only a reference report — what a
  step declared as a role is stated there too, which is the asymmetry the next bullet used to call a gap.
- **Three states read as one** (registered by default, switched, tier-allowed) → the boot report prints the
  state it acted on for each role, and a disabled group is named as disabled rather than unknown, so the
  silence that once stood for "nothing registered" now has three different sentences to say. A tier refusal
  already names the pack and the role it refused. **Closed as box 3.16 landed it: an unresolvable role is
  named by the run.** The claim this bullet first carried — that the runtime already named it — was measured
  and did not hold when it was written: a declared role name is a pass-through that reaches the platform's
  resolver only when the step prompts, and the run report carried skill notes and reference outcomes and
  nothing about roles. It holds now by construction rather than by assertion: `RuntimeCore.unresolvedRoles`
  compares each step's declared roles against the resolver's own `availableToolGroups()` and states the gap
  per declaring step in the addon's `role: reason` shape, so a name nobody registers is named by the run and
  not only by the resolver's failure message one level down. A role that resolved reports no failure either,
  and a role this project's addon shadows is not called unresolved, because the comparison is against what
  answers rather than against a list of expected names. What the runtime cannot see is *why* nothing answers:
  a group an operator switched off is indistinguishable from a typo at the resolver, so the reason stays the
  addon's boot line's, which names the property that decided it.
- **Registering `shell` turns a declaration into command execution** → the tier is unchanged and already
  refuses `shell` at `STANDARD`; the pinned role test plus the existing `aHighTrustStepIsBlockedUnderThe
  StandardTier` case are the guards, and the default enable state is off.
- **Reach can widen with no repo change at all** → `math` already resolves in an untouched boot, and an
  MCP connection configured on the machine adds `web`, `maps`, `github` and `browser_automation` through
  upstream's own conditions — one of which, `web`, `STANDARD` already allows. The pinned set states the
  offline expectation, and machine-level MCP configuration is documented as an operator action rather
  than pretended away.
- **A skill's scripts are outside the tier mechanism entirely** → the tier matches declared tool-group role
  names, and a script is not a role, so no tier setting blocks one; the only gates are the script switch,
  the engine it selects and the root that engine is confined to. Hence the default is off, the boot and run
  output name the switch state rather than leaving a script tool's absence to look like a tier refusal, and
  the script tool's name is reported in both its file and wire form so a run cannot be audited against a
  role list that never contained it.
- **Embabel's loader drops a whole skill on a dangling file reference, with a warning nobody reads** → the
  runtime names refused folders itself rather than leaving them in a log line, as the builder's host
  already does.
- **Tool-heavy steps burn the 60-second interaction timeout, since the tool loop's round-trips count
  inside it** → the example's steps stay tool-light, one or two calls each; a step that needs more is a
  follow-up with a raised timeout, not a silent failure rate.
- **A live coding run against this machine's endpoint hits the throughput ceiling** → the live leg is one
  hand-run recorded as a dated claim, never a matrix; `make test` stays model-free by the pom's
  `test.excludedGroups` default, which is what keeps CI honest about the offline half.
- **An addon that registers a role also changes what the A2A card's per-pack filter sees** → the card's
  filter and the run gate are one predicate already; the change adds a case, not a second rule.

## Migration Plan

Additive: new modules, one new optional step key, one new optional directory, a handful of switches, one
new pair of example folders. No existing pack file changes; a pack that declares neither `references:` nor
a new role behaves exactly as before, which is what the existing runtime suite proves. Rollback is
`smith.tools.file.enabled=false` and `smith.tools.shell.enabled=false` — no data, no store, no published
artifact involved, and the run report then says both roles were disabled by configuration rather than
missing. In the other direction it is worth saying plainly: reverting the commit removes the default set
too, so a pack that began working at HEAD stops resolving `file` without any configuration changing, and
that is what the report line is for.

## Open Questions

- Whether a per-run root is worth an upstream ask (D8), or whether one root per deployment is the honest
  product boundary.
- Whether the A2A card should advertise a pack's carried skills, which needs a loader-side answer to what
  a card entry means for a spec-sourced pack.

## Apply-phase coordination notes

- **7.3 landed (`ea55f9f`); 7.7 window is open on the RASR side.** Packs owns `ReferenceAwareStepSpecRepository.java`
  (a `formOffer` set, a `formsOffered()` accessor, one guarded line in `wrap()`) and the additive half of
  `RuntimeCore.report()` until they close the window. The 6.3 step-side intersection belongs in a new helper
  file for exactly this reason — it was ruled there to keep RASR collision-free; anyone who finds it cannot
  live outside RASR states that to the lead and is sequenced after the window closes. `attached()` stays
  byte-identical either way, so the 4.5 arrow assertions and the repository test's pinned formats are not
  touched by 7.7.
- **The 6.8 disclosures are owed as two strokes in two files**: the `PackSkills.environment()` line (corrected
  allowlist wording plus the 6.4 output-cap on the state line, one stroke) and the `docs/07-runtime.md`
  environment paragraph (replacement text supplied by the interpreter, plus the two-limit sentence). The
  shipped engine no longer inherits the host environment — anything still saying "the host's in full" is
  stale as of `3f5c942`.
- **9.5's re-plan clause lands as printed lines, not a schema change.** `TraceRecordJson` is strict — one
  record per run, extra fields rejected — and it stays that way; the re-plan becomes a run line emitted by
  a listener of ours (`ReplanRequestedEvent`, precedent in the builder's own test listener). The
  excluded-versus-absent clause is a new test against the existing pack-level surface
  (`IngestedSource.excludedPackForGoal`, `LifecycleBlockedException`), naming both sides and
  mutation-checking the wording, per the 4.5 lineage's warning about name-matched absence assertions.
  `RuntimeCore` lines for 9.5, 8.1's posture print and 8.3's `ProcessOptions` belong to the interpreter now
  that the 7.7 window (`f4b1b59`) is closed.
