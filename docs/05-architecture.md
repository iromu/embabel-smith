# Architecture and Build Invariants

The structural rules every module follows, and why they are shaped this way. These are the
invariants a change is expected to preserve; the per-module contracts live in the pages indexed
from [`index.md`](index.md).

## Module map

The root POM (`pom.xml`) aggregates two trees by default, and a third behind the `poc` profile:

| Tree | Contents |
| --- | --- |
| `smith-common` | The agent-spec format: data model (`common.model`), YAML I/O (`common.io`), validation (`common.validation`), the endpoint gate (`common.config`), and the sample packs (`common.samples`). Framework-free and fully offline-testable. |
| `poc/` | 26 numbered proof-of-concept modules. **Frozen evidence** — see below. Not in the default reactor: they build only with `-Ppoc` (`make … POC=1`). |
| `prod/` | The product: `embabel-smith-specsource`, `embabel-smith-runtime`, `embabel-smith-a2a-starter`, `embabel-smith-builder`, `embabel-smith-skillevaluator`, `embabel-smith-studio`, and the `embabel-smith-tools` addon tree (`embabel-smith-tools-coding`). A product reaches a tool addon by declaring it: the runtime depends on the coding addon at **provided** scope, so a plain `smith run` answers the `file` role while the studio's and an A2A host's boots still resolve neither — a provided edge stops at the module that declares it, and both of those products depend on the runtime. Provided rather than optional because the CLI ships as a repackaged jar and spring-boot-maven-plugin leaves optional dependencies out of its `BOOT-INF/lib`, which would leave the one product that wants the addon without it. The addon's switches and refusals are documented in `prod/embabel-smith-tools/embabel-smith-tools-coding/README.md`. |

`embabel-smith-skillevaluator` is the **Tier 1 skill validator**: a static pipeline
(`Tier1Runner`) over a skill or a folder of skills, with one `ValidationResult` per validator —
schema, license, hygiene, version, quality score, script lint, Python syntax, code risk, and the
security family (secrets, PII, Unicode smuggling). It is a port of a Python `tier1` validator, so
it runs with no model call and depends only on SnakeYAML and Jackson.

**Its consumer is the builder's `skill` target.** `skill/SkillInspector` is the `inspect` gate of
[`08-builder.md`](08-builder.md) §"`skill`", and it is the only production class outside the module
that imports `org.iromu.embabel.smith.skillevaluator`; the root POM's `dependencyManagement` names it
and the builder depends on it. The gate asks for the seven checks the
evaluator runs in-JVM: `schema`, `version`, `quality`, `unicode`, `lint` and `license` block the
build, while `pii` runs and is reported as an advisory whatever it finds — advisory by check name,
not by severity, so a stricter PII heuristic cannot silently acquire blocking authority. It does
**not** request `security`, `secrets` or `code-integrity`, because those shell out to SkillSpector,
Gitleaks, Bandit or Semgrep: **a default skill build invokes none of them**, and the build report
carries a line saying so. `--full-inspection` is the only route that requests them. This is a
writing-quality gate; the builder's security gate remains `security/AgentSpecSecurityScanner`.

The POCs sit behind the profile because they are evidence, not shipped code: they are slow to build
(the POC 17 ONNX bundle, the POC 20 encoder) and their model-dependent tests are endpoint-gated, so a
default build of the product should not pay for either. The consequence is that `-pl poc/…` needs
`-Ppoc` — Maven can only select a module that is in the reactor.

`specsource` is the only piece shared by more than one product module: the runtime reads it, the
builder publishes into it and runs the improvement loop against it, and the studio browses sources and
drives that loop from it. It holds no Embabel agent code, so it stays offline-testable.

`embabel-smith-a2a-starter` is a starter library and the only `prod/` module that declares
`embabel-agent-starter-a2a`, so it is the only one that gains `spring-boot-starter-web` and a servlet
engine *from that dependency*. Its beans are conditional on `smith.a2a.enabled`, which is unset by
default, so depending on it opens no endpoint and no port. There is no launcher module: the host is
`embabel-smith-studio`, the repo's only long-lived web application, started with `make run-a2a`, which
sets the flag. What the deleted launcher used to supply now comes from the starter's
`SmithA2aEnvironmentPostProcessor`: when, and only when, the flag is `true`, one property source
contributes `spring.main.web-application-type: servlet` and the `18090` default, added with `addLast`
— deliberately the lowest precedence — so the host's own config files, `SERVER_PORT` and
`SPRING_APPLICATION_JSON` all outrank it, and with the flag absent the source contributes nothing. The
studio declares its own web starter, because it is the UI, and its main still sets the web type as a
system property, because a transitive jar ships a classpath `application.properties` saying `none`.

**Who can host it.** `embabel-smith-runtime` cannot: the starter's request handler, configuration and
card skills import `AgentSpecRunService`, `TrustTier`, `TrustTierPolicy` and `JsonlTraceStore` from the
runtime, so a runtime-to-starter edge is a Maven cycle that `provided` and `optional` scopes do not
avoid. The CLI could not hold a server either way — `SmithRuntimeApplication` boots non-web, closes the
context and exits, and has no servlet engine on its classpath. So the surface reaches a process through
the studio, or through a host that depends on this starter directly: wiring a new host means
declaring the starter and setting the flag, and nothing else. The surface still **holds no model
call** — every request delegates to the runtime's `AgentSpecRunService` — so it adds a transport, not
a second route to the model. What it serves, and what it deliberately does not, is in
[`07-runtime.md`](07-runtime.md).

## Extract, do not depend

Product modules copy-and-generalize POC logic rather than depending on `poc-*` artifacts. Two
reasons: a POC module is a Spring Boot *launcher* — fat with web starters and demo mains whose
configuration would leak into the product — and POCs are frozen evidence whose fate must not
entangle the product.

The rule is machine-enforced, not conventional: `maven-enforcer-plugin` runs a `bannedDependencies`
rule on `prod/pom.xml` (execution `ban-poc-dependencies`, banning `org.iromu.embabel.smith:poc-*`).
The pattern is by group, which is exactly why it needs watching: `bannedDependencies` matches the
group literally, so a `groupId` rename voids the ban silently and the build stays green. It went
unnoticed for a whole rename cycle that way — the rule is only visible when a violation exists, so
re-check the exclude against `pom.xml`'s `<groupId>` after any rename, and prove it with a temporary
POC dependency.

Because the POCs are no longer in the default reactor, this ban is the only thing between a product
module and a stale `poc-*` jar resolved from the local repo.

**POC modules are frozen.** Product work never edits anything under `poc/`. A POC's job was to
de-risk exactly one claim, in its own module; folding new behavior into one changes its identity and
makes it unclear which claim it still evidences.

The rule also settles where a *shared* type goes when two product modules need one. When the
improvement loop moved out of the studio into `builder/improve`, the trace record it reads had to
follow it — and importing it from the runtime would have made the builder depend on the runtime, so
the two stopped being siblings and the runtime's fate became the builder's. `TraceRecord` and
`TraceStore` therefore live in `smith-common` (`common/trace/`) rather than in either of them, and the
loop's readers import the framework-free type. The same reasoning moved the lifecycle state
discussion: a second reader of it is the trigger for moving those types down, not for adding an edge
between the modules that read it.

## Testing discipline: the gating taxonomy

Every module separates what runs offline from what needs an external resource, and a gated test
fails with a *clear configuration error*, never an opaque model error. Four categories:

| Gate | Needs | Offline behaviour |
| --- | --- | --- |
| offline | nothing | always runs |
| endpoint-gated (`Live*`) | an OpenAI-compatible endpoint via `OPENAI_CUSTOM_*` | skipped |
| local-model-gated | a one-time model download (e.g. the POC 17 ONNX bundle, POC 20 encoder) | logic mocked, build green |
| docker-gated | a container the app manages (POC 19's llama.cpp server) | endpoint stubbed, build green |

The discipline survives extraction: logic pulled from a POC into `prod/` keeps the POC's offline
unit-testability, and anything touching the model stays endpoint-gated.

**No model endpoint is ever hardcoded.** Base URL, key and model list come from the environment
(Embabel's `openai.custom`). A committed config would leak credentials and stop the project running
elsewhere — and it let the POCs be scaffolded before an endpoint existed.

Where an external service would otherwise be mocked at the HTTP level, the code injects a transport
seam instead (`EmbeddingTransport`, injectable gate probes) or runs a real
`com.sun.net.httpserver.HttpServer` in-process, so the round-trip is exercised offline. Git
behaviour is tested against local bare repos (`git init --bare`), covering clone, fetch, ref-pinning
and unknown-ref errors with no network.

## The model layer

**Every model call under `prod/` goes through Embabel's `Ai` layer.** No production class opens its
own HTTP connection to a model endpoint.

The rule exists because a hand-rolled caller bypasses everything `Ai` provides — role-based model
selection, provider portability, guardrails, interaction accounting — and it spreads: a class that
needs its own base URL, key and model name tends to read them out of `ModelEndpointConfig` and pick
`models.split(",")[0]`, which ignores the configured default LLM and pins the call to whatever
happens to be first in the list.

The guard is two offline tests that walk the source tree and fail on a forbidden import
(`import java.net.http.`): `builder/NoDirectModelRequestTest` and `studio/NoDirectModelRequestTest`.
They cover the two modules that talk to a model; `runtime`, `a2a-starter`, `specsource` and
`skillevaluator` carry no guard because they hold no model call — the runtime reaches the model only
by executing actions on the platform, and `a2a-starter` reaches the runtime.
Because checkstyle and tests run in the normal build, a violation fails `make test` with no endpoint
needed.

The guard is deliberately **prod-only**. The POCs keep raw callers on purpose: they are the
legitimate comparison arms POC 24 and POC 25 measured against.

Two behaviour changes came with the migration, both deliberate:

- **The system prompt is a system prompt.** Callers previously concatenated
  `system + "\n\n---\n\n" + user` into a single user message to imitate the raw comparison arm. That
  was a measurement-fidelity artifact with no purpose in the product; the seam now uses
  `PromptRunner.withSystemPrompt` and skips it when blank.
- **Raw transport strings are gone.** Messages like `"Rewrite LLM call failed: HTTP 429 …"` no
  longer exist. Tests assert the caller-visible outcome, not the wire text.

`ModelEndpointConfig` (in `smith-common`) survives as the **endpoint gate** controllers consult
(`configError()` → `MODEL_NOT_CONFIGURED`) and as the env bridge the POC launchers read. It is not
the product's source of model names.

Which provider a run uses is decided once, by `ModelEndpointConfig.detect`, in a fixed first-match
order: any one of `OPENAI_CUSTOM_BASE_URL` / `_API_KEY` / `_MODELS` present selects the
OpenAI-compatible endpoint; otherwise `OPENAI_API_KEY` selects the bundled OpenAI catalog; otherwise
`ANTHROPIC_API_KEY` selects the bundled Anthropic catalog; otherwise the provider is `NONE` and every
model-backed call is refused before it reaches a client. The two catalog providers take their
endpoint from Embabel's own defaults, and their base URL is overridable by `OPENAI_BASE_URL` /
`ANTHROPIC_BASE_URL` — the full env-and-property table is
[`prod/embabel-smith-studio/README.md`](../prod/embabel-smith-studio/README.md). `EMBABEL_MODELS_DEFAULT_LLM`
names the model whichever way the provider was chosen, and `configError()` lists only the variables
that provider still lacks. The POC launchers read the same bridge but are OpenAI-compatible only.

## Cross-cutting invariants

- **Validate before write.** A generated spec is never written unless the deterministic
  `AgentSpecValidator` passes it. The throwaway-platform deploy comes *later*, as the eval — the
  write gate is static, so an invalid spec is blocked before anything lands on disk. This is the
  guardrail that makes "generate with no code" safe.
- **The static security scan gates publication.** A `CRITICAL` finding blocks the publish; `WARN`
  findings force review but do not block. There is no `HIGH` severity in the scale
  (`INFO` / `WARN` / `CRITICAL`).
- **Trust tiers are recorded in the trace**, so a policy block is never mistaken for a bad spec —
  see [`07-runtime.md`](07-runtime.md).
- **Publishing commits; it never pushes.** Pushing is a shared-state action needing credentials and
  intent, so it takes an explicit `--push`. Name collisions need an explicit `--overwrite`.
- **Every run emits a trace record.** The improvement loop has no other evidence source.

## Local state

| Path | Owner | Notes |
| --- | --- | --- |
| `~/.smith/spec-sources/<uri-hash>` | `GitSpecSource` | one subdir per source so two repos never collide; re-resolve is `fetch` + `checkout`; a cache with no `.git` is replaced by a fresh clone |
| `~/.smith/traces/runs.jsonl` | `JsonlTraceStore` | default trace file; `--trace-dir` overrides the directory |
| `~/.smith/studio/` | `StorageSettingsService` | holds `storage.json` at mode `0600` — see [`09-studio.md`](09-studio.md) |

## Build entry points

Use the root `Makefile` rather than inventing Maven invocations (`make help` lists them).

- `make build` — compile the reactor, tests skipped.
- `make test` / `make test-module MODULE=prod/embabel-smith-studio` — the offline suite. The target
  still sources `~/.config/env/llm.env` **when that file exists** and exports the endpoint vars, but
  since 2026-11-17 that no longer decides whether live tests run (see the next bullet); the export is
  there so a module's own endpoint-aware *wiring* configures the same way it does in production.
- `make test-live` — the **only** supported opt-in to the live group. See below.
- `make lint` / `make checkstyle-module MODULE=…` — the project's SpringChecks.
- **`POC=1` on any target** adds `-Ppoc`, bringing the 26 POC modules into the reactor
  (`make test POC=1`, `make lint POC=1`). A `MODULE` under `poc/` implies it. To run a POC module's
  tests, use `make test-module MODULE=poc/poc-1-no-code-runtime` — `make module` appends
  `-DskipTests`, so `make module … GOAL=test` compiles without running anything. `make lint` without
  it no longer checks POC sources — the pre-commit hook is product-only now, and the POCs are frozen
  anyway.

**`.github/workflows/offline-verification.yml` runs `make build`, `make test` and `make lint`** on every
pull request and every push to the default branch — the same three commands, the same wrapper, no
endpoint and no secrets, so a failure there reproduces with the command above. Two things follow. The
first is that these guarantees no longer depend on someone's local hooks: a style violation or a broken
test-compile in any module of the default reactor fails a check on the change itself. The second is
that its scope is this page's scope and no wider — the default reactor, so not `poc/`, and the live
group excluded, so the paths below stay unproven by CI exactly as they are by a local run. The
exclusion is the pom's `test.excludedGroups`, not the workflow: `make test` sources the endpoint env
when that file exists, and a CI runner simply has no such file.

### The live group is opt-in (`test.excludedGroups`)

Every test that needs a real model endpoint carries `@Tag("live")`. The root `pom.xml`'s Surefire
`pluginManagement` block — inherited by every module, so a new module cannot forget the policy —
excludes the group through the property `test.excludedGroups`, which defaults to `live`. A plain
`mvn test` therefore runs no live test whether or not an endpoint is configured, which is what makes
the default reactor green on a machine that has no model.

`make test-live` sources the endpoint env and runs the group. By hand it is
`./mvnw test -Dtest.excludedGroups= -Dgroups=live`, and **both flags are required**:
`mvn test -Dgroups=live` alone selects *nothing*, because Surefire applies the group selection and
the exclusion together and the exclusion still says "exclude `live`". Forgetting to clear the
exclusion is silent — the run goes green having executed zero live tests.

Each live test keeps `@EnabledIfEnvironmentVariable(named = "OPENAI_CUSTOM_BASE_URL")` *beside* the
tag, so the two gates answer different questions: the tag says "should this run by default", the env
condition says "can it run here". An opted-in run on a laptop with no endpoint is a wall of skips,
not a wall of failures. Removing the env condition is what turns that into failures; removing the tag
is what makes `make test` depend on a model agreeing.

The live tests are the builder's `LiveBuildIntegrationTest`, `LiveExampleBuildTest`,
`eval/DeployedBuilderPackCallsValidatePackTest` and one method of `eval/ToolGroupBindingTest`, the
runtime's `LiveRunIntegrationTest`, the a2a starter's `SmithA2aLiveTest`, and the studio's
`improve/RestIntegrationTest`, `improve/LiveLoopIntegrationTest` and
`storage/PacksKeyMigrationPostgresTest`. **The list is deliberately open rather than numbered**: a
fifth test joined it when the A2A surface landed and three more arrived with the builder's pack and
tool-group work and the studio's key migration, and a page that says "exactly these four" silently
becomes false the moment someone adds a test — which is what happened here twice. Check it against the
tree rather than against this page:

```bash
grep -rn '@Tag("live")' prod/*/src/test | grep -v '\*'
```

That prints one line per real annotation, and when it disagrees with the paragraph above, the grep is
right. The `grep -v` is not decoration: two builder tests (`tools/ExposedToolGroupsTest`,
`tools/ValidationToolGroupTest`) carry the tag's *name* in a comment explaining why they deliberately
do not carry the tag, so the plain `grep -rln` counts two tests that never run live. The builder's
example suites are *not* in it; they were removed on 2026-11-17, and
building against a live model is now a set of hand-started scripts under `examples/` that
`make test-live` does not run (see [`08-builder.md`](08-builder.md)).

**What the default run therefore does not prove.** A green `make test` is not full coverage, and the
tests above are the only execution of the things below — everywhere else they are exercised against a
substituted generator, caller, platform or publisher:

- the builder's pipeline running on real model output all the way to a **published** pack. The offline
  build tests substitute the generator, so they cover validation, scoring, recommendation, the
  security gate and the publish mechanics — never a pack the model actually produced. This is also
  where the `embabel` technique's known upstream limit is evidenced, at its generation seam, which is
  the claim [`08-builder.md`](08-builder.md) makes about it;
- the runtime's **successful** run — source resolved, packs ingested, a goal planned and achieved, its
  trace record written. The
  offline runtime tests say so in their own javadoc: they cover the failure paths, and "the success
  path is covered by the endpoint-gated integration test";
- the studio's three production REST surfaces (run a goal, build a pack, one loop iteration) driven
  with the **real model-backed cores wired**. Offline, the controller tests substitute those cores;
- the improvement loop's full iteration — real refine, real re-eval, publishing an improved pack —
  rather than the queue and gate mechanics the offline `ImprovementLoopTest` drives with fakes;
- the a2a endpoint answering a real task: a `message/send` that runs the goal and returns a completed
  task carrying the output as an artifact, and the streaming form closing with a terminal
  `completed` status update. Offline, `RunBackedA2ARequestHandlerTest` stubs the run service, so it
  proves the handler drives the runner correctly — never that a goal actually runs behind it.

The affordable response the earlier version of this page pointed at now exists:
`LiveExampleBuildTest`, described in [`08-builder.md`](08-builder.md), builds one hand-written 1.5 KB
blueprint through both targets and asserts the shape the document dictates — the goal's output type is
the deliverable it names, the stages are there, the skill carries a gradeable suite. It is asserted,
not recorded, which is what the `examples/` scripts cannot be.

**Checkstyle runs in Maven's `validate` phase**, so a style violation fails `make build` and
`make test` too, not just `make lint`. Because the reactor stops at the first failing module, one
violation early in the build hides every module behind it — read the *first* error, not the last.
The pre-commit hook installed by `make setup` runs `make lint`; a clone without it commits unchecked.

## Ports

Port 8080 is held by an unrelated local service, so the POCs and the studio take explicit ports:
18080 for POC 13's MCP server, 18081 for POC 19's llama.cpp container, 18090 for the A2A surface —
which `make run-a2a` serves from the studio by passing `SERVER_PORT`, because the studio's own
default is 8080 — and `SERVER_PORT` for anything else. POC 16's
sidecar also defaults to 18090, so running that launcher and the A2A endpoint at the same time needs
one of the two moved.
