#!/usr/bin/env bash
#
# parity-agentic.sh — the hand-run parity measurement for a pack flow: `--flow agentic` by default,
# `--flow harness` with SMITH_PARITY_FLOW=harness. Same harness, same bar, same columns either way.
#
# This is NOT a test and no CI runs it. It spends model calls on purpose, which is why it is a
# script a human runs after deciding the answer is worth paying for. Nothing in the repo claims a
# number that this script did not produce.
#
# WHAT IT MEASURES, AND THE BAR
#
# `docs/08-builder.md` §"Who issues the generation call" measured the pack flow against
# the java flow on three inputs and took the do-not-flip branch. The built counts there are this
# run's bar:
#
#   parcel-claims    java OK        pack OK        (pack publishes)
#   Sentinel         java BLOCKED   pack BLOCKED   (both at `security`)
#   TradingAgents    java OK        pack BLOCKED   (pack declined the offered validator,
#                                                   then eval found 16 DANGLING_TYPE_REF)
#
#   built: java 2/3, pack 1/3 — the bar an agentic run has to reach is 2/3.
#
# The agentic route exists to close exactly one of those three gaps: the declined offer on
# TradingAgents. On this flow the validator and the security scanner are run by the build between
# model calls and the step is re-asked with the findings, so a step that declines to call them
# does not skip them. Read the result that way: if TradingAgents is still blocked with the decline
# case closed, the parity gap was never about the tools, and `--flow java` stays the default for
# the reason docs/08 gives rather than this change adding a new one.
#
# The harness arm (SMITH_PARITY_FLOW=harness) closes the same gap — the forcing loop is the same Java
# code, its generator extends the agentic one — and changes one thing on top of it: a step decides what
# to read next instead of every step seeing the corpus the driver assembled. So read a harness run
# against an agentic run rather than only against java: if both pack arms now close the decline case and
# the harness arm is the one that builds TradingAgents, the gap was the reading, not the check; if the
# harness arm builds no more than the agentic arm did, the extra deciding step bought nothing and the
# cost it adds is a regression.
#
# ---------------------------------------------------------------------------
# WHAT IT PRINTS. Per input, one line: the verdict (BUILT, or BLOCKED at <stage>,
# or the exit code when neither report line exists), answered LLM calls, calls
# spent without an answer, how many steps started a tool loop that named at
# least one group, and how many tool calls actually happened. Then a footer with
# `built N of 3` against the 2/3 bar and a sentence about what follows from it.
#
# Two of the four columns need reading before they are believed; both were misread once.
#
# `answered` greps Embabel's `received LLM response` line, and the platform prints that line for an
# ACTION's response — `(action:surveyBlueprint) received LLM response of type String ... in 40 seconds`
# — not for the per-round responses inside a tool loop, which go out as `starting tool loop [...]` lines
# on an embabel-platform thread. So on the agentic and harness arms this column counts landed
# action-level interactions and under-counts everything the loop did: one recorded build logs
# `answered=0` beside 386 tool calls, and every one of those calls is model output asking for a tool,
# hence an answered completion this grep never saw. The java and single-shot arms' 9-14 answered calls
# are comparable with each other on this column; a tool-loop arm's number is not a count of what the
# model answered, and its zero is not "nothing was answered".
#
# `offered` counts loops, and the bracketed list is that step's own tool surface, which differs by
# input: on parcel-claims it reads [assemble_corpus, list_docs, read_doc, scan_spec, validatePack] and
# on TradingAgents and Sentinel it reads [assemble_corpus, list_docs, list_packs, rank_packs, read_doc]
# — no validator on offer at all. So offered>0 says a step was offered something, never that a validator
# was declined. `offered>0 with calls=0` is still the decline signature the prior record measured; a
# nonzero calls column with no verdict line is a step that used its tools and ran out of clock instead.
# Each input also leaves examples/out/<blueprint>/<flow>/{build.log,report.md,exit.txt}, where <flow>
# is the arm you ran, so an agentic run and a harness run sit side by side and nothing overwrites the
# other's evidence.
#
# ---------------------------------------------------------------------------
# WHAT IT COSTS. Read this before running it.
#
# One build is not one model call, and the agentic flow costs MORE per build than java, not less:
# a forced re-invoke after a finding is a call the java flow never pays.
#
#   generation          2 calls minimum — the single-agent and multi-agent candidates
#   tool loop           up to 20 iterations per step, each an answered call
#   forced re-ask       1 call per round the host-side checks report something, bounded
#   eval                1 call per eval task against the deployed pack, plus refinement
#
# The harness arm adds a third step to that arithmetic — survey, plan, draft — so it pays its own tool
# loop on top of the drafting step's, which is the price of the deciding act this arm exists to measure.
# Budget it above the agentic arm, not beside it: three steps × up to 20 iterations plus the same
# forcing and the same eval.
#
# The recorded java arm ran 9-14 answered calls per build and 4-24 calls spent without an answer
# (endpoint timeouts, counted separately because they are the endpoint's cost profile, not the
# flow's). Expect the agentic arm above those, and count both columns: an agentic build that
# publishes at 40 answered calls is a parity pass on output and a regression on cost, and the
# summary line says both.
#
# Three builds against a local endpoint is on the order of 30-90 minutes. Long chains of
# "LLM action:<name> timed out" are endpoint latency (Embabel retries a 60s-timeout interaction
# up to 10 times), not a hung builder — the prior record re-ran two inputs at 2700 s after a
# 1800 s cap cut them off with 62 spent calls each, which is why SMITH_PARITY_TIMEOUT defaults
# to 2700 here.
# ---------------------------------------------------------------------------
#
# It does NOT run the java arm. The java numbers above were measured on this same harness and the
# fixes since then do not touch the java path's generation — but `AgentSpecInvokerFactory` is
# also the eval deploy seam, so if you want the comparison to be simultaneous rather than
# carried forward, run `./generate-spec.sh <blueprint>` for each input too and say so in the
# record you write.
#
# Usage:
#   ./parity-agentic.sh                       # all three inputs the prior record used, --flow agentic
#   ./parity-agentic.sh TradingAgents         # one input
#   SMITH_PARITY_TIMEOUT=1800 ./parity-agentic.sh
#   SMITH_PARITY_FLOW=harness ./parity-agentic.sh   # the harness arm instead (--flow harness)
#
# SMITH_PARITY_FLOW picks the arm: `agentic` (the default, so an unqualified run still reproduces the
# number the agentic change's own record expects) or `harness`. Anything else is a usage error and
# exits 2 before anything is resolved or spent.
#
# Exit code is 0 when every input reached a verdict and 1 when any build was cut off by the
# timeout — a BLOCKED build is a result, not a failure, and exits 0.

set -euo pipefail
cd "$(dirname "$0")"
# shellcheck disable=SC1091
. ./smith-cli.sh

timeout_s="${SMITH_PARITY_TIMEOUT:-2700}"
flow="${SMITH_PARITY_FLOW:-agentic}"
case "$flow" in
  agentic) flow_pack="builder-agentic-blueprint, two steps, host-side checks" ;;
  harness) flow_pack="builder-harness, three steps: survey, decide what to read next, draft" ;;
  *)
    echo "SMITH_PARITY_FLOW must be agentic or harness, not '$flow'" >&2
    exit 2 ;;
esac
inputs=("$@")
if [ ${#inputs[@]} -eq 0 ]; then
  inputs=(parcel-claims Sentinel TradingAgents)
fi

smith_load_endpoint_env

# The three inputs the prior record used, at the paths it used them. Two are examples; the third
# is the builder's own test blueprint, which is where that record read it from.
parity_dir() {
  local slug="$1"
  case "$(echo "$slug" | tr '[:upper:]' '[:lower:]')" in
    parcel-claims | parcelclaims)
      echo "$SMITH_ROOT/prod/embabel-smith-builder/src/test/resources/blueprints/parcel-claims" ;;
    *) blueprint_dir "$slug" ;;
  esac
}

echo "flow      : ${flow} (${flow_pack})"
echo "timeout   : ${timeout_s}s per build (SMITH_PARITY_TIMEOUT)"
echo "endpoint  : ${OPENAI_CUSTOM_BASE_URL:-unset} / model ${EMBABEL_MODELS_DEFAULT_LLM:-default-llm}"
echo "bar       : 2 of 3 built, from the java arm of the prior parity record"
echo

# The classpath is resolved once, here, rather than per build: `timeout` cannot exec a shell
# function, so the loop below runs `java` directly and cannot call run_smith. Compiling and
# resolving up front also means a build is not cut short by the classpath resolution landing
# inside its own timeout.
echo "classpath : resolving the builder and its dependencies"
SMITH_JCP="$SMITH_ROOT/$BUILDER_MODULE/target/classes:$(smith_classpath)"
echo

summary=()
worst=0
for slug in "${inputs[@]}"; do
  docs="$(parity_dir "$slug")"
  if [ ! -d "$docs" ]; then
    echo "No blueprint directory at $docs — available:" >&2
    ls -1 "$SMITH_ROOT/examples/blueprints" >&2
    exit 2
  fi

  out_dir="$SMITH_ROOT/examples/out/$(basename "$docs")/$flow"
  mkdir -p "$out_dir"
  echo "==> $slug  ($docs)"

  # The same harness the prior parity record used: a hard timeout around a plain `java -cp` of
  # the builder, because a build that the endpoint has stalled can sit there indefinitely and an
  # untimed batch is how an afternoon disappears.
  #
  # Pack loading does not depend on where the build starts: each pack generator addresses its pack through
  # core/BuilderPackPaths, which resolves it as a classpath resource under the module's
  # src/main/resources/specs. The cd into the module is the harness's standing convention, not a
  # requirement — --docs, --out and the classpath are all absolute — so a "failed to load the builder
  # pack" line here now means the build failed, not that the harness was standing in the wrong directory.
  set +e
  (cd "$SMITH_ROOT/$BUILDER_MODULE" && timeout -k 30 "$timeout_s" \
    java -cp "$SMITH_JCP" "$BUILDER_MAIN" build --docs "$docs" \
      --target agent-spec \
      --technique single-shot \
      --flow "$flow" \
      --out "folder:$out_dir/published" \
      --overwrite) \
    >"$out_dir/build.log" 2>&1
  exit_code=$?
  set -e
  smith_write_report "$out_dir/build.log" "$out_dir/report.md"
  echo "$exit_code" >"$out_dir/exit.txt"

  if [ "$exit_code" -eq 124 ] || [ "$exit_code" -eq 137 ]; then
    echo "    cut off by the ${timeout_s}s timeout — this one is NOT a verdict." >&2
    worst=1
  fi

  # The three columns the prior record counted, by the same log patterns so the numbers are
  # comparable rather than merely similar. The fourth separates the two things a zero in the
  # third column can mean: a step that was offered tools and declined them, which is the
  # recorded failure mode, and a step that never started a loop at all.
  answered=$(grep -c 'received LLM response' "$out_dir/build.log" 2>/dev/null || true)
  spent=$(grep -cE 'Retry attempt|timed out after' "$out_dir/build.log" 2>/dev/null || true)
  # Underscores included, deliberately, and this column is not comparable with the prior record's.
  # The pattern here used to be 'calling tool [A-Za-z]+\(', which cannot match a snake_case name —
  # and every tool a build actually reaches is snake_case (read_doc, list_docs, list_packs,
  # rank_packs, assemble_corpus, scan_spec, and the derived <skill>_<file> names). It printed 0
  # against logs holding 386, 183, 50 and 13 calls, so a zero in this column in any earlier run
  # said nothing about whether the step used its tools.
  tool_calls=$(grep -cE 'calling tool [A-Za-z_][A-Za-z0-9_]*\(' "$out_dir/build.log" 2>/dev/null || true)
  offered=$(grep -cE 'starting tool loop \[[^]]' "$out_dir/build.log" 2>/dev/null || true)
  verdict="exit $exit_code"
  # Anchored on the agent-spec report's first word: BuildResult.report() prints "BUILD OK:" and
  # "BUILD BLOCKED at". The skill target's SkillBuildResult.report() (skill/SkillBuildResult.java:74,98)
  # prints "SKILL BUILD OK:" and "SKILL BUILD BLOCKED at", which neither ^BUILD pattern matches — so
  # widening this script to --target skill means adding both strings here first, or every skill run
  # counts as neither BUILT nor BLOCKED and the built count, which is the bar, quietly reads 0.
  if grep -q '^BUILD OK' "$out_dir/report.md" 2>/dev/null; then
    verdict="BUILT"
  elif grep -q '^BUILD BLOCKED' "$out_dir/report.md" 2>/dev/null; then
    verdict="BLOCKED at $(sed -n 's/^BUILD BLOCKED at \([a-z]*\).*/\1/p' "$out_dir/report.md" | head -1)"
  fi
  summary+=("$(printf '%-14s %-24s answered=%-4s spent=%-4s offered=%-3s tool calls=%s' \
    "$slug" "$verdict" "${answered:-0}" "${spent:-0}" "${offered:-0}" "${tool_calls:-0}")")
  echo "    $verdict, answered=${answered:-0} spent-without-answer=${spent:-0} \
steps-offered-tools=${offered:-0} tool-calls=${tool_calls:-0}"
  echo "    report: $out_dir/report.md"
done

echo
echo "--- parity summary (built count is the bar, cost is the price) ---------"
built=0
for line in "${summary[@]}"; do
  echo "  $line"
  case "$line" in *BUILT*) built=$((built + 1)) ;; esac
done
echo
echo "built ${built} of ${#inputs[@]} against a bar of 2/3."
if [ "$built" -ge 2 ]; then
  echo "Output parity holds. Whether the flip is justified is a separate question: check the"
  echo "answered-call column against the java arm's 9-14 before arguing it is."
else
  echo "Output parity does not hold on this run. The default stays --flow java, and the reason"
  echo "is recorded here rather than in a prompt fix someone tries next."
fi
exit "$worst"
