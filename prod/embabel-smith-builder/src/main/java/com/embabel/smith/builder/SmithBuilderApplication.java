package com.embabel.smith.builder;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.builder.cli.BuildCommand;
import com.embabel.smith.builder.cli.SmithBuilderCommand;
import com.embabel.smith.builder.core.BuilderAgent;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import picocli.CommandLine;

/**
 * Spring entry point of the builder product: boots the Embabel {@link AgentPlatform} and {@link Ai}
 * (model endpoint fully env-driven, see {@code application.yml}) and hands the CLI arguments to the
 * picocli command line ({@code smith build ...}).
 *
 * <p>{@link BuilderAgent} is a code-backed {@code @Agent} (hence a Spring {@code @Component} via the
 * annotation's meta-annotation) but is always constructed manually — by {@code BuildCommand} and the
 * tests — with an explicit scratch dir and compiler, never via the {@code AgentPlatform}. It is
 * excluded from component scanning so the context does not try (and fail) to instantiate it.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.embabel.smith.builder",
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = BuilderAgent.class))
public class SmithBuilderApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(SmithBuilderApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
        int exitCode;
        try {
            AgentPlatform platform = context.getBean(AgentPlatform.class);
            Ai ai = context.getBean(Ai.class);
            CommandLine smith = new CommandLine(new SmithBuilderCommand());
            smith.addSubcommand(new BuildCommand(platform, ai,
                    env("OPENAI_CUSTOM_BASE_URL"), env("OPENAI_CUSTOM_API_KEY"), firstModel(env("OPENAI_CUSTOM_MODELS"))));
            exitCode = smith.execute(args);
        } catch (Exception e) {
            System.err.println(e.getMessage() == null ? e.toString() : e.getMessage());
            exitCode = 1;
        } finally {
            context.close();
        }
        System.exit(exitCode);
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }

    private static String firstModel(String models) {
        if (models == null || models.isBlank()) {
            return "";
        }
        return models.split(",")[0].trim();
    }
}
