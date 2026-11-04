package com.embabel.smith.unified;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * POC 22 launcher — the unified builder UI (doc 01's "missing piece").
 *
 * <p>A Spring Boot web app that serves the Vue 3 + Quasar + TypeScript SPA (built into
 * {@code static/}) and exposes a thin REST surface ({@code /api/*}) — one endpoint per pane — each
 * delegating to a proven POC class (the POC 15 pattern). No new agent/skill logic: the pixels drive
 * the same logic the offline POCs prove.
 *
 * <p>Run (pick a free port; 8080 may be taken by another local service):
 * <pre>
 *   cd frontend &amp;&amp; npm install &amp;&amp; npm run build   # emit the SPA into src/main/resources/static
 *   SERVER_PORT=18080 mvn -pl poc/poc-22-unified-ui spring-boot:run
 *   # then open http://localhost:18080/
 * </pre>
 */
@SpringBootApplication
public class UnifiedUiApp {

    public static void main(String[] args) {
        // Force the servlet web app: POC 9's transitive embabel-agent-eval leaks an
        // application.properties with spring.main.web-application-type=none (it is a CLI eval tool),
        // which would otherwise make this boot as a non-web app that exits after refresh.
        // application.properties shadows it; this programmatic set is a second layer.
        SpringApplication app = new SpringApplication(UnifiedUiApp.class);
        app.setWebApplicationType(WebApplicationType.SERVLET);
        app.run(args);
    }
}
