# The frozen task set

Six tasks, one JSON file each, authored by hand from `../self/self_process_description.md` and the
docs it distils. They ask about embabel-smith's own build process: the pipeline a candidate generated
from that description is supposed to reproduce. This set is **fixed for the run** (see
`../../evidence/MUTABILITY.md`): the loop may not add, remove, or edit a task, and a candidate that
emits proposed tasks is scored without them.

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
`--target` arity) are ones the unmodified process is expected to pass, so the set is not a wall of
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
comparison nothing to do, which is the failure mode already on record for builder-built packs. The
measured baseline replaces this table when task 2.4 runs; this table is the design intent, not a
result.
