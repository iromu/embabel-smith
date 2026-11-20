/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.innerloop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How a detection policy scored against one corpus split, as a confusion matrix.
 *
 * <p>
 * Precision and recall are the two numbers this reports and there is deliberately no
 * third one. A loop that wants to believe failures exist buys recall with precision, and
 * a single F-shaped score hides exactly that trade — so the spec asks for the two
 * separately and this record holds the line by not offering a blend to sum into. The four
 * counts stay public for the same reason: the trade is only legible from them.
 *
 * @param split which corpus split was scored
 * @param cases the number of lines scored
 * @param truePositives defects the policy flagged
 * @param falsePositives non-defects the policy flagged
 * @param falseNegatives defects the policy missed
 * @param trueNegatives non-defects the policy left alone
 * @param precision flagged lines that were genuine defects; zero when the policy flagged
 * nothing, since a policy that never raises its hand has no precision to claim
 * @param recall genuine defects the policy caught
 * @author Ivan Rodriguez
 */
public record SplitScore(String split, int cases, int truePositives, int falsePositives, int falseNegatives,
		int trueNegatives, double precision, double recall) {

	/**
	 * Score a policy's verdicts against the labels.
	 * @param split the split that was scored
	 * @param lines the corpus lines
	 * @param verdicts the policy's answers, one per line
	 * @return the confusion matrix with precision and recall
	 * @throws IllegalArgumentException when the policy did not answer every line exactly
	 * once, or answered a line that was not in the split — a missing verdict would
	 * otherwise be scored as a correct rejection of a defect the policy never saw
	 */
	public static SplitScore of(String split, List<TraceCase> lines, List<PolicyRun.Verdict> verdicts) {
		Map<String, PolicyRun.Verdict> byRunId = index(verdicts, split);
		int tp = 0;
		int fp = 0;
		int fn = 0;
		int tn = 0;
		for (TraceCase line : lines) {
			PolicyRun.Verdict verdict = byRunId.remove(line.runId());
			if (verdict == null) {
				throw new IllegalArgumentException("the policy scored against '" + split + "' never answered line '"
						+ line.runId() + "'; an unanswered line is not a clean rejection");
			}
			boolean flagged = verdict.defect();
			boolean defect = line.labelledDefect();
			if (flagged && defect) {
				tp++;
			}
			else if (flagged) {
				fp++;
			}
			else if (defect) {
				fn++;
			}
			else {
				tn++;
			}
		}
		if (!byRunId.isEmpty()) {
			throw new IllegalArgumentException("the policy scored against '" + split + "' answered lines that are not"
					+ " in it: " + byRunId.keySet());
		}
		int flagged = tp + fp;
		int genuine = tp + fn;
		return new SplitScore(split, lines.size(), tp, fp, fn, tn, (flagged == 0) ? 0.0 : (double) tp / flagged,
				(genuine == 0) ? 0.0 : (double) tp / genuine);
	}

	private static Map<String, PolicyRun.Verdict> index(List<PolicyRun.Verdict> verdicts, String split) {
		Map<String, PolicyRun.Verdict> byRunId = new HashMap<>();
		List<String> repeats = new ArrayList<>();
		for (PolicyRun.Verdict verdict : verdicts) {
			if (byRunId.put(verdict.runId(), verdict) != null) {
				repeats.add(verdict.runId());
			}
		}
		if (!repeats.isEmpty()) {
			throw new IllegalArgumentException(
					"the policy scored against '" + split + "' answered the same line twice: " + repeats);
		}
		return byRunId;
	}

	/**
	 * The one-line form the loop prints and the per-generation record carries.
	 * @return the two numbers, never a blend of them.
	 */
	public String describe() {
		return this.split + ": precision " + this.precision + " recall " + this.recall + " (tp " + this.truePositives
				+ " fp " + this.falsePositives + " fn " + this.falseNegatives + " tn " + this.trueNegatives + ")";
	}

}
