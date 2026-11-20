/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.innerloop;

import java.util.List;

/**
 * What happened when one policy was run over one corpus split.
 *
 * <p>
 * The distinction that matters here is between {@link Status#RAN} and everything else. A
 * script that failed to compile, timed out, was denied by the sandbox, or printed
 * something that is not a verdict did not answer "not a defect" — it answered nothing.
 * Treating that as a clean window would hand a broken generation a perfect score, so only
 * {@code RAN} runs reach the scorer.
 *
 * @param policyName the policy that ran
 * @param status whether it ran, failed, or was refused before running
 * @param verdicts the per-case decisions, in the order the script printed them; empty
 * unless the policy ran
 * @param diagnostic the engine's own words for a run that produced no verdicts, or null
 * when it ran
 * @param durationMillis how long the run took, measured by {@link PolicyRunner}
 * @author Ivan Rodriguez
 */
public record PolicyRun(String policyName, Status status, List<Verdict> verdicts, String diagnostic,
		long durationMillis) {

	/**
	 * A scored run.
	 * @param policyName the policy that ran
	 * @param verdicts one per case
	 * @param durationMillis the measured run time
	 * @return the run.
	 */
	public static PolicyRun ran(String policyName, List<Verdict> verdicts, long durationMillis) {
		return new PolicyRun(policyName, Status.RAN, List.copyOf(verdicts), null, durationMillis);
	}

	/**
	 * A run that produced nothing to score.
	 * @param policyName the policy that ran
	 * @param diagnostic the engine's explanation
	 * @param durationMillis the measured run time
	 * @return the run.
	 */
	public static PolicyRun failed(String policyName, String diagnostic, long durationMillis) {
		return new PolicyRun(policyName, Status.FAILED, List.of(), diagnostic, durationMillis);
	}

	/**
	 * A run the engine refused before executing the script.
	 * @param policyName the policy that was refused
	 * @param reason the engine's reason
	 * @return the run.
	 */
	public static PolicyRun denied(String policyName, String reason) {
		return new PolicyRun(policyName, Status.DENIED, List.of(), reason, 0L);
	}

	/**
	 * Whether this run may be scored.
	 * @return true when the policy ran and answered.
	 */
	public boolean ran() {
		return this.status == Status.RAN;
	}

	/**
	 * Whether a policy run produced verdicts worth scoring.
	 *
	 * @author Ivan Rodriguez
	 */
	public enum Status {

		/** It ran, exited zero, and answered every case. */
		RAN,

		/**
		 * It ran and failed: a syntax error, a non-zero exit, a sandbox denial, a bad
		 * answer.
		 */
		FAILED,

		/** The engine refused it before execution. */
		DENIED

	}

	/**
	 * One case's verdict.
	 *
	 * @param runId the case this verdict is about
	 * @param defect whether the policy calls it a genuine defect
	 * @param reasons the triggers that fired, empty when it does not
	 * @author Ivan Rodriguez
	 */
	public record Verdict(String runId, boolean defect, List<String> reasons) {

		/**
		 * A verdict that no trigger fired for.
		 * @param runId the case it is about
		 * @return the clean verdict
		 */
		public static Verdict clean(String runId) {
			return new Verdict(runId, false, List.of());
		}
	}

}
