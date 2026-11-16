/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.embabel.agent.domain.io.UserInput;
import com.embabel.smith.builder.core.BuilderAgent;
import com.embabel.smith.builder.core.Candidate;
import com.embabel.smith.builder.core.CandidateShape;
import com.embabel.smith.builder.core.CompiledDraft;
import com.embabel.smith.builder.core.ProcessRepair;
import com.embabel.smith.builder.core.ProcessSpecGenerator;
import com.embabel.smith.builder.core.Recommendation;
import com.embabel.smith.builder.core.ValidatedDraft;
import com.embabel.smith.builder.eval.AgentSpecInvoker;
import com.embabel.smith.builder.eval.EvalScorer;
import com.embabel.smith.builder.eval.EvalTask;
import com.embabel.smith.builder.eval.InvokerFactory;
import com.embabel.smith.builder.eval.ProcessInvokerFactory;
import com.embabel.smith.builder.eval.PromptRewriter;
import com.embabel.smith.builder.eval.RefinementLoop;
import com.embabel.smith.builder.eval.RefinementOutcome;
import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleStamper;
import com.embabel.smith.builder.lifecycle.ProcessContentHash;
import com.embabel.smith.builder.lifecycle.SpecContentHash;
import com.embabel.smith.builder.publish.AgentSpecPublishable;
import com.embabel.smith.builder.publish.CollisionException;
import com.embabel.smith.builder.publish.FolderPublisher;
import com.embabel.smith.builder.publish.GitPublisher;
import com.embabel.smith.builder.publish.ProcessPublishable;
import com.embabel.smith.builder.publish.PublishResult;
import com.embabel.smith.builder.publish.PublishTarget;
import com.embabel.smith.builder.publish.Publishable;
import com.embabel.smith.builder.publish.Publisher;
import com.embabel.smith.builder.security.AgentSpecSecurityScanner;
import com.embabel.smith.builder.security.ScanReport;
import com.embabel.smith.builder.security.ScanVerdict;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.ProcessSpec;
import com.embabel.smith.common.validation.AgentSpecValidator;
import com.embabel.smith.common.validation.ProcessValidationResult;
import com.embabel.smith.common.validation.ProcessValidator;

/**
 * The product builder (design D1): a thin, plain-class orchestration that the studio
 * reuses. One description, two candidates: the build compiles it once into a single-agent
 * spec and once into a multi-agent process, scores both against the same eval tasks, and
 * publishes the one that wins — keeping both on disk so the rejected candidate stays
 * inspectable.
 *
 * <pre>
 *   1. BuilderAgent            — compile → validate → write the single-agent draft (D5)
 *   2. ProcessSpecGenerator    — decompose → validate → (repair) the multi-agent candidate
 *   3. RefinementLoop          — score the pack, rewrite prompts from failures, re-validate
 *   4. ProcessInvokerFactory   — deploy every pack of the process and score its entry goal
 *   5. Recommendation          — publish the valid candidate with the higher score
 *   6. AgentSpecSecurityScanner — static, offline; a critical finding blocks the build
 *   7. LifecycleStamper        — content hash + baseline pass-rate + state
 *   8. Publisher               — FolderPublisher | GitPublisher (collision gate; no push by default)
 * </pre>
 *
 * <p>
 * Each gate stops the build and reports the stage and reason; nothing is published when a
 * gate fails. The score gate applies to the candidate the build recommended: what must
 * not publish below the gate is what the build ships.
 *
 * @author Ivan Rodriguez
 */
public class BuilderService {

	private final BuilderAgent builder;

	private final InvokerFactory invokerFactory;

	private final ProcessInvokerFactory processInvokerFactory;

	private final ProcessSpecGenerator processGenerator;

	private final PromptRewriter rewriter;

	private final List<EvalTask> evalTasks;

	private final AgentSpecSecurityScanner scanner = new AgentSpecSecurityScanner();

	private final LifecycleStamper stamper = new LifecycleStamper();

	private final ProcessValidator processValidator = new ProcessValidator();

	private final AgentSpecValidator packValidator = new AgentSpecValidator();

	/**
	 * A build with one candidate: no process generator, so the single-agent spec is the
	 * only thing on offer.
	 * @param builder the build component that compiles, validates and writes the pack
	 * @param invokerFactory the factory that deploys a pack for eval
	 * @param rewriter the prompt rewriter the refinement loop drives
	 * @param evalTasks the eval tasks both candidates are scored against
	 */
	public BuilderService(BuilderAgent builder, InvokerFactory invokerFactory, PromptRewriter rewriter,
			List<EvalTask> evalTasks) {
		this(builder, invokerFactory, null, null, rewriter, evalTasks);
	}

	/**
	 * A build with both candidates.
	 * @param builder the build component that compiles, validates and writes the pack
	 * @param invokerFactory the factory that deploys a pack for eval
	 * @param processInvokerFactory the factory that deploys every pack of a process for
	 * eval
	 * @param processGenerator the generator of the multi-agent candidate
	 * @param rewriter the prompt rewriter the refinement loop drives
	 * @param evalTasks the eval tasks both candidates are scored against
	 */
	public BuilderService(BuilderAgent builder, InvokerFactory invokerFactory,
			ProcessInvokerFactory processInvokerFactory, ProcessSpecGenerator processGenerator, PromptRewriter rewriter,
			List<EvalTask> evalTasks) {
		this.builder = builder;
		this.invokerFactory = invokerFactory;
		this.processInvokerFactory = processInvokerFactory;
		this.processGenerator = processGenerator;
		this.rewriter = rewriter;
		this.evalTasks = (evalTasks != null) ? List.copyOf(evalTasks) : List.of();
	}

	public BuildResult build(String description, PublishTarget target, BuildOptions options) {
		// 1. The single-agent candidate: generate → validate → write (D5)
		CompiledDraft compiled = this.builder.compileFromDescription(new UserInput(description), options.technique(),
				options.model());
		if (compiled.hasIssues()) {
			// The description could not be resolved (ambiguous/unsupported) — report the
			// generator's issues and retain the draft for review.
			return BuildResult.blocked("build", compiled.pack().name(), compiled.issues(), null, null, compiled.pack());
		}
		ValidatedDraft validated = this.builder.validateDraft(compiled);
		if (!validated.valid()) {
			return BuildResult.blocked("build", validated.pack().name(), validated.issues(), null, null,
					validated.pack());
		}
		this.builder.writeAgentSpec(validated);
		AgentSpec pack = validated.pack();

		// 2. The multi-agent candidate from the same description: decompose → validate →
		// repair a cycle → re-validate.
		ProcessCandidate process = generateProcess(description, options.model());

		// 3. Score both candidates against the same eval tasks.
		RefinementOutcome outcome = refine(pack, options);
		Candidate single = Candidate.singleAgent(pack.name(), outcome.score());
		Candidate multi = scoreProcess(process, options.model());
		Recommendation recommendation = Recommendation.choose(single, multi,
				(process != null && !process.valid()) ? process.note() : null);

		// 4. The score gate, on the candidate the build recommends.
		double score = recommendation.score();
		if (score < options.scoreGate()) {
			return BuildResult.blocked("eval", recommendation.chosen().name(),
					gateIssues(recommendation, outcome, options), score, null, null);
		}

		// 5. Store both candidates: the pack was written above, the process lands beside
		// it.
		if (process != null && process.valid()) {
			this.builder.writeProcess(process.spec());
		}

		// 6. Security gate (static, offline) over what the build is about to publish.
		ScanReport scan = scan(recommendation, pack, process);
		if (scan.verdict() == ScanVerdict.BLOCK) {
			List<String> findings = scan.findings()
				.stream()
				.map((f) -> f.severity() + " " + f.category() + ": " + f.message() + " [" + f.evidence() + "]")
				.toList();
			return BuildResult.blocked("security", recommendation.chosen().name(), findings, score, scan, null);
		}

		// 7. Lifecycle stamp on the recommended candidate.
		LifecycleMetadata lifecycle = (recommendation.shape() == CandidateShape.MULTI_AGENT)
				? this.stamper.stamp(ProcessContentHash.of(process.spec()), score)
				: this.stamper.stamp(SpecContentHash.of(pack), score);

		// 8. Publish only the recommended candidate (collision gate; no push by default).
		Publishable unit = (recommendation.shape() == CandidateShape.MULTI_AGENT)
				? new ProcessPublishable(process.spec(), lifecycle) : new AgentSpecPublishable(pack, lifecycle);
		try (Publisher publisher = publisherFor(target)) {
			PublishResult published = publisher.publish(unit, options.overwrite(), options.push());
			return BuildResult.built(unit.name(), score, scan, lifecycle, published, recommendation);
		}
		catch (CollisionException ex) {
			return BuildResult.blocked("publish", unit.name(), List.of(ex.getMessage()), score, scan, null);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to publish '" + unit.name() + "': " + ex.getMessage(), ex);
		}
	}

	/**
	 * The pack's eval, with the bounded prompt refinement the score gate asks for.
	 * @param pack the pack to run
	 * @param options the per-build knobs
	 * @return the outcome of the eval/refine loop
	 */
	private RefinementOutcome refine(AgentSpec pack, BuildOptions options) {
		return new RefinementLoop(this.invokerFactory, this.rewriter, this.evalTasks, options.scoreGate(),
				options.maxRefine(), options.model())
			.refine(pack);
	}

	/**
	 * The second candidate: the description decomposed into a process, its cross-agent
	 * hand-offs validated, a detected cycle reordered once, and the result re-validated.
	 * A candidate that cannot be generated or repaired is reported rather than guessed at
	 * — the single-agent candidate stands on its own.
	 * @param description the business-process description to decompose
	 * @param model the LLM to decompose with, or empty for the configured default
	 * @return the candidate, or {@code null} when this build has no process generator or
	 * the model produced no process
	 */
	private ProcessCandidate generateProcess(String description, String model) {
		if (this.processGenerator == null) {
			return null;
		}
		ProcessSpec process;
		try {
			process = this.processGenerator.generateProcess(description, model).process();
		}
		catch (IllegalStateException ex) {
			return new ProcessCandidate(null, null, "the multi-agent candidate failed to generate: " + ex.getMessage());
		}
		ProcessValidationResult validation = this.processValidator.validate(process);
		if (validation.hasCycle()) {
			ProcessRepair.Result repaired = ProcessRepair.repair(process, validation);
			process = repaired.process();
			validation = repaired.validation();
		}
		// A process is only as publishable as its packs: each one has to pass the same
		// structural validation a lone pack is held to, or the runtime would refuse to
		// ingest the process the build just published.
		List<String> issues = new ArrayList<>(validation.issues());
		for (AgentSpec pack : process.agents()) {
			this.packValidator.validate(pack)
				.issues()
				.forEach(
						(issue) -> issues.add("pack '" + pack.name() + "': " + issue.code() + " — " + issue.message()));
		}
		ProcessValidationResult merged = new ProcessValidationResult(issues, validation.sharedTypes(),
				validation.connected(), validation.cycle());
		String note = merged.isValid() ? null : String.join("; ", issues);
		return new ProcessCandidate(process, merged, note);
	}

	/**
	 * The multi-agent candidate scored the same way the pack is: every pack deployed into
	 * one scope and the process's entry goal run against each eval task. A process that
	 * did not validate is not worth a model call.
	 * @param process the process candidate, or {@code null}
	 * @param model the LLM the process's steps run on, or empty for the configured
	 * default
	 * @return the candidate, or {@code null} when there was none
	 */
	private Candidate scoreProcess(ProcessCandidate process, String model) {
		if (process == null || process.spec() == null) {
			return null;
		}
		String name = process.spec().process();
		if (!process.valid()) {
			return Candidate.multiAgent(name, false, null);
		}
		AgentSpecInvoker invoker = this.processInvokerFactory.invokerFor(process.spec(), model);
		List<String> outputs = new ArrayList<>();
		for (EvalTask task : this.evalTasks) {
			outputs.add(invoker.invoke(task.input()));
		}
		return Candidate.multiAgent(name, true, EvalScorer.score(this.evalTasks, outputs));
	}

	/**
	 * Why the build stopped below its gate: the score that fell short, the failing tasks,
	 * and what became of the candidate that did not win.
	 * @param recommendation the recommendation that was gated
	 * @param outcome the single-agent candidate's eval outcome
	 * @param options the per-build knobs
	 * @return the issues to report
	 */
	private static List<String> gateIssues(Recommendation recommendation, RefinementOutcome outcome,
			BuildOptions options) {
		List<String> issues = new ArrayList<>();
		issues.add("eval score " + String.format("%.3f", recommendation.score()) + " is below the gate "
				+ options.scoreGate() + " for the recommended " + recommendation.chosen().shape().label()
				+ "; failing tasks: " + outcome.failingTasks());
		if (recommendation.processNote() != null) {
			issues.add(recommendation.processNote());
		}
		return issues;
	}

	/**
	 * The static scan over the packs the build publishes: the winning candidate's,
	 * whatever its shape — a process contributes every one of its packs.
	 * @param recommendation the recommendation the build settled on
	 * @param pack the single-agent candidate
	 * @param process the multi-agent candidate, or {@code null}
	 * @return the merged scan report
	 */
	private ScanReport scan(Recommendation recommendation, AgentSpec pack, ProcessCandidate process) {
		if (recommendation.shape() == CandidateShape.MULTI_AGENT) {
			return this.scanner.scanProcess(recommendation.chosen().name(), process.spec().agents());
		}
		return this.scanner.scan(pack);
	}

	private static Publisher publisherFor(PublishTarget target) {
		return switch (target.type()) {
			case FOLDER -> new FolderPublisher(target);
			case GIT -> new GitPublisher(target);
		};
	}

	/**
	 * The multi-agent candidate between generation and publication.
	 *
	 * @param spec the process, or {@code null} when it could not be generated
	 * @param validation its validation after any repair, or {@code null}
	 * @param note why the candidate is unusable, when it is
	 */
	private record ProcessCandidate(ProcessSpec spec, ProcessValidationResult validation, String note) {

		boolean valid() {
			return this.spec != null && this.validation != null && this.validation.isValid();
		}

	}

}
