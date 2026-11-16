/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.eval;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.embabel.smith.common.io.Yaml;

/**
 * Assembles the flat step-spec directory that Embabel's {@code YmlStepSpecRepository}
 * reads.
 *
 * <p>
 * A agent spec keeps {@code goal.yml} at its root and the action steps under
 * {@code steps/}, but the repository loads from a single directory. This copies every
 * pack's {@code goal.yml} (renamed to {@code goal-<i>.yml}) and its {@code steps/*.yml}
 * (prefixed {@code <i>-}) into one target directory so all goals and actions are ingested
 * together (and thus planned together). The source packs are left untouched. Extracted
 * from the runtime (POC 1) for the builder's endpoint-gated eval.
 *
 * @author Ivan Rodriguez
 */
public class StepSpecAssembler {

	private static final CopyOption[] COPY = { StandardCopyOption.REPLACE_EXISTING };

	/**
	 * Copies each pack's {@code goal.yml} and every {@code steps/*.yml} into
	 * {@code targetDir}.
	 * @param packDirs the source pack directories to assemble
	 * @param targetDir the directory to copy the flattened step specs into
	 * @return the target directory now holding all goals and action steps
	 */
	public Path assemble(List<Path> packDirs, Path targetDir) throws IOException {
		return assemble(packDirs, targetDir, null);
	}

	/**
	 * Assembles the same flat step-spec directory, pinning every action step to one LLM.
	 *
	 * <p>
	 * A model chosen by the caller is a property of the <em>run</em>, not of the pack:
	 * the pack on disk keeps exactly what it had, and only the staged copy carries the
	 * {@code llm.model} key Embabel reads per step. A step that already names an
	 * {@code llm} keeps its other settings and has only the model replaced.
	 * @param packDirs the source pack directories to assemble
	 * @param targetDir the directory to copy the flattened step specs into
	 * @param model the LLM every action step should use, or empty to leave each step's
	 * YAML as the pack wrote it
	 * @return the target directory now holding all goals and action steps
	 */
	public Path assemble(List<Path> packDirs, Path targetDir, String model) throws IOException {
		boolean pinModel = notBlank(model);
		Files.createDirectories(targetDir);
		int i = 0;
		for (Path packDir : packDirs) {
			Path goal = packDir.resolve("goal.yml");
			if (!Files.isRegularFile(goal)) {
				throw new IOException("agent spec has no goal.yml: " + packDir);
			}
			Files.copy(goal, targetDir.resolve("goal-" + i + ".yml"), COPY);
			for (Path step : yamlFiles(packDir.resolve("steps"))) {
				Path target = targetDir.resolve(i + "-" + step.getFileName().toString());
				if (pinModel) {
					writeWithModel(step, target, model);
				}
				else {
					Files.copy(step, target, COPY);
				}
			}
			i++;
		}
		return targetDir;
	}

	private void writeWithModel(Path source, Path target, String model) throws IOException {
		Map<String, Object> step = readStepMap(source);
		Object current = step.get("llm");
		Map<String, Object> llm = new LinkedHashMap<>();
		if (current instanceof Map<?, ?> currentLlm) {
			currentLlm.forEach((key, value) -> llm.put(String.valueOf(key), value));
		}
		llm.put("model", model);
		step.put("llm", llm);
		Yaml.mapper().writeValue(target.toFile(), step);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> readStepMap(Path source) throws IOException {
		return Yaml.mapper().readValue(source.toFile(), Map.class);
	}

	private static boolean notBlank(String s) {
		return s != null && !s.isBlank();
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
