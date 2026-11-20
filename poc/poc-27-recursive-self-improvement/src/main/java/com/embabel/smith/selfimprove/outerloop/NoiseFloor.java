/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.outerloop;

import java.nio.file.Path;

import com.embabel.smith.selfimprove.referee.Referee;

/**
 * The smallest score difference this benchmark cannot tell apart from noise, and whether
 * that threshold was measured or handed in.
 *
 * <p>
 * Improvement is judged as a paired delta against this number rather than against zero,
 * because the frozen task set is six tasks long: one task flipping is a 0.167 move, and a
 * model that answers a borderline task differently on a second run produces exactly that
 * move for free. Without a floor, run-to-run jitter reads as progress and the loop
 * hill-climbs on noise.
 *
 * <p>
 * The floor is carried as a value rather than read from {@code evidence/noise-floor.json}
 * because that file has not been measured yet (task 2.5 is open), and the referee refuses
 * to start a paid outer loop until it exists. A caller that supplies the number is asking
 * the loop to exercise its selection logic, not asking it to certify an improvement — and
 * {@link #measured()} is how the run report says which one happened.
 *
 * @param delta the minimum score move that counts as improvement
 * @param source where the number came from, in the run's own words
 * @param measured true when the number was read from the recorded artifact
 * @author Ivan Rodriguez
 */
public record NoiseFloor(double delta, String source, boolean measured) {

	/**
	 * A floor read from the recorded artifact.
	 * @param delta the measured minimum detectable move
	 * @param artifact the file it was read from
	 * @return the floor, marked measured.
	 */
	public static NoiseFloor measured(double delta, Path artifact) {
		return new NoiseFloor(checked(delta), "read from " + artifact, true);
	}

	/**
	 * A floor supplied by the caller instead of measured from repeated runs.
	 * @param delta the minimum score move to require
	 * @param why the caller's reason for having to supply it
	 * @return the floor, marked not measured.
	 */
	public static NoiseFloor injected(double delta, String why) {
		return new NoiseFloor(checked(delta), "injected, not measured: " + why, false);
	}

	/**
	 * The artifact a measured floor is expected to come from, named where the referee
	 * names it rather than restated here.
	 * @return the recorded-noise-floor path.
	 */
	public static Path recordedArtifact() {
		return Referee.NOISE_FLOOR;
	}

	/**
	 * One-line form for the run report.
	 * @return the threshold and its provenance.
	 */
	public String describe() {
		return String.format("%.3f", this.delta) + " (" + this.source + ")";
	}

	private static double checked(double delta) {
		if (delta < 0.0 || delta >= 1.0) {
			throw new IllegalArgumentException("Noise floor must be in [0, 1), got " + delta);
		}
		return delta;
	}

}
