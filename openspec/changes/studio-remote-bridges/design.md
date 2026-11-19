# Design

## Context

See `proposal.md` for motivation; `specs/remote-bridge-invocation/spec.md` for the contract.

Facts this design has to work with, each read this session:

- The contract is three endpoints. `poc/poc-16-remote-action/.../RemoteActionSidecar.java` says it
  "exposes a deterministic action over the three endpoints Embabel's `embabel-agent-remote` client
  expects": `GET /api/v1/actions` (the catalog as `RestActionMetadata` JSON), `GET /api/v1/types`, and
  `POST /api/v1/actions/execute` with body `{action_name, parameters}`.
- The execute request names one action and nests its input. The sidecar reads
  `req.path("parameters").path("userInput").path("content")` and returns a `BackgroundMessage`-shaped
  object, `{"content": ..., "timestamp": ...}`.
- The POC pane already proved the degradation shape. `poc/poc-22-unified-ui/.../RemoteController.java`
  returns `RemoteResponse.notRunning("backing process not running: " + ...)` on any exception, with a
  3-second connect timeout and a 10-second request timeout.
- The POC pane hardcodes the action: `private static final String ACTION_NAME = "remoteDigest";`.
- Planner integration needs a library the product does not have. `poc/poc-16-remote-action/pom.xml`
  carries `embabel-agent-remote`; the launcher does
  `RestServer restServer = new RestServer(registration, restClient, objectMapper);` then
  `AgentScope restScope = restServer.agentScope(this.platform);` and deploys that scope. Neither the
  dependency nor an equivalent appears under `prod/`.
- The pack format cannot name a remote step: `docs/agent-spec-format.md` has no match for
  `remote|mcp|sidecar|RestAction`.
- The MCP half is a client-side library too. `poc/poc-13-remote-mcp/.../RemoteMcpServerApp.java`
  drives `McpClient.sync(HttpClientStreamableHttpTransport...)`, `client.listTools()` and
  `client.callTool(...)`; `modelcontextprotocol` is in no `prod/` pom.
- POC 19's gate shows what a docker-managed dependency costs: `ServingGate.check` fails with
  "Docker daemon not reachable. The model server runs in a container defined in this module's
  compose.yml; start Docker and re-run." Docker reachability is itself something to fail on.
- The studio's rule is unchanged: `docs/09-studio.md` — "It holds **no capability of its own**".

## Goals / Non-Goals

**Goals:**

- A person sees which remote bridges the installation configured, which of them answer, and what each
  one can do.
- A person runs one advertised action from the browser and sees the output the *sidecar* produced.
- An absent sidecar is a legible state with the URL that was probed, not a 500.
- Nothing is started, and nothing is claimed about a process the product did not run.

**Non-Goals:**

- Not process supervision, and not a container runtime (D1).
- Not planner integration: no GOAP plan in the product runs over a remote action in this change (D2).
- Not an MCP client (D3).
- Not a general remote-function UI: one text input, mapped to the action's `UserInput` (D6).

## Decisions

**D1 — Sidecars are started by hand; the studio never spawns one.**
The backlog left this open and noted "the gating logic is identical either way", so nothing is lost by
taking the smaller half. Four reasons. (a) Spawning is a subsystem, not a call: start and stop, port
allocation to avoid collisions, log capture, crash and orphan handling, and a lifecycle to display —
exactly the kind of speculative breadth `AGENTS.md`'s Simplicity First rules out. (b) POC 19's Dev
Services route makes Docker a *product* dependency for a dev UI, and POC 19's own gate exists because
Docker reachability fails on real machines; the product would inherit that failure mode as a feature.
(c) The product's whole posture toward external resources is "the operator configures it, the app
degrades clearly when it is absent" — that is the storage endpoint and the model endpoint story
already. (d) The pane's value is *legibility about* a bridge, and it delivers that without owning the
process. Rejected: an app-managed compose file, for (b); rejected: a "Start sidecar" button that
launches a jar path from the browser, which is a remote-code-execution surface in a tool that binds a
port.

**D2 — No planner integration in this change.**
POC 16's *interesting* result is that GOAP plans over a sidecar action exactly like a local one. The
product cannot expose that today: a pack has no step that names a remote action, so adding it means
changing the agent spec format — a format decision that `docs/agent-spec-format.md` and the validator
both have to carry — plus the `RestServer`/`agentScope` wiring and `embabel-agent-remote` as a runtime
dependency. Rejected: smuggling a remote step in behind the format, which produces packs the validator
accepts and the docs do not describe. This change ships the bridge as *visible and invocable*, and
leaves planner integration as the named follow-on, so the smaller half is not mistaken for the whole.

**D3 — The MCP endpoint is reported as configured, never probed and never invoked.**
`docs/04-poc-run-results.md` §7.2 proved the MCP bridge with a real client, and productizing it means
the studio becomes an MCP client: the SDK as a dependency, plus a generated form per arbitrary tool
`inputSchema`. Rejected: a fake "reachable" badge derived from a bare `GET` against an MCP endpoint,
because Streamable HTTP answers such a request in a way that does not mean *the tool works* — a badge
that lies is worse than no badge. The pane therefore states the configured endpoint and that the
product has not invoked it.

**D4 — The bridge client lives in the runtime module, not the studio.**
POC 13 and POC 16 are catalogued as *runtime bridges* (`docs/04-poc-run-results.md` §7.2), and a
bridge is about running work, not displaying it. It also means the CLI can reach the same class the
pane does, which is the pattern `docs/07-runtime.md` states for the run path: "Every CLI wraps a plain
service class ... so the studio can embed the same capability". Rejected: putting it in the studio,
which breaks that module's stated rule; rejected: the builder, which is about producing packs and has
no business talking to a running sidecar.

**D5 — Action names come from the sidecar's catalog, never from the product.**
The POC hardcoded `remoteDigest`, which is POC fixture code. The product lists what
`GET /api/v1/actions` returns and executes one of those by the name the sidecar gave it, so any sidecar
speaking the contract works and none of this repository's fixtures leak into the product.

**D6 — One text input, mapped to the action's `UserInput`.**
The contract's execute body carries `parameters.userInput.content`, and every action the evidence
covers consumes `UserInput` and produces `BackgroundMessage`. An advertised action whose inputs are not
a single `UserInput` is listed with its declared input types and marked as not invokable from this
pane. Rejected: a schema-generated form per arbitrary input type, which is the same breadth as D3's
MCP form and belongs with whichever change adds a second real sidecar.

**D7 — Fixed short timeouts, and reachability is the catalog call.**
3-second connect and 10-second request timeouts, as the POC used. "Reachable" means
`GET /api/v1/actions` answered with a JSON array; a non-2xx, a timeout, or a body that is not an array
is unreachable with the reason kept. Rejected: a separate `/health` probe, which the contract does not
define, and rejected: an unbounded wait, which hangs a browser pane on a machine that is simply off.

## Risks / Trade-offs

- **The pane invokes a remote action; it does not prove one is useful.** → The pane shows the raw
  output the sidecar returned and nothing else, so there is no product-side interpretation to be wrong
  about.
- **Configured sidecars are contacted unauthenticated, over plain HTTP, on a dev machine.** → Stated
  rather than fixed, in the same terms `docs/09-studio.md` already uses for the plaintext storage
  password: a local development tool with no network exposure. The URLs are operator configuration, not
  browser input, so the pane is not an open proxy.
- **A slow sidecar blocks its request thread for up to 10 s.** → The timeout is the bound and the
  pane shows a pending state for the bridge being called; raising it is one constant.
- **Two implementations of the same client now exist** (POC 22's controller and the runtime's bridge).
  → Intended: `poc/` is frozen evidence. The runtime copy is the one with the tests that matter.
- **Reporting MCP without probing it may read as a gap.** → It is one, and the pane says so in the
  same words the backlog uses; the alternative is a badge that claims more than it measured.
- **D2 leaves the more impressive POC result unwired.** → Named in the proposal's Impact as the
  follow-on change, with what it needs (a format decision, the runtime dependency, the scope wiring) so
  it is not lost.
