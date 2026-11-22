# Tasks

Nothing below section 2 is implementable until section 1 pins the artifact and reports what it found.
Section 1 is deliberately cheap — no product code — and it can end the change early with a
BLOCKED verdict rather than a half-built endpoint.

## 1. Dependency-resolution spike

**Ran 2026-11-19. Verdict PROCEED; findings in `design.md` §"Section 1 spike findings". Three of the
tasks below were written on premises the spike disproved — each carries a correction where it stands.**

- [x] 1.1 Add `com.embabel.agent:embabel-agent-starter-a2a` to the root `pom.xml`
      `dependencyManagement` at `${embabel-agent.version}` (1.5.4) and resolve it with
      `./mvnw -q dependency:get`, and verify the resolution succeeds offline afterwards by re-running
      it with the network unavailable; record the exact resolved version. If it does not resolve,
      stop and report this change BLOCKED — the backlog's reuse constraint cannot be met and the
      alternative is a hand-written protocol, which is a different decision.
      Resolution passed both ways (`dependency:get` and the same with `-o` → `BUILD SUCCESS`; resolved
      version 1.5.4), and the spike confirmed `${embabel-agent.version}` is the property the pin should
      use. The spike deliberately touched no build file, so the pin arrived with task 2.1 in the same
      commit as the module — which is what this task asked for. Verified in place: the entry is inside
      `dependencyManagement` at `pom.xml:146` with `${embabel-agent.version}`, and the module declares
      the starter with no version of its own, so it resolves through the pin.
- [x] 1.2 Create a throwaway probe module (or a temporary dependency on the runtime) that depends on
      the starter, run `./mvnw -q dependency:tree` on it, and verify the reported tree names
      `spring-boot-starter-web` at 4.1.1 and `io.github.a2asdk:a2a-java-sdk-spec` at a single
      version; record which version wins between the 0.3.2.Final that `embabel-agent-api:1.5.4`
      declares and the 0.3.3.Final that `embabel-build-dependencies:2.0.3` pins, and report the
      conflict as a finding if Maven had to choose.
      **Premise wrong: there was no conflict to mediate.** Only 0.3.2.Final enters the tree; the 0.3.3
      pin lives in a BOM this repo does not import, and `embabel-agent-a2a:1.5.4` does not declare the
      SDK at all. The winner *is* the version the library was compiled against. They are different
      binaries (distinct sha1s), so a future pin to 0.3.3 would be a real untested upgrade — do not add
      one. `spring-boot-starter-web:4.1.1` and `snakeyaml:2.6` (the BOM's pin, not 2.3) both confirmed;
      `embabel-agent-eval` is absent, so its jar-shipped `web-application-type=none` cannot explain 1.4.
- [x] 1.3 In the probe, deserialize the JSON body of a real `message/send` request and serialize a
      `SendMessageResponse` back, using the same Jackson pair the library uses (the SDK's Jackson 2
      model classes and the `tools.jackson` Jackson 3 mapper its facade deserializes with), and
      verify the round-trip preserves `parts`, `role` and `messageId`. Report the result as
      PASS or as a named incompatibility; do not proceed on an assumption.
      **PASS, with one named incompatibility.** Both directions preserve `parts`, `role`, `messageId`
      under both mappers, and the `@JsonSubTypes` Part union resolves — Jackson 3 reads the SDK's
      annotations because `jackson-annotations` keeps its package across majors. The incompatibility is
      a changed default: Jackson 3 has `FAIL_ON_NULL_FOR_PRIMITIVES=true`. It bites live on `tasks/get`
      — see the correction on 3.5.
- [x] 1.4 With the starter on the classpath and `spring.main.web-application-type: none`, boot the
      existing `SmithRuntimeApplication` in the probe, and verify it starts as a non-web application,
      runs `smith run` against a test pack, and opens no listening port.
      **PASS, and for the right reason.** `cli-exit-code=1`, `listening-sockets-while-running=NONE`
      from polling `ss -ltnp` for the process's own PID rather than trusting a log line, and no
      servlet-engine line at all. `embabel-agent-eval` is absent from the tree, so the module's own
      configuration decided it — the shadowing landmine does not apply here.
- [x] 1.5 With the starter on the classpath and a servlet application type, verify in the probe that
      `AgentA2AAutoConfiguration` activates and registers `GET /a2a/.well-known/agent.json` and
      `POST /a2a`, and record the registered paths verbatim.
      **PASS — the paths are exactly as written here** (`/a2a/.well-known/agent.json`, verbatim from
      `getHandlerMethods()`; `GET /.well-known/agent-card.json` → 404, card reports
      `protocolVersion=0.3.0`). But it only activated via the system property — see the correction on 2.3.
- [x] 1.6 Record in this change's `design.md` the pinned coordinates, the winning SDK version, the
      Jackson verdict and the registered paths, and verify each recorded value is copied from command
      output rather than from upstream naming.

## 2. Module and wiring

- [x] 2.1 Create `prod/embabel-smith-runtime-a2a` with a pom whose parent is `embabel-smith-prod`
      and whose dependencies are `embabel-smith-runtime`, `embabel-agent-starter-a2a` and the test
      dependencies the other prod modules use, add it to `prod/pom.xml` `<modules>` and to the root
      `dependencyManagement`, and verify `make build` compiles the reactor including the new module
      and that the inherited `ban-poc-dependencies` rule runs for it.
- [x] 2.2 Give the module its own `application.yml` with an explicit non-default server port and the
      same env-driven provider block the runtime uses (inert defaults for every provider the
      environment does not configure), and verify the app boots with no endpoint configured and
      without touching port 8080.
- [x] 2.3 Write the servlet launcher **using
      `System.setProperty("spring.main.web-application-type", "SERVLET")` in `main()` before
      `SpringApplication.run(...)`** — the spike proved the programmatic `WebApplicationType.SERVLET`
      form does **not** work: Spring Boot 4 re-binds `spring.main.*` from the environment, so the
      runtime's own `application.yml` (`none`) wins and the context comes up
      `AnnotationConfigApplicationContext` with every A2A bean absent. This is the same override
      `prod/embabel-smith-studio` already carries for the same reason. Leave the existing
      `SmithRuntimeApplication` untouched, and verify `smith run` still exits with the CLI's exit code
      and starts no server. Record in the module's own docs the asymmetry this creates: the override
      that turns this launcher on is the only thing that could turn a server on in a module that must
      not have one.
      **Done as `SmithA2aApplication`, and it hit a second library obstacle this plan did not
      foresee.** Declaring our own `AgentCardHandler` alongside the library's does not merely lose the
      bean race — the library's is an unconditional `@Bean` with no `@ConditionalOnMissingBean`, and its
      `A2AEndpointRegistrar` injects `List<AgentCardHandler>` and registers one mapping per bean, so two
      handlers fail the **boot** with `Ambiguous mapping. Cannot map 'AgentCardHandlerWebFacade' …
      GET [/a2a/.well-known/agent.json]`. The fix that works is to exclude `AgentA2AAutoConfiguration` at
      the launcher and repeat its own `@ComponentScan("com.embabel.agent.a2a")` from
      `A2aConfiguration` with `A2AConfiguration` filtered out. That keeps the library's registrar,
      streaming handler and task-state manager instead of re-declaring them, and replaces exactly one
      bean. Recorded here because task 4.1 assumes a card handler can simply be provided — it can, but
      only by this route, and section 4 must build on it rather than rediscover it.
      The `smith run` half of this task is true by construction rather than by test:
      `SmithRuntimeApplication` was not touched and its `application.yml` still says `none`, and no test
      in `embabel-smith-runtime` pins that — nothing did before this change either. What is pinned is the
      other side: every a2a test sets `web-application-type` explicitly, so the two modules' postures
      cannot drift into each other by accident.
- [x] 2.4 Add a `Makefile` entry point that launches the A2A server, with its `make help` line, and
      verify the documented command starts the server and that no existing target starts it.
      **Done as `make run-a2a`.** One incidental fix it forced: `make help` filtered targets with the awk
      class `^[a-zA-Z_-]+:`, which silently hid any target containing a digit — so `run-a2a` would have
      built fine and never appeared in the help. The class is widened to `[a-zA-Z0-9_-]`. Worth knowing
      because the same trap hides `poc-25`-style targets generally.
- [x] 2.5 Add an offline test asserting the studio does not expose `/a2a`, and verify it passes at
      HEAD today and would fail if the starter were added to the runtime module (prove this once by
      adding the dependency temporarily and re-running).
      **Done as `NoA2aEndpointTest`, and it cannot assert what this task implied.** The task's shape
      assumes the studio answers 404 for `/a2a` today; it does not. `SpaWebConfig` maps `/**` to the
      frontend with an `index.html` fallback, so at HEAD `GET /a2a/.well-known/agent.json` answers
      **200** and `POST /a2a` **405** — a 404 assertion would have been testing the SPA fallback, not
      the absence of an endpoint. The test therefore asserts what actually distinguishes the two cases:
      no `RequestMappingHandlerMapping` entry mentioning `/a2a`, and the card route not answering with
      a card. Mutation-proved as asked: adding the starter to the runtime pom killed both assertions,
      and the pom was restored (`git diff` on it clean).
      **Related trap for any future servlet test here:** `@SpringBootTest(RANDOM_PORT)` in the studio
      comes up non-web — `local.server.port` unpublished — unless the test states
      `spring.main.web-application-type=SERVLET` in its own properties. The launcher's system property
      is not available to tests, and per 2.3 the programmatic form does not stick.

## 3. Run-backed request handler

- [x] 3.1 Implement an `A2ARequestHandler` that maps a send-message request onto
      `AgentSpecRunService.run(sourceUri, goal, input, plannerOverride, tier)` using the SDK's own
      request and response types, and verify an offline test that stubs the service asserts the
      goal, the input and the tier it was called with.
- [x] 3.2 Translate `RunResult` into a Task: `success` with output becomes a COMPLETED task carrying
      the output as an artifact; a failed outcome becomes a FAILED task carrying the result's `error`
      in its status message, and verify a test covers success, goal-not-found and trust-tier-block
      with the service stubbed so no endpoint is needed.
- [x] 3.3 Make the task id the emitted `TraceRecord.runId` and treat a run whose trace could not be
      written as a named failure rather than a task with no id, and verify the test asserts the
      returned task id equals the trace record's run id.
- [x] 3.4 Verify with an offline test that one send-message request appends exactly one trace record
      and that the record carries the run's trust tier, so a policy block stays distinguishable
      from a bad spec.
- [x] 3.5 Serve `tasks/get` and `tasks/cancel` from the trace store: get reads the record for the
      id, cancel returns the task FAILED with a cancellation reason when the record is not yet
      terminal and refuses a terminal task, and verify a test covers an unknown id, a completed
      task, and a cancel attempt on a run that already finished. Cancel must not claim to stop a
      running plan it cannot stop — if the run is synchronous and already returned, say so in the
      status message.
      **One trap the spike found, and it is not ours to fix in the handler.** Jackson 3 changed
      `FAIL_ON_NULL_FOR_PRIMITIVES` to `true`, so a client that omits a primitive-backed field fails
      *inside the facade, before our code runs*. `tasks/get` hits this today: omitting `historyLength`
      answers HTTP 500 `Cannot map null into type int ... TaskQueryParams["historyLength"]`. The SDK is
      inconsistent about it — `TaskQueryParams` declares `int historyLength` while
      `MessageSendConfiguration` declares `Integer historyLength` — so a test that only ever sends every
      field will never see it. Cover the omitted-field case explicitly and decide, in the open, between
      relaxing the app's mapper default and documenting the field as required; do not leave the 500 as
      an undocumented surprise.
      **Ruled: the field is documented as required, and the 500 is asserted as measured rather than
      papered over.** The reasoning is that the mapper belongs to the library's facade, not this module,
      and quietly relaxing a Jackson 3 default to forgive an SDK inconsistency would apply module-wide to
      every mapper in the application to fix one upstream declaration. The test asserts 200 with the
      field and 500 without it, so the behaviour is pinned and visible; the module README states the
      requirement. If a real client is ever blocked by this, revisiting is one property — but it should
      be a decision, not a default.
      **One branch this task asked for cannot be reached, and that is a defect in the task, not the
      code.** "Cancel returns FAILED when the record is not yet terminal" presumes a stored task can be
      observed mid-flight. It cannot: the trace record is appended *after* the run returns, so every
      record in the store is already terminal, and a run with no record is simply unknown. Implemented as
      refusal-plus-task-not-found, with the refusal naming the synchronous run. Serving a genuine
      in-flight cancel would need the run to be made durable before it finishes — a different change.
- ~~3.6 Serve `tasks/list` by reading the trace store and mapping records to task summaries, filtering
      by goal, pack and state and paging with a token.~~ **Cut on 2026-11-19, not implemented.** The
      spike proved the facade rejects `tasks/list` before any `A2ARequestHandler` runs, so it cannot be
      served from behind the library this change reuses; the alternative was a route of our own outside
      the library's dispatch, which rebuilds the protocol surface the reuse ruling avoids. The endpoint
      serves **five** operations. What replaces this task is the deviation entry in 5.4 and the wording
      in 6.1 — both corrected here so no page still promises six. Number kept rather than reusing it,
      because 3.5 and 3.7 are cited by number elsewhere in this change.
- [x] 3.7 Serve `message/stream` over SSE by emitting the task, then its artifact, then a terminal
      status update, and verify a test asserts the stream closes after the terminal event and
      contains no event the run did not produce.
      **Done: `[task, artifact-update, status-update(final)]` then `complete()`, and a failed run emits
      two events with no artifact at all.** The honest part is the second half of the sentence — no
      invented `WORKING` events to make the stream look alive. Note for whoever writes the next test
      here: the artifact leg needs a *successful* run, and no offline source can produce one, so the
      test stubs `AgentSpecRunService` with `@MockitoBean` — Boot 4 removed `@MockBean`, and the older
      annotation is not on this classpath.

## 4. Agent card

- [x] 4.1 Provide the `AgentCardHandler` for the ingested source at path `a2a`, built from the
      deployed agent's goals, and verify an offline test asserts one card skill per exported goal
      and that the card's `url` names the registered POST path.
      **Done as `A2aAgentCardTest`, and the phrase "the ingested source" hides a gap.** The card
      reads `agentPlatform.goals`, and the only thing that puts goals on the platform is an ingest —
      which the endpoint performs when a run asks it to, not at boot. So a freshly started endpoint
      serves a card with `skills: []` until its first run. (This note originally added a second
      symptom — "and after that the card grows a duplicate skill per run" — which is **wrong**; see
      decision 7. `getGoals()` ends in `toSet` over a value-equal `Goal` data class, so re-deploys
      collapse. Measured, not reasoned.) This task's own test ingests the source itself to have goals
      to assert on, and the module README states the empty-until-first-run behaviour rather than
      implying it away.
      **Superseded on 2026-11-19 by 4.4**, which fixes the emptiness; the duplicate-skill half was never
      a defect to fix.
- [x] 4.2 Filter the advertised goals by the configured A2A trust tier and pass the same tier to
      every run, and verify a test with a disallowed pack asserts its goal is absent from the card
      and that attempting it anyway yields a FAILED task naming the tier.
      **Done as `TrustTierGoalFilter`, wired into the card bean, tested by `TrustTierGoalFilterTest`
      and `A2aTrustTierTest`.** One thing the task's wording does not say: a goal carries no tier
      information at all — tool groups are declared on *steps* — so the filter cannot decide from
      the `Goal` it is handed. It reads the configured source's packs and excludes the goal of every
      pack whose tool groups the tier disallows, through `TrustTierPolicy.declaredToolGroups`, which
      is the same per-pack check `RuntimeCore` makes before a run. Two consequences worth having on
      the record: the read is once-only and lazy (a `git:` source must not be resolved during boot),
      and it fails **open** when the source cannot be read, because the run-time gate is the
      enforcement and an unreadable source that advertised nothing would look like an empty agent.
      The tier-blocked run is a real run refused before any step executes, so it names the tier
      ("granted: standard", the `shell` group) with no model call.
      **The fail-open half of this note is superseded by 4.4** — with the source as the only source of
      skills, there is nothing left to fall back to. The tier predicate itself stands.
- [x] 4.3 Fetch the card over HTTP in a test and assert the serialized document's `name`, `version`,
      `capabilities`, `defaultInputModes`, `defaultOutputModes` and `skills`, and verify the test
      also asserts `protocolVersion` is the value section 1 recorded — this is the claim that tells
      a client which protocol this endpoint speaks.
      **Done, and every value is the library's, not ours.** Measured through HTTP, matching the
      bytecode of `EmbabelServerGoalsAgentCardHandler`: `version` is the constant `0.1.0-SNAPSHOT`,
      `documentationUrl` is `https://embabel.com/docs`, `provider.organization` is `Embabel`,
      `capabilities` is `streaming=true, pushNotifications=false, stateTransitionHistory=false,
      extensions=[]`, both mode lists are `[application/json, text/plain]`,
      `supportsAuthenticatedExtendedCard=false`, `protocolVersion=0.3.0`. Only `name` and `url` are
      per-deployment: `name` is the platform's, and `url` is `{scheme}://{host}:{port}/{path}` — so
      the card hands a client exactly the POST path the registrar opened.
      **Re-check these assertions when 4.4 lands**: the sentence above is true of the library's handler,
      and after 4.4 the `skills` list is assembled by a class of ours, so keep the values and change the
      explanation — and update the module README sentence that documents the empty-until-first-run
      behaviour, which 4.4 removes.
- [x] 4.4 Build the card's **skill list** from `smith.a2a.source` instead of
      `AgentPlatform.getGoals()`, and verify a test asserts the card is non-empty *before any run has
      happened* and still lists one skill per goal after two runs.
      **Ruled on 2026-11-19; supersedes 4.1's mechanism, not its assertions.** The contract, measured:
      * `AgentCardHandler` is an *interface* extending `A2ARequestHandler` and `HasInfoString`,
        declaring `getPath()` and `agentCard(String, String, int)`. Implement it and delegate
        `handleJsonRpc` to `RunBackedA2ARequestHandler` — which is what
        `EmbabelServerGoalsAgentCardHandler` does with its own `a2ARequestHandler` field.
        `A2AEndpointRegistrar` takes `List<? extends AgentCardHandler>` and registers three mappings per
        bean (card GET, JSON-RPC POST, `text/event-stream` POST), so exactly one card-handler bean may
        exist; see the ambiguous-mapping boot failure recorded under 2.3.
      * `AgentSkill` is a record whose canonical form is `(id, name, description, tags, examples,
        inputModes, outputModes, security)`. From a pack: `id` and `name` are `goal().name()`,
        description is `goal().description()`, both mode lists are `List.of("application/json")` as the
        library passes. **`tags` and `examples` have no source at all** — `goal.yml` declares only
        `name`/`description`/`cost`/`personifies`/`produces`/criteria, and `AgentSpecReader`'s `GOAL_KEYS`
        would reject a `tags` key, because those two lists belong to `@Agent` on a Java class, which a
        no-code pack does not have. So both are `List.of()`, which the SDK accepts: its constructor
        asserts `id`, `name`, `description` and `tags` are non-*null*, not non-empty. Nothing is invented
        to fill them.
      * Keep the source read **once, lazily**. Nothing caches across calls: `SpecSource.resolve()`
        re-clones a `git:` source into a fresh temp directory each time, and `AgentSpecReader` is
        stateless — so a per-request read would hit the network on every card fetch. Cache only success:
        boot must still never read the source (`AgentSpecReader` throws `AgentSpecValidationException`, a
        `RuntimeException`), and a failed read must not be remembered, so a source that was briefly
        unreachable does not leave the endpoint permanently unable to describe itself.
      * **Do not carry over `TrustTierGoalFilter`'s fail-open.** It existed because the platform's goal
        set was a real list to fall back on; with the source as the only source of skills, falling back
        means serving `skills: []`, which tells a client the agent does nothing. Raise instead, naming the
        source, and assert the 500 through HTTP plus the recovery on the next request. See decision 7's
        closing paragraph.
      * Both new assertions need a mutation proof. The empty-before-run one is exactly the test 4.1 could
        not write, because the mechanism it replaced could not pass it.

      **Done as `SpecSourceSkills` (the read, the tier predicate and the skills) plus
      `SourceDerivedAgentCardHandler` (the `AgentCardHandler` that assembles the card and delegates
      both JSON-RPC methods to `RunBackedA2ARequestHandler`).** `TrustTierGoalFilter` is deleted with
      its fail-open; its predicate moved, and the useful cases of its test moved with it. Measured
      through HTTP: 45 module tests green, full reactor 1496, `make lint` 0 violations.

      Three things the contract above got wrong, corrected against the jars and the running endpoint:
      * `HasInfoString` declares **`infoString(Boolean, int)`**, not `getInfoString()`.
      * `handleJsonRpcStream` must be delegated **as well**. The interface's default throws
        `UnsupportedOperationException`, so delegating only the non-streaming method would have taken
        `message/stream` down — two existing tests caught it.
      * The duplicate-skill premise does not reproduce, so the second assertion is a **guard, not a
        red-then-green proof**. Reverting the bean made `theCardListsEveryGoalOfTheConfiguredSource
        BeforeAnythingHasRun` fail with `Expected size: 2 but was: 0` — the real defect, and the one
        4.1 could not test — but `twoRunsOfTheSameSourceLeaveOneSkillPerGoalOnTheCard` passed unchanged.
        `getGoals()` ends in `toSet` over a value-equal `Goal` data class, so re-deploys collapse.
        Decision 7 carries the correction.
      A fourth, from the ruling rather than the contract: a **blank** `smith.a2a.source` now raises too,
      because there is one code path and an empty list is the lie the requirement names. So `make run-a2a`
      without `SMITH_A2A_SOURCE` answers 500 on the card instead of serving an empty one, and the module
      README's env table now says the variable is effectively required.

      One thing this does *not* fix, noted for you rather than here: nothing validates that two packs of
      one source export different goal names. `SpecValidator` has no such check and `AgentSpecReader`
      only checks each step's `produces` against that pack's own `producedTypeNames()`, so two packs
      sharing a goal name would put two skills with the same `id` on the card. The runtime does refuse
      that source, but only at ingest: `RuntimeCore.checkGoalNameCollisions` throws
      `IllegalStateException` naming both packs. Pre-existing source-validation gap; this change makes
      the card honest about what the endpoint is configured to run, not about whether the source is
      self-consistent.

## 5. Protocol conformance record

- [x] 5.1 Write one offline test per operation that posts the JSON-RPC envelope and asserts the
      response body against the shapes in the fetched specification (Task `id` and `status`
      required; Message `messageId`, `role`, `parts` required; Artifact `artifactId` and `parts`
      required), and verify each test names the specification section it checks.
      **Done as `A2aSpecShapesTest` — four operations, each test naming its sections, asserting on
      the body parsed with the same Jackson 3 mapper the facade serializes with.** One shape needed
      a test of its own rather than a slot in the four: a Message appears in a response only when a
      task **failed** (§4.1.4) — a completed task carries an artifact and no status message — so the
      Message-shape test drives a failed run.
- [x] 5.2 Assert that the discovery document is served at the path the library registers and record
      in the test name that the current specification's well-known path differs, and verify the
      recorded value is copied from section 1's output.
      **Done as `discoveryIsServedAtTheLibrarysWellKnownAgentJsonPathNotTheSpecificationSection8Point2AgentCardJsonPath`:**
      the 0.3 path answers 200, and both the specification's filename under `/a2a` and its path at
      the server root answer 404. The recorded value is section 1's — `agent.json`, with the card's
      `protocolVersion` 0.3.0 as the field a 1.0 client is expected to read.
- [x] 5.3 Add `SendStreamingMessage` and `SendMessage` happy-path tests tagged `@Tag("live")`
      beside their existing `@EnabledIfEnvironmentVariable` condition, and verify a default
      `make test` reports them not-run while `make test-live` selects them.
      **Done as `SmithA2aLiveTest`, its two tests named for the operations they exercise.** Selection
      verified without calling an endpoint: under `make test` the class does not appear in the
      surefire report at all (the `live` group is excluded before JUnit evaluates anything), while
      `./mvnw test -Dtest.excludedGroups= -Dgroups=live` prints `Running …SmithA2aLiveTest` and
      reports `Tests run: 2, Skipped: 2` — selected, and skipped only because that shell had no
      `OPENAI_CUSTOM_BASE_URL`. **The happy paths themselves were never executed by the agent that
      wrote them**, on the change's own no-endpoint-calls rule; they mirror the one goal
      (`extract` / `briefDone`) that `LiveRunIntegrationTest` already proves this endpoint can achieve.
- [x] 5.4 Record the deviations from the specification in one place — **five operations, not six:
      `tasks/list` is not served, and a client that calls it gets the facade's HTTP 500
      `Method tasks/list is not supported`, which is what the cut in 3.6 actually looks like from
      outside**, unmapped methods surfacing as HTTP 500 rather than the section 9.5 error codes, an
      omitted primitive-backed field such as `historyLength` failing the same way (3.5), no push
      notification configuration, no `SubscribeToTask`, no extended card, no signing — and verify each
      named deviation is either covered by a test or listed in this change's non-goals.
      **Done as a "Deviations from the specification" table in the module README** — the one place
      that sits next to the code and is not a `docs/` page, which section 6 owns. Nine lines, each
      with its cause and the test that pins it. Four of them are newly measured rather than inferred,
      in `A2aDeviationRecordTest`: an invented method answers 500 with `not/a/method is not supported`
      (so the 500 is the facade's generic behaviour, not a `tasks/list` special case);
      `agent/getAuthenticatedExtendedCard` answers 500; `tasks/resubscribe` answers 500; and the
      served card carries no `securitySchemes` and no `security`. Push notifications and the extended
      card are also declared `false` in the card itself, which is what makes those two omissions
      visible to a client rather than silent.

## 6. Documentation

- [x] 6.1 Add the A2A surface to `docs/07-runtime.md` beside the CLI, and make **the sentence that
      introduces it** state that it is a local development surface on an explicit non-default port
      with no authentication, that it runs real billable model calls for anyone who can reach the
      port, and that it speaks A2A protocol 0.3 so a client expecting the current 1.0 specification
      will not find it. Then name the **five** operations it actually serves, say plainly that listing
      tasks is not among them and what a client gets if it asks, and give the task-to-run mapping and
      the configured tier. Verify the page names the module and paths that actually exist after
      section 2, and verify no page in `docs/` still claims six.
      Done 2026-11-19 as `## A2A endpoint`, disclosure first. Its terminal-only and stream-shape
      sentences were checked against `RunBackedA2ARequestHandler` rather than against this brief:
      `tasksCancel` does answer with a status message naming the run synchronous, and the stream does
      emit task, artifacts, terminal status, close.
- [x] 6.2 Note in `docs/05-architecture.md` that the new module is the only prod module with a
      network surface and still holds no model call, and verify the module map table lists it.
      **The wording this brief asked for is false, and the writer was right to refuse it**:
      `prod/embabel-smith-studio/pom.xml:42` declares `spring-boot-starter-web` and its
      `application.yml` sets `port: ${SERVER_PORT:8080}`, so the studio listens too. What landed says
      the true and narrower thing — the only prod module that gains a servlet engine *from a
      dependency* rather than declaring one — and the same correction went into
      `SmithA2aApplication`'s javadoc, which had carried the false version since wave 2a. The module
      map lists it. Found while verifying the Ports section: **POC 16's sidecar defaults to the same
      18090** (`poc/poc-16-remote-action/.../RemoteActionApp.java:71`,
      `${builder.sidecar-port:18090}`), so the two cannot run at once; noted there rather than changed,
      since `poc/` is frozen.
- [x] 6.3 Correct the Misc entry in `docs/12-backlog.md`. Its wording *"reuse embabel and Spring AI
      libs"* cannot be honoured as written — no Spring AI A2A artifact exists on Maven Central — so
      replace it with the artifacts actually verified: `com.embabel.agent:embabel-agent-a2a:1.5.4`
      (with `-autoconfigure` and `-starter-a2a`) and
      `io.github.a2asdk:a2a-java-sdk-spec`. Leave whatever remains open (protocol 1.0) in its place,
      and verify the page names coordinates that resolve rather than a library that does not exist.
      Done; both new `Check:` commands were run and both resolve.
- [x] 6.4 Re-run the wiki ingest and lint for the pages touched, and verify the cross-links resolve
      and the GitHub Pages publish step is noted as owed.
      **Scoped, not a full ingest** — the A2A surface only (`features/a2a-endpoint.md` new;
      `index.md`, `features/smith-runtime.md`, `overview/build-tooling.md`,
      `risks/model-calls-go-through-ai.md` refreshed), because a full ingest is a separate decision
      still owed to the human: 1083 tracked files have moved since `last_commit`. That field is
      deliberately unadvanced. Lint re-verified independently rather than taken on report: 45 pages,
      0 broken `[[links]]`, 0 orphans, 0 `status: stale`. Pages publish is recorded as owed in the
      `index.md` ingest note.
- [x] 6.5 Give `README.md` the row the new module is missing, and verify it. Found by the sweep this
      change should always do: `grep -rli a2a README.md docs/` returns three files, and none of them is
      the module map — `README.md`'s Modules table lists `embabel-smith-specsource` and
      `embabel-smith-runtime` and stops, so the page AGENTS.md calls the entry point does not know a
      ninth module exists. Add the row in that table's voice (what it is for, and that it is the one
      prod module that listens on a port), and add `make run-a2a` wherever the run book lists the other
      `make` targets.
      Row and `make` paragraph added, and the table's count fixed from five prod modules to six. Again
      **not** "the one prod module that listens on a port" — false, for the reason in 6.2 — so the row
      carries the dependency-based wording. `run-a2a` verified to be the only `make` target that opens
      a socket, which is what that paragraph claims.
- [x] 6.6 Decide what `docs/03-embabel-spec-agent-builder-design.md`'s Phase 4 row now means, and verify
      the page does not read as though A2A is untouched. Its "Ecosystem" row lists `A2A spec mesh` and
      `A2A spec discovery` as new code, which is *not* what landed — this surface exposes runtime runs, not
      spec discovery — so the row stays open, but it needs one clause saying the runtime's own A2A endpoint
      already exists so a reader does not conclude there is no A2A anywhere yet.
      Done: one clause in the row, still open, and it says which A2A is which.
