# Examples

Business-process blueprints, and the scripts that build an agent spec or a skill from one.

Everything here is **launched by hand, one run at a time**. There is no matrix runner and no
live test that fires the whole set, because a full sweep of the three blueprints across both
targets costs on the order of a hundred model calls and fifty minutes of a local endpoint —
cost you should decide to pay per run, in a terminal you are watching, rather than inherit
from a test phase.

## Layout

| Path | What it is |
| --- | --- |
| `blueprints/<Name>/` | The input: business-process documents a build reads as-is. A new example is a new directory here — hand-written, or made by `generate-blueprint.sh`. |
| `smith-cli.sh` | Shared bootstrap the scripts source — how to actually start the builder CLI. |
| `generate-blueprint.sh <codebase>` | One repository → one blueprint, from a git URL, a repository archive or a checkout. The only script that talks to the code, not to a blueprint. |
| `tmp/` | Repository downloads to build blueprints from, and the names `generate-blueprint.sh` resolves a bare argument against. Only the `*.zip` files are ignored — an unpacked tree here is untracked third-party source, and the builder unpacks to a temp directory of its own, so there is no reason to unpack one here. |
| `generate-spec.sh <blueprint>` | One blueprint → one executable agent spec. |
| `generate-skill.sh <blueprint>` | One blueprint → one Agent Skill. |
| `parity-agentic.sh [blueprint...]` | The hand-run pack-flow measurement over the three inputs the parity record used — `--flow agentic` by default, `--flow harness` with `SMITH_PARITY_FLOW=harness`. Costs model calls by design; nothing runs it automatically. |
| `out/<Name>/` | What a run leaves behind: `report.md`, `exit.txt`, and `published/` when a gate let it through. Regenerated, not authored. |

## The scripts

```bash
cd examples
./generate-blueprint.sh agentic-software-factory               # a download in tmp/: no clone
./generate-blueprint.sh https://github.com/OmegaMc1331/agentic-software-factory.git
./generate-spec.sh Aurora                              # agent spec, single-shot technique
./generate-spec.sh Aurora --technique embabel          # the NL compiler; expected to block
./generate-skill.sh Aurora                             # skill, gated on the with/without lift
```

`generate-blueprint.sh` is the one that starts from a repository instead of a blueprint: it
runs `smith blueprint`, which fetches the codebase, reads its documents and source into one
capped corpus, asks the discovery prompt one question, and writes the seven deliverables and an
index into `blueprints/<name>/`. It refuses to touch a blueprint that already exists — the ones
here are hand-written — so `--force` or `--name` is how you re-run one.

Fetch it three ways: a git URL (shallow clone), `--path` a checkout, or an archive — a forge's
repository download, by path or by name (`--zip`, or a bare argument resolved against `tmp/`
the way a blueprint name is resolved against `blueprints/`; no argument lists what is there). The archive is the one to use while iterating: nothing
is cloned or fetched, and the corpus comes out the same either way (23 files and 199,775 characters
on the repository above, from the clone and from the download). The download's wrapper directory is
lifted, so the paths the model sees are the repository's own; the blueprint takes the archive's name,
ref suffix included, so pass `--name` when that suffix is not what you want.

Each script prints the blueprint it read, the endpoint it is talking to and where the output
goes before it spends anything, and each writes its build report to `out/` whether or not the
build published. Read the header comment of a script before running a batch — `generate-skill.sh`
is roughly fifteen times more expensive per blueprint than it looks.

### What a run leaves, and what the terminal shows

| | Content |
| --- | --- |
| `out/<Name>/<cell>/report.md` | The CLI's report only: the candidate chosen, the eval score, the stage a blocked build stopped at, the issues. ~7 lines. |
| `out/<Name>/<cell>/build.log` | Everything the JVM wrote — startup chatter, every action, every timeout. For post-mortems, not for watching. |
| the terminal | A filtered feed: `executing action <name>` / `executed action <name> in PT1M54S`, failures, and the report. A timeout tally at the end. |

The JVM logs to stdout alongside its report (Spring Boot 4.1's console appender has no stderr
target), so the split between the two files is made by pattern after the run, not by stream.

Worth knowing before a long run: the eval stage **deploys the generated process and runs it**, so
an agent-spec build pays one LLM call per generated action. TradingAgents has 13, and on this
endpoint the first build took **18 minutes** — 11 of them the eval's own GOAP run — and the first
action alone spent 5 minutes inside Embabel's 60 s × 10 retry loop before it answered.
`--max-refine 0` does not shorten that; the cost is the eval, not the refinement.

To go through everything deliberately, and cheaply enough to finish:

```bash
for b in Aurora Sentinel TradingAgents; do
  ./generate-spec.sh "$b" -- --max-refine 0   || true     # || true: a blocked build is data
done
```

`|| true` because a build that stops at a gate exits 1 and still leaves the report that
explains it — that is the evidence, not a failure of the loop. The classpath the scripts need
is resolved on the first run and cached under `/tmp` afterwards, so a sequence pays that once.

### The pack-flow parity measurement

`parity-agentic.sh` is the one script here that exists to produce a number rather than an
artifact. It runs a pack flow over the three inputs the parity record in
`docs/08-builder.md` §"Who issues the generation call" used — `parcel-claims`, `Sentinel`,
`TradingAgents` — under the same timed `java -cp` harness, and prints built count, answered
calls, calls spent without an answer, and tool calls per build against that record's java bar of
2 of 3. `SMITH_PARITY_FLOW` picks the arm: `agentic` (the default, `builder-agentic-blueprint`)
or `harness` (`builder-harness`, which adds the step that decides what to read next and costs
another step's tool loop for it — its header says so). Either way the evidence lands in
`out/<blueprint>/<flow>/`, so the two arms can be compared without one overwriting the other.

It is unrun by default and it is not a test. Nothing in the repo claims a parity number for
either pack flow until someone runs this and writes the result down, in whichever direction it
comes out.

## Why no build script passes `--extract`

`--extract` runs `prompts/business_process_discovery.jinja`, and that prompt asks for a
**codebase**: "Analyse the entire codebase", "Do not describe classes, functions, modules or
files", and it emits the seven discovery deliverables. A blueprint is already one of those
deliverables — `blueprints/TradingAgents/` is a copy of hand-written business documentation.
Extraction over it re-summarises business prose into less detailed business prose and charges
a full-context model call for the privilege.

So the two build scripts read blueprints directly. The script that runs the discovery prompt
is `generate-blueprint.sh`, and it runs it over a repository, which is what the prompt was
written for: `smith blueprint` clones the URL, reads the repository's source and documents
into one capped corpus, and writes the deliverables back out here. `--extract` itself stays a
modifier on `--docs`, and `--docs` reads `.md`/`.markdown`/`.txt` only — so a repository's
documentation can sit behind it, but code cannot.
