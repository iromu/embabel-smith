/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The product-wide model-transport invariant: every model call goes through the Embabel
 * model layer, so no production class of this module may build an HTTP client to reach an
 * endpoint itself. {@code java.net.http} is how a class here would do that, so its import
 * is what the test looks for. Fails the build if a raw client reappears.
 */
class NoDirectModelRequestTest {

	@Test
	void noProductionSourceImportsTheJdkHttpClient() throws IOException {
		Path sources = Path.of("src", "main", "java");
		assertThat(sources).as("this module's production sources").isDirectory();

		List<String> offenders;
		try (Stream<Path> files = Files.walk(sources)) {
			offenders = files.filter((path) -> path.toString().endsWith(".java"))
				.filter(this::importsHttpClient)
				.map(Path::toString)
				.collect(Collectors.toList());
		}
		assertThat(offenders).as("production sources that call a model endpoint directly").isEmpty();
	}

	private boolean importsHttpClient(Path path) {
		try {
			return Files.readString(path, StandardCharsets.UTF_8).contains("import java.net.http.");
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
