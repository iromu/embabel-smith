# POC 13 — Remote / MCP invocation

The **remote half** of the "normal agent harness" that doc 03 §2.3 promises (POC 7 covered the
local-CLI half via a `shell` tool group). A skill's goal is published as an **MCP tool** over
Streamable HTTP, and a *separate* MCP client invokes it over the network — the "consume" side of
remote invocation, where the exported goal is exposed to external MCP clients just as the no-code
runtime ingests it locally.

Endpoint-gated — the server-side action calls the model, so it needs a live OpenAI-compatible
endpoint. The MCP round-trip itself (server + client over HTTP) works regardless; the model call is
the endpoint-gated part.

## What it proves

- **An exported goal is auto-published as an MCP tool.** An `@Action` + `@AchievesGoal` method
  annotated `@Export(remote = true)` is picked up by `embabel-agent-starter-mcpserver` and exposed
  as a tool over Streamable HTTP at `http://localhost:<port>/mcp` — no manual tool registration.
- **A separate MCP client can invoke the skill over the network.** The official Java MCP SDK client
  connects to the endpoint, lists the published tools, and calls the tool; the server-side GOAP
  planner formulates the plan, executes the action, calls the LLM, and returns the result to the
  client. The round-trip is a genuine HTTP call, not an in-process one.

## How it works

```mermaid
sequenceDiagram
    participant A as RemoteExplainerAgent<br/>@Export(remote=true)
    participant S as MCP server<br/>Streamable HTTP
    participant C as MCP client (separate connection)
    A ->> S: goal auto-published as tool
    C ->> S: initialize
    C ->> S: listTools
    C ->> S: callTool(explainTopic, {content, timestamp})
    S ->> S: GOAP plan → action → LLM call
    S -->> C: result (genuine HTTP round-trip)
```

`RemoteExplainerAgent` defines a single goal — `explainTopic(UserInput) → String` — with
`@Action`, `@AchievesGoal`, and `@Export(remote = true, name = "explainTopic")`. `RemoteMcpServerApp`
is a `@SpringBootApplication` that boots the MCP server (the default Streamable HTTP transport on the
app's web port) and, once it is up, connects to its own endpoint as an MCP client: `initialize` →
`listTools` → `callTool(explainTopic, {content, timestamp})`. It prints the published tools and their
input schemas, then the remote result, and shuts the context down so `spring-boot:run` terminates.

Two things that only surface in the live run:

- The goal method needs **both** `@Action` and `@AchievesGoal` to register as a goal. An
  `@AchievesGoal`-only method is rejected (`MISSING_ACTION_ANNOTATION` / `MISSING_GOALS`) and no tool
  is published.
- The tool's input schema mirrors the `UserInput` type, so the call must supply **both** `content`
  and `timestamp` (a `content`-only call fails schema validation).

## Run it

Endpoint-gated (needs the env vars from the root README). Pick a free port — the default 8080 may be
held by another local service — and point the client at the same port:

```bash
export OPENAI_CUSTOM_BASE_URL=<base-url>
export OPENAI_CUSTOM_API_KEY=<key>
export OPENAI_CUSTOM_MODELS=<model>
export EMBABEL_MODELS_DEFAULT_LLM=<model>

SERVER_PORT=18080 BUILDER_MCP_URL="http://localhost:18080" \
BUILDER_TOPIC="how a coffee machine works" \
    mvn -pl poc/poc-13-remote-mcp spring-boot:run
```

See `docs/04-poc-run-results.md`.
