/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.eval;

import java.util.List;

/**
 * Statistics over repeated eval composite scores — the measurement behind docs/04 caveat
 * #4 ("eval scores are only as stable as the harness, which was not measured"). Pure and
 * offline: given the per-run composite scores, it reports the distribution and whether
 * the score is a repeatable (stable) signal or a noisy one that should be read as a
 * distribution rather than a point.
 *
 * @param count the number of runs
 * @param mean the mean of the per-run composite scores
 * @param min the lowest per-run composite score
 * @param max the highest per-run composite score
 * @param range the spread (max minus min) across runs
 * @param stdev the standard deviation of the per-run composite scores
 * @param stable whether the score is a repeatable signal
 * @author Ivan Rodriguez
 */
public record ScoreStability(int count, double mean, double min, double max, double range, double stdev,
		boolean stable) {

	/** A score is "stable" when the spread across runs is at or below this range. */
	public static final double STABLE_RANGE_THRESHOLD = 0.25;

	/**
	 * Compute stability over {@code scores}; an empty list yields a degenerate (count 0)
	 * report.
	 */
	public static ScoreStability of(List<Double> scores) {
		if (scores.isEmpty()) {
			return new ScoreStability(0, 0.0, 0.0, 0.0, 0.0, 0.0, true);
		}
		double min = scores.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
		double max = scores.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
		double mean = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
		double stdev = Math.sqrt(scores.stream().mapToDouble((s) -> (s - mean) * (s - mean)).average().orElse(0.0));
		double range = max - min;
		return new ScoreStability(scores.size(), mean, min, max, range, stdev, range <= STABLE_RANGE_THRESHOLD);
	}
}
