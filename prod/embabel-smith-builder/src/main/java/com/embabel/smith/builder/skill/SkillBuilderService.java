/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
 *   3. EvalSource       — the model drafts the evals.json suite
 *   4. EvalRunner       — every scenario run with the skill and without it
 *   5. Benchmark        — the two pass rates and the lift between them (the gate)
 *   6. security scan    — the static prompt-injection scan over the SKILL.md
 *   7. LifecycleStamper — content hash + with-skill pass rate as baseline + incubating
 *   8. Publisher        — FolderPublisher | GitPublisher (collision gate; no push by default)
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

	public SkillBuilderService(SpecPromptCaller caller) {
		this(caller, SkillPrompts.generateSkill(), SkillPrompts.generateEvals(), SkillPrompts.gradeEval(),
				SkillPrompts.agentWithSkill(), SkillPrompts.agentBaseline());
	}

	public SkillBuilderService(SpecPromptCaller caller, String skillPrompt, String evalsPrompt, String gradePrompt,
			String withSkillPrompt, String baselinePrompt) {
		this.caller = caller;
		this.generator = new SkillGenerator(caller, skillPrompt);
		this.evalsPrompt = evalsPrompt;
		this.runner = new EvalRunner(caller, new SkillGrader(caller, gradePrompt), withSkillPrompt, baselinePrompt);
	}

	/**
	 * Builds a skill from a description and publishes it when every gate passes.
	 * @param description the business-process description to compile.
	 * @param target the destination to publish to.
	 * @param options the per-build knobs; the skill gate reads
	 * {@link BuildOptions#minLift()}.
	 * @return the build outcome, including the lift it was gated on.
	 */
	public SkillBuildResult build(String description, PublishTarget target, BuildOptions options) {
		// 1. Generate: the name is fixed by the description so a rebuild targets the same
		// source location, which is what the collision gate detects.
		String skillName = PackName.slug(description);
		Skill skill;
		try {
			skill = this.generator.generate(skillName, description);
		}
		catch (IllegalStateException ex) {
			return SkillBuildResult.blocked("generate", skillName, List.of(ex.getMessage()), null, null);
		}

		// 2. Frontmatter gate: an ill-formed SKILL.md is never published.
		List<String> frontmatter = SkillMdValidator.validate(skill.name(), skill.description());
		if (!frontmatter.isEmpty()) {
			return SkillBuildResult.blocked("validate", skillName, frontmatter, null, null);
		}

		// 3–5. Both arms, and the lift between them.
		EvalSuite evals;
		try {
			evals = EvalSource.generate(this.caller, this.evalsPrompt, skillName, description);
		}
		catch (IllegalStateException ex) {
			return SkillBuildResult.blocked("eval", skillName, List.of(ex.getMessage()), null, null);
		}
		if (evals.evals().isEmpty()) {
			return SkillBuildResult.blocked("eval", skillName,
					List.of("the model drafted no eval scenarios, so the skill cannot be validated"), null, null);
		}
		Benchmark.Summary summary = Benchmark.aggregate(this.runner.run(skill, evals));
		if (summary.lift() <= options.minLift()) {
			return SkillBuildResult.blocked("eval", skillName, liftIssues(summary, options.minLift()), summary, null);
		}

		// 6. Security gate (static, offline) over the SKILL.md the skill publishes as.
		ScanReport scan = this.scanner.scanText(skillName, skill.toSkillMd());
		if (scan.verdict() == ScanVerdict.BLOCK) {
			List<String> findings = scan.findings()
				.stream()
				.map((f) -> f.severity() + " " + f.category() + ": " + f.message() + " [" + f.evidence() + "]")
				.toList();
			return SkillBuildResult.blocked("security", skillName, findings, summary, scan);
		}

		// 7. Lifecycle stamp: the with-skill pass rate is the baseline the library later
		// compares against.
		LifecycleMetadata lifecycle = this.stamper.stamp(SkillContentHash.of(skill, evals), summary.withPassRate());

		// 8. Publish (collision gate; no push by default)
		try (Publisher publisher = publisherFor(target)) {
			PublishResult published = publisher.publish(new SkillPublishable(skill, evals, lifecycle),
					options.overwrite(), options.push());
			return SkillBuildResult.built(skillName, summary, scan, lifecycle, published);
		}
		catch (CollisionException ex) {
			return SkillBuildResult.blocked("publish", skillName, List.of(ex.getMessage()), summary, scan);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to publish skill '" + skillName + "': " + ex.getMessage(), ex);
		}
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
