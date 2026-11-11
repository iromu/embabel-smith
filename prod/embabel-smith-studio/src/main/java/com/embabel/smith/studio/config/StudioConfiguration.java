package com.embabel.smith.studio.config;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.builder.BuildOptions;
import com.embabel.smith.builder.BuilderService;
import com.embabel.smith.builder.core.BuilderAgent;
import com.embabel.smith.builder.core.DefaultSkillCompiler;
import com.embabel.smith.builder.eval.AgentSkillInvokerFactory;
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
import com.embabel.smith.common.io.SkillPackWriter;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.runtime.SkillRunService;
import com.embabel.smith.runtime.core.TrustTier;
import com.embabel.smith.runtime.trace.JsonlTraceStore;
import com.embabel.smith.skillsource.GitSkillSource;
import com.embabel.smith.skillsource.SkillSource;
import com.embabel.smith.studio.generate.BlueprintLoader;
import com.embabel.smith.studio.generate.FlowPromptCaller;
import com.embabel.smith.studio.generate.FlowValidator;
import com.embabel.smith.studio.generate.LlmFlowPromptCaller;
import com.embabel.smith.studio.discover.BusinessProcessDiscovery;
import com.embabel.smith.studio.generate.ProcessFlowGenerator;
import com.embabel.smith.studio.improve.ImprovementLoop;
import com.embabel.smith.studio.loop.LoopSchedulerState;
import com.embabel.smith.studio.rest.BuildController;
import com.embabel.smith.studio.rest.EvalController;
import com.embabel.smith.studio.rest.GenerateController;
import com.embabel.smith.studio.rest.RunController;
import com.embabel.smith.studio.rest.SimulateController;
import com.embabel.smith.studio.rest.Sources;
import com.embabel.smith.studio.rest.dto.GenerateResponse;
import com.embabel.smith.studio.rest.dto.SimulateResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Wires the production cores as beans for the studio (change 06): the model endpoint (env-driven),
 * the trace store, the run service (change 03), the builder service (change 04), and the
 * self-improvement loop (change 05) with its publisher, improve source, and control state. The
 * loop is always constructed in <em>hold</em> mode; auto-publish is toggled at runtime by the
 * {@link com.embabel.smith.studio.loop.LoopService}.
 */
@Configuration
@EnableScheduling
public class StudioConfiguration {

    @Bean
    public ModelEndpointConfig modelEndpointConfig(
            @Value("${OPENAI_CUSTOM_BASE_URL:}") String baseUrl,
            @Value("${OPENAI_CUSTOM_API_KEY:}") String apiKey,
            @Value("${OPENAI_CUSTOM_MODELS:}") String models,
            @Value("${EMBABEL_MODELS_DEFAULT_LLM:}") String defaultLlm) {
        return new ModelEndpointConfig(baseUrl, apiKey, models, defaultLlm);
    }

    @Bean
    public JsonlTraceStore traceStore(@Value("${smith.studio.trace-dir:}") String traceDir) {
        return new JsonlTraceStore(traceFile(traceDir));
    }

    @Bean
    public SkillRunService skillRunService(AgentPlatform platform, JsonlTraceStore traceStore) {
        return new SkillRunService(platform, traceStore.file());
    }

    @Bean
    public BuilderService builderService(AgentPlatform platform, Ai ai, ModelEndpointConfig endpoint)
            throws IOException {
        Path scratch = Files.createTempDirectory("smith-studio-builder-scratch-");
        BuilderAgent agent = new BuilderAgent(new DefaultSkillCompiler(ai), platform, scratch.toString());
        InvokerFactory invokerFactory = new AgentSkillInvokerFactory(platform);
        PromptRewriter rewriter = new LlmPromptRewriter(endpoint.baseUrl(), endpoint.apiKey(),
                model(endpoint));
        return new BuilderService(agent, invokerFactory, rewriter, defaultEvalTasks());
    }

    /** The Run pane's model-dependent run, backed by the change-03 run service. */
    @Bean
    public RunController.RunFn runFn(SkillRunService runService) {
        return runService::run;
    }

    /** The Author pane's model-dependent build, backed by the change-04 builder service. */
    @Bean
    public BuildController.BuildFn buildFn(BuilderService builderService) {
        return builderService::build;
    }

    /** Resolves a generate blueprint from a description or a path to business-process docs. */
    @Bean
    public BlueprintLoader blueprintLoader() {
        return new BlueprintLoader();
    }

    /** The generation core: the process-to-flow prompt plus the endpoint-gated model caller. */
    @Bean
    public ProcessFlowGenerator processFlowGenerator(ModelEndpointConfig endpoint) {
        FlowPromptCaller caller =
                new LlmFlowPromptCaller(endpoint.baseUrl(), endpoint.apiKey(), model(endpoint));
        return new ProcessFlowGenerator(caller, loadPrompt("process_to_flow.jinja"));
    }

    /** The workspace's model-dependent generation: blueprint to a generated pack plus flow issues. */
    @Bean
    public GenerateController.GenerateFn generateFn(ProcessFlowGenerator generator) {
        return blueprint -> {
            try {
                SkillPack pack = generator.generate(blueprint);
                return GenerateResponse.of(pack, FlowValidator.issues(pack));
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.toString() : e.getMessage();
                return GenerateResponse.failed("Generation failed: " + message);
            }
        };
    }

    /** Reads a prompt template from the classpath {@code prompts/} directory. */
    private static String loadPrompt(String name) {
        try (InputStream in = StudioConfiguration.class.getResourceAsStream("/prompts/" + name)) {
            if (in == null) {
                throw new IllegalStateException("prompt resource not found: /prompts/" + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load prompt " + name + ": " + e.getMessage(), e);
        }
    }

    /** The workspace's dry-run: write the pack to a temp folder and run it through the run service. */
    @Bean
    public SimulateController.SimulateFn simulateFn(SkillRunService runService) {
        SkillPackWriter writer = new SkillPackWriter();
        return (pack, input) -> {
            try {
                // A folder source scans immediate subdirectories for packs, so the pack must live in
                // its own named subdirectory, not at the folder root.
                Path tmp = Files.createTempDirectory("smith-simulate-");
                String name = pack.name() == null || pack.name().isBlank() ? "pack" : pack.name();
                writer.write(pack, tmp.resolve(name));
                SkillRunService.RunResult result =
                        runService.run("folder:" + tmp, pack.goal().name(), input, "GOAP", TrustTier.STANDARD);
                return SimulateResponse.of(result);
            } catch (IOException e) {
                throw new IllegalStateException("Simulate setup failed: " + e.getMessage(), e);
            }
        };
    }

    /** Loads business-process docs from a path or repo URL for the discovery/ingest step. */
    @Bean
    public BusinessProcessDiscovery businessProcessDiscovery() {
        return new BusinessProcessDiscovery();
    }

    /** The Eval pane's model-dependent evaluation: a bounded eval → refine loop over the tasks. */
    @Bean
    public EvalController.EvalFn evalFn(AgentPlatform platform, ModelEndpointConfig endpoint) {
        InvokerFactory invokerFactory = new AgentSkillInvokerFactory(platform);
        PromptRewriter rewriter = new LlmPromptRewriter(endpoint.baseUrl(), endpoint.apiKey(),
                model(endpoint));
        return (pack, tasks) -> new RefinementLoop(invokerFactory, rewriter, tasks,
                BuildOptions.DEFAULT_SCORE_GATE, BuildOptions.DEFAULT_MAX_REFINE).refine(pack);
    }

    /** The source the improvement loop watches and refines (defaults to the first configured source). */
    @Bean
    public SkillSource improveSource(
            @Value("${smith.studio.improve-source:}") String improveSource,
            @Value("${smith.studio.sources:}") String sources,
            @Value("${smith.studio.source-cache-dir:}") String cacheDir) throws IOException {
        String uri = improveSource;
        if (uri == null || uri.isBlank()) {
            uri = firstSource(sources);
        }
        if (uri == null || uri.isBlank()) {
            // No source configured: the loop is inert (an empty folder source yields no packs).
            Path empty = Files.createTempDirectory("smith-studio-empty-source-");
            return new com.embabel.smith.skillsource.FolderSkillSource("folder:" + empty, empty);
        }
        return Sources.parse(uri, cacheRoot(cacheDir));
    }

    @Bean
    public Publisher loopPublisher(SkillSource improveSource,
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
    public ImprovementLoop improvementLoop(SkillSource improveSource, JsonlTraceStore traceStore,
                                           ModelEndpointConfig endpoint, AgentPlatform platform,
                                           Publisher loopPublisher,
                                           @Value("${smith.studio.loop.auto-publish:false}") boolean autoPublish) {
        PromptRewriter rewriter = new LlmPromptRewriter(endpoint.baseUrl(), endpoint.apiKey(),
                model(endpoint));
        InvokerFactory invokerFactory = new AgentSkillInvokerFactory(platform);
        // Always hold: auto-publish is applied at the LoopService layer so the toggle is live.
        return new ImprovementLoop(improveSource, traceStore, rewriter, invokerFactory,
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

    /** The model to call: the first of the configured models, falling back to the default LLM. */
    private static String model(ModelEndpointConfig endpoint) {
        String models = endpoint.models();
        if (models != null && !models.isBlank()) {
            return models.split(",")[0].trim();
        }
        return endpoint.defaultLlm() == null ? "" : endpoint.defaultLlm();
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
            return GitSkillSource.defaultCacheRoot();
        }
        return expandHome(configured.trim());
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
