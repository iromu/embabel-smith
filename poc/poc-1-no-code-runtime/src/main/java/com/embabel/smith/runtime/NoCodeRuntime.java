package com.embabel.smith.runtime;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.common.config.ModelEndpointConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Path;

/**
 * The fixed no-code runtime launcher (POC 1).
 *
 * <p>Boots an Embabel {@link AgentPlatform}, ingests a skill-pack from YAML with zero
 * compilation, and runs a plan over the exported goal. The model endpoint is fully env-driven
 * (see {@link ModelEndpointConfig}); the planner defaults to GOAP and is selectable per run.
 *
 * <p>Run (with a live OpenAI-compatible endpoint):
 * <pre>
 *   export OPENAI_CUSTOM_BASE_URL=http://localhost:4000
 *   export OPENAI_CUSTOM_API_KEY=sk-...
 *   export OPENAI_CUSTOM_MODELS=my-model
 *   export EMBABEL_MODELS_DEFAULT_LLM=my-model
 *   mvn -pl poc/poc-1-no-code-runtime spring-boot:run \
 *       -Dbuilder.input="Explain how a coffee machine works" -Dbuilder.planner=GOAP
 * </pre>
 */
@SpringBootApplication
public class NoCodeRuntime implements CommandLineRunner {

    private final AgentPlatform platform;
    private final ModelEndpointConfig endpoint;
    private final Path packDir;
    private final String input;
    private final String plannerOverride;

    public NoCodeRuntime(AgentPlatform platform,
                         @Value("${OPENAI_CUSTOM_BASE_URL:}") String baseUrl,
                         @Value("${OPENAI_CUSTOM_API_KEY:}") String apiKey,
                         @Value("${OPENAI_CUSTOM_MODELS:}") String models,
                         @Value("${EMBABEL_MODELS_DEFAULT_LLM:}") String defaultLlm,
                         @Value("${builder.pack-dir:skill-packs/explainer}") Path packDir,
                         @Value("${builder.input:}") String input,
                         @Value("${builder.planner:}") String plannerOverride) {
        this.platform = platform;
        this.endpoint = new ModelEndpointConfig(baseUrl, apiKey, models, defaultLlm);
        this.packDir = packDir;
        this.input = input;
        this.plannerOverride = plannerOverride;
    }

    public static void main(String[] args) {
        SpringApplication.run(NoCodeRuntime.class, args);
    }

    @Override
    public void run(String... args) {
        String configError = endpoint.configError();
        if (configError != null) {
            throw new IllegalStateException(configError);
        }

        String result = new SkillRunner(platform).run(packDir, input, plannerOverride);
        System.out.println("=== Skill output ===");
        System.out.println(result);
    }
}
