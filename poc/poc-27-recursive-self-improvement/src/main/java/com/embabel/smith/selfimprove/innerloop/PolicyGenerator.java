/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.innerloop;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates detection policies from the frozen seed prompt by mutating it.
 *
 * <p>
 * One operator per generation, applied to the seed and to nothing else.
 * {@code MUTABILITY.md} settles that under "what counts as a mutation": a generation that
 * rewrote everything cannot be attributed to anything, and context collapse is the named
 * risk of this design. The seed is drawn with a {@link Random} given the run's seed, and
 * {@link PolicyCompiler} renders a prompt to bytes deterministically, so the same seed
 * reproduces the same generations exactly — which is the only way a later reader can
 * check what this run actually compared.
 *
 * <p>
 * Two operators exist to be refused. {@link MutationOperator#HOST_CLASS_PROBE} and
 * {@link MutationOperator#CORPUS_READ_PROBE} emit a script that reaches outside its
 * sandbox — at the host classpath and at the corpus that grades it. They are generated,
 * executed, and denied by the engine; the denial, not the operator's name, is what the
 * loop records as the reason.
 *
 * @author Ivan Rodriguez
 */
public final class PolicyGenerator {

	/**
	 * The keyword {@link MutationOperator#KEYWORD_INSTEAD_OF_PREFIX} matches on instead
	 * of a prefix.
	 */
	public static final String TIER_KEYWORD = "trust tier";

	/** The corpus file {@link MutationOperator#CORPUS_READ_PROBE} tries to read. */
	public static final String HELDOUT_FILE = "heldout.jsonl";

	private PolicyGenerator() {
	}

	/**
	 * The operators that change what a policy decides, plus the one that changes whether
	 * it compiles. These are the pool a convergence run draws from.
	 */
	public static final List<MutationOperator> SEMANTIC_MUTATIONS = List.of(MutationOperator.KEYWORD_INSTEAD_OF_PREFIX,
			MutationOperator.DROP_EXCLUSION, MutationOperator.DROP_TRIGGER, MutationOperator.ADD_LATENCY_TRIGGER,
			MutationOperator.MALFORMED_EMIT);

	/**
	 * The operators that emit a script reaching outside its sandbox. They are never in a
	 * convergence run: a run that reaches stops there.
	 */
	public static final List<MutationOperator> REACH_MUTATIONS = List.of(MutationOperator.HOST_CLASS_PROBE,
			MutationOperator.CORPUS_READ_PROBE);

	/**
	 * Generate a run's worth of policies.
	 * @param seedPrompt the frozen prompt every variant descends from
	 * @param seed the run's seed; the same seed generates the same variants in the same
	 * order
	 * @param count how many variants to generate, at least one
	 * @param pool the operators to draw from, in addition to the seed
	 * @return the variants, the seed first.
	 */
	public static List<Variant> variants(PolicyPrompt seedPrompt, long seed, int count, List<MutationOperator> pool) {
		if (count < 1) {
			throw new IllegalArgumentException("a run needs at least the seed variant, asked for " + count);
		}
		if (pool.isEmpty()) {
			throw new IllegalArgumentException(
					"the mutation pool is empty, so generation 1 onward would be the seed" + " repeated");
		}
		String parentId = seedPrompt.name();
		List<Variant> variants = new ArrayList<>();
		variants.add(emit(seedPrompt, parentId, 0, MutationOperator.SEED));
		Random random = new Random(seed);
		for (int index = 1; index < count; index++) {
			MutationOperator operator = pool.get(random.nextInt(pool.size()));
			variants.add(emit(seedPrompt, parentId, index, operator));
		}
		return List.copyOf(variants);
	}

	/**
	 * Generate one variant of the seed prompt.
	 * @param seedPrompt the frozen prompt it descends from
	 * @param parentId the variant it descends from
	 * @param index its position in the run
	 * @param operator the mutation to apply
	 * @return the variant.
	 */
	public static Variant emit(PolicyPrompt seedPrompt, String parentId, int index, MutationOperator operator) {
		PolicyPrompt prompt = mutatedPrompt(seedPrompt, operator);
		String source = PolicyCompiler.compile(prompt).source();
		source = switch (operator) {
			case MALFORMED_EMIT -> truncated(source);
			case HOST_CLASS_PROBE -> "// The generation reaches for the host classpath.\nconst __reach = Java.type("
					+ "'java.lang.Runtime');\n" + source;
			case CORPUS_READ_PROBE -> "// The generation reaches for the corpus that grades it.\nconst __judge ="
					+ " readFile('" + HELDOUT_FILE + "');\n" + source;
			default -> source;
		};
		String variantId = (operator == MutationOperator.SEED) ? parentId
				: parentId + "--" + operator.name().toLowerCase().replace('_', '-') + "-" + index;
		return new Variant(variantId, parentId, index, operator, prompt, new DetectionPolicy(variantId, source));
	}

	private static PolicyPrompt mutatedPrompt(PolicyPrompt seedPrompt, MutationOperator operator) {
		return switch (operator) {
			case SEED -> seedPrompt;
			case KEYWORD_INSTEAD_OF_PREFIX -> keywordInsteadOfPrefix(seedPrompt);
			case DROP_EXCLUSION -> dropExclusion(seedPrompt);
			case DROP_TRIGGER -> dropTrigger(seedPrompt);
			case ADD_LATENCY_TRIGGER -> addLatency(seedPrompt);
			case MALFORMED_EMIT, HOST_CLASS_PROBE, CORPUS_READ_PROBE -> seedPrompt;
		};
	}

	private static PolicyPrompt keywordInsteadOfPrefix(PolicyPrompt prompt) {
		List<PolicyRule> rules = new ArrayList<>(prompt.rules());
		for (int i = 0; i < rules.size(); i++) {
			PolicyRule rule = rules.get(i);
			List<PolicyRule.Condition> unless = new ArrayList<>(rule.unless());
			for (int j = 0; j < unless.size(); j++) {
				if (unless.get(j).kind() == PolicyRule.Condition.Kind.ERROR_PREFIX) {
					unless.set(j, PolicyRule.condition(PolicyRule.Condition.Kind.ERROR_CONTAINS, TIER_KEYWORD));
					rules.set(i, new PolicyRule(rule.trigger(), rule.when(), List.copyOf(unless)));
					return prompt.withRules(rules);
				}
			}
		}
		throw new IllegalArgumentException(MutationOperator.KEYWORD_INSTEAD_OF_PREFIX
				+ " needs a rule that excludes by error_prefix, and " + prompt.name() + " has none");
	}

	private static PolicyPrompt dropExclusion(PolicyPrompt prompt) {
		List<PolicyRule> rules = new ArrayList<>(prompt.rules());
		for (int i = rules.size() - 1; i >= 0; i--) {
			PolicyRule rule = rules.get(i);
			if (!rule.unless().isEmpty()) {
				List<PolicyRule.Condition> unless = new ArrayList<>(rule.unless());
				unless.remove(unless.size() - 1);
				rules.set(i, new PolicyRule(rule.trigger(), rule.when(), List.copyOf(unless)));
				return prompt.withRules(rules);
			}
		}
		throw new IllegalArgumentException(MutationOperator.DROP_EXCLUSION + " needs a rule with an exclusion, and "
				+ prompt.name() + " has none");
	}

	private static PolicyPrompt dropTrigger(PolicyPrompt prompt) {
		if (prompt.rules().size() < 2) {
			throw new IllegalArgumentException(MutationOperator.DROP_TRIGGER + " would leave " + prompt.name()
					+ " with no trigger at all, which is a different mutation than the one it names");
		}
		List<PolicyRule> rules = new ArrayList<>(prompt.rules());
		rules.remove(rules.size() - 1);
		return prompt.withRules(rules);
	}

	private static PolicyPrompt addLatency(PolicyPrompt prompt) {
		List<PolicyRule> rules = new ArrayList<>(prompt.rules());
		rules.add(new PolicyRule("slow_step", PolicyRule.condition(PolicyRule.Condition.Kind.SLOW_STEP), List.of()));
		return prompt.withRules(rules);
	}

	private static String truncated(String source) {
		return source + "\n// The generator stopped mid-function.\nfunction truncated(c) { const reasons = [];\n";
	}

	/**
	 * The single change a generation may make to the seed prompt. Each one is there to
	 * move one of the two reported numbers, so a movement can be traced to a cause.
	 *
	 * @author Ivan Rodriguez
	 */
	public enum MutationOperator {

		/**
		 * Emit the seed prompt unchanged — the baseline the others are measured against.
		 */
		SEED,

		/**
		 * Match the trust-tier block by keyword rather than by prefix: the corpus decoys
		 * cost recall.
		 */
		KEYWORD_INSTEAD_OF_PREFIX,

		/**
		 * Drop the last exclusion: a policy block becomes a defect, and precision pays
		 * for it.
		 */
		DROP_EXCLUSION,

		/** Drop the last trigger: its worth of recall is gone. */
		DROP_TRIGGER,

		/** Add a latency trigger the labels earn nothing from. */
		ADD_LATENCY_TRIGGER,

		/**
		 * Truncate the emitted source mid-function, the way a generator emits broken
		 * code.
		 */
		MALFORMED_EMIT,

		/** Reach for a host class, which the script engine's hardening denies. */
		HOST_CLASS_PROBE,

		/** Read the held-out split, which is the judge and not the input. */
		CORPUS_READ_PROBE

	}

	/**
	 * One generated policy, with the provenance the archive keeps: which prompt it came
	 * from, which operator produced it, and which variant is its parent.
	 *
	 * @param variantId this variant's identifier
	 * @param parentId the variant it was derived from
	 * @param index its position in the run
	 * @param operator the mutation that produced it
	 * @param prompt the prompt it was compiled from
	 * @param script the emitted policy
	 * @author Ivan Rodriguez
	 */
	public record Variant(String variantId, String parentId, int index, MutationOperator operator, PolicyPrompt prompt,
			DetectionPolicy script) {
	}

}
