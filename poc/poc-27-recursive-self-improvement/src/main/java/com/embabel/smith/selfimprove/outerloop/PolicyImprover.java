/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.outerloop;

import java.util.ArrayList;
import java.util.List;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.selfimprove.innerloop.PolicyGenerator;
import com.embabel.smith.selfimprove.innerloop.PolicyGenerator.MutationOperator;
import com.embabel.smith.selfimprove.innerloop.PolicyPrompt;
import com.embabel.smith.selfimprove.referee.Candidate;

/**
 * The improver the outer loop runs: a policy prompt that proposes candidate packs, and
 * that derives its own successor.
 *
 * <p>
 * This is the {@code improver-pack} surface of {@code evidence/MUTABILITY.md} — "the
 * improver itself, expressed as a pack the runtime executes" — and generation 1's
 * instance is literally the policy the offline inner loop converged on:
 * {@link #surviving} takes the prompt that loop left behind and puts it to work as the
 * outer loop's first improver. Generation 2's improver is not chosen by a human and not
 * re-derived from the seed: {@link #derive} builds it out of the variant generation 1
 * selected, so the lineage the archive records is the lineage that actually ran.
 *
 * <p>
 * What the improver emits is a pack carrying its own prompt, which is what makes a
 * generation attributable: one {@link MutationOperator} per variant, applied to the
 * improver's prompt and to nothing else, exactly as the inner loop enforces it.
 *
 * @author Ivan Rodriguez
 */
public final class PolicyImprover {

	/**
	 * The operators the outer loop offers. The three that are absent are absent on
	 * purpose: {@code MALFORMED_EMIT} breaks the emitted script, and the referee never
	 * executes a candidate's script, so offering it would score a broken policy the same
	 * as a sound one and call that a measurement. The two reach-probes are the inner
	 * loop's sandbox tests, not improvements, and they stay there.
	 */
	public static final List<MutationOperator> SCORED_MUTATIONS = List.of(MutationOperator.KEYWORD_INSTEAD_OF_PREFIX,
			MutationOperator.DROP_EXCLUSION, MutationOperator.DROP_TRIGGER, MutationOperator.ADD_LATENCY_TRIGGER);

	/** Prefix every derived improver's id carries. */
	private static final String DERIVED_PREFIX = "b27-gen";

	private final String id;

	private final String parentVariantId;

	private final int generation;

	private final PolicyPrompt prompt;

	private PolicyImprover(String id, String parentVariantId, int generation, PolicyPrompt prompt) {
		this.id = id;
		this.parentVariantId = parentVariantId;
		this.generation = generation;
		this.prompt = prompt;
	}

	/**
	 * Generation 1's improver: the policy that survived the offline inner loop, used as
	 * it stands.
	 * @param survivingPolicy the prompt the inner loop converged on
	 * @return an improver with no parent variant, because nothing preceded it.
	 */
	public static PolicyImprover surviving(PolicyPrompt survivingPolicy) {
		return new PolicyImprover(survivingPolicy.name(), null, 1, survivingPolicy);
	}

	/**
	 * The next generation's improver, built from the variant this generation selected.
	 * @param chosen the variant that won this generation
	 * @return an improver whose prompt is the chosen variant's prompt, renamed so its
	 * variants cannot collide with the ones already in the archive.
	 */
	public PolicyImprover derive(Proposal chosen) {
		int next = this.generation + 1;
		String derivedId = DERIVED_PREFIX + next;
		return new PolicyImprover(derivedId, chosen.variantId(), next, chosen.prompt().withName(derivedId));
	}

	/**
	 * Propose this generation's candidates: the improver's own policy unchanged, then one
	 * variant per operator offered.
	 *
	 * <p>
	 * The unchanged policy is included because it is the comparison, not a wasted call:
	 * it is what a candidate that changed nothing looks like, and a loop that never
	 * scores one cannot notice that its floor is doing no work.
	 * @param operators the mutations to offer, in order
	 * @return the proposals, plus the operators that refused and why
	 */
	public Batch propose(List<MutationOperator> operators) {
		if (operators.contains(MutationOperator.SEED)) {
			throw new IllegalArgumentException("SEED is not a mutation to offer: the unchanged policy is always"
					+ " proposed once by this method, and offering it again would put two variants with the same"
					+ " identifier in front of an archive that refuses to overwrite");
		}
		List<Proposal> offered = new ArrayList<>();
		List<String> refused = new ArrayList<>();
		offered.add(new Proposal(this.id, this.parentVariantId, MutationOperator.SEED.name(), this.prompt,
				candidate(this.id, this.prompt)));
		int index = 1;
		for (MutationOperator operator : operators) {
			try {
				PolicyGenerator.Variant variant = PolicyGenerator.emit(this.prompt, this.id, index, operator);
				offered.add(new Proposal(variant.variantId(), this.id, operator.name(), variant.prompt(),
						candidate(variant.variantId(), variant.prompt())));
				index++;
			}
			catch (IllegalArgumentException notApplicable) {
				refused.add(operator + ": " + notApplicable.getMessage());
			}
		}
		return new Batch(List.copyOf(offered), List.copyOf(refused));
	}

	/**
	 * This improver's identifier, which is also the archive key of the unchanged variant
	 * it proposes.
	 * @return the id.
	 */
	public String id() {
		return this.id;
	}

	/**
	 * The variant this improver was derived from.
	 * @return the parent variant id, or null for generation 1.
	 */
	public String parentVariantId() {
		return this.parentVariantId;
	}

	/**
	 * Which generation this improver runs.
	 * @return the one-based generation number.
	 */
	public int generation() {
		return this.generation;
	}

	/**
	 * The prompt this improver mutates.
	 * @return the policy prompt.
	 */
	public PolicyPrompt prompt() {
		return this.prompt;
	}

	private static Candidate candidate(String variantId, PolicyPrompt prompt) {
		ActionStepSpec step = ActionStepSpec.action("detect", "step detect", List.of("UserInput"), "BackgroundMessage",
				prompt.render());
		AgentSpec pack = new AgentSpec(new AgentSpecManifest(variantId, "1.0.0"),
				GoalStepSpec.exportedGoal(variantId, "goal " + variantId, "BackgroundMessage"), List.of(step),
				List.of());
		return new Candidate(variantId, pack, null);
	}

	/**
	 * One candidate this improver proposed, with the lineage the archive keeps.
	 *
	 * @param variantId this proposal's identifier
	 * @param parentVariantId the variant it descends from, or null for a root
	 * @param operator the mutation that produced it, or {@code SEED} for the unchanged
	 * policy
	 * @param prompt the policy prompt it carries
	 * @param candidate the pack handed to the referee
	 * @author Ivan Rodriguez
	 */
	public record Proposal(String variantId, String parentVariantId, String operator, PolicyPrompt prompt,
			Candidate candidate) {
	}

	/**
	 * A generation's proposals, with the operators that declined to apply. A mutation
	 * that cannot be applied to this prompt is reported, not silently dropped: a run that
	 * quietly offered two variants instead of four is a run whose comparisons are not the
	 * ones its budget paid for.
	 *
	 * @param proposals the variants offered, the improver's own policy first
	 * @param notOffered one entry per operator that refused, saying why
	 * @author Ivan Rodriguez
	 */
	public record Batch(List<Proposal> proposals, List<String> notOffered) {
	}

}
