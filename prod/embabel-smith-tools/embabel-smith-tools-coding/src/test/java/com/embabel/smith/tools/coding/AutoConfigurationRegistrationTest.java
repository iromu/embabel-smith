/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The addon is picked up by a host that never mentions it, which needs two facts to hold
 * and both are asserted here rather than assumed.
 *
 * <p>
 * The boot-state tests boot through {@code @EnableAutoConfiguration}, so between them
 * they prove the whole route: a jar on a classpath, one registration line, conditions
 * deciding what the conditions decide. This class checks the two halves of the route
 * themselves, so a broken registration fails with a sentence about the registration
 * rather than as four unexplained empty contexts.
 *
 * @author Ivan Rodriguez
 */
class AutoConfigurationRegistrationTest {

	private static final String REGISTRATION = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

	@Test
	@DisplayName("the entry point is an auto-configuration")
	void entryPointIsAnAutoConfiguration() {
		assertThat(CodingToolsAutoConfiguration.class).hasAnnotation(AutoConfiguration.class);
	}

	@Test
	@DisplayName("it is registered on the classpath, which is the only way a host learns of it")
	void registeredThroughTheImportsFile() throws IOException {
		assertThat(CodingToolsAutoConfiguration.class.getClassLoader().getResource(REGISTRATION))
			.as("registration file")
			.isNotNull();
		try (InputStream resource = CodingToolsAutoConfiguration.class.getClassLoader()
			.getResourceAsStream(REGISTRATION)) {
			String registered = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
			assertThat(Arrays.asList(registered.split("\\R"))).contains(CodingToolsAutoConfiguration.class.getName());
		}
	}

	@Test
	@DisplayName("every property the module reads is documented")
	void propertiesAreDocumented() throws IOException {
		String metadata = metadata();
		assertThat(metadata).contains("smith.tools.file.enabled")
			.contains("smith.tools.shell.enabled")
			.contains("smith.tools.coding.root")
			.contains("smith.tools.coding.command-timeout")
			.contains("smith.tools.coding.max-output-chars");
	}

	@Test
	@DisplayName("the documented defaults are the ones the annotations claim")
	void documentedDefaultsMatchTheConditions() throws IOException {
		String metadata = metadata();
		// file on, shell off — the asymmetry is the point, so both are checked.
		assertThat(defaultFor(metadata, "smith.tools.file.enabled")).isEqualTo("true");
		assertThat(defaultFor(metadata, "smith.tools.shell.enabled")).isEqualTo("false");
	}

	private static String metadata() throws IOException {
		try (InputStream resource = CodingToolsAutoConfiguration.class.getClassLoader()
			.getResourceAsStream("META-INF/spring-configuration-metadata.json")) {
			assertThat(resource).as("spring-configuration-metadata.json").isNotNull();
			return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/**
	 * The default a property declares, as the metadata file spells it.
	 * @param metadata the metadata file
	 * @param property the property name
	 * @return the default, lower-cased, or empty when the property declares none
	 */
	private static String defaultFor(String metadata, String property) {
		int at = metadata.indexOf("\"" + property + "\"");
		assertThat(at).as("property %s is documented", property).isGreaterThan(-1);
		int end = metadata.indexOf("\"defaultValue\"", at);
		int valueStart = end + "\"defaultValue\"".length();
		int valueEnd = metadata.indexOf(",", end);
		int brace = metadata.indexOf("}", end);
		if (brace != -1 && (valueEnd == -1 || brace < valueEnd)) {
			valueEnd = brace;
		}
		return metadata.substring(metadata.indexOf(':', valueStart) + 1, valueEnd)
			.trim()
			.replace("\"", "")
			.toLowerCase();
	}

}
