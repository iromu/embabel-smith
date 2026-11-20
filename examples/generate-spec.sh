#!/usr/bin/env bash
#
# generate-spec.sh — build ONE executable agent spec from ONE blueprint.
#
# What it does, in order:
#   1. reads examples/blueprints/<blueprint>/  — the business-process documents
#   2. runs `smith build --docs <that directory> --target agent-spec`
#   3. prints the build's own report as it happens, and writes it verbatim to
#      examples/out/<blueprint>/agent-spec-<technique>/report.md
#   4. publishes the spec, if every gate passed, to
#      examples/out/<blueprint>/agent-spec-<technique>/published/
#
# Three files come out of a run and they are deliberately different:
#
#   report.md   the CLI's report — the candidate it chose, the eval score, the stage a blocked
#               build stopped at, and any issue text. Nothing else.
#   build.log   everything the JVM wrote, startup chatter and every timeout included. This is
#               for when a build did something you cannot explain; it is not meant to be read
#               while it runs.
#   the tty     a filtered feed of build.log, so the terminal shows which action is running and
#               how long it took rather than 116 lines of model-bean registration.
#
# (The JVM logs to stdout along with its report, so the split is made by pattern afterwards,
# not by stream. Worth fixing in the product; the script does not depend on that choice.)
#
# This is the product CLI, not a test harness: what the script runs is what a user runs, so a
# green run is evidence about the product path and not about a test double of it.
#
# ---------------------------------------------------------------------------
# WHAT IT COSTS. Read this before running a batch. One build is NOT one model call:
#
#   generation          2 calls — the single-agent candidate and the multi-agent candidate
#   eval                1 call per eval task against the deployed pack
#   refinement          up to --max-refine rounds (default 3), each rewriting failing
#                       prompts from the eval failures and re-scoring
#   process eval        the multi-agent candidate is deployed and scored too
#
# The score gate defaults to 1.0 — every eval task must pass — so a build whose smoke task
# fails keeps refining, and that is where the calls multiply. The default eval set is the
# CLI's single smoke task (EvalTask.smokeDefault); the CLI takes no richer eval file, so a
# build here is scored on "the spec runs and produces output", nothing more.
#
# Budget 5-10 minutes per build against a local endpoint. Long chains of
# "LLM action:<name> timed out" are endpoint latency (Embabel retries a 60s-timeout
# interaction up to 10 times), not a hung builder.
#
# To cut the cost on a first pass, pass -- --max-refine 0.
# ---------------------------------------------------------------------------
#
# It deliberately does NOT pass --extract. That flag runs business_process_discovery.jinja,
# whose prompt asks for a CODEBASE ("Analyse the entire codebase", "Do not describe classes,
# functions, modules or files"). A blueprint here is already a business-process document, so
# extraction would re-summarise business prose into less detailed business prose and pay a
# full-context call to do it. --extract belongs in front of source code, not a blueprint.
#
# Usage:
#   ./generate-spec.sh <blueprint>                       # single-shot, the default technique
#   ./generate-spec.sh <blueprint> --technique embabel   # Embabel's NL compiler. Expected to
#                                                        # block at `build` with DANGLING_TYPE_REF:
#                                                        # the compiler emits no intermediate
#                                                        # types. Run it for the evidence, not
#                                                        # for a published spec.
#   ./generate-spec.sh <blueprint> -- --max-refine 0     # everything after -- goes to the CLI
#
# A different model: the CLI has no --model flag; the model is chosen by the endpoint env.
# Export EMBABEL_MODELS_DEFAULT_LLM before the script sources it, or point SMITH_LLM_ENV at
# an env file that names the model you want.
#
# Exit code is the CLI's: 0 built and published, 1 blocked at a gate, 2 usage error. A blocked
# build is a normal outcome and still leaves its report.md behind.

set -euo pipefail
cd "$(dirname "$0")"
# shellcheck disable=SC1091
. ./smith-cli.sh

blueprint="${1:-}"
if [ -z "$blueprint" ]; then
  echo "Usage: ./generate-spec.sh <blueprint> [--technique single-shot|embabel] [-- <extra smith build args>]" >&2
  ls -1 "$SMITH_ROOT/examples/blueprints" >&2
  exit 2
fi
shift
docs="$(blueprint_dir "$blueprint")"

technique="single-shot"
extra=()
while [ $# -gt 0 ]; do
  case "$1" in
    --technique) technique="$2"; shift 2 ;;
    --) shift; extra=("$@"); break ;;
    *) extra+=("$1"); shift ;;
  esac
done

smith_load_endpoint_env

out_dir="$SMITH_ROOT/examples/out/$(basename "$docs")/agent-spec-$technique"
mkdir -p "$out_dir"

echo "blueprint : $docs"
echo "target    : agent-spec ($technique)"
echo "output    : $out_dir"
echo "endpoint  : $OPENAI_CUSTOM_BASE_URL / model ${EMBABEL_MODELS_DEFAULT_LLM:-default-llm}"
echo

# --overwrite because a rerun is meant to replace this folder's evidence, and without it the
# publisher's collision gate stops every second run at `publish`.
#
# Everything the JVM writes — its log and the CLI's report alike, since Spring Boot 4.1's
# console appender has no stderr target — goes to build.log, and the report is then cut out of
# it into report.md by its verdict line. The log is streamed to the tty as it is written, so a
# watched run shows progress rather than 116 lines of model-bean registration; the unfiltered
# text is kept, because the line that explains a failed build is always the one the filter
# dropped.
set +e
run_smith build --docs "$docs" \
  --target agent-spec \
  --technique "$technique" \
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
