# The frozen task set

Six tasks, one JSON file each, authored by hand from `../self/self_process_description.md` and the
docs it distils. They ask about embabel-smith's own build process: the pipeline a candidate generated
from that description is supposed to reproduce. This set is **fixed for the run** (see
`../../evidence/MUTABILITY.md`): the loop may not add, remove, or edit a task, and a candidate that
emits proposed tasks is scored without them. These six are the set the POC shipped with, and they are
still the set the launcher scores against: the four task files task 6.1 authored live in
[`../tasks-headroom/`](../tasks-headroom/) instead of here, and the last section says why that choice
was made rather than the obvious one.

## Format

One object per file, six keys:

| Key | Meaning |
| --- | --- |
| `id` | stable identifier; the file name carries it too |
| `stage` | which stage of the described process the task interrogates |
| `prompt` | the question put to the candidate |
| `require` | phrases that must **all** appear in the answer |
| `anyOf` | at least **one** of these phrases must appear (empty means no condition) |
| `forbid` | none of these phrases may appear (empty means no condition) |
| `check` | what the check is, in prose — the file states its own expectation, so a scorer needs nothing outside it |

A task **passes** when every `require` phrase matches, the `anyOf` condition holds, and no `forbid`
phrase matches. Nothing else is consulted: no model call, no similarity, no partial credit.

**This is the stricter of two scorers, and the loop does not gate on it.** The product's own gate
scores an `EvalTask` by a single expected-substring test, so it can carry `require` but not `anyOf`
or `forbid`. **Every task in this set declares both `anyOf` and `forbid`**, which means every one of
them is narrowed when it reaches that gate — that arm is systematically the looser one, not
occasionally. A score here therefore has two values that are not interchangeable, and
`evidence/baseline.json` names them rather than leaving it implied: `baselineScore` is the product's
substring score and is the primary, which both the gate and the improvement delta read, while
`humanLabelScore` is this set's full verdict, recorded as the known gap and never as a pass mark.
`divergingTaskIds` lists the tasks the human label rejects and the primary accepts on that run.
A task the two arms disagree about is excluded from the deciding number: `excludedTaskIds` names those tasks, and
`decidingTaskIds` and `decidingScore` are what remains and its score — a smaller denominator, not a better result, and
a candidate compared against it has to be scored on the same tasks. The exclusion is derived from the two verdict
lists, never handed in, so a task cannot be dropped for being inconvenient.
Read a bare "2/6" as ambiguous.

## Matching rule

Case-folded. Collapse runs of whitespace to one space and leave hyphens and slashes alone. A phrase
matches when it occurs as a substring delimited at both ends by a non-alphanumeric character or the
end of the string. So `no` matches "No —" but not "not" or "known", and `push` does not match
"pushes". Phrase lengths are chosen so this rule is unambiguous.

Matching is literal, and inflection is therefore part of authoring a task: `never push` does not
match "never pushes", so a phrase set has to cover the forms a correct answer plausibly uses. That
brittleness is the price of a scorer that makes no model call, and it is the reason each task's
`anyOf` carries several forms rather than one.

## The anti-restatement invariant

No `require` or `anyOf` phrase appears in that task's own `prompt`. A candidate cannot pass by
echoing the question back. That invariant is checkable and the scorer should assert it over the set
before scoring anything; it is what keeps the baseline honest.

## Why these six, and where the headroom is

Each task interrogates a decision in the described process that a general model answers from its own
prior rather than from the project's rule, and the prior is usually the opposite: the severity scale
has no `HIGH`; the quality floor is a project constant; refinement touches one named prompt, not the
failing one; publishing commits and does not push. Two tasks (the strict baseline comparison, the
never-push default) are ones the unmodified process is expected to pass, so the set is not a wall of
impossible questions — it is a spread.

| Task | Stage | Expected of the unmodified process |
| --- | --- | --- |
| `b27-t01` | security scan | fail — the common prior is that `HIGH` exists and blocks |
| `b27-t02` | inspect | fail — `70` and the three-draft bound are project defaults, not derivable |
| `b27-t03` | evaluate / refine | fail — the intuitive answer is "rewrite the failing step" |
| `b27-t04` | gate | pass — "equal is not progress" is the likelier guess |
| `b27-t05` | publish | pass — not pushing without being asked is a common default |
| `b27-t06` | build target | fail — three targets is the natural assumption |

That is a 2/6 baseline expectation, deliberately: a baseline pinned near the ceiling gives the strict
comparison nothing to do, which is the failure mode already on record for builder-built packs. Task 2.4
has since run, and `evidence/baseline.json` is the result; this table is the design intent it replaced,
kept because the *disagreement* between them is informative. The aggregate agrees — the human label
scored 2/6 — but by a different route: four of these six per-task predictions held, while `b27-t01`
passed against the expectation that the "HIGH exists" prior would win, and `b27-t05` failed against the
expectation that not-pushing is a common default. And the two arms are not interchangeable: the gate's
substring arm scored 4/6 on the same answers, because it cannot carry the `anyOf` and `forbid` clauses
that decide `t05` and `t06`. Read a figure here as naming its arm.

## The four headroom tasks in `../tasks-headroom/`, and what they are for

Task 6.1's premise is that a task set the seed already answers is not a benchmark. The README records
the inner loop's seed scoring 1.0/1.0 held-out, which is the ceiling failure the design names, and the
outer loop's stop branch exists for the same reason on this side. The four files from `b27-t07` were
therefore authored against rules a general prior gets *wrong*, one per stage, so a candidate has
somewhere to go:

| Task | Stage | Why the prior is wrong |
| --- | --- | --- |
| `b27-t07` | inspect | the `pii` check is advisory by check name, so nothing it reports can block — the instinct is that a personal-data finding blocks whatever raised it |
| `b27-t08` | inspect | `security`, `secrets` and `code-integrity` shell out to third-party binaries and run only under `--full-inspection` — the instinct is that a pipeline which publishes has scanned for secrets |
| `b27-t09` | loop publish | a `git:` source with pushing off is refused, because the commit would sit in a temporary clone that is then deleted — the never-push rule invites the wrong answer that it commits and skips the push |
| `b27-t10` | loop detect | a retired pack is excluded from automatic refinement — the instinct is that the pack with the most failures is the best candidate |

Each one carries an `anyOf` and a `forbid` clause like the six, so the gate's substring arm narrows them
in the same way, and none of them puts its own answer in its own question — the anti-restatement
invariant above is asserted over the ten, not the six, by `baseline/AddedHeadroomTasksTest`, which is
the only thing that loads the two directories together.

**All four are expected-fail, which skews the spread the table above is proud of.** Deliberate for
these four, not for the set: the six already carry the pass half, and the addition exists to buy
headroom. Two of them are evidenced in the labelled corpus rather than floating free — `t-017` and
`h-011` in `../../corpus/traces/` are the retirement cases `b27-t10` asks about — and `b27-t09` is not,
because a trace records runs of a published pack and never a publish that was refused. That limit
belongs to the corpus, not to the task.

**Why they sit beside the frozen set instead of inside it.** Task count is load-bearing, not
descriptive. `Baseline` reads `tasks.ids().size()`, so the denominator, the `2/6` its verdict quotes as
design intent, and `Budget.costPerCandidate(frozenTaskCount)` all move with the file count — and the
budget is the sharp one. Ten tasks declare `5 x (1 + 10) x 2 = 110` calls, which at the 60 s per-call
figure the POC measured is 6600 s against `OuterLoopProcess`'s 5400 s wall-clock ceiling, so
`runPass` refuses **before the first call**: measured directly, `OuterLoopProcessTest` fails with
`OuterLoopRefused`, not with a wrong number. Twenty-three assertions across ten classes were measured
failing with the files in this directory — `TaskSetReadOnlyToLoopTest`, `SpendCeilingTest`,
`BaselineHeadroomTest`, `NoiseFloorTest`, `DivergingTaskExclusionTest`, `FrozenGateVerdictsTest` (three
`NullPointerException`s, from `answersInTaskOrder` having no default for an id it did not expect),
`OuterGenerationRecordTest`, `DistinctAnswerGuardTest`, `NoiseFloorGateTest`, `ArchiveRetainsLosersTest`
— and behind several of them sit `../evidence/baseline.json` and `../evidence/noise-floor.json`,
measured over six prompts, cited as `2 of 6` in `docs/04` and the module README.

So the frozen set keeps its six files and its recorded denominators stay true. Folding the headroom
tasks in is a deliberate later decision with a named price: re-measure both evidence files over ten
prompts, update the six fixtures and the `SpendCeilingTest`/`OuterLoopFixtures` arithmetic, and raise
the wall-clock ceiling past 6600 s or cut the generation count — a guardrail change, which is exactly
why it should be a decision and not a side effect of adding benchmark files.
