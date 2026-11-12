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

package com.embabel.smith.lifecycle;

import java.util.ArrayList;
import java.util.List;

/**
 * The outcome of one proactive re-eval: the lifecycle transition and the two drift
 * signals, plus a human-readable verdict.
 *
 * @param spec the spec id being assessed.
 * @param before the recorded lifecycle state before the re-eval.
 * @param after the lifecycle state after the re-eval.
 * @param contentDrift whether the content hash diverged from the baseline.
 * @param baselineScore the eval pass-rate recorded at baseline.
 * @param currentScore the freshly-measured eval pass-rate.
 * @param recordedHash the content hash recorded at baseline.
 * @param currentHash the content hash as measured now.
 * @author Ivan Rodriguez
 */
public record DriftReport(String spec, LifecycleState before, LifecycleState after, boolean contentDrift,
		double baselineScore, double currentScore, String recordedHash, String currentHash) {

	public boolean drifted() {
		return this.contentDrift || this.after == LifecycleState.DEGRADED || this.after == LifecycleState.RETIRED;
	}

	public String verdict() {
		if (this.after == LifecycleState.RETIRED) {
			return String.format("RETIRED: score collapsed %.2f -> %.2f; the spec's contract is violated",
					this.baselineScore, this.currentScore);
		}
		if (this.after == LifecycleState.DEGRADED) {
			List<String> reasons = new ArrayList<>();
			if (this.contentDrift) {
				reasons.add("content changed");
			}
			if (this.currentScore < this.baselineScore) {
				reasons.add(String.format("score dropped %.2f -> %.2f", this.baselineScore, this.currentScore));
			}
			return "DEGRADED: " + String.join(", ", reasons) + "; re-evaluate before trusting it";
		}
		if (this.after == LifecycleState.ACTIVE && this.before == LifecycleState.INCUBATING) {
			return String.format("ACTIVE: baseline eval passed (%.2f); promoted from incubation", this.baselineScore);
		}
		return "STABLE: no drift; the spec matches its recorded baseline";
	}
}
