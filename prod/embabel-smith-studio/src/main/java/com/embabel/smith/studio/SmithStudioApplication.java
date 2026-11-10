package com.embabel.smith.studio;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Entry point of the self-improving studio product (change 06): a Spring Boot web application that
 * hosts the production cores — the runtime (change 03), the builder (change 04), and the
 * self-improvement loop (change 05) — behind a REST surface and a Vue 3 + Quasar web UI.
 *
 * <p>The model endpoint is fully env-driven (see {@code application.yml}); the app boots and serves
 * the UI even when no endpoint is configured, and model-backed actions report a structured
 * {@code MODEL_NOT_CONFIGURED} error rather than crashing. The built SPA is served from
 * {@code src/main/resources/static}.
 *
 * <p>The web application type is fixed to {@code servlet} in this module's {@code application.yml}
 * (not here): the embabel-agent-eval jar pulled in via the builder ships its own classpath
 * {@code application.properties} with {@code spring.main.web-application-type=none} (it is a headless
 * CLI tool), and Spring Boot 4 re-binds {@code spring.main.*} from the environment at startup, so a
 * programmatic {@code .web(...)} value would be overridden by that file. This module's own
 * {@code application.yml} takes precedence over the jar's file.
 */
@SpringBootApplication
public class SmithStudioApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(SmithStudioApplication.class).run(args);
    }
}
