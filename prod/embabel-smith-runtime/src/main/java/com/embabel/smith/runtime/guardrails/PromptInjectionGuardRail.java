/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.guardrails;

import java.util.List;
import java.util.Locale;

import com.embabel.agent.api.validation.guardrails.UserInputGuardRail;
import com.embabel.agent.core.Blackboard;
import com.embabel.common.core.validation.ValidationError;
import com.embabel.common.core.validation.ValidationResult;
import com.embabel.common.core.validation.ValidationSeverity;

/**
 * The run's one global user-input guardrail: an attempt to override the model's
 * instructions is refused before the model is called.
 *
 * <p>
 * <b>How it gets installed, which is why this file contains no registration.</b> The
 * platform's {@code GlobalGuardRailsRegistry} is a {@code @Component} that
 * {@code ScanConfiguration} already reaches — the platform auto-configuration component-
 * scans {@code com.embabel.agent.spi}, and the runtime boots that auto-configuration — so
 * the registry is instantiated by any boot of this module, with no bean declaration of
 * ours. What it instantiates <em>from</em> is read from the environment: the class names
 * listed in {@code embabel.agent.guardrails.user-input}. So the whole of the wiring is
 * this class plus a line in {@code application.yml}, and a test can ask the registry
 * which guardrails it actually made rather than take this file's word for it.
 *
 * <p>
 * <b>Why it is named by a property rather than injected.</b> The registry builds
 * guardrails reflectively, from a comma-separated list of class names, and does it once
 * per boot into a singleton that every LLM operation consults. That is a deployment-time
 * choice about what a deployment refuses, in the same category as the script-engine
 * switch: the class is fixed here, whether it runs at all is theirs. It also means the
 * class needs a no-argument constructor and must not need anything injected — it is
 * created outside the context, so anything it required from the context would be absent.
 *
 * <p>
 * <b>What it catches, stated narrowly on purpose.</b> Matching is normalized —
 * lower-cased with every non-alphanumeric character removed — against a set of known
 * override phrases, so {@code IGNORE   PREVIOUS instructions} and
 * {@code ignore-previous-instructions} are caught where a plain substring match would not
 * be. It is a pattern matcher, not a semantic detector: a rephrasing outside the set gets
 * through, and this class does not pretend otherwise. The set is deliberately small. A
 * {@link ValidationSeverity#CRITICAL} result stops the operation, so a phrase that
 * ordinary instructions also contain would refuse legitimate work — which is why the
 * broad {@code you are now} of the POC this was written against (poc-10) is not here: it
 * appears in ordinary role-assignment prose, and a guardrail that blocks the run's own
 * prompts is worse than one that misses a novel phrasing.
 *
 * @author Ivan Rodriguez
 */
public final class PromptInjectionGuardRail implements UserInputGuardRail {

	/**
	 * Override and exfiltration phrases in normalized form — lower-case, alphanumerics
	 * only — because that is how the input arrives at the comparison. Each is a phrase a
	 * user has no ordinary reason to type.
	 */
	private static final List<String> FORBIDDEN_PATTERNS = List.of("ignorepreviousinstructions", "ignoreallprevious",
			"disregardpreviousinstructions", "disregardtheabove", "overrideyourinstructions", "revealyoursystemprompt",
			"printyourinstructions");

	@Override
	public String getName() {
		return "prompt-injection";
	}

	@Override
	public String getDescription() {
		return "Blocks attempts to override the model's instructions or extract its system prompt";
	}

	/**
	 * The form the patterns are written in: lower-case, alphanumerics only. Obfuscation
	 * by spacing, punctuation or case is what normalization neutralizes, so this is the
	 * comparison form rather than a prettier one.
	 * @param input the text as typed
	 * @return the same text in the form {@link #FORBIDDEN_PATTERNS} is written in
	 */
	public static String normalize(String input) {
		return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	/**
	 * Whether this input carries an override attempt. The blackboard is not consulted:
	 * the decision reads only the text the user supplied, which is what makes it
	 * decidable without running anything.
	 * @param input the user input about to be sent
	 * @param blackboard the operation's blackboard, unused
	 * @return a critical failure naming the attempt, or a pass
	 */
	@Override
	public ValidationResult validate(String input, Blackboard blackboard) {
		if (input == null) {
			return new ValidationResult(true, List.of());
		}
		String normalized = normalize(input);
		for (String pattern : FORBIDDEN_PATTERNS) {
			if (normalized.contains(pattern)) {
				return new ValidationResult(false,
						List.of(new ValidationError("prompt-injection",
								"Input contains an attempt to override the model's instructions and is blocked",
								ValidationSeverity.CRITICAL)));
			}
		}
		return new ValidationResult(true, List.of());
	}

}
