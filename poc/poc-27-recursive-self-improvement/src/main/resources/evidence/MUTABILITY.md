# What a generation may change, and what it may not

This file is the single source for the mutable/fixed boundary. Later classes of this POC cite it
rather than restating it, and a run may not start without it. An attempt to mutate anything on the
fixed side stops the run, names the artifact, and records no score for that generation.

The boundary is the one the change's design records as **D1 — The regress stops at the interpreter**:

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

"Who improves the improver" is answered structurally, not recursively: a generated policy is data the
kernel interprets, so generation N+1's improver is checked by code that generation N could not edit.

## Mutable — a generation may change these

Each is generated, versioned as a new variant, and gated before it counts.

| Name | What it is in this POC |
| --- | --- |
| `generator-prompts` | the prompts that produce a candidate's agent specs and skill drafts |
| `decomposition` | how the self-description is cut into steps and skills |
| `skill-scripts` | the generated JavaScript under a candidate skill's `scripts/`, run through POC 26's engine |
| `process-shape` | the multi-agent shape: how many agent specs, which hand-offs |
| `detection-policy` | the generated script that decides which recorded failures count as defects |
| `refinement-policy` | the generated policy that decides what to rewrite and from which evidence |
| `improver-pack` | the improver itself, expressed as a pack the runtime executes — the surface that makes the loop self-referential |

One mutation per generation, against a frozen seed prompt. Context collapse is a named risk: a
generation that rewrites everything cannot be attributed to anything.

## Fixed — a generation may not touch these

| Name | Why it is fixed |
| --- | --- |
| `AgentPlatform` | The Embabel platform and the no-code runtime are the interpreter the claim is relative to. Fix it and "improved" stops meaning anything; there is also no gate in this repository that reviews generated Java before executing it. |
| `EvalScorer` | Called statically inside `GateChain.reeval`, and `docs/10-self-improvement.md` records that seam as not injectable. For this POC that limitation is the referee's tenure, not a defect to fix: an improver that could score itself optimises its scorer, not its work. |
| `GateChain` | Prod code, and `prod/` is not modified by this POC. It is *configured* — its two-argument constructor takes the frozen task list and an invoker factory — which is construction with fixed arguments, not mutation of it. |
| `benchmark-task-set` | `src/main/resources/benchmark/tasks/`. Authored by hand and checked in before any tuning; a candidate that could add a task could choose which work counts, which is the exact hazard this design exists to close. |
| `trace-corpus` | `src/main/resources/corpus/traces/`, including which lines are the held-out split. The detector's judge, so it cannot be the detector's output. |
| `generation-archive` | Every variant, its parent and its score. The archive is what makes archive-versus-greedy comparable; an improver that could edit history could manufacture the derivative. |
| `self-description` | `src/main/resources/self/self_process_description.md`. The seed, written by hand from `docs/05`, `docs/08` and `docs/10` — not extracted, because `smith blueprint` asks for a codebase and a blueprint already is its output. |
| `noise-floor` | `src/main/resources/evidence/noise-floor.json`, measured by repeated scoring before any prompt was tuned. An improvement threshold the loop could move is not a threshold. |
| `script-engine-limits` | POC 26's hardening: no host class lookup, no IO, no thread or process creation, no environment, plus its wall-clock and statement limits. A policy that could widen its own sandbox is not sandboxed. |
| `referee-process` | The separate, hand-started JVM that scores. It loads no skill folder and opens no Graal `Context`, so generated code never runs where it is graded. |
| `human-control` | Approval queue holds, auto-publish off, commit-never-push. Not a knob a generation can turn, and the held-candidate count is a reported number, not a target. |

## What counts as a mutation

Writing, editing, deleting, or renaming a fixed artifact — including writing it somewhere else and
reading it back. Constructing a fixed class with arguments a generation chose is configuration and is
allowed; that is how the referee is built. Generating a script that *runs* under a fixed engine is
allowed; that is how a policy is expressed.

Generated scripts never reach the referee's execution path. That invariant is enforced by
construction, not intent: the referee process loads no skill folder and opens no script context.
