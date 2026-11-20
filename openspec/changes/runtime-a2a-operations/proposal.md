# Proposal

## Why

`docs/12-backlog.md` carries the item as two lines: *"a2a operations on runtime module: Send
Message, Send Streaming Message, Get Task, List Tasks, Cancel Task, Get Agent Card"* with the
constraint *"This should reuse embabel and Spring AI libs to handle a2a protocol definition"*. The
runtime today has no network surface at all: `prod/embabel-smith-runtime` boots with
`WebApplicationType.NONE` (`SmithRuntimeApplication`) and `spring.main.web-application-type: none`
(`application.yml`), so a spec source can be driven only by `smith run` or by the studio embedding
`AgentSpecRunService` behind its own `POST /api/run`. An agent that wants to consume an ingested
spec has nothing standard to talk to.

The backlog's constraint turns out to be the easy part and the interesting part at once. Embabel
ships its own A2A server — `com.embabel.agent:embabel-agent-a2a:1.5.4`, plus
`embabel-agent-a2a-autoconfigure` and `embabel-agent-starter-a2a`, all three verified present at
version 1.5.4 on `repo.embabel.com/artifactory/libs-release`, and the starter depends on
`spring-boot-starter-web:4.1.1`, which is this repo's Spring Boot. It provides the JSON-RPC dispatch,
the endpoint registration, the SSE streaming hook and an agent card **built from
`agentPlatform.goals`** — which is exactly what the runtime ingests. So the protocol definition is
genuinely borrowed rather than hand-written, and the card is derived rather than configured.

What is *not* provided is half the backlog's list, and that is the decision this change exists to
make rather than discover mid-implementation.

## What Changes

- **Five** operations are implemented against the protocol surface the library actually speaks
  (**A2A protocol version 0.3**, verified: `EmbabelServerGoalsAgentCardHandler` sets
  `.protocolVersion("0.3.0")` and the dispatch switches on the 0.3 method constants
  `message/send`, `message/stream`, `tasks/get`, `tasks/cancel`, `tasks/resubscribe` — a fixed set the
  spike confirmed from the compiled facade, and one the change cannot extend from a handler). The
  current published
  specification is **version 1.0** and renames every method to PascalCase (`SendMessage`,
  `GetTask`, …, spec §9.1) and moves discovery to
  `/.well-known/agent-card.json` (§8.2, §14.3). **The consequence is stated up front because it is
  the largest thing this change accepts: an A2A 1.0 client will not find this endpoint.** It ships
  0.3 and records the divergence; it does not hand-write a 1.0 binding, and it does not chase the
  SDK's `1.0.0.Alpha3` line.
- A second consequence of the same check: the backlog's *"reuse embabel and Spring AI libs"* cannot be
  honoured as written. No Spring AI A2A artifact exists on Maven Central — five coordinate probes all
  returned 404 and the local `spring-ai-bom` 2.0.2 has no `a2a` entry. The constraint's intent is met
  by `com.embabel.agent:embabel-agent-a2a:1.5.4` and the `io.github.a2asdk:a2a-java-sdk-spec` model
  classes it brings; the backlog wording is corrected as part of this change.
- **Get Agent Card** is the well-known-URI document, not an RPC method. The library registers
  `GET /{path}/.well-known/agent.json` and only exposes `GetExtendedAgentCard` as a method.
- **List Tasks is out, decided after the spike.** This bullet originally said listing would be
  implemented here rather than borrowed, served from the `TraceStore` SPI. The spike disproved that:
  the facade dispatches on the method string and throws `Method tasks/list is not supported` *before*
  any `A2ARequestHandler` is consulted, so a handler cannot serve it no matter what it implements. The
  only alternative was a route of our own outside the library's dispatch — a second protocol surface,
  which is exactly what the reuse ruling exists to avoid, for a local development endpoint. Ruled
  2026-11-19: **five operations.** A client that calls `tasks/list` gets the facade's HTTP 500, and
  that is recorded as a deviation rather than papered over. `tasks/get` still reads the trace store, so
  a task survives a restart without any registry of ours.
- **SendMessage is served by this repo's own handler, not by the library's `AutonomyA2ARequestHandler`.**
  That handler drives Embabel Autonomy directly, which would skip the three things the runtime
  guarantees: trust-tier enforcement, per-run planner selection, and the trace record the
  self-improvement loop depends on. Our handler delegates to `AgentSpecRunService`, so an A2A run is
  the same run a CLI or studio call makes.
- **Send Streaming Message is honest, not theatrical.** `AgentSpecRunService.run` blocks until the
  run completes and writes the trace once at the end, so the stream carries the task, its artifact
  and its terminal status — no invented per-step progress. Incremental step events are out of scope.
- A **new launcher module**, `prod/embabel-smith-runtime-a2a`, holds the servlet surface and is the
  only place the A2A starter appears. This is forced, not stylistic: `AgentA2AAutoConfiguration` is
  `ConditionalOnClass(AgentCardHandler, RequestMappingHandlerMapping)` and
  `ConditionalOnWebApplication(SERVLET)`, and the studio already depends on the runtime and is a
  servlet application — so adding the dependency to the runtime module would auto-configure an
  `/a2a` endpoint into the studio without anyone asking for one.
- The CLI launcher is unchanged and still boots as a non-web application.

## Capabilities

### New Capabilities

- `runtime-a2a`: the runtime's agent-to-agent surface — the five operations it serves, the agent card
  derived from the goals of the ingested source, the mapping of an A2A task onto one run and one trace
  record, the trust tier an A2A run executes under, which of those operations the reused library
  implements versus the ones this repo implements itself, and the operation it does not serve at all
  because the library cannot reach it.

### Modified Capabilities

None. There are no capability specs under `openspec/specs/` — they were consolidated into `docs/` on
2026-11-15 — so there is no existing requirement text to modify. The durable home for what this
change settles is `docs/07-runtime.md` (the runtime's surfaces) with a pointer from
`docs/05-architecture.md` (module map, the `Ai`-only model layer) and the item removed from
`docs/12-backlog.md`.

## Impact

- **`prod/pom.xml`**: gains `embabel-smith-runtime-a2a` in `<modules>`. The `ban-poc-dependencies`
  enforcer rule already covers every child, so the new module inherits it.
- **Root `pom.xml`**: `dependencyManagement` gains the new module and the A2A starter coordinates.
- **New module `prod/embabel-smith-runtime-a2a`**: depends on `embabel-smith-runtime` and
  `embabel-agent-starter-a2a`; holds the run-backed request handler, the trace-backed task lookup,
  the goal-filtered card, a servlet launcher and its own `application.yml` port.
- **`prod/embabel-smith-runtime`**: **unchanged, full stop.** This bullet originally warned that the
  change "may need a small seam — a way to look up a run by id and to list runs", which would have
  touched the `TraceStore` SPI and therefore the studio's `JpaTraceStore`. Both halves of that warning
  are now void: listing was cut (above), and lookup by id needs no new method because
  `TraceStore.read()` already returns every record in completion order, so the new module filters the
  list it is given. Verified against the SPI on 2026-11-19: it declares exactly `append(TraceRecord)`
  and `read()`. **This change therefore modifies no other module's interface** — which is the whole
  point of a `TraceStore` that reads back what every run appended.
- **`prod/embabel-smith-studio`**: must keep *not* exposing `/a2a`; a test asserts it.
- **`Makefile`**: a run entry point for the new launcher, matching the existing style.
- **Cost**: an A2A `SendMessage` is a full GOAP run, so it costs the same model calls as a
  `smith run`. The endpoint is env-driven and unconfigured endpoints fail the run rather than the
  boot, exactly as the CLI behaves today.
- **Depends on**: nothing. Independent of `builder-docs-ingestion` and `builder-live-example-tests`.
