# POC 25 — blueprint → Agent Skill generation + with/without-skill eval

Each blueprint is turned into a `SKILL.md`; its `evals.json` suite is then run
against a general-purpose agent **with the skill loaded** and **without it** (the
baseline), graded against verifiable expectations. The skill lift is the
with-skill pass rate minus the no-skill pass rate.

## Blueprint: Aurora

- **Skill:** `aurora` (109 lines of SKILL.md)
- **Description:** Manage end-to-end pharmaceutical cold-chain distribution, inventory allocation, and incident response. Use when processing purchase orders, validating regulatory compliance, allocating FEFO inventory, coordinating warehouse fulfillment, monitoring temperature excursions, handling delivery exceptions, or managing product recalls.
- **Evals:** 4 scenario evals (bundled)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |   100% |    42% |   +58% |
| embabel |    75% |    50% |   +25% |

### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | A customer orders 200 units of a refrigerated vaccine. The … | 3/3 | 2/3 |
| 2 | A purchase order arrives. The customer-authorisation check … | 3/3 | 2/3 |
| 3 | The carrier reports that a shipment has arrived at the hosp… | 3/3 | 1/3 |
| 4 | A critical-priority order is missing required information a… | 3/3 | 0/3 |

### Arm: embabel

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | A customer orders 200 units of a refrigerated vaccine. The … | 2/3 | 2/3 |
| 2 | A purchase order arrives. The customer-authorisation check … | 2/3 | 2/3 |
| 3 | The carrier reports that a shipment has arrived at the hosp… | 3/3 | 2/3 |
| 4 | A critical-priority order is missing required information a… | 2/3 | 0/3 |

## Blueprint: Sentinel

- **Skill:** `sentinel` (114 lines of SKILL.md)
- **Description:** Operate the SENTINEL multi-agent security platform to detect, investigate, assess, contain, and resolve cybersecurity threats. Use this skill when handling security alerts, coordinating agent workflows, managing incident lifecycles, or executing containment actions.
- **Evals:** 3 scenario evals (bundled)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |   100% |    56% |   +44% |
| embabel |   100% |    44% |   +56% |

### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | The orchestrator receives an alert that a single endpoint s… | 3/3 | 2/3 |
| 2 | INTEL finds a suspicious IP address in a threat-intelligenc… | 3/3 | 3/3 |
| 3 | Watchtower raises a high-severity alert that spans both a c… | 3/3 | 0/3 |

### Arm: embabel

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | The orchestrator receives an alert that a single endpoint s… | 3/3 | 1/3 |
| 2 | INTEL finds a suspicious IP address in a threat-intelligenc… | 3/3 | 3/3 |
| 3 | Watchtower raises a high-severity alert that spans both a c… | 3/3 | 0/3 |

## Blueprint: TradingAgents

- **Skill:** `tradingagents` (129 lines of SKILL.md)
- **Description:** Execute a single-instrument investment analysis by simulating a multi-agent trading firm, or settle past decisions to generate learning lessons. Use when analyzing a specific ticker on a given date, running the full analyst-researcher-trader-risk-manager pipeline, or processing pending decision logs.
- **Evals:** 4 scenario evals (bundled)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |    75% |    42% |   +33% |
| embabel |    75% |    33% |   +42% |

### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | You start a single-instrument analysis for ticker NVDA on 2… | 2/3 | 2/3 |
| 2 | A decision was made on 2026-09-01 with the default holding … | 3/3 | 0/3 |
| 3 | In an evaluation sweep over a grid of tickers and dates, do… | 2/3 | 1/3 |
| 4 | A run for AAPL on 2026-08-15 was interrupted. The operator … | 2/3 | 2/3 |

### Arm: embabel

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | You start a single-instrument analysis for ticker NVDA on 2… | 2/3 | 2/3 |
| 2 | A decision was made on 2026-09-01 with the default holding … | 3/3 | 0/3 |
| 3 | In an evaluation sweep over a grid of tickers and dates, do… | 2/3 | 1/3 |
| 4 | A run for AAPL on 2026-08-15 was interrupted. The operator … | 2/3 | 1/3 |
