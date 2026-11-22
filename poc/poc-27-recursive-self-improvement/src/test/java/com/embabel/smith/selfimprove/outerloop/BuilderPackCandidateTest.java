/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.outerloop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import com.embabel.smith.builder.eval.InvokerFactory;
import com.embabel.smith.builder.improve.GateChain;
import com.embabel.smith.builder.improve.GateResult;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.selfimprove.innerloop.PolicyPrompt;
import com.embabel.smith.selfimprove.referee.Candidate;

/**
 * Task 4.1: a candidate is a builder pack with mutated step prompts, not a pack assembled
 * in Java.
 *
 * <p>
 * This is a new class because nothing in this package looked at the candidate pack
 * before: every other outer-loop test is about the loop's mechanics — lineage, retention,
 * the paired delta, the budget boundary — and what a proposal carried was never asserted.
 * That is exactly how a hand-built one-step pack sat in {@code candidate(...)} while the
 * loop around it stayed green, so the property this change establishes needs a home of
 * its own rather than one more line in a loop test.
 *
 * <p>
 * The assertion that carries the task is the second one: a variant differs from its
 * parent in one step's prompt and in nothing else. Step names, their declared inputs and
 * outputs, the exported goal and the declared types all have to stay the builder's,
 * because a candidate that also changed the pack's structure is a candidate whose score
 * cannot be attributed to the mutation the run says it applied.
 *
 * <p>
 * Offline, and no score here is evidence about a model: the gate test hands in an invoker
 * that answers from a lambda. See {@link OuterLoopFixtures} for what these answers mean.
 *
 * @author Ivan Rodriguez
 */
class BuilderPackCandidateTest {

	/**
	 * The candidate pack's entry prompt exactly as the builder ships it, which is the
	 * prefix a mutation may only ever extend.
	 */
	private static final String SHIPPED_ENTRY_PROMPT = BuilderPacks.load(PolicyImprover.CANDIDATE_PACK)
		.step(PolicyImprover.MUTATED_STEP)
		.prompt();

	@Test
	void aCandidateIsTheBuilderPackReadOffDiskRatherThanAPackAssembledHere() {
		AgentSpec shipped = BuilderPacks.load(PolicyImprover.CANDIDATE_PACK);
		AgentSpec candidate = seed().spec();

		// The shape, taken from the builder's own files rather than from a literal here:
		// every
		// step, the exported goal and every declared type are the pack's.
		assertThat(candidate.steps().stream().map(ActionStepSpec::name).toList())
			.isEqualTo(shipped.steps().stream().map(ActionStepSpec::name).toList());
		assertThat(candidate.goal()).isEqualTo(shipped.goal());
		assertThat(candidate.types()).isEqualTo(shipped.types());
		assertThat(candidate.version()).isEqualTo(shipped.version());

		// And the two things the hand-built candidate was, and no longer is: one step,
		// and a
		// goal whose output was BackgroundMessage — design D5, whose polymorphic parts
		// cost the
		// last run what it cost.
		assertThat(shipped.steps()).as("a one-step pack would let this pass on a pack no builder ships")
			.hasSizeGreaterThan(1);
		assertThat(candidate.steps()).filteredOn((step) -> "detect".equals(step.name())).isEmpty();
		assertThat(candidate.goalOutputType()).isNotEqualTo("BackgroundMessage");
	}

	@Test
	void everyVariantDiffersFromItsParentInOneStepPromptAndInNothingElse() {
		PolicyImprover improver = PolicyImprover.surviving(PolicyPrompt.frozenSeed());
		PolicyImprover.Batch batch = improver.propose(PolicyImprover.SCORED_MUTATIONS);
		Candidate parent = batch.proposals().get(0).candidate();

		for (PolicyImprover.Proposal proposal : batch.proposals().subList(1, batch.proposals().size())) {
			assertDiffersOnlyInStepPrompt(parent.spec(), proposal.candidate().spec(), proposal.variantId());
			assertThat(proposal.candidate().spec().step(PolicyImprover.MUTATED_STEP).prompt())
				.as("%s carries the policy text its operator moved", proposal.variantId())
				.contains(proposal.prompt().render());
		}

		// Against the shipped pack too. The sibling comparison above cannot see a
		// mutation that
		// rewrites a step field the same way on every candidate, because both sides of it
		// go
		// through the same mutator and so match whatever it did. The builder's own files
		// are the
		// one side that does not, and a candidate may differ from them in its name —
		// which is how
		// a variant is identified — and in the one prompt, and nowhere else.
		for (PolicyImprover.Proposal proposal : batch.proposals()) {
			assertThat(stepDifferences(BuilderPacks.load(PolicyImprover.CANDIDATE_PACK), proposal.candidate().spec()))
				.as("what %s changed in the shipped pack", proposal.variantId())
				.containsExactly("pack.name", PolicyImprover.MUTATED_STEP + ".prompt");
		}
	}

	@Test
	void theMutatedStepStillInterpolatesTheInputItDeclares() {
		// The regression this exists for: an entry prompt that names no variable never
		// sends
		// the eval task's prompt, so every distinct task asks the model the same question
		// and
		// the run's whole budget buys one question asked once per task.
		String reference = interpolationFor(
				BuilderPacks.load(PolicyImprover.CANDIDATE_PACK).step(PolicyImprover.MUTATED_STEP));
		PolicyImprover.Batch batch = PolicyImprover.surviving(PolicyPrompt.frozenSeed())
			.propose(PolicyImprover.SCORED_MUTATIONS);

		assertThat(batch.proposals()).as("the pack's own entry prompt, interpolation and all, survives every mutation")
			.allSatisfy((proposal) -> assertThat(proposal.candidate().spec().step(PolicyImprover.MUTATED_STEP).prompt())
				.contains(reference));
	}

	@Test
	void eachVariantCarriesItsOwnNameSoTheArchiveAndTheAnswerTableCanTellThemApart() {
		PolicyImprover.Batch batch = PolicyImprover.surviving(PolicyPrompt.frozenSeed())
			.propose(PolicyImprover.SCORED_MUTATIONS);
		String shippedVersion = BuilderPacks.load(PolicyImprover.CANDIDATE_PACK).version();

		assertThat(batch.proposals())
			.allSatisfy((proposal) -> assertThat(proposal.candidate().spec().name()).isEqualTo(proposal.variantId()));
		assertThat(batch.proposals()).map((proposal) -> proposal.candidate().spec().name()).doesNotHaveDuplicates();
		assertThat(batch.proposals())
			.as("the rename is a name and not a new version: lineage is carried by the variant id")
			.allSatisfy((proposal) -> assertThat(proposal.candidate().spec().version()).isEqualTo(shippedVersion));
	}

	@Test
	void theGateTakesAMutatedPackPastItsDeterministicArms() {
		// The two arms that can stop a candidate before any eval spend: validation (an
		// undeclared prompt reference, an unexported goal) and the security scan (a
		// CRITICAL
		// finding blocks before a call is bought). The pack on disk, unmutated, is the
		// control,
		// so the pair cannot pass on two empty findings lists.
		GateChain gate = new GateChain(answers("No — the scanner rates findings from INFO to CRITICAL."),
				OuterLoopFixtures.frozenTasks().toEvalTasks());

		for (AgentSpec pack : List.of(BuilderPacks.load(PolicyImprover.CANDIDATE_PACK), seed().spec())) {
			GateResult result = gate.gate(pack, 0.5);
			assertThat(result.stage()).as("the gate stopped %s at", pack.name()).isNotIn("validation", "security");
		}
	}

	/**
	 * The mutation discipline: the same in every field a step carries except one prompt.
	 * @param parent the pack the variant was built from
	 * @param child the candidate
	 * @param variantId what to name the failure with
	 */
	private static void assertDiffersOnlyInStepPrompt(AgentSpec parent, AgentSpec child, String variantId) {
		// The rename is lineage, not mutation: every variant carries its own identifier
		// as its
		// pack name, so a sibling differs from its parent by that and by the one prompt.
		assertThat(stepDifferences(parent, child)).as("what %s changed in its parent's pack", variantId)
			.containsExactly("pack.name", PolicyImprover.MUTATED_STEP + ".prompt");
		assertThat(child.goal()).isEqualTo(parent.goal());
		assertThat(child.types()).isEqualTo(parent.types());
		assertThat(child.manifest().defaultPlanner()).isEqualTo(parent.manifest().defaultPlanner());
		assertThat(child.manifest().defaultModel()).isEqualTo(parent.manifest().defaultModel());
		assertThat(child.manifest().version()).isEqualTo(parent.manifest().version());
		// Against the pack on disk, not against the batch's parent: proposals().get(0) is
		// itself
		// a candidate — the surviving policy applied to the pack — so a sibling's prompt
		// cannot
		// start with the parent's whole prompt, and asserting that would fail for a
		// reason that
		// has nothing to do with the discipline under test. The prefix that must survive
		// is the
		// step's own shipped instruction.
		for (AgentSpec pack : List.of(parent, child)) {
			assertThat(pack.step(PolicyImprover.MUTATED_STEP).prompt())
				.as("the step's own instruction is prepended to, not replaced by, the policy")
				.startsWith(SHIPPED_ENTRY_PROMPT);
		}
	}

	/**
	 * Every difference between two packs, named: one entry per step field that moved,
	 * plus {@code pack.name} when the pack itself was renamed. Asserted against different
	 * allowed sets by the two callers — a variant's parent has the shipped pack's name,
	 * the shipped pack itself does not — which is why the comparison returns names rather
	 * than asserting one verdict of its own.
	 * @param parent the pack as it was
	 * @param child the pack as it became
	 * @return the fields that differ, in the order they were read
	 */
	private static List<String> stepDifferences(AgentSpec parent, AgentSpec child) {
		List<String> differences = new ArrayList<>();
		if (!parent.name().equals(child.name())) {
			differences.add("pack.name");
		}
		if (parent.steps().size() != child.steps().size()) {
			differences.add("step count " + parent.steps().size() + " -> " + child.steps().size());
		}
		for (int at = 0; at < Math.min(parent.steps().size(), child.steps().size()); at++) {
			ActionStepSpec before = parent.steps().get(at);
			ActionStepSpec after = child.steps().get(at);
			complain(differences, before, after, "name", before.name(), after.name());
			complain(differences, before, after, "stepType", before.stepType(), after.stepType());
			complain(differences, before, after, "description", before.description(), after.description());
			complain(differences, before, after, "inputTypeNames", before.inputTypeNames(), after.inputTypeNames());
			complain(differences, before, after, "outputTypeName", before.outputTypeName(), after.outputTypeName());
			complain(differences, before, after, "pre", before.pre(), after.pre());
			complain(differences, before, after, "post", before.post(), after.post());
			complain(differences, before, after, "cost", before.cost(), after.cost());
			complain(differences, before, after, "value", before.value(), after.value());
			complain(differences, before, after, "canRerun", before.canRerun(), after.canRerun());
			complain(differences, before, after, "nullable", before.nullable(), after.nullable());
			complain(differences, before, after, "toolGroups", before.toolGroups(), after.toolGroups());
			if (!Objects.equals(before.prompt(), after.prompt())) {
				differences.add(before.name() + ".prompt");
			}
		}
		return differences;
	}

	private static void complain(List<String> differences, ActionStepSpec before, ActionStepSpec after, String field,
			Object beforeValue, Object afterValue) {
		if (!Objects.equals(beforeValue, afterValue)) {
			differences.add(before.name() + "." + field + " (" + beforeValue + " -> " + afterValue + ")");
		}
	}

	/**
	 * How a step's first declared input has to be written in its own prompt for the
	 * platform to send it: the decapitalized type name, then its content.
	 * @param step the step to derive the reference from
	 * @return the interpolation, e.g. {@code {{userInput.content}}}.
	 */
	private static String interpolationFor(ActionStepSpec step) {
		String type = step.inputTypeNames().get(0);
		String variable = Character.toLowerCase(type.charAt(0)) + type.substring(1);
		return "{{" + variable + ".content}}";
	}

	private static Candidate seed() {
		return PolicyImprover.surviving(PolicyPrompt.frozenSeed()).propose(List.of()).proposals().get(0).candidate();
	}

	private static InvokerFactory answers(String answer) {
		return (pack) -> (input) -> answer;
	}

}
