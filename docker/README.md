# Containers

Two images from one Maven build, and the pipeline that builds, proves and publishes them.

| file | what it is |
| --- | --- |
| `Dockerfile` | one build stage, two product targets: `studio` and `runner` |
| `smith-runner.sh` | the runner's entrypoint — the part that accepts agents and skills |
| `docker-compose.yml` | the studio as a service, the runner as a one-shot proof, Postgres optional |
| `docker-compose-infra.yml` | the developer Postgres the studio's `local` profile auto-starts (predates this directory's other files) |
| `smoke.sh` | the check CI runs, runnable by hand: boots the studio, probes the runner |

The build-context filter is `.dockerignore` at the repository root, not in this directory.

## Quick start

```bash
docker build -f docker/Dockerfile --target studio -t embabel-smith-studio .
docker run -p 8080:8080 embabel-smith-studio        # http://localhost:8080
```

The studio needs nothing to serve. It boots with no model endpoint and no keys — its
`application.yml` feeds inert placeholders — and reports which models it can reach at
`/api/status`. Model-backed panes stay unavailable until you pass an endpoint:

```bash
docker run -p 8080:8080 \
  -e OPENAI_CUSTOM_BASE_URL=http://your-host:4000/v1 \
  -e OPENAI_CUSTOM_API_KEY=sk-... \
  -e OPENAI_CUSTOM_MODELS=my-model \
  -e EMBABEL_MODELS_DEFAULT_LLM=my-model \
  -v "$PWD/agent-specs:/opt/smith/specs:ro" \
  -e SMITH_STUDIO_SOURCES=folder:/opt/smith/specs \
  embabel-smith-studio
```

Same variables as the host run, same meaning. `make help` lists the non-container entry points.

## The sample runner

The runner image is the proof that the pieces fit: it carries the product's own two CLIs and a
sample of the repository's own artifacts, and answers a request with both kinds of pack in front
of it.

```bash
docker build -f docker/Dockerfile --target runner -t embabel-smith-runner .
docker run --rm embabel-smith-runner probe          # what it can see; no model call
docker run --rm -e OPENAI_CUSTOM_BASE_URL=... -e OPENAI_CUSTOM_API_KEY=... \
  -e OPENAI_CUSTOM_MODELS=... -e EMBABEL_MODELS_DEFAULT_LLM=... \
  embabel-smith-runner                              # both legs, then a verdict
```

`probe` prints the agent-spec source and the skill directory it can see, and whether a model
endpoint is configured. It is the only mode that works without an endpoint, which is why it is
what CI runs.

Modes: `prove` (default), `agents [INPUT]`, `skill [REQUEST]`, `probe`, and pass-through for the
raw `run`, `skill-run`, `build` and `blueprint` commands. `docker run --rm <image> help` prints
the list.

What it runs is not a container-only harness. `agents` is `smith run` from
`prod/embabel-smith-runtime` — deploy the specs in a source, plan a GOAP chain over their types,
execute — and `skill` is `smith skill-run` from `prod/embabel-smith-builder`, which attaches a
published skill folder's tools to one request.

The baked sample is one agent pack (`agent-specs/explainer`) and the builder's own shipped skill
(`prod/embabel-smith-builder/src/main/resources/skills/`). One pack in the source means the run
resolves its single exported goal with no `--goal`, so the default needs no arguments. The repo's
other packs mount in the same way:

```bash
docker run --rm -v "$PWD/agent-specs:/opt/smith/specs:ro" \
  -e SMITH_AGENT_SOURCE=folder:/opt/smith/specs ... embabel-smith-runner agents
```

One of those packs, `agent-specs/shell-runner`, declares the `shell` tool group. The trust gate
checks every pack in a source rather than only the steps a plan selects, so a source containing it
refuses at the default `standard` tier with

```text
Run blocked by trust tier: pack 'shell-runner' declares tool group(s) [shell]
that require a higher trust tier (granted: standard). Re-run with the higher tier to allow
these steps.
```

That refusal happens before any model call and is the gate doing its job. `SMITH_TRUST_TIER=high`
is the answer when you mean it — with a caveat measured rather than assumed: granting it over a
source that contains `shell-runner` lets the planner reach for that pack's `runCli` step while
planning an unrelated goal, because the step's output type is one the planner is short of. A source
of the packs you mean to run beats a broad tier.

## Compose

```bash
docker compose -f docker/docker-compose.yml up -d studio
docker compose -f docker/docker-compose.yml --profile demo up --abort-on-container-exit
docker compose -f docker/docker-compose.yml --profile storage up -d     # + Postgres
```

`demo` runs the runner as a one-shot job against the same `agent-specs/` the UI is browsing. The
studio works with storage unbound, so Postgres is a profile and not a requirement.

## Variables

| variable | image | meaning |
| --- | --- | --- |
| `SERVER_PORT` | studio | the port it binds (default 8080) |
| `SMITH_STUDIO_SOURCES` | studio | comma-separated `folder:<path>` / `git:<url>[@ref]` sources the panes offer |
| `SMITH_STUDIO_IMPROVE_SOURCE` | studio | the source the improvement loop watches (defaults to the first of `sources`) |
| `SMITH_STUDIO_TRACE_DIR` | studio | where the JSONL trace store lives |
| `OPENAI_CUSTOM_BASE_URL` `_API_KEY` `_MODELS` | both | an OpenAI-compatible endpoint |
| `EMBABEL_MODELS_DEFAULT_LLM` | both | which of those models is the default |
| `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`, `GEMINI_API_KEY` | both | direct providers; nothing is baked into either image |
| `SMITH_AGENT_SOURCE` | runner | spec source for `agents` — a directory that *contains* pack directories |
| `SMITH_AGENT_GOAL` | runner | which goal to run; empty means the source's single exported goal |
| `SMITH_AGENT_INPUT` | runner | the text bound to `UserInput` |
| `SMITH_SKILL_DIR` | runner | a published skill directory: one folder per skill |
| `SMITH_SKILL_REQUEST` | runner | the request `skill` answers |
| `SMITH_TRUST_TIER` | runner | `standard` (default) or `high` |
| `SPRING_PROFILES_ACTIVE` | both | defaults to `container`, a profile with no config file — see below |
| `JAVA_OPTS` | both | extra JVM flags |

Both images run as uid 10001 and write under `/home/smith/.smith` (traces, storage settings, git
source cache). A read-only root filesystem needs that path mounted writable.

`SPRING_PROFILES_ACTIVE` deserves a note. The studio's *default* profile is `local`, and
`application-local.sample.yaml` turns on `spring.docker.compose` against the repo-relative path
`./docker/docker-compose-infra.yml`. That path does not exist in an image, and a container
starting a container is not what anyone wants, so both images default to a profile name that has
no config file anywhere in the repository. Override it and you own the consequences.

## How these images run Java

Neither image is an executable Spring Boot jar, because no module in this repository produces one:
the root pom keeps `spring-boot-maven-plugin` in `<pluginManagement>` only and nothing declares a
`repackage` goal, so `target/*.jar` is thin — no `Main-Class`, no `BOOT-INF/`. The images therefore
lay out the module's classes plus its resolved runtime dependencies and launch with `-cp`, which
is the form `examples/smith-cli.sh` already uses on a host. If `repackage` is ever added to these
poms, both final stages collapse to a `COPY` of the fat jar.

Two consequences worth knowing:

- The studio's frontend is built *inside* the image. `frontend-maven-plugin` downloads Node 26 and
  runs `npm install`, `npm update` and `npm run build`; Vite writes the bundle into
  `src/main/resources/static`, which is gitignored. That stage needs network, and the Maven base
  image needs `libatomic1` or the downloaded Node binary fails with exit 127.
- The runner keeps the runtime's and the builder's classes in separate directories with one shared
  `lib`. Both modules ship an `application.yml` at their classpath root, so merging the two class
  directories would let one silently answer for the other.

## CI

`.github/workflows/docker.yml` builds both targets, runs `docker/smoke.sh`, scans the images and
publishes to `ghcr.io/<owner>/embabel-smith-{studio,runner}` on a push to the default branch or a
`v*` tag. Pull requests build and smoke-test but never publish. The smoke test makes no model
call, so the model-backed legs are not covered there — those stay with the `@Tag("live")` tests and
`examples/`.

## Troubleshooting

**The model-backed legs fail on a DNS lookup.** A container resolves names with its own resolver, so
an endpoint given as a `.local` name — mDNS, served on a host by `nss-mdns` and Avahi, neither of
which is in a JRE base image — does not resolve inside it, and the run dies in the HTTP client with
an OkHttp `Dns.lookup` stack rather than at the model. The same applies to an alias that only exists
in the host's `/etc/hosts`.

`--network host` does **not** fix a `.local` name: shared networking still leaves the container
without an mDNS resolver. What works is telling the container the address directly:

```bash
docker run --add-host spark.local:192.168.1.197 ... embabel-smith-runner
```

Under Compose it is the same statement as an `extra_hosts` entry on the `demo` service, which is
commented in `docker-compose.yml` for exactly this reason. An endpoint given as an IP address, or as
a name in real DNS, needs neither. This is why the CI smoke test makes no model call at all.

**The studio boots and then exits with a `BuilderPackSurface` refusal.** That is
`improveSource` refusing to run the self-improvement loop without a checkout of this repository to
rewrite, which an image never has. The image's default `SMITH_STUDIO_SOURCES` takes that branch; if
you cleared it to an empty value, set it (or `SMITH_STUDIO_IMPROVE_SOURCE`) to any source URI and the
studio starts.

**The studio serves the UI but 404s at `/`.** The frontend is built inside the image, not stored in
git. The build asserts that `index.html` reached the classes directory, so this state should fail
the build rather than ship; if it ever ships, the Vite overlay step in `docker/Dockerfile` stopped
doing its job.

**`npm install` fails with exit 127** and `libatomic.so.1` in the message — the Maven base image
needs the `libatomic1` package the build stage installs.

## Checking a build

```bash
docker/smoke.sh                       # STUDIO_IMAGE / RUNNER_IMAGE override the :local defaults
STUDIO_PORT=18080 docker/smoke.sh     # when 8080 is taken
```

It asserts that the studio answers `/api/status`, serves the SPA bundle at `/`, answers
`/api/sources` with JSON, and returns 404 rather than the SPA fallback for an unknown `/api`
route; and that the runner's `probe` sees both an agent-spec source and a skill directory. Studio
logs print on failure, because a Spring boot failure sits in the middle of them, not at the end.
