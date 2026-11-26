# embabel-smith-a2a-starter

The agent-to-agent surface, as a starter library a host depends on: `POST /a2a`
plus the agent card at `GET /a2a/.well-known/agent.json`, A2A protocol 0.3, served by
[Embabel's A2A starter](https://github.com/embabel/agent) with this repo's request handler
behind it.

There is no launcher, no application class and no classpath `application.yml` here. The beans
arrive through `SmithA2aAutoConfiguration`, conditional on the A2A classes, a servlet web
application, and `smith.a2a.enabled=true` with no `matchIfMissing` — so **depending on this
module opens no endpoint and no port**; a host opts in by setting that property, and
`SmithA2aEnvironmentPostProcessor` then supplies the servlet mode and the default port the
beans need. The host that sets it is [`embabel-smith-studio`](../embabel-smith-studio/),
started with `make run-a2a`.

It is the only prod module that declares `embabel-agent-starter-a2a`, and therefore the only
one that brings `spring-boot-starter-web` and a servlet engine into a build. They take effect
in the process that depends on this one, which today is that studio — and the studio declares
this module in its own pom rather than inheriting it, because serving `/a2a` on request is now
part of its contract.

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

## Deviations from the specification

Everything this endpoint does that the A2A specification does not describe, in one place.
Each line was measured through the library's facade — the test named in the last column
posts the request and asserts the answer it actually got. "Non-goal" means the change
decided not to implement it, for the reason given.

| Deviation                                                                                                                                   | Cause                                                                                                                                                                                                                                          | Pinned by                                                                   |
| ------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| **`tasks/list` is not served: five operations, not six.** A client that calls it gets HTTP 500 with `Method tasks/list is not supported`.     | Protocol 0.3 has no `tasks/list` method, so the facade rejects it before any request handler runs. Offering it would mean a route outside the library's dispatch — the protocol surface the reuse ruling exists to avoid.                          | `A2aEndpointOverHttpTest.aMethodTheFacadeDoesNotDispatchIsAFiftyHundred…`   |
| **`historyLength` is required on `tasks/get`.** Omitting it answers HTTP 500 (`Cannot map null into type int`) before this module's code runs. | `TaskQueryParams.historyLength` is a primitive `int` and Jackson 3 ships `FAIL_ON_NULL_FOR_PRIMITIVES=true`. Documented as required rather than worked around: the mapper belongs to the library's facade, not to this module.                   | `A2aEndpointOverHttpTest.omittingThePrimitiveBackedHistoryLength…`          |
| **A method the facade does not route answers HTTP 500, not one of the section 9.5 error codes.**                                             | Upstream behaviour: its dispatch throws `UnsupportedOperationException` and the facade maps that to a 500 with a JSON-RPC error body. Accepted for v1 rather than routed around.                                                                 | `A2aDeviationRecordTest.aMethodTheFacadeDoesNotRouteAnswersHttp500…`        |
| **Discovery is served at `/{path}/.well-known/agent.json`, not at the current specification's `/.well-known/agent-card.json` (§8.2).**        | The library registers the 0.3 location and the card reports `protocolVersion: 0.3.0`. A client expecting the 1.0 specification will not find this endpoint, which is the point of the field. Non-goal: protocol 1.0.                            | `A2aAgentCardTest.discoveryIsServedAtTheLibrarysWellKnownAgentJsonPath…`    |
| **No push notification configuration (§3.1.7–§3.1.10).**                                                                                    | Non-goal. The card says so: `capabilities.pushNotifications` is `false`, so a client does not try. Runs are synchronous and the caller waits for the task.                                                                                       | `A2aAgentCardTest.theCardDocumentCarriesTheProtocolVersionAndDefaults…`     |
| **No `SubscribeToTask` (§3.1.6, `tasks/resubscribe`).**                                                                                      | Non-goal. There is nothing to resubscribe to: the run is synchronous and its record is written when it finishes, so a stream is over the moment it is opened.                                                                                   | `A2aDeviationRecordTest.taskResubscriptionIsNotServed`                      |
| **No extended agent card (§3.1.11, §9.4.6).**                                                                                               | Non-goal. There is no authenticated caller to issue it to. The card declares `supportsAuthenticatedExtendedCard: false` and the method answers 500.                                                                                              | `A2aDeviationRecordTest.theExtendedAgentCardMethodIsNotServed`              |
| **No card signing (§8.4).**                                                                                                                 | Non-goal, with the same reason as the absence of authentication: this is a local development surface. The served card carries no `securitySchemes` and no `security`.                                                                         | `A2aDeviationRecordTest.theCardDeclaresNoSecuritySchemes`                   |
| **`tasks/cancel` never stops a run.** It returns the task in the state it is actually in, with a status message saying it was not cancelled.   | The trace record is written after the run returns, so every task the store knows about is already terminal. Claiming a stop would be a fiction; a real in-flight cancel needs the run made durable first, which is a different change.           | `A2aEndpointOverHttpTest.cancellingAFinishedTaskRefusesOverTheWire`         |


## Run parameters

The protocol request carries no spec source and no trust tier, so both are configured for
the endpoint once. The goal and the planner are per-request, in the message-send
`metadata` under `goal` and `planner`.

| Environment variable    | Property                    | Meaning                                                                                       |
| ----------------------- | --------------------------- | --------------------------------------------------------------------------------------------- |
| `SMITH_A2A_ENABLED`     | `smith.a2a.enabled`         | The opt-in. Unset or `false`: no beans, no endpoint, no port. `true`: the surface, plus the servlet mode and the `18090` default it needs |
| `SERVER_PORT`           | `server.port`               | Port to serve (default 18090)                                                                 |
| `SMITH_A2A_SOURCE`      | `smith.a2a.source`          | Spec source every run executes against, and the only thing the card's skills come from — unset, the card answers 500 |
| `SMITH_A2A_TRUST_TIER`  | `smith.a2a.trust-tier`      | `STANDARD` (default) or `HIGH`                                                                |
| `SMITH_A2A_TRACE_DIR`   | `smith.a2a.trace-dir`       | Trace directory (default `~/.smith/traces`)                                                   |

## The agent card and the trust tier

`GET /a2a/.well-known/agent.json` advertises one skill per goal of the **configured
source** (`smith.a2a.source`), minus the goals whose pack the configured tier cannot run —
tool groups are declared on steps, so a goal's tier requirement comes from the pack that
exports it. The card and the run gate make the same per-pack check, so they cannot disagree
about which packs are the problem.

The card is derived from the source rather than from `AgentPlatform.getGoals()` because
that set is a deployment history, not a description of the endpoint: every run ends in
`platform.deploy(agent)`, so it is empty until something has been ingested. Only the skill
list changed source — every other card field is still the one
`EmbabelServerGoalsAgentCardHandler` sets, asserted through HTTP by `A2aAgentCardTest`.
`tags` and `examples` are empty because a `goal.yml` declares neither; those two lists
belong to `@Agent` on a Java class, and nothing is invented to fill them.

**A card entry describes a capability; it is not a host for one.** Nothing in this module
assembles an Embabel `Skills` or holds a `SkillScriptExecutionEngine`, so a skill the card
advertises says what the endpoint's source can be asked to do, not that a published skill
folder is loaded here. The host for that is the builder's — `builder/skill/PublishedSkillHost`,
attached to a model call by `smith skill-run` — and this module's purpose did not change when it
landed: an A2A run is still the run `smith run` makes, with the tools the deployed pack's steps
declare. What a published folder does and does not give a reader is in
[`docs/08-builder.md`](../../docs/08-builder.md) §`skill`.

The source is read once, on the first card request, not at boot: resolving a `git:` source
during startup would let a source that is temporarily unreachable fail a boot, and
`SpecSource.resolve()` re-clones one on every call, so a per-request read would hit the
network on every card fetch. **A source that cannot be read fails the card request** with a
message naming it, which the library's facade turns into a 500. There is nothing to fall
back to now that the source is the only source of skills, and an empty skill list would
tell a client the agent can do nothing — false in the direction that hides a
misconfiguration. The failure is not cached, so the next request succeeds once the source
is readable again.

## Running it

This module is not runnable on its own — `make run-a2a` starts
[`embabel-smith-studio`](../embabel-smith-studio/) with `SMITH_A2A_ENABLED=true`, the repo's
only long-lived web application. The target passes `SERVER_PORT` too (18090 unless overridden), and
that is what puts the surface on 18090 rather than the contributed default: the studio's own
`application.yml` resolves `${SERVER_PORT:8080}`, and a host's own file outranks this module's port.
It also sets `SPRING_PROFILES_ACTIVE=a2a` — a profile with no config file, whose only job is to keep
the studio's default `local` profile, and the Docker Postgres it starts, out of a surface that needs
no database. The runtime's `main` runs the `smith` CLI, closes the context and exits, so it cannot
host a server; it cannot declare this module either, because this module depends on it.

```bash
make run-a2a                                   # port 18090
SERVER_PORT=18091 SMITH_A2A_SOURCE=folder:/path/to/packs make run-a2a
```

```bash
curl -s http://127.0.0.1:18090/a2a/.well-known/agent.json
curl -s -X POST http://127.0.0.1:18090/a2a -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"message/send","params":{"message":{"role":"user","kind":"message","messageId":"m-1","parts":[{"kind":"text","text":"summarize it"}]},"metadata":{"goal":"explainDone"}}}'
```

## Why this module ships no application.yml

Spring Boot 4 re-binds `spring.main.*` from the environment at startup, so the web application
type is decided by a config file rather than by a programmatic `.web(...)`. A library that
shipped an `application.yml` would therefore decide the web application type of every process
that depends on it — the one choice a library is not allowed to make — so this module ships
none. What it does instead is contribute the two properties the opt-in needs from
`SmithA2aEnvironmentPostProcessor`, and only when `smith.a2a.enabled=true`: servlet mode, and
`server.port` 18090 for a host that has not named a port. That source is added with `addLast`,
deliberately last, so a host's own config file, `SERVER_PORT` and `SPRING_APPLICATION_JSON` all
win. The consequence is part of the contract: a host whose own `application.yml` says
`spring.main.web-application-type: none` keeps `none`, because setting the flag against that
line is a contradiction the host resolves in its own file, not one this library overrules.
`embabel-smith-studio` is unaffected either way — it already states servlet mode, in its
`application.yml` and as a system property, because it reaches `embabel-agent-eval`, a headless
CLI tool that ships an `application.properties` which would outrank a `.yml`.

**The asymmetry that comes with it.** `SmithRuntimeApplication` is untouched: `smith run` boots
`WebApplicationType.NONE`, exits with the CLI's exit code and opens no listening socket. It
cannot declare this module in any case — this module depends on the runtime, so the edge would
be a Maven cycle — and it has no servlet engine on its classpath at all, so it could not serve
even with the flag set. A third-party host that wants the surface depends on this starter
directly. `NoA2aEndpointTest` and `A2aEndpointWhenEnabledTest` in the studio are the guards
that notice: the first asserts the flag unset registers nothing under `/a2a`, the second that
the flag set registers the mappings and the Smith card handler.

## Why the library's auto-configuration is excluded

The library's own `AgentCardHandler` is an unconditional `@Bean`, and its registrar
registers one mapping per card handler bean — so declaring ours alongside it fails the
boot with an ambiguous mapping for `/a2a`. `AgentA2AAutoConfiguration` (which is only a
`@ComponentScan` of `com.embabel.agent.a2a`) is therefore suppressed by
`SmithA2aEnvironmentPostProcessor`, which appends it to `spring.autoconfigure.exclude`: a
library cannot say `@SpringBootApplication(exclude = …)`, so the exclusion is applied from
the environment. It applies wherever this module's classes are on the classpath, not only
when the surface is enabled — leaving the library's Autonomy-backed `/a2a` live in the
disabled case would open an endpoint on a host that never asked for one. The same scan runs
from `SmithA2aAutoConfiguration` with `A2AConfiguration` filtered out. The library's
registrar, streaming handler and task state manager are still the library's; only the
handler that would route goals through Embabel Autonomy — skipping the trust-tier gate and
the trace record — is replaced.
