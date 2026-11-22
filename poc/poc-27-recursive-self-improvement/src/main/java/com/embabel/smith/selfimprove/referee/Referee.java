/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.referee;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.embabel.smith.builder.eval.AgentSpecInvoker;
import com.embabel.smith.builder.eval.InvokerFactory;
import com.embabel.smith.builder.improve.GateChain;
import com.embabel.smith.builder.improve.GateResult;
import com.embabel.smith.common.model.AgentSpec;

/**
 * The referee: the product's own gate, constructed over the frozen task set through the
 * seams that already exist, held by a process that is started by hand and never by the
 * code under test.
 *
 * <p>
 * <b>No prod code is changed to build it.</b> {@code GateChain}'s two-argument
 * constructor takes the eval task list and the invoker factory, and
 * {@code docs/10-self-improvement.md} records those as injected collaborators — that is
 * the whole seam this class uses. The same document records the other side of the
 * boundary: the eval scorer is constructed inside {@code GateChain} and is not
 * injectable. For this POC that is the referee's tenure, not a defect to fix — an
 * improver that could reach the scorer would optimise its scorer instead of its work. The
 * product's eval scorer is therefore not named anywhere in the referee's own source, and
 * a test asserts that absence across the package — a static call is invisible to an
 * object-graph walk, so only a check of the source text can see it. The fixed/mutable
 * contract is {@code evidence/MUTABILITY.md}, cited here rather than restated.
 *
 * <p>
 * <b>Isolation is by process boundary and by construction, not by absent jars.</b> Both
 * loops are in this one Maven module, so POC 26's engine classes are on the shared
 * classpath and a "no Graal classes here" claim would be false. What is enforced instead
 * is physical: this class' construction path never creates, receives or stores a script
 * engine or a script context, which {@code RefereeIsolationTest} checks by walking the
 * object graph this constructor produces and failing if a script engine or script context
 * is reachable in it. A referee handed a skill folder refuses to start. Widening that to
 * jar-level isolation would need a second Maven module, which this plan did not ask for.
 *
 * @author Ivan Rodriguez
 */
public final class Referee {

	/** Where the frozen task files live, relative to the module's resources directory. */
	public static final Path TASKS = Path.of("benchmark", "tasks");

	/**
	 * The recorded baseline the outer loop compares against; task 2.4 of the change
	 * produces it.
	 */
	public static final Path BASELINE = Path.of("evidence", "baseline.json");

	/** The measured repeat-to-repeat noise floor; task 2.5 of the change produces it. */
	public static final Path NOISE_FLOOR = Path.of("evidence", "noise-floor.json");

	private final Path resourcesDir;

	private final FrozenTaskSet frozen;

	private final GateChain gate;

	private final InvokerFactory invokerFactory;

	private Referee(Path resourcesDir, FrozenTaskSet frozen, GateChain gate, InvokerFactory invokerFactory) {
		this.resourcesDir = resourcesDir;
		this.frozen = frozen;
		this.gate = gate;
		this.invokerFactory = invokerFactory;
	}

	/**
	 * Builds the referee over the frozen set.
	 *
	 * <p>
	 * The invoker factory is wrapped so a candidate answers each prompt once per scoring:
	 * the gate runs the pack itself during re-eval, and without the wrap the referee
	 * would pay for the same model calls twice and could report two different scores for
	 * one answer set because the model answered differently the second time. The recorded
	 * answers are per candidate, never shared across candidates.
	 * @param resourcesDir the module's resources directory, holding
	 * {@code benchmark/tasks} and {@code evidence}
	 * @param skillFolder a skill folder to load scripts from; must be null, because a
	 * referee that loads a skill folder is a referee that runs generated code where it
	 * grades it
	 * @param invokerFactory how the gate deploys and runs a candidate
	 * @return the referee, with the gate already constructed over the frozen set
	 * @throws IllegalStateException if a skill folder is supplied, or the frozen set is
	 * missing or cannot be scored honestly
	 */
	public static Referee start(Path resourcesDir, Path skillFolder, InvokerFactory invokerFactory) {
		if (skillFolder != null) {
			throw new IllegalStateException("the referee refuses to start with a skill folder (" + skillFolder
					+ "). evidence/MUTABILITY.md fixes the referee-process: it loads no skill folder and opens no"
					+ " script context, so generated script code can never run in the process that grades the"
					+ " candidate that generated it. Scripts run only inside a candidate.");
		}
		FrozenTaskSet frozen = FrozenTaskSet.load(resourcesDir.resolve(TASKS));
		frozen.assertNoRestatedPhrases();
		InvokerFactory recorded = new RecordedInvokerFactory(invokerFactory);
		return new Referee(resourcesDir, frozen, new GateChain(recorded, frozen.toEvalTasks()), recorded);
	}

	/**
	 * Scores one candidate: the full deterministic check over the frozen answers, then
	 * the product's gate over the same answers, with the candidate's own proposed tasks
	 * dropped and recorded.
	 * @param candidate the candidate to score
	 * @param baseline the pass-rate the candidate must strictly beat
	 * @return the report, naming the tasks scored, the tasks failed, and the tasks
	 * excluded
	 */
	public RefereeReport score(Candidate candidate, double baseline) {
		List<String> answers = answersFor(candidate);
		List<String> excluded = candidate.proposedTaskIds();
		GateResult result = this.gate.gate(candidate.spec(), baseline);
		return new RefereeReport(candidate.name(), result.gated(), result.stage(), result.reasons(), result.score(),
				this.frozen.score(answers), this.frozen.failedTaskIds(answers), this.frozen.ids(), excluded);
	}

	/**
	 * Stops the paid outer loop when the evidence it compares against has not been
	 * recorded. Both artifacts are fixed for the run by {@code evidence/MUTABILITY.md}: a
	 * baseline the loop has not measured, and a noise floor it could move, are both the
	 * same failure — the loop setting its own pass mark.
	 *
	 * <p>
	 * Presence is what is checked, not content — and that is a real limitation now that
	 * both artifacts exist. Task 2.4 wrote its measured baseline at exactly the path this
	 * referee chose, so the path needed no renegotiation; but a {@code baseline.json}
	 * recording {@code headroom: false} sitting beside a floor would be waved through,
	 * because this method only asks whether the files are there. A launcher that starts a
	 * paid run therefore has to read {@code headroom} and the verdict from the artifact,
	 * not merely its existence.
	 * @throws IllegalStateException naming every artifact that has not been recorded yet
	 */
	public void assertOuterLoopMayStart() {
		List<String> missing = new ArrayList<>();
		for (Path artifact : List.of(BASELINE, NOISE_FLOOR)) {
			Path file = this.resourcesDir.resolve(artifact);
			if (!Files.isRegularFile(file)) {
				missing.add(artifact + " (looked for at " + file + ")");
			}
		}
		if (!missing.isEmpty()) {
			throw new IllegalStateException("the outer loop may not start: no baseline is recorded for the frozen"
					+ " task set and no noise floor was measured before tuning. Missing " + missing
					+ ". An improvement claimed against an unrecorded baseline is the self-grading this POC exists"
					+ " to prevent, so record them first (tasks 2.4 and 2.5).");
		}
	}

	/**
	 * The frozen set the referee scores against.
	 * @return the task set
	 */
	public FrozenTaskSet frozen() {
		return this.frozen;
	}

	private List<String> answersFor(Candidate candidate) {
		AgentSpecInvoker invoker = this.invokerFactory.invokerFor(candidate.spec());
		return this.frozen.tasks().stream().map((task) -> invoker.invoke(task.prompt())).toList();
	}

	/**
	 * Records a candidate's answers per prompt so the gate's re-eval and the frozen check
	 * read the same run of the candidate. The cache is keyed on the pack, so two calls
	 * asking for the same pack's invoker — the referee's own pass and the gate's — share
	 * one recording.
	 */
	private static final class RecordedInvokerFactory implements InvokerFactory {

		private final InvokerFactory delegate;

		private final Map<AgentSpec, Map<String, String>> recorded = new LinkedHashMap<>();

		private RecordedInvokerFactory(InvokerFactory delegate) {
			this.delegate = delegate;
		}

		@Override
		public AgentSpecInvoker invokerFor(AgentSpec pack) {
			return recorded(pack, this.delegate.invokerFor(pack));
		}

		@Override
		public AgentSpecInvoker invokerFor(AgentSpec pack, String model) {
			return recorded(pack, this.delegate.invokerFor(pack, model));
		}

		private AgentSpecInvoker recorded(AgentSpec pack, AgentSpecInvoker fresh) {
			Map<String, String> answers = this.recorded.computeIfAbsent(pack, (key) -> new LinkedHashMap<>());
			return (input) -> answers.computeIfAbsent(input, fresh::invoke);
		}

	}

}
