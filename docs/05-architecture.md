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
| `prod/` | The product: `embabel-smith-specsource`, `embabel-smith-runtime`, `embabel-smith-builder`, `embabel-smith-skillevaluator`, `embabel-smith-studio`. |

`embabel-smith-skillevaluator` is the **Tier 1 skill validator**: a static pipeline
(`Tier1Runner`) over a skill or a folder of skills, with one `ValidationResult` per validator —
schema, license, hygiene, version, quality score, script lint, Python syntax, code risk, and the
security family (secrets, PII, Unicode smuggling). It is a port of a Python `tier1` validator, so
it runs with no model call and depends only on SnakeYAML and Jackson. **It is not yet wired to a
consumer**: no other product module depends on it, and it is absent from the root POM's
`dependencyManagement`. Treat it as built-but-unintegrated; see [`12-backlog.md`](12-backlog.md).

The POCs sit behind the profile because they are evidence, not shipped code: they are slow to build
(the POC 17 ONNX bundle, the POC 20 encoder) and their interesting tests are endpoint-gated, so a
default build of the product should not pay for either. The consequence is that `-pl poc/…` needs
`-Ppoc` — Maven can only select a module that is in the reactor.

`specsource` is the only piece shared by more than one product module: the runtime reads it, the
builder publishes into it, and the studio browses sources and drives the improvement loop from it.
It holds no Embabel agent code, so it stays offline-testable.

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
They cover the two modules that talk to a model; `runtime`, `specsource` and `skillevaluator` carry
no guard because they hold no model call — the runtime reaches the model only by executing actions
on the platform.
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
- `make test` / `make test-module MODULE=prod/embabel-smith-studio` — the target sources
  `~/.config/env/llm.env` **when that file exists** and exports the endpoint vars, so `Live*` tests
  actually run instead of silently skipping. Consequence, on a machine that has the file: `make test`
  never exercises the offline path. To run the
  deterministic suite, unset the endpoint:
  `env -u OPENAI_CUSTOM_BASE_URL -u OPENAI_CUSTOM_API_KEY -u OPENAI_CUSTOM_MODELS -u EMBABEL_MODELS_DEFAULT_LLM ./mvnw test`.
- `make lint` / `make checkstyle-module MODULE=…` — the project's SpringChecks.
- **`POC=1` on any target** adds `-Ppoc`, bringing the 26 POC modules into the reactor
  (`make test POC=1`, `make lint POC=1`). A `MODULE` under `poc/` implies it. To run a POC module's
  tests, use `make test-module MODULE=poc/poc-1-no-code-runtime` — `make module` appends
  `-DskipTests`, so `make module … GOAL=test` compiles without running anything. `make lint` without
  it no longer checks POC sources — the pre-commit hook is product-only now, and the POCs are frozen
  anyway.

**Checkstyle runs in Maven's `validate` phase**, so a style violation fails `make build` and
`make test` too, not just `make lint`. Because the reactor stops at the first failing module, one
violation early in the build hides every module behind it — read the *first* error, not the last.
The pre-commit hook installed by `make setup` runs `make lint`; a clone without it commits unchecked.

## Ports

Port 8080 is held by an unrelated local service, so the POCs and the studio take explicit ports:
18080 for POC 13's MCP server, 18081 for POC 19's llama.cpp container, and `SERVER_PORT` for
anything else.
