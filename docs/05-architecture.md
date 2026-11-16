# Architecture and Build Invariants

The structural rules every module follows, and why they are shaped this way. These are the
invariants a change is expected to preserve; the per-module contracts live in the pages indexed
from [`index.md`](index.md).

## Module map

The root POM (`pom.xml`) aggregates three trees:

| Tree | Contents |
| --- | --- |
| `smith-common` | The agent-spec format: data model (`common.model`), YAML I/O (`common.io`), validation (`common.validation`). Framework-free and fully offline-testable. |
| `poc/` | 26 numbered proof-of-concept modules. **Frozen evidence** — see below. |
| `prod/` | The product: `embabel-smith-specsource`, `embabel-smith-runtime`, `embabel-smith-builder`, `embabel-smith-studio`. |

`specsource` is the only piece shared by two product modules (the runtime reads it, the builder
publishes into it). It holds no Embabel agent code, so it stays offline-testable.

## Extract, do not depend

Product modules copy-and-generalize POC logic rather than depending on `poc-*` artifacts. Two
reasons: a POC module is a Spring Boot *launcher* — fat with web starters and demo mains whose
configuration would leak into the product — and POCs are frozen evidence whose fate must not
entangle the product.

The rule is machine-enforced, not conventional: `maven-enforcer-plugin` runs a `bannedDependencies`
rule on `prod/pom.xml` (execution `ban-poc-dependencies`, banning `com.embabel.smith:poc-*`). The
pattern is by group, so it survived the later module renames untouched.

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
They cover the two modules that talk to a model; `runtime` and `specsource` carry no guard because
they hold no model call — the runtime reaches the model only by executing actions on the platform.
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

- **Validate before write.** A generated spec is never written unless it validates on a throwaway
  platform. This is the guardrail that makes "generate with no code" safe.
- **The static security scan gates publication.** A high-severity finding blocks the publish.
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

- `make build` — compile the full reactor, tests skipped.
- `make test` / `make test-module MODULE=prod/embabel-smith-studio` — the target sources
  `~/.config/env/llm.env` and exports the endpoint vars first, so `Live*` tests actually run instead
  of silently skipping. Consequence: `make test` never exercises the offline path. To run the
  deterministic suite, unset the endpoint:
  `env -u OPENAI_CUSTOM_BASE_URL -u OPENAI_CUSTOM_API_KEY -u OPENAI_CUSTOM_MODELS -u EMBABEL_MODELS_DEFAULT_LLM ./mvnw test`.
- `make lint` / `make checkstyle-module MODULE=…` — the project's SpringChecks.

**Checkstyle runs in Maven's `validate` phase**, so a style violation fails `make build` and
`make test` too, not just `make lint`. Because the reactor stops at the first failing module, one
violation early in the build hides every module behind it — read the *first* error, not the last.
The pre-commit hook installed by `make setup` runs `make lint`; a clone without it commits unchecked.

## Ports

Port 8080 is held by an unrelated local service, so the POCs and the studio take explicit ports:
18080 for POC 13's MCP server, 18081 for POC 19's llama.cpp container, and `SERVER_PORT` for
anything else.
