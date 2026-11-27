# Platform facts

Facts about Embabel 1.5.4 that this change's implementation codes against, each read off a jar or off
this repo's own call site rather than off a class name. A delegation reported a first draft of this page
and it did not land on disk; several of its absence claims also failed a direct check, so what follows is
the re-measurement. The commands are included because an absence claim is only worth as much as the
search behind it — pattern-matching a class name for a word is not evidence that a capability is missing.

Version inspected: `embabel-agent.version` = 1.5.4 (root `pom.xml`), jars under
`~/.m2/repository/com/embabel/`. `embabel-agent-spec` resolves from `embabel-agent-experimental.version`
= 1.5.4-SNAPSHOT.

## Confirmed by measurement

| Thing | What the jar says | Where it matters |
| --- | --- | --- |
| Script engines | `NoOpExecutionEngine`, `ProcessSkillScriptExecutionEngine`, `DockerSkillScriptExecutionEngine`, `PodmanSkillScriptExecutionEngine`, all behind `AbstractContainerSkillScriptExecutionEngine` (which carries a `ContainerStartupStatus` with an `Unavailable` case, and `CpuLimit`) | D4, task 6.2 — the property maps to these four classes, and "container selected on a host that cannot run containers" has a real state to report |
| `ProcessSkillScriptExecutionEngine.confinedTo(String root)` | Builds the engine with `FileTools.Companion.readWrite(root, …)` and feeds that into a `ConfinedInputResolver` field, whose `resolve(Path)` the `execute` path calls on the input list | D8, tasks 6.2/6.4 — the root the file tools use *is* the root the script inputs are checked against; a path outside it is the resolver's refusal, not ours |
| `Skills` reference surface | `withLocalSkills(String)`, `withLocalSkill(String)`, `withFilter(SkillFilter)`, `withScriptExecutionEngine(SkillScriptExecutionEngine)`, `activate(String): String`, `asIndividualReferences(): List<LlmReference>`, `tools(): List<Tool>`. No `withUnfolding` on this class | D5, tasks 4.2, 6.3, 7.3 — unfolding comes from `api/reference/UnfoldingReference`, not from a `Skills` setter |
| `SkillFilter.WITHOUT_SCRIPTS` | A `private static` on `SkillFilter$Companion`, exposed as `getWITHOUT_SCRIPTS()`. Java reaches it as `SkillFilter.Companion.getWITHOUT_SCRIPTS()`, not as a static field | task 6.3 — the task text writes the field form; the call form is what compiles |
| Guardrail registration | `GlobalGuardRailsRegistry` is a `@Component` reading `${embabel.agent.guardrails.user-input:}`, `${embabel.agent.guardrails.assistant-message:}` and `${embabel.agent.guardrails.fail-on-error:false}`; it logs "Instantiated guardrail" and "Failed to instantiate guardrail from class" | D15, task 8.2 — nothing to wire, and the permissive default is exactly the trap the posture line has to defeat |
| Group publishing recipe | `prod/embabel-smith-builder/.../tools/ToolGroups.java` takes `ToolGroupMetadata.Companion` and scans `getClass().getMethods()` for the `(ToolGroupDescription, String, String, Set, String)` overload, failing with a named `IllegalStateException` when it is absent | D7, task 3.3 — the recipe to duplicate into the addon is this one, and POC 7's `ShellToolGroupFactory` is its frozen precedent |
| What a runtime boot resolves | `[math]` with nothing configured; the MCP property adds `browser_automation`, `github`, `maps`, `web` | D3 — see `runtime/core/ResolvedToolGroupsTest` |

## One gap the tasks did not anticipate

`ProcessOptions` lives at `com.embabel.agent.core.ProcessOptions` (not `spi`). Its state is `contextId`,
`identities`, `blackboard`, `verbosity`, `budget`, `processControl`, `prune`, `ephemeral`, `listeners`,
`outputChannel`, `plannerType`, `toolCallContext`, with `withBudget`, `withProcessControl`,
`withPlannerType`, `withAdditionalEarlyTerminationPolicy` and friends.

It carries the action, cost and token budget and the planner type. It does **not** carry a tool-loop
iteration ceiling or a tool-not-found policy — those are per-`LlmInteraction` settings, which D15 already
says are reachable only from the Kotlin DSL or an `@Action` body. So task 8.3's list is reachable three
ways out of five: budgets and the planner yes, the ceiling and the tool-not-found policy not through
`ProcessOptions`. `runtime-guardrails`'s requirement that "the deployment SHALL be able to set the bounds
the platform does support — … the tool-loop iteration ceiling, and what happens when a model calls a tool
that does not exist" therefore needs either a different seam or a narrowed requirement, and that is a
ruling, not an implementation detail. Flagged when group 8 is reached.

## Not yet checked

Claimed once and not re-verified here, so treat as open until the group that needs it measures it:
`FileUtilsKt.resolveAndValidateFile` versus plain `resolvePath` per offered method on the upstream file
interfaces (task 3.5 asks for exactly this split, and asks that it be read from the addon's own test);
the `ToolGroupMetadata.of` overloads visible as `@JvmStatic` alongside the reflective route; whether
`LocalDirectoryReference`, `SpringResource`, `LiteralText`, `WebPage` and `LocalDirectory` are each
constructible from a pack path (task 7.1 builds three kinds and refuses the rest); and the exact
`ScriptLanguage` set each engine accepts, which decides task 6.2's "a language the engine does not take"
case.
