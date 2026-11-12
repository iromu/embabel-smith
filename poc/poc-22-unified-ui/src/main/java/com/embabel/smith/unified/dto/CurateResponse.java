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

package com.embabel.smith.unified.dto;

import com.embabel.smith.lifecycle.DriftReport;

/**
 * The result of a lifecycle re-eval (POC 18): the spec's lifecycle transition (before →
 * after), the two drift signals (content hash + eval pass-rate), the human-readable POC
 * 18 verdict, and whether the spec is still surfaced by retrieval. {@link #error} is
 * non-null when the re-eval could not run (no spec selected, an unknown spec, or no
 * endpoint configured) — in which case the transition fields are empty.
 *
 * @param error The error when the re-eval could not run, or null.
 * @param specName The spec re-scored.
 * @param before The lifecycle state before the re-eval.
 * @param after The lifecycle state after the re-eval.
 * @param contentDrift Whether the content hash changed.
 * @param baselineScore The eval pass-rate baseline.
 * @param currentScore The current eval pass-rate.
 * @param verdict The human-readable POC 18 verdict.
 * @param surfaced Whether the spec is still surfaced by retrieval.
 * @author Ivan Rodriguez
 */
public record CurateResponse(String error, String specName, String before, String after, boolean contentDrift,
		double baselineScore, double currentScore, String verdict, boolean surfaced) {

	public static CurateResponse error(String error) {
		return new CurateResponse(error, null, null, null, false, 0.0, 0.0, null, false);
	}

	public static CurateResponse from(String specName, DriftReport report, boolean surfaced) {
		return new CurateResponse(null, specName, report.before().name(), report.after().name(), report.contentDrift(),
				round4(report.baselineScore()), round4(report.currentScore()), report.verdict(), surfaced);
	}

	private static double round4(double v) {
		return Math.round(v * 10000.0) / 10000.0;
	}
}
