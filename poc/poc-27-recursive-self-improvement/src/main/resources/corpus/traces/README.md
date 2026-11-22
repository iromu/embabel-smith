# The frozen labelled trace corpus

Authored evidence for the offline inner loop: a generated detection policy is scored against these
lines for zero model calls, and the label is the answer. This corpus is **fixed for the run** (see
`../../../evidence/MUTABILITY.md`); the loop may not add, remove, or relabel a line.

## The two splits

| File | Split | Lines | Labelled `defect` | Purpose |
| --- | --- | --- | --- | --- |
| `tuning.jsonl` | tuning | 17 | 8 | what a generated policy is fitted against |
| `heldout.jsonl` | held-out | 12 | 6 | what it is judged on; never fitted against |

Lines after `t-016` and `h-010` were added by task 6.1 to put corpus behind the task files
`../../benchmark/tasks/` gained from `b27-t07` onward; the three of them are described in the traps
below. They are appended, not interleaved, so the ids the earlier pages cite still name the same
lines.

**Disjointness.** The split is per file, and every `runId` carries its split as a prefix — `t-` for
tuning, `h-` for held-out — so the two sets cannot share a record and a scorer can assert it by
reading the ids: no id in one file appears in the other, and no id violates its file's prefix. The
held-out split also uses sources, packs and timestamps the tuning split does not, so it is a
different stretch of traffic rather than the same traffic re-cut.

Precision and recall are reported separately, never blended: a loop that wants to believe failures
exist buys recall with precision, and a single score would hide that trade.

## Line format

One JSON object per line. The record fields are the ones `JsonlTraceStore` actually writes, so the
corpus is shaped like real traffic:

| Field | Where it comes from |
| --- | --- |
| `runId`, `timestamp`, `source`, `pack`, `goal`, `input`, `planner`, `trustTier`, `steps`, `success`, `error`, `outputExcerpt` | `smith-common/src/main/java/com/embabel/smith/common/trace/TraceRecord.java:36` |
| `source.uri`, `source.ref`, `source.resolvedAt` | `TraceRecord.java:39` — `ref`/`resolvedAt` are null for a `folder:` source |
| `pack.name`, `pack.contentHash` | `TraceRecord.java:42` — the whole `pack` is null when no pack exports the goal |
| `steps[].name`, `.status`, `.durationMs`, `.outputExcerpt` | `TraceRecord.java:54` |
| `trustTier` as `standard` / `high` | `TraceRecorder.java:127` (the enum name, lower-cased) |
| `steps: []` on a run that never executed | `TraceRecorder.java:134` — tier block, lifecycle block, goal not found |
| only the **last** step carries `status: "failed"` | `TraceRecorder.java:150` |
| nulls written as JSON `null` | `TraceRecordJson` uses a default `ObjectMapper`; asserted in `TraceRecordJsonTest` |

Three fields are added by this corpus and exist nowhere in the product:

| Field | Meaning |
| --- | --- |
| `label` | `defect` or `not_defect` — the human answer a policy is scored against |
| `label_reason` | why, from a fixed set (below) |
| `spec_context` | present only on the lines whose trigger is a property of the pack rather than of the run — the two drift reasons and the re-eval-equal case below: the sidecar's recorded content hash and baseline pass-rate, and the re-evaluated pass-rate where one was recorded |

**Those extra fields make a line unreadable by the product's own codec.** `TraceRecordJson` uses a
default `ObjectMapper`, which fails on unknown properties, so `JsonlTraceStore.read()` would skip
every line here as corrupt. That is fine and intended — the corpus is POC evidence, not a live trace
file — but the inner loop must read it with its own reader configured to ignore unknown properties
(or read the labels separately). Do not point `--trace-dir` at this folder.

### `label_reason` values

| Reason | `label` | The rule it follows |
| --- | --- | --- |
| `failed_step` | defect | a failed run that is not a policy block — `Detector.failedNonPolicyBlock` |
| `goal_not_found` | defect | a failure with neither block prefix, so the product queues it too |
| `content_drift` | defect | the pack's content hash differs from the sidecar's — `Detector.hasDrift` |
| `below_baseline` | defect | a re-eval below the recorded baseline — `Detector.belowBaseline` |
| `reeval_equal` | not_defect | a re-eval that *equals* the baseline — `Detector.belowBaseline` compares with a strict `<`, so equal is not a regression, exactly as equal is not progress at the gate |
| `tier_block` | not_defect | `error` starts with `Run blocked by trust tier` — `Detector.java:41`, and the message shape is `TrustTierBlockedException.java:36` |
| `lifecycle_block` | not_defect | `error` starts with `Run blocked by lifecycle state` — `Detector.java:48`, message shape `LifecycleBlockedException.java:31` |
| `success` | not_defect | the goal was achieved |
| `success_slow` | not_defect | achieved, with one step an order of magnitude slower — latency is not a defect, and a policy that reads `durationMs` should earn nothing from it |

## Deliberate traps in the set

- `t-007` and `h-006` failed for a reason whose message *mentions* the trust tier but does not start
  with the block prefix. The product matches `startsWith`, so both are defects. A keyword policy
  calling either a policy block loses recall; a policy that calls them defects is right for the same
  reason the product is.
- `t-002`, `t-012`, `h-002`, `h-009` are tier blocks: failed, `steps: []`, no planner, and the pack
  present — the run was blocked before it planned anything.
- `t-006` and `h-005` are lifecycle blocks: the retired pack was never ingested, so `pack` is null
  and only the message prefix says why. A policy that keys on a null pack conflates a retirement with
  a typo.
- `t-009` is a goal-not-found failure with `pack: null` — the same null pack as the retirement above,
  and here it *is* a defect.
- `t-004`, `t-016`, `h-010` are successes whose pack content hash no longer matches the sidecar.
- `t-005`, `h-004` are successes whose re-evaluated pass-rate is below the sidecar's baseline.
- `t-017` is a lifecycle block that does not look like the two above: the retirement is recorded in
  the sidecar while the pack's folder is still on disk, so goal resolution finds the pack and `pack`
  is populated, and the block still happens before planning — `steps: []`, no planner. The same
  message prefix, the opposite null-pack shape.
- `h-011` is `t-007`'s trap in lifecycle clothing: a failed step whose message *mentions* the pack's
  lifecycle state without beginning with the block prefix. It is a defect, and only prefix matching
  says so — a policy that greps the message for "lifecycle" calls it a block and loses recall on it.
  `t-017` and `h-011` together are the corpus behind task `b27-t10`: one retirement enforced from the
  sidecar, one failure that only looks like retirement.
- `h-012` is a success whose re-eval *equals* the baseline — the strict `<` above, tested from the
  side a policy fitted only on `below_baseline` lines would never reach.

## Where this corpus is a simplification

The product's detector is not a per-trace function. `Detector.detect` takes the whole trace window
**plus** the pack's current content and its lifecycle sidecar, and two of its three triggers —
content drift and below-baseline — are properties of a *pack*, not of a run: nothing on a trace line
says the pack drifted, and nothing on a trace line carries a pass-rate. This corpus encodes those two
triggers as `spec_context` on a representative line so that a per-line label is well defined and the
inner loop stays a confusion matrix. A policy that ignores `spec_context` cannot get those cases
right, and a policy that keys on them is doing what `Detector` does. The simplification is stated here
rather than hidden, because it is the difference between scoring the product's detector and scoring a
caricature of it.
