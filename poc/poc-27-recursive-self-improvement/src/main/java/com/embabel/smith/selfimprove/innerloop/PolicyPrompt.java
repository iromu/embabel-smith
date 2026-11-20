/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.innerloop;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A policy prompt: the instruction set a generation mutates, in the form a human can
 * read.
 *
 * <p>
 * In a paid loop this text is what you would hand a model. Here it is compiled by
 * {@link PolicyCompiler} instead, because design D3 and D5 make the inner loop the free
 * one: a detection decision has to cost nothing for hundreds of generations, and a
 * model's answer cannot be reproduced, so a generation could not be attributed to the one
 * mutation that produced it. The prompt is still the seed the mutations act on, which
 * keeps the surface the same shape it would have if a model stood behind it. The grammar
 * is documented once, in {@code src/main/resources/policies/README.md}.
 *
 * @param name the variant name the compiled script is written under
 * @param rules the triggers, in the order the prompt declares them
 * @author Ivan Rodriguez
 */
public record PolicyPrompt(String name, List<PolicyRule> rules) {

	/** The frozen seed prompt the inner loop mutates. */
	public static final String SEED_RESOURCE = "/policies/seed-prompt.txt";

	/**
	 * The frozen seed prompt, read from the classpath. It is frozen for a run: every
	 * generation mutates one operator off this text and off nothing else.
	 * @return the seed prompt.
	 */
	public static PolicyPrompt frozenSeed() {
		return parse(readResource(SEED_RESOURCE));
	}

	/**
	 * Parse prompt text. Blank lines and lines whose first non-blank character is
	 * {@code #} are ignored.
	 * @param text the prompt
	 * @return the parsed prompt
	 * @throws IllegalArgumentException when a line is not a {@code POLICY} or
	 * {@code TRIGGER} the grammar allows, since a silently dropped rule would change what
	 * a generation means
	 */
	public static PolicyPrompt parse(String text) {
		String name = null;
		List<PolicyRule> rules = new ArrayList<>();
		for (String line : text.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) {
				continue;
			}
			List<String> tokens = tokenize(trimmed);
			if ("POLICY".equals(tokens.get(0))) {
				if (tokens.size() != 2) {
					throw new IllegalArgumentException("a POLICY line takes one name, got: " + trimmed);
				}
				name = tokens.get(1);
			}
			else if ("TRIGGER".equals(tokens.get(0))) {
				rules.add(parseRule(trimmed, tokens));
			}
			else {
				throw new IllegalArgumentException("unknown policy prompt line: " + trimmed);
			}
		}
		if (name == null) {
			throw new IllegalArgumentException("the policy prompt declares no POLICY name");
		}
		return new PolicyPrompt(name, List.copyOf(rules));
	}

	/**
	 * This prompt with different rules.
	 * @param replacement the rules to carry
	 * @return a prompt with the same name and these rules.
	 */
	public PolicyPrompt withRules(List<PolicyRule> replacement) {
		return new PolicyPrompt(this.name, List.copyOf(replacement));
	}

	/**
	 * This prompt renamed, which is how a variant keeps its own identity while inheriting
	 * the seed's rules.
	 * @param newName the name to carry
	 * @return a prompt with these rules and that name.
	 */
	public PolicyPrompt withName(String newName) {
		return new PolicyPrompt(newName, this.rules);
	}

	/**
	 * The prompt written back out. Parsing and rendering are inverses, so a mutation
	 * applied to the rules is visible in the text a human reads, and two runs of the same
	 * seed render byte-identical prompts.
	 * @return the prompt text.
	 */
	public String render() {
		StringBuilder out = new StringBuilder("# Generated from the frozen seed prompt by ").append(this.name)
			.append('\n');
		out.append("POLICY ").append(this.name).append('\n');
		for (PolicyRule rule : this.rules) {
			out.append("TRIGGER ").append(rule.trigger()).append(" WHEN ").append(render(rule.when()));
			for (PolicyRule.Condition unless : rule.unless()) {
				out.append(" UNLESS ").append(render(unless));
			}
			out.append('\n');
		}
		return out.toString();
	}

	private static String render(PolicyRule.Condition condition) {
		return condition.hasOperand() ? condition.kind().name().toLowerCase() + " \"" + condition.operand() + "\""
				: condition.kind().name().toLowerCase();
	}

	private static PolicyRule parseRule(String line, List<String> tokens) {
		if (tokens.size() < 4 || !"WHEN".equals(tokens.get(2))) {
			throw new IllegalArgumentException(
					"a TRIGGER line reads TRIGGER <name> WHEN <condition> [UNLESS …], got: " + line);
		}
		String trigger = tokens.get(1);
		PolicyRule.Condition when = parseCondition(tokens, 3, line);
		List<PolicyRule.Condition> unless = new ArrayList<>();
		int at = 4;
		while (at < tokens.size()) {
			if (!"UNLESS".equals(tokens.get(at))) {
				throw new IllegalArgumentException("expected UNLESS in: " + line);
			}
			at += 1;
			unless.add(parseCondition(tokens, at, line));
			at += conditionWidth(tokens, at, line);
		}
		return new PolicyRule(trigger, when, List.copyOf(unless));
	}

	private static PolicyRule.Condition parseCondition(List<String> tokens, int at, String line) {
		if (at >= tokens.size()) {
			throw new IllegalArgumentException("a condition is missing from: " + line);
		}
		PolicyRule.Condition.Kind kind = kind(tokens.get(at), line);
		return takesOperand(kind) ? PolicyRule.condition(kind, tokens.get(at + 1)) : PolicyRule.condition(kind);
	}

	private static int conditionWidth(List<String> tokens, int at, String line) {
		return takesOperand(kind(tokens.get(at), line)) ? 2 : 1;
	}

	private static PolicyRule.Condition.Kind kind(String token, String line) {
		for (PolicyRule.Condition.Kind kind : PolicyRule.Condition.Kind.values()) {
			if (kind.name().equalsIgnoreCase(token)) {
				return kind;
			}
		}
		throw new IllegalArgumentException("unknown condition '" + token + "' in: " + line);
	}

	private static boolean takesOperand(PolicyRule.Condition.Kind kind) {
		return kind == PolicyRule.Condition.Kind.ERROR_PREFIX || kind == PolicyRule.Condition.Kind.ERROR_CONTAINS;
	}

	private static List<String> tokenize(String line) {
		List<String> tokens = new ArrayList<>();
		StringBuilder token = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '"') {
				if (quoted) {
					tokens.add(token.toString());
					token.setLength(0);
				}
				else {
					flush(token, tokens);
				}
				quoted = !quoted;
			}
			else if (!quoted && Character.isWhitespace(c)) {
				flush(token, tokens);
			}
			else {
				token.append(c);
			}
		}
		if (quoted) {
			throw new IllegalArgumentException("unterminated quote in policy prompt line: " + line);
		}
		flush(token, tokens);
		return tokens;
	}

	private static void flush(StringBuilder token, List<String> tokens) {
		if (token.length() > 0) {
			tokens.add(token.toString());
			token.setLength(0);
		}
	}

	private static String readResource(String resource) {
		try (InputStream in = PolicyPrompt.class.getResourceAsStream(resource)) {
			if (in == null) {
				throw new IllegalStateException("policy prompt '" + resource + "' is not on the classpath");
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("could not read the policy prompt '" + resource + "'", ex);
		}
	}

}
