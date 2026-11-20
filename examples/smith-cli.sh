#!/usr/bin/env bash
#
# smith-cli.sh — shared bootstrap for the example scripts. Source it, do not run it.
#
# It exists so generate-spec.sh and generate-skill.sh share one answer to "how do you actually
# start the builder CLI", which is not the obvious answer:
#
#   mvn spring-boot:run -Dspring-boot.run.arguments="build --docs ..." does NOT work. Maven
#   4.1.1's plugin passes the whole string as ONE argv entry, so picocli reports
#   "Unmatched argument at index 0". exec:java needs exec-maven-plugin, which is not in the
#   local ~/.m2, so it fails offline.
#
# The form that works is a plain `java -cp` of the module's classes plus a classpath Maven
# has already resolved. That is what run_smith below does, and it caches the resolved
# classpath under /tmp keyed on the builder's pom, so the second blueprint in a sequence does
# not pay the resolution again. SMITH_CP overrides that cache with a file of your own.
#
set -euo pipefail

SMITH_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILDER_MODULE="prod/embabel-smith-builder"
BUILDER_MAIN="com.embabel.smith.builder.SmithBuilderApplication"

# The endpoint the builder talks to. These live in ~/.config/env/llm.env on this machine and
# are NOT exported by a plain shell, so a script that skips this step fails with a Spring
# start-up error rather than a clear "no endpoint" message.
smith_load_endpoint_env() {
  local env_file="${SMITH_LLM_ENV:-$HOME/.config/env/llm.env}"
  if [ ! -f "$env_file" ]; then
    echo "No model endpoint env file at $env_file." >&2
    echo "Set SMITH_LLM_ENV to one that exports OPENAI_CUSTOM_BASE_URL and OPENAI_CUSTOM_API_KEY." >&2
    exit 1
  fi
  # shellcheck disable=SC1090
  . "$env_file"
  export OPENAI_CUSTOM_BASE_URL OPENAI_CUSTOM_API_KEY OPENAI_CUSTOM_MODELS OPENAI_API_KEY \
    ANTHROPIC_API_KEY EMBABEL_MODELS_DEFAULT_LLM 2>/dev/null || true
  if [ -z "${OPENAI_CUSTOM_BASE_URL:-}" ]; then
    echo "$env_file does not set OPENAI_CUSTOM_BASE_URL, so there is no endpoint to build against." >&2
    exit 1
  fi
}

# Compile the builder and print the runtime classpath.
#
# The compile is unconditional, and that is deliberate: an up-to-date module costs a few
# seconds, while gating it on the pom being newer than the cache means a change to a .java
# file runs as classes from before the change — which reads as a fix that did nothing. The
# dependency resolution is the expensive part, and that stays cached at a path keyed on the
# module's pom, so a sequential run of several blueprints pays it once and picks it up again
# automatically. Point SMITH_CP at a file yourself to override the cache.
smith_classpath() {
  local cache="/tmp/smith-cp-$(md5sum "$SMITH_ROOT/$BUILDER_MODULE/pom.xml" | cut -c1-12).txt"
  if [ -n "${SMITH_CP:-}" ]; then
    if [ ! -f "$SMITH_CP" ]; then
      echo "SMITH_CP points at $SMITH_CP, which does not exist. Drop the variable to let the" >&2
      echo "script resolve the classpath itself." >&2
      exit 2
    fi
    cat "$SMITH_CP"
    return
  fi
  # `install`, not `compile`: the builder depends on its sibling embabel-smith-skillevaluator,
  # and dependency:build-classpath below resolves siblings from the local repository, not from
  # the reactor. Compiling alone leaves that artifact uninstalled, so the resolution step fails
  # offline with "Cannot access embabel-snapshots ... has not been downloaded from it before".
  (cd "$SMITH_ROOT" && ./mvnw -o -q -DskipTests install -pl "$BUILDER_MODULE" -am)
  if [ ! -s "$cache" ] || [ "$SMITH_ROOT/$BUILDER_MODULE/pom.xml" -nt "$cache" ]; then
    (cd "$SMITH_ROOT" && ./mvnw -o -q dependency:build-classpath -pl "$BUILDER_MODULE" \
      -Dmdep.outputFile="$cache" -Dmdep.includeScope=runtime)
    echo "resolved classpath -> $cache" >&2
  fi
  cat "$cache"
}

# Stream the lines of a build's log that say something, for as long as the build runs.
#
# A builder JVM logs ~116 lines before it generates anything — one per model bean it registers,
# one per MCP server it does not connect to — and then, once the eval deploys the generated
# process, a world-state dump twice per action. Reading that raw is how you lose an hour to a
# wall of text that never says what the build is doing. These patterns are the exceptions:
# the planner's own progress, the timeouts and failures, and the report.
#
# Deliberately excluded: the `Input map for action` and `world state` dumps, which repeat a
# ~2,400-character boolean map twice per action and are unreadable at any speed.
SMITH_PROGRESS_PATTERN='executing action|executed action|Goal achieved|Planning|GOAP|received LLM response|using LLM|BUILD OK|BUILD BLOCKED|SKILL BUILD|Exception|Caused by|^\s+- '
# Endpoint health, counted rather than streamed: Embabel retries a timed-out interaction up to
# ten times, so one slow action emits twenty near-identical lines and drowns the progress feed.
# They stay in build.log, where the sequence and the timestamps actually mean something.
SMITH_RETRY_PATTERN='timed out|Retry attempt'

# Stream the lines of a build's log that say something, until the build whose log it is exits.
smith_progress() {
  local log="$1"
  local build_pid="$2"
  local waited=0
  while [ ! -s "$log" ] && [ "$waited" -lt 120 ] && kill -0 "$build_pid" 2>/dev/null; do
    sleep 0.5
    waited=$((waited + 1))
  done
  if [ ! -s "$log" ]; then
    echo "   (no log output yet — the JVM is still starting; full text will be in build.log)"
    return
  fi
  echo "   (live progress; the whole log, including every timeout, is in $(basename "$(dirname "$log")")/build.log)"
  # Print what has landed so far, then follow from the next line: tail -f alone, started a
  # half-second after the JVM, would miss the lines written in that half-second.
  local seen=0
  # `|| true` is load-bearing: the lines on disk at this moment are startup chatter that
  # matches none of the patterns, grep exits 1 on zero matches, and under pipefail that
  # would end the script before the build had even started.
  seen="$(grep -E "$SMITH_PROGRESS_PATTERN" "$log" | tee /dev/stderr | wc -l || true)"
  tail -n +$((seen + 1)) -f "$log" 2>/dev/null | grep -E --line-buffered "$SMITH_PROGRESS_PATTERN" &
  local progress_job=$!
  local retries=0
  while kill -0 "$build_pid" 2>/dev/null; do
    sleep 5
    retries=$(grep -cE "$SMITH_RETRY_PATTERN" "$log" 2>/dev/null || true)
  done
  # Let the follower flush what the JVM wrote in its last moments, then stop it: tail -f does
  # not stop by itself, and a script that hangs here after a finished build is worse than one
  # that loses a line.
  sleep 1
  kill "$progress_job" 2>/dev/null || true
  wait "$progress_job" 2>/dev/null || true
  retries=$(grep -cE "$SMITH_RETRY_PATTERN" "$log" 2>/dev/null || true)
  if [ "${retries:-0}" -gt 0 ]; then
    echo "   ($retries timeout/retry lines in build.log — endpoint health, not the builder)"
  fi
}

# Pull the CLI's own report out of the captured stream.
#
# The builder's report goes to System.out and so does every log line — Spring Boot 4.1's
# console appender has no stderr target — so the two cannot be separated by file descriptor.
# They are separated by shape instead: the report starts at a verdict line and runs to the
# indented summary under it, and log lines resume with a timestamp. The verdict line is cut by
# prefix, not by a colon, because only the OK forms have one: a blocked build says
# "BUILD BLOCKED at <stage> (<name>)", so a pattern that demands a colon silently drops every
# blocked report — the one outcome the reader most needs the report for.
smith_write_report() {
  local log="$1" file="$2"
  sed -n '/^\(SKILL \)\?BUILD \(OK\|BLOCKED\)/,$p' "$log" 2>/dev/null |
    awk '(/^[0-9][0-9]:[0-9][0-9]:[0-9][0-9]\./ || /^JvmType - /) && NR > 1 { exit } { print }' >"$file"
  # A run that never reached a verdict — a rejected argument, a failure during startup — has no
  # report to cut, and a zero-byte report.md reads as "nothing happened" rather than "nothing
  # ran". Keep the CLI's last words instead, untimestamped lines only so the log's chatter about
  # model registration does not drown them.
  if [ ! -s "$file" ]; then
    {
      echo "NO REPORT: the CLI printed no verdict line, so nothing was built or blocked at a gate."
      echo "The last things it said:"
      grep -vE '^[0-9]{2}:[0-9]{2}:[0-9]{2}\.' "$log" | tail -20
    } >"$file"
  fi
}

# Run the builder CLI with the arguments given. Paths must be ABSOLUTE: --docs and --out
# resolve against the JVM's working directory, not this script's.
run_smith() {
  local cp
  cp="$SMITH_ROOT/$BUILDER_MODULE/target/classes:$(smith_classpath)"
  (cd "$SMITH_ROOT" && java -cp "$cp" "$BUILDER_MAIN" "$@")
}

# The blueprint directory for a slug — a blueprint is a directory under examples/blueprints/,
# so a new example is a new directory there and no edit to any script. The directories are
# titled (Aurora, TradingAgents); a slug finds its directory ignoring case and hyphens, so
# `trading-agents`, `TradingAgents` and `tradingagents` all resolve to the same blueprint.
blueprint_dir() {
  local slug="$1"
  local exact="$SMITH_ROOT/examples/blueprints/$slug"
  if [ -d "$exact" ]; then
    echo "$exact"
    return
  fi
  local pattern="${slug//-/*}"
  local match
  match="$(find "$SMITH_ROOT/examples/blueprints" -maxdepth 1 -type d -iname "$pattern" | head -1)"
  if [ -z "$match" ]; then
    echo "No blueprint matching '$slug' under examples/blueprints/. Available:" >&2
    ls -1 "$SMITH_ROOT/examples/blueprints" >&2
    exit 2
  fi
  echo "$match"
}
