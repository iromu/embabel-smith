package com.embabel.smith.builder.cli;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.builder.BuildOptions;
import com.embabel.smith.builder.BuildResult;
import com.embabel.smith.builder.BuilderService;
import com.embabel.smith.builder.core.BuilderAgent;
import com.embabel.smith.builder.core.DefaultAgentSpecCompiler;
import com.embabel.smith.builder.eval.AgentSpecInvokerFactory;
import com.embabel.smith.builder.eval.EvalTask;
import com.embabel.smith.builder.eval.InvokerFactory;
import com.embabel.smith.builder.eval.LlmPromptRewriter;
import com.embabel.smith.builder.eval.PromptRewriter;
import com.embabel.smith.builder.publish.PublishTarget;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * The {@code smith build} command: builds a agent spec from a natural-language description, runs it
 * through eval → refine → security → lifecycle, and publishes it to a source. The build report goes
 * to stdout; a blocked build prints the stage and reason and exits 1. Usage errors exit 2 (picocli
 * default).
 */
@Command(name = "build", description = "Build a agent spec from a description and publish it to a source")
public class BuildCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "The natural-language description of the spec to build")
    String description;

    @Option(names = "--out", required = true,
            description = "Source URI to publish to: folder:<abs-path> or git:<url>[@ref]")
    String out;

    @Option(names = "--max-refine", description = "Maximum refinement rounds (default ${DEFAULT-VALUE})")
    int maxRefine = BuildOptions.DEFAULT_MAX_REFINE;

    @Option(names = "--overwrite", description = "Overwrite an existing pack of the same name")
    boolean overwrite;

    @Option(names = "--push", description = "Push the git commit to the remote (default: local commit only)")
    boolean push;

    private final AgentPlatform platform;
    private final Ai ai;
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public BuildCommand(AgentPlatform platform, Ai ai, String baseUrl, String apiKey, String model) {
        this.platform = platform;
        this.ai = ai;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    /** Test seam: allows substituting the build service. */
    protected BuilderService newService(Path scratch) {
        BuilderAgent agent = new BuilderAgent(new DefaultAgentSpecCompiler(ai), platform, scratch.toString());
        InvokerFactory factory = new AgentSpecInvokerFactory(platform);
        PromptRewriter rewriter = new LlmPromptRewriter(baseUrl, apiKey, model);
        return new BuilderService(agent, factory, rewriter, defaultEvalTasks());
    }

    /**
     * The default eval task set: a single "the spec runs and produces output" case, shared via
     * {@link EvalTask#smokeDefault()}. The task set is a parameter of the build (design D1); richer
     * acceptance criteria can be supplied by callers of {@link BuilderService}.
     */
    private static List<EvalTask> defaultEvalTasks() {
        return EvalTask.smokeDefault();
    }

    @Override
    public Integer call() {
        // The scratch dir is only used by the BuilderAgent write step; delete it once the build is
        // done so we don't leak a temp dir per invocation.
        Path scratch;
        try {
            scratch = Files.createTempDirectory("smith-builder-scratch-");
        } catch (IOException e) {
            System.err.println("cannot create builder scratch dir: " + e.getMessage());
            return 1;
        }
        try {
            PublishTarget target = PublishTarget.parse(out);
            BuildOptions options = new BuildOptions(maxRefine, BuildOptions.DEFAULT_SCORE_GATE, overwrite, push);
            BuildResult result = newService(scratch).build(description, target, options);
            System.out.print(result.report());
            return result.isBuilt() ? 0 : 1;
        } catch (Exception e) {
            System.err.println(e.getMessage() == null ? e.toString() : e.getMessage());
            return 1;
        } finally {
            deleteTree(scratch);
        }
    }

    private static void deleteTree(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException ignored) {
                    // best-effort cleanup; ignore failures
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup; ignore failures
        }
    }
}
