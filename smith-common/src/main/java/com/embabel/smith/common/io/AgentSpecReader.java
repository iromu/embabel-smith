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

package com.embabel.smith.common.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.GoalStepSpec;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads a agent spec directory into an in-memory {@link AgentSpec}.
 *
 * <p>
 * Locates {@code manifest.yml}, {@code goal.yml}, the action steps under {@code steps/}
 * and the intermediate types under {@code types/}. Files that are not
 * {@code .yml}/{@code .yaml} (e.g. the {@code .gitkeep} placed in an empty
 * {@code types/}) are ignored.
 *
 * @author Ivan Rodriguez
 */
public class AgentSpecReader {

	private final ObjectMapper yaml = Yaml.mapper();

	public AgentSpec read(Path dir) throws IOException {
		AgentSpecManifest manifest = readYaml(dir.resolve("manifest.yml"), AgentSpecManifest.class);
		GoalStepSpec goal = readYaml(dir.resolve("goal.yml"), GoalStepSpec.class);

		List<ActionStepSpec> steps = new ArrayList<>();
		for (Path file : ymlFiles(dir.resolve("steps"))) {
			steps.add(readYaml(file, ActionStepSpec.class));
		}

		List<DynamicTypeDef> types = new ArrayList<>();
		for (Path file : ymlFiles(dir.resolve("types"))) {
			types.add(readYaml(file, DynamicTypeDef.class));
		}

		return new AgentSpec(manifest, goal, steps, types);
	}

	private List<Path> ymlFiles(Path dir) throws IOException {
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

	private <T> T readYaml(Path file, Class<T> type) throws IOException {
		return this.yaml.readValue(Files.readString(file), type);
	}

}
