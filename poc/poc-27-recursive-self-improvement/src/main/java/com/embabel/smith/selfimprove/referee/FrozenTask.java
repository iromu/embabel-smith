/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.referee;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One task of the frozen benchmark, read from
 * {@code src/main/resources/benchmark/tasks/}. This class is a <em>reader</em> of that
 * set, never a writer: the set is a fixed artifact for the run, named as such by
 * {@code src/main/resources/evidence/MUTABILITY.md} under {@code benchmark-task-set}, and
 * the matching rule applied here is the one documented in
 * {@code benchmark/tasks/README.md} rather than a rule restated here.
 *
 * <p>
 * The check is deterministic and makes no model call: every {@code require} phrase must
 * match, at least one {@code anyOf} phrase must match when that list is non-empty, and no
 * {@code forbid} phrase may match. There is no partial credit and nothing else is
 * consulted.
 *
 * @param id the stable task identifier, also carried by the file name
 * @param stage the stage of the described process this task interrogates
 * @param prompt the question put to the candidate
 * @param require phrases that must all appear in the answer
 * @param anyOf phrases of which at least one must appear, empty meaning no condition
 * @param forbid phrases none of which may appear, empty meaning no condition
 * @param check what the check is, in prose, as the task file states its own expectation
 * @author Ivan Rodriguez
 */
public record FrozenTask(String id, String stage, String prompt, List<String> require, List<String> anyOf,
		List<String> forbid, String check) {

	public FrozenTask {
		require = List.copyOf(require);
		anyOf = List.copyOf(anyOf);
		forbid = List.copyOf(forbid);
	}

	/**
	 * Whether an answer passes this task, under the matching rule the task set documents:
	 * case folded, whitespace collapsed, and a phrase counted only when delimited at both
	 * ends by a non-alphanumeric character or the end of the string. That delimiting is
	 * what stops {@code no} from matching inside "not" and {@code push} from matching
	 * "pushes".
	 * @param answer the candidate's answer to {@link #prompt}
	 * @return whether the answer passes every condition of this task
	 */
	public boolean passes(String answer) {
		String text = normalize(answer);
		for (String phrase : this.require) {
			if (!delimited(text, phrase)) {
				return false;
			}
		}
		if (!this.anyOf.isEmpty() && this.anyOf.stream().noneMatch((phrase) -> delimited(text, phrase))) {
			return false;
		}
		return this.forbid.stream().noneMatch((phrase) -> delimited(text, phrase));
	}

	/**
	 * The {@code require} and {@code anyOf} phrases that occur inside this task's own
	 * prompt, which is the invariant {@code benchmark/tasks/README.md} states as "a
	 * candidate cannot pass by echoing the question back". It is checked over the set
	 * before anything is scored, so a task that was authored with its own answer in the
	 * question is caught rather than silently inflating the baseline.
	 * @return the phrases that restate the question; empty when this task honours the
	 * invariant
	 */
	public List<String> restatedPhrases() {
		String text = normalize(this.prompt);
		List<String> restated = new ArrayList<>();
		for (String phrase : this.require) {
			if (delimited(text, phrase)) {
				restated.add(phrase);
			}
		}
		for (String phrase : this.anyOf) {
			if (delimited(text, phrase)) {
				restated.add(phrase);
			}
		}
		return List.copyOf(restated);
	}

	/**
	 * Whether the product's scorer can express this task's check. That scorer tests one
	 * substring, so a task with an {@code anyOf} alternative or a {@code forbid} phrase
	 * cannot be carried into the gate intact — the answer to this question is what the
	 * referee reports alongside the gate's own score instead of pretending the two are
	 * the same measurement.
	 * @return {@code true} when the check is exactly one required phrase and nothing else
	 */
	public boolean expressibleBySubstring() {
		return this.anyOf.isEmpty() && this.forbid.isEmpty() && this.require.size() == 1;
	}

	/**
	 * The form a phrase and an answer are both put into before matching: case-folded,
	 * with runs of whitespace collapsed to one space. Hyphens and slashes are left alone,
	 * as the task set's README requires, so {@code --push} stays matchable.
	 * @param text the text to normalize, possibly null
	 * @return the normalized form, empty for null
	 */
	static String normalize(String text) {
		return (text != null) ? text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim() : "";
	}

	private static boolean delimited(String normalizedText, String phrase) {
		String needle = normalize(phrase);
		if (needle.isEmpty()) {
			return false;
		}
		int at = normalizedText.indexOf(needle);
		while (at >= 0) {
			int end = at + needle.length();
			if (openAt(normalizedText, at - 1) && openAt(normalizedText, end)) {
				return true;
			}
			at = normalizedText.indexOf(needle, at + 1);
		}
		return false;
	}

	/**
	 * Whether a position delimits a phrase: the two ends of the string and any
	 * non-alphanumeric character are delimiters, so hyphens and slashes delimit exactly
	 * as the task set's README specifies.
	 * @param text the normalized text
	 * @param index the position to test, possibly just outside the string
	 * @return whether the position delimits a phrase on this side
	 */
	private static boolean openAt(String text, int index) {
		return (index < 0) || (index >= text.length()) || !Character.isLetterOrDigit(text.charAt(index));
	}

}
