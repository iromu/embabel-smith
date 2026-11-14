# POC 25 — blueprint → Agent Skill generation + with/without-skill eval

Each blueprint is first DECOMPOSED into the set of skills required to compartmentalize
the process (the extraction step); each skill is generated into its own `SKILL.md`, and its
per-skill `evals.json` suite is then run against a general-purpose agent **with the skill
loaded** and **without it** (the baseline), graded against verifiable expectations. The
skill lift is the with-skill pass rate minus the no-skill pass rate, per skill.

## Blueprint: Aurora

Decomposed into 9 skill(s): order-intake-and-validation, inventory-allocation-and-replenishment, warehouse-fulfilment-and-dispatch, logistics-and-transportation-management, shipment-monitoring-and-quality-assurance, returns-and-recall-management, incident-management-and-coordination, billing-and-financial-reconciliation, notifications-and-communication.

### Skill: order-intake-and-validation

- **Description:** Handles the initial reception, data extraction, priority assignment, and multi-stage validation (regulatory and commercial) of incoming purchase orders from portal, EDI, or sales reps. Use this skill when processing new orders, managing missing information loops, or resolving validation holds.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |    88% |    81% |    +6% |

#### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | A hospital customer submits a purchase order via the portal… | 3/4 | 3/4 |
| 2 | A standard priority order is received from a new customer '… | 4/4 | 3/4 |
| 3 | An order for 'Insulin Pen Refills' is received. The custome… | 4/4 | 4/4 |
| 4 | A customer submits an order for 'Controlled Substance Y' (S… | 3/4 | 3/4 |

### Skill: inventory-allocation-and-replenishment

- **Description:** Manages stock availability determination, reservation lifecycle, and procurement replenishment. Use when checking inventory for orders, handling allocation scenarios (full, partial, distributed, none), managing reservations, triggering replenishment, or assessing expiry risks.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |   100% |    63% |   +38% |

#### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | A customer orders 50 units of Product X (SKU: PX-100). DC-A… | 4/4 | 4/4 |
| 2 | A Critical Priority order for 100 units of Medical Device Y… | 4/4 | 2/4 |
| 3 | A standard order for 200 units of Chemical Z (SKU: CZ-300) … | 4/4 | 3/4 |
| 4 | A reservation for 10 units of Product W (SKU: PW-400) was m… | 4/4 | 1/4 |

### Skill: warehouse-fulfilment-and-dispatch

- **Description:** Executes the end-to-end warehouse fulfilment process, including picking, independent verification, packaging configuration, documentation, quality release, and dispatch handover. Use this skill when preparing physical shipments for release, handling picking exceptions, or managing quality holds and dispatch gates.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |    87% |    73% |   +13% |

#### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | An operator attempts to pick Batch B-992 of Product X (Expi… | 4/4 | 3/4 |
| 2 | Operator John Doe completes the picking of Order #5001. He … | 2/3 | 2/3 |
| 3 | Order #7788 contains 50 units of 'FreshYogurt' (Temp range:… | 4/4 | 4/4 |
| 4 | Order #9001 has passed Quality Release Check and is marked … | 3/4 | 2/4 |

### Skill: logistics-and-transportation-management

- **Description:** Manages end-to-end logistics operations including carrier selection, booking, customs clearance, route deviation handling, and delay escalation. Use this skill when handling transportation provider selection, international shipment compliance, logistics incidents, or supply chain disruptions.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |   100% |    69% |   +31% |

#### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | We need to ship 500 units of 'VaxGuard' (a temperature-sens… | 4/4 | 4/4 |
| 2 | A Critical priority shipment of emergency cardiac stents is… | 4/4 | 0/4 |
| 3 | An international shipment of 'NeuroPax' (a controlled subst… | 4/4 | 4/4 |
| 4 | A shipment of clinical trial drugs is delayed by 48 hours d… | 4/4 | 3/4 |

### Skill: shipment-monitoring-and-quality-assurance

- **Description:** Manages real-time shipment monitoring, temperature excursion investigations, quality dispositions, and delivery receipt verification. Use when handling shipment tracking alerts, investigating environmental deviations, making release/quarantine decisions, or validating physical delivery conditions.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |   100% |    68% |   +32% |

#### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | A shipment of temperature-sensitive biologics (SKU: BIO-992… | 6/6 | 2/6 |
| 2 | During a routine monitoring check, a shipment of vaccines (… | 6/6 | 6/6 |
| 3 | A shipment of sterile injectables (SKU: INJ-44) arrives at … | 7/7 | 4/7 |
| 4 | A shipment of electronics (SKU: ELEC-22) is marked 'Deliver… | 6/6 | 5/6 |

### Skill: returns-and-recall-management

- **Description:** Manages reverse logistics for customer returns and regulatory recalls, including return authorization, quarantine assessment, recall containment, and quantity reconciliation. Use when handling return requests, processing returned inventory, or executing recall procedures.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |    72% |    67% |    +6% |

#### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | A customer requests a return for Product SKU-992 (a control… | 2/4 | 2/4 |
| 2 | A batch of returned goods (Batch ID: RET-2023-004) has arri… | 4/4 | 4/4 |
| 3 | A regulatory recall notice is issued for Batch ID: BATCH-XY… | 5/5 | 4/5 |
| 4 | The Compliance Team is attempting to close Recall ID: REC-2… | 2/5 | 2/5 |

### Skill: incident-management-and-coordination

- **Description:** Manages the central incident register, severity classification, and cross-departmental coordination for critical operational failures. Use this skill when registering incidents (e.g., temperature excursions, recalls, cyber issues), classifying severity (P1-P4), enforcing escalation timers, coordinating multi-department responses, or verifying closure integrity.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |   100% |   100% |    +0% |

#### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | A temperature excursion alert is received at 02:00 AM for a… | 4/4 | 4/4 |
| 2 | An incident is registered regarding a missing shipment of c… | 4/4 | 4/4 |
| 3 | A cyber/data integrity incident affects both the Quality As… | 4/4 | 4/4 |
| 4 | A P2 incident involving a significant production disruption… | 4/4 | 4/4 |

### Skill: billing-and-financial-reconciliation

- **Description:** Manages invoice generation, payment monitoring, and financial closure for orders. Use when handling billing triggers, reconciling delivered vs. ordered quantities, applying financial rules, managing payment workflows, or finalizing order closure.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|

Arm errors:
  ! raw: Failed to obtain the grading after 3 attempts: Unexpected end-of-input: expected close marker for Array (start marker at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1, column: 18])
 at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1, column: 1116]

### Skill: notifications-and-communication

- **Description:** Generates, routes, tracks, and logs system notifications for internal and external stakeholders based on specific business triggers. Use this skill when handling order status changes, regulatory/credit holds, stock issues, dispatch/delivery events, temperature alerts, recalls, returns, or incident escalations, ensuring strict data segregation and acknowledgement tracking.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |    76% |    65% |   +12% |

#### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | A customer with Order #ORD-9921 (High-Value Electronics) ha… | 4/4 | 3/4 |
| 2 | A critical temperature alert was triggered for Shipment #SH… | 3/4 | 3/4 |
| 3 | A regulatory hold has been placed on Batch #BATCH-778 due t… | 4/5 | 4/5 |
| 4 | A customer submits an information request asking for the 'r… | 2/4 | 1/4 |

## Blueprint: Sentinel

Decomposed into 8 skill(s): threat-detection-and-triage, threat-investigation-and-attribution, threat-intelligence-enrichment, risk-assessment-and-prioritization, incident-response-and-containment, digital-forensics-and-evidence-management, regulatory-compliance-and-governance, orchestration-and-coordination.

### Skill: threat-detection-and-triage

- **Description:** Ingests, normalizes, and classifies security events from network, endpoint, identity, cloud, and email sources to determine severity and routing. Use this skill when processing raw security telemetry, triaging initial alerts, correlating related events into cases, or requesting initial threat enrichment.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |    88% |    88% |    +0% |

#### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | An event is ingested from the endpoint agent: 'Suspicious P… | 2/4 | 3/4 |
| 2 | An email security gateway event is ingested: 'Phishing URL … | 4/4 | 4/4 |
| 3 | Two events are ingested within 10 minutes: 1) 'Failed login… | 4/4 | 4/4 |
| 4 | An event is ingested from the cloud audit log: 'Public S3 b… | 4/4 | 3/4 |

### Skill: threat-investigation-and-attribution

- **Description:** Executes deep-dive analysis for confirmed or suspected security threats by coordinating INTEL, FORENSICS, and SENTINEL-RISK inputs to determine root cause, affected assets, and final verdicts (Confirmed/Likely Compromise, False Positive, Inconclusive). Use when initiating or managing the investigation phase of a security incident, building attack timelines, or finalizing threat attribution.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |   100% |    90% |   +10% |

#### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | An alert triggered for suspicious PowerShell execution on F… | 5/5 | 5/5 |
| 2 | Investigation into a suspected ransomware attack on HR-DB-0… | 5/5 | 4/5 |
| 3 | A 'False Positive' alert was generated for a legitimate bac… | 5/5 | 4/5 |
| 4 | Investigation into a suspected insider threat on Dev-Workst… | 5/5 | 5/5 |

### Skill: threat-intelligence-enrichment

- **Description:** Enriches security indicators with external threat intelligence context, validates IP/domain reputation, identifies known malware/vulnerabilities, and assesses emerging campaigns. Use when analyzing suspicious indicators, verifying threat reports, or providing new indicators to WATCHTOWER for detection rule improvements.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |    88% |    94% |    -6% |

#### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | An analyst submits an IP address (192.0.2.15) and a domain … | 3/4 | 4/4 |
| 2 | The INVESTIGATOR team confirms that a specific malware hash… | 4/4 | 4/4 |
| 3 | A user requests enrichment for a domain (update-software.co… | 4/4 | 3/4 |
| 4 | An emerging campaign is reported by a single, low-reputatio… | 3/4 | 4/4 |

### Skill: risk-assessment-and-prioritization

- **Description:** Evaluates business impact and incident severity to recommend P1-P4 priority levels. Use when assessing affected services, data sensitivity, financial exposure, or customer impact to determine incident priority and escalation needs.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|

Arm errors:
  ! raw: LLM call failed: request timed out

### Skill: incident-response-and-containment

- **Description:** Executes technical containment, eradication, and recovery of security incidents. Use when handling active threats requiring endpoint isolation, credential revocation, or indicator blocking, and when coordinating evidence preservation with forensics before destructive actions.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|

Arm errors:
  ! raw: LLM call failed: request timed out

### Skill: digital-forensics-and-evidence-management

- **Description:** Preserves, collects, and analyzes digital evidence including logs, files, and processes to detect persistence, lateral movement, and exfiltration. Use when tasked with forensic acquisition, chain of custody maintenance, or generating forensic reports for investigators and compliance, ensuring evidence integrity without making containment or severity decisions.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |   100% |    80% |   +20% |

#### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | You are tasked with collecting volatile memory from a compr… | 5/5 | 4/5 |
| 2 | You have acquired a disk image from a suspect's laptop. Dur… | 5/5 | 4/5 |
| 3 | Analyze the provided Windows Event Logs (System and Securit… | 5/5 | 4/5 |
| 4 | You are analyzing a Linux server (LNX-WEB-02) for persisten… | 5/5 | 4/5 |

### Skill: regulatory-compliance-and-governance

- **Description:** Assesses legal and regulatory obligations for security incidents, including data exposure evaluation, reporting deadlines, and compliance validation. Use when determining if an incident triggers mandatory notifications, preparing regulatory reports, or validating security operations against legal standards.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |    75% |    75% |    +0% |

#### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | A security incident has been confirmed involving the unauth… | 5/5 | 4/5 |
| 2 | During a routine audit, it was discovered that a legacy ser… | 4/5 | 4/5 |
| 3 | A security operations team has contained a ransomware attac… | 4/5 | 5/5 |
| 4 | An employee accidentally sent an email containing a spreads… | 2/5 | 2/5 |

### Skill: orchestration-and-coordination

- **Description:** Orchestrates the security operation by receiving alerts, creating cases, assigning tasks to specialist agents, managing dependencies, resolving assessment conflicts, and handling agent failures or timeouts. Use this skill when coordinating multi-agent security workflows, tracking incident state, or resolving disagreements between specialist assessments.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |    90% |   100% |   -10% |

#### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | You receive two conflicting alerts for the same host (SRV-D… | 3/5 | 5/5 |
| 2 | A critical incident (INC-2023-001) requires a firewall rule… | 5/5 | 5/5 |
| 3 | During a complex incident involving a data exfiltration att… | 5/5 | 5/5 |
| 4 | An agent reports that a containment action was successful, … | 5/5 | 5/5 |

## Blueprint: TradingAgents

Decomposed into 16 skill(s): run-configuration-and-validation, instrument-identity-resolution, market-technical-analysis, sentiment-analysis, news-macro-analysis, fundamentals-analysis, research-debate-facilitation, research-manager-verdict, trader-proposal-generation, risk-debate-facilitation, portfolio-manager-decision, decision-recording-and-reporting, decision-settlement, memory-lesson-retrieval, run-resumption-management, evaluation-sweep-orchestration.

### Skill: run-configuration-and-validation

- **Description:** Configures and validates the setup phase for a single-instrument analysis run. Use this skill when initializing a new analysis task or resuming an interrupted one, ensuring all parameters (ticker, date, analysts, models) are valid, credentials are present, and run identity matches any existing snapshot.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |   100% |    65% |   +35% |

#### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | Configure a run for ticker 'BTC-USD' on date '2023-10-25' w… | 4/4 | 1/4 |
| 2 | Configure a run for ticker 'AAPL' on date '2024-12-31' (tod… | 4/4 | 2/4 |
| 3 | Resume a run for ticker 'MSFT' on date '2024-05-19' with an… | 5/5 | 4/5 |
| 4 | Configure a run for ticker '../../etc/passwd' on date '2024… | 4/4 | 4/4 |

### Skill: instrument-identity-resolution

- **Description:** Deterministically resolves an instrument's true identity (name, sector, industry, market) before analytical roles execute, handling point-in-time constraints and fail-open scenarios to prevent misidentification.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |    81% |    88% |    -6% |

#### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | Resolve the identity for ticker 'TSLA' for a backtest run d… | 3/4 | 3/4 |
| 2 | Resolve the identity for ticker 'FB' for a current-day anal… | 4/4 | 4/4 |
| 3 | Resolve the identity for ticker 'XYZ' which does not exist … | 4/4 | 4/4 |
| 4 | Resolve the identity for ticker 'AAPL' for a run dated '201… | 2/4 | 3/4 |

### Skill: market-technical-analysis

- **Description:** Generates detailed technical market analysis reports by retrieving verified OHLCV data and technical indicators from configured providers, enforcing point-in-time integrity, and handling data source failures via fallback chains. Use this skill when asked to perform technical analysis, generate price/indicator reports, or retrieve historical market data for a specific date.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |    88% |    81% |    +6% |

#### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | Generate a technical analysis report for Apple (AAPL) as of… | 4/4 | 4/4 |
| 2 | Analyze the technical setup for Tesla (TSLA) on 2024-01-15.… | 3/4 | 3/4 |
| 3 | Create a technical report for Microsoft (MSFT) as of 2023-1… | 4/4 | 4/4 |
| 4 | Retrieve technical indicators for Amazon (AMZN) for the ana… | 3/4 | 2/4 |

### Skill: sentiment-analysis

- **Description:** Generates structured sentiment analysis for financial assets by retrieving news and social chatter, scoring sentiment on a six-level band and 0-10 scale, and handling data availability constraints. Use when asked to analyze market sentiment, produce a sentiment read, or evaluate retail chatter and news for a specific ticker.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|

Arm errors:
  ! raw: Failed to obtain the grading after 3 attempts: Unexpected end-of-input: expected close marker for Array (start marker at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1, column: 18])
 at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1, column: 929]

### Skill: news-macro-analysis

- **Description:** Generates a point-in-time news and macroeconomic analysis report for trading by retrieving company news, global headlines, FRED macro indicators, and Polymarket probabilities, strictly clamped to the analysis date.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |    80% |    40% |   +40% |

#### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | Generate a market analysis report for Apple (AAPL) as of 20… | 3/5 | 3/5 |
| 2 | Produce a global macro report for 2024-01-15. The news retr… | 3/3 | 0/3 |
| 3 | Create a trading context report for the S&P 500 as of 2023-… | 2/3 | 1/3 |
| 4 | Generate a report for Tesla (TSLA) as of 2024-02-10. Polyma… | 4/4 | 2/4 |

### Skill: fundamentals-analysis

- **Description:** Retrieves and validates fundamental financial data (profile, statements, insider trades) for US and non-US filers, enforcing point-in-time accuracy rules and handling data unavailability explicitly. Use when analyzing company fundamentals, retrieving SEC filings, or processing financial data for historical backtests.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |   100% |    46% |   +54% |

#### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | Retrieve the income statement for Apple Inc. (AAPL) as of 2… | 4/4 | 2/4 |
| 2 | Retrieve the company profile (including sector, market cap,… | 3/3 | 1/3 |
| 3 | Retrieve insider trading data for Amazon (AMZN) for the per… | 3/3 | 0/3 |
| 4 | Retrieve the balance sheet for Bitcoin (BTC) as of 2023-05-… | 3/3 | 3/3 |

### Skill: research-debate-facilitation

- **Description:** Facilitates the adversarial debate between Bull and Bear researchers by managing turn-taking, enforcing rebuttal rules, and handling handoff to the Research Manager. Use this skill when initiating, progressing, or concluding a structured investment debate.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |    94% |    88% |    +6% |

#### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | Initiate a debate for 'TechCorp' with a configured depth of… | 4/4 | 4/4 |
| 2 | The debate for 'GreenEnergy Inc.' has completed 2 full roun… | 4/4 | 3/4 |
| 3 | During Round 2 of the debate for 'PharmaCo', the Bear Resea… | 4/4 | 3/4 |
| 4 | The Bull Researcher has just finished their opening stateme… | 3/4 | 4/4 |

### Skill: research-manager-verdict

- **Description:** Synthesizes Bull/Bear debate history and analyst reports into a structured five-level investment plan (Buy, Overweight, Hold, Underweight, Sell) for the Trader, applying strict evidence-weighting rules to avoid defaulting to Hold.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |    81% |    94% |   -13% |

#### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | Review the debate for TechCorp (TCKR). The Bull case presen… | 4/4 | 3/4 |
| 2 | Review the debate for GreenEnergy Inc. (GEI). The Bull argu… | 4/4 | 4/4 |
| 3 | Review the debate for PharmaCo (PHCO). The Bull presents a … | 1/4 | 4/4 |
| 4 | Review the debate for RetailGiant (RTG). The Bull and Bear … | 4/4 | 4/4 |

### Skill: trader-proposal-generation

- **Description:** Converts the Research Manager's investment plan into a concrete transaction proposal (Buy, Hold, Sell) with absolute price levels and position sizing for the Risk Team. Use this skill when finalizing trade execution details, determining entry/stop-loss prices, or handling malformed numeric data in trading instructions.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |    75% |    75% |    +0% |

#### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | The Research Manager's plan for TSLA is 'Accumulate on dip'… | 4/5 | 5/5 |
| 2 | The Research Manager's plan for AAPL is 'Hold'. The technic… | 5/5 | 5/5 |
| 3 | The Research Manager's plan for NVDA is 'Sell'. The technic… | 3/5 | 4/5 |
| 4 | The Research Manager's plan for MSFT is 'Buy'. The technica… | 3/5 | 1/5 |

### Skill: risk-debate-facilitation

- **Description:** Facilitates the structured risk debate between Aggressive, Conservative, and Neutral analysts for a trader's proposal. Use this skill when managing turn-taking, enforcing debate depth, ensuring shared context, and handing off the debate history to the Portfolio Manager.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |    95% |    75% |   +20% |

#### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | Facilitate a risk debate for Trader J. Doe's proposal to bu… | 5/5 | 4/5 |
| 2 | Facilitate a debate for Trader A. Smith's proposal to short… | 4/5 | 2/5 |
| 3 | Facilitate a debate for Trader B. Lee's proposal to hold cu… | 5/5 | 5/5 |
| 4 | Facilitate a debate for Trader C. Wu's proposal to invest i… | 5/5 | 4/5 |

### Skill: portfolio-manager-decision

- **Description:** Executes the final Portfolio Manager decision gate by synthesizing research, trading proposals, risk debates, and historical lessons into a definitive five-level rating and investment thesis. Use this skill when finalizing an investment decision, resolving conflicting inputs, or issuing the run's final verdict.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |    81% |    69% |   +13% |

#### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | Review the following inputs for TechCorp (TCKR): Research M… | 4/4 | 3/4 |
| 2 | Process the decision for GreenEnergy Inc. (GEI). The Resear… | 1/4 | 1/4 |
| 3 | Finalize the decision for PharmaCo (PHCO). Research Manager… | 4/4 | 4/4 |
| 4 | Issue the final decision for RetailGiant (RTG). Research Ma… | 4/4 | 3/4 |

### Skill: decision-recording-and-reporting

- **Description:** Records the final decision to the persistent log, generates role-based and consolidated reports with provenance headers, and manages snapshot cleanup. Use when finalizing a run, persisting decisions, or generating output documents for operators.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |   100% |    25% |   +75% |

#### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | Process the final decision for Instrument 'INS-9921' dated … | 6/6 | 2/6 |
| 2 | Process the final decision for Instrument 'INS-5544' dated … | 6/6 | 1/6 |
| 3 | Process the final decision for Instrument 'INS-1122' dated … | 6/6 | 1/6 |
| 4 | Process the final decision for Instrument 'INS-7788' dated … | 6/6 | 2/6 |

### Skill: decision-settlement

- **Description:** Settles pending trading decisions by calculating returns and alpha against regional benchmarks after the holding window expires, generating AI reflections, and updating the decision log. Use this skill when processing the learning loop for past decisions, specifically when identifying decisions whose holding window (default 5 trading days) has fully traded since the analysis date.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |    86% |    71% |   +14% |

#### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | Settle decision ID 'DEC-2023-089' for AAPL. The analysis da… | 4/4 | 3/4 |
| 2 | Attempt to settle decision ID 'DEC-2023-090' for TSLA. Anal… | 4/4 | 3/4 |
| 3 | Settle decision ID 'DEC-2023-091' for a regional stock 'XYZ… | 1/3 | 1/3 |
| 4 | Two concurrent processes attempt to settle decision ID 'DEC… | 3/3 | 3/3 |

### Skill: memory-lesson-retrieval

- **Description:** Retrieves and formats settled decision lessons from the decision log for the Portfolio Manager, enforcing point-in-time integrity and excluding pending entries. Use when the Portfolio Manager requires historical context, recent instrument-specific lessons, or cross-instrument insights for a specific run date.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|

Arm errors:
  ! raw: Failed to obtain the grading after 3 attempts: Unexpected end-of-input: was expecting closing quote for a string value
 at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1, column: 1048]

### Skill: run-resumption-management

- **Description:** Manages the checkpointing and resumption logic for interrupted runs. Use this skill when initiating a run to determine whether to resume from a saved snapshot or start fresh, and when finalizing a run to clear or retain snapshots based on success or failure.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|
| raw |   100% |    54% |   +46% |

#### Arm: raw

| Eval | Scenario | With-skill | No-skill |
|------|----------|:----------:|:--------:|
| 1 | A data pipeline run with identity signature 'sig-abc-123' w… | 4/4 | 3/4 |
| 2 | A batch processing job with identity signature 'sig-def-456… | 3/3 | 1/3 |
| 3 | A machine learning training run with identity signature 'si… | 3/3 | 3/3 |
| 4 | A new run is invoked with identity signature 'sig-jkl-000'.… | 3/3 | 0/3 |

### Skill: evaluation-sweep-orchestration

- **Description:** Orchestrates the evaluation (backtest) mode by executing a grid of instruments and dates through the full firm workflow, handling resumption, failure isolation, settlement, and summary generation. Use this skill when initiating, resuming, or completing a backtest sweep.
- **Evals:** 4 scenario evals (generated per-skill)

| Arm | With-skill pass | No-skill pass | Delta (skill lift) |
|-----|:---------------:|:-------------:|:------------------:|

Arm errors:
  ! raw: Failed to obtain the grading after 3 attempts: Unexpected character ('(' (code 40)): was expecting comma to separate Object entries
 at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1, column: 442]
