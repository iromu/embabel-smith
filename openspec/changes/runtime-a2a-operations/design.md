# Design

## Context

**The protocol, as fetched.** `https://a2a-protocol.org/latest/specification/` was fetched this
session; the extracted text is the basis for every claim below, cited by section number. The
operations are defined protocol-neutrally in §3.1 (§3.1.1 Send Message, §3.1.2 Send Streaming
Message, §3.1.3 Get Task, §3.1.4 List Tasks, §3.1.5 Cancel Task, §3.1.11 Get Extended Agent Card) and
bound to JSON-RPC in §9.4 (§9.4.1 `SendMessage`, §9.4.2 `SendStreamingMessage`, §9.4.3 `GetTask`,
§9.4.4 `ListTasks`, §9.4.5 `CancelTask`, §9.4.6 `SubscribeToTask`, §9.4.8 `GetExtendedAgentCard`).
§9.1 states the naming rule verbatim: *"**Method Naming:** PascalCase method names matching gRPC
conventions (e.g., `SendMessage`, `GetTask`)"*, with streaming over Server-Sent Events.

There is **no "Get Agent Card" JSON-RPC method** in the core set. The public card is a document at a
well-known URI — §8.2: *"**Well-Known URI:** Accessing
`https://{server_domain}/.well-known/agent-card.json`"*, restated in §14.3 — and only the
authenticated *extended* card is a method (§9.4.8), gated on `capabilities.extendedAgentCard`.

Core shapes: Task (§4.1.1) with `id` and `status` required and `contextId`, `artifacts`, `history`,
`metadata` optional; TaskState (§4.1.3); Message (§4.1.4) with `messageId`, `role`, `parts` required;
Part (§4.1.6) carrying exactly one of text/file/data; Artifact (§4.1.7) with `artifactId` and `parts`
required; AgentCard (§4.4.1).

**The library, verified from artifacts on this machine and from the Embabel release repo.**

| Coordinate | Evidence | Status |
| --- | --- | --- |
| `com.embabel.agent:embabel-agent-a2a:1.5.4` | `curl` of its `.pom` on `repo.embabel.com/artifactory/libs-release` → HTTP 200; its `maven-metadata.xml` lists `<release>1.5.4</release>`. POM description: *"Embabel Agent To Agent Protocol"*. Depends on `embabel-agent-api:1.5.4`, `spring-webmvc:7.0.9`, `jakarta.servlet-api:6.1.0`. | **verified resolvable** |
| `com.embabel.agent:embabel-agent-a2a-autoconfigure:1.5.4` | `.pom` → HTTP 200. Depends on `embabel-agent-a2a:1.5.4`, `spring-boot-autoconfigure:4.1.1`. | **verified resolvable** |
| `com.embabel.agent:embabel-agent-starter-a2a:1.5.4` | `.pom` → HTTP 200. Depends on `embabel-agent-starter:1.5.4`, `embabel-agent-a2a-autoconfigure:1.5.4`, **`spring-boot-starter-web:4.1.1`** — the repo's own `<spring-boot.version>` is `4.1.1`. | **verified resolvable, version-compatible** |
| `io.github.a2asdk:a2a-java-sdk-spec:0.3.3.Final` | Present in `~/.m2/repository` with jar, pom and sources. Its own POM's only compile deps are `jackson-databind` and `jackson-datatype-jsr310`; parent POM sets `<release>17</release>` and a Quarkus 3.28.2 BOM that the spec module does not use. | **verified in local repo** |
| `io.github.a2asdk:a2a-java-sdk-spec` newer line | Central `maven-metadata.xml` lists `0.3.4`, then `1.0.0.Alpha1`, `1.0.0.Alpha2`, `1.0.0.Alpha3`. | **verified from metadata; not downloaded** |
| Spring AI A2A | Five Central probes — `spring-ai-a2a`, `spring-ai-starter-a2a`, `spring-ai-starter-a2a-server`, `spring-ai-autoconfigure-a2a-server`, `spring-ai-a2a-server` — all HTTP 404. The local `spring-ai-bom` 2.0.2 POM contains no `a2a` entry. | **not found; see the open question** |

`embabel-agent-api:1.5.4` already declares `io.github.a2asdk:a2a-java-sdk-spec:0.3.2.Final` at
compile scope, so the A2A wire types are already transitive to this repo through the runtime's
existing `embabel-agent-starter` dependency. `com.embabel.build:embabel-build-dependencies:2.0.3`
pins `<a2a.sdk.version>0.3.3.Final</a2a.sdk.version>`.

**What the library actually does, read from its sources jar.**

- `A2AEndpointRegistrar` registers, per `AgentCardHandler`, `GET /{path}/.well-known/agent.json` and
  `POST {path}` (consumes `application/json`, produces `application/json` and
  `text/event-stream`).
- Its facade dispatches on the method string and handles exactly `SendStreamingMessageRequest.METHOD`,
  `TaskResubscriptionRequest.METHOD`, `SendMessageRequest.METHOD`, `GetTaskRequest.METHOD` and
  `CancelTaskRequest.METHOD`; anything else reaches
  `throw UnsupportedOperationException("Method ${method} is not supported")`.
- The 0.3 method constants, read from the SDK sources: `message/send`, `message/stream`, `tasks/get`,
  `tasks/cancel`, `tasks/resubscribe`, `agent/getAuthenticatedExtendedCard`. **There is no
  `tasks/list`** and no `ListTasksRequest` class in 0.3.3.Final.
- `EmbabelServerGoalsAgentCardHandler` builds the card from `agentPlatform.goals` filtered by a
  `GoalFilter`, through `FromGoalsAgentSkillFactory`, and sets `.protocolVersion("0.3.0")`,
  `streaming(true)`, `pushNotifications(false)`, `stateTransitionHistory(false)`,
  `supportsAuthenticatedExtendedCard(false)`.
- `A2AConfiguration` contributes one bean, `defaultAgentCardHandler`, at path `"a2a"` with
  `goalFilter = { true }` and the library's own `AutonomyA2ARequestHandler`.
- `TaskStateManager` keeps `activeTasks` and `completedTasks` as `ConcurrentHashMap`s with no list
  operation, so tasks are lost on restart.
- `AgentA2AAutoConfiguration` is registered through
  `META-INF/spring/…AutoConfiguration.imports` and its metadata records
  `ConditionalOnClass=com.embabel.agent.a2a.server.AgentCardHandler,org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping`
  and `ConditionalOnWebApplication=SERVLET`.
- The facade deserializes with `tools.jackson.databind.ObjectMapper` — Jackson 3 — while the SDK
  model classes and this repo's `jackson-databind` pin are Jackson 2. Both are on a Spring Boot 4
  classpath; the interaction is a spike question, not a known failure.

**The repo side, read this session.** `prod/embabel-smith-runtime` boots with
`WebApplicationType.NONE` and `application.yml` sets `spring.main.web-application-type: none`.
`AgentSpecRunService.run(...)` is a plain non-Spring class returning `RunResult(success, output,
error, trace)`; it catches resolution, ingestion, goal-selection, tier-block and abort failures into
a failed outcome and appends a `TraceRecord` for **every** attempt. `TraceStore` is the SPI with two
implementations: `JsonlTraceStore` (runtime) and `JpaTraceStore` (studio). `TraceRecord` carries
`runId`, `timestamp`, `source`, `pack`, `goal`, `input`, `planner`, `trustTier`, `steps`, `success`,
`error`, `outputExcerpt`. The studio reaches the same service through `RunController`'s `RunFn` seam
at `POST /api/run`. `prod/pom.xml` lists five modules and runs the `ban-poc-dependencies` enforcer
rule that every child inherits.

## Goals / Non-Goals

**Goals**

- Serve **five** of the backlog operations over the A2A protocol without hand-writing any part of the
  wire format or the JSON schema, using the Embabel A2A module and the SDK model classes it brings.
  Listing tasks is not among them — see decision 6 for why the reused library makes it impossible.
- Make an A2A run *the same run* as a CLI run: same trust-tier gate, same planner selection, same
  trace record.
- Derive the agent card from the goals of the ingested source rather than from a second, hand-kept
  description of them.
- Serve `tasks/get` from the trace store that already exists, so a task survives a restart without a
  second registry of our own.
- Leave the CLI and the studio behaving exactly as they do today.

**Non-Goals**

- A2A protocol 1.0. The reused library speaks 0.3; adopting 1.0 means either waiting for
  `a2a-java-sdk` 1.0.0 to leave Alpha or writing the binding by hand. Out of scope, recorded.
- Push notification configuration (§3.1.7–§3.1.10), `SubscribeToTask` (§3.1.6), the extended agent
  card (§3.1.11) and card signing (§8.4).
- Per-step live streaming events; the run's steps exist only after it completes.
- Multi-tenancy, authentication, and card signing — the endpoint is a local development surface,
  consistent with how the studio is described in `docs/09-studio.md`.
- Any change to `poc/`. POC 13 and POC 16 proved the MCP and remote-action bridges; an A2A bridge is
  a fourth surface and is product work, so it starts in `prod/`.

## Decisions

**1. Reuse `embabel-agent-starter-a2a:1.5.4`; do not hand-write the protocol.**

The alternative — writing the JSON-RPC envelope, the Task/Message/Part/Artifact/AgentCard shapes and
the SSE framing by hand — is what the backlog note explicitly forbids ("reuse embabel and Spring AI
libs to handle a2a protocol definition"), and it is the wrong trade even if the constraint were
absent: §4.1 defines five interlocking object shapes with required-versus-optional distinctions,
§4.1.6 makes Part a discriminated union, and §9.5 defines a structured error mapping. Getting those
wrong produces an endpoint that looks right and fails interop tests silently. The SDK model module
costs two Jackson dependencies and no container. Rejected.

**2. Ship protocol 0.3, and record the divergence instead of bridging it.**

The library's card sets `protocolVersion("0.3.0")` and its dispatch keys off the 0.3 method strings;
the published 1.0 spec renamed the methods (§9.1) and the discovery path (§8.2). Two honest options
existed: implement 0.3 with the library, or implement 1.0 by hand. The second contradicts decision 1,
and the SDK's own 1.0.0 line is at Alpha3. So: 0.3 now, and the divergence is a named, checkable
claim in the spec delta so nobody later mistakes this endpoint for a 1.0 server. The alternative of
silently accepting whichever the library does is rejected — the difference is visible to any client.

**3. A new launcher module, `prod/embabel-smith-runtime-a2a`.**

The alternative was to add the starter to `prod/embabel-smith-runtime` itself, which the backlog's
wording ("on runtime module") suggests. It does not work cleanly, for a reason visible only in the
auto-configuration metadata: `AgentA2AAutoConfiguration` activates on
`ConditionalOnWebApplication(SERVLET)` plus the presence of `AgentCardHandler` and
`RequestMappingHandlerMapping`. The studio depends on `embabel-smith-runtime` and is a servlet
application, so the transitive starter would auto-configure an `/a2a` endpoint into the studio —
exposing the studio's platform to the network without anyone asking. A second alternative, keeping the
dependency optional or `<scope>provided</scope>`, relies on a consumer's classpath discipline to
prevent a security-relevant default; that is the kind of "flexibility" that fails later. The new
module is one directory, inherits the enforcer ban, and makes the network surface a thing you have to
run deliberately. The runtime module keeps all the logic; the new module holds only wiring.

**4. Our own `A2ARequestHandler`, delegating to `AgentSpecRunService`.**

`A2AConfiguration` supplies `AutonomyA2ARequestHandler`, which runs goals through Embabel Autonomy on
the platform. That path does not consult `TrustTierPolicy`, does not honour the runtime's planner
selection, and does not write a `TraceRecord` — and `docs/07-runtime.md` states the trace is a
product requirement because *"the self-improvement loop has no other evidence source"*. Borrowing the
library's handler would therefore trade three invariants for convenience. We implement the interface,
call `AgentSpecRunService`, and translate `RunResult` into a Task. The library still owns every
request and response type; only the routing is ours.

**5. Task identity: one run is one task; the task id is the trace `runId`.**

`TraceRecord.runId` is already unique per attempt and is already persisted, so Get Task and List
Tasks read the trace store and no second task table appears. A failed run is a task in `FAILED` with
the trace's `error` in its status message — which also means the tier-block and goal-not-found cases
become visible tasks rather than HTTP errors, matching how the service already treats them as runs.
The library's `TaskStateManager` is not used for these two operations; it remains in play only for
streaming bookkeeping it does internally.

**6. Listing tasks is out of scope — decided by the spike, not by preference.**

This decision read "List Tasks is served from `TraceStore`" until the spike ran. The facade rejects
`tasks/list` before any `A2ARequestHandler` is consulted, so the design it described was not
implementable behind the library we are reusing, and serving it would mean registering a route outside
that library's dispatch — a second protocol surface of our own, which is what the reuse ruling was
avoiding. Ruled: **five operations**, and the omission is recorded as a deviation rather than worked
around.

What survives from the earlier reasoning is the part that never depended on listing: the SPI stays
alone. `TraceStore.read()` returning every record in completion order is enough for `tasks/get`, and
adding `list(filter, page)` to the SPI would force a change to `JpaTraceStore` and the JSONL store for
a query nothing now issues. If a future change wants listing, it needs both a route and a reason, and
that is its own change.

**7. The card is built from the configured spec source, one skill per exported goal.**

This decision read "the card is `EmbabelServerGoalsAgentCardHandler` with a `GoalFilter` that excludes
the goals the configured tier disallows" until wave 2a built exactly that and found the handler's goal
list is the wrong list. It comes from `AgentPlatform.getGoals()`, which is a view over the agents that
have been **deployed so far**, and `RuntimeCore.ingest()` ends in `platform.deploy(agent)` on every run.
So the set describes what this process has already done, not what this endpoint offers: it is empty at
boot, which means a client fetching the card before the first run is told the agent can do nothing. That
is the defect, and it reproduces — reverting to the library handler makes the card answer
`skills: []`.

**A second symptom I claimed here is false, and it is worth keeping the correction rather than deleting
it.** I wrote that repeated runs would duplicate the card's skills, on the reasoning that each run
re-deploys the source under a fresh scope name and the set accumulates. Wave 2a's implementer measured
it and it does not happen, and the bytecode says why: `AgentPlatform.getGoals()` is a default method
that flat-maps `agents()` and ends in `CollectionsKt.toSet`, and `Goal` is a Kotlin data class with
generated `equals`/`hashCode` over its components — so goals from a re-deployed source are value-equal
and the set collapses them. The card lists two skills after one run and after ten.

That correction does not rescue the library handler, because the emptiness at boot stands on its own and
because a set of deployed goals is the wrong thing to advertise even when it happens to be deduplicated:
it can only ever describe the past, and the endpoint's contract is about what it is configured to do.
It is also why the "one skill per goal" assertion stays in the suite as a **guard** rather than as a
fixed defect — it never broke, and if `Goal` ever stops being value-equal, it will.

Ruled: the card is built from `smith.a2a.source`. A class of ours reads the configured source, keeps the
packs whose declared tool groups the endpoint's tier allows, and constructs the card document using the
SDK's own `AgentCard`/`AgentSkill` types, registering through the library's `A2AEndpointRegistrar` like
before. So the card is correct at any moment: non-empty on the first fetch, exactly one skill per
exported goal, and no dependence on run history. It costs one class that assembles a wire object the
library used to assemble for us; the protocol types, the registrar and the dispatch all stay theirs, so
decision 1 is untouched.

The change is to the skill list specifically, not to the whole card. The card's identity fields stay the
library's, because none of them is history-dependent: `name` and `description` come from
`AgentPlatformProperties` (`embabel-default` / "Embabel Default Agent Platform" unless configured),
`version` is a constant, and `url` is still derived from the request plus `getPath()`. What stops being
an input is `AgentPlatform.getGoals()` — the mutable set that made the skill list wrong.

Two things survive from the earlier form. The tier predicate is unchanged: the same per-pack check
`RuntimeCore.checkTier` applies decides which packs appear, so the card and the gate still cannot
disagree. And the run still passes the tier to `AgentSpecRunService`, so the tier lands in the trace and
a policy block stays distinguishable from a bad spec — the invariant in `docs/05-architecture.md`.
Per-request tier selection remains out of scope: there is no authenticated caller identity to select
from.

**An unreadable source now fails the card request, and that is not a regression.** The earlier form could
fail *open*: if the source could not be read, advertise every goal the platform happened to hold, since
the run-time gate was the real enforcement. That option disappears with the platform's goal set — the
source is now the only thing a skill list can be built from, so failing open would mean serving an empty
card. An empty card tells a client the agent can do nothing, which is false and false in the direction
that hides a misconfiguration. So a card fetch that cannot read `smith.a2a.source` raises, which the
library's facade turns into a 500 naming the source — the same honesty the run path already shows, where
an unreadable source is a FAILED task naming it rather than an empty success. Boot still never reads the
source, which is what the lazy read is for, and a failure is not cached: a source that was briefly
unreachable must not leave the endpoint permanently unable to describe itself.

**8. Streaming delivers the completed run over SSE; no simulated progress.**

`AgentSpecRunService.run` blocks and the trace is written once, after the run. So the stream emits the
task, then its artifact, then a terminal status update, and closes. The alternative — emitting a
WORKING event per step — needs a callback into the platform's execution that the runtime does not
have, and faking it would put events on the wire that the run never produced.

**9. No model call is added, so the `Ai` invariant is untouched.**

`docs/05-architecture.md` records that `runtime` carries no `NoDirectModelRequestTest` guard because
it holds no model call, reaching the model only by executing actions on the platform. This change
adds a transport, not a model call: the handler calls `AgentSpecRunService`. The guard test is not
extended to the new module, and the new module must not gain one — stated as a requirement so the
next person does not "helpfully" add an HTTP client.

## Risks / Trade-offs

- **Protocol version is the dominant risk, stated plainly: an A2A 1.0 client will not find this
  endpoint.** Different method names (§9.1 PascalCase versus the 0.3 constants), different discovery
  path (§8.2 `/.well-known/agent-card.json` versus the library's `/{path}/.well-known/agent.json`).
  Mitigated only by the card's own `protocolVersion` field, which is what a well-behaved client reads
  and what a naive one does not. Chasing `a2a-java-sdk` `1.0.0.Alpha3` is explicitly rejected — an
  Alpha dependency in a product module to gain a version this repo's own platform release does not
  speak is the wrong side of that trade.
- **Jackson 2 and Jackson 3 coexist** on the classpath: the SDK models are Jackson 2 and the
  library's facade deserializes with `tools.jackson` (Jackson 3). This works on Spring Boot 4 today
  only in the sense that Embabel ships it; the spike must prove a round-trip rather than assume one.
- **The servlet flip.** Adding `spring-boot-starter-web` anywhere near the runtime risks the CLI
  starting a server. The new-module decision contains this to one launcher, but the spike must still
  confirm `smith run` is unaffected.
- **Blocking runs behind HTTP threads.** A GOAP run makes model calls and can take tens of seconds;
  an A2A client that keeps connections open will hold a Tomcat worker. Acceptable for a local
  development endpoint, and the reason push notifications and non-blocking `return_immediately`
  (§3.1.1) stay out of scope rather than being half-implemented.
- **No authentication, and deliberately no localhost bind.** The endpoint runs a real GOAP plan
  against a real model endpoint, spending billable calls, for anyone who can reach the port. The
  ruling taken is that this is a **local development surface** and the answer is to say so rather
  than to half-mitigate it: an explicit non-default port, no launcher started by any existing
  `Makefile` target, and `docs/07-runtime.md` naming the posture in the very sentence that introduces
  the surface. It is the same posture the backlog already accepts for `storage.json`, with one
  material difference — a wider surface and a running cost — which is why the disclosure is a
  requirement rather than a comment.
- **The library is upstream code we do not own.** Its dispatch throws `UnsupportedOperationException`
  for anything unrecognised and its facade returns HTTP 500 with a JSON-RPC error body. Our handler
  sits behind that facade, so an unmapped method surfaces as a 500 rather than the §9.5 error code.
  Accepted for v1; recorded as a known deviation rather than a claim of full conformance.

## Section 1 spike findings

Evidence, not intent. Every value below is copied from a command's output. The probe lived at
`.qwen/tmp/a2a-probe/` — outside the reactor (`prod/pom.xml` `<modules>` does not list it) and
outside version control — and inherited the real `embabel-smith-prod` parent so its answers are the
answers a future `prod/embabel-smith-runtime-a2a` module would give.

**Verdict: PROCEED**, with three corrections to the plan recorded below (the servlet flip, the
`tasks/list` route, and the omitted-primitive failure). None of them changes decision 1 or 2; the
first two change how section 2 and section 3 must be written, and the `tasks/list` finding was ruled on
after this section was written — **listing was cut rather than routed around, leaving five operations**
(decision 6).

### 1.1 Coordinates resolve, online and offline — PASS

`./mvnw -N dependency:get -Dartifact=com.embabel.agent:embabel-agent-starter-a2a:1.5.4` and the same
command with `-o` both end in `BUILD SUCCESS`; the offline run resolves `with transitive
dependencies` from `~/.m2` alone. Pinned coordinates:

- `com.embabel.agent:embabel-agent-starter-a2a:1.5.4` — resolves; the repo's own
  `${embabel-agent.version}` is the property the probe used, so task 1.1's pin is expressible as
  written.
- Deviation from the task text: the task says to add the pin to the root `pom.xml`. This spike was
  scoped to change no file but this one, so the pin was proven *usable* rather than *installed*.
  Installing it is section 2's first act.

### 1.2 One SDK version wins, and there was no conflict to mediate — PASS, plan premise wrong

`./mvnw -o -f .qwen/tmp/a2a-probe/pom.xml dependency:tree` reports exactly one `a2a-java-sdk-spec`
node and no omitted-for-conflict node:

```
[INFO] |  |  +- com.embabel.agent:embabel-agent-api:jar:1.5.4:compile
[INFO] |  |  |  \- io.github.a2asdk:a2a-java-sdk-spec:jar:0.3.2.Final:compile
[INFO] |  |  |     \- io.github.a2asdk:a2a-java-sdk-common:jar:0.3.2.Final:compile
```

**The winner is 0.3.2.Final, and Maven never had to choose.** The 0.3.3.Final the Context cites
never reaches this build: `embabel-build-dependencies:2.0.3` does pin
`<a2a.sdk.version>0.3.3.Final</a2a.sdk.version>`, but that BOM is not imported into this repo's
`dependencyManagement`, and a transitive POM's own dependency management does not apply to our
resolution. `embabel-agent-a2a:1.5.4` does not declare the SDK at all (zero `a2asdk` entries in its
POM) — it reaches the wire types through `embabel-agent-api:1.5.4`, which declares
`<version>0.3.2.Final</version>` explicitly. So the winner **is** the version the library was
compiled against, which is the outcome the design wanted and did not expect to get.

This matters because the two are genuinely different binaries, not one jar under two names:
`sha1sum` gives `7d8dc230365fea21b75a7bb74a96c5a6878df9ed` for 0.3.2.Final and
`c8d15d5c74e5cf77acdb1466126b194e980255c9` for 0.3.3.Final. A future root `dependencyManagement`
entry pinning 0.3.3.Final would be a real, untested upgrade, not a no-op. Do not add one.

Also resolved, both asked about:

- `spring-boot-starter-web:jar:4.1.1:compile` — yes, it really arrives, with
  `tomcat-embed-core-11.0.24.jar` behind it.
- `org.yaml:snakeyaml:jar:2.6:compile` — not 2.3. 2.6 is what the Spring Boot 4.1.1 BOM pins
  (`<snakeyaml.version>2.6</snakeyaml.version>`), so nothing is fighting it. Separately,
  `org.snakeyaml:snakeyaml-engine:jar:3.0.1` is also present; they are different artifacts and do
  not collide.
- `embabel-agent-eval` is **absent** — zero occurrences in the tree and zero on the resolved
  classpath. The jar-shipped `spring.main.web-application-type=none` landmine therefore cannot
  explain 1.4's result: the module's own configuration decided it.

### 1.3 The Jackson 2 / Jackson 3 round-trip works — PASS, with one named failure mode

The request leg is the library's own call, read from the bytecode of
`AgentCardHandlerWebFacade.handleJsonRpc`: `tools.jackson.databind.ObjectMapper.convertValue(...)`
targeting `io.a2a.spec.SendMessageRequest` — a Jackson 3 mapper building Jackson 2 annotated
models. Two probes: a direct mapper round-trip, and one driving the real package-private facade
with a recording `AgentCardHandler`.

Winner at runtime, printed by the probe: `a2a-java-sdk-spec-0.3.2.Final.jar`,
`tools.jackson.core/jackson-databind/3.1.5`, `com.fasterxml.jackson.core/jackson-databind/2.19.0`.

Posting a real `message/send` envelope, both mappers, `SPIKE jackson-roundtrip PASS`:

```
SPIKE j3 messageId OK   want=3f0e4c1a-2b6d-4b7a-9d1c-8e5f0a6b7c8d got=3f0e4c1a-...
SPIKE j3 role OK   want=USER got=USER
SPIKE j3 role wire spelling -> user
SPIKE j3 parts.size OK   want=1 got=1
SPIKE j3 part[0].kind OK   want=TEXT got=TEXT
SPIKE j3 part kind wire spelling -> text
SPIKE j3 SendMessageResponse json -> {"jsonrpc":"2.0","id":1,"result":{"role":"user",
  "parts":[{"text":"Summarize the quarterly report","kind":"text"}],
  "messageId":"3f0e4c1a-2b6d-4b7a-9d1c-8e5f0a6b7c8d","kind":"message"}}
```

`parts`, `role` and `messageId` survive both directions, identically under Jackson 2 and Jackson 3.
The polymorphic `Part` union resolves (`kind:text` → `TextPart`, text intact) and the enum spellings
map correctly (`"user"` ⇄ `Role.USER`, `"text"` ⇄ `Part.Kind.TEXT`). Jackson 3 sees the SDK's
annotations because `jackson-annotations` keeps the `com.fasterxml.jackson.annotation` package
across both majors — one artifact (`jackson-annotations-2.21.jar`) serves both mappers.

Driving the real facade end to end: `SPIKE HTTP status OK want=[200 OK]`, body class
`io.a2a.spec.SendMessageResponse`, inbound fields all preserved. And over live HTTP, section 1.5's
`message/send` reached `A2ARequestHandler - Received JSONRPC message message/send:
io.a2a.spec.SendMessageRequest`, so the Spring-wired Jackson 3 mapper behaves like the
hand-built one.

**The named incompatibility.** Jackson 3 changed one default:
`SPIKE j3 FAIL_ON_NULL_FOR_PRIMITIVES=true` versus `SPIKE j2 FAIL_ON_NULL_FOR_PRIMITIVES=false`.
So a wire field backed by a *primitive* that the client omits is a hard failure under Jackson 3
where Jackson 2 silently filled zero. It bites `tasks/get` today, live on the endpoint:

```
{"jsonrpc":"2.0","id":2,"error":{"code":500,"message":"Internal server error: Cannot map `null`
into type `int` ... (through reference chain:
io.a2a.spec.GetTaskRequest[\"params\"]->io.a2a.spec.TaskQueryParams[\"historyLength\"])
```

Sending `"historyLength":0` clears that particular error. The exposure is narrow and the SDK is
inconsistent about it — `TaskQueryParams` declares `int historyLength` while
`MessageSendConfiguration` declares `Integer historyLength` — so task 3.5 must either always send
the field or configure the mapper with `FAIL_ON_NULL_FOR_PRIMITIVES=false`. Record it in 5.4's
deviation list.

### 1.4 The CLI still boots non-web and opens no port — PASS

Booting the existing `SmithRuntimeApplication` with the starter on the classpath and the runtime's
own `spring.main.web-application-type: none`, against the `unreachable` test pack:

```
SPIKE 1.4 cli-exit-code=1
SPIKE 1.4 listening-sockets-while-running=NONE
SPIKE 1.4 no-servlet-engine-log-line     (no Tomcat/Jetty/Undertow line at all)
```

The socket check polled `ss -ltnp` for the process's own PID for its whole lifetime, so "no port"
is an observation, not an inference. The run was a real CLI run: it ingested the pack, deployed it
(`Deployed agent: unreachable`), attempted the goal, and wrote a trace record —
`"goal":"ghostDone"`, `"planner":"GOAP"`, `"trustTier":"standard"`, `"success":false`, error
`Goal 'ghostDone' not achieved (status=STUCK)`. The pack was chosen precisely because its plan
cannot be satisfied, so no action body runs and no model call is made; the spike was forbidden from
reaching an endpoint, and every provider was additionally pinned to an inert base-url. A successful
end-to-end run stays section 3's test.

### 1.5 The autoconfiguration activates, but only via the system property — PASS with a plan correction

Registered paths, verbatim from `RequestMappingHandlerMapping.getHandlerMethods()`:

```
PROBE 1.5 registered-mapping={GET [/a2a/.well-known/agent.json], produces [application/json]}
  -> com.embabel.agent.a2a.server.support.AgentCardHandlerWebFacade#agentCard(ServletRequest)
PROBE 1.5 registered-mapping={POST [/a2a], consumes [application/json],
  produces [application/json || text/event-stream]}
  -> com.embabel.agent.a2a.server.support.AgentCardHandlerWebFacade#handleJsonRpc(Map)
```

and from the registrar's own log:

```
INFO A2AEndpointRegistrar - Registering 1 A2A endpoints
INFO A2AEndpointRegistrar - Registering web endpoint under /a2a/.well-known/agent.json for
     EmbabelServerGoalsAgentCardHandler(path='a2a', agentPlatform=default-agent-platform)
```

Task 1.5's path is correct as written: `/a2a/.well-known/agent.json`, not the §8.2
`agent-card.json` location. That divergence runs the other way — protocol 1.0 moved discovery, so
this endpoint serves the 0.3 location and a 1.0 client will not find it. Measured, not asserted:
`GET /.well-known/agent-card.json` → `404`. The card itself came back `200` and reports
`protocolVersion=0.3.0`, `streaming=true`, `pushNotifications=false`,
`supportsAuthenticatedExtendedCard=false`, exactly as the Context describes.

**The servlet flip — the plan's assumption is wrong.** With the programmatic form task 2.3
prescribes, nothing happens:

```
PROBE 1.5 mode=programmatic
PROBE 1.5 context-class=org.springframework.context.annotation.AnnotationConfigApplicationContext
PROBE 1.5 env-web-application-type=none
PROBE 1.5 agent-card-handler-bean=NONE
PROBE 1.5 a2a-autoconfiguration-bean=NONE
```

Spring Boot 4 re-binds `spring.main.*` from the environment, so `.web(WebApplicationType.SERVLET)`
loses to the `application.yml` the runtime already carries. With
`System.setProperty("spring.main.web-application-type", "SERVLET")` before the run — the form
`prod/embabel-smith-studio` already uses — everything activates and a socket genuinely accepts
requests (`listening-sockets=[LISTEN ... *:18091 ... users:(("java",pid=...))]`, not merely a log
line):

```
PROBE 1.5 env-web-application-type=SERVLET
PROBE 1.5 context-class=...AnnotationConfigServletWebServerApplicationContext
PROBE 1.5 agent-card-handler-bean=defaultAgentCardHandler
PROBE 1.5 request-handler-bean=autonomyA2ARequestHandler
PROBE 1.5 a2a-autoconfiguration-bean=com.embabel.agent.autoconfigure.a2a.AgentA2AAutoConfiguration
PROBE 1.5 a2a-endpoint-registrar-bean=a2AEndpointRegistrar
```

**Task 2.3 must use the system property, not the programmatic call.** Note the asymmetry this
creates, which is worth a sentence in the module's own docs: the same override that turns the A2A
launcher on is the one thing that could turn a server on inside a module that must not have one.

### Two findings that constrain sections 2 and 3, not just the record

**The network surface is real and the transitive-starter risk is now proven, not argued.** The
starter puts `spring-boot-starter-web` and Tomcat on any module that declares it, so the runtime
module would gain a servlet engine it must not have — decision 3 stands. Stronger: the activation
above happened over `SmithRuntimeApplication`'s own context with *zero* extra wiring, because
`AgentA2AAutoConfiguration` gates only on
`ConditionalOnClass=...AgentCardHandler,...RequestMappingHandlerMapping` plus
`ConditionalOnWebApplication=SERVLET` (verbatim from the jar's
`spring-autoconfigure-metadata.properties`). Any servlet consumer of a starter-carrying runtime —
the studio is exactly that — gets `/a2a` automatically. Task 2.5's negative test is load-bearing.

**`tasks/list` cannot be served by our handler behind this facade.** The facade dispatches on the
method string and throws *before* reaching `A2ARequestHandler`:

```
{"jsonrpc":"2.0","id":3,"error":{"code":500,"message":"Internal server error:
 Method tasks/list is not supported" ...
 "className":"com.embabel.agent.a2a.server.support.AgentCardHandlerWebFacade"}}
```

Decision 6 said List Tasks is served from `TraceStore`, and the Goals promised six operations, but a
custom `A2ARequestHandler` is never consulted for a method the facade does not know. The two ways out
were a second registered mapping of our own outside the library's dispatch, or a scope reduction.
**Ruled on 2026-11-19: cut listing, ship five operations, record the deviation** — a route of our own
would rebuild the protocol surface the reuse ruling exists to avoid, for a local development endpoint.
Task 3.6 is therefore deleted rather than rewritten, and decision 6 now says what is *not* served.
Same shape of problem, smaller: an unmapped method answers HTTP 500 rather than a §9.5 code, confirmed
above — which is exactly what a client calling `tasks/list` will get, and the deviation record must say
so plainly instead of leaving it to be discovered.

### Where reality disagreed with the plan

| Plan said | Reality |
| --- | --- |
| 1.2 expects a 0.3.2/0.3.3 conflict Maven must resolve | No conflict: only 0.3.2.Final enters the tree; the 0.3.3 pin is in a BOM this repo does not import |
| 2.3 boots `WebApplicationType.SERVLET` programmatically | The programmatic form yields a non-web context; the system property is required |
| 3.6 serves `tasks/list` from a handler implementing the library interface | The facade rejects `tasks/list` before the handler runs |
| 3.5 `tasks/get` reads the record for the id | Omitting `historyLength` fails in Jackson 3 before the handler runs |
| 1.1 add the pin to the root `pom.xml` | Not done here — this spike changes no file but this page; the pin is proven usable |

One caveat about the substrate: `embabel-smith-runtime:0.1.0-SNAPSHOT` in `~/.m2` predates the
latest commit touching that module, which only *added* the `remote/` package. Nothing on the boot,
CLI or launcher path differs, so 1.4's and 1.5's results hold; a section 2 module built inside the
reactor will not depend on the installed jar at all.
