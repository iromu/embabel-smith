#!/usr/bin/env bash
#
# generate-blueprint.sh — reconstruct ONE repository's business process as a blueprint.
#
# The other two scripts start from a blueprint. This one makes one, which is the job
# prompts/business_process_discovery.jinja was written for and the step that was missing:
# the prompt asks for a codebase, and until now nothing in the product read one — the git
# path cloned a repository and then filtered it to .md.
#
# What it does, in order:
#   1. runs `smith blueprint --repo <url>`, `--zip <archive>` or `--path <checkout>`
#   2. the builder reads the codebase — shallow-cloning the URL or extracting the archive
#      into a temp directory it deletes afterwards — assembles a corpus of its documents and
#      source, and runs the discovery prompt over it in ONE call
#   3. cuts the answer into the seven deliverables the prompt names, adds a README index
#      saying where they came from, and writes them to examples/blueprints/<name>/
#   4. prints the run report as it happens and writes it verbatim to
#      examples/out/<name>/blueprint/report.md
#
# The result is an input, not an output: once it lands, ./generate-spec.sh <name> and
# ./generate-skill.sh <name> can read it like any other blueprint.
#
# ---------------------------------------------------------------------------
# ONE CALL, AND THE BIGGEST ONE THE BUILDER MAKES. The corpus is capped at ~200K
# characters, and the answer is seven documents rather than one spec, so this is both the
# longest prompt and the longest completion the builder sends. Expect a few minutes against
# a local endpoint, not the tens of seconds a generation takes.
#
# No repository fits a context window — the example below is 3.3M characters of source — so
# the corpus is bounded, not complete: the repository's own documents are read first and may
# take at most half the cap, and source gets the rest. The report and the generated README
# both state how many files were left out, because a blueprint of a slice of a repository is
# a different claim from a blueprint of the whole thing. For a monorepo, --subdir is usually
# the better answer than letting the cap decide.
# ---------------------------------------------------------------------------
#
# It refuses to write over a blueprint that already exists, because the ones under
# examples/blueprints/ are hand-written business documentation. --force overrides that, and
# replaces the documents the previous run wrote rather than leaving a stale set beside a
# report that says they are missing. The replacement happens once the new answer is in hand,
# so a run whose endpoint times out mid-call leaves the blueprint it was given rather than
# an empty directory.
#
# WHAT A PARTIAL RUN LOOKS LIKE, measured here against the example repository: the local
# endpoint produced a strong BUSINESS_OVERVIEW.md and stopped, so the run wrote that one
# document, named the six deliverables it did not get, and exited 1. Where an answer named no
# deliverable at all, it was kept whole in DISCOVERY_ANSWER.md rather than thrown away with
# the call that paid for it. A small endpoint is a reason to narrow the corpus with --subdir
# or to name a bigger model in the env — not a reason to accept a half-blueprint quietly.
#
# Usage:
#   ./generate-blueprint.sh agentic-software-factory        # a download in examples/tmp/
#   ./generate-blueprint.sh tmp/agentic-software-factory-main.zip
#   ./generate-blueprint.sh https://github.com/OmegaMc1331/agentic-software-factory.git
#   ./generate-blueprint.sh git@github.com:acme/shop.git --name shop --subdir apps/shop
#   ./generate-blueprint.sh --path /home/me/checkouts/shop --force
#   ./generate-blueprint.sh <url> -- --subdir crates/engine   # after --, straight to the CLI
#
# A bare name is looked up under examples/tmp/, the way generate-spec.sh looks up a blueprint:
# exact first, then case- and hyphen-insensitive, and a download's name may be longer than the
# repository's because it carries the ref — so `agentic-software-factory` and `trading` find
# agentic-software-factory-main.zip and TradingAgents-main.zip. Running the script with no
# argument lists the downloads it could read; a name that matches none is a usage error rather
# than a clone of something nobody named, and --repo is how you insist on the clone.
#
# An archive is the cheaper way to run an example: a repository download (GitHub's Download
# ZIP button, or codeload.<host>/…/<ref>) needs no network and no clone, and yields the same
# corpus the clone would — measured identical on the example above at 23 files and 199,775
# characters either way. The archive's wrapper directory is lifted, so the paths the model is
# shown are the repository's own, and the blueprint is named after the archive: pass --name if
# the ref suffix on a download name is not what you want.
#
# The directory name is the last segment of the URL, archive or path, without its .git or .zip
# suffix — so a download names its blueprint after itself including the ref. --name overrides
# it, which is also how you keep two runs of the same repository apart.
#
# After --, the pass-through may not name --out or --overwrite: where the blueprint goes and
# whether an existing one may be replaced are this script's decisions, since they are what
# its guard over hand-written blueprints is made of.
#
# A different model: the CLI has no --model flag; export EMBABEL_MODELS_DEFAULT_LLM, or point
# SMITH_LLM_ENV at an env file that names it.
#
# Exit code is the CLI's: 0 every deliverable written, 1 refused, failed or left
# deliverables missing, 2 usage error. A partial blueprint is a normal outcome and still
# leaves the documents it did produce.

set -euo pipefail
cd "$(dirname "$0")"
# shellcheck disable=SC1091
. ./smith-cli.sh

usage() {
  echo "Usage: ./generate-blueprint.sh <codebase> [--name <name>] [--subdir <path>] [--force]" >&2
  echo "       where <codebase> is a git URL, a --zip <archive> or a --path <checkout>" >&2
  echo "Everything after -- goes straight to \`smith blueprint\`; run it without --out to" >&2
  echo "see the flags it takes." >&2
  exit 2
}

# An option needs its value. Reading $2 unguarded aborts under `set -u` with exit 1, and
# exit 1 is this script's code for "a run reached a verdict and did not pass".
needs_value() {
  if [ $# -lt 2 ] || [ -z "$2" ]; then
    echo "$1 expects a value." >&2
    usage
  fi
}

kind=""
location=""
explicit=0
TMP_ARCHIVES="$SMITH_ROOT/examples/tmp"

# One codebase, named once.
refuse_second() {
  echo "Name one codebase: one of a git URL, an archive or a directory." >&2
  exit 2
}

# The downloads that could be read, as their names are given on the command line. An unpacked
# tree may sit alongside them and is not one: find_archive matches files, not directories.
list_archives() {
  local listing
  listing="$(find "$TMP_ARCHIVES" -maxdepth 1 -type f -iname '*.zip' 2>/dev/null | sed 's|.*/||; s/^/  /' | sort)"
  printf '%s\n' "${listing:-  (none)}" >&2
}

# The archive a name refers to, or nothing. As blueprint_dir resolves a blueprint, the exact
# name is tried first and then a case- and hyphen-insensitive match, so
# `agentic-software-factory` finds agentic-software-factory-main.zip the way `trading-agents`
# finds TradingAgents/ — a forge download carries the ref in its name, so a name may be a
# prefix of it.
find_archive() {
  local slug="${1%.zip}"
  if [ -f "$TMP_ARCHIVES/$slug.zip" ]; then
    echo "$TMP_ARCHIVES/$slug.zip"
    return
  fi
  find "$TMP_ARCHIVES" -maxdepth 1 -type f -iname "${slug//-/*}*.zip" 2>/dev/null | head -1
}

# An archive as a path, resolved; or as a name, looked up under examples/tmp/ and missed
# loudly. A name that matches nothing is a usage error rather than a clone attempt, because
# a repository nobody named is exactly what a mistyped name would otherwise fetch.
resolve_archive() {
  local asked="$1"
  if [ -f "$asked" ]; then
    realpath "$asked"
    return
  fi
  local match
  match="$(find_archive "$asked")"
  if [ -z "$match" ]; then
    echo "No archive named '$asked'. Archives in examples/tmp/:" >&2
    list_archives
    echo "Download one with:" >&2
    echo "  curl -L -o examples/tmp/NAME.zip https://codeload.github.com/OWNER/REPO/zip/refs/heads/REF" >&2
    exit 2
  fi
  realpath "$match"
}

# A leading dash is always an option, so `-h` is help rather than a repository named "-h"
# and a typo'd flag cannot become a blueprint directory. A positional is an archive when it
# is one — a `.zip`, or a bare name matching a download — and a repository to clone otherwise;
# a slash, a colon or an `@` is what makes it a URL or a path. A bare word that matches no
# download is refused rather than cloned, because a bare word is not a git URL, and a name
# that mistypes is exactly the run that would otherwise fetch something nobody asked for.
if [ $# -gt 0 ] && [ "${1#-}" = "$1" ]; then
  location="$1"
  case "$location" in
    *.zip) kind="zip" ;;
    */* | *:* | *@*) kind="repo" ;;
    *)
      kind="zip"
      location="$(find_archive "$location")"
      if [ -z "$location" ]; then
        echo "'$1' is no archive under examples/tmp/, and not a git URL either — those have a" >&2
        echo "host or a path in them. Pass --repo to insist on a clone. Archives available:" >&2
        list_archives
        exit 2
      fi ;;
  esac
  explicit=1
  shift
fi
name=""
subdir=""
force=0
extra=()
while [ $# -gt 0 ]; do
  case "$1" in
    --repo)
      needs_value "$@"
      [ "$explicit" -eq 0 ] || refuse_second
      kind="repo"; location="$2"; explicit=1; shift 2 ;;
    --path)
      needs_value "$@"
      [ "$explicit" -eq 0 ] || refuse_second
      kind="path"; location="$2"; explicit=1; shift 2 ;;
    --zip)
      needs_value "$@"
      [ "$explicit" -eq 0 ] || refuse_second
      kind="zip"; location="$2"; explicit=1; shift 2 ;;
    --name) needs_value "$@"; name="$2"; shift 2 ;;
    --subdir) needs_value "$@"; subdir="$2"; shift 2 ;;
    --force) force=1; shift ;;
    -h | --help) usage ;;
    --) shift; extra=("$@"); break ;;
    *) extra+=("$1"); shift ;;
  esac
done

if [ -z "$location" ]; then
  if [ -n "$(find "$TMP_ARCHIVES" -maxdepth 1 -type f -iname '*.zip' 2>/dev/null | head -1)" ]; then
    echo "Archives in examples/tmp/ (a bare name is looked up here):" >&2
    list_archives
  fi
  usage
fi

# The script owns the destination and the overwrite decision — that is what its guard over
# a hand-written blueprint is made of — so neither may come through the pass-through and
# replace one silently.
for arg in ${extra[@]+"${extra[@]}"}; do
  case "$arg" in
    --out | --overwrite)
      echo "$arg after -- is the script's decision, not the CLI's. Use --force to replace" >&2
      echo "a blueprint, or --name to write a different one." >&2
      exit 2 ;;
  esac
done

# A local source is resolved here because the JVM resolves it against the repository root,
# not against examples/, and a source that is not there should cost nothing to discover.
if [ "$kind" = "zip" ]; then
  location="$(resolve_archive "$location")"
elif [ "$kind" = "path" ]; then
  [ -e "$location" ] || { echo "No such directory: $location" >&2; exit 2; }
  location="$(realpath "$location")"
fi

cli_args=()
case "$kind" in
  path) cli_args+=("--path" "$location") ;;
  zip) cli_args+=("--zip" "$location") ;;
  *) cli_args+=("--repo" "$location") ;;
esac
[ -z "$subdir" ] || cli_args+=("--subdir" "$subdir")
overwrite=()
[ "$force" -eq 1 ] && overwrite=(--overwrite)

[ -n "$name" ] || name="$(basename "${location%/}" | sed 's/\.git$//; s/\.zip$//')"
if [ -z "$name" ]; then
  echo "Cannot derive a blueprint name from '$location' — pass --name." >&2
  exit 2
fi

bp_dir="$SMITH_ROOT/examples/blueprints/$name"
if [ -d "$bp_dir" ] && [ -n "$(ls -A "$bp_dir" 2>/dev/null)" ] && [ "$force" -eq 0 ]; then
  echo "$bp_dir already holds a blueprint. Pass --force to replace it, or --name" >&2
  echo "a different one. The blueprints under examples/ are hand-written documents, so" >&2
  echo "this script does not overwrite them by default." >&2
  exit 2
fi

smith_load_endpoint_env

out_dir="$SMITH_ROOT/examples/out/$name/blueprint"
mkdir -p "$out_dir"

echo "codebase  : $location"
echo "blueprint : $bp_dir"
echo "output    : $out_dir"
echo "endpoint  : $OPENAI_CUSTOM_BASE_URL / model ${EMBABEL_MODELS_DEFAULT_LLM:-default-llm}"
echo "cost      : ONE full-context call (~200K chars in, seven documents out). Minutes, not"
echo "            seconds, on a local endpoint."
echo

# The corpus is assembled inside the builder, so the clone is the JVM's temp directory and
# this script never touches the repository. --out is absolute because the JVM resolves it
# against its own working directory, which is the repository root, not examples/.
set +e
run_smith blueprint "${cli_args[@]}" \
  --out "$bp_dir" \
  ${overwrite[@]+"${overwrite[@]}"} \
  ${extra[@]+"${extra[@]}"} \
  >"$out_dir/build.log" 2>&1 &
cli_pid=$!
# smith_progress blocks here until the run exits; `wait` in the current shell (not inside
# $(...), whose subshell cannot see this child) then gives its real exit code.
smith_progress "$out_dir/build.log" "$cli_pid"
exit_code=0
wait "$cli_pid" || exit_code=$?
smith_write_report "$out_dir/build.log" "$out_dir/report.md"
set -e

echo "$exit_code" >"$out_dir/exit.txt"
echo
echo "--- run report -------------------------------------------------------"
if [ -s "$out_dir/report.md" ]; then
  cat "$out_dir/report.md"
else
  # The CLI prints its report only when a run reaches a verdict. An empty report means the
  # JVM died before one — a bad endpoint, a clone that would not complete — so say so and
  # show the end of the log instead of an empty screen.
  echo "(no run report — the CLI reached no verdict. Last lines of build.log:)"
  tail -20 "$out_dir/build.log"
fi
echo
if [ -d "$bp_dir" ]; then
  echo "--- documents --------------------------------------------------------"
  ls -1 "$bp_dir"
  echo
fi
if [ "$exit_code" -eq 0 ]; then
  echo "==> blueprint: $bp_dir — next: ./generate-spec.sh $name"
elif [ -s "$out_dir/report.md" ]; then
  echo "==> blocked or failed (exit $exit_code) — report.md names the stage and the reason." >&2
else
  echo "==> no verdict reached (exit $exit_code) — build.log holds the whole stream." >&2
fi
exit "$exit_code"
