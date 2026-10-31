---
title: "Runtime adapter: CLI + remote/MCP invocation (POC 7, POC 13)"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "poc/poc-7-runtime-adapter/src/main/java/com/embabel/smith/adapter/AdapterRunner.java"
  - "poc/poc-7-runtime-adapter/src/main/java/com/embabel/smith/adapter/ShellCommandTool.java"
  - "poc/poc-7-runtime-adapter/src/main/java/com/embabel/smith/adapter/ShellToolGroupFactory.java"
  - "skill-packs/shell-runner/steps/runCli.yml"
  - "poc/poc-13-remote-mcp/src/main/java/com/embabel/smith/remote/RemoteExplainerAgent.java"
  - "poc/poc-13-remote-mcp/src/main/java/com/embabel/smith/remote/RemoteMcpServerApp.java"
updated_at: "2026-11-02"
---

# Runtime adapter: CLI + remote/MCP invocation (POC 7, POC 13)

POC 7 is the **runtime adapter** claim (doc 03 §2.3): a skill-pack is **not** locked to LLM-only
actions. A skill whose action declares `toolGroups: [shell]` gets the real `shell` tool injected by
the platform, so the skill can drive the host and run an actual CLI command — no orchestration
code. This is the **local-CLI half** of the "normal agent harness". POC 13 covers the **remote/MCP
half**: the same exported-goal-as-skill surface, published as an MCP tool and invoked over the
network. Both halves are now exercised (see [[what-the-pocs-prove]]).

## The `shell` tool group

- **`ShellCommandTool`** — the real executor behind the group. `runCommand` runs a command via
  `ProcessBuilder` (`bash -lc`) and returns a `CommandResult(exitCode, stdout, stderr)`. Pure and
  offline-testable.
- **`ShellToolGroup`** — a Spring `@Configuration` that exposes the group as a platform `ToolGroup`
  bean registered under the role `shell`.
- **`ShellToolGroupFactory`** — builds the `ToolGroup` from Java. Embabel's `ToolGroupMetadata` is
  a Kotlin interface whose only factory is a mangled companion and whose `getVersion()` is
  name-mangled, so it cannot be constructed directly from Java; the factory resolves the mangled
  companion method by signature and wraps it with `ToolGroup.Companion.ofTools`. The reflection is
  confined to this one class so the rest of the POC uses the clean `ToolGroup` API.

## The skill that uses it

`skill-packs/shell-runner/` declares `toolGroups: [shell]` on its `runCli` action (the rest is a
plain `UserInput → BackgroundMessage` skill). At run time the platform auto-registers the injected
group (`Registered tool groups: [math, shell]`), the action calls `runCommand({"command":"uname
-s"})`, and the goal returns the real output.

## How the run works

`AdapterRunner.run(packDir, input)` is POC 1's run path plus one addition: the
`StepSpecAgentScopeBuilder` is told the scope wants the `shell` group via a
`ToolGroupDescription` (so the platform injects `ShellCommandTool` for the `toolGroups: [shell]`
action). It loads and validates the pack, registers the types, assembles the flat step directory,
deploys, and runs a GOAP plan. The full run (goal achieved in ~4.5 s) is in
`docs/04-poc-run-results.md` §5. See [[runtime-run]] for the base path and [[trust-tiers]] for how
the same `shell` group is gated behind a trust tier.

## The remote/MCP half (POC 13)

The other half of the "normal agent harness": an exported goal exposed to **external** MCP clients
over the network, not just ingested in-process. `poc/poc-13-remote-mcp` boots
`embabel-agent-starter-mcpserver`, which auto-publishes an exported goal as an MCP tool over
**Streamable HTTP** at `http://localhost:<port>/mcp`.

- **`RemoteExplainerAgent`** — a single goal `explainTopic(UserInput) → String` annotated
  `@Action` + `@AchievesGoal` + `@Export(remote = true, name = "explainTopic")`. The `@Export`
  annotation is what the MCP server picks up and publishes.
- **`RemoteMcpServerApp`** — a `@SpringBootApplication` that boots the MCP server, then connects to
  its own endpoint as an MCP client (the official Java MCP SDK): `initialize` → `listTools` →
  `callTool(explainTopic, {content, timestamp})`. It prints the published tools + input schemas and
  the remote result, then closes the context so `spring-boot:run` terminates.

Two things that only surface in the live run:

- The goal method needs **both** `@Action` and `@AchievesGoal` to register as a goal. An
  `@AchievesGoal`-only method is rejected (`MISSING_ACTION_ANNOTATION` / `MISSING_GOALS`) and no
  tool is published.
- The tool's input schema mirrors the `UserInput` type, so the call must supply **both** `content`
  and `timestamp` (a `content`-only call fails schema validation).

Endpoint-gated (the server-side action calls the model). Run with a free port (8080 may be taken by
another local service) and the client pointed at the same port — see `poc/poc-13-remote-mcp/README.md`
and `docs/04-poc-run-results.md` §5. This is the "consume" side of [[exported-goal-as-skill]] across
processes.
