package com.embabel.smith.ui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * POC 15 launcher — the visual spec-chain editor (doc 03 §2.2).
 *
 * <p>A small, offline Spring Boot web app. It serves a self-contained drag-and-drop canvas
 * ({@code static/index.html}) and exposes REST endpoints that wrap POC 14's proven composer model:
 * {@code GET /api/specs} (the available specs and their I/O types), {@code POST /api/canvas}
 * (validate a canvas — type-checked edges + the composed chain), and {@code POST /api/compose}
 * (export the canvas as a composite agent spec). The drag-and-drop "snap" is POC 14's edge
 * type-check. No model endpoint is needed.
 *
 * <p>Run (pick a free port; 8080 may be taken by another local service):
 * <pre>
 *   SERVER_PORT=18080 mvn -pl poc/poc-15-composer-ui spring-boot:run
 *   # then open http://localhost:18080/
 * </pre>
 */
@SpringBootApplication
public class ComposerUiApp {

    public static void main(String[] args) {
        // Force the servlet web app: POC 9's transitive embabel-agent-eval leaks an
        // application.properties with spring.main.web-application-type=none (it is a CLI eval tool),
        // which would otherwise make this builder UI boot as a non-web app that exits after refresh.
        // application.properties shadows it; this programmatic set is a second layer.
        SpringApplication app = new SpringApplication(ComposerUiApp.class);
        app.setWebApplicationType(WebApplicationType.SERVLET);
        app.run(args);
    }
}
