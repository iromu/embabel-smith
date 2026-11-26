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
# Two launch mechanisms, one per module, because that is what each module ships: the runtime has an
# executable jar (`repackage` with a classifier, so its library consumers still get the thin jar),
# while the builder's packaged form is the native `smith` binary and so gets the classes-plus-lib
# layout that `spring-boot:run` would hand a JVM.
RUNTIME_JAR="$ROOT/bin/runtime.jar"
BUILDER_CP="$ROOT/builder/classes:$ROOT/lib/*"
BUILDER_MAIN="com.embabel.smith.builder.SmithBuilderApplication"

SMITH_AGENT_SOURCE="${SMITH_AGENT_SOURCE:-$ROOT/samples/agents}"
SMITH_AGENT_GOAL="${SMITH_AGENT_GOAL:-}"
SMITH_AGENT_INPUT="${SMITH_AGENT_INPUT:-Explain how a coffee machine works}"
SMITH_SKILL_DIR="${SMITH_SKILL_DIR:-$ROOT/samples/skills}"
SMITH_SKILL_REQUEST="${SMITH_SKILL_REQUEST:-What can this skill do for me?}"
SMITH_TRUST_TIER="${SMITH_TRUST_TIER:-standard}"

# JAVA_OPTS is a string of JVM flags, and they must reach java as separate arguments: quoted, the
# whole string becomes ONE argument and the JVM dies with "Improperly specified VM option".
# Splitting it once here is why nothing below has to remember that.
read -r -a JVM_ARGS <<< "${JAVA_OPTS:--XX:MaxRAMPercentage=75.0}"

# The CLI wants a source URI, not a path: `folder:` or `git:`, and it refuses a bare path with
# "spec source URI '...' has no scheme". A bare path is also the natural thing to set from a
# -v mount, so it is accepted here and given the scheme it meant rather than failing on syntax.
case "$SMITH_AGENT_SOURCE" in
	folder:* | git:*) ;;
	*) SMITH_AGENT_SOURCE="folder:$SMITH_AGENT_SOURCE" ;;
esac

java_runtime() {
	exec java "${JVM_ARGS[@]}" -jar "$RUNTIME_JAR" "$@"
}

java_builder() {
	exec java "${JVM_ARGS[@]}" -cp "$BUILDER_CP" "$BUILDER_MAIN" "$@"
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
	java "${JVM_ARGS[@]}" -jar "$RUNTIME_JAR" "${argv[@]}" || rc=$?
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
	java "${JVM_ARGS[@]}" -cp "$BUILDER_CP" "$BUILDER_MAIN" \
		skill-run --skill-dir "$SMITH_SKILL_DIR" "$request" || rc=$?
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

usage() {
	sed -n '3,30p' "$0" | sed 's/^# \{0,1\}//'
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
