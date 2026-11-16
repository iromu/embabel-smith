# TradingAgents — Business Process Documentation

A business-level description of what the TradingAgents application does, who uses
it, and the processes it follows. Written for a business analyst, product owner,
or anyone who wants to understand the application **without knowing how to
program**. Technical implementation detail (languages, frameworks, data schemas,
internal mechanisms) is deliberately excluded and translated into business
concepts.

Every statement is grounded in the codebase and marked:
**Confirmed** (directly observable) · **Assumption** (inferred) ·
**Unresolved** (open question).

## Documents

| Document | What it covers |
|---|---|
| [BUSINESS_OVERVIEW.md](BUSINESS_OVERVIEW.md) | The purpose, the simulated firm, what it is *not*, and key concepts. |
| [ACTORS_AND_ROLES.md](ACTORS_AND_ROLES.md) | The operator, the firm's roles, and the external parties. |
| [BUSINESS_PROCESSES.md](BUSINESS_PROCESSES.md) | The four business processes and a supporting setup process. |
| [WORKFLOWS.md](WORKFLOWS.md) | Step-by-step workflows, with flowcharts and sequence diagrams. |
| [BUSINESS_RULES.md](BUSINESS_RULES.md) | The rules that govern each process (ratings, point-in-time, settlement, …). |
| [STATES_AND_TRANSITIONS.md](STATES_AND_TRANSITIONS.md) | The lifecycle of a decision, a run, a sentiment read, and a memory entry. |
| [EXCEPTIONS_AND_EDGE_CASES.md](EXCEPTIONS_AND_EDGE_CASES.md) | What happens when something fails or is unexpected. |

## One-line summary

TradingAgents simulates a small trading firm: a team of AI-powered analysts and
decision-makers reason about one instrument on one date, debate the case, and
issue a five-level investment rating — recording each decision in a persistent log
that later runs learn from, and that can be scored for decision quality over time.
