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
# WHY ONE BUILD STAGE FOR BOTH
#   `mvn install -pl prod/embabel-smith-studio -am` already builds nine modules — smith-common,
#   the prod aggregator and every shipped module, including the runtime and the builder. The
#   runner needs two of those nine. Splitting the Dockerfile in two would pay for the same
#   reactor twice, so the two final stages branch off one build.
#
# WHY A CLASSPATH AND NOT `java -jar`
#   No module in this repository produces an executable Spring Boot jar. The root pom keeps
#   spring-boot-maven-plugin in <pluginManagement> only (pom.xml:349-352) and nothing declares a
#   `repackage` goal, so each module's target/*.jar is a thin jar with no Main-Class and no
#   BOOT-INF/ — java -jar refuses it. Rather than add a repackage goal to production modules for
#   packaging's sake, these images lay out what `mvn spring-boot:run` hands the JVM: the module's
#   classes plus its resolved runtime dependencies. That is the launch form examples/smith-cli.sh
#   already proves works here, chosen over spring-boot:run in-image because Maven would then be
#   part of the runtime image.
#
#   The consequence worth knowing for the studio: the image runs the module's classes, so the
#   frontend bundle must exist. frontend-maven-plugin (prod/embabel-smith-studio/pom.xml:97-146)
#   installs Node v26.13.1 and runs npm install, npm update and npm run build at the compile
#   phase, and Vite writes the SPA into src/main/resources/static
#   (prod/embabel-smith-studio/frontend/vite.config.ts:22-25), which .gitignore:14 keeps out of
#   git. So the npm stage needs network. -Dskip.npm is not a way out: that property belongs to
#   the plugin's `npm` goals and is inert for install-node-and-npm.
#
#   If a future change adds `repackage` to these poms, both final stages collapse to a COPY of
#   the fat jar and the classpath assembly below goes away.
#
# BUILD CONTEXT
#   .dockerignore at the repo root drops target/, node_modules, the host-built static bundle, the
#   frozen POC tree, and the gitignored application-local.yaml — a developer's file naming the
#   dev Postgres and its credentials, which must not ride into an image.
#
# The run commands, the variables that point them at a spec source, and how to tell a healthy
# container from a booted one are in docker/README.md.

# ---------------------------------------------------------------------------
# build: the reactor, plus the classpath layout both images ship
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

# Node needs libatomic, and the Maven image does not carry it. Without this the studio's npm stage
# fails at `npm install` with exit 127 and
# "node: error while loading shared libraries: libatomic.so.1", because frontend-maven-plugin
# downloads a Node 26 binary that is linked against it — a build-time dependency of the frontend
# toolchain only, so it stays in this stage and never reaches either final image.
RUN apt-get update \
    && apt-get install -y --no-install-recommends libatomic1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /src

# The whole tree, filtered by .dockerignore. One layer: the reactor is named by the poms, and
# enumerating module internals here would duplicate a list the build already owns.
COPY . .

# -DskipTests: the offline suite is the CI gate's job (make build && make test), not an image
# build's. The cache mount keeps ~/.m2 warm across builds without baking it into a layer — locally,
# where the mount directory persists on the BuildKit daemon. It is worth knowing that a cache mount
# is NOT part of what `type=gha` exports, so on a hosted runner with an ephemeral daemon this mount
# starts empty and Maven resolves the reactor's dependencies from scratch; the layer cache below is
# what CI actually reuses.
#
# dependency:copy-dependencies -DincludeScope=runtime is what turns "the module's classpath"
# into files — the same graph spring-boot:run would resolve, minus test-scope jars.
#
# The plugin is named by fully-qualified coordinates, not by the `dependency:` prefix. Prefix
# resolution reads plugin-group metadata from the repository, and inside a clean image that
# metadata is not in ~/.m2 yet, so the short form dies with "No plugin found for prefix
# 'dependency'" — and -o makes it unrecoverable. The version is pinned for the same reason: an
# unpinned prefix resolves to whatever a cache happens to hold.
#
# The bundle overlay is the one non-obvious step, and it exists because of plugin order. Vite
# writes to src/main/resources/static at the compile phase, which is AFTER maven-resources-plugin
# copied that directory into target/classes — so on a clean single-pass build the classes
# directory has no static/ at all, and the studio would serve 404 at / while booting perfectly. A
# developer's working tree hides this: a second build copies what the first one generated. The
# overlay does what that second build would have done.
#
# The assertions after it are not decoration either: an npm stage that produced nothing fails the
# build here rather than shipping an image that only answers on /api routes.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests install \
        -pl prod/embabel-smith-studio,prod/embabel-smith-runtime,prod/embabel-smith-builder -am \
    && for m in studio runtime builder; do \
        case "$m" in studio) p=prod/embabel-smith-studio;; runtime) p=prod/embabel-smith-runtime;; builder) p=prod/embabel-smith-builder;; esac; \
        mvn -B -ntp -q -pl "$p" \
            org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies \
            -DoutputDirectory=/staging/$m/lib -DincludeScope=runtime; \
        mkdir -p "/staging/$m/classes"; \
        cp -a "$p/target/classes/." "/staging/$m/classes/"; \
        echo "$m: $(find /staging/$m/classes -type f | wc -l) class files, $(ls /staging/$m/lib | wc -l) jars"; \
    done \
    && mkdir -p /staging/studio/classes/static \
    && cp -a prod/embabel-smith-studio/src/main/resources/static/. /staging/studio/classes/static/ \
    && test -f /staging/studio/classes/static/index.html \
        || { echo "no frontend bundle in the studio's classes — the npm build did not run"; exit 1; } \
    && test -f /staging/builder/classes/skills/skill-card-generator/SKILL.md \
        || { echo "the builder shipped no skill folder to sample"; exit 1; }

# ---------------------------------------------------------------------------
# studio: the UI
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS studio

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

# A non-root uid, and a home it owns. The studio writes three places under $HOME — the JSONL
# trace store (~/.smith/traces), its storage settings (~/.smith/studio) and the git source cache
# (~/.smith/spec-sources) — so a read-only root filesystem still needs one of those mounted
# writable; see docker/docker-compose.yml.
RUN groupadd --gid 10001 smith \
    && useradd --uid 10001 --gid 10001 --create-home --home-dir /home/smith --shell /usr/sbin/nologin smith \
    && mkdir -p /opt/smith /opt/smith/specs /home/smith/.smith \
    && chown -R smith:smith /opt/smith /home/smith

COPY --from=build --chown=smith:smith /staging/studio/ /opt/smith/

WORKDIR /opt/smith
USER smith

# SERVER_PORT is the property the studio's own application.yml:2 reads, so the port is
# operator-controlled without a rebuild.
#
# SMITH_STUDIO_SOURCES has a non-empty default here for a reason that is easy to trip over. With
# both smith.studio.sources and smith.studio.improve-source empty, StudioConfiguration's
# improveSource falls back to BuilderPackSurface.specSource(), which walks the filesystem for a
# CHECKOUT of this repository and refuses when it finds none — deliberately, because the improvement
# loop rewrites the builder's own packs in place and must never be handed the target/classes copy
# that the next build recopies. An image has no checkout, so that refusal is a context-startup
# failure rather than a degraded loop. Naming any source at all takes the other branch, and an empty
# directory is inert: the loop schedule is off until someone starts it from the UI.
#
# SPRING_PROFILES_ACTIVE names a profile that has no config file anywhere in the repo — the same
# trick the Makefile uses for the A2A run (Makefile:69). It matters because the studio's DEFAULT
# profile is `local`, and application-local.sample.yaml turns on spring.docker.compose against the
# repo-relative path ./docker/docker-compose-infra.yml, which does not exist in this image.
# Overriding the default profile is what stops a container from trying to start a container.
#
# MaxRAMPercentage lets the container's memory limit size the heap rather than the host's RAM.
ENV SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=container \
    SMITH_STUDIO_SOURCES="folder:/opt/smith/specs" \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom" \
    CLASSPATH="/opt/smith/classes:/opt/smith/lib/*" \
    MAIN_CLASS="com.embabel.smith.studio.SmithStudioApplication"

EXPOSE 8080

# /api/status answers 200 with no model endpoint and no key configured — StatusController reports
# configured:false rather than failing — so the probe never depends on a secret being present.
# start-period is generous: the Embabel platform scans packages and builds its decision registry
# during boot (about 5s warm on a host, longer on a loaded runner).
HEALTHCHECK --interval=15s --timeout=3s --start-period=90s --retries=5 \
    CMD curl -fsS "http://127.0.0.1:${SERVER_PORT}/api/status" || exit 1

# Exec form, with the JVM re-execed so it becomes PID 1 and receives SIGTERM: Spring Boot then
# closes its context instead of being killed at the grace period.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -cp \"$CLASSPATH\" $MAIN_CLASS \"$@\""]

# ---------------------------------------------------------------------------
# runner: the same product, pointed at a sample of itself
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runner

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

RUN groupadd --gid 10001 smith \
    && useradd --uid 10001 --gid 10001 --create-home --home-dir /home/smith --shell /usr/sbin/nologin smith \
    && mkdir -p /opt/smith /home/smith/.smith \
    && chown -R smith:smith /opt/smith /home/smith

# Two classpaths, not one merged classpath. Both the runtime and the builder ship an
# application.yml at their classpath root, so merging their classes directories would let one
# silently answer for the other; the lib directory is shared because the reactor resolves one
# version of every artifact, so a shared filename is the same bytes.
COPY --from=build --chown=smith:smith /staging/runtime/lib/ /opt/smith/lib/
COPY --from=build --chown=smith:smith /staging/builder/lib/ /opt/smith/lib/
COPY --from=build --chown=smith:smith /staging/runtime/classes/ /opt/smith/runtime/classes/
COPY --from=build --chown=smith:smith /staging/builder/classes/ /opt/smith/builder/classes/
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
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom" \
    SMITH_AGENT_SOURCE="folder:/opt/smith/samples/agents" \
    SMITH_AGENT_GOAL="" \
    SMITH_AGENT_INPUT="Explain how a coffee machine works" \
    SMITH_SKILL_DIR="/opt/smith/samples/skills" \
    SMITH_SKILL_REQUEST="What can this skill do for me?" \
    SMITH_TRUST_TIER="standard"

ENTRYPOINT ["/opt/smith/bin/smith"]
