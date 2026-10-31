package com.embabel.smith.eval;

import java.util.List;

/**
 * Statistics over repeated eval composite scores — the measurement behind docs/04 caveat #4 ("eval
 * scores are only as stable as the harness, which was not measured"). Pure and offline: given the
 * per-run composite scores, it reports the distribution and whether the score is a repeatable
 * (stable) signal or a noisy one that should be read as a distribution rather than a point.
 */
public record ScoreStability(int count, double mean, double min, double max, double range,
                             double stdev, boolean stable) {

    /** A score is "stable" when the spread across runs is at or below this range. */
    public static final double STABLE_RANGE_THRESHOLD = 0.25;

    /** Compute stability over {@code scores}; an empty list yields a degenerate (count 0) report. */
    public static ScoreStability of(List<Double> scores) {
        if (scores.isEmpty()) {
            return new ScoreStability(0, 0.0, 0.0, 0.0, 0.0, 0.0, true);
        }
        double min = scores.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double max = scores.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double mean = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double stdev = Math.sqrt(scores.stream()
                .mapToDouble(s -> (s - mean) * (s - mean))
                .average().orElse(0.0));
        double range = max - min;
        return new ScoreStability(scores.size(), mean, min, max, range, stdev,
                range <= STABLE_RANGE_THRESHOLD);
    }
}
