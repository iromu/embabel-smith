/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
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
 * <p>
 * A pack is read from wherever it was placed: {@link #read(Path)} takes one named on the
 * filesystem, {@link #readResource(String)} one that ships as a classpath directory. Both
 * end up in the same {@code read(Path)} body, so the two never disagree about the format.
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

	/**
	 * Reads a pack that ships as a directory on the classpath.
	 * @param resourcePath the slash-separated resource path of the pack directory, e.g.
	 * {@code specs/builder-single-spec}
	 * @return the pack those files declare
	 * @throws IOException when the pack is there but cannot be read
	 * @throws IllegalStateException when nothing is on the classpath at that location
	 */
	public AgentSpec readResource(String resourcePath) throws IOException {
		return read(SpecFolders.classpath(resourcePath));
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
