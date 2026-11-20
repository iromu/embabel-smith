/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.innerloop;

/**
 * Renders a policy prompt into a JavaScript detection policy.
 *
 * <p>
 * This is the deterministic stand-in for the model a paid loop would call. It is
 * deliberately boring: the same prompt renders the same bytes, so a generation differs
 * from its parent by exactly the mutation applied to it and by nothing else. That
 * property is what makes a derivative measurable at all — a model's answer varies between
 * runs of the same prompt, and the loop would be reading noise it had no floor for.
 *
 * <p>
 * The emitted script honours the contract in
 * {@code src/main/resources/policies/README.md}: it reads cases on {@code stdin} and
 * prints one verdict per case. Trigger names and matched text are checked or escaped
 * before they reach the source, because a prompt is data and a name that closed the
 * emitted string would be a policy that rewrites its own interpreter.
 *
 * @author Ivan Rodriguez
 */
public final class PolicyCompiler {

	/** The step duration the {@code slow_step} condition calls slow, in milliseconds. */
	public static final long SLOW_STEP_MILLIS = 60_000L;

	private PolicyCompiler() {
	}

	/**
	 * Compile one prompt into a runnable policy.
	 * @param prompt the prompt to render
	 * @return the policy, ready to run through the script engine.
	 */
	public static DetectionPolicy compile(PolicyPrompt prompt) {
		StringBuilder source = new StringBuilder();
		source.append("// Generated from the policy prompt '")
			.append(prompt.name())
			.append("' by PolicyCompiler - deterministic, no model call.\n")
			.append("// Runs only inside POC 26's script engine, on the actor side (design D9).\n")
			.append("const cases = JSON.parse(stdin).cases;\n\n")
			.append("function decide(c) {\n  const reasons = [];\n");
		for (PolicyRule rule : prompt.rules()) {
			source.append("  if (").append(expression(rule.when()));
			for (PolicyRule.Condition unless : rule.unless()) {
				source.append(" && !(").append(expression(unless)).append(')');
			}
			source.append(") {\n    reasons.push(\"").append(triggerLiteral(rule)).append("\");\n  }\n");
		}
		source.append("  return reasons;\n}\n\n")
			.append("for (const c of cases) {\n  const reasons = decide(c);\n  console.log(JSON.stringify(")
			.append("{ runId: c.runId, defect: reasons.length > 0, reasons: reasons }));\n}\n");
		return new DetectionPolicy(prompt.name(), source.toString());
	}

	/**
	 * The JavaScript expression a condition compiles to, over one case.
	 * @param condition the condition to emit
	 * @return the expression.
	 */
	static String expression(PolicyRule.Condition condition) {
		return switch (condition.kind()) {
			case FAILED -> "c.success === false";
			case ERROR_PREFIX -> "(typeof c.error === \"string\" && c.error.startsWith(" + literal(condition) + "))";
			case ERROR_CONTAINS -> "(typeof c.error === \"string\" && c.error.includes(" + literal(condition) + "))";
			case HASH_DIFFERS ->
				"(!!c.pack && !!c.spec_context && c.pack.contentHash !== c.spec_context.sidecarContentHash)";
			case REEVAL_BELOW_BASELINE -> "(!!c.spec_context && typeof c.spec_context.reevalPassRate === \"number\""
					+ " && c.spec_context.reevalPassRate < c.spec_context.baselinePassRate)";
			case SLOW_STEP -> "(c.steps || []).some((s) => s.durationMs > " + SLOW_STEP_MILLIS + ")";
		};
	}

	private static String literal(PolicyRule.Condition condition) {
		if (!condition.hasOperand()) {
			throw new IllegalArgumentException("condition " + condition.kind() + " needs an operand to compile");
		}
		return "\"" + escape(condition.operand()) + "\"";
	}

	private static String escape(String operand) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < operand.length(); i++) {
			char c = operand.charAt(i);
			if (c == '"' || c == '\\') {
				out.append('\\');
			}
			if (Character.isISOControl(c)) {
				throw new IllegalArgumentException("a policy prompt operand cannot contain a control character");
			}
			out.append(c);
		}
		return out.toString();
	}

	private static String triggerLiteral(PolicyRule rule) {
		if (!rule.trigger().matches("[a-z][a-z0-9_]{1,40}")) {
			throw new IllegalArgumentException("trigger '" + rule.trigger() + "' is not a lowercase identifier, so it"
					+ " cannot be written into the generated source");
		}
		return rule.trigger();
	}

}
