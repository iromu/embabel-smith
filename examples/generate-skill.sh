#!/usr/bin/env bash
#
# generate-skill.sh — build ONE Agent Skill from ONE blueprint.
#
# What it does, in order:
#   1. reads examples/blueprints/<blueprint>/  — the business-process documents
#   2. runs `smith build --docs <that directory> --target skill`
#   3. prints the build's own report as it happens, and writes it verbatim to
#      examples/out/<blueprint>/skill/report.md
#   4. publishes the skill, if every gate passed, to examples/out/<blueprint>/skill/published/
#
# report.md is the CLI's report and nothing else; build.log is everything the JVM wrote,
# startup chatter and every timeout included; the terminal gets a filtered feed of that log.
# The JVM logs to stdout along with its report, so the split is made by pattern afterwards.
#
# The pipeline behind that one command (SkillBuilderService): generate the SKILL.md, validate
# its frontmatter, have the model draft an evals.json suite, run every scenario twice — once
# with the skill in the prompt, once without — grade both, gate on the lift between the two
# pass rates, then run the static security scan, stamp the lifecycle and publish.
#
# ---------------------------------------------------------------------------
# THIS IS THE EXPENSIVE ONE. ~15-19 model calls per blueprint:
#
#   skill generator        1 call, retried up to 3 times if the answer will not parse
#   evals.json drafter     1 call, retried up to 3 times, and it is asked to write
#                          3-4 scenarios, so a fussy endpoint pays 3 calls just to get a suite
#   eval runner            4 calls PER SCENARIO — run with the skill, grade it, run the
#                          baseline without it, grade that. With 4 scenarios this is 16
#   security scan          free (static, offline)
#
# There is no refinement loop on this path, so the cost is bounded, but the eval runner is
# the bulk of it and it scales with the number of scenarios the model chose to write. On a
# contended local endpoint, run ONE blueprint at a time, sequentially, and not alongside
# anything else that talks to the same GPU.
# ---------------------------------------------------------------------------
#
# It deliberately does NOT pass --extract. See generate-spec.sh: the discovery prompt wants a
# codebase, and a blueprint is already business-process prose.
#
# Usage:
#   ./generate-skill.sh <blueprint>
#   ./generate-skill.sh <blueprint> -- --max-refine 0    # everything after -- goes straight to
#                                                        # `smith build`; see `smith build --help`
#                                                        # for the flags it actually takes
#
# The lift gate is not a CLI flag — it is BuildOptions.DEFAULT_MIN_LIFT, 0.0, meaning the
# with-skill pass rate must merely beat the no-skill baseline. A blocked build is a normal,
# readable outcome — the report carries the per-scenario grading so you can see which
# scenarios did not move.
#
# A different model: the CLI has no --model flag; export EMBABEL_MODELS_DEFAULT_LLM, or point
# SMITH_LLM_ENV at an env file that names it.
#
# Exit code is the CLI's: 0 built and published, 1 blocked at a gate, 2 usage error.

set -euo pipefail
cd "$(dirname "$0")"
# shellcheck disable=SC1091
. ./smith-cli.sh

blueprint="${1:-}"
if [ -z "$blueprint" ]; then
  echo "Usage: ./generate-skill.sh <blueprint> [-- <extra smith build args>]" >&2
  ls -1 "$SMITH_ROOT/examples/blueprints" >&2
  exit 2
fi
shift
docs="$(blueprint_dir "$blueprint")"
extra=("$@")

smith_load_endpoint_env

out_dir="$SMITH_ROOT/examples/out/$(basename "$docs")/skill"
mkdir -p "$out_dir"

echo "blueprint : $docs"
echo "target    : skill"
echo "output    : $out_dir"
echo "endpoint  : $OPENAI_CUSTOM_BASE_URL / model ${EMBABEL_MODELS_DEFAULT_LLM:-default-llm}"
echo

set +e
run_smith build --docs "$docs" \
  --target skill \
  --out "folder:$out_dir/published" \
  --overwrite \
  ${extra[@]+"${extra[@]}"} \
  >"$out_dir/build.log" 2>&1 &
cli_pid=$!
# smith_progress blocks here until the build exits; `wait` in the current shell (not inside
# $(...), whose subshell cannot see this child) then gives its real exit code.
smith_progress "$out_dir/build.log" "$cli_pid"
exit_code=0
wait "$cli_pid" || exit_code=$?
smith_write_report "$out_dir/build.log" "$out_dir/report.md"
set -e

echo "$exit_code" >"$out_dir/exit.txt"
echo
echo "--- build report -----------------------------------------------------"
if [ -s "$out_dir/report.md" ]; then
  cat "$out_dir/report.md"
else
  # The CLI prints its report only when a build reaches a verdict. An empty report means the
  # JVM died before one — a bad endpoint, a context that would not start — so say so and
  # show the end of the log instead of an empty screen.
  echo "(no build report — the CLI reached no verdict. Last lines of build.log:)"
  tail -20 "$out_dir/build.log"
fi
echo
if [ "$exit_code" -eq 0 ]; then
  echo "==> published: $out_dir/published"
elif [ -s "$out_dir/report.md" ]; then
  echo "==> blocked or failed (exit $exit_code) — report.md names the stage and the reason." >&2
else
  echo "==> no verdict reached (exit $exit_code) — build.log holds the whole stream." >&2
fi
exit "$exit_code"
