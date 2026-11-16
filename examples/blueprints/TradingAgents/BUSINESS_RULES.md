# Business Rules

This document lists the rules that govern each process. Each rule states what it
is, where it applies, and (where relevant) why it matters.

**Legend:** **Confirmed** · **Assumption** · **Unresolved.**

---

## 1. Rating scales

The tool uses **three different scales**, each at a specific point in the process.
**Confirmed.**

| Scale | Levels | Used by |
|---|---|---|
| **Five-level investment rating** | Buy · Overweight · Hold · Underweight · Sell | Research Manager (investment plan) and Portfolio Manager (final decision) |
| **Three-level transaction direction** | Buy · Hold · Sell | Trader (the concrete transaction) |
| **Six-level sentiment band** | Bullish · Mildly Bullish · Neutral · Mixed · Mildly Bearish · Bearish | Sentiment Analyst |

- The Trader's three levels map onto the five-level scale: an Overweight plan is
  a **Buy**, an Underweight plan is a **Sell**, sized by how strong the case is.
  The finer Overweight/Underweight call is made at the Portfolio Manager.
  **Confirmed.**
- The sentiment band also carries a 0–10 score and a low/medium/high confidence.
  **Confirmed.**

---

## 2. Decision-making rules

These rules shape how the firm turns evidence into a rating. **Confirmed.**

1. **Conflict is not a reason to Hold.** The debates always contain conflicting
   stances; deciding which side is stronger *is the job*. The managers are
   instructed to commit to the stronger side, sized by how decisively it wins.
2. **Hold only when warranted.** Hold is chosen only when the evidence is still
   balanced after weighing, or too thin to support a call — never to appear
   decisive, and never merely because the sides disagreed.
3. **Weigh on merits, not order.** The managers weigh the arguments independent
   of which side spoke first or last.
4. **A decision nobody can read is not a position.** If no rating can be read
   from the final decision, the run is recorded as **REVIEW** (for human
   review / re-run), **never** silently downgraded to Hold. A Hold recorded in
   its place would later be quoted back as a call that was never made.
5. **Ground claims in evidence.** The technical analyst must ground exact
   price/indicator claims in a verified price snapshot and flag discrepancies
   rather than invent reconciled numbers. The trader states entry/stop as
   absolute prices, never percentages or ranges.

---

## 3. Point-in-time integrity

The tool's central integrity rule: **a run dated on day D only sees information
that was actually public on day D.** **Confirmed.**

- **Data served as of the run date.** Any data request is clamped so it never
  reaches a date later than the analysis date.
- **Live-only data is withheld for past dates.** Company profiles (name, sector,
  industry, market cap, valuation) carry no historical vintage, so for a past
  run they are **withheld** and the reason is stated, rather than leaking
  post-decision information.
- **Statements "as filed" (US filers).** US company statements come from SEC
  filings, which record the date every figure was filed. A past run reads
  statements exactly as they stood that day: a fiscal year that has ended but not
  been filed is not served, and a figure restated later still reads as first
  reported.
- **Statements without a filing date are withheld.** A source that dates a
  statement by the period it covers (not when it was published) cannot say which
  figures were public by a past date, so for a past run those are withheld and
  the reason stated.
- **Insider trades without a filing date are withheld** for the same reason (the
  trade may not yet have been disclosed).
- **Social feeds are the known exception.** News/social feeds serve recent items
  and are not archived, so a past run's sentiment inputs are *not* fully
  point-in-time. This is stated as a limitation. **Confirmed.**
- **Memory lessons are point-in-time too.** For a past-dated run, only lessons
  whose outcome was already known by that date are included. **Confirmed.**

**Business implication.** This is what makes a past-dated run a faithful
"what would we have decided then" exercise, and it is what the evaluation mode
relies on to score past decisions without look-ahead bias.

---

## 4. Fundamentals-as-filed rule

**Confirmed.** For companies that file with the SEC (including foreign companies
listed in the US), financial statements are served **as filed**, with filing
dates, so a past run reads the figures exactly as they stood on the analysis
date. For anything else (e.g. Hong Kong or A-share listings), the tool falls
through to the next data source in the configured chain. Machine-readable filings
begin in 2009; a quarter that is only reported inside the annual figure is
reported as unavailable rather than derived.

---

## 5. Data-source selection rule

**Confirmed.** The operator configures, per evidence category, **which source
serves it**, and may list several **in order as a fallback chain**.

- **The configured list is the exact chain.** The tool does **not** silently fall
  back to a source the operator did not choose — doing so previously returned
  data from an unexpected source and caused cross-source inconsistencies.
- **Failure within the chain is handled explicitly.** If a source is throttled or
  unreachable, the next in the chain is tried. If *every* configured source says
  it has no data for the instrument, the tool returns an explicit
  "no data available" notice (the instrument may be invalid, delisted, or
  uncovered) and instructs the analyst to report it as unavailable — **not** to
  estimate or fabricate a value.
- **Optional enrichment degrades instead of aborting.** Macro indicators and
  prediction markets are "flavour" data: a failure there is reported as
  unavailable and the run proceeds. Core data (prices, fundamentals, news)
  problems are surfaced loudly instead.

---

## 6. Portfolio (holdings) rule

**Confirmed.** Three states are distinct and must stay so:

1. **A position** in the instrument.
2. **A flat book** (explicitly no position).
3. **No portfolio provided** — the tool does *not* assume the caller holds
   nothing; it says it does not know and gives direction/sizing the caller can
   apply to their own position.

Treating "not provided" as "flat" would invent a fact about the caller's account.
The holdings are used for **sizing context only** — they are never carried
forward or treated as an executable book.

---

## 7. Settlement rules

**Confirmed.**

- **Holding window.** A decision's outcome is measured over a fixed window
  (default **5 trading days**) from the **first close after the analysis date**.
- **Settle only when fully traded.** A decision is settled only once the full
  window has traded. A re-run before that leaves it pending (to retry later)
  rather than scoring a premature partial return. A window that has passed but
  did not trade through (a long market closure) is extended to today.
- **Alpha vs the regional benchmark.** The return is measured against the
  instrument's regional benchmark (auto-chosen from the market, or an override).
  Returns are compared as percentages, each in its own currency.
- **Record the known-by date.** Each settled decision stores the date its
  outcome became known, so a later past-dated run can filter lessons correctly.
- **Reflection is bounded to the window.** The reflection explicitly notes that
  the window may be shorter than the horizon the decision was written for, and
  says so plainly if the window is too short to judge the thesis.
- **Concurrency and idempotency.** One settlement pass at a time per log; a
  decision is settled once; an interrupted pass keeps what it finished.

---

## 8. Decision-log (memory) rules

**Confirmed.**

- **Append-only.** Each completed run appends its decision.
- **One entry per instrument + date.** A re-run for the same instrument and date
  does not add a second entry (whether the earlier one is pending or settled), so
  the same decision is never counted twice.
- **Pending → settled.** Entries start as *pending* and are updated in place to
  *settled* with the outcome and reflection.
- **Rotation.** Optionally, the oldest *settled* entries are pruned once a cap is
  exceeded. **Pending entries are never pruned** (they are unprocessed work).
- **Read-back.** Later runs read the most recent settled decisions for the same
  instrument plus recent cross-instrument lessons.

---

## 9. Instrument identity rule

**Confirmed.** Before any role runs, the tool deterministically resolves the
instrument's identity (its real name and classification) and injects it so every
role anchors to the correct company. This stops the pipeline from inferring a
*different* company from a chart pattern and cascading that error through every
downstream role. If the lookup is unavailable, the run proceeds with
ticker-only context (fail-open) rather than blocking. For a past-dated run, only
the current name is given (to identify the company), not today's sector/industry,
which may not have applied on that date.

---

## 10. Run-configuration rules

**Confirmed.**

- **Two model tiers.** A "quick" model serves the analysts, researchers, debaters
  and trader; a "deep" model serves the two managers. Each tier may use a
  different provider.
- **Research depth sets both debate and risk rounds.** The operator chooses
  shallow / medium / deep (or sets the round counts directly).
- **Non-determinism is expected.** Because the tool is AI-driven, two runs of the
  same input can differ (model sampling, and live data that changes over time).
  Lowering the sampling temperature reduces variation on models that honor it;
  reasoning-first models largely ignore it.
- **Reproducibility safeguards.** The analysed company's identity is resolved
  deterministically, and the technical analyst grounds exact price/indicator
  claims in a verified snapshot — addressing earlier issues of "different
  companies" or fabricated price levels across runs.
- **Configuration is recorded.** Each saved report records what produced the run
  (version, models, providers, analysts, round counts, data sources) — but never
  credentials or local paths.

---

## 11. Evaluation-mode rules

**Confirmed.**

- **Evaluates decision quality, not portfolio value.** It is explicitly *not* a
  portfolio simulator; no quantity, fill price, or cash ledger is invented.
- **Cells are independent.** No position is carried forward across cells.
- **Own log.** The evaluation writes to its own decision log; the operator's own
  log is never written to.
- **Entered at the first close after the analysis date** (a run reads news
  published after that day's close).
- **Scored by rating.** Each rating's outcome is scored against its direction:
  Buy/Overweight expect positive alpha, Sell/Underweight expect negative alpha,
  and Hold claims no direction (so nothing about alpha proves it right or
  wrong). A decision with no readable rating is reported as *unscored*.
