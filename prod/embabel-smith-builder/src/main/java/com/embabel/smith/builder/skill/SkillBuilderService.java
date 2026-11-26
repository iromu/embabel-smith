/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.embabel.smith.builder.BuildOptions;
import com.embabel.smith.builder.BuilderService;
import com.embabel.smith.builder.core.PackName;
import com.embabel.smith.builder.core.SpecPromptCaller;
import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleStamper;
import com.embabel.smith.builder.publish.CollisionException;
import com.embabel.smith.builder.publish.FolderPublisher;
import com.embabel.smith.builder.publish.GitPublisher;
import com.embabel.smith.builder.publish.PublishResult;
import com.embabel.smith.builder.publish.PublishTarget;
import com.embabel.smith.builder.publish.Publisher;
import com.embabel.smith.builder.publish.SkillPublishable;
import com.embabel.smith.builder.security.AgentSpecSecurityScanner;
import com.embabel.smith.builder.security.ScanReport;
import com.embabel.smith.builder.security.ScanVerdict;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentsPack;

/**
 * The skill output target (design D1): a build that compiles a business-process
 * description into an Agent Skill rather than an executable pack. It runs alongside
 * {@link BuilderService} — the spec pipeline is untouched — because the two differ in
 * output (a {@code SKILL.md} plus {@code evals.json}, not a pack directory), in eval (a
 * with/without-skill comparison graded by an LLM judge, not deterministic tasks) and in
 * gate (the lift, not "every task passes"). The stages they share — the static security
 * scan, the lifecycle stamp, and the publisher with its collision gate and no-push
 * default — are shared classes, so a published skill carries the same semantics as a
 * published spec.
 *
 * <pre>
 *   1. SkillGenerator   — description → SKILL.md (name fixed by the description)
 *   2. SkillMdValidator — Agent Skills frontmatter constraints
 *   3. SkillInspector   — the skill writing guidelines, redrafting the draft while the
 *                         gate still refuses it (bounded by --max-inspect)
 *   4. EvalSource       — the model drafts the evals.json suite
 *   5. EvalRunner       — every scenario run with the skill and without it
 *   6. Benchmark        — the two pass rates and the lift between them (the gate)
 *   7. security scan    — the static prompt-injection scan over both documents the build
 *                         publishes from model output — SKILL.md and evals.json — plus the
 *                         static code checks over any scripts/ folder it also publishes
 *   8. LifecycleStamper — content hash + with-skill pass rate as baseline + incubating
 *   9. Publisher        — FolderPublisher | GitPublisher (collision gate; no push by default)
 * </pre>
 *
 * <p>
 * Each gate stops the build and reports the stage and reason; nothing is published when a
 * gate fails. The lift gate is a deliberate departure from the spec path's deterministic
 * gate: the judge is non-deterministic, so the gate is relative, and the report carries
 * the per-scenario grading so a blocked build can be inspected.
 *
 * @author Ivan Rodriguez
 */
public class SkillBuilderService {

	private final SpecPromptCaller caller;

	private final SkillGenerator generator;

	private final String evalsPrompt;

	private final EvalRunner runner;

	private final AgentSpecSecurityScanner scanner = new AgentSpecSecurityScanner();

	private final LifecycleStamper stamper = new LifecycleStamper();

	private final SkillInspector inspector = new SkillInspector();

	public SkillBuilderService(SpecPromptCaller caller) {
		this(caller, SkillPrompts.generateSkill(), SkillPrompts.generateEvals(), SkillPrompts.gradeEval(),
				SkillPrompts.agentWithSkill(), SkillPrompts.agentBaseline());
	}

	/**
	 * The wiring {@code BuildCommand} uses, with the script host the engine bean made
	 * possible: a null host is an ordinary build, whose eval arms are prompt substitution
	 * and nothing else.
	 * @param caller the builder's model layer.
	 * @param scriptHost the host to run the skill's scripts with, or null.
	 */
	public SkillBuilderService(SpecPromptCaller caller, SkillScriptHost scriptHost) {
		this(caller, SkillPrompts.generateSkill(), SkillPrompts.generateEvals(), SkillPrompts.gradeEval(),
				SkillPrompts.agentWithSkill(), SkillPrompts.agentBaseline(), scriptHost);
	}

	public SkillBuilderService(SpecPromptCaller caller, String skillPrompt, String evalsPrompt, String gradePrompt,
			String withSkillPrompt, String baselinePrompt) {
		this(caller, skillPrompt, evalsPrompt, gradePrompt, withSkillPrompt, baselinePrompt, null);
	}

	/**
	 * Builds a skill service with every prompt named and a script host.
	 * @param caller the builder's model layer.
	 * @param skillPrompt the generation prompt.
	 * @param evalsPrompt the eval-generation prompt.
	 * @param gradePrompt the judge prompt.
	 * @param withSkillPrompt the with-skill arm prompt.
	 * @param baselinePrompt the baseline arm prompt.
	 * @param scriptHost the host that lets the with-skill arm run the skill's scripts, or
	 * null — which is the state of a build that did not install
	 * {@code smith.builder.skill-scripts.enabled}. Every other stage is unchanged by it:
	 * the security scan still reads the scripts, and publish still refuses a failing one.
	 */
	public SkillBuilderService(SpecPromptCaller caller, String skillPrompt, String evalsPrompt, String gradePrompt,
			String withSkillPrompt, String baselinePrompt, SkillScriptHost scriptHost) {
		this.caller = caller;
		this.generator = new SkillGenerator(caller, skillPrompt);
		this.evalsPrompt = evalsPrompt;
		this.runner = new EvalRunner(caller, new SkillGrader(caller, gradePrompt), withSkillPrompt, baselinePrompt,
				scriptHost);
	}

	/**
	 * Builds one skill per stage of a process specification.
	 * <p>
	 * A stage is a member {@link AgentSpec} of the process, and its description is
	 * assembled here from fields the pack already carries: the stage's name, then for
	 * each of its steps the step description, the types it consumes and the type it
	 * produces. There is no model call in the derivation — a stage that already declares
	 * what it reads and writes does not need to be asked what it does. The descriptions
	 * differ because the steps differ, which is what makes the stages separately
	 * buildable rather than N copies of one prompt.
	 * <p>
	 * Each description then goes through
	 * {@link #build(String, PublishTarget, BuildOptions)} unchanged. The frontmatter,
	 * inspection, eval and lift gates belong to that method and this one adds none of its
	 * own, so a stage of a process is gated exactly as a hand-written description would
	 * be. The first stage a gate refuses stops the run and is the reported result: a
	 * process with an unpublished stage is not a built process. When every stage passes,
	 * the earlier ones are already published and the report is the final stage's.
	 * @param agentsPack the decomposition to build skills from.
	 * @param target the destination each stage's skill publishes to.
	 * @param options the per-build knobs, applied to every stage alike.
	 * @return the first blocked stage, or the final stage's result when all of them
	 * built.
	 */
	public SkillBuildResult buildFromAgentsPack(AgentsPack agentsPack, PublishTarget target, BuildOptions options) {
		List<AgentSpec> stages = agentsPack.agents();
		if (stages.isEmpty()) {
			return SkillBuildResult.blocked("decompose", agentsPack.process(),
					List.of("the process declares no stages, so there is no skill to build"), null, null);
		}
		SkillBuildResult result = null;
		for (AgentSpec stage : stages) {
			result = build(stageDescription(stage), target, options);
			if (!result.isBuilt()) {
				return result;
			}
		}
		return result;
	}

	/**
	 * The description handed to the skill generator for one stage, assembled from that
	 * stage's own steps.
	 * @param stage the member agent to describe.
	 * @return the stage description.
	 */
	private static String stageDescription(AgentSpec stage) {
		StringBuilder description = new StringBuilder("Stage ").append(stage.name()).append(". ");
		for (ActionStepSpec step : stage.steps()) {
			description.append(step.name()).append(": ").append(step.description());
			if (!step.inputTypeNames().isEmpty()) {
				description.append(" (from ").append(String.join(", ", step.inputTypeNames())).append(')');
			}
			description.append(" produces ").append(step.outputTypeName()).append(". ");
		}
		return description.toString().trim();
	}

	/**
	 * Builds a skill from a description and publishes it when every gate passes.
	 * @param description the business-process description to compile.
	 * @param target the destination to publish to.
	 * @param options the per-build knobs; the skill gate reads
	 * {@link BuildOptions#minLift()}, and the inspection gate reads
	 * {@link BuildOptions#maxInspect()} and {@link BuildOptions#fullInspection()}.
	 * @return the build outcome, including the lift it was gated on.
	 */
	public SkillBuildResult build(String description, PublishTarget target, BuildOptions options) {
		// 1–3. Generate, frontmatter, inspect — and redraft, up to --max-inspect times,
		// while the inspection still refuses the draft. The bound is an explicit break on
		// the last round, so the skill that leaves the loop is the one that was inspected
		// and no redraft is paid for after the verdict is already known.
		String skillName = PackName.slug(description);
		int maxInspect = Math.max(0, options.maxInspect());
		Skill skill = null;
		SkillInspector.Inspection inspection = null;
		int drafts = 0;
		for (int round = 0; round <= maxInspect; round++) {
			drafts++;
			try {
				skill = (inspection != null)
						? this.generator.generate(skillName, description, inspection.blockingFindings())
						: this.generator.generate(skillName, description);
			}
			catch (IllegalStateException ex) {
				return SkillBuildResult.blocked("generate", skillName, List.of(ex.getMessage()), null, null);
			}

			// 2. Frontmatter gate: an ill-formed SKILL.md is never published, and a
			// redraft is not asked to fix one — the record itself is wrong, not the
			// writing.
			List<String> frontmatter = SkillMdValidator.validate(skill.name(), skill.description());
			if (!frontmatter.isEmpty()) {
				return SkillBuildResult.blocked("validate", skillName, frontmatter, null, null);
			}

			// 3. Inspection gate, before any eval scenario is drafted: it costs no model
			// calls, and a draft it refuses would have made every arm a waste. Only the
			// blocking findings go back to the model; advisories are reported, not fed
			// back.
			inspection = this.inspector.inspect(skill, options.fullInspection());
			if (inspection.passed()) {
				break;
			}
			if (round == maxInspect) {
				return SkillBuildResult.blocked("inspect", skillName, inspection.blockingFindings(), null, null,
						inspectionReport(inspection, drafts, options.fullInspection()));
			}
		}
		SkillBuildResult.InspectionReport inspected = inspectionReport(inspection, drafts, options.fullInspection());

		// 4–6. Both arms, and the lift between them.
		EvalSuite evals;
		try {
			evals = EvalSource.generate(this.caller, this.evalsPrompt, skillName, description);
		}
		catch (IllegalStateException ex) {
			return SkillBuildResult.blocked("eval", skillName, List.of(ex.getMessage()), null, null, inspected);
		}
		if (evals.evals().isEmpty()) {
			return SkillBuildResult.blocked("eval", skillName,
					List.of("the model drafted no eval scenarios, so the skill cannot be validated"), null, null,
					inspected);
		}
		// A scenario with no expectations contributes 0/0 to both arms, so it cannot
		// move the lift: a suite of them blocks with "+0.000" indistinguishable from a
		// genuinely useless skill, and a mixed suite silently gates on the scenarios
		// that do measure something. Refused here, before either arm is paid for.
		List<Integer> unmeasurable = evals.evals()
			.stream()
			.filter((eval) -> eval.expectations().isEmpty())
			.map(EvalSuite.Eval::id)
			.toList();
		if (!unmeasurable.isEmpty()) {
			return SkillBuildResult.blocked("eval", skillName,
					List.of("the model drafted eval scenarios with no expectations (scenario "
							+ unmeasurable.stream().map(String::valueOf).collect(Collectors.joining(", "))
							+ "), so they measure nothing; every scenario must state at least one expectation"),
					null, null, inspected);
		}
		Benchmark.Summary summary = Benchmark.aggregate(this.runner.run(skill, evals));
		if (summary.lift() <= options.minLift()) {
			return SkillBuildResult.blocked("eval", skillName, liftIssues(summary, options.minLift()), summary, null,
					inspected);
		}

		// 7. Security gate (static, offline) over everything the skill publishes from
		// model
		// output: the two documents and the JavaScript. The lifecycle sidecar is
		// excluded:
		// the builder wrote it, not the model. A script is the one part of a published
		// skill
		// that executes, so it is scanned as code and not as text alone — a script that
		// reaches for the host, the network or a parent-directory path blocks the build
		// here
		// rather than being discovered by the reader's runtime.
		ScanReport scan = this.scanner.scanSkill(skillName, List.of(skill.toSkillMd(), evals.toJson()),
				skill.scriptSources());
		if (scan.verdict() == ScanVerdict.BLOCK) {
			List<String> findings = scan.findings()
				.stream()
				.map((f) -> f.severity() + " " + f.category() + ": " + f.message() + " [" + f.evidence() + "]")
				.toList();
			return SkillBuildResult.blocked("security", skillName, findings, summary, scan, inspected);
		}

		// 7b. Reference gate, beside the security one and for the same reason: it is
		// static, it costs no model call, and what it refuses is an artifact that cannot
		// do what it says. A body that tells the agent to run scripts/x.js the folder
		// does
		// not carry is not a warning about that script — a reader's loader validates the
		// references as it loads and refuses the whole skill, logging the reason on the
		// reader's side and nowhere the builder can see. So the published folder is
		// checked against the body's own claims here, before anything is written, rather
		// than discovered unreadable later. See SkillFileReferences.
		List<String> dangling = SkillFileReferences.missing(skill);
		if (!dangling.isEmpty()) {
			return SkillBuildResult.blocked("references", skillName,
					List.of("the SKILL.md names " + String.join(", ", dangling)
							+ ", which the published folder does not contain; a reader's loader refuses the whole"
							+ " skill on a missing reference, so the skill would publish unreadable — either emit the"
							+ " script it names or stop pointing at it"),
					summary, scan, inspected);
		}

		// 8. Lifecycle stamp: the with-skill pass rate is the baseline the library later
		// compares against.
		LifecycleMetadata lifecycle = this.stamper.stamp(SkillContentHash.of(skill, evals), summary.withPassRate());

		// 9. Publish (collision gate; no push by default)
		try (Publisher publisher = publisherFor(target)) {
			PublishResult published = publisher.publish(new SkillPublishable(skill, evals, lifecycle),
					options.overwrite(), options.push());
			return SkillBuildResult.built(skillName, summary, scan, lifecycle, published, inspected);
		}
		catch (CollisionException ex) {
			return SkillBuildResult.blocked("publish", skillName, List.of(ex.getMessage()), summary, scan, inspected);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to publish skill '" + skillName + "': " + ex.getMessage(), ex);
		}
	}

	/**
	 * What the gate did, in the form the build report carries. The checks it requested
	 * are quoted so a reader can see the lineup was the binary-free one; the checks it
	 * did not request come from the inspection itself.
	 * @param inspection the gate's verdict
	 * @param drafts the number of drafts the gate consumed
	 * @param fullInspection whether the operator asked for the binary-backed checks
	 * @return the report's inspection section
	 */
	private static SkillBuildResult.InspectionReport inspectionReport(SkillInspector.Inspection inspection, int drafts,
			boolean fullInspection) {
		return new SkillBuildResult.InspectionReport(SkillInspector.requestedChecks(fullInspection).size(),
				inspection.qualityScore(), drafts, inspection.advisoryFindings(), inspection.incompleteScans(),
				inspection.checksNotRun());
	}

	/**
	 * Why the lift gate stopped the build: the margin required, and the scenarios that
	 * did not move. A blocked build must be inspectable, because the judge — not a
	 * deterministic assertion — decided it.
	 * @param summary the benchmark summary.
	 * @param minLift the lift the gate required.
	 * @return the issues to report.
	 */
	private static List<String> liftIssues(Benchmark.Summary summary, double minLift) {
		List<String> issues = new ArrayList<>();
		issues.add("skill lift " + String.format("%+.3f", summary.lift()) + " does not exceed the required "
				+ String.format("%+.3f", minLift) + " (with-skill " + String.format("%.3f", summary.withPassRate())
				+ " vs no-skill baseline " + String.format("%.3f", summary.withoutPassRate()) + ")");
		for (Benchmark.EvalScore score : summary.perEval()) {
			if (score.withPassed() <= score.withoutPassed()) {
				issues
					.add("eval " + score.evalId() + " did not improve with the skill: with " + score.withPassed() + "/"
							+ score.withTotal() + " vs without " + score.withoutPassed() + "/" + score.withoutTotal());
			}
		}
		return issues;
	}

	private static Publisher publisherFor(PublishTarget target) {
		return switch (target.type()) {
			case FOLDER -> new FolderPublisher(target);
			case GIT -> new GitPublisher(target);
		};
	}

}
