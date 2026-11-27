#!/usr/bin/env bash
# Author: Ivan Rodriguez
#
# smith-runner.sh — the entrypoint of the runner image (docker/Dockerfile, --target runner).
#
# It is the "accepts skills and agents" half of the sample. Two CLIs ship in this image, and
# they are the product's own, not a container-only harness:
#
#   smith run        (prod/embabel-smith-runtime)   — deploys the agent specs in a spec source
#                                                      with zero compilation and plans to a goal
#   smith skill-run  (prod/embabel-smith-builder)   — answers one request with a published skill
#                                                      folder's tools attached
#
# Both are GraalVM native images — the same binaries `make native` and the container build
# produce — so they are invoked directly rather than through a JVM. That is the only difference a
# caller can see: there is no JAVA_OPTS to pass, because a native program sizes itself from the
# container's own cgroup and would read a -XX: flag as a program argument it does not understand.
# The two programs stay separate because they are separate modules, each with its own
# application.yml at its classpath root.
#
# Everything below is argument plumbing. The behaviour — which goal is exported, how the planner
# chains the steps, what the trust tier refuses — is the product's, and running these commands on
# a host with a checkout does the same thing.
#
# Modes:
#   prove [INPUT]      run both legs and print a verdict (default)
#   agents [INPUT]     one goal over the agent specs in SMITH_AGENT_SOURCE
#   skill  [REQUEST]   one request over the skills in SMITH_SKILL_DIR
#   probe              say what the image can see; makes no model call
#   run     ARGS...    pass through to `smith run`
#   skill-run ARGS...  pass through to `smith skill-run`
#   build     ARGS...  pass through to `smith build` (needs a model endpoint)
#   help
#
# Both legs need a model endpoint. Point it the way the rest of the project does —
# OPENAI_CUSTOM_BASE_URL, OPENAI_CUSTOM_API_KEY, OPENAI_CUSTOM_MODELS and
# EMBABEL_MODELS_DEFAULT_LLM — with nothing baked into the image.

set -euo pipefail

ROOT="${SMITH_HOME:-/opt/smith}"
# Two programs, one per module, because that is what each module ships: the runtime's native image
# is `smith run`, and the builder's is the `smith` CLI. The builder's binary is renamed on the way
# in so this entrypoint keeps the name the image's ENTRYPOINT already uses.
RUNTIME_BIN="$ROOT/bin/runtime"
BUILDER_BIN="$ROOT/bin/builder"

SMITH_AGENT_SOURCE="${SMITH_AGENT_SOURCE:-$ROOT/samples/agents}"
SMITH_AGENT_GOAL="${SMITH_AGENT_GOAL:-}"
SMITH_AGENT_INPUT="${SMITH_AGENT_INPUT:-Explain how a coffee machine works}"
SMITH_SKILL_DIR="${SMITH_SKILL_DIR:-$ROOT/samples/skills}"
SMITH_SKILL_REQUEST="${SMITH_SKILL_REQUEST:-What can this skill do for me?}"
SMITH_TRUST_TIER="${SMITH_TRUST_TIER:-standard}"

# The CLI wants a source URI, not a path: `folder:` or `git:`, and it refuses a bare path with
# "spec source URI '...' has no scheme". A bare path is also the natural thing to set from a
# -v mount, so it is accepted here and given the scheme it meant rather than failing on syntax.
case "$SMITH_AGENT_SOURCE" in
	folder:* | git:*) ;;
	*) SMITH_AGENT_SOURCE="folder:$SMITH_AGENT_SOURCE" ;;
esac

java_runtime() {
	exec "$RUNTIME_BIN" "$@"
}

java_builder() {
	exec "$BUILDER_BIN" "$@"
}

# A model call is the only thing that can answer either leg, and the failure mode without one is
# an endpoint error buried in a Spring start-up log, so it is worth saying up front.
endpoint_note() {
	if [ -z "${OPENAI_CUSTOM_BASE_URL:-}${OPENAI_API_KEY:-}${ANTHROPIC_API_KEY:-}${GEMINI_API_KEY:-}" ]; then
		echo "note: no model endpoint is configured, so this leg will fail at the model call." >&2
		echo "note: set OPENAI_CUSTOM_BASE_URL, OPENAI_CUSTOM_API_KEY, OPENAI_CUSTOM_MODELS" >&2
		echo "note: and EMBABEL_MODELS_DEFAULT_LLM (or use 'probe', which needs none)." >&2
	fi
}

leg_agents() {
	local input="${1:-$SMITH_AGENT_INPUT}"
	echo "== agents: smith run over $SMITH_AGENT_SOURCE (trust tier $SMITH_TRUST_TIER)"
	local -a argv=(run --source "$SMITH_AGENT_SOURCE" --input "$input" --trust-tier "$SMITH_TRUST_TIER")
	[ -n "$SMITH_AGENT_GOAL" ] && argv+=(--goal "$SMITH_AGENT_GOAL")
	local rc=0
	"$RUNTIME_BIN" "${argv[@]}" || rc=$?
	if [ "$rc" -eq 0 ]; then
		echo "-- agents: ok (goal output above)"
	else
		echo "-- agents: failed, exit $rc" >&2
	fi
	return "$rc"
}

leg_skill() {
	local request="${1:-$SMITH_SKILL_REQUEST}"
	echo "== skills: smith skill-run over $SMITH_SKILL_DIR"
	local rc=0
	"$BUILDER_BIN" skill-run --skill-dir "$SMITH_SKILL_DIR" "$request" || rc=$?
	if [ "$rc" -eq 0 ]; then
		echo "-- skills: ok"
	else
		echo "-- skills: failed, exit $rc" >&2
	fi
	return "$rc"
}

probe() {
	# The source is a URI, not a path, so strip the scheme before touching the filesystem — a
	# directory test against "folder:/opt/..." is a test against a name that cannot exist.
	local uri="$SMITH_AGENT_SOURCE" path kind
	case "$uri" in
	folder:*)
		path="${uri#folder:}"
		kind=folder
		;;
	git:*)
		path="${uri#git:}"
		kind=git
		;;
	*)
		path="$uri"
		kind=folder
		;;
	esac

	echo "agent specs in $uri"
	local packs=0
	if [ "$kind" = git ]; then
		echo "  a git source resolves at run time; nothing to list here"
	elif [ -f "$path/manifest.yml" ]; then
		# A pack named directly. The run will refuse it — a source is a directory that CONTAINS
		# packs — so say so here rather than letting the failure arrive as "contains no agent specs".
		echo "  $(basename "$path") — a pack, not a source: point SMITH_AGENT_SOURCE at its parent"
	else
		local dir
		for dir in "$path"/*/; do
			[ -f "$dir/manifest.yml" ] || continue
			packs=$((packs + 1))
			echo "  $(basename "$dir") — $(find "$dir/steps" -name '*.yml' 2>/dev/null | wc -l) step(s)"
		done
	fi
	echo "agent packs: $packs"
	echo "skills in $SMITH_SKILL_DIR"
	local dir
	local skills=0
	for dir in "$SMITH_SKILL_DIR"/*/; do
		[ -f "$dir/SKILL.md" ] || continue
		skills=$((skills + 1))
		echo "  $(basename "$dir") — $(find "$dir" -type f | wc -l) file(s)"
	done
	echo "skill folders: $skills"
	if [ -n "${OPENAI_CUSTOM_BASE_URL:-}${OPENAI_API_KEY:-}${ANTHROPIC_API_KEY:-}${GEMINI_API_KEY:-}" ]; then
		echo "model endpoint: configured"
	else
		echo "model endpoint: not configured"
	fi
}

# The header comment IS the help text, so it must not drift from it: print the leading comment
# block and stop at the first line that is not one. A fixed line range printed the wrong lines the
# first time the header grew.
usage() {
	awk 'NR > 2 { if ($0 !~ /^#/) exit; sub(/^# ?/, ""); print }' "$0"
}

cmd="${1:-prove}"
[ $# -gt 0 ] && shift || true
case "$cmd" in
	prove)
		endpoint_note
		rc_agents=0
		rc_skill=0
		leg_agents "${1:-}" || rc_agents=$?
		echo
		leg_skill "${1:-}" || rc_skill=$?
		echo
		echo "== verdict"
		printf '   agent specs : %s\n' "$([ "$rc_agents" -eq 0 ] && echo ran || echo "failed ($rc_agents)")"
		printf '   skill folder: %s\n' "$([ "$rc_skill" -eq 0 ] && echo loaded || echo "failed ($rc_skill)")"
		[ "$rc_agents" -eq 0 ] && [ "$rc_skill" -eq 0 ]
		;;
	agents) leg_agents "${1:-}" ;;
	skill) leg_skill "${1:-}" ;;
	probe) probe ;;
	help | --help | -h) usage ;;
	run) java_runtime "$cmd" "$@" ;;
	skill-run) java_builder "$cmd" "$@" ;;
	build | blueprint) java_builder "$cmd" "$@" ;;
	*)
		echo "unknown mode: $cmd" >&2
		usage >&2
		exit 2
		;;
esac
