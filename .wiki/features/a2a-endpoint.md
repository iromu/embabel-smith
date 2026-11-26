---
title: "The A2A endpoint (make run-a2a)"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "prod/embabel-smith-a2a-starter/src/main/java/com/embabel/smith/a2a/SmithA2aAutoConfiguration.java"
  - "prod/embabel-smith-a2a-starter/src/main/java/com/embabel/smith/a2a/SmithA2aEnvironmentPostProcessor.java"
  - "prod/embabel-smith-a2a-starter/src/main/java/com/embabel/smith/a2a/RunBackedA2ARequestHandler.java"
  - "prod/embabel-smith-a2a-starter/src/main/java/com/embabel/smith/a2a/SourceDerivedAgentCardHandler.java"
  - "prod/embabel-smith-a2a-starter/src/main/java/com/embabel/smith/a2a/SpecSourceSkills.java"
  - "prod/embabel-smith-a2a-starter/src/main/java/com/embabel/smith/a2a/A2aConfiguration.java"
  - "prod/embabel-smith-a2a-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
  - "prod/embabel-smith-a2a-starter/src/main/resources/META-INF/spring.factories"
  - "prod/embabel-smith-a2a-starter/src/test/java/com/embabel/smith/a2a/SmithA2aEnvironmentPostProcessorTest.java"
  - "prod/embabel-smith-a2a-starter/pom.xml"
  - "prod/embabel-smith-studio/pom.xml"
  - "prod/embabel-smith-studio/src/main/resources/application.yml"
  - "prod/embabel-smith-studio/src/test/java/com/embabel/smith/studio/NoA2aEndpointTest.java"
  - "prod/embabel-smith-studio/src/test/java/com/embabel/smith/studio/A2aEndpointWhenEnabledTest.java"
  - "prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/AgentSpecRunService.java"
  - "prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/core/LifecycleBlockedException.java"
  - "docs/07-runtime.md"
  - "docs/12-backlog.md"
  - "prod/embabel-smith-a2a-starter/README.md"
  - "Makefile"
updated_at: "2026-11-26"
---

# The A2A endpoint (`make run-a2a`)

`prod/embabel-smith-a2a-starter` is another front door onto the same run the CLI makes
([[smith-runtime]]): an agent-to-agent endpoint so a *different process* can ask this product to run
one of a spec's goals. It is a starter library and the only `prod/` module that declares
`embabel-agent-starter-a2a`, so it is the only one that gains `spring-boot-starter-web` and a servlet
engine **from a dependency rather than by asking for one**; its beans are conditional on
`smith.a2a.enabled`, off by default, so **depending on it opens no endpoint and no port**. It ships no
launcher and no `application.yml` of its own: the host is `prod/embabel-smith-studio`, which declares
the starter in its own pom, and the only place the flag is set is `make run-a2a`. It holds no model
call of its own ([[model-calls-go-through-ai]]).

**Read this before starting it.** It is a local development surface: an explicit non-default port
(18090), **no authentication, no TLS, no localhost bind**. Anyone who can reach the port can start a
real GOAP run and pay for its model calls ([[model-endpoint]]).

## What is served

`GET /a2a/.well-known/agent.json` (the agent card) and `POST /a2a` (JSON-RPC), over **A2A protocol
0.3** — so a client expecting the current 1.0 specification will not find it. `make run-a2a` starts the
studio with `SMITH_A2A_ENABLED=true` (the env form of `smith.a2a.enabled`) and `SERVER_PORT` (18090 by
default — the studio's own
`application.yml` resolves `${SERVER_PORT:8080}`, so the port arrives through the environment) and
`SPRING_PROFILES_ACTIVE=a2a`, a profile with no config file. Naming a profile that does not exist is
what keeps the studio's default `local` profile out of a surface that needs no database — including
the Docker Postgres that profile brings, whose compose path is repo-root relative and so unresolvable
from the module-directory run `spring-boot:run` makes. The launcher passes `SMITH_A2A_SOURCE=` and
`SMITH_A2A_TRUST_TIER=` through the same way (see [[build-tooling]]); the starter's own
`README.md` carries the full `SMITH_A2A_*`-to-property table, including `SMITH_A2A_TRACE_DIR`
(default `~/.smith/traces`), and `docs/07-runtime.md` §"A2A endpoint" is the runtime-facing version of
the same page.

**Five operations, never six:** `message/send`, `message/stream`, `tasks/get`, `tasks/cancel`, plus the
card. **Listing tasks is not one of them**: protocol 0.3 has no `tasks/list`, so the library's facade
rejects it before any handler runs and the client gets HTTP 500 reading
`Method tasks/list is not supported`. That is a ruling, not a gap — the
alternative was a route outside the reused library's dispatch, recorded as decided in
`docs/12-backlog.md` — and a client does not need it: `message/send` returns the task and `tasks/get`
reads it back by id, and a caller that lost an id can read the trace file directly.

## How a request becomes a run

The handler is `RunBackedA2ARequestHandler`, deliberately replacing the library's own, which would
have run the goal through Embabel Autonomy and skipped both the tier gate and the trace
([[trust-tiers]]).

- **One run is one task, and the task id is the trace `runId`.** `tasks/get` reads the JSONL trace
  store, so no second task registry exists and a task survives a restart.
- A success is a `COMPLETED` task carrying the goal output as an artifact; a failure (missing goal,
  tier block, unreadable source) is a `FAILED` task carrying the run's error.
- The **trust tier** and the **spec source** are configured once for the endpoint
  (`smith.a2a.trust-tier`, default `STANDARD`; `smith.a2a.source`) because the protocol request carries
  no caller identity and no source. Goal and planner *are* per-request, in the message `metadata` under
  `goal` and `planner` ([[spec-sources]]).
- **Nothing stops a run.** The trace record is written after the run returns, so every stored task is
  already terminal: `tasks/cancel` refuses and says so, and `message/stream` emits the task, its
  artifact and a terminal status, with no invented progress.

## The agent card

One skill per goal exported by the **configured source**, filtered by `SpecSourceSkills.tierAllows` —
the same per-pack tool-group check (declared groups × tier) the run gate makes. `tags` and
`examples` are empty because a `goal.yml` declares neither. The source is read lazily, on the first
card request, never at boot; a source that cannot be read **fails the card** rather than serving an
empty skill list. A *readable* source that simply holds no packs still yields `skills: []`, and it
should: a `folder:` source reads the immediate subdirectories of the path it names, so pointing it at
one pack (`folder:…/agent-specs/explainer`) rather than at a directory of packs is a correct read of
nothing, not a failure.

**The agreement is tier-only.** The card's filter never consults `lifecycle.yml` — nothing in this
module reads it — so a goal exported only by a retired pack stays on the card, and `message/send` for
it returns a `FAILED` task reading `Run blocked by lifecycle state` ([[spec-lifecycle]]). The card can
advertise a goal the endpoint refuses.

## Two traps

- **The flag is the surface, and the surface is an explicit opt-in.** `smith.a2a.enabled` has no
  `matchIfMissing`, so a host that merely depends on the starter registers nothing and opens no port —
  which is the rule as `docs/07-runtime.md` now states it: no process exposes an A2A
  endpoint unless the surface is enabled *in that process*, and the command-line runtime cannot expose
  one at all because it never depends on the starter. The flag also carries what the surface needs in
  order to serve: when it is `true`, `SmithA2aEnvironmentPostProcessor` adds one lowest-precedence
  source (`addLast`) carrying servlet mode and the `18090` default, so the host's own config files,
  `SERVER_PORT` and `SPRING_APPLICATION_JSON` all outrank it; with the flag absent that source
  contributes nothing, and a host whose own file states `spring.main.web-application-type: none` keeps
  `none`. Who can host it is forced rather than chosen: the studio declares the starter, and
  `embabel-smith-runtime` cannot — the starter's `A2aConfiguration`, `RunBackedA2ARequestHandler` and
  `SpecSourceSkills` import `AgentSpecRunService`, `TrustTier`, `TrustTierPolicy` and `JsonlTraceStore`
  from the runtime, so the reverse edge is a Maven cycle no scope hides, and the CLI closes its context
  and exits besides. Wiring another host means declaring the starter and setting the flag, and nothing
  else. `NoA2aEndpointTest` asserts the studio's default state — no request mapping registered under
  `/a2a`, and the card route does not answer with a card — and `A2aEndpointWhenEnabledTest` the opt-in.
  The assertion is on the mappings rather than on a 404 because the studio's SPA catch-all answers 200
  (405 to a `POST`) to any unmapped path, so a status code would not tell the two cases apart.
  `SmithA2aEnvironmentPostProcessorTest` pins the same contribution from the other side: servlet mode
  and the port with the flag on, neither with it off or absent, and a host's own configuration
  outranking it.
- The library's card handler is an unconditional `@Bean`, so declaring ours alongside it fails the
  boot with an ambiguous mapping. `AgentA2AAutoConfiguration` is appended to
  `spring.autoconfigure.exclude` by `SmithA2aEnvironmentPostProcessor` — a library cannot say
  `@SpringBootApplication(exclude = …)`, so the exclusion comes from the environment — added `addFirst`,
  the opposite precedence to the surface source, so it outranks a host's own file — and it applies
  wherever the library's classes are on the classpath, not only when the surface is enabled. A host's
  own exclusions are carried through rather than replaced, and one it already listed is not listed
  twice. The library's
  own scan then runs from `SmithA2aAutoConfiguration` with `A2AConfiguration` filtered out. Replace the
  handler by that route only.

Related: [[smith-runtime]], [[runtime-run]], [[trust-tiers]], [[build-tooling]].
