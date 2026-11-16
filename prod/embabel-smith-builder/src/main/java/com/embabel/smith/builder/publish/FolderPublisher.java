/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.publish;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Publishes a {@link Publishable} as a subdirectory named after it under the destination
 * folder (design D3): the artifact's files, whatever their shape — an agent-spec pack
 * with its {@code lifecycle.yml} sidecar, or a skill folder with its {@code SKILL.md} and
 * {@code evals.json}. A same-named subdirectory that already exists is a collision unless
 * {@code --overwrite} replaces it.
 *
 * @author Ivan Rodriguez
 */
public class FolderPublisher implements Publisher {

	private final PublishTarget target;

	public FolderPublisher(PublishTarget target) {
		this.target = target;
	}

	@Override
	public PublishResult publish(Publishable unit, boolean overwrite, boolean push) throws IOException {
		Path unitDir = Path.of(this.target.location()).resolve(unit.name());
		checkCollision(unitDir, unit.name(), overwrite);
		if (Files.isDirectory(unitDir)) {
			deleteTree(unitDir);
		}
		Files.createDirectories(unitDir);
		unit.writeTo(unitDir);
		return new PublishResult(unitDir, false);
	}

	private static void checkCollision(Path unitDir, String name, boolean overwrite) {
		if (Files.isDirectory(unitDir) && !overwrite) {
			throw new CollisionException("A published artifact named '" + name + "' already exists at " + unitDir
					+ "; pass --overwrite to replace it");
		}
	}

	private static void deleteTree(Path dir) {
		try (Stream<Path> stream = Files.walk(dir)) {
			stream.sorted(Comparator.reverseOrder()).forEach((path) -> {
				try {
					Files.delete(path);
				}
				catch (IOException ex) {
					throw new UncheckedIOException("cannot delete " + path, ex);
				}
			});
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot walk " + dir, ex);
		}
	}

}
