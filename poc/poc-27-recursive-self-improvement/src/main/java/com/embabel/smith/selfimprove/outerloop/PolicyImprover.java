/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.outerloop;

import java.util.ArrayList;
import java.util.List;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
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
 * <p>
 * <b>The pack the improver emits is the builder's own</b> (task 4.1). A candidate is
 * {@link #CANDIDATE_PACK} read off disk by {@link BuilderPacks#load(String)} with one
 * step prompt rewritten — {@link #MUTATED_STEP}, the pack's entry step, the one that
 * interpolates the operator's request and so the one an eval task's prompt actually
 * reaches. Step names, declared inputs and outputs, the exported goal and the types are
 * the builder's and unchanged, so the text a mutation operated on is the only thing a
 * generation can move. The candidate carries its variant's identifier as its pack name,
 * because that name is the key both the archive and a test's answer table are keyed on.
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

	/**
	 * The pack a candidate is made from: the builder's single-agent-spec pack, the flow
	 * that mirrors {@code core/BuilderAgent}'s cognitive stages. It is a multi-step pack,
	 * so a candidate is a pack the builder ships rather than the one-step pack this loop
	 * used to assemble here.
	 */
	static final String CANDIDATE_PACK = "builder-single-spec";

	/**
	 * The step a variant's policy text is written into: the pack's entry step, the only
	 * step that declares {@code UserInput} and interpolates it. Mutating any other step
	 * would leave the eval task's prompt nowhere to enter the candidate.
	 */
	static final String MUTATED_STEP = "scopeCoreProcess";

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
		// The candidate is the builder's pack rather than a pack assembled here: read it,
		// name it for the variant, rewrite one step's prompt. The step is the pack's
		// entry
		// step because that is the one which interpolates the operator's request — a
		// candidate whose entry prompt named no input variable asks every distinct task
		// the
		// same question, and that was the whole of this loop's original invalid run.
		// Appending to the step's own prompt keeps the interpolation it already carried,
		// and
		// the field-for-field pass-through in BuilderPacks keeps everything else.
		AgentSpec parent = BuilderPacks.renamed(BuilderPacks.load(CANDIDATE_PACK), variantId);
		ActionStepSpec entry = parent.step(MUTATED_STEP);
		if (entry == null) {
			throw new IllegalStateException(CANDIDATE_PACK + " carries no step '" + MUTATED_STEP + "' to mutate; its"
					+ " steps are " + parent.steps().stream().map(ActionStepSpec::name).toList());
		}
		return new Candidate(variantId,
				BuilderPacks.withStepPrompt(parent, MUTATED_STEP, entry.prompt() + "\n\n" + prompt.render()), null);
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
