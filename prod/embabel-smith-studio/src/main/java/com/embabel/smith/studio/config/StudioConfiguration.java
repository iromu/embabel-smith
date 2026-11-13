/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.studio.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.builder.BuildOptions;
import com.embabel.smith.builder.BuilderService;
import com.embabel.smith.builder.core.BuilderAgent;
import com.embabel.smith.builder.core.DefaultAgentSpecCompiler;
import com.embabel.smith.builder.eval.AgentSpecInvokerFactory;
import com.embabel.smith.builder.eval.EvalTask;
import com.embabel.smith.builder.eval.InvokerFactory;
import com.embabel.smith.builder.eval.LlmPromptRewriter;
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
import com.embabel.smith.studio.generate.FlowPromptCaller;
import com.embabel.smith.studio.generate.FlowValidator;
import com.embabel.smith.studio.generate.LlmFlowPromptCaller;
import com.embabel.smith.studio.generate.NlCompilerFlowGenerator;
import com.embabel.smith.studio.generate.ProcessExtractor;
import com.embabel.smith.studio.generate.ProcessFlowGenerator;
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

	@Bean
	public ModelEndpointConfig modelEndpointConfig(@Value("${OPENAI_CUSTOM_BASE_URL:}") String baseUrl,
			@Value("${OPENAI_CUSTOM_API_KEY:}") String apiKey, @Value("${OPENAI_CUSTOM_MODELS:}") String models,
			@Value("${EMBABEL_MODELS_DEFAULT_LLM:}") String defaultLlm) {
		return new ModelEndpointConfig(baseUrl, apiKey, models, defaultLlm);
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
	public BuilderService builderService(AgentPlatform platform, Ai ai, ModelEndpointConfig endpoint)
			throws IOException {
		Path scratch = Files.createTempDirectory("smith-studio-builder-scratch-");
		BuilderAgent agent = new BuilderAgent(new DefaultAgentSpecCompiler(ai), platform, scratch.toString());
		InvokerFactory invokerFactory = new AgentSpecInvokerFactory(platform);
		PromptRewriter rewriter = new LlmPromptRewriter(endpoint.baseUrl(), endpoint.apiKey(), model(endpoint));
		return new BuilderService(agent, invokerFactory, rewriter, defaultEvalTasks());
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
	 * The generation core: the process-to-flow prompt plus the endpoint-gated model
	 * caller.
	 * @param endpoint the model endpoint configuration.
	 * @return the generation core.
	 */
	@Bean
	public ProcessFlowGenerator processFlowGenerator(ModelEndpointConfig endpoint) {
		FlowPromptCaller caller = new LlmFlowPromptCaller(endpoint.baseUrl(), endpoint.apiKey(), model(endpoint));
		return new ProcessFlowGenerator(caller, loadPrompt("process_to_flow.jinja"));
	}

	/**
	 * The embabel NL-compiler generation technique: the builder's compile seam (backed by
	 * Embabel's NL compiler) plus the shared agent-spec mapper.
	 * @param ai the Embabel AI service that drives the NL compiler.
	 * @param platform the platform, which provides the shared type dictionary.
	 * @return the embabel NL-compiler generation core.
	 */
	@Bean
	public NlCompilerFlowGenerator nlCompilerFlowGenerator(Ai ai, AgentPlatform platform) {
		return new NlCompilerFlowGenerator(new DefaultAgentSpecCompiler(ai), platform);
	}

	/**
	 * The workspace's model-dependent generation: a blueprint and a technique to a generated
	 * pack plus flow issues. The technique picks the single-shot prompt or the embabel NL
	 * compiler; anything other than {@code embabel} uses the single-shot generator.
	 * @param singleShot the single-shot generation core.
	 * @param embabel the embabel NL-compiler generation core.
	 * @return the model-dependent generation for the workspace.
	 */
	@Bean
	public GenerateController.GenerateFn generateFn(ProcessFlowGenerator singleShot, NlCompilerFlowGenerator embabel) {
		return (blueprint, technique) -> {
			try {
				AgentSpec pack = isEmbabel(technique) ? embabel.generate(blueprint) : singleShot.generate(blueprint);
				return GenerateResponse.of(pack, FlowValidator.issues(pack));
			}
			catch (Exception ex) {
				String message = (ex.getMessage() == null) ? ex.toString() : ex.getMessage();
				return GenerateResponse.failed("Generation failed: " + message);
			}
		};
	}

	/**
	 * Whether the requested generation technique is the embabel NL compiler (the default,
	 * for any other value, is the single-shot prompt).
	 * @param technique the requested technique.
	 * @return true when the embabel NL compiler should be used.
	 */
	private static boolean isEmbabel(String technique) {
		return "embabel".equals(technique);
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
	 * The model-based process extraction: the discovery prompt plus the endpoint-gated
	 * model caller.
	 * @param endpoint the model endpoint configuration.
	 * @return the model-based process extraction.
	 */
	@Bean
	public ProcessExtractor processExtractor(ModelEndpointConfig endpoint) {
		FlowPromptCaller caller = new LlmFlowPromptCaller(endpoint.baseUrl(), endpoint.apiKey(), model(endpoint));
		return new ProcessExtractor(caller, loadPrompt("business_process_discovery.jinja"));
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
		return (pack, input) -> {
			try {
				// A folder source scans immediate subdirectories for packs, so the pack
				// must live in
				// its own named subdirectory, not at the folder root.
				Path tmp = Files.createTempDirectory("smith-simulate-");
				String name = (pack.name() == null || pack.name().isBlank()) ? "pack" : pack.name();
				writer.write(pack, tmp.resolve(name));
				AgentSpecRunService.RunResult result = runService.run("folder:" + tmp, pack.goal().name(), input,
						"GOAP", TrustTier.STANDARD);
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
	 * @param endpoint the model endpoint configuration.
	 * @return the model-dependent evaluation for the Eval pane.
	 */
	@Bean
	public EvalController.EvalFn evalFn(AgentPlatform platform, ModelEndpointConfig endpoint) {
		InvokerFactory invokerFactory = new AgentSpecInvokerFactory(platform);
		PromptRewriter rewriter = new LlmPromptRewriter(endpoint.baseUrl(), endpoint.apiKey(), model(endpoint));
		return (pack, tasks) -> new RefinementLoop(invokerFactory, rewriter, tasks, BuildOptions.DEFAULT_SCORE_GATE,
				BuildOptions.DEFAULT_MAX_REFINE)
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
			ModelEndpointConfig endpoint, AgentPlatform platform, Publisher loopPublisher,
			@Value("${smith.studio.loop.auto-publish:false}") boolean autoPublish) {
		PromptRewriter rewriter = new LlmPromptRewriter(endpoint.baseUrl(), endpoint.apiKey(), model(endpoint));
		InvokerFactory invokerFactory = new AgentSpecInvokerFactory(platform);
		// Always hold: auto-publish is applied at the LoopService layer so the toggle is
		// live. The
		// loop reads traces through the selected backend (JSONL file or database).
		return new ImprovementLoop(improveSource, storageContext.traceStore(), rewriter, invokerFactory,
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

	/**
	 * The model to call: the first of the configured models, falling back to the default
	 * LLM.
	 * @param endpoint the model endpoint configuration.
	 * @return the model to call.
	 */
	private static String model(ModelEndpointConfig endpoint) {
		String models = endpoint.models();
		if (models != null && !models.isBlank()) {
			return models.split(",")[0].trim();
		}
		return (endpoint.defaultLlm() == null) ? "" : endpoint.defaultLlm();
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
