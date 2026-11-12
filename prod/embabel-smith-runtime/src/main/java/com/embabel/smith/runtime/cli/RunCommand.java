package com.embabel.smith.runtime.cli;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.runtime.AgentSpecRunService;
import com.embabel.smith.runtime.core.TrustTier;
import com.embabel.smith.runtime.trace.JsonlTraceStore;
import picocli.CommandLine.Option;
import picocli.CommandLine.Command;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * The {@code smith run} command: runs an exported goal over the agent specs in a source,
 * printing the goal output to stdout on success (exit 0) and a clear error to stderr on
 * failure (exit 1). Usage errors exit 2 (picocli default).
 */
@Command(name = "run", description = "Run an exported goal over the agent specs in a source")
public class RunCommand implements Callable<Integer> {

    @Option(names = "--source", required = true,
            description = "Spec source URI: folder:<path> or git:<url>[@ref]")
    String source;

    @Option(names = "--goal", description = "Goal to run (defaults to the single exported goal)")
    String goal;

    @Option(names = "--input", description = "Input bound to the goal's UserInput")
    String input;

    @Option(names = "--planner", description = "Planner override (default GOAP)")
    String planner;

    @Option(names = "--trust-tier", converter = CaseInsensitiveTrustTierConverter.class,
            description = "Trust tier: standard (default) or high")
    TrustTier trustTier = TrustTier.STANDARD;

    @Option(names = "--trace-dir", description = "Directory for the JSONL trace (default ~/.smith/traces)")
    Path traceDir;

    private final AgentPlatform platform;

    public RunCommand(AgentPlatform platform) {
        this.platform = platform;
    }

    /** Test seam: allows substituting the run service. */
    protected AgentSpecRunService newService(Path traceFile) {
        return new AgentSpecRunService(platform, traceFile);
    }

    @Override
    public Integer call() {
        try {
            Path traceFile = traceDir != null ? traceDir.resolve("runs.jsonl") : JsonlTraceStore.defaultPath();
            AgentSpecRunService.RunResult result = newService(traceFile)
                    .run(source, goal, input, planner, trustTier);
            if (result.success()) {
                System.out.println(result.output());
                return 0;
            }
            System.err.println(result.error());
            return 1;
        } catch (Exception e) {
            System.err.println(e.getMessage() == null ? e.toString() : e.getMessage());
            return 1;
        }
    }
}
