---
title: "The studio's Compose pane: a visual GOAP flow editor"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "prod/embabel-smith-studio/frontend/src/panes/ComposePane.vue"
  - "prod/embabel-smith-studio/frontend/src/compose/model.ts"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/rest/ComposeController.java"
  - "prod/embabel-smith-studio/frontend/src/__tests__/compose.model.test.ts"
  - "prod/embabel-smith-studio/src/test/java/com/embabel/smith/studio/rest/ComposeControllerTest.java"
updated_at: "2026-11-22"
---

# The Compose pane

The visual editor in the studio ([[smith-studio-web]]): drag action steps onto a canvas, wire them
to a goal by **type name**, and export the result as a publishable agent spec
([[agent-spec-format]]). It is the product version of POC 14's headless Composer and POC 15's canvas
([[spec-composer]]) — same idea, wired to the production publishers instead of a POC launcher.

## No model in the loop

This is the pane to reach for when you already know the shape of the flow. `POST /api/compose`
(`rest/ComposeController.java:42`) never calls an LLM: the browser assembles the pack and the server
validates, stamps, and publishes it. That makes it the only authoring path in the studio that works
with no model endpoint configured.

## The canvas

`panes/ComposePane.vue` renders a `VueFlow` graph with type-carrying handles. A connection is
accepted only when the output type of one node is the input type the next one needs — the gate is
`isValidConnection` (`ComposePane.vue:43,375-377`), so the editor cannot express a chain GOAP could not
plan.

The rules live outside the `.vue` file, in `frontend/src/compose/model.ts`, so they are testable
without a browser:

- `INITIAL_TYPES = ['UserInput']` (`:38`) — the types available before any step runs.
- `isValidConnection` (`:44`) — handle ids are `in:<Type>` / `out:<Type>`.
- `validateFlow` (`:59`) — every step input must be produced upstream or be an initial type.
- `assemblePack` (`:122`) — turns the graph into the pack JSON the server expects.
- `packToNodes` (`:224`) — a layered layout, used when loading an existing pack back in.

## What the server will refuse

The controller repeats the shape check server-side (`:75-97`) — never trust the canvas alone. A pack
is rejected unless it names a **source**, carries an **exported goal**, has **at least one step**, and
the goal's output type is **produced by some step**. That last rule is the interesting one: a flow
that can never reach its goal is not exportable, which is the same reachability question the GOAP
planner would otherwise discover at run time.

A pack that passes is stamped `INCUBATING` with a **0.0** eval baseline
(`LifecycleStamper.stamp(pack, 0.0)`, `:51`) — it is a new spec that has never been evaluated, so the
lifecycle machinery starts it at the bottom ([[spec-lifecycle]]). It then publishes through the
builder's `FolderPublisher` / `GitPublisher` to a `folder:` or `git:` source ([[spec-sources]]).

**Tests:** `compose.model.test.ts` (connection rules, flow validation, assembly) and
`ComposeControllerTest` (the server-side shape gate). Related: [[studio-process-workspace]] — the
wizard that generates a flow with a model, which you can then open here to adjust by hand.
