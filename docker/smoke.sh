#!/usr/bin/env bash
# Author: Ivan Rodriguez
#
# smoke.sh — proves a built pair of images is actually usable, with no model endpoint and no
# secrets. This is the gate .github/workflows/docker.yml runs; running it by hand after a
# `docker build` is the same check.
#
#   STUDIO_IMAGE=embabel-smith-studio:local RUNNER_IMAGE=embabel-smith-runner:local docker/smoke.sh
#
# What it proves, and what it deliberately does not:
#
#   * the studio boots to a SERVING state as a container — not merely that the JVM started, but
#     that /api/status answers and / returns the SPA. That catches the two failures a Dockerfile
#     can hide: a missing frontend bundle (the studio would serve 404 at / while booting fine) and
#     a profile that makes the container try to start a container.
#   * the runner sees both kinds of input it exists to accept — an agent-spec source and a skill
#     directory — through its own probe, which makes no model call.
#   * nothing about a model-backed path. There is no endpoint on a runner and no key in this
#     script, so `demo prove` is not exercised here; that stays a live check, the same one
#     examples/ and the @Tag("live") tests cover.
#
# Failures print the container logs, because a Spring boot failure sits in the middle of them and
# not on the last line.

set -uo pipefail

STUDIO_IMAGE="${STUDIO_IMAGE:-embabel-smith-studio:local}"
RUNNER_IMAGE="${RUNNER_IMAGE:-embabel-smith-runner:local}"
STUDIO_PORT="${STUDIO_PORT:-18080}"
# Generous for a cold container on a shared runner; a warm boot is about 5s.
WAIT_SECONDS="${WAIT_SECONDS:-180}"

failures=0
studio_cid=""

check() {
	local label="$1" ok="$2"
	if [ "$ok" = "0" ]; then
		printf '  ok    %s\n' "$label"
	else
		printf '  FAIL  %s\n' "$label"
		failures=$((failures + 1))
	fi
}

# Suffixed with the pid so two smoke runs on one machine cannot read each other's log.
LOG_FILE="${TMPDIR:-/tmp}/smith-studio-smoke-$$.log"

capture_log() {
	[ -n "$studio_cid" ] && docker logs "$studio_cid" > "$LOG_FILE" 2>&1 || true
}

cleanup() {
	if [ -n "$studio_cid" ]; then
		capture_log
		docker rm -f "$studio_cid" >/dev/null 2>&1 || true
	fi
}
trap cleanup EXIT

echo "images: studio=$STUDIO_IMAGE runner=$RUNNER_IMAGE"

# --- studio ------------------------------------------------------------------
echo "== studio: boot and serve"
# No --rm: a container that exits takes its log with it, and the failure message that matters is
# in that log. cleanup() removes it after capturing.
studio_cid=$(docker run -d -p "$STUDIO_PORT:8080" "$STUDIO_IMAGE")

ready=1
for _ in $(seq 1 "$WAIT_SECONDS"); do
	if curl -fsS -o /dev/null "http://127.0.0.1:$STUDIO_PORT/api/status" 2>/dev/null; then
		ready=0
		break
	fi
	sleep 1
done
check "boots and answers /api/status within ${WAIT_SECONDS}s" "$ready"

if [ "$ready" = "0" ]; then
	# The status body is the honest "no endpoint configured" report: the point is that it answers
	# at all without a key, not what it says about a model.
	status_body=$(curl -fsS "http://127.0.0.1:$STUDIO_PORT/api/status")
	printf '        /api/status -> %s\n' "$status_body"

	# The frontend bundle lives in the image, not in git. A studio that boots and 404s at / is a
	# studio whose npm build produced nothing, which is the failure the Dockerfile asserts against
	# and this is where it would surface anyway.
	root_body=$(curl -fsS "http://127.0.0.1:$STUDIO_PORT/" 2>/dev/null | tr -d '[:space:]')
	case "$root_body" in
	*"<html"* | *"<!doctypehtml"*) check "serves the SPA bundle at /" 0 ;;
	*)
		check "serves the SPA bundle at /" 1
		printf '        got: %s\n' "${root_body:0:120}"
		;;
	esac

	# /api/sources is the spec-source seam. The image names a source (its SMITH_STUDIO_SOURCES
	# default), so an empty list here means an empty directory, which is inert; a non-JSON answer
	# means the route is not there. A studio with no source named at all would not boot — see the
	# BuilderPackSurface note in docker/README.md.
	sources=$(curl -fsS "http://127.0.0.1:$STUDIO_PORT/api/sources")
	case "$sources" in
	\[* | '{'*) check "answers /api/sources with JSON" 0 ;;
	*)
		check "answers /api/sources with JSON" 1
		printf '        got: %s\n' "${sources:0:120}"
		;;
	esac

	# A 404 on an unknown api route, rather than the SPA fallback swallowing everything, is what
	# keeps the UI's own errors debuggable.
	api404=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$STUDIO_PORT/api/not-a-route")
	[ "$api404" = "404" ] && check "unknown /api route is a 404, not the SPA" 0 ||
		check "unknown /api route is a 404, not the SPA" 1
else
	echo "        studio did not become ready; its log follows"
	docker logs "$studio_cid" 2>&1 | tail -40
	failures=$((failures + 3))
fi

# --- runner ------------------------------------------------------------------
echo "== runner: accepts agents and skills"
probe_out=$(docker run --rm "$RUNNER_IMAGE" probe 2>&1)
probe_rc=$?
printf '%s\n' "$probe_out" | sed 's/^/        /'
check "probe exits 0" "$probe_rc"
packs=$(printf '%s\n' "$probe_out" | sed -n 's/^agent packs: //p' | tr -d '[:space:]')
skills=$(printf '%s\n' "$probe_out" | sed -n 's/^skill folders: //p' | tr -d '[:space:]')
if [ "${packs:-0}" -gt 0 ] 2>/dev/null; then
	check "sees $packs agent pack(s) in the source" 0
else
	check "sees at least one agent pack (reported: ${packs:-none})" 1
fi
if [ "${skills:-0}" -gt 0 ] 2>/dev/null; then
	check "sees $skills skill folder(s) in the published directory" 0
else
	check "sees at least one skill folder (reported: ${skills:-none})" 1
fi
case "$probe_out" in
*"endpoint: configured"*) printf '        note: this environment has a model endpoint\n' ;;
esac

echo
if [ "$failures" -eq 0 ]; then
	echo "smoke: all checks passed"
	exit 0
fi
echo "smoke: $failures check(s) failed"
# The EXIT trap has not run yet at this point, so the log has to be captured here rather than
# relied upon from cleanup() — otherwise this prints a previous run's log, or nothing.
capture_log
[ -s "$LOG_FILE" ] && {
	echo "--- studio log (tail) ---"
	tail -40 "$LOG_FILE"
}
exit 1
