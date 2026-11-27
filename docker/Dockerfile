# syntax=docker/dockerfile:1
# Author: Ivan Rodriguez
#
# Two images from one Maven build:
#
#   studio  — the browser UI (prod/embabel-smith-studio), serving the pipeline on :8080
#   runner  — a sample runner that ingests agent specs AND skill folders and answers,
#             proving the builder's output, the spec source, and the no-code runtime
#             actually work together without a compiler in the loop
#
#   docker build -f docker/Dockerfile --target studio -t embabel-smith-studio .
#   docker build -f docker/Dockerfile --target runner -t embabel-smith-runner .
#
# HOW THESE IMAGES LAUNCH
#   Every process they run is a GraalVM native image: the studio's `smith-studio`, the runtime's
#   `smith-runtime`, and the builder's `smith`. All three are compiled by the same `-Pnative`
#   profile, and a module is only in that build if it declares the profile's three executions in
#   its own pom (docs/08-builder.md, "As a native binary"). Nothing here runs a JVM, so there is
#   no `java` in either final image, no CLASSPATH, and no JAVA_OPTS — a native binary reads its
#   own memory settings from the container's cgroup and rejects a `-XX:` flag handed to it as an
#   argument, which is why those three things were dropped rather than translated.
#
#   Going native moves three failure modes from run time to build time, and the assertions below
#   are the ones that catch them. A native image carries resources as a flat named set with no
#   directory entries, so a resource the build did not REGISTER is missing silently: the studio
#   boots, answers /api, and 404s at `/` because the SPA's hashed assets were never put in. And
#   Spring's AOT pass decides `@ConditionalOnProperty` while compiling, so a flag flipped at run
#   time cannot add a bean the image was not built with. Both of those are asserted here rather
#   than trusted — the studio binary is booted inside the build and probed at `/`.
#
# WHY ONE BUILD STAGE FOR BOTH
#   `./mvnw -Pnative package -pl prod/embabel-smith-studio -am` already builds nine modules —
#   smith-common, the prod aggregator and every shipped module, including the runtime and the
#   builder. The runner needs two of those nine. Splitting the Dockerfile in two would pay for the
#   same reactor twice, so the two final stages branch off one build.
#
# BUILD CONTEXT
#   .dockerignore at the repo root drops target/, node_modules, the host-built static bundle, the
#   frozen POC tree, and the gitignored application-local.yaml — a developer's file naming the
#   dev Postgres and its credentials, which must not ride into an image. Dropping the host's static
#   bundle is also what makes the SPA probe mean something: inside the image only the npm stage can
#   produce that directory, and only the native build can put it inside the binary.

# ---------------------------------------------------------------------------
# build: the reactor, compiled to native images
# ---------------------------------------------------------------------------
FROM debian:12-slim AS build

# The GraalVM the images are compiled with, as a version rather than a base-image tag.
#
# There is no usable GraalVM image for this: `ghcr.io/graalvm/jdk-community:25` carries no
# `native-image` at all (its bin is the JDK's tools), and there is no `native-community:25` tag to
# pull instead. So the compiler comes from the same artifact .github/workflows/native-image.yml
# downloads and checksums — the graalvm-ce-builds `jdk-<version>` release — which keeps the
# container's compiler and the CI's on one line. `--strip-components=1` is required, not tidy:
# the archive's top-level directory is `graalvm-community-openjdk-<version>+<build>`, a name that
# carries a build number no Dockerfile can write down, so naming it directly fails with a bare
# "not found" after a perfectly good download.
#
# build-essential and zlib1g-dev are not optional. GraalVM's analysis runs to completion and the
# LAST step shells out to `gcc` to link, so a slim stage can fail in two ways that both read as a
# broken GraalVM and are nothing of the kind. Without a compiler the message is "Default
# native-compiler executable 'gcc' not found via environment variable PATH"; with a compiler but
# without zlib's development symlink, the link reaches collect2 and dies at
# "/usr/bin/ld: cannot find -lz" — native-image always passes -lz, and only the -dev package
# answers that bare name. GitHub runners ship both, so the CI path never shows either. They stay in
# this stage: a native binary is self-contained, so no final image needs them.
#
# libatomic1 is for the studio's frontend stage: frontend-maven-plugin downloads a Node 26 binary
# linked against it, and without the package `npm install` dies with exit 127 and
# "error while loading shared libraries: libatomic.so.1".
#
# linux-x64 is hard-coded because the pipeline is linux/amd64 only (docs/14-containers.md). On an
# arm64 builder the asset name needs the aarch64 suffix, which is what native-image.yml derives
# from `uname -m`; nothing here builds a multi-architecture manifest.
ARG GRAAL_VERSION=25.0.2
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        build-essential ca-certificates curl libatomic1 zlib1g-dev \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /opt/graalvm \
    && curl -fsSL --retry 3 \
        "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-${GRAAL_VERSION}/graalvm-community-jdk-${GRAAL_VERSION}_linux-x64_bin.tar.gz" \
        | tar -xz --strip-components=1 -C /opt/graalvm \
    && /opt/graalvm/bin/native-image --version \
    && /opt/graalvm/bin/java -version

# `./mvnw` rather than a Maven install: the repository pins its own Maven in
# .mvn/wrapper/maven-wrapper.properties, and a build stage that installs a second Maven would be a
# second version to keep honest. JAVA_HOME has to be the JDK itself — native-image is a symlink
# into lib/svm/bin, and resolving it to its real path yields a directory that is not a JDK.
ENV JAVA_HOME=/opt/graalvm \
    PATH=/opt/graalvm/bin:$PATH

# The compiler's own heap. This cannot be set from outside the image the way it is in
# .github/workflows/native-image.yml, where `NATIVE_IMAGE_OPTIONS` is a step env: inside an image
# build, an environment variable on the workflow job never reaches the RUN, so the value has to be
# carried by the build. It is an ARG rather than a hard-coded ENV for exactly that reason — CI and a
# developer can both override it without editing this file.
#
# It matters because the launcher sizes a heap at a quarter of the machine's memory, and on a
# 16 GB GitHub runner that quarter is 4 GB — below what a Spring Boot image of this reachability
# wants. Without a cap the compiler is killed at the cgroup limit, which arrives as a compiler
# crash rather than as an out-of-memory error; .github/workflows/docker.yml adds swap alongside this
# so the peak costs time instead of the build.
#
# The number is the studio's, not the builder's. At 10 GB the studio's analysis ran seventeen
# minutes and then reported `Error: Java heap space` — a compiler out-of-memory, not a code fault —
# while the builder and the runtime were already finished and on their way into the image. This
# module pulls in Tomcat, JPA and Hibernate on top of everything the runtime has, so it is the one
# that sets the ceiling for all three. Override it downward on a machine that cannot hold it; the
# failure mode of an override that is too low is the same heap-space error, late in the build.
ARG NATIVE_IMAGE_OPTIONS=-J-Xmx14g
ENV NATIVE_IMAGE_OPTIONS=$NATIVE_IMAGE_OPTIONS

WORKDIR /src

# The whole tree, filtered by .dockerignore. One layer: the reactor is named by the poms, and
# enumerating module internals here would duplicate a list the build already owns.
COPY . .

# -DskipTests: the offline suite is the CI gate's job (`make build && make test`), not an image
# build's, and process-aot rewrites the classes the tests would run against. The cache mount keeps
# ~/.m2 warm across builds without baking it into a layer — locally, where the mount directory
# persists on the BuildKit daemon. A cache mount is NOT part of what `type=gha` exports, so on a
# hosted runner with an ephemeral daemon this mount starts empty and Maven resolves the reactor
# from scratch; the layer cache is what CI actually reuses.
#
# The four assertions are the part that keeps this honest, and the last one is the interesting one.
#
#   1. Each binary exists and is a native ELF executable rather than a launcher script.
#   2. `smith-runtime run --help` must print its options. The builder's CI smoke runs the binary
#      with no subcommand, and the root path never builds a subcommand's ArgSpecs — so an image
#      whose picocli options were never registered still passes a banner grep. Asserting the
#      subcommand is the assertion that actually proves the CLI is wired.
#   3. The builder binary answers for the skill leg the runner image is going to ask it about.
#   4. The studio binary is BOOTED here and probed at `/`, because that is the only way to see
#      whether the SPA's assets were registered into the image. A jar can be inspected with
#      `jar tf`; a native image's resource set has no listing a script could grep.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -Pnative -DskipTests package \
        -pl prod/embabel-smith-studio,prod/embabel-smith-runtime,prod/embabel-smith-builder -am \
    && mkdir -p /staging/studio/bin /staging/runner/bin \
    && cp prod/embabel-smith-studio/target/smith-studio /staging/studio/bin/studio \
    && cp prod/embabel-smith-runtime/target/smith-runtime /staging/runner/bin/runtime \
    && cp prod/embabel-smith-builder/target/smith /staging/runner/bin/builder \
    && for b in /staging/studio/bin/studio /staging/runner/bin/runtime /staging/runner/bin/builder; do \
           head -c 4 "$b" | grep -q ELF || { echo "$b is not a native executable"; exit 1; }; \
       done \
    && echo "binaries: studio $(du -h /staging/studio/bin/studio | cut -f1), runtime $(du -h /staging/runner/bin/runtime | cut -f1), builder $(du -h /staging/runner/bin/builder | cut -f1)" \
    && ( prod/embabel-smith-runtime/target/smith-runtime run --help 2>&1 | grep -q -- '--source' \
        || { echo "the runtime binary does not expose its run options — picocli wiring is missing"; exit 1; } ) \
    && ( prod/embabel-smith-builder/target/smith --help 2>&1 | grep -q 'Usage: smith' \
        || { echo "the builder binary does not print its usage"; exit 1; } ) \
    && ( mkdir -p /tmp/probe-specs \
        && SPRING_PROFILES_ACTIVE=container SMITH_STUDIO_SOURCES=folder:/tmp/probe-specs \
           prod/embabel-smith-studio/target/smith-studio --server.port=18099 \
           > /tmp/studio-probe.log 2>&1 & \
           studio_pid=$!; \
           ready=1; \
           for _ in $(seq 1 90); do \
               curl -fsS -o /dev/null http://127.0.0.1:18099/api/status 2>/dev/null && { ready=0; break; }; \
               sleep 1; \
           done; \
           if [ "$ready" -ne 0 ]; then \
               echo "the native studio did not become ready"; tail -30 /tmp/studio-probe.log; \
               kill "$studio_pid" 2>/dev/null; exit 1; \
           fi; \
           asset=$(curl -fsS http://127.0.0.1:18099/ 2>/dev/null \
                   | grep -oE '/assets/[A-Za-z0-9._-]+\.js' | head -1); \
           if [ -z "$asset" ]; then \
               echo "the studio binary served no index.html naming a bundle — its SPA assets were not registered into the image"; \
               kill "$studio_pid" 2>/dev/null; exit 1; \
           fi; \
           curl -fsS -o /dev/null "http://127.0.0.1:18099$asset" \
               || { echo "the studio binary serves an index.html whose $asset it cannot serve"; \
                    kill "$studio_pid" 2>/dev/null; exit 1; }; \
           kill "$studio_pid" 2>/dev/null; \
           echo "studio binary answered /api/status and serves $asset" )

# ---------------------------------------------------------------------------
# studio: the UI
# ---------------------------------------------------------------------------
FROM debian:12-slim AS studio

# OCI labels so an inspected image says where it came from. The workflow passes these; a plain
# docker build leaves them empty, which is honest. The ARG names are deliberately undotted:
# BuildKit reads a dot inside ${...} as a substitution modifier and fails the build with
# "unsupported modifier (.) in substitution", so the names carry the values and the keys spell
# the standard.
ARG OCI_TITLE=embabel-smith-studio
ARG OCI_SOURCE=
ARG OCI_REVISION=
ARG OCI_VERSION=
LABEL org.opencontainers.image.title="$OCI_TITLE" \
      org.opencontainers.image.source="$OCI_SOURCE" \
      org.opencontainers.image.revision="$OCI_REVISION" \
      org.opencontainers.image.version="$OCI_VERSION" \
      org.opencontainers.image.description="Embabel Smith studio: one browser surface over the builder, the no-code runtime and the spec sources" \
      org.opencontainers.image.licenses="Apache-2.0"

# curl is for the HEALTHCHECK below, and nothing else — the application itself needs no package,
# because what it runs is a compiled binary rather than a JVM plus a jar.
#
# A non-root uid, and a home it owns. The studio writes three places under $HOME — the JSONL
# trace store (~/.smith/traces), its storage settings (~/.smith/studio) and the git source cache
# (~/.smith/spec-sources) — so a read-only root filesystem still needs one of those mounted
# writable; see docker/docker-compose.yml.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 smith \
    && useradd --uid 10001 --gid 10001 --create-home --home-dir /home/smith --shell /usr/sbin/nologin smith \
    && mkdir -p /opt/smith /opt/smith/specs /home/smith/.smith \
    && chown -R smith:smith /opt/smith /home/smith

COPY --from=build --chown=smith:smith /staging/studio/bin/studio /opt/smith/bin/studio

WORKDIR /opt/smith
USER smith

# SERVER_PORT is the property the studio's own application.yml:2 reads, so the port is
# operator-controlled without a rebuild — an environment variable reaches a native image the same
# way it reaches a JVM one, through Spring's binding rather than through reflection.
#
# SMITH_STUDIO_SOURCES has a non-empty default here for a reason that is easy to trip over. With
# both smith.studio.sources and smith.studio.improve-source empty, StudioConfiguration's
# improveSource falls back to BuilderPackSurface.specSource(), which walks the filesystem for a
# CHECKOUT of this repository and refuses when it finds none — deliberately, because the improvement
# loop rewrites the builder's own packs in place and must never be handed a build output copy.
# An image has no checkout, so that refusal is a context-startup failure rather than a degraded
# loop. Naming any source at all takes the other branch, and an empty directory is inert: the loop
# schedule is off until someone starts it from the UI.
#
# SPRING_PROFILES_ACTIVE names a profile that has no config file anywhere in the repo — the same
# trick the Makefile uses for the A2A run (Makefile:69). It matters because the studio's DEFAULT
# profile is `local`, and application-local.sample.yaml turns on spring.docker.compose against the
# repo-relative path ./docker/docker-compose-infra.yml, which does not exist in this image.
# Overriding the default profile is what stops a container from trying to start a container.
#
# There is deliberately no JAVA_OPTS here. This is a compiled program, not a JVM: a -XX: flag would
# arrive as a program argument, and the heap sizes itself from the container's memory limit.
ENV SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=container \
    SMITH_STUDIO_SOURCES="folder:/opt/smith/specs"

EXPOSE 8080

# /api/status answers 200 with no model endpoint and no key configured — StatusController reports
# configured:false rather than failing — so the probe never depends on a secret being present.
# start-period is generous for a JVM boot and generous for this one too: a native image starts in
# well under a second, but the Embabel platform still scans packages and builds its decision
# registry before the context is ready.
HEALTHCHECK --interval=15s --timeout=3s --start-period=90s --retries=5 \
    CMD curl -fsS "http://127.0.0.1:${SERVER_PORT}/api/status" || exit 1

# Exec form, with the program re-execed so it becomes PID 1 and receives SIGTERM: Spring Boot then
# closes its context instead of being killed at the grace period.
ENTRYPOINT ["sh", "-c", "exec /opt/smith/bin/studio \"$@\""]

# ---------------------------------------------------------------------------
# runner: the same product, pointed at a sample of itself
# ---------------------------------------------------------------------------
FROM debian:12-slim AS runner

ARG OCI_TITLE=embabel-smith-runner
ARG OCI_SOURCE=
ARG OCI_REVISION=
ARG OCI_VERSION=
LABEL org.opencontainers.image.title="$OCI_TITLE" \
      org.opencontainers.image.source="$OCI_SOURCE" \
      org.opencontainers.image.revision="$OCI_REVISION" \
      org.opencontainers.image.version="$OCI_VERSION" \
      org.opencontainers.image.description="Embabel Smith sample runner: ingests agent specs and skill folders with no compiler and answers a request" \
      org.opencontainers.image.licenses="Apache-2.0"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 smith \
    && useradd --uid 10001 --gid 10001 --create-home --home-dir /home/smith --shell /usr/sbin/nologin smith \
    && mkdir -p /opt/smith /home/smith/.smith \
    && chown -R smith:smith /opt/smith /home/smith

# Two binaries, and each is the product's own artifact rather than a container invention: the
# agents leg is `smith run` from prod/embabel-smith-runtime, the skills leg is `smith skill-run`
# from prod/embabel-smith-builder. They are separate programs, exactly as they are separate modules
# on a host — and they stay separate here for the same reason the JVM layout kept them apart: each
# ships an application.yml at its own classpath root, and one merged artifact would let one
# silently answer for the other.
COPY --from=build --chown=smith:smith /staging/runner/bin/runtime /opt/smith/bin/runtime
COPY --from=build --chown=smith:smith /staging/runner/bin/builder /opt/smith/bin/builder
# --chmod rather than trusting the context's mode: COPY preserves the file's mode as sent, and a
# checkout with core.fileMode=false (or a Windows one) would deliver an entrypoint the kernel refuses
# to exec, which surfaces as an image that cannot start rather than as a build error.
COPY --chown=smith:smith --chmod=0755 docker/smith-runner.sh /opt/smith/bin/smith

# The samples are the repository's own artifacts, which is the point: the packs the studio
# publishes and the skill folder the builder ships are the inputs, not a fixture written for the
# container.
#
#   agents/  — a spec source: a directory that CONTAINS pack directories, each holding
#              manifest.yml, goal.yml, types/ and steps/. The nesting matters. `folder:` names the
#              collection, not a pack — pointing it at agent-specs/explainer itself answers
#              "contains no agent specs" — so the pack sits one level down and the source is
#              agents/. One pack means the run resolves its single exported goal with no --goal,
#              which is what makes the default demo need no arguments.
#
#              Only explainer is baked in. The repo's other packs are one -v away, but the
#              shell-runner pack among them declares the `shell` tool group, and the trust gate
#              checks every pack in a source, not just the ones a plan picks — so adding it to the
#              default source makes the default run refuse at standard tier. That refusal is the
#              gate working; SMITH_TRUST_TIER=high is the answer, not a reason to hide the pack.
#   skills/  — a published skill directory holding one folder per skill, which is the shape
#              `smith skill-run --skill-dir` asks for; seeded from the builder's own shipped skill,
#              prod/embabel-smith-builder/src/main/resources/skills/.
COPY --chown=smith:smith agent-specs/explainer/ /opt/smith/samples/agents/explainer/
COPY --chown=smith:smith prod/embabel-smith-builder/src/main/resources/skills/ /opt/smith/samples/skills/

WORKDIR /opt/smith
USER smith

ENV SPRING_PROFILES_ACTIVE=container \
    SMITH_AGENT_SOURCE="folder:/opt/smith/samples/agents" \
    SMITH_AGENT_GOAL="" \
    SMITH_AGENT_INPUT="Explain how a coffee machine works" \
    SMITH_SKILL_DIR="/opt/smith/samples/skills" \
    SMITH_SKILL_REQUEST="What can this skill do for me?" \
    SMITH_TRUST_TIER="standard"

ENTRYPOINT ["/opt/smith/bin/smith"]
