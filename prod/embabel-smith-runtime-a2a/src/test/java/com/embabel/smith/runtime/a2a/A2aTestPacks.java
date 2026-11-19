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

import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.runtime.core.RuntimeCore;
import com.embabel.smith.specsource.SpecSource;

/**
 * Access to the agent specs under {@code src/test/resources/a2a-test-packs}: builds a
 * temporary {@code folder:} library from named packs, and deploys one so its goals reach
 * the platform the agent card is built from. Ingesting deploys but never runs, so it
 * needs no model endpoint.
 */
final class A2aTestPacks {

	static final String EXPLAIN = "explain";

	static final String EXTRACT = "extract";

	static final String SHELL = "shell";

	private A2aTestPacks() {
	}

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

	/**
	 * Deploys a library's packs into the platform, which is what puts their goals on the
	 * agent card.
	 * @param platform the platform the card handler reads goals from
	 * @param library the directory {@link #library(Path, String...)} staged
	 */
	static void deploy(AgentPlatform platform, Path library) {
		new RuntimeCore(platform).ingest(SpecSource.parse("folder:" + library).resolve());
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
