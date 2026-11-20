---
title: "The A2A endpoint (make run-a2a)"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "prod/embabel-smith-runtime-a2a/src/main/java/com/embabel/smith/runtime/a2a/SmithA2aApplication.java"
  - "prod/embabel-smith-runtime-a2a/src/main/java/com/embabel/smith/runtime/a2a/RunBackedA2ARequestHandler.java"
  - "prod/embabel-smith-runtime-a2a/src/main/java/com/embabel/smith/runtime/a2a/SourceDerivedAgentCardHandler.java"
  - "prod/embabel-smith-runtime-a2a/src/main/java/com/embabel/smith/runtime/a2a/SpecSourceSkills.java"
  - "prod/embabel-smith-runtime-a2a/src/main/java/com/embabel/smith/runtime/a2a/A2aConfiguration.java"
  - "prod/embabel-smith-runtime-a2a/src/main/resources/application.yml"
  - "prod/embabel-smith-runtime-a2a/README.md"
  - "Makefile"
updated_at: "2026-11-19"
---

# The A2A endpoint (`make run-a2a`)

`prod/embabel-smith-runtime-a2a` is another front door onto the same run the CLI makes
([[smith-runtime]]): an agent-to-agent endpoint so a *different process* can ask this product to run
one of a spec's goals. It is the only `prod/` module that gains a servlet engine **from a dependency**
rather than declaring one (the studio declares its own, because it is the UI), and it holds no model
call of its own ([[model-calls-go-through-ai]]).

**Read this before starting it.** It is a local development surface: an explicit non-default port
(18090), **no authentication, no TLS, no localhost bind**. Anyone who can reach the port can start a
real GOAP run and pay for its model calls ([[model-endpoint]]).

## What is served

`GET /a2a/.well-known/agent.json` (the agent card) and `POST /a2a` (JSON-RPC), over **A2A protocol
0.3** — so a client expecting the current 1.0 specification will not find it. Launch with
`make run-a2a` (`SERVER_PORT=`, `SMITH_A2A_SOURCE=`, `SMITH_A2A_TRUST_TIER=`; see [[build-tooling]]).

**Five operations, never six:** `message/send`, `message/stream`, `tasks/get`, `tasks/cancel`, plus the
card. **Listing tasks is not one of them**: protocol 0.3 has no `tasks/list`, so the library's facade
rejects it before any handler runs and the client gets HTTP 500. That is a ruling, not a gap — the
alternative was a route outside the reused library's dispatch.

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

One skill per goal exported by the **configured source**, filtered by the same per-pack tool-group
check the run gate makes, so the card cannot advertise a goal the endpoint would refuse. `tags` and
`examples` are empty because a `goal.yml` declares neither. The source is read lazily, on the first
card request, never at boot; a source that cannot be read **fails the card** rather than serving an
empty skill list.

## Two traps

- `SmithA2aApplication.main()` sets `spring.main.web-application-type=SERVLET` as a **system
  property** — the same reason the studio does. Copy that line into another launcher and that module's
  CLI becomes a network surface.
- The library's card handler is an unconditional `@Bean`, so declaring ours alongside it fails the
  boot with an ambiguous mapping; `AgentA2AAutoConfiguration` is excluded and its scan re-run from
  `A2aConfiguration`. Replace the handler by that route only.

Related: [[smith-runtime]], [[runtime-run]], [[trust-tiers]], [[build-tooling]].
