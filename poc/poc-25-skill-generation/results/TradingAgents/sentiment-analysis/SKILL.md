---
name: sentiment-analysis
description: Generates structured sentiment analysis for financial assets by retrieving news and social chatter, scoring sentiment on a six-level band and 0-10 scale, and handling data availability constraints. Use when asked to analyze market sentiment, produce a sentiment read, or evaluate retail chatter and news for a specific ticker.
---

# Sentiment Analysis Skill

## Core Workflow

1. **Identify Target**: Determine the asset (ticker/cashtag) and the time window for analysis.
2. **Retrieve Data**:
   - Fetch news headlines from configured news sources.
   - Fetch retail social chatter (cashtag streams, community forums) from configured social sources.
3. **Pre-screen (Optional)**: If a relevance screener is configured, apply it to social posts to filter out noise before analysis.
4. **Analyze Sentiment**:
   - Evaluate the tone, direction, and intensity of the retrieved content.
   - Determine the **Six-Level Band**: `Bullish`, `Mildly Bullish`, `Neutral`, `Mixed`, `Mildly Bearish`, or `Bearish`.
   - Assign a **0-10 Score**: 0 = Extremely Bearish, 5 = Neutral, 10 = Extremely Bullish.
   - Assess **Confidence Level**: `low`, `medium`, or `high` based on data quality and sample size.
   - Draft a **Narrative**: Summarize key drivers, notable headlines, and dominant themes in the chatter.
5. **Validate Consistency**: Ensure the Band and Score align (see Consistency Rules below).
6. **Handle Data Gaps**: Check for missing data windows. If a feed was never observed for a specific period, mark it as `unavailable` rather than empty.
7. **Output**: Return the structured sentiment read containing Band, Score, Confidence, Narrative, and Data Availability status.

## Decision Points & Rules

### Confidence Level Assignment
- **High**: Large sample size (>100 relevant posts/headlines), high-quality sources, consistent signals.
- **Medium**: Moderate sample size (20-100 posts), mixed source quality, or slight signal ambiguity.
- **Low**: Small sample size (<20 posts), poor source quality, or highly conflicting signals.

### Band vs. Score Consistency
The Band and Score must reflect the same sentiment intensity. Use the following mapping to ensure consistency:
- **Bullish**: Score 8.0 - 10.0
- **Mildly Bullish**: Score 6.0 - 7.9
- **Neutral**: Score 4.0 - 5.9
- **Mixed**: Score 4.0 - 5.9 (Note: 'Mixed' implies high variance or conflicting strong signals, not just neutrality. If signals are weakly conflicting, use 'Neutral'.)
- **Mildly Bearish**: Score 2.0 - 3.9
- **Bearish**: Score 0.0 - 1.9

*If the calculated score falls outside the band's range, adjust the band or the score to align. Do not output mismatched pairs.*

### Data Availability Handling
- **Recent Items Only**: Social feeds are not archived. Assume only recent items are available.
- **Historical Runs**: If analyzing a past window, acknowledge that data may be incomplete or unavailable.
- **Unavailable vs. Empty**:
  - If a feed returned no results because the window was never observed (e.g., feed was down, or window is too old), report status as `unavailable`.
  - If a feed was active but returned zero relevant posts, report status as `empty` (or zero count).
  - *Why*: Reporting `empty` for `unavailable` data falsely implies a lack of sentiment rather than a lack of data, which can mislead downstream consumers.

## Escalation & Exceptions

- **No Data Found**: If no news or social data is retrieved for the target asset, return a `Neutral` band with a `0` score, `low` confidence, and a narrative stating "No data available for analysis."
- **Conflicting Signals**: If news and social chatter strongly contradict each other (e.g., bullish news, bearish chatter), use the `Mixed` band. The score should reflect the net intensity, but the narrative must explicitly highlight the divergence.
- **Screener Failure**: If the optional pre-screening step fails or returns zero results despite raw data being available, proceed with raw data but note "Pre-screening failed; analysis based on raw feed" in the narrative.

## Gotchas

- **Social Feeds Are Not Archived**: Never assume historical social data is retrievable. If a user asks for sentiment from 6 months ago, and the feed only serves recent items, you must report the data as `unavailable`, not `empty` or `neutral`.
- **'Mixed' Is Not 'Neutral'**: `Neutral` means lack of strong sentiment. `Mixed` means strong but opposing sentiments. A score of 5.0 can be either, depending on the variance of the underlying data. Check for high variance to distinguish them.
- **Score-Band Mismatch**: A common error is assigning a `Bullish` band with a score of 6.5. This is invalid. `Bullish` requires a score ≥ 8.0. Always validate this pair before outputting.
- **Cashtag Relevance**: Ensure social posts are actually about the target asset. Generic market chatter (e.g., "The market is down") should not be attributed to a specific ticker unless explicitly linked. Use the configured screener to enforce this.
- **Confidence vs. Score**: A high score does not imply high confidence. You can have a `Bullish` (9.0) score with `low` confidence if the sample size is tiny. Keep these dimensions independent.
