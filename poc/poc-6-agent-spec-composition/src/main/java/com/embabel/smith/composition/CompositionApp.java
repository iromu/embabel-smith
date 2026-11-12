package com.embabel.smith.composition;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.common.config.ModelEndpointConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * The spec-composition launcher (POC 6). Boots an Embabel {@link AgentPlatform}, composes two or
 * more independent agent specs, and runs a plan over the final goal so the GOAP planner chains an
 * action from each pack. The model endpoint is fully env-driven (see {@link ModelEndpointConfig}).
 *
 * <p>Run (with a live OpenAI-compatible endpoint):
 * <pre>
 *   export OPENAI_CUSTOM_BASE_URL=http://localhost:4000
 *   export OPENAI_CUSTOM_API_KEY=sk-...
 *   export OPENAI_CUSTOM_MODELS=my-model
 *   export EMBABEL_MODELS_DEFAULT_LLM=my-model
 *   BUILDER_PACK_DIRS="$PWD/agent-specs/compose-extract,$PWD/agent-specs/compose-explain" \
 *   BUILDER_TARGET_GOAL=explainerDone \
 *   BUILDER_INPUT="Explain how a coffee machine works" \
 *       mvn -pl poc/poc-6-spec-composition spring-boot:run
 * </pre>
 */
@SpringBootApplication
public class CompositionApp implements CommandLineRunner {

    private final AgentPlatform platform;
    private final ModelEndpointConfig endpoint;
    private final List<Path> packDirs;
    private final String targetGoal;
    private final String input;
    private final String plannerOverride;

    public CompositionApp(AgentPlatform platform,
                          @Value("${OPENAI_CUSTOM_BASE_URL:}") String baseUrl,
                          @Value("${OPENAI_CUSTOM_API_KEY:}") String apiKey,
                          @Value("${OPENAI_CUSTOM_MODELS:}") String models,
                          @Value("${EMBABEL_MODELS_DEFAULT_LLM:}") String defaultLlm,
                          @Value("${builder.pack-dirs:}") String packDirs,
                          @Value("${builder.target-goal:}") String targetGoal,
                          @Value("${builder.input:}") String input,
                          @Value("${builder.planner:}") String plannerOverride) {
        this.platform = platform;
        this.endpoint = new ModelEndpointConfig(baseUrl, apiKey, models, defaultLlm);
        this.packDirs = splitPaths(packDirs);
        this.targetGoal = targetGoal;
        this.input = input;
        this.plannerOverride = plannerOverride;
    }

    public static void main(String[] args) {
        SpringApplication.run(CompositionApp.class, args);
    }

    @Override
    public void run(String... args) {
        String configError = endpoint.configError();
        if (configError != null) {
            throw new IllegalStateException(configError);
        }
        if (packDirs.size() < 2) {
            throw new IllegalStateException("Composition needs at least two agent specs; got "
                    + packDirs.size() + ". Set builder.pack-dirs to a comma-separated list.");
        }
        if (input == null || input.isBlank()) {
            throw new IllegalStateException("builder.input is required");
        }

        System.out.println("=== Composing " + packDirs.size() + " specs over goal '" + targetGoal + "' ===");
        String result = new CompositionRunner(platform).compose(packDirs, targetGoal, input, plannerOverride);
        System.out.println("=== Composed output ===");
        System.out.println(result);
    }

    private static List<Path> splitPaths(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Paths::get)
                .toList();
    }
}
