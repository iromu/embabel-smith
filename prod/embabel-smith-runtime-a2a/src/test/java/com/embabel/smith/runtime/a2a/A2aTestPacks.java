/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.a2a;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Access to the agent specs under {@code src/test/resources/a2a-test-packs}: builds a
 * temporary {@code folder:} library from named packs. Staging is all the endpoint needs —
 * both the card and the run read the source itself, so nothing has to be deployed first,
 * and no model endpoint is involved.
 */
final class A2aTestPacks {

	static final String EXPLAIN = "explain";

	static final String EXTRACT = "extract";

	static final String SHELL = "shell";

	private A2aTestPacks() {
	}

	/**
	 * Copies the named packs into {@code target} as a {@code folder:} library.
	 * @param target the directory to stage into; created if missing, and reusable, so a
	 * second call adds packs to a library already staged there
	 * @param packNames the pack directories to copy
	 * @return {@code target}, for chaining
	 */
	static Path library(Path target, String... packNames) {
		try {
			Files.createDirectories(target);
			for (String pack : packNames) {
				copyRecursively(root().resolve(pack), target.resolve(pack));
			}
			return target;
		}
		catch (IOException ex) {
			throw new IllegalStateException("Cannot stage the A2A test library at " + target, ex);
		}
	}

	private static Path root() {
		try {
			return Path.of(Objects.requireNonNull(A2aTestPacks.class.getResource("/a2a-test-packs")).toURI());
		}
		catch (URISyntaxException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static void copyRecursively(Path from, Path to) throws IOException {
		try (Stream<Path> walk = Files.walk(from)) {
			for (Path path : walk.toList()) {
				Path destination = to.resolve(from.relativize(path).toString());
				if (Files.isDirectory(path)) {
					Files.createDirectories(destination);
				}
				else {
					Files.copy(path, destination);
				}
			}
		}
	}

}
