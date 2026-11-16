---
name: instrument-identity-resolution
description: Deterministically resolves an instrument's true identity (name, sector, industry, market) before analytical roles execute, handling point-in-time constraints and fail-open scenarios to prevent misidentification.
---

# Instrument Identity Resolution

This skill ensures that every analytical role operates on the correct company identity by resolving the instrument's true name and classification before any analysis begins. It prevents downstream errors where price patterns might be misinterpreted as belonging to a different company due to ticker collisions or stale data.

## Core Workflow

1. **Receive Input**: Accept the instrument ticker and the `analysis_date` (the point-in-time for the analysis).
2. **Lookup Identity**: Query the master instrument database for the instrument's current official name, sector, industry, and market classification.
3. **Apply Point-in-Time Constraints**:
   - If `analysis_date` is **today** (or the current system date): Use the full resolved identity (Name, Sector, Industry, Market).
   - If `analysis_date` is **in the past**:
     - Provide the **current official name** for identification purposes only.
     - **Withhold** the current Sector, Industry, and Market classifications. Do not inject these into the prompt, as they may not have applied on the historical analysis date.
4. **Handle Lookup Failures**:
   - If the lookup service is unavailable or returns no result: **Fail-open**.
   - Proceed with **ticker-only context**. Do not block the run. Do not guess the name or sector.
5. **Inject Context**: Pass the resolved identity string into the prompt context for all subsequent analytical roles.

## Decision Rules

### Point-in-Time Logic
- **Rule**: Historical accuracy of classification is prioritized over completeness.
- **Action**: When `analysis_date < current_date`, strip sector/industry/market fields from the output. Only the name is provided to anchor the identity, but the classification is omitted to avoid anachronistic bias.
- **Why**: A company's sector classification can change over time (e.g., a tech company reclassified as a utility). Injecting today's sector into a past-dated analysis can skew fundamental or comparative analysis.

### Fail-Open Strategy
- **Rule**: Availability of analysis is prioritized over perfect identity resolution.
- **Action**: If the identity lookup fails (timeout, 404, service down), return the ticker symbol alone as the identity context.
- **Why**: Blocking the entire pipeline for a missing sector label is disproportionate. Analytical roles can often proceed with price/volume data using the ticker, even if fundamental context is missing. It is better to have a partial analysis than no analysis.

## Escalation & Exceptions

- **Ambiguous Tickers**: If the ticker maps to multiple instruments (e.g., cross-listed shares), select the primary listing (highest volume or home market) unless the input explicitly specifies a market.
- **Delisted Instruments**: If the instrument is delisted, return the last known name and mark the status as `DELISTED`. Do not provide current sector/industry.

## Gotchas

- **Name vs. Classification**: The *name* is treated as a stable identifier for the entity, even in historical contexts. The *classification* (sector/industry) is treated as volatile and time-sensitive. Never assume today's sector applies to a past date.
- **Fail-Open ≠ Silent Failure**: When failing open, you must explicitly log that the identity resolution failed and that the run is proceeding with ticker-only context. Do not let downstream roles assume the identity was successfully resolved.
- **Ticker Collisions**: Do not rely on price patterns to infer identity. If the ticker is ambiguous, default to the primary market listing. Never let an analytical role "guess" the company based on recent price moves.
- **Injection Scope**: The resolved identity must be injected into **all** subsequent role prompts. If a role is added later, ensure it receives this context. Missing context here causes the most common downstream hallucinations.
