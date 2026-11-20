# Design

## Context

The loop that exists today is narrow in a specific, verifiable way. `studio/improve/Refiner` rewrites
the prompts of the steps named in the failure traces and leaves every other step byte-identical.
`studio/improve/Detector` is a pure function of the trace window, the pack content and the sidecar,
with three hard-coded triggers and no model call. `studio/improve/GateChain` takes its `InvokerFactory`
and task list as constructor arguments, but calls `EvalScorer.score(...)` statically inside `reeval` —
`docs/10-self-improvement.md` records that seam as not injectable. `builder/skill/` generates a
`SKILL.md` and an `evals.json` and no scripts; `poc-26` ships the GraalJS engine that runs them and has
no producer upstream. `--target` accepts `agent-spec` and `skill` only, so a multi-agent process exists
as a stored candidate, never as a target (`docs/08-builder.md`).

The constraint that shapes everything below: `prod/` is not modified, and `poc/` is frozen evidence.

## Goals / Non-Goals

**Goals:**

- Establish a mutable/immutable boundary that a self-referential loop can live inside, and show which
  side each named class falls on.
- Measure improvement as a *derivative* across generations, with a noise floor measured before tuning.
- Produce the missing producer for `poc-26`'s script engine: generated JavaScript skill scripts.
- Report the failure modes as results, not as caveats to hide.

**Non-Goals:**

- No generated Java, no self-modifying source, no fine-tuning. The recursion is over artifacts the
  platform interprets.
- No builder HTTP API and no process boundary for the *actor*; only the referee is a separate process.
- No product adoption decision. Nothing here promotes into `prod/` — at most it hands back the one
  seam worth injecting.
- No unattended publishing. The loop's existing defaults (approval queue, `auto-publish` off,
  commit-never-push per `docs/05-architecture.md`) stay exactly as they are and the approval-queue
  yield is reported as a first-class number.

## Decisions

### D1 — The regress stops at the interpreter

```
 MUTABLE -- generated, versioned, gated ------------------------------+
   generator prompts  ·  decomposition  ·  skill scripts (JS)         |
   process shape (N agent specs)  ·  detection policy                 |
   refinement policy  ·  the improver expressed as a pack             |
+---------------------------------------------------------------------+
 IMMUTABLE -- the kernel the claim is relative to ---------------------+
   Embabel AgentPlatform + the no-code runtime                        |
   EvalScorer (statically called inside GateChain.reeval)             |
   the frozen task set  ·  the labelled trace corpus                  |
   the generation archive                                             |
+---------------------------------------------------------------------+
```

"Who improves the improver" is answered structurally, not recursively: a generated policy is *data the
kernel interprets*, so generation N+1's improver is checked by code that generation N could not edit.
This is the Gödel-machine move — fix an interpreter, state the claim relative to it — at a fraction of
the blast radius.

*Alternative considered*: let the improver rewrite its own Java. Rejected. It contradicts the product's
"specs, not agents" premise, and there is no gate in this repository that reviews generated Java before
executing it.

### D2 — The referee is a separate hand-started process

`GateChain`'s two-argument constructor already accepts an injected task list and invoker factory, so a
second gate with a frozen task set needs no prod change. It runs in its own JVM rather than beside the
mutating code, because the mutating code must not hold the scorer's classes on its classpath.

*Alternative considered*: a second `GateChain` instance in-process. Rejected — cheaper, and it makes
"isolation" a convention rather than a classloader fact.
*Also rejected*: the builder growing a REST API so the referee could call it. That is the premise this
POC was pitched with, and it turns out not to be load-bearing: the studio embeds the builder jar and
calls `BuilderService` in-process, and adding an HTTP surface to a port-bound development tool is a
decision this POC does not need. The referee is launched by hand, matching the studio's rule that
sidecars are reported and never started (`docs/09-studio.md`).

### D3 — The improvement policy is a generated JavaScript skill script

`Detector` is already a pure, model-free function, which means a *replacement* for it can be scored
against a labelled trace corpus for zero model calls. So the detection and refinement *decisions*
become generated ECMAScript run through `poc-26`'s `SkillScriptExecutionEngine`, and the inner loop can
run hundreds of generations for free.

*Alternative considered*: a generated prompt for an LLM detector. Kept for the outer loop, rejected for
the inner one — it costs a call per generation and its variance is unknown, so it cannot be the surface
that recurses cheaply.

### D4 — The benchmark is authored by hand, with its baseline and noise floor recorded first

Six-ish tasks with human-written expected outputs, and a frozen trace corpus whose lines carry a human
label for "genuine defect". Both are checked in before any tuning, and the POC records the baseline
score and the repeat-to-repeat variance of the scoring run before touching a prompt. An improvement is
a paired delta that clears that variance, not a strictly greater number.

*Alternative considered*: mine real traces for the task set, reusing `GateChain`'s failure-fix fallback.
Rejected — the improving system would choose which tasks count, which is the exact hazard the design
exists to close.
*Alternative considered*: reuse `examples/blueprints/`. Rejected — those five were never built to have
headroom, and a baseline pinned near the maximum gives a comparison nothing to do, which is the failure
`docs/10-self-improvement.md` already documents for builder-built packs.

### D5 — Two loops, because the outer one is unaffordable at depth

```
 INNER  offline · deterministic · 0 model calls
   frozen labelled corpus -> [generated JS policy] -> precision / recall
                                       |  survivors
                                       v
 OUTER  live · paid · >= 2 generations
   improver gen N -> candidate packs + skills -> frozen GateChain -> archive -> score
```

The inner loop is where the recursion happens; the outer loop tests whether an offline win transfers
into better packs. That transfer is the paper's worth of result, and it is the only thing in the design
that requires paid calls.

### D6 — An archive, not a hill-climb

Every variant keeps its parent pointer and its score. Darwin-Gödel-Machine-style: greedy selection from
one best variant converges early and cannot show whether the *policy* improved or the search got lucky.
The archive also makes the second-cheapest experiment in the design free: archive selection versus
greedy, on identical generations.

### D7 — Agent specs (plural) come from the existing generator, not a new target

`core/ProcessSpecGenerator` and `common/validation/ProcessValidator` are called directly and the pack
folder is written by the POC.

*Alternative considered*: add `--target process` to `BuildTarget`. Rejected — `docs/08-builder.md` states
the current contract (both candidates built, the recommender publishes one) and a POC does not amend a
product contract to save itself a call.

### D8 — The self-description is written, not extracted

Distilled by hand from `docs/05`, `docs/08` and `docs/10`. `smith blueprint --path .` is not used: its
extraction prompt asks for a codebase and a blueprint already *is* that output, so running it over a
description re-summarises prose and charges full context for it (`docs/08-builder.md`).

### D9 — Generated scripts never reach the referee's execution path

Scripts run inside a candidate, never inside the scorer. Enforced by construction rather than intent:
the referee process loads no skill folder and opens no Graal `Context`. This is the one invariant worth
enforcing physically — a candidate that can generate the code that grades it is the Darwin-Gödel-Machine
tampering hazard, and this repository has already lived the softer version of it once.

### D10 — Single-shot generation only

The `embabel` technique scores 0/N across every recorded run and cannot publish a multi-step pack
because the upstream compiler reports no intermediate types (`docs/08-builder.md`). Using it here would
confound a self-improvement result with a known upstream block.

## Risks / Trade-offs

- **The scorer is an LLM grader for part of the suite** → keep at least one deterministic arm, and report
  the two arms separately. A loop optimising a model judge will find the judge's vocabulary.
- **The baseline sits at the ceiling** → measure it before tuning and refuse to run the outer loop if
  there is no headroom; a loop that cannot move reports that as its result rather than inventing a
  softer gate.
- **Generation-to-generation prompt rewriting eats its own gains** (context collapse) → freeze the seed
  prompt, mutate exactly one surface per generation, and keep every historical variant in the archive so
  a collapsed generation is visible as a regression rather than as the new normal.
- **Run-to-run variance swamps the effect** → paired repeats, a pre-recorded noise floor, and the
  derivative test; a delta inside the floor is reported as "no improvement", not as a small improvement.
  **Part of this defence did not survive contact with the endpoint:** the measured floor is 0.0 across
  three repeats with no task flipping, so on this setup the inside-the-floor branch cannot fire and the
  protection reduces to the paired repeats and the derivative test. That is a property of a
  near-deterministic local model, not a reason to raise the threshold.
- **The detector overfits the labelled corpus** → hold out a labelled split, report precision and recall
  separately, and expect a high-recall low-precision policy (a loop prefers to believe failures exist).
- **The improver makes easy candidates instead of better ones** → the task set is frozen and a candidate
  cannot add tasks; the only lever is the work.
- **Cost overruns** → budget is expressed in model calls and generations before the first run, with a
  stated halt condition; the inner loop is the pressure valve because it is free.
- **The honest negative outcome is a real outcome.** If the derivative is flat, the finding is that a
  single round of self-reference buys one hill-climb wearing the word "recursive" as decoration. That is
  publishable and it is the modal result in this literature.

## Migration Plan

Not a deployment. Run order: author the frozen benchmark and corpus, record baseline and noise floor,
run the inner loop to convergence, then spend the outer loop's generations. Rollback is deleting
`poc/poc-27-recursive-self-improvement/` and the two lines in `poc/pom.xml`; `prod/` is untouched
throughout, so nothing else can be affected.

## Open Questions

- How many tasks the frozen set needs, decided by the measured headroom rather than in advance.
  **Half answered:** 2.4 measured headroom as true on six tasks — two of six fail the gate arm — so the
  set is not what is constraining the loop. Six is enough to leave room; whether it is enough to
  *distinguish* candidates is a question the six-task set cannot answer on its own, since one flipped
  task moves the score by 0.167.
- Whether the outer loop buys two generations or three, decided once the noise floor and per-generation
  endpoint time are known. **One of the two is now known, and it argues for the longer run, not the
  shorter one:** the measured floor is 0.0, so there is no variance for an extra generation to have to
  overcome — the only reason to stop early was that a later generation's gain might not clear the floor.
  Per-generation endpoint time is the remaining half, and 10.2's run is what measures it.
