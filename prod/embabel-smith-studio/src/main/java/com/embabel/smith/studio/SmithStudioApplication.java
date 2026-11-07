package com.embabel.smith.studio;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Entry point of the self-improving studio product: boots the Embabel {@code AgentPlatform} (model
 * endpoint fully env-driven, see {@code application.yml}) that hosts the runtime and the builder.
 * The self-improvement loop (change 05) is plain Java wired to this platform; the REST surface and
 * web UI are change 06.
 */
@SpringBootApplication
public class SmithStudioApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(SmithStudioApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }
}
