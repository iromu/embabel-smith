# DeerFlow — Business Process Documentation

A business-level description of what DeerFlow does, who uses it, and the processes
it follows. Written for a business analyst, product owner, or anyone who wants to
understand the application **without knowing how to program**. Technical
implementation detail (languages, frameworks, data schemas, internal mechanisms) is
deliberately excluded and translated into business concepts.

Every statement is grounded in the codebase and marked:
**Confirmed** (directly observable) · **Assumption** (inferred) ·
**Unresolved** (open question).

## Documents

| Document | What it covers |
|---|---|
| [BUSINESS_OVERVIEW.md](BUSINESS_OVERVIEW.md) | The purpose, what it is and is not, the shape of the work, and the key business concepts. |
| [ACTORS_AND_ROLES.md](ACTORS_AND_ROLES.md) | Administrator, member, company-sign-in colleague, messaging user, script token, the product's own automation, anonymous recipient, external systems — and the permissions each holds. |
| [BUSINESS_PROCESSES.md](BUSINESS_PROCESSES.md) | Eighteen processes across request-driven work, autonomous work, equipping the assistant, keeping context, and running the service. |
| [WORKFLOWS.md](WORKFLOWS.md) | Fourteen step-by-step workflows, with flowcharts and sequence diagrams. |
| [BUSINESS_RULES.md](BUSINESS_RULES.md) | The rules that govern each process, with the exact figures and defaults. |
| [STATES_AND_TRANSITIONS.md](STATES_AND_TRANSITIONS.md) | The lifecycle of a conversation, a turn of work, a completion condition, a work list, delegated work, files, projects and shared snapshots. |
| [EXCEPTIONS_AND_EDGE_CASES.md](EXCEPTIONS_AND_EDGE_CASES.md) | What happens when something fails or is unexpected, and what the person sees. |

## One-line summary

DeerFlow is a self-hosted assistant workbench: a person hands it a whole task
rather than a question, and the assistant plans it, carries it out using real tools
in a workspace of its own, delegates pieces to helpers, stops and asks when it
should, keeps going until a declared outcome is genuinely reached — and when it
stops short, names which limit it hit.

## The four things worth knowing before reading further

1. **It acts, not only answers.** It reads and writes files, runs computations,
   operates a browser and calls connected systems — which is why the product ships
   with an explicit security notice and defaults to being reachable only from its
   own machine.
2. **An administrator account is equivalent to running code on the host.** The
   product says so itself. This is a governance fact, not a caveat.
3. **Completion is judged, not assumed.** A separate non-reasoning check reads what
   is visible after each turn and decides whether the declared outcome was reached;
   a step succeeding is not evidence of it.
4. **Failure is informative by design.** Thirteen named reasons for stopping, a
   named missing decision for blocked unattended work, and a rule that a turn which
   cannot show its promised output is reported as failed.

## Provenance

Compiled by direct analysis of the DeerFlow 2.0 repository at this checkout
(the "super agent harness" rewrite, MIT-licensed), reading its user guides, backend
guides, cross-component contract files and source. Where a published guide and the
code disagree, the code is followed and the disagreement is noted — for example the
helper-work limits, where one guide still says "3 per turn, 15 minutes" while the
code carries a concurrency constant of 3, a per-turn cap of 6, and a 30-minute
built-in allowance.
