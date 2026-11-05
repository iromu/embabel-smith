package com.embabel.smith.runtime;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.runtime.cli.RunCommand;
import com.embabel.smith.runtime.cli.SmithCommand;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine;

/**
 * Spring entry point of the runtime product: boots the Embabel {@link AgentPlatform}
 * (model endpoint fully env-driven, see {@code application.yml}) and hands the CLI arguments to
 * the picocli command line ({@code smith run ...}).
 */
@SpringBootApplication
public class SmithRuntimeApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(SmithRuntimeApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
        int exitCode;
        try {
            AgentPlatform platform = context.getBean(AgentPlatform.class);
            CommandLine smith = new CommandLine(new SmithCommand());
            smith.addSubcommand(new RunCommand(platform));
            exitCode = smith.execute(args);
        } catch (Exception e) {
            System.err.println(e.getMessage() == null ? e.toString() : e.getMessage());
            exitCode = 1;
        } finally {
            context.close();
        }
        System.exit(exitCode);
    }
}
