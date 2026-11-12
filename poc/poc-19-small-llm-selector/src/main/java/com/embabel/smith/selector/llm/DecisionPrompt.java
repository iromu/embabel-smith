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

package com.embabel.smith.selector.llm;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.embabel.smith.retrieval.SpecEntry;

/**
 * The two prompt formats POC 19 measures the model under, and the parsers that map its
 * replies back to a candidate id.
 *
 * <p>
 * The in-distribution (ID) format is the model's typed-decision contract: a question,
 * lettered options, and the instruction to answer with the single letter of the correct
 * option — the shape the model was fine-tuned on, where the answer is the first generated
 * token followed by EOS. The out-of-distribution (OOD) format drops the lettered-option
 * contract in favor of a free-form "reply with the spec id" prompt; the ID/OOD gap on
 * identical candidate sets is itself a finding.
 *
 * @author Ivan Rodriguez
 */
public final class DecisionPrompt {

	/** The two measured prompt formats. */
	public enum Format {

		/**
		 * The model's typed-decision contract: lettered options, single-letter answer.
		 */
		ID,
		/** Free-form: numbered options, "reply with only the number" answer. */
		OOD

	}

	private static final Pattern FIRST_NUMBER = Pattern.compile("\\d+");

	private DecisionPrompt() {
	}

	/**
	 * Build the prompt for {@code format}: the query plus one option line per candidate.
	 * @param format the prompt format to build
	 * @param query the user request to select a spec for
	 * @param candidates the spec candidates to offer as options
	 * @return the prompt text
	 */
	public static String build(Format format, String query, List<SpecEntry> candidates) {
		StringBuilder options = new StringBuilder();
		for (int i = 0; i < candidates.size(); i++) {
			SpecEntry candidate = candidates.get(i);
			String label = (format != Format.ID) ? String.valueOf(i + 1) : String.valueOf((char) ('A' + i));
			options.append(label)
				.append(". ")
				.append(candidate.name())
				.append(" — ")
				.append(candidate.description())
				.append('\n');
		}
		if (format == Format.ID) {
			return "Which spec should the agent invoke for this request?\n\n" + "Request: \"" + query + "\"\n\n"
					+ "Options:\n" + options + "\nAnswer with the single letter of the correct option.";
		}
		return "You are choosing which spec to invoke for an agent.\n\n" + "User request: \"" + query + "\"\n\n"
				+ "Specs:\n" + options + "\nReply with only the number of the best spec.";
	}

	/**
	 * Parse a model reply into a candidate id.
	 *
	 * <p>
	 * ID: the first character must be a letter mapping to a candidate in range (the
	 * contract answer is a single letter). OOD: the first integer is a 1-based option
	 * index; when the reply carries no number, the first candidate whose name appears in
	 * the reply is used. Anything else is no selection ({@code null}).
	 * @param format the prompt format the reply was produced under
	 * @param reply the raw model reply
	 * @param candidates the spec candidates the reply refers to
	 * @return the selected candidate id, or {@code null} when no selection
	 */
	public static String parse(Format format, String reply, List<SpecEntry> candidates) {
		if (reply == null || reply.isBlank() || candidates.isEmpty()) {
			return null;
		}
		String text = reply.strip();
		if (format == Format.ID) {
			char first = Character.toUpperCase(text.charAt(0));
			if (first < 'A' || first > 'Z') {
				return null;
			}
			int index = first - 'A';
			return (index >= candidates.size()) ? null : candidates.get(index).id();
		}
		Matcher matcher = FIRST_NUMBER.matcher(text);
		if (matcher.find()) {
			int index = Integer.parseInt(matcher.group()) - 1;
			return (index < 0 || index >= candidates.size()) ? null : candidates.get(index).id();
		}
		String lower = text.toLowerCase();
		for (SpecEntry candidate : candidates) {
			if (lower.contains(candidate.name().toLowerCase())) {
				return candidate.id();
			}
		}
		return null;
	}

}
