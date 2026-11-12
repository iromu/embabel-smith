# Exceptions and Edge Cases

This document lists what happens when something fails or an unexpected situation
occurs. For each: the situation, how the application responds, and the business
rationale.

**Legend:** **Confirmed** · **Assumption** · **Unresolved.**

The overarching principle: **report what is unavailable rather than invent it, and
let a non-critical failure degrade instead of aborting the run.** **Confirmed.**

---

## 1. Data availability

### 1.1 No usable data from any configured source
**Situation.** Every configured source says it has no data for the instrument
(invalid symbol, delisted, not covered, or stale data).
**Response.** The analyst is given an explicit "no data available" notice that
states the specific reason and instructs it to **report the data as unavailable —
not to estimate or fabricate a value.** **Confirmed.**

### 1.2 A source is throttled or unreachable
**Situation.** A configured source is rate-limited or cannot be reached.
**Response.** The tool tries the **next source in the configured chain**. If a
source failed (throttled) *and* another reported "no data," the tool reports the
*source* as the problem (not the instrument), because a throttled source never
said whether it had the data. It does **not** end the run. **Confirmed.**

### 1.3 Optional enrichment unavailable
**Situation.** Macro indicators or prediction-market data (the "flavour" data that
enriches the news analyst) cannot be retrieved.
**Response.** Reported as unavailable and the run **proceeds without it** — a
missing key, a bad request, or a network blip does not crash an analysis over
non-core data. **Confirmed.**

### 1.4 A report is absent (analyst not selected / returned nothing)
**Situation.** A downstream role needs a report that does not exist (its analyst
was not selected, refused, or produced nothing).
**Response.** The absence is presented as an explicit *"No X report in this run:
it is not available, not an empty finding,"* so the reading role does **not** fill
a blank from nothing. **Confirmed.**

---

## 2. Point-in-time exceptions

### 2.1 Live-only company data for a past date
**Situation.** A run dated in the past needs company profile data (name, sector,
market cap, valuation) that only exists in the present.
**Response.** The data is **withheld** and a notice explains that serving it would
put post-decision information into the analysis. Only statements that have a
known filing date are served as filed. **Confirmed.**

### 2.2 Statements / insider trades without a filing date
**Situation.** A source dates a statement by the period it covers (or a trade by
when it happened), with no filing date, for a past run.
**Response.** Withheld, with a notice that the source cannot show which figures
were public on that date. **Confirmed.**

### 2.3 Social feeds are not archived
**Situation.** A past-dated run needs sentiment from social feeds that only serve
recent items.
**Response.** This is a **known limitation**: the sentiment inputs for a
historical run are not fully point-in-time, and the tool states this. A window a
feed never observed is reported as *unavailable*, not as an absence of content.
**Confirmed.**

---

## 3. Decision and output exceptions

### 3.1 No readable rating in the final decision
**Situation.** The Portfolio Manager's decision does not contain a readable
five-level rating (e.g. contradictory rating lines, or the scale echoed rather
than a call).
**Response.** The run is recorded as **REVIEW** — for human review or re-run — and
**never** silently downgraded to Hold. The operator is told plainly that no rating
could be read and to re-run or judge the text themselves. **Confirmed.**

### 3.2 Malformed numeric fields (entry/stop/target)
**Situation.** The model writes a placeholder ("None", "N/A"), a **percentage**
("15%"), or a human-formatted price ("$1,234.50") where an absolute price was
asked.
**Response.** Placeholders and percentages are **dropped** (a percentage cannot be
salvaged into a price — reading "15%" as 15 would put a stop at $15 on a $600
stock). A formatted price is reduced to its number. Crucially, **one bad field is
nulled rather than failing the whole decision**, so the fields the model got right
are not lost. **Confirmed.**

### 3.3 First speaker in a debate has no opponent
**Situation.** The first role to speak has no opposing argument yet to rebut.
**Response.** It is told the other side "has not spoken yet — open the debate with
your own case," so it does **not** fabricate the opponent's position.
**Confirmed.**

---

## 4. Settlement exceptions

### 4.1 Holding window has not fully traded yet
**Situation.** A decision's holding window (default 5 trading days) has not fully
traded since the analysis date.
**Response.** The decision is **left pending** and retried on a later pass — never
scored on a premature, partial return. **Confirmed.**

### 4.2 A window that passed but did not trade through
**Situation.** The calendar window has passed but the market did not trade through
it (a long closure, e.g. a national holiday or a halt).
**Response.** The tool extends the price request to today to see whether the
window can be completed; if still incomplete, the decision stays pending.
**Confirmed.**

### 4.3 Prices unavailable
**Situation.** The instrument's closes cannot be fetched for settlement.
**Response.** The decision stays **pending** and is reported as failed
("prices unavailable"); the run is not blocked. **Confirmed.**

### 4.4 Reflection failed
**Situation.** The model call that writes the reflection fails (e.g. a transient
provider error).
**Response.** The decision stays **pending** and is reported as failed
("reflection failed"); retried on the next pass. **Confirmed.**

### 4.5 Concurrent settlement of the same log
**Situation.** Two runs/processes try to settle the same decision log at once.
**Response.** One pass at a time per log; a pass already running is not duplicated.
Each outcome is written as soon as its reflection is done, so an interrupted pass
keeps what it finished. **Confirmed.**

---

## 5. Run-lifecycle exceptions

### 5.1 A run is interrupted (checkpoint enabled)
**Situation.** A run crashes or is interrupted mid-way.
**Response.** With checkpointing on, the last completed step is saved; a later
invocation with the same instrument, date, and configuration **resumes** from
there. A mid-stream failure keeps the snapshot; a clean completion clears it.
**Confirmed.**

### 5.2 Configuration changed since the snapshot
**Situation.** A run is re-invoked with different analysts, depth, asset type,
holdings, or models than the saved snapshot.
**Response.** The snapshot does **not** apply; the run starts **fresh** rather than
silently continuing a different run. **Confirmed.**

### 5.3 A future analysis date
**Situation.** The operator supplies a date later than today.
**Response.** Rejected up front — a future date has no outcome to settle against
and cannot be analysed. **Confirmed.**

### 5.4 An invalid or unsafe instrument
**Situation.** The ticker is malformed, or is a value that would escape the
results directory (path-traversal-style input).
**Response.** Rejected before the run — both at the input prompt and at the point
the run would write its files. **Confirmed.**

### 5.5 No terminal to answer setup questions
**Situation.** A run is started without a terminal (e.g. a script) and a required
parameter is not supplied.
**Response.** The run stops **before it starts** and lists **every** question
still open (naming the flag or setting to provide), rather than stopping at the
first one. **Confirmed.**

---

## 6. Evaluation-mode exceptions

### 6.1 One cell fails
**Situation.** A single (instrument, date) cell errors (e.g. an unreachable data
source).
**Response.** The cell is recorded as failed and the **sweep continues** — one
unreachable source must not end the whole evaluation. **Confirmed.**

### 6.2 A decision in the sweep has no readable rating
**Situation.** A settled cell's decision has no readable rating.
**Response.** It is reported as **unscored** — it can neither count for nor
against the system. **Confirmed.**

### 6.3 Resuming an interrupted sweep
**Situation.** A sweep was interrupted and is re-run with the same sweep id.
**Response.** Cells already in that sweep's log are **skipped**; the sweep
continues where it stopped. **Confirmed.**

### 6.4 Settlement after the sweep
**Situation.** Each instrument's last cell would otherwise stay pending (settlement
normally happens at the start of the *next* run for that instrument).
**Response.** The sweep runs a settlement pass for each instrument at the end; any
that fail are reported as unsettled. **Confirmed.**

---

## 7. Reproducibility and non-determinism

### 7.1 Two runs of the same input differ
**Situation.** The same instrument and date produce different decisions across
runs.
**Response.** This is **expected, not a defect.** Sources of variation: AI model
sampling (reasoning models vary most and largely ignore temperature), and live
data that changes over time (news and social feeds reflect "now" even for a fixed
historical trade date). **Confirmed.**

**Mitigations available to the operator**
- Lower the sampling temperature (reduces variation on models that honor it).
- Name a non-reasoning model for tighter reproducibility.
- Pin the analysis date to hold the price/indicator window fixed.

**What no longer varies**
- The analysed company's identity is resolved deterministically before any role
  runs.
- The technical analyst grounds exact price/indicator claims in a verified
  snapshot.

**Confirmed.**

---

## 8. Unresolved questions

These are areas the codebase does not fully specify, flagged for a business
analyst to resolve with the team:

- **Unresolved.** The tool measures a decision's outcome over a fixed default
  window (5 trading days), but a decision may have been written for a longer
  horizon. The reflection notes the mismatch, but there is no separate
  "horizon-appropriate" score. Whether a longer-horizon evaluation is wanted is a
  product decision.
- **Unresolved.** The evaluation scores *directional* correctness (did the call's
  direction match the realised alpha) and mean alpha, but does not model position
  sizing, transaction costs, or portfolio-level P&L. Whether that is sufficient
  for the intended audience is a product decision.
- **Unresolved.** Sentiment inputs for historical runs are not fully
  point-in-time (feeds are not archived). Whether this limitation is acceptable
  for the tool's stated research purposes, or needs mitigation, is open.
- **Unresolved.** The operator's holdings are used for sizing context only and are
  never carried forward. Whether a persistent, forward-carrying "paper portfolio"
  is desired is a product decision (the current design explicitly avoids it).
