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
import java.util.Map;
import java.util.concurrent.Callable;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;
import com.embabel.smith.builder.BuildOptions;
import com.embabel.smith.builder.BuildResult;
import com.embabel.smith.builder.BuilderService;
import com.embabel.smith.builder.blueprint.BlueprintLoader;
import com.embabel.smith.builder.blueprint.ProcessExtractor;
import com.embabel.smith.builder.core.AgentSpecGenerator;
import com.embabel.smith.builder.core.AgentSpecGenerators;
import com.embabel.smith.builder.core.AiSpecPromptCaller;
import com.embabel.smith.builder.core.BuildTarget;
import com.embabel.smith.builder.core.BuilderAgent;
import com.embabel.smith.builder.core.GenerationTechnique;
import com.embabel.smith.builder.core.SingleShotProcessSpecGenerator;
import com.embabel.smith.builder.eval.AgentSpecInvokerFactory;
import com.embabel.smith.builder.eval.AgenticAgentSpecGenerator;
import com.embabel.smith.builder.eval.AiPromptRewriter;
import com.embabel.smith.builder.eval.EvalTask;
import com.embabel.smith.builder.eval.HarnessAgentSpecGenerator;
import com.embabel.smith.builder.eval.InvokerFactory;
import com.embabel.smith.builder.eval.PackDrivenAgentSpecGenerator;
import com.embabel.smith.builder.eval.PromptRewriter;
import com.embabel.smith.builder.publish.PublishTarget;
import com.embabel.smith.builder.skill.SkillBuildResult;
import com.embabel.smith.builder.skill.SkillBuilderService;
import com.embabel.smith.builder.skill.SkillScriptHost;
import com.embabel.smith.builder.tools.ToolSurface;
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
 * <p>
 * {@code --flow} says who issues the generation call: {@code java} (the default, and what
 * every build did before this flag existed) has the Java stages call the generator, while
 * {@code pack} has the builder's own {@code builder-spec-one-shot} pack issue it through
 * {@code AgentSpecInvokerFactory}, {@code agentic} has {@code builder-agentic-blueprint}
 * issue it with the checks forced in Java, and {@code harness} has
 * {@code builder-harness} issue it over steps that read the document roots this command
 * opened. It is one flag with one branch, in {@link #generators(InvokerFactory)}, with no
 * property behind it (design D3); the combination no pack flow can serve — a pack flow
 * with the Embabel technique, which has no pack — is refused before anything is spent
 * (design D4), and the report names the flow that ran so a run is attributable from its
 * output.
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

	@Option(names = "--min-lift", description = "Skill lift the with/without-skill gate requires "
			+ "(default ${DEFAULT-VALUE}); accepted on an agent-spec build and unused there")
	double minLift = BuildOptions.DEFAULT_MIN_LIFT;

	@Option(names = "--max-inspect", description = "Skill inspection redrafts the gate is allowed to ask for "
			+ "(default ${DEFAULT-VALUE}); accepted on an agent-spec build and unused there")
	int maxInspect = BuildOptions.DEFAULT_MAX_INSPECT;

	@Option(names = "--full-inspection", description = "Also run the skill checks that need an external scanner "
			+ "(skillspector, gitleaks, bandit, semgrep); only useful where those tools are installed")
	boolean fullInspection;

	@Option(names = "--overwrite", description = "Overwrite an existing pack or skill of the same name")
	boolean overwrite;

	@Option(names = "--push", description = "Push the git commit to the remote (default: local commit only)")
	boolean push;

	@Option(names = "--technique", converter = TechniqueConverter.class,
			description = "Generation technique: single-shot (default, one model call emits the spec) "
					+ "or embabel (the Embabel NL compiler)")
	GenerationTechnique technique = GenerationTechnique.SINGLE_SHOT;

	@Option(names = "--flow", converter = FlowConverter.class,
			description = "What drives the build: java "
					+ "(default, the Java stages issue the generation call), pack (the builder's own "
					+ "builder-spec-one-shot pack issues it; its step declares the validate tool group, which "
					+ "the builder registers, so the step can call the validator), agentic (the builder's "
					+ "builder-agentic-blueprint pack issues it over two steps that read the build's documents "
					+ "and library through tools, and the build runs the validator and the scanner on each "
					+ "draft itself, re-asking with the findings when they report something), or harness (the "
					+ "builder's own builder-harness pack issues it over three steps that decide what to read "
					+ "next from the document roots this command opened, under the same forced checks)")
	BuildFlow flow = BuildFlow.JAVA;

	@Option(names = "--target", converter = TargetConverter.class,
			description = "Output target: agent-spec (default, an executable pack) or skill "
					+ "(a SKILL.md validated by the with/without-skill lift)")
	BuildTarget target = BuildTarget.AGENT_SPEC;

	private final AgentPlatform platform;

	private final Ai ai;

	/**
	 * What the agentic flow opens for the pack's tools to read, and it has to be
	 * <em>this</em> instance: the tool groups are beans that received the context's
	 * {@link ToolSurface} when the context started, so a surface built here instead would
	 * be populated and read by nobody. Hence the constructor takes it
	 * ({@code SmithBuilderApplication} passes the bean) rather than defaulting one.
	 */
	private final ToolSurface surface;

	/**
	 * The skill-script engine, present only when
	 * {@code smith.builder.skill-scripts.enabled} installed the bean. Null is an ordinary
	 * build: the skill path then has no script host, so its eval arms stay prompt
	 * substitution.
	 */
	private final SkillScriptExecutionEngine scriptEngine;

	public BuildCommand(AgentPlatform platform, Ai ai, ToolSurface surface) {
		this(platform, ai, surface, null);
	}

	public BuildCommand(AgentPlatform platform, Ai ai, ToolSurface surface, SkillScriptExecutionEngine scriptEngine) {
		this.platform = platform;
		this.ai = ai;
		this.surface = surface;
		this.scriptEngine = scriptEngine;
	}

	/**
	 * Test seam: allows substituting the build service.
	 * @param scratch the scratch directory the builder writes intermediate files into.
	 * @return the build service wired with the default compiler, invoker factory and
	 * rewriter.
	 */
	protected BuilderService newService(Path scratch) {
		AgentSpecInvokerFactory invokers = new AgentSpecInvokerFactory(this.platform);
		BuilderAgent agent = new BuilderAgent(generators(invokers), scratch.toString());
		PromptRewriter rewriter = new AiPromptRewriter(this.ai);
		return new BuilderService(agent, invokers, invokers, new SingleShotProcessSpecGenerator(this.ai), rewriter,
				defaultEvalTasks());
	}

	/**
	 * The generators the build drives — the one place {@code --flow} branches. The pack
	 * flow replaces the single-shot generator with the builder's own pack and leaves the
	 * Embabel technique out of the map entirely, so a build that asked for both has
	 * already been refused by {@link #call()} and {@code BuilderAgent} would refuse it
	 * again rather than fall back (design D4). The multi-agent candidate is untouched:
	 * the flow flag governs the shipped single-spec generation, which is what the pack
	 * flow is about.
	 * @param invokers the factory the pack flow deploys and invokes its pack through.
	 * @return the generators keyed by technique.
	 */
	protected Map<GenerationTechnique, AgentSpecGenerator> generators(InvokerFactory invokers) {
		if (this.flow == BuildFlow.PACK) {
			return Map.of(GenerationTechnique.SINGLE_SHOT,
					new PackDrivenAgentSpecGenerator(invokers, PackDrivenAgentSpecGenerator.builderPack()));
		}
		if (this.flow == BuildFlow.AGENTIC) {
			openToolSurface();
			return Map.of(GenerationTechnique.SINGLE_SHOT,
					new AgenticAgentSpecGenerator(invokers, this.platform, AgenticAgentSpecGenerator.builderPack()));
		}
		if (this.flow == BuildFlow.HARNESS) {
			openToolSurface();
			return Map.of(GenerationTechnique.SINGLE_SHOT, new HarnessAgentSpecGenerator(invokers, this.platform));
		}
		return AgentSpecGenerators.production(this.ai, this.platform);
	}

	/**
	 * Tells the tool groups what this build was given, which is the whole of a builder
	 * tool's reach: the documents from {@code --docs}, and the library only when
	 * {@code --out} names a {@code folder:} one — a {@code git:} source would mean the
	 * tool clones, and cloning is a write and a network call this surface does not make.
	 * The trace store is not wired by {@code smith build} at all, so the trace tools
	 * answer that honestly ({@code tools/TraceToolGroup}).
	 * <p>
	 * Both tool-using flows call this, and the {@link ToolSurface#startRun()} that opens
	 * it is load-bearing for the harness flow in particular: its steps re-read documents
	 * across three of them and across the driver's re-invocations, so the run-scoped
	 * dedupe has to be scoped to <em>this</em> build and not to whatever the process
	 * built last.
	 */
	private void openToolSurface() {
		this.surface.startRun();
		this.surface.openedTo((this.input.docs != null) ? List.of(Path.of(this.input.docs)) : List.of());
		this.surface.openedLibrary(libraryOf(this.out));
	}

	private static Path libraryOf(String out) {
		try {
			PublishTarget target = PublishTarget.parse(out);
			return (target.type() == PublishTarget.Type.FOLDER) ? Path.of(target.location()) : null;
		}
		catch (IllegalArgumentException ex) {
			// Already reported by the publish path; the library is simply nothing here.
			return null;
		}
	}

	/**
	 * The pack a pack-driving flow runs, as the refusal names it. Naming it resolves it,
	 * so the caller that prints this sits inside the command's failure handler: a pack
	 * that cannot be resolved then reports where it was looked for instead of throwing
	 * out of {@link #call()}.
	 * @param flow the flow asked for, which is not {@code java}
	 * @return the pack directory the flow deploys
	 */
	private static String packOf(BuildFlow flow) {
		if (flow == BuildFlow.AGENTIC) {
			return AgenticAgentSpecGenerator.builderPack().toString();
		}
		if (flow == BuildFlow.HARNESS) {
			return HarnessAgentSpecGenerator.builderPack().toString();
		}
		return PackDrivenAgentSpecGenerator.builderPack().toString();
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
	 * @return the skill service wired with the builder's model layer and bundled prompts,
	 * and with a script host only when the engine bean was present.
	 */
	protected SkillBuilderService newSkillService() {
		SkillScriptHost host = (this.scriptEngine != null) ? new SkillScriptHost(this.scriptEngine) : null;
		return new SkillBuilderService(new AiSpecPromptCaller(this.ai), host);
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
			// Checked here, before the documents are read and before --extract spends a
			// call: a combination with nothing to run is a refusal, not a fallback
			// (design D4). Inside the try because naming the pack means resolving it,
			// and a pack that cannot be resolved is then a build failure with a message
			// rather than a throwable escaping the command.
			if (this.flow != BuildFlow.JAVA && this.technique == GenerationTechnique.EMBABEL) {
				System.err.println("Refusing '--flow " + this.flow.id() + " --technique embabel' before any"
						+ " model call: the " + this.flow.id() + " flow runs " + packOf(this.flow) + ", whose"
						+ " steps are the single-shot prompt, and there is no pack for the Embabel NL compiler."
						+ " Running the single-shot prompt instead would make the flow flag a lie, so nothing falls"
						+ " back either. Use '--flow " + this.flow.id() + "' with '--technique single-shot', or"
						+ " '--flow java --technique embabel'.");
				return 1;
			}
			PublishTarget target = PublishTarget.parse(this.out);
			BuildOptions options = new BuildOptions(this.maxRefine, BuildOptions.DEFAULT_SCORE_GATE, this.overwrite,
					this.push, this.technique, this.target, this.minLift, null, this.maxInspect, this.fullInspection);
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
			BuildResult result = newService(scratch).build(description, target, options).withFlow(this.flow);
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

	/**
	 * Accepts a build flow by its user-facing name ({@code java} / {@code pack}), so a
	 * typo is a usage error (exit 2) rather than a silent fallback to the Java flow.
	 */
	public static class FlowConverter implements CommandLine.ITypeConverter<BuildFlow> {

		@Override
		public BuildFlow convert(String value) {
			return BuildFlow.parse(value);
		}

	}

}
