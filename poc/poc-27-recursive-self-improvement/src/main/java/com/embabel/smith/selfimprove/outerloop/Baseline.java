/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.outerloop;

import java.nio.file.Path;

import com.embabel.smith.selfimprove.referee.Referee;

/**
 * The pass-rate generation 1 is measured against, and whether that number was measured or
 * handed in.
 *
 * <p>
 * The distinction is the whole point of carrying it. {@code evidence/MUTABILITY.md} fixes
 * the baseline as an artifact a generation may not touch, and {@code Referee} refuses to
 * start a paid outer loop while {@code evidence/baseline.json} is absent. A run that was
 * given a number instead of reading the recorded one is still allowed to run — the
 * offline suite has to, and task 2.4 has not recorded a baseline yet — but it must say so
 * in its own report rather than let an assumed pass-rate read as a measured one.
 *
 * @param passRate the pass-rate a candidate must beat, in {@code [0, 1]}
 * @param source where the number came from, in the run's own words
 * @param measured true when the number was read from the recorded artifact
 * @author Ivan Rodriguez
 */
public record Baseline(double passRate, String source, boolean measured) {

	/**
	 * A baseline read from the recorded artifact.
	 * @param passRate the recorded pass-rate
	 * @param artifact the file it was read from
	 * @return the baseline, marked measured.
	 */
	public static Baseline recorded(double passRate, Path artifact) {
		return new Baseline(passRate, "read from " + artifact, true);
	}

	/**
	 * The artifact a measured baseline is expected to come from, named where the referee
	 * names it rather than restated here.
	 * @return the recorded-baseline path.
	 */
	public static Path recordedArtifact() {
		return Referee.BASELINE;
	}

	/**
	 * A baseline supplied by the caller instead of read from evidence. Valid for driving
	 * the loop's machinery; not valid for claiming an improvement, and the run report
	 * says which of the two it was.
	 * @param passRate the pass-rate to beat
	 * @param why the caller's reason for having to supply it
	 * @return the baseline, marked not measured.
	 */
	public static Baseline injected(double passRate, String why) {
		return new Baseline(passRate, "injected, not measured: " + why, false);
	}

	/**
	 * One-line form for the run report.
	 * @return the pass-rate and its provenance.
	 */
	public String describe() {
		return String.format("%.3f", this.passRate) + " (" + this.source + ")";
	}

}
