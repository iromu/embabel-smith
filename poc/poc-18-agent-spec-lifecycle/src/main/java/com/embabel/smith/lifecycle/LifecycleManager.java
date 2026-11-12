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

import com.embabel.smith.common.model.AgentSpec;

/**
 * The SpecOps-style proactive re-eval (doc 02 §5 caveat 4, doc 03 §5): re-score a spec,
 * compare its content hash and eval pass-rate against the recorded baseline, and move it
 * through the lifecycle. Pure and deterministic — the scores are inputs, so the whole
 * state machine is verifiable offline without a model.
 *
 * @author Ivan Rodriguez
 */
public final class LifecycleManager {

	private final DriftDetector drift = new DriftDetector();

	private final double activeThreshold; // baseline score required to be promoted to
											// ACTIVE

	private final double driftTolerance; // score-drop tolerance before flagging score
											// drift

	private final double retireFloor; // score below which the contract is violated

	public LifecycleManager(double activeThreshold, double driftTolerance, double retireFloor) {
		this.activeThreshold = activeThreshold;
		this.driftTolerance = driftTolerance;
		this.retireFloor = retireFloor;
	}

	/**
	 * Sane defaults: promote at &ge;0.8, flag a drop of &gt;0.1, retire below 0.2.
	 * @return a manager with the default thresholds.
	 */
	public static LifecycleManager defaults() {
		return new LifecycleManager(0.8, 0.1, 0.2);
	}

	/**
	 * Re-assess a spec. {@code state} is its recorded lifecycle state,
	 * {@code recordedHash} the content hash at baseline, {@code current} the spec as it
	 * is now, and {@code baselineScore} / {@code currentScore} its recorded and
	 * freshly-measured eval pass-rates.
	 * @param spec the spec id being assessed.
	 * @param state the recorded lifecycle state.
	 * @param recordedHash the content hash at baseline.
	 * @param current the spec as it is now.
	 * @param baselineScore the recorded eval pass-rate.
	 * @param currentScore the freshly-measured eval pass-rate.
	 * @return the drift report with the new state and verdict.
	 */
	public DriftReport assess(String spec, LifecycleState state, String recordedHash, AgentSpec current,
			double baselineScore, double currentScore) {
		boolean contentDrift = this.drift.contentDrift(recordedHash, current);
		LifecycleState after = transition(state, contentDrift, baselineScore, currentScore);
		return new DriftReport(spec, state, after, contentDrift, baselineScore, currentScore, recordedHash,
				SpecContentHash.of(current));
	}

	/**
	 * The pure lifecycle transition, given the drift signals and the two scores.
	 * @param state the recorded lifecycle state.
	 * @param contentDrift whether the content hash diverged.
	 * @param baselineScore the recorded eval pass-rate.
	 * @param currentScore the freshly-measured eval pass-rate.
	 * @return the lifecycle state after the transition.
	 */
	public LifecycleState transition(LifecycleState state, boolean contentDrift, double baselineScore,
			double currentScore) {
		if (state == LifecycleState.RETIRED) {
			return LifecycleState.RETIRED; // terminal
		}
		if (currentScore < this.retireFloor) {
			return LifecycleState.RETIRED; // contract violated
		}
		boolean scoreDrift = this.drift.scoreDrift(baselineScore, currentScore, this.driftTolerance);
		if (contentDrift || scoreDrift) {
			// A not-yet-active spec just hasn't validated; flag it but keep it
			// incubating.
			return (state == LifecycleState.INCUBATING) ? LifecycleState.INCUBATING : LifecycleState.DEGRADED;
		}
		if (state == LifecycleState.INCUBATING && baselineScore >= this.activeThreshold) {
			return LifecycleState.ACTIVE; // promoted on a clean baseline
		}
		return state;
	}

}
