package com.embabel.smith.adapter;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.common.config.ModelEndpointConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Path;
import java.util.List;

/**
 * The skill runtime adapter launcher (POC 7). Boots an Embabel {@link AgentPlatform} (which
 * registers the {@code shell} tool group bean), ingests a CLI skill-pack, and runs it so the skill
 * executes a real command via the injected shell tool. The model endpoint is fully env-driven.
 *
 * <p>Run (with a live OpenAI-compatible endpoint):
 * <pre>
 *   export OPENAI_CUSTOM_BASE_URL=http://localhost:4000
 *   export OPENAI_CUSTOM_API_KEY=sk-...
 *   export OPENAI_CUSTOM_MODELS=my-model
 *   export EMBABEL_MODELS_DEFAULT_LLM=my-model
 *   BUILDER_PACK_DIR="$PWD/skill-packs/shell-runner" \
 *   BUILDER_INPUT="Run the command: uname -s, and report the result" \
 *       mvn -pl poc/poc-7-runtime-adapter spring-boot:run
 * </pre>
 */
@SpringBootApplication
public class AdapterApp implements CommandLineRunner {

    private final AgentPlatform platform;
    private final ModelEndpointConfig endpoint;
    private final Path packDir;
    private final String input;

    public AdapterApp(AgentPlatform platform,
                      @Value("${OPENAI_CUSTOM_BASE_URL:}") String baseUrl,
                      @Value("${OPENAI_CUSTOM_API_KEY:}") String apiKey,
                      @Value("${OPENAI_CUSTOM_MODELS:}") String models,
                      @Value("${EMBABEL_MODELS_DEFAULT_LLM:}") String defaultLlm,
                      @Value("${builder.pack-dir:skill-packs/shell-runner}") Path packDir,
                      @Value("${builder.input:}") String input) {
        this.platform = platform;
        this.endpoint = new ModelEndpointConfig(baseUrl, apiKey, models, defaultLlm);
        this.packDir = packDir;
        this.input = input;
    }

    public static void main(String[] args) {
        SpringApplication.run(AdapterApp.class, args);
    }

    @Override
    public void run(String... args) {
        String configError = endpoint.configError();
        if (configError != null) {
            throw new IllegalStateException(configError);
        }
        if (input == null || input.isBlank()) {
            throw new IllegalStateException("builder.input is required");
        }

        List<String> available = platform.getToolGroupResolver().availableToolGroups().stream()
                .map(m -> m.getRole())
                .sorted()
                .toList();
        System.out.println("=== Registered tool groups: " + available + " ===");
        if (!available.contains(ShellToolGroup.ROLE)) {
            System.out.println("WARNING: the 'shell' tool group is not registered; the skill will run without shell access.");
        }

        System.out.println("=== Running CLI skill over: " + input + " ===");
        String result = new AdapterRunner(platform).run(packDir, input);
        System.out.println("=== Skill output ===");
        System.out.println(result);
    }
}
