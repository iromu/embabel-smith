/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.outerloop;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.embabel.smith.builder.eval.AgentSpecInvoker;
import com.embabel.smith.builder.eval.InvokerFactory;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.selfimprove.innerloop.ModelCallMeter;
import com.embabel.smith.selfimprove.innerloop.PolicyGenerator.MutationOperator;
import com.embabel.smith.selfimprove.referee.FrozenTaskSet;
import com.embabel.smith.selfimprove.referee.Referee;

/**
 * Fixtures for the outer loop's offline tests. No live model call anywhere in this
 * package: a candidate's answers come from a table the test hands in, keyed by the pack
 * the improver named it after.
 *
 * <p>
 * <b>What that measures and what it does not.</b> It measures the loop — lineage,
 * retention, the paired-delta judgement, the budget boundary, which curve is which, what
 * a run is allowed to claim. It does not measure model quality, and no score in these
 * tests is evidence about a model: the answers are typed in. A real score needs the
 * recorded baseline and the measured floor, which tasks 2.4 and 2.5 have not produced.
 *
 * <p>
 * The two answer sets are restated from the referee package's own fixtures rather than
 * imported, because those are package-private and the outer loop is a different package;
 * {@code control/ControlFixtures} restates them for the same reason.
 *
 * @author Ivan Rodriguez
 */
final class OuterLoopFixtures {

	/**
	 * The frozen task ids an unmodified process already passes, so they are never at
	 * stake.
	 */
	static final List<String> ALWAYS_PASSED = List.of("b27-t04", "b27-t05");

	/** The four frozen tasks a variant's answers are discriminated on. */
	static final List<String> DISCRIMINATING = List.of("b27-t01", "b27-t02", "b27-t03", "b27-t06");

	/** Answers that pass every frozen task. */
	private static final Map<String, String> CORRECT = Map.of("b27-t01",
			"No. The scanner's scale is INFO, WARN and CRITICAL, so HIGH is not one of its ratings; only CRITICAL"
					+ " blocks publication.",
			"b27-t02",
			"The default quality floor is 70, and the redraft bound is 2, so the gate considers three"
					+ " drafts in total before it refuses.",
			"b27-t03",
			"The refinement loop rewrites the prompt of the terminal action only, one prompt per round, so"
					+ " the mid-pipeline step named in the evidence is left alone.",
			"b27-t04", "No. The baseline comparison is strict, so a pass-rate equal to the baseline is not progress.",
			"b27-t05",
			"No. Publishing commits the pack, and pushing to the remote needs an explicit --push, which is"
					+ " off by default.",
			"b27-t06", "No. It is a usage error: the command accepts two targets, agent-spec and skill.");

	/**
	 * Answers that miss the four discriminating tasks and pass the two that are never at
	 * stake.
	 */
	private static final Map<String, String> MISS = Map.of("b27-t01",
			"Yes. HIGH is one of the scanner's severity ratings and a finding at that level blocks publication of the"
					+ " pack.",
			"b27-t02",
			"The gate enforces a minimum quality score of 80 and will consider up to five drafts before" + " refusing.",
			"b27-t03",
			"It rewrites the prompt of the failing step named in the evidence, so that mid-pipeline step"
					+ " gets fixed.",
			"b27-t04", "No — the gate is strict, so equal pass-rates are not an improvement.", "b27-t05",
			"No. It commits the change and never pushes; a push needs an explicit --push.", "b27-t06",
			"Yes, --target process is accepted and the command builds the multi-agent process.");

	private OuterLoopFixtures() {
	}

	/**
	 * The module's resources directory, resolved the way the launcher resolves it.
	 * @return the directory holding {@code benchmark/tasks} and {@code evidence}.
	 */
	static Path resources() {
		return Path.of(System.getProperty("smith.self-improve.dir", "src/main/resources"));
	}

	/**
	 * The frozen task set as checked in.
	 * @return the loaded set.
	 */
	static FrozenTaskSet frozenTasks() {
		return FrozenTaskSet.load(resources().resolve("benchmark/tasks"));
	}

	/**
	 * One variant's answers: the two tasks that are never at stake answered correctly,
	 * and the four discriminating tasks answered correctly only for the ids listed.
	 * @param passing the discriminating task ids this variant gets right
	 * @return its answer table.
	 */
	static Map<String, String> answers(String... passing) {
		List<String> got = List.of(passing);
		Map<String, String> out = new LinkedHashMap<>();
		for (String id : DISCRIMINATING) {
			out.put(id, got.contains(id) ? CORRECT.get(id) : MISS.get(id));
		}
		for (String id : ALWAYS_PASSED) {
			out.put(id, CORRECT.get(id));
		}
		return Map.copyOf(out);
	}

	/**
	 * The score {@link #answers} implies: two sixths are always banked, and each
	 * discriminating task listed adds one more.
	 * @param passing the discriminating task ids answered correctly
	 * @return the expected frozen score.
	 */
	static double scoreOf(String... passing) {
		return (ALWAYS_PASSED.size() + passing.length) / 6.0;
	}

	/**
	 * A standing referee over the frozen set, with every call charged to the meter.
	 * @param tasks the frozen set
	 * @param answersByPack variant id to its answers
	 * @param meter the counter to charge
	 * @return the referee.
	 */
	static Referee referee(FrozenTaskSet tasks, Map<String, Map<String, String>> answersByPack, ModelCallMeter meter) {
		return Referee.start(resources(), null, new MeteredInvokerFactory(table(tasks, answersByPack), meter));
	}

	/**
	 * A factory answering each pack from its own row of the table.
	 * @param tasks the frozen set, to map a prompt back to the task it belongs to
	 * @param answersByPack variant id to its answers
	 * @return the factory.
	 */
	static InvokerFactory table(FrozenTaskSet tasks, Map<String, Map<String, String>> answersByPack) {
		return new PerPackTable(tasks, answersByPack);
	}

	/**
	 * Rows for one generation's proposals, keyed on the variant identifiers the improver
	 * itself produced. The identifiers come from production rather than from a literal in
	 * the test, so a test cannot pass by answering a variant that was never proposed.
	 * @param improver the improver whose proposals are being answered
	 * @param operators the operators it is offered
	 * @param passingByOperator operator name to the discriminating tasks that variant
	 * gets right
	 * @return the answer rows, keyed by variant id.
	 */
	static Map<String, Map<String, String>> tableOf(PolicyImprover improver, List<MutationOperator> operators,
			Map<String, String[]> passingByOperator) {
		Map<String, Map<String, String>> rows = new LinkedHashMap<>();
		PolicyImprover.Batch batch = improver.propose(operators);
		Set<String> offered = new LinkedHashSet<>();
		for (PolicyImprover.Proposal proposal : batch.proposals()) {
			String[] passing = passingByOperator.get(proposal.operator());
			if (passing == null) {
				throw new IllegalStateException("the test assigned no answers to " + proposal.operator()
						+ ", so this variant's score would be whatever the fixture defaulted it to");
			}
			offered.add(proposal.operator());
			rows.put(proposal.variantId(), answers(passing));
		}
		// The refusal itself is the run's to report, in `OuterRun.notMeasured()`. But
		// answers the test prepared for an operator that proposed nothing would otherwise
		// vanish, and the test would then be asserting against a variant that never ran.
		Set<String> unanswered = new LinkedHashSet<>(passingByOperator.keySet());
		unanswered.removeAll(offered);
		if (!unanswered.isEmpty()) {
			throw new IllegalStateException("the test assigned answers to " + unanswered + " but " + improver.id()
					+ " proposed no such variant; it refused " + batch.notOffered());
		}
		return rows;
	}

	/**
	 * The proposal one operator produced.
	 * @param batch the generation's proposals
	 * @param operator the operator to find
	 * @return its proposal.
	 */
	static PolicyImprover.Proposal proposalFor(PolicyImprover.Batch batch, MutationOperator operator) {
		return batch.proposals()
			.stream()
			.filter((proposal) -> proposal.operator().equals(operator.name()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException(operator + " offered no proposal to derive from"));
	}

	/**
	 * Answers every prompt of the pack it was asked for, and records what it was asked so
	 * a test can tell "not scored" from "scored and failed".
	 */
	private static final class PerPackTable implements InvokerFactory {

		private final FrozenTaskSet tasks;

		private final Map<String, Map<String, String>> answersByPack;

		private final List<String> asked = new ArrayList<>();

		private PerPackTable(FrozenTaskSet tasks, Map<String, Map<String, String>> answersByPack) {
			this.tasks = tasks;
			this.answersByPack = answersByPack;
		}

		@Override
		public AgentSpecInvoker invokerFor(AgentSpec pack) {
			Map<String, String> answers = this.answersByPack.get(pack.name());
			if (answers == null) {
				throw new IllegalStateException(
						"the test gave no answers for " + pack.name() + ", so its score would be an accident");
			}
			return (input) -> {
				this.asked.add(pack.name() + " " + input);
				return this.tasks.tasks()
					.stream()
					.filter((task) -> task.prompt().equals(input))
					.map((task) -> answers.getOrDefault(task.id(), ""))
					.findFirst()
					.orElse("");
			};
		}

	}

}
