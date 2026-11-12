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
 * The two drift signals for a spec: <em>content drift</em> (the pack's content hash
 * diverged from the recorded baseline) and <em>score drift</em> (its eval pass-rate
 * dropped below the baseline by more than a tolerance). Both are the "spec silently
 * breaks" failure mode (doc 02 §5 caveat 4).
 *
 * @author Ivan Rodriguez
 */
public final class DriftDetector {

	/**
	 * True when the spec's content no longer matches the hash recorded at baseline.
	 * @param recordedHash the content hash recorded at baseline.
	 * @param current the spec as it is now.
	 * @return true when the content has drifted.
	 */
	public boolean contentDrift(String recordedHash, AgentSpec current) {
		return !recordedHash.equals(SpecContentHash.of(current));
	}

	/**
	 * True when the current score has fallen below the baseline by more than
	 * {@code tolerance}.
	 * @param baselineScore the eval pass-rate recorded at baseline.
	 * @param currentScore the freshly-measured eval pass-rate.
	 * @param tolerance the allowed score drop before flagging drift.
	 * @return true when the score has drifted.
	 */
	public boolean scoreDrift(double baselineScore, double currentScore, double tolerance) {
		return currentScore < baselineScore - tolerance;
	}

}
