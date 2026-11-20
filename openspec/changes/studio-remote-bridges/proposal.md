# Proposal

## Why

Two POCs proved the two runtime bridges the product never wired. `docs/04-poc-run-results.md` §7.2
records the remote-action bridge live as "`remoteDigest` executed over HTTP to a separate process;
plan in ~0.13 s" with **0** LLM calls in the plan, and the MCP bridge as "`explainTopic` invoked over
Streamable HTTP by a separate client".

Verified 2026-11-18:

- `ls prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/rest/ | grep -Ei
  'retrieve|scale|remote'` → no output (exit 1). No remote controller exists.
- `grep -rn 'embabel-agent-remote' prod/ --include=pom.xml | wc -l` → `0`;
  `grep -rn 'mcpserver\|modelcontextprotocol' prod/ --include=pom.xml | wc -l` → `0`. Neither bridge
  library is a product dependency.
- `grep_search` for `remote|mcp|sidecar|RestAction` in `docs/agent-spec-format.md` → "No matches
  found". The pack format has no step that can name a remote action.
- `prod/embabel-smith-studio/frontend/src/panes/RemotePane.vue` wraps `PocOnlyPane` with
  `detail="Invoke a remote action (RestAction sidecar) or MCP tool over HTTP."`.

What the POCs hold that prod lacks is narrow and worth naming exactly. `poc/poc-16-remote-action`
gave two things: a sidecar that speaks Embabel's three-endpoint contract, and a launcher that pulls
that sidecar's actions into an `AgentScope` so GOAP plans over them. `poc/poc-22-unified-ui`'s
`RemoteController` proved the *pane* half: probe the sidecar, execute one action, and when the process
is absent return a body that says so rather than a stack trace. The POC pane hardcodes
`private static final String ACTION_NAME = "remoteDigest";` — which is fixture code, and the product
version must instead offer whatever the sidecar's own catalog advertises.

## What Changes

- **The runtime module gains a `remote/` package** with `RemoteActionBridge`: a client over the
  contract POC 16 documents — `GET /api/v1/actions` for the catalog, `GET /api/v1/types`,
  `POST /api/v1/actions/execute` to run one — over the JDK `HttpClient`, so no dependency is added.
  It is extracted from POC 22's controller, with the hardcoded action name removed: the caller names
  the action.
- **Bridges are configured, not discovered**: `smith.studio.remote-bridges`, a comma-separated list of
  `name@url`, following the shape `smith.studio.sources` already uses.
- **Two endpoints**: `GET /api/remote/bridges` returns each configured bridge with its reachability
  and its action catalog; `POST /api/remote/execute` runs one named action on one named bridge with a
  text input and returns the output content.
- **`/remote` becomes real**: `RemotePane.vue` lists the bridges with a reachable/unreachable state,
  the actions a reachable one exposes, an input box and the returned output — through the single
  `src/api/client.ts` fetch site.
- **A sidecar that is not running is a named state, never a 500.** The response carries the bridge
  name, the URL probed and the failure, in the same shape the studio already uses for
  `MODEL_NOT_CONFIGURED`.
- **The studio never starts, stops or supervises a process.** The backlog left this open ("Spawning
  remote sidecars from the UI (POC 19-style Dev Services) versus requiring them started by hand") and
  this change closes it: started by hand. See D1.
- **The MCP half is reported, not driven.** The pane shows a configured MCP endpoint as configured and
  unprobed. Making the studio an MCP client needs the MCP SDK, which is a new external dependency, and
  a form per arbitrary tool schema — that is its own change. See D3.
- **GOAP planning over a remote action inside the product runtime stays out of scope**, because the
  format has no step that could name one. See D2.

## Capabilities

### New Capabilities

- `remote-bridge-invocation`: the product's view of a remote-action sidecar — which bridges an
  operator configured, whether each one answers, which actions it advertises, running one of those
  actions on request and returning its output, and what the product is allowed to claim about a
  process it did not start. It also fixes that the product neither spawns nor supervises those
  processes.

### Modified Capabilities

None. There are no capability specs under `openspec/specs/` — consolidated into `docs/` on
2026-11-15 — so there is no existing requirement text to modify. The durable home for these rules is
`docs/09-studio.md` (the Surfaces table, the POC-only row) and a new bridge section in
`docs/07-runtime.md`, both named under Impact.

## Impact

- **`prod/embabel-smith-runtime`**: new `remote/` package holding the bridge client, extracted from
  `poc/poc-22-unified-ui/.../RemoteController.java` (the HTTP shape and the not-running body) and
  `poc/poc-16-remote-action/.../RemoteActionSidecar.java` (the endpoint contract it speaks). No new
  dependency: the client is `java.net.http`, and `embabel-agent-remote` is *not* added, because the
  product does not yet plan over remote actions.
- **`prod/embabel-smith-studio`**: `RemoteController` plus its DTOs, one configuration property, and
  the pane plus its client function and mirrored types.
- **`docs/07-runtime.md`**: a bridge section stating the contract the product client speaks and that
  planning over remote actions is not yet a product capability. **`docs/09-studio.md`**: the `/remote`
  row leaves the "none — POC-only stubs" row and the spawn-by-hand decision is recorded.
  **`docs/12-backlog.md`**: the "Remote invocation" bullet closes and the "Spawning remote sidecars"
  bullet is decided rather than open. Then a `.wiki/` re-ingest, wiki lint and republish.
- **`poc/`**: untouched — the extraction copies, which is what the enforcer ban on POC dependencies
  requires.
- **Deferred to its own change, named here so it is not lost**: planner integration (a pack step
  served by a remote action, which needs a format decision plus `RestServer(...).agentScope(platform)`
  wiring in the runtime) and the studio-as-MCP-client half.
