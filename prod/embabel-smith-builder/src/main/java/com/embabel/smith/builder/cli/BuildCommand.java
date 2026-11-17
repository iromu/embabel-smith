/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.builder.BuildOptions;
import com.embabel.smith.builder.BuildResult;
import com.embabel.smith.builder.BuilderService;
import com.embabel.smith.builder.blueprint.BlueprintLoader;
import com.embabel.smith.builder.blueprint.ProcessExtractor;
import com.embabel.smith.builder.core.AgentSpecGenerators;
import com.embabel.smith.builder.core.AiSpecPromptCaller;
import com.embabel.smith.builder.core.BuildTarget;
import com.embabel.smith.builder.core.BuilderAgent;
import com.embabel.smith.builder.core.GenerationTechnique;
import com.embabel.smith.builder.core.SingleShotProcessSpecGenerator;
import com.embabel.smith.builder.eval.AgentSpecInvokerFactory;
import com.embabel.smith.builder.eval.AiPromptRewriter;
import com.embabel.smith.builder.eval.EvalTask;
import com.embabel.smith.builder.eval.PromptRewriter;
import com.embabel.smith.builder.publish.PublishTarget;
import com.embabel.smith.builder.skill.SkillBuildResult;
import com.embabel.smith.builder.skill.SkillBuilderService;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * The {@code smith build} command: builds a agent spec from a natural-language
 * description, or from business-process documents, runs it through eval → refine →
 * security → lifecycle, and publishes it to a source. The build report goes to stdout; a
 * blocked build prints the stage and reason and exits 1. Usage errors exit 2 (picocli
 * default).
 *
 * @author Ivan Rodriguez
 */
@Command(name = "build", description = "Build a agent spec from a description and publish it to a source")
public class BuildCommand implements Callable<Integer> {

	@ArgGroup(exclusive = true, multiplicity = "1", heading = "Build input (exactly one of)%n")
	Input input;

	@Option(names = "--extract",
			description = "Run the discovery prompt over --docs first, and build the business-process "
					+ "description it extracts rather than the documents themselves")
	boolean extract;

	@Option(names = "--out", required = true,
			description = "Source URI to publish to: folder:<abs-path> or git:<url>[@ref]")
	String out;

	@Option(names = "--max-refine", description = "Maximum refinement rounds (default ${DEFAULT-VALUE})")
	int maxRefine = BuildOptions.DEFAULT_MAX_REFINE;

	@Option(names = "--overwrite", description = "Overwrite an existing pack or skill of the same name")
	boolean overwrite;

	@Option(names = "--push", description = "Push the git commit to the remote (default: local commit only)")
	boolean push;

	@Option(names = "--technique", converter = TechniqueConverter.class,
			description = "Generation technique: single-shot (default, one model call emits the spec) "
					+ "or embabel (the Embabel NL compiler)")
	GenerationTechnique technique = GenerationTechnique.SINGLE_SHOT;

	@Option(names = "--target", converter = TargetConverter.class,
			description = "Output target: agent-spec (default, an executable pack) or skill "
					+ "(a SKILL.md validated by the with/without-skill lift)")
	BuildTarget target = BuildTarget.AGENT_SPEC;

	private final AgentPlatform platform;

	private final Ai ai;

	public BuildCommand(AgentPlatform platform, Ai ai) {
		this.platform = platform;
		this.ai = ai;
	}

	/**
	 * Test seam: allows substituting the build service.
	 * @param scratch the scratch directory the builder writes intermediate files into.
	 * @return the build service wired with the default compiler, invoker factory and
	 * rewriter.
	 */
	protected BuilderService newService(Path scratch) {
		BuilderAgent agent = new BuilderAgent(AgentSpecGenerators.production(this.ai, this.platform),
				scratch.toString());
		AgentSpecInvokerFactory invokers = new AgentSpecInvokerFactory(this.platform);
		PromptRewriter rewriter = new AiPromptRewriter(this.ai);
		return new BuilderService(agent, invokers, invokers, new SingleShotProcessSpecGenerator(this.ai), rewriter,
				defaultEvalTasks());
	}

	/**
	 * The default eval task set: a single "the spec runs and produces output" case,
	 * shared via {@link EvalTask#smokeDefault()}. The task set is a parameter of the
	 * build (design D1); richer acceptance criteria can be supplied by callers of
	 * {@link BuilderService}.
	 * @return the default eval task set.
	 */
	private static List<EvalTask> defaultEvalTasks() {
		return EvalTask.smokeDefault();
	}

	/**
	 * Test seam: allows substituting the skill build service.
	 * @return the skill service wired with the builder's model layer and bundled prompts.
	 */
	protected SkillBuilderService newSkillService() {
		return new SkillBuilderService(new AiSpecPromptCaller(this.ai));
	}

	/**
	 * Test seam: allows substituting the model-based extraction that {@code --extract}
	 * runs over the documents.
	 * @return the extractor, running the bundled discovery prompt on the builder's model
	 * layer.
	 */
	protected ProcessExtractor newExtractor() {
		return new ProcessExtractor(new AiSpecPromptCaller(this.ai));
	}

	/**
	 * The build description, resolved from the input the command line named: a
	 * description as given, or the documents under {@code --docs} assembled into a corpus
	 * — and, under {@code --extract}, run through the discovery prompt first.
	 * @return the description to build.
	 */
	private String resolveDescription() {
		if (this.input.docs == null) {
			return this.input.description;
		}
		String corpus = new BlueprintLoader().resolve(null, this.input.docs);
		String description = this.extract ? newExtractor().extract(corpus) : corpus;
		if (description == null || description.isBlank()) {
			throw new IllegalStateException(
					"Ingestion produced no build description from the documents at " + this.input.docs);
		}
		return description;
	}

	/**
	 * Runs the build, dispatching on the output target: the agent-spec pipeline publishes
	 * a pack gated on its eval score, the skill pipeline publishes a skill folder gated
	 * on the with/without-skill lift.
	 * @return the exit code: 0 on a successful build, 1 on failure.
	 */
	@Override
	public Integer call() {
		// Enforced here, not declared with @Option(dependsOn): the pinned picocli jar
		// ships no dependency validation at all, so the constraint has no declarative
		// home.
		if (this.extract && this.input.docs == null) {
			System.err.println("Missing required parameter for option '--extract': it applies only to --docs.");
			return 2;
		}
		try {
			PublishTarget target = PublishTarget.parse(this.out);
			BuildOptions options = new BuildOptions(this.maxRefine, BuildOptions.DEFAULT_SCORE_GATE, this.overwrite,
					this.push, this.technique, this.target, BuildOptions.DEFAULT_MIN_LIFT);
			String description = resolveDescription();
			return (options.target() == BuildTarget.SKILL) ? buildSkill(description, target, options)
					: buildAgentSpec(description, target, options);
		}
		catch (Exception ex) {
			System.err.println((ex.getMessage() == null) ? ex.toString() : ex.getMessage());
			return 1;
		}
	}

	private Integer buildSkill(String description, PublishTarget target, BuildOptions options) {
		SkillBuildResult result = newSkillService().build(description, target, options);
		System.out.print(result.report());
		return result.isBuilt() ? 0 : 1;
	}

	private Integer buildAgentSpec(String description, PublishTarget target, BuildOptions options) {
		// The scratch dir is only used by the BuilderAgent write step; delete it once the
		// build is
		// done so we don't leak a temp dir per invocation.
		Path scratch;
		try {
			scratch = Files.createTempDirectory("smith-builder-scratch-");
		}
		catch (IOException ex) {
			System.err.println("cannot create builder scratch dir: " + ex.getMessage());
			return 1;
		}
		try {
			BuildResult result = newService(scratch).build(description, target, options);
			System.out.print(result.report());
			return result.isBuilt() ? 0 : 1;
		}
		catch (Exception ex) {
			System.err.println((ex.getMessage() == null) ? ex.toString() : ex.getMessage());
			return 1;
		}
		finally {
			deleteTree(scratch);
		}
	}

	private static void deleteTree(Path dir) {
		if (!Files.exists(dir)) {
			return;
		}
		try (var stream = Files.walk(dir)) {
			stream.sorted(Comparator.reverseOrder()).forEach((path) -> {
				try {
					Files.delete(path);
				}
				catch (IOException ignored) {
					// best-effort cleanup; ignore failures
				}
			});
		}
		catch (IOException ignored) {
			// best-effort cleanup; ignore failures
		}
	}

	/**
	 * The two mutually exclusive build inputs: a hand-written description, or a directory
	 * of business-process documents. Exactly one is required, so naming neither — and
	 * naming both — is a usage error (exit 2).
	 */
	static class Input {

		@Parameters(index = "0", arity = "0..1", description = "The natural-language description of the spec to build")
		String description;

		@Option(names = "--docs", description = "Directory of business-process documents to build from")
		String docs;

	}

	/**
	 * Accepts a technique by its user-facing name ({@code single-shot} /
	 * {@code embabel}), so a typo is a usage error (exit 2) rather than a silent
	 * fallback.
	 */
	public static class TechniqueConverter implements CommandLine.ITypeConverter<GenerationTechnique> {

		@Override
		public GenerationTechnique convert(String value) {
			return GenerationTechnique.parse(value);
		}

	}

	/**
	 * Accepts an output target by its user-facing name ({@code agent-spec} /
	 * {@code skill}), so a typo is a usage error (exit 2) rather than a silent fallback.
	 */
	public static class TargetConverter implements CommandLine.ITypeConverter<BuildTarget> {

		@Override
		public BuildTarget convert(String value) {
			return BuildTarget.parse(value);
		}

	}

}
