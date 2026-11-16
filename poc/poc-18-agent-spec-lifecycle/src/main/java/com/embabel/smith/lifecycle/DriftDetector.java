/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
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
