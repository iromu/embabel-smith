#!/usr/bin/env bash
#
# run-loop.sh — the full build→run loop (task 7.1).
#
#   [1/2] poc-3-builder-agent : a natural-language description → a validated skill-pack on disk
#   [2/2] poc-1-no-code-runtime: run that produced skill-pack over an input and print the goal output
#
# Requires a LIVE model endpoint (any OpenAI-compatible backend) via env vars — see README "Model endpoint":
#   OPENAI_CUSTOM_BASE_URL, OPENAI_CUSTOM_API_KEY, OPENAI_CUSTOM_MODELS, EMBABEL_MODELS_DEFAULT_LLM
#
# Usage:
#   ./run-loop.sh [description] [input]
#   PLANNER=UTILITY BUILDER_OUTPUT_DIR=./built-skills ./run-loop.sh
#
set -euo pipefail
cd "$(dirname "$0")"

# --- endpoint check (fail fast with a clear message) -------------------------
missing=()
for v in OPENAI_CUSTOM_BASE_URL OPENAI_CUSTOM_API_KEY OPENAI_CUSTOM_MODELS; do
  [ -z "${!v:-}" ] && missing+=("$v")
done
if [ ${#missing[@]} -gt 0 ]; then
  echo "Missing model endpoint env vars: ${missing[*]}" >&2
  echo "Set them to point at any OpenAI-compatible endpoint (LiteLLM / OpenAI / Ollama), then re-run." >&2
  echo "See README 'Model endpoint (env-driven, design D4)'." >&2
  exit 1
fi

# --- inputs (overridable) ----------------------------------------------------
DESCRIPTION="${1:-A skill that explains a topic in plain language and posts it as a background message}"
INPUT="${2:-Explain how a coffee machine works}"
PLANNER="${PLANNER:-GOAP}"
# Absolute by default so the built pack is found regardless of the forked app's working directory.
OUTPUT_DIR="${BUILDER_OUTPUT_DIR:-$(pwd)/built-skills}"

echo "==> [0/2] Building modules to the local repo (mvn install -DskipTests)"
mvn -q install -DskipTests

# Per-run inputs are passed as OS environment variables, NOT -D system properties:
# spring-boot:run forks the app with the module dir as CWD and does NOT forward Maven -D
# properties to it, so -Dbuilder.* would silently fall back to defaults. Env vars are inherited
# by the forked JVM and Spring's relaxed binding maps builder.X -> BUILDER_X.
echo "==> [1/2] Builder — description: $DESCRIPTION"
BUILD_LOG="$(mktemp)"
trap 'rm -f "$BUILD_LOG"' EXIT
if ! BUILDER_DESCRIPTION="$DESCRIPTION" BUILDER_OUTPUT_DIR="$OUTPUT_DIR" \
      mvn -pl poc/poc-3-builder-agent spring-boot:run 2>&1 | tee "$BUILD_LOG"; then
  echo "==> Builder failed (see output above)." >&2
  exit 1
fi

PACK_PATH="$(grep -E 'Built skill-pack:' "$BUILD_LOG" | tail -1 | sed -E 's/.* at //')"
if [ -z "${PACK_PATH:-}" ] || [ ! -d "$PACK_PATH" ]; then
  echo "Could not locate the built skill-pack (expected under $OUTPUT_DIR)." >&2
  exit 1
fi
echo "    built skill-pack: $PACK_PATH"

echo "==> [2/2] No-code runtime — input: $INPUT (planner=$PLANNER)"
BUILDER_PACK_DIR="$PACK_PATH" BUILDER_INPUT="$INPUT" BUILDER_PLANNER="$PLANNER" \
  mvn -pl poc/poc-1-no-code-runtime spring-boot:run

echo "==> Loop complete."
