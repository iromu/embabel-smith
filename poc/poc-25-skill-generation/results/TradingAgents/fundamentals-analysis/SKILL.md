---
name: fundamentals-analysis
description: Retrieves and validates fundamental financial data (profile, statements, insider trades) for US and non-US filers, enforcing point-in-time accuracy rules and handling data unavailability explicitly. Use when analyzing company fundamentals, retrieving SEC filings, or processing financial data for historical backtests.
---

# Fundamentals Analysis

This skill governs the retrieval and validation of fundamental financial data for equity instruments. It ensures data integrity by enforcing point-in-time accuracy for historical queries and explicit reporting for missing data.

## Scope & Constraints
- **Applicability**: This role is **not available** for crypto instruments. If the target instrument is crypto, halt and report that fundamentals analysis is out of scope.
- **Data Sources**:
  - US Filers: SEC filings ("as filed" basis).
  - Non-US Filers: Configured fallback sources.

## Core Workflow

### 1. Instrument Validation
- Check if the target instrument is a crypto asset.
- **If crypto**: Stop. Report that fundamentals analysis is not supported for this instrument type.
- **If equity**: Proceed to source determination.

### 2. Source Determination
- Determine if the issuer is a US filer or non-US filer.
- **US Filer**: Use SEC filings data. Apply "as filed" logic (see Point-in-Time Rules).
- **Non-US Filer**: Use configured fallback sources. Apply standard availability checks.

### 3. Data Retrieval
Retrieve the following data points:
- Company Profile
- Income Statement
- Balance Sheet
- Cash Flow Statement
- Insider Trading Data

### 4. Point-in-Time Validation (Historical Queries)
If the query is for a past date (historical run), apply strict filtering to ensure no look-ahead bias:

#### A. Profile Data Filtering
- **Rule**: Withhold "live-only" profile attributes for past runs.
- **Attributes to Withhold**: Sector, Market Cap, Valuation metrics.
- **Action**: Return these fields as `null` or `unavailable` for historical timestamps. Do not use current values for past dates.

#### B. Statement & Insider Trade Filtering
- **Rule**: Data must have a valid filing date to be served for past runs.
- **Check**: Verify if the specific statement or insider trade record has an associated filing date.
- **Action**:
  - If filing date exists and is <= query date: Serve the data.
  - If filing date is missing: Withhold the data (report as unavailable).
  - If filing date is > query date: Withhold the data (not yet filed at that time).

#### C. Restatement Handling
- **Rule**: Use "as filed" figures.
- **Action**: If a fiscal year has been restated, serve the **first reported** figures, not the restated ones. This ensures the data reflects what was publicly known at the time.

### 5. Unavailability Handling
- **Rule**: Never estimate or interpolate missing data.
- **Action**: If any requested data point is unavailable (due to missing filing dates, non-filing status, or source gaps), explicitly report it as `unavailable`.
- **Constraint**: Do not substitute with current data, peer averages, or model estimates.

## Gotchas
- **Fiscal Year vs. Filing Date**: A fiscal year may have ended, but if the filing date is after the query date, the data is **not** available. Do not serve data for a period that has not yet been filed.
- **Restatements are Invisible**: For historical accuracy, restated figures are ignored. Always retrieve the original "as filed" version. Using restated data introduces look-ahead bias.
- **Live-Only Metrics**: Sector, Market Cap, and Valuation are dynamic. Using current values for a past date is a critical error. These must be withheld for historical queries.
- **Missing Filing Dates**: If a statement or insider trade lacks a filing date, it cannot be validated for point-in-time accuracy. It must be withheld for historical runs, even if the content seems relevant.
- **Crypto Exclusion**: Do not attempt to apply this workflow to crypto assets. There is no SEC filing equivalent, and the logic does not apply.
- **Explicit Unavailability**: Silence is not an option. If data is missing, the output must explicitly state `unavailable`. Do not return empty objects or nulls without context if the system supports explicit status flags.
