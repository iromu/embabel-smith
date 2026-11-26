/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.a2a;

import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Contributes what a host cannot be asked to write for itself: the exclusion that keeps
 * the Embabel library's own A2A auto-configuration out of the process, and — when, and
 * only when, the host opts in — the servlet mode and the default port that make the
 * opt-in serve.
 *
 * <p>
 * <b>The exclusion applies whether or not the surface is enabled.</b> It is gated on the
 * class being present, not on {@code smith.a2a.enabled}, because the promise of this
 * library is that depending on it opens no endpoint: leaving the library's own
 * Autonomy-backed {@code /a2a} live in the disabled case would break that promise on the
 * one path where nobody asked for a surface. {@code AgentA2AAutoConfiguration} is a
 * component scan of {@code com.embabel.agent.a2a}, and that package declares an
 * unconditional {@code AgentCardHandler} bean. Two card handlers mean two mappings on
 * {@code /a2a}, so the library's registrar fails the boot with an ambiguous mapping. That
 * handler is also a surface this library does not want: it runs goals through Embabel
 * Autonomy, with no trust-tier gate and no trace record. A library cannot use
 * {@code @SpringBootApplication(exclude = …)}, so the exclusion goes into
 * {@code spring.autoconfigure.exclude} from the environment, which is the mechanism Boot
 * itself offers for it. A host's own entries are carried through rather than replaced,
 * and an exclusion the host already listed is not added a second time.
 *
 * <p>
 * <b>The servlet mode and the port are contributed only when the surface is enabled</b>,
 * which is how a library turns a port on without ever turning one on by itself: an
 * application class is not an option here (this module ships none), so the opt-in arrives
 * as properties instead. One source carries both keys and it is added with
 * {@code addLast} — deliberately the lowest precedence in the environment, below the
 * host's own configuration files, below {@code SERVER_PORT} and below
 * {@code SPRING_APPLICATION_JSON}. The consequence is stated rather than hidden: a host
 * that already chose a port keeps it, a host that is already servlet is unaffected, and a
 * host whose own {@code application.yml} states
 * {@code spring.main.web-application-type: none} keeps {@code none} — a flag set against
 * that line is a contradiction the host resolves in its own file, not one this library
 * overrides. A host with no opinion about either key gets servlet mode on {@code 18090},
 * which is the launcher's old default with no launcher.
 *
 * @author Ivan Rodriguez
 */
// Ordered last on purpose: EnvironmentPostProcessors are sorted, and this one has to run
// after ConfigDataEnvironmentPostProcessor so the host's application.yml — and a test's
// inlined properties — are already in the environment when the existing exclusions are
// read
// and when smith.a2a.enabled is resolved.
@Order(Ordered.LOWEST_PRECEDENCE)
public class SmithA2aEnvironmentPostProcessor implements EnvironmentPostProcessor {

	private static final String EXCLUDE_PROPERTY = "spring.autoconfigure.exclude";

	private static final String LIBRARY_AUTO_CONFIGURATION = "com.embabel.agent.autoconfigure.a2a.AgentA2AAutoConfiguration";

	private static final String EXCLUSION_SOURCE_NAME = "smithA2aAutoConfiguration";

	private static final String SURFACE_ENABLED_PROPERTY = "smith.a2a.enabled";

	private static final String WEB_APPLICATION_TYPE_PROPERTY = "spring.main.web-application-type";

	private static final String SERVER_PORT_PROPERTY = "server.port";

	private static final String SURFACE_SOURCE_NAME = "smithA2aSurface";

	/**
	 * The port a host gets when it turns the surface on without naming a port of its own.
	 */
	static final int DEFAULT_SERVER_PORT = 18090;

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		excludeLibraryAutoConfiguration(environment);
		contributeSurfaceDefaults(environment);
	}

	private void excludeLibraryAutoConfiguration(ConfigurableEnvironment environment) {
		if (!ClassUtils.isPresent(LIBRARY_AUTO_CONFIGURATION, getClass().getClassLoader())) {
			return;
		}
		String existing = environment.getProperty(EXCLUDE_PROPERTY, String.class, "");
		if (StringUtils.commaDelimitedListToSet(existing).contains(LIBRARY_AUTO_CONFIGURATION)) {
			return;
		}
		String exclusions = existing.isBlank() ? LIBRARY_AUTO_CONFIGURATION
				: existing + "," + LIBRARY_AUTO_CONFIGURATION;
		environment.getPropertySources()
			.addFirst(new MapPropertySource(EXCLUSION_SOURCE_NAME, Map.of(EXCLUDE_PROPERTY, exclusions)));
	}

	private void contributeSurfaceDefaults(ConfigurableEnvironment environment) {
		if (!environment.getProperty(SURFACE_ENABLED_PROPERTY, Boolean.class, false)) {
			return;
		}
		// addLast, not addFirst: see the class javadoc. Anything the host says about its
		// own
		// web application type or port — a config file, SERVER_PORT,
		// SPRING_APPLICATION_JSON, a test's inlined properties — outranks this
		// contribution.
		environment.getPropertySources()
			.addLast(new MapPropertySource(SURFACE_SOURCE_NAME, Map.of(WEB_APPLICATION_TYPE_PROPERTY, "servlet",
					SERVER_PORT_PROPERTY, Integer.toString(DEFAULT_SERVER_PORT))));
	}

}
