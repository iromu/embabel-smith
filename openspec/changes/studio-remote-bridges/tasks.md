# Tasks

Independent of `studio-retrieval-curation`: different module, different external resource, and one is
purely offline. Neither blocks the other.

## 1. Bridge client in the runtime

- [x] 1.1 Create a `remote/` package in `prod/embabel-smith-runtime` and define the value shapes: a
      bridge descriptor (`name`, `baseUrl`), an advertised action (`name`, `description`, declared
      input type names), and a bridge state (`name`, `baseUrl`, reachable flag, reason, actions), and
      verify a test builds each shape and reads its fields back.
- [x] 1.2 Write `RemoteActionBridge` over `java.net.http.HttpClient` with a 3-second connect timeout
      and a 10-second request timeout, exposing `probe()` against `GET /api/v1/actions` and
      `execute(actionName, input)` against `POST /api/v1/actions/execute` with body
      `{action_name, parameters: {userInput: {content}}}`, and verify the module's `pom.xml` gained no
      dependency.
- [x] 1.3 Decide reachability from the response, not from the absence of an exception: a 2xx whose body
      parses as a JSON array is reachable, anything else is unreachable with the status or failure kept
      as the reason, and verify offline tests against an in-process `HttpServer` — the precedent the
      runtime already uses for offline HTTP — cover a valid catalog, a 500, a non-array body, and a
      connection refused on a closed port.
- [x] 1.4 Parse the execution reply's `content` field and keep the raw body beside the status when the
      sidecar answers non-success, and verify tests assert the returned content for a 200, and that a
      400 reply carries the status and the sidecar's body rather than a fabricated result.
- [x] 1.5 Verify the whole package's tests pass offline, with no endpoint and no sidecar started by the
      build beyond the in-process stub.

## 2. Configuration and the studio endpoints

- [x] 2.1 Add `smith.studio.remote-bridges` to the studio's `application.yml` as a comma-separated
      `name@url` list with an env override, in the shape `smith.studio.sources` already uses, and
      verify a parser test covers a valid pair, a blank entry, and an entry with no `@` which is
      reported as malformed rather than guessed.
- [x] 2.2 Reject any request naming a bridge that configuration does not define, before any outbound
      call is made, and verify a test asserts no HTTP request leaves the process for an unknown name.
- [x] 2.3 Add `BridgeInfo`, `BridgesResponse`, `RemoteExecuteRequest` and `RemoteExecuteResponse` to
      `rest/dto`, the execute response carrying bridge, action, output, and separately the unreachable
      shape with the URL probed and the reason, and verify the field names match what the frontend
      mirror declares in the next section.
- [x] 2.4 Write `RemoteController`: `GET /api/remote/bridges` probes every configured bridge and
      returns the list, and `POST /api/remote/execute` runs one action on one named bridge, and verify
      by reading the class that the HTTP contract lives in the runtime's client and not in the
      controller.
- [x] 2.5 Write `RemoteControllerTest` on MockMvc against an in-process stub sidecar, covering: one
      reachable bridge with two advertised actions; one unreachable bridge reported in a 2xx response;
      an execution returning the sidecar's content; an execution against a dead bridge reported as
      unreachable rather than as a 500; and an unknown bridge name refused.
- [x] 2.6 Verify `make test-module MODULE=prod/embabel-smith-studio` passes with no endpoint
      configured and with no external sidecar running.

## 3. The remote pane

- [x] 3.1 Mirror the four DTOs in `frontend/src/api/client.ts`'s type imports and in
      `frontend/src/api/types.ts`, and add `listBridges()` and `executeRemote()` to `client.ts`, the
      single fetch site, and verify `npm run typecheck` (`vue-tsc --noEmit`) passes.
- [x] 3.2 Replace the `PocOnlyPane` wrapper in `RemotePane.vue` with a bridge list showing name, URL,
      reachable or unreachable with the reason, and the advertised actions; an action picker, an input
      field and a run button; and the returned output, keeping `PageHeader` and `SectionCard`.
- [x] 3.3 Show an unreachable bridge as a state on its row, not as a failed request, and keep the
      configured MCP endpoint in the view labelled as configured and not invoked, and verify a
      component test asserts the unreachable reason renders and that no "reachable" badge is rendered
      beside the MCP endpoint.
- [x] 3.4 Mark an advertised action whose declared inputs are not one `UserInput` as not runnable from
      this pane, and verify a component test asserts that action is listed with its input types and
      cannot be selected.
- [x] 3.5 Verify the frontend gates run green offline: the type gate, the Vitest logic tests and the
      component tests, with no backend and no sidecar running.

## 4. Decisions recorded

- [x] 4.1 Update `docs/09-studio.md`: move `/remote` out of the "none — POC-only stubs" row, leave
      `/scale` in it, and record the decision that sidecars are started by hand and that the studio
      never spawns a process, and verify the route table still matches `frontend/src/router.ts`.
- [x] 4.2 Add a bridge section to `docs/07-runtime.md` naming the three endpoints the product client
      speaks, the timeouts, and that planning a GOAP plan over a remote action is **not** yet a product
      capability and what it would need (a format decision, `embabel-agent-remote`, the scope wiring),
      and verify the page does not claim more than section 1 built.
- [x] 4.3 Close the "Remote invocation" bullet in `docs/12-backlog.md` and convert the "Spawning remote
      sidecars from the UI" bullet from an open question to a recorded decision with its reason, and
      verify the section's remaining bullets describe only what is genuinely unwired.
- [ ] 4.4 Re-ingest `.wiki/`, run the wiki lint and republish, and verify the lint reports no broken
      cross-links after those pages changed.

      **Not done, and not done here.** The `git-wiki` ruleset reads the committed `HEAD` and ignores
      uncommitted work, so an ingest run now would re-derive the pages as they were *before* this
      change and report a green over work it never saw. It is owed after the human commits these
      three pages (`09-studio.md`, `07-runtime.md`, `12-backlog.md`); ticking it here would be a
      recorded pass for a command that did not run.

## 5. Checks

- [x] 5.1 Verify `make lint` and `make build` are green offline, which is also the check that no POC
      dependency was introduced and that `embabel-agent-remote` did not sneak into a `prod/` pom.
- [x] 5.2 Start a process that speaks the contract by hand — the POC 16 sidecar class is the reference
      shape, a throwaway stub serving the three endpoints is enough — point
      `smith.studio.remote-bridges` at it, run one action from the pane and read the output, and
      verify the returned text is what that process produced rather than anything the product composed.
- [x] 5.3 Stop the sidecar and reload the pane, and verify the bridge is reported unreachable with the
      URL that was probed, and that the application stayed healthy and served every other pane.

## Evidence as run

- **Whole reactor:** `make test` → `BUILD SUCCESS`, 1449 tests
  (common 39, specsource 29, skillevaluator 942, runtime 82, builder 212, studio 145) against the
  1412 that was the baseline — +26 runtime, +11 studio, nothing else moved. `make build` and
  `make lint` (`0 Checkstyle violations`) both green. No `prod/` pom changed, and
  `embabel-agent-remote` appears in `prod/` exactly once: in a Javadoc line of
  `RemoteActionBridge` saying it is *not* wired here.
- **What "offline" meant here.** `make test` sources `~/.config/env/llm.env` and exports
  `OPENAI_CUSTOM_BASE_URL` whenever that file exists, and it exists on this machine — so these runs
  were **not** runs with no endpoint. The offline guarantee is the root pom's
  `test.excludedGroups=live`, which is what kept the endpoint-gated cases out of these totals.
- **Runtime:** 82 tests (was 56), `Tests run: 82, Failures: 0, Errors: 0, Skipped: 0`. Ten mutants
  killed, each verified to have landed before its kill was counted.
- **Studio:** 145 tests (was 134), `BUILD SUCCESS`; `NoDirectModelRequestTest` still green with
  `RemoteController` in the tree, and that class imports no `java.net.http`. Six controller mutants
  killed, including the bridge lookup keyed on the address instead of the name and an unknown name
  falling back to the first configured bridge — the second is caught by the stub's request counter,
  which is why the refusal test fires an allowed request first.
- **Frontend:** `vue-tsc --noEmit` clean (also reactor-enforced, since maven's compile phase runs
  `npm run build`); Vitest 77 tests (was 67). Seven mutants killed — and one of them survived its
  first run, exposing that the pane's decline branch (`status` + `sidecarBody` rendered, no output
  invented) had no test at all. That test is now in `panes.component.test.ts`; the mutant is killed
  by it.
- **Hand-run (5.2/5.3):** a throwaway sidecar was started by hand on 18099 and the studio booted on
  18081 pointed at it. The catalog came back with both advertised actions, `compareBriefs` marked
  `runnable: false`; running `remoteDigest` returned `SIDECAR pid 735454 booted 01:55:55 digested 4
  words, 22 chars: [alpha beta gamma delta]` — that pid and boot time are the stub process's own, so
  the text is its output rather than the product's. An invented bridge name came back as a refusal
  with `urlProbed: null`. After the stub was stopped the same endpoint reported `reachable: false`,
  `reason: "ConnectException"`, `urlProbed` naming the catalog URL, and the app still answered
  `/api/models`, `/api/sources` and `/api/status`. Verified through the HTTP surface the pane calls
  (curl/urllib) — no browser was driven, and the pane's own rendering is covered by the component
  tests above.
- **Still uncovered, deliberately:** the spec's timeout case. Forcing a timeout needs either a 10s+
  test or a configurable timeout; the second is configurability no task asked for and the first is a
  slow test, so reachability is proven through the other three paths (valid catalog, non-2xx, non-array
  body) plus a refused connection.
