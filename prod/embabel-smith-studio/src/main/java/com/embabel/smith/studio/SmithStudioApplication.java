/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

/**
 * Entry point of the self-improving studio product (change 06): a Spring Boot web
 * application that hosts the production cores — the runtime (change 03), the builder
 * (change 04), and the self-improvement loop (change 05) — behind a REST surface and a
 * Vue 3 + Quasar web UI.
 *
 * <p>
 * The model endpoint is fully env-driven (see {@code application.yml}); the app boots and
 * serves the UI even when no endpoint is configured, and model-backed actions report a
 * structured {@code MODEL_NOT_CONFIGURED} error rather than crashing. The built SPA is
 * served from {@code src/main/resources/static}.
 *
 * <p>
 * The web application type is fixed to {@code servlet} here (a system property) and also
 * in this module's {@code application.yml}: the embabel-agent-eval jar pulled in via the
 * builder ships its own classpath {@code application.properties} with
 * {@code spring.main.web-application-type=none} (it is a headless CLI tool), and Spring
 * Boot 4 re-binds {@code spring.main.*} from the environment at startup, so a
 * programmatic {@code .web(...)} value would be overridden by that file. A system
 * property has higher precedence than any classpath config file, so setting it here is
 * immune to the jar shadowing the module's {@code application.yml}.
 *
 * <p>
 * The datasource and JPA auto-configurations are excluded: storage is bound
 * programmatically and hot-swappable by
 * {@link com.embabel.smith.studio.storage.StorageContext} (change:
 * smith-studio-postgres-storage), so no Spring-managed {@code DataSource} / {@code
 * EntityManagerFactory} / repository beans may exist. With the datasource auto-config
 * off, the JPA entity-manager auto-config (which requires a {@code DataSource}) does not
 * fire either, and the app boots with no database configured at all — store operations
 * then report "storage not configured".
 *
 * @author Ivan Rodriguez
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class, DataJpaRepositoriesAutoConfiguration.class })
public class SmithStudioApplication {

	public static void main(String[] args) {
		// See the class javadoc: force servlet mode via a system property so the
		// transitive
		// embabel-agent-eval jar's web-application-type=none cannot shadow this module's
		// config.
		System.setProperty("spring.main.web-application-type", "SERVLET");
		new SpringApplicationBuilder(SmithStudioApplication.class).run(args);
	}

}
