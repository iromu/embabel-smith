# Workflows

This document describes each process **step by step**, with flowcharts and
sequence diagrams.

**Legend:** **Confirmed** · **Assumption** · **Unresolved.**

---

## 1. The main analysis workflow

This is the step-by-step of a single-instrument run.

```mermaid
flowchart TD
    Start([Operator starts a run]) --> Setup[Setup: instrument, date,<br/>analysts, depth, models, language,<br/>and optional holdings]
    Setup --> Creds{Credentials /<br/>endpoints OK?}
    Creds -- no --> Stop1[Stop before the run;<br/>name what is missing]
    Creds -- yes --> Identity[Resolve the instrument's identity<br/>- what it actually is]
    Identity --> Parallel[Analysts start together<br/>for the selected subset]

    subgraph Analysts[Analyst team - in parallel]
        M[Market / technical report]
        S[Sentiment report]
        N[News / macro report]
        F[Fundamentals report]
        MEM[Settle due past decisions<br/>and load lessons for this instrument]
    end

    Parallel --> Analysts
    Analysts --> AllIn{Every selected<br/>analyst filed?}
    AllIn -- no --> Analysts
    AllIn -- yes --> Debate

    subgraph Debate[Research debate]
        B1[Bull argues] --> B2[Bear argues]
        B2 --> Bmore{Round limit<br/>reached?}
        Bmore -- no --> B1
    end

    Debate --> RM[Research Manager<br/>issues investment plan<br/>with a 5-level recommendation]
    RM --> Trader[Trader proposes a transaction<br/>Buy/Hold/Sell, entry, stop, sizing]

    subgraph Risk[Risk debate]
        R1[Aggressive argues] --> R2[Conservative argues] --> R3[Neutral argues]
        R3 --> Rmore{Round limit<br/>reached?}
        Rmore -- no --> R1
    end

    Trader --> Risk
    Risk --> PM[Portfolio Manager<br/>issues final decision<br/>5-level rating plus thesis]
    PM --> Review{Rating readable?}
    Review -- no --> RecReview[Record as FOR REVIEW,<br/>not a position]
    Review -- yes --> Record[Record decision to the log]
    RecReview --> Output
    Record --> Output[Show result; offer to save report<br/>docs plus single-page web view]
    Output --> End([Run complete])
```

### Step-by-step

1. **Setup.** The operator supplies (or is prompted for) the instrument, date,
   analysts, depth, models, and language; optionally holdings. The previous run's
   answers are the defaults. **Confirmed.**
2. **Pre-flight.** The credentials/endpoints for the models actually in use are
   checked. A missing credential stops the run before it starts. **Confirmed.**
3. **Instrument identity.** The tool resolves what the instrument actually is
   (its real name and classification) so every role anchors to the correct
   company instead of inferring one from the price chart. If the lookup is
   unavailable, it proceeds with ticker-only context rather than failing.
   **Confirmed.**
4. **Analysts in parallel.** The selected analysts each produce their report from
   their own evidence. In parallel, the tool settles any due past decisions and
   loads the lessons for this instrument. **Confirmed.**
5. **Gate.** The research debate begins only once *every* selected analyst has
   filed. **Confirmed.**
6. **Research debate.** The Bull and Bear take turns up to the configured depth,
   each rebutting the other. **Confirmed.**
7. **Research Manager verdict.** The manager weighs both sides and issues a
   structured investment plan with a five-level recommendation. **Confirmed.**
8. **Trader proposal.** The Trader turns the plan into a concrete transaction
   (action, reasoning, entry/stop/sizing), grounded in the technical price
   structure and, if given, the operator's holdings. **Confirmed.**
9. **Risk debate.** The Aggressive, Conservative, and Neutral risk analysts
   debate the proposal up to the configured depth. **Confirmed.**
10. **Portfolio Manager decision.** The Portfolio Manager issues the final
    five-level rating, executive summary, and thesis, incorporating any lessons.
    **Confirmed.**
11. **Record + output.** The decision is recorded to the decision log (as a
    position, or as *for review* if no rating is readable). The result is shown,
    and the operator is offered the saved report (documents + single-page web
    view). **Confirmed.**

---

## 2. Sequence: a single run

```mermaid
sequenceDiagram
    participant OP as Operator
    participant Firm as The firm
    participant Data as Data providers
    participant AI as AI models
    participant Log as Decision log

    OP->>Firm: instrument, date, analysts, depth, models
    Firm->>Firm: resolve instrument identity
    par Analysts (parallel)
        Firm->>Data: prices / indicators
        Data-->>Firm: verified snapshot
        Firm->>AI: market report
        AI-->>Firm: market report
    and
        Firm->>Data: news + social chatter
        Data-->>Firm: sources
        Firm->>AI: sentiment read
        AI-->>Firm: sentiment report
    and
        Firm->>Data: company + global news, macro
        Data-->>Firm: news / macro
        Firm->>AI: news report
        AI-->>Firm: news report
    and
        Firm->>Data: statements, profile, insider
        Data-->>Firm: fundamentals
        Firm->>AI: fundamentals report
        AI-->>Firm: fundamentals report
    and
        Firm->>Log: settle due decisions, load lessons
        Log-->>Firm: lessons
    end
    Firm->>AI: Bull argument
    AI-->>Firm: bull
    Firm->>AI: Bear argument
    AI-->>Firm: bear
    Note over Firm: repeat to depth
    Firm->>AI: Research Manager plan
    AI-->>Firm: investment plan
    Firm->>AI: Trader proposal
    AI-->>Firm: transaction
    Firm->>AI: Risk debate (3 stances)
    AI-->>Firm: risk debate
    Firm->>AI: Portfolio Manager decision
    AI-->>Firm: final rating + thesis
    Firm->>Log: record decision (pending)
    Firm-->>OP: show result, offer report
```

**Confirmed.** The parallel fan-out of the analysts, the ordered hand-offs, and
the final recording are the actual run structure.

---

## 3. The research debate (sub-workflow)

```mermaid
flowchart LR
    Start([All analyst reports in]) --> Bull[Bull: opening case]
    Bull --> Bear[Bear: rebuttal]
    Bear --> Cnt{Count reached<br/>2 x depth?}
    Cnt -- no --> Bull2[Bull: next turn]
    Bull2 --> Bear2[Bear: next turn]
    Bear2 --> Cnt
    Cnt -- yes --> RM[Research Manager<br/>weighs both, issues plan]
```

- Each **round** = one Bull turn + one Bear turn. The debate stops when the count
  reaches twice the configured depth. **Confirmed.**
- The first speaker, with no opponent yet, is told the other "has not spoken
  yet" and opens with its own case rather than inventing a rebuttal.
  **Confirmed.**
- The Research Manager's instruction is explicit: **conflict alone is not a
  reason to Hold** — commit to the stronger side, sized by how decisively it
  wins; Hold only when the evidence is still balanced or too thin. **Confirmed.**

---

## 4. The risk debate (sub-workflow)

```mermaid
flowchart LR
    Start([Trader's proposal]) --> Ag[Aggressive]
    Ag --> Con[Conservative]
    Con --> Neu[Neutral]
    Neu --> Cnt{Count reached<br/>3 x depth?}
    Cnt -- no --> Ag2[Aggressive]
    Ag2 --> Con2[Conservative]
    Con2 --> Neu2[Neutral]
    Neu2 --> Cnt
    Cnt -- yes --> PM[Portfolio Manager<br/>final decision]
```

- The three stances speak in a fixed order (Aggressive → Conservative → Neutral);
  a **round** is one turn each. The debate stops when the count reaches three
  times the configured depth. **Confirmed.**
- The Portfolio Manager applies the same "conflict is not a Hold" rule to the risk
  debate. **Confirmed.**

---

## 5. The settlement workflow (learning loop)

```mermaid
flowchart TD
    S([A run starts - or a settle request]) --> Due[Find pending decisions<br/>whose holding window has fully traded]
    Due --> None{Any due?}
    None -- no --> Done([nothing to settle])
    None -- yes --> Bench[Resolve the regional benchmark]
    Bench --> Prices[Fetch the instrument's closes<br/>from the first close after the analysis date]
    Prices --> Window{Full window traded?}
    Window -- no --> Skip[Leave pending - retry later]
    Window -- yes --> Calc[Compute raw return plus alpha<br/>vs benchmark; note the known-by date]
    Calc --> Reflect[Ask the model for a 2-4 sentence reflection]
    Reflect --> Rfail{Reflection OK?}
    Rfail -- no --> Fail[Leave pending, report as failed]
    Rfail -- yes --> Write[Update the entry, pending to settled<br/>with return, alpha, window, reflection]
    Write --> Done
    Skip --> Done
    Fail --> Done
```

**Confirmed.** Key rules in this workflow:
- A decision is settled **only** once the full holding window has traded; a
  re-run before that leaves it pending rather than scoring a premature, partial
  return.
- If prices or the reflection are unavailable, the decision stays pending and is
  reported; the run is not blocked.
- Settlement is **idempotent and safe under concurrency**: one pass at a time per
  log, and each outcome is written as soon as its reflection is done, so an
  interrupted pass keeps what it finished.

---

## 6. The evaluation (backtest) workflow

```mermaid
flowchart TD
    S([Operator: tickers, start, end, step]) --> Grid[Build the date grid<br/>- never past today]
    Grid --> Cells[Cross product: each ticker x each date]
    Cells --> Skip{Cell already in this<br/>sweep's log?}
    Skip -- yes --> Next
    Skip -- no --> Run[Run the full firm for the cell]
    Run --> RFail{Cell OK?}
    RFail -- no --> LogFail[Record cell failure] --> Next
    RFail -- yes --> Next[Next cell]
    Next --> AllDone{All cells done?}
    AllDone -- no --> Skip
    AllDone -- yes --> Settle[Settle each ticker's due decisions]
    Settle --> Summary[Summarise by rating:<br/>count, hit rate, mean alpha]
```

**Confirmed.**
- The grid stops at **today** (a future date has no outcome to score).
- An interrupted sweep **resumes**: re-run with the same sweep id skips done cells.
- The evaluation writes to its **own** log; the operator's own log is never
  touched.
- One unreachable data source must not end the whole sweep — a failed cell is
  recorded and the sweep continues.

---

## 7. The resume workflow

```mermaid
flowchart TD
    S([Run starts, resume enabled]) --> Sig[Compute the run's identity:<br/>instrument, date, analysts, depth,<br/>asset type, holdings, models, settings]
    Sig --> Exists{Saved snapshot<br/>for this identity?}
    Exists -- no --> Fresh[Start fresh,<br/>save a snapshot after each step]
    Exists -- yes --> Resume[Resume from the last<br/>completed step]
    Fresh --> Run[Run to completion]
    Resume --> Run
    Run --> OK{Completed?}
    OK -- yes --> Clear[Clear the snapshot]
    OK -- no --> Keep[Keep the snapshot<br/>for a later resume]
```

**Confirmed.** Changing any run-affecting setting invalidates the snapshot, so a
run under different settings starts fresh instead of silently continuing the
previous one.
