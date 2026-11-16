/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.smith.builder.BuildOptions;
import com.embabel.smith.builder.BuilderService;
import com.embabel.smith.builder.core.AgentSpecGenerator;
import com.embabel.smith.builder.core.AgentSpecGenerators;
import com.embabel.smith.builder.core.AiSpecPromptCaller;
import com.embabel.smith.builder.core.BuilderAgent;
import com.embabel.smith.builder.core.GenerationTechnique;
import com.embabel.smith.builder.core.SingleShotProcessSpecGenerator;
import com.embabel.smith.builder.core.SpecPromptCaller;
import com.embabel.smith.builder.eval.AgentSpecInvokerFactory;
import com.embabel.smith.builder.eval.AiPromptRewriter;
import com.embabel.smith.builder.eval.EvalTask;
import com.embabel.smith.builder.eval.InvokerFactory;
import com.embabel.smith.builder.eval.PromptRewriter;
import com.embabel.smith.builder.eval.RefinementLoop;
import com.embabel.smith.builder.publish.FolderPublisher;
import com.embabel.smith.builder.publish.GitPublisher;
import com.embabel.smith.builder.publish.PublishTarget;
import com.embabel.smith.builder.publish.Publisher;
import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.runtime.AgentSpecRunService;
import com.embabel.smith.runtime.core.TrustTier;
import com.embabel.smith.runtime.trace.JsonlTraceStore;
import com.embabel.smith.specsource.GitSpecSource;
import com.embabel.smith.specsource.SpecSource;
import com.embabel.smith.studio.discover.BusinessProcessDiscovery;
import com.embabel.smith.studio.generate.BlueprintLoader;
import com.embabel.smith.studio.generate.FlowValidator;
import com.embabel.smith.studio.generate.ProcessExtractor;
import com.embabel.smith.studio.improve.ImprovementLoop;
import com.embabel.smith.studio.loop.LoopSchedulerState;
import com.embabel.smith.studio.rest.BuildController;
import com.embabel.smith.studio.rest.EvalController;
import com.embabel.smith.studio.rest.ExtractController;
import com.embabel.smith.studio.rest.GenerateController;
import com.embabel.smith.studio.rest.RunController;
import com.embabel.smith.studio.rest.SimulateController;
import com.embabel.smith.studio.rest.Sources;
import com.embabel.smith.studio.rest.dto.GenerateResponse;
import com.embabel.smith.studio.rest.dto.SimulateResponse;
import com.embabel.smith.studio.storage.StorageContext;
import com.embabel.smith.studio.storage.StorageSettingsService;
import com.embabel.smith.studio.store.ProcessStore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wires the production cores as beans for the studio (change 06): the model endpoint
 * (env-driven), the trace store, the run service (change 03), the builder service (change
 * 04), and the self-improvement loop (change 05) with its publisher, improve source, and
 * control state. The loop is always constructed in <em>hold</em> mode; auto-publish is
 * toggled at runtime by the {@link com.embabel.smith.studio.loop.LoopService}.
 *
 * @author Ivan Rodriguez
 */
@Configuration
@EnableScheduling
public class StudioConfiguration {

	/**
	 * The endpoint the studio reports and gates model-backed actions on: whichever of the
	 * three providers the environment actually configures. The default LLM is read from
	 * its env var rather than the resolved {@code embabel.models.default-llm}, so the
	 * studio's inert placeholder for a no-endpoint boot does not read as an
	 * operator-chosen model.
	 * @param customBaseUrl the resolved {@code OPENAI_CUSTOM_BASE_URL}.
	 * @param customApiKey the resolved {@code OPENAI_CUSTOM_API_KEY}.
	 * @param customModels the resolved {@code OPENAI_CUSTOM_MODELS}.
	 * @param openAiApiKey the resolved {@code OPENAI_API_KEY}.
	 * @param anthropicApiKey the resolved {@code ANTHROPIC_API_KEY}.
	 * @param defaultLlm the resolved {@code EMBABEL_MODELS_DEFAULT_LLM}.
	 * @return the detected model endpoint.
	 */
	@Bean
	public ModelEndpointConfig modelEndpointConfig(@Value("${OPENAI_CUSTOM_BASE_URL:}") String customBaseUrl,
			@Value("${OPENAI_CUSTOM_API_KEY:}") String customApiKey,
			@Value("${OPENAI_CUSTOM_MODELS:}") String customModels, @Value("${OPENAI_API_KEY:}") String openAiApiKey,
			@Value("${ANTHROPIC_API_KEY:}") String anthropicApiKey,
			@Value("${EMBABEL_MODELS_DEFAULT_LLM:}") String defaultLlm) {
		return ModelEndpointConfig.detect(customBaseUrl, customApiKey, customModels, openAiApiKey, anthropicApiKey,
				defaultLlm);
	}

	/**
	 * The JSONL file trace store — the default
	 * {@link com.embabel.smith.runtime.trace.TraceStore} backend.
	 * @param traceDir the configured trace directory, or empty for the default.
	 * @return the JSONL file trace store.
	 */
	@Bean
	public JsonlTraceStore jsonlTraceStore(@Value("${smith.studio.trace-dir:}") String traceDir) {
		return new JsonlTraceStore(traceFile(traceDir));
	}

	/**
	 * The local settings document for storage (change: smith-studio-postgres-storage).
	 * @param storageDir the configured storage directory, or empty for the default.
	 * @return the local storage settings document.
	 */
	@Bean
	public StorageSettingsService storageSettingsService(@Value("${smith.studio.storage-dir:}") String storageDir) {
		return new StorageSettingsService(storageRoot(storageDir));
	}

	/**
	 * The hot-swappable storage binding and the selected trace backend (change:
	 * smith-studio-postgres-storage).
	 * @param settingsService the local storage settings document.
	 * @param jsonlTraceStore the JSONL file trace store.
	 * @param url the configured database URL, or empty for local.
	 * @param user the configured database user.
	 * @param password the configured database password.
	 * @return the hot-swappable storage binding.
	 */
	@Bean
	public StorageContext storageContext(StorageSettingsService settingsService, JsonlTraceStore jsonlTraceStore,
			@Value("${smith.studio.storage.url:}") String url, @Value("${smith.studio.storage.user:}") String user,
			@Value("${smith.studio.storage.password:}") String password) {
		return new StorageContext(settingsService, jsonlTraceStore, url, user, password);
	}

	@Bean
	public AgentSpecRunService agentSpecRunService(AgentPlatform platform, StorageContext storageContext) {
		return new AgentSpecRunService(platform, storageContext.traceStore());
	}

	@Bean
	public BuilderService builderService(AgentPlatform platform, Ai ai, PromptRewriter promptRewriter)
			throws IOException {
		Path scratch = Files.createTempDirectory("smith-studio-builder-scratch-");
		BuilderAgent agent = new BuilderAgent(AgentSpecGenerators.production(ai, platform), scratch.toString());
		AgentSpecInvokerFactory invokers = new AgentSpecInvokerFactory(platform);
		return new BuilderService(agent, invokers, invokers, new SingleShotProcessSpecGenerator(ai), promptRewriter,
				defaultEvalTasks());
	}

	/**
	 * The model caller the workspace's generation and extraction share: Embabel's
	 * {@link Ai} with the budget a full-spec answer needs, so neither truncates. The
	 * model itself is whatever the platform configuration selects.
	 * @param ai the Embabel AI service both calls go through.
	 * @return the shared model caller.
	 */
	@Bean
	public SpecPromptCaller specPromptCaller(Ai ai) {
		LlmOptions budget = LlmOptions.withDefaults().withTemperature(0.2).withMaxTokens(12000);
		return new AiSpecPromptCaller(ai, budget);
	}

	/**
	 * The eval refiner's model call, on the same model layer as generation.
	 * @param ai the Embabel AI service the rewriter calls through.
	 * @return the prompt rewriter.
	 */
	@Bean
	public PromptRewriter promptRewriter(Ai ai) {
		return new AiPromptRewriter(ai);
	}

	/**
	 * The Run pane's model-dependent run, backed by the change-03 run service.
	 * @param runService the run service to back the Run pane with.
	 * @return the model-dependent run for the Run pane.
	 */
	@Bean
	public RunController.RunFn runFn(AgentSpecRunService runService) {
		return runService::run;
	}

	/**
	 * The Author pane's model-dependent build, backed by the change-04 builder service.
	 * @param builderService the builder service to back the Author pane with.
	 * @return the model-dependent build for the Author pane.
	 */
	@Bean
	public BuildController.BuildFn buildFn(BuilderService builderService) {
		return builderService::build;
	}

	/**
	 * Resolves a generate blueprint from a description or a path to business-process
	 * docs.
	 * @return the blueprint resolver.
	 */
	@Bean
	public BlueprintLoader blueprintLoader() {
		return new BlueprintLoader();
	}

	/**
	 * The workspace's model-dependent generation: a blueprint and a technique to a
	 * generated pack plus flow issues. Both techniques are the builder's engine — the
	 * studio owns no generator — and any value other than {@code embabel} means the
	 * single-shot default.
	 * @param ai the Embabel AI service both techniques call through.
	 * @param platform the platform, which provides the shared type dictionary.
	 * @return the model-dependent generation for the workspace.
	 */
	@Bean
	public GenerateController.GenerateFn generateFn(Ai ai, AgentPlatform platform) {
		return generateFn(AgentSpecGenerators.production(ai, platform));
	}

	/**
	 * The dispatch over the builder's generators: the technique picks the generator, and
	 * any value other than {@code embabel} means the single-shot default. A generator
	 * failure is reported as a structured failure, not an exception.
	 * @param generators the builder's generators, keyed by technique.
	 * @return the model-dependent generation for the workspace.
	 */
	static GenerateController.GenerateFn generateFn(Map<GenerationTechnique, AgentSpecGenerator> generators) {
		return (blueprint, technique, model) -> {
			try {
				AgentSpec pack = generators.get(GenerationTechnique.from(technique)).generate(blueprint, model).pack();
				return GenerateResponse.of(pack, FlowValidator.issues(pack));
			}
			catch (Exception ex) {
				String message = (ex.getMessage() == null) ? ex.toString() : ex.getMessage();
				return GenerateResponse.failed("Generation failed: " + message);
			}
		};
	}

	/**
	 * The durable store for generated packs and discovery history, backed by the storage
	 * database.
	 * @param storageContext the storage binding to back the store with.
	 * @return the durable process store.
	 */
	@Bean
	public ProcessStore processStore(StorageContext storageContext) {
		return new ProcessStore(storageContext);
	}

	/**
	 * The model-based process extraction: the discovery prompt plus the model caller the
	 * workspace shares with generation.
	 * @param specPromptCaller the model caller to run the discovery prompt through.
	 * @return the model-based process extraction.
	 */
	@Bean
	public ProcessExtractor processExtractor(SpecPromptCaller specPromptCaller) {
		return new ProcessExtractor(specPromptCaller, loadPrompt("business_process_discovery.jinja"));
	}

	/**
	 * The workspace's model-based extraction: a docs corpus to a business-process
	 * description.
	 * @param extractor the extractor to run.
	 * @return the model-based extraction for the workspace.
	 */
	@Bean
	public ExtractController.ExtractFn extractFn(ProcessExtractor extractor) {
		return extractor::extract;
	}

	/**
	 * Reads a prompt template from the classpath {@code prompts/} directory.
	 * @param name the prompt template name.
	 * @return the prompt template text.
	 */
	private static String loadPrompt(String name) {
		try (InputStream in = StudioConfiguration.class.getResourceAsStream("/prompts/" + name)) {
			if (in == null) {
				throw new IllegalStateException("prompt resource not found: /prompts/" + name);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to load prompt " + name + ": " + ex.getMessage(), ex);
		}
	}

	/**
	 * The workspace's dry-run: write the pack to a temp folder and run it through the run
	 * service.
	 * @param runService the run service to dry-run the pack through.
	 * @return the model-dependent dry-run for the workspace.
	 */
	@Bean
	public SimulateController.SimulateFn simulateFn(AgentSpecRunService runService) {
		AgentSpecWriter writer = new AgentSpecWriter();
		return (pack, input, model) -> {
			try {
				// A folder source scans immediate subdirectories for packs, so the pack
				// must live in
				// its own named subdirectory, not at the folder root.
				Path tmp = Files.createTempDirectory("smith-simulate-");
				String name = (pack.name() == null || pack.name().isBlank()) ? "pack" : pack.name();
				writer.write(pack, tmp.resolve(name));
				AgentSpecRunService.RunResult result = runService.run("folder:" + tmp, pack.goal().name(), input,
						"GOAP", TrustTier.STANDARD, model);
				return SimulateResponse.of(result);
			}
			catch (IOException ex) {
				throw new IllegalStateException("Simulate setup failed: " + ex.getMessage(), ex);
			}
		};
	}

	/**
	 * Loads business-process docs from a path or repo URL for the discovery/ingest step.
	 * @return the business-process docs loader.
	 */
	@Bean
	public BusinessProcessDiscovery businessProcessDiscovery() {
		return new BusinessProcessDiscovery();
	}

	/**
	 * The Eval pane's model-dependent evaluation: a bounded eval → refine loop over the
	 * tasks.
	 * @param platform the agent platform the eval runs on.
	 * @param promptRewriter the model call the refiner revises prompts with.
	 * @return the model-dependent evaluation for the Eval pane.
	 */
	@Bean
	public EvalController.EvalFn evalFn(AgentPlatform platform, PromptRewriter promptRewriter) {
		InvokerFactory invokerFactory = new AgentSpecInvokerFactory(platform);
		return (pack, tasks, model) -> new RefinementLoop(invokerFactory, promptRewriter, tasks,
				BuildOptions.DEFAULT_SCORE_GATE, BuildOptions.DEFAULT_MAX_REFINE, model)
			.refine(pack);
	}

	/**
	 * The source the improvement loop watches and refines (defaults to the first
	 * configured source).
	 * @param improveSource the configured improve source URI, or empty for the default.
	 * @param sources the comma-separated configured sources.
	 * @param cacheDir the configured source cache directory.
	 * @return the improve source.
	 */
	@Bean
	public SpecSource improveSource(@Value("${smith.studio.improve-source:}") String improveSource,
			@Value("${smith.studio.sources:}") String sources,
			@Value("${smith.studio.source-cache-dir:}") String cacheDir) throws IOException {
		String uri = improveSource;
		if (uri == null || uri.isBlank()) {
			uri = firstSource(sources);
		}
		if (uri == null || uri.isBlank()) {
			// No source configured: the loop is inert (an empty folder source yields no
			// packs).
			Path empty = Files.createTempDirectory("smith-studio-empty-source-");
			return new com.embabel.smith.specsource.FolderSpecSource("folder:" + empty, empty);
		}
		return Sources.parse(uri, cacheRoot(cacheDir));
	}

	@Bean
	public Publisher loopPublisher(SpecSource improveSource,
			@Value("${smith.studio.improve-source:}") String improveSourceUri,
			@Value("${smith.studio.sources:}") String sources,
			@Value("${smith.studio.source-cache-dir:}") String cacheDir) {
		String uri = improveSourceUri;
		if (uri == null || uri.isBlank()) {
			uri = firstSource(sources);
		}
		if (uri == null || uri.isBlank()) {
			return new FolderPublisher(PublishTarget.parse("folder:" + Path.of(System.getProperty("java.io.tmpdir"))));
		}
		return publisherFor(uri, cacheRoot(cacheDir));
	}

	@Bean
	public ImprovementLoop improvementLoop(SpecSource improveSource, StorageContext storageContext,
			PromptRewriter promptRewriter, AgentPlatform platform, Publisher loopPublisher,
			@Value("${smith.studio.loop.auto-publish:false}") boolean autoPublish) {
		InvokerFactory invokerFactory = new AgentSpecInvokerFactory(platform);
		// Always hold: auto-publish is applied at the LoopService layer so the toggle is
		// live. The
		// loop reads traces through the selected backend (JSONL file or database).
		return new ImprovementLoop(improveSource, storageContext.traceStore(), promptRewriter, invokerFactory,
				defaultEvalTasks(), loopPublisher, false);
	}

	@Bean
	public LoopSchedulerState loopSchedulerState(
			@Value("${smith.studio.loop.auto-publish:false}") boolean autoPublish) {
		return new LoopSchedulerState(autoPublish);
	}

	private static Publisher publisherFor(String uri, Path cacheRoot) {
		PublishTarget target = PublishTarget.parse(uri);
		return switch (target.type()) {
			case FOLDER -> new FolderPublisher(target);
			case GIT -> new GitPublisher(target);
		};
	}

	private static List<EvalTask> defaultEvalTasks() {
		return EvalTask.smokeDefault();
	}

	private static String firstSource(String sources) {
		if (sources == null) {
			return null;
		}
		for (String s : sources.split(",")) {
			if (!s.isBlank()) {
				return s.trim();
			}
		}
		return null;
	}

	private static Path traceFile(String traceDir) {
		if (traceDir == null || traceDir.isBlank()) {
			return JsonlTraceStore.defaultPath();
		}
		return expandHome(traceDir.trim()).resolve("runs.jsonl");
	}

	private static Path cacheRoot(String configured) {
		if (configured == null || configured.isBlank()) {
			return GitSpecSource.defaultCacheRoot();
		}
		return expandHome(configured.trim());
	}

	/**
	 * The storage settings root: a configured dir, or the persistent
	 * {@code ~/.smith/studio} default.
	 * @param configured the configured storage directory, or empty for the default.
	 * @return the storage settings root.
	 */
	private static Path storageRoot(String configured) {
		if (configured != null && !configured.isBlank()) {
			return expandHome(configured.trim());
		}
		return Path.of(System.getProperty("user.home"), ".smith", "studio");
	}

	private static Path expandHome(String p) {
		if (p.equals("~")) {
			return Path.of(System.getProperty("user.home"));
		}
		if (p.startsWith("~/")) {
			return Path.of(System.getProperty("user.home"), p.substring(2));
		}
		return Path.of(p);
	}

}
