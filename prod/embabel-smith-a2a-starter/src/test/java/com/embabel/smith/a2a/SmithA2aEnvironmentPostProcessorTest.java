/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.a2a;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The environment contribution itself, asserted rather than assumed: what the
 * post-processor puts in when the surface is on, and the fact that it puts in nothing but
 * the exclusion when it is off.
 *
 * <p>
 * The precedence half is the part a boot test cannot show. The contributed source is
 * added with {@code addLast}, so every property source the host brought with it — its
 * {@code application.yml}, {@code SERVER_PORT}, {@code SPRING_APPLICATION_JSON}, a test's
 * inlined properties — outranks it. That is asserted here against a host source standing
 * in for all of those, because the failure mode it guards against is silent: an
 * {@code addFirst} would still leave every boot test in this repository green while
 * quietly making this library able to overrule the web application type of every process
 * that depends on it.
 *
 * @author Ivan Rodriguez
 */
class SmithA2aEnvironmentPostProcessorTest {

	private static final String SURFACE_SOURCE = "smithA2aSurface";

	private static final String WEB_APPLICATION_TYPE = "spring.main.web-application-type";

	private static final String SERVER_PORT = "server.port";

	private final SmithA2aEnvironmentPostProcessor postProcessor = new SmithA2aEnvironmentPostProcessor();

	@Test
	void enabledSurfaceContributesServletModeAndTheDefaultPort() {
		MockEnvironment environment = enabled();

		assertThat(environment.getProperty(WEB_APPLICATION_TYPE)).isEqualTo("servlet");
		assertThat(environment.getProperty(SERVER_PORT))
			.isEqualTo(Integer.toString(SmithA2aEnvironmentPostProcessor.DEFAULT_SERVER_PORT));
		assertThat(sourceNames(environment)).contains(SURFACE_SOURCE);
	}

	@Test
	void disabledSurfaceContributesNeitherServletModeNorAPort() {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("smith.a2a.enabled", "false");

		this.postProcessor.postProcessEnvironment(environment, application());

		assertThat(sourceNames(environment)).doesNotContain(SURFACE_SOURCE);
		assertThat(environment.getProperty(WEB_APPLICATION_TYPE)).isNull();
		assertThat(environment.getProperty(SERVER_PORT)).isNull();
	}

	@Test
	void absentFlagContributesNeitherServletModeNorAPort() {
		MockEnvironment environment = new MockEnvironment();

		this.postProcessor.postProcessEnvironment(environment, application());

		assertThat(sourceNames(environment)).doesNotContain(SURFACE_SOURCE);
		assertThat(environment.getProperty(WEB_APPLICATION_TYPE)).isNull();
		assertThat(environment.getProperty(SERVER_PORT)).isNull();
	}

	@Test
	void theHostSOwnConfigurationOutranksTheContribution() {
		MockEnvironment environment = new MockEnvironment();
		// Stands in for the host's application.yml, SERVER_PORT and
		// SPRING_APPLICATION_JSON,
		// all of which are property sources the host already owns.
		environment.getPropertySources()
			.addFirst(new MapPropertySource("hostConfiguration",
					Map.of(SERVER_PORT, "19500", WEB_APPLICATION_TYPE, "reactive")));
		environment.setProperty("smith.a2a.enabled", "true");

		this.postProcessor.postProcessEnvironment(environment, application());

		assertThat(environment.getProperty(SERVER_PORT)).isEqualTo("19500");
		assertThat(environment.getProperty(WEB_APPLICATION_TYPE)).isEqualTo("reactive");
		// Present, and behind everything the host brought with it.
		assertThat(sourceNames(environment)).contains(SURFACE_SOURCE);
		assertThat(sourceNames(environment).indexOf(SURFACE_SOURCE)).isGreaterThan(0);
	}

	private MockEnvironment enabled() {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("smith.a2a.enabled", "true");
		this.postProcessor.postProcessEnvironment(environment, application());
		return environment;
	}

	private static List<String> sourceNames(MockEnvironment environment) {
		return environment.getPropertySources()
			.stream()
			.map(org.springframework.core.env.PropertySource::getName)
			.toList();
	}

	private static SpringApplication application() {
		return new SpringApplication(SmithA2aEnvironmentPostProcessorTest.class);
	}

}
