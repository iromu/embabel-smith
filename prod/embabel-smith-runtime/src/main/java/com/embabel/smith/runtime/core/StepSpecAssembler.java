/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.runtime.core;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Assembles the flat step-spec directory that Embabel's {@code YmlStepSpecRepository}
 * reads.
 *
 * <p>
 * A agent spec keeps {@code goal.yml} at its root and the action steps under
 * {@code steps/}, but the repository loads from a single directory. This copies every
 * pack's {@code goal.yml} (renamed to {@code goal-<i>.yml}) and its {@code steps/*.yml}
 * (prefixed {@code <i>-}) into one target directory so all goals and actions are ingested
 * together (and thus planned together). The per-pack prefixes keep same-named files from
 * different packs from clobbering each other. The source packs are left untouched.
 *
 * @author Ivan Rodriguez
 */
public class StepSpecAssembler {

	private static final CopyOption[] COPY = { StandardCopyOption.REPLACE_EXISTING };

	/**
	 * Copies each pack's {@code goal.yml} and every {@code steps/*.yml} into
	 * {@code targetDir}.
	 * @param packDirs the agent spec pack directories
	 * @param targetDir the flat step-spec directory to assemble
	 * @return the target directory now holding all goals and action steps
	 */
	public Path assemble(List<Path> packDirs, Path targetDir) throws IOException {
		Files.createDirectories(targetDir);
		int i = 0;
		for (Path packDir : packDirs) {
			Path goal = packDir.resolve("goal.yml");
			if (!Files.isRegularFile(goal)) {
				throw new IOException("agent spec has no goal.yml: " + packDir);
			}
			Files.copy(goal, targetDir.resolve("goal-" + i + ".yml"), COPY);
			for (Path step : yamlFiles(packDir.resolve("steps"))) {
				Files.copy(step, targetDir.resolve(i + "-" + step.getFileName().toString()), COPY);
			}
			i++;
		}
		return targetDir;
	}

	private List<Path> yamlFiles(Path dir) throws IOException {
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(Files::isRegularFile)
				.filter(this::isYaml)
				.sorted(Comparator.comparing(Path::toString))
				.toList();
		}
	}

	private boolean isYaml(Path file) {
		String name = file.getFileName().toString();
		return name.endsWith(".yml") || name.endsWith(".yaml");
	}

}
