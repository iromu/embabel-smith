# embabel-smith-runtime-a2a

The runtime's agent-to-agent surface. One module, one launcher, one endpoint:
`POST /a2a` plus the agent card at `GET /a2a/.well-known/agent.json`, A2A protocol 0.3,
served by [Embabel's A2A starter](https://github.com/embabel/agent) with this repo's
request handler behind it.

It is the only prod module that declares `embabel-agent-starter-a2a`, and therefore the
only one that gains `spring-boot-starter-web` and a servlet engine from it.

## What it is, and what it is not

A **local development surface**. Explicit non-default port (18090, never 8080), **no
authentication, no TLS, no localhost bind**. Anyone who can reach the port can start a
real GOAP run, which spends real billable model calls. That is a decision, not an
oversight; if the endpoint ever faces anything but a development machine, authentication
is a prerequisite, not a follow-up.

The module holds no model call of its own. Every run goes through
`embabel-smith-runtime`'s `AgentSpecRunService`, so an A2A run is the same run
`smith run` makes: same trust-tier gate, same planner selection, one `TraceRecord`
appended to the same JSONL file.

## Served operations

Five, and only five: `message/send`, `message/stream`, `tasks/get`, `tasks/cancel`, plus
the card document.

- **`tasks/list` is not served.** The library's JSON-RPC facade rejects it before any
  request handler runs — there is no 0.3 method for it to route — so no handler behind
  that facade can offer it. Listing would require a route outside the library's dispatch,
  which is the protocol surface the reuse ruling exists to avoid.
- **`historyLength` is required on `tasks/get`.** `TaskQueryParams.historyLength` is a
  primitive `int` and Jackson 3 ships `FAIL_ON_NULL_FOR_PRIMITIVES=true`, so omitting the
  field fails inside the facade with HTTP 500 (`Cannot map null into type int`) before
  this module's code runs. The field is documented as required rather than worked around,
  because relaxing the mapper would mean re-tuning a default inside a library this module
  does not own.
- **`tasks/cancel` never claims to stop anything.** A run is synchronous, and the trace
  record is written after it returns, so every task the store knows about has already
  reached a terminal state. Cancelling one returns the task in the state it is actually in
  with a status message saying it was not cancelled and why.

## Run parameters

The protocol request carries no spec source and no trust tier, so both are configured for
the endpoint once. The goal and the planner are per-request, in the message-send
`metadata` under `goal` and `planner`.

| Environment variable    | Property                    | Meaning                                          |
| ----------------------- | --------------------------- | ------------------------------------------------ |
| `SERVER_PORT`           | `server.port`               | Port to serve (default 18090)                    |
| `SMITH_A2A_SOURCE`      | `smith.a2a.source`          | Spec source every run executes against           |
| `SMITH_A2A_TRUST_TIER`  | `smith.a2a.trust-tier`      | `STANDARD` (default) or `HIGH`                   |
| `SMITH_A2A_TRACE_DIR`   | `smith.a2a.trace-dir`       | Trace directory (default `~/.smith/traces`)      |

## Running it

```bash
make run-a2a                                   # port 18090
SERVER_PORT=18091 SMITH_A2A_SOURCE=folder:/path/to/packs make run-a2a
```

```bash
curl -s http://127.0.0.1:18090/a2a/.well-known/agent.json
curl -s -X POST http://127.0.0.1:18090/a2a -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"message/send","params":{"message":{"role":"user","kind":"message","messageId":"m-1","parts":[{"kind":"text","text":"summarize it"}]},"metadata":{"goal":"explainDone"}}}'
```

## Why the launcher sets a system property

Spring Boot 4 re-binds `spring.main.*` from the environment at startup, so a programmatic
`.web(WebApplicationType.SERVLET)` loses to the `application.yml` the
`embabel-smith-runtime` jar ships (which says `none`), and the context comes up non-web
with every A2A bean absent. `SmithA2aApplication.main()` therefore sets
`spring.main.web-application-type=SERVLET` as a system property before `run()` — the same
override `embabel-smith-studio` carries for the same reason.

**The asymmetry that comes with it.** That override is the only thing that can turn a
server on inside a module whose dependency must not have one. `SmithRuntimeApplication` is
untouched: `smith run` boots `WebApplicationType.NONE`, exits with the CLI's exit code and
opens no listening socket — and `prod/embabel-smith-runtime` has no servlet engine on its
classpath at all, so it could not serve even if the property were set. Anyone who copies
the line into another launcher turns that module's CLI into a network surface.
`NoA2aEndpointTest` in the studio is the guard that notices.

## Why the library's auto-configuration is excluded

The library's own `AgentCardHandler` is an unconditional `@Bean`, and its registrar
registers one mapping per card handler bean — so declaring ours alongside it fails the
boot with an ambiguous mapping for `/a2a`. `AgentA2AAutoConfiguration` (which is only a
`@ComponentScan` of `com.embabel.agent.a2a`) is excluded at the launcher and the same scan
runs from `A2aConfiguration` with `A2AConfiguration` filtered out. The library's registrar,
streaming handler and task state manager are still the library's; only the handler that
would route goals through Embabel Autonomy — skipping the trust-tier gate and the trace
record — is replaced.
