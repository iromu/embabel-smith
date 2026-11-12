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

package com.embabel.smith.studio.improve;

import java.util.List;

import com.embabel.smith.builder.security.ScanReport;

/**
 * The outcome of running the gate chain on a candidate pack: whether it cleared every
 * gate (and may be published), or the stage and reasons it was rejected at. Carries the
 * re-eval score and the security scan so a rejection is recorded with evidence.
 *
 * @param gated whether the candidate cleared every gate
 * @param stage the stage the candidate was rejected at (null on pass)
 * @param reasons the recorded rejection reasons
 * @param score the re-eval pass-rate, or null when not reached
 * @param scan the security scan report, or null when not run
 * @author Ivan Rodriguez
 */
public record GateResult(boolean gated, String stage, List<String> reasons, Double score, ScanReport scan) {

	/** A candidate that cleared every gate (re-eval beat the baseline). */
	public static GateResult pass(double score, ScanReport scan) {
		return new GateResult(true, null, List.of(), score, scan);
	}

	/** A candidate rejected at a named stage, with the recorded reasons. */
	public static GateResult reject(String stage, List<String> reasons, Double score, ScanReport scan) {
		return new GateResult(false, stage, List.copyOf(reasons), score, scan);
	}
}
