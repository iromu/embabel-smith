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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Writes a {@link AgentSpec} to disk in the agent spec directory format.
 *
 * <p>
 * Produces {@code manifest.yml}, {@code goal.yml}, one file per action under
 * {@code steps/}, and one file per intermediate type under {@code types/}. An empty
 * {@code types/} directory is still created (with a {@code .gitkeep}) so the layout is
 * complete even for packs with no intermediate types.
 *
 * @author Ivan Rodriguez
 */
public class AgentSpecWriter {

	private final ObjectMapper yaml = Yaml.mapper();

	/**
	 * Writes the pack under {@code dir}, creating directories as needed.
	 * @param pack the agent spec to write
	 * @param dir the directory to write the agent spec under
	 * @return the directory the pack was written to
	 */
	public Path write(AgentSpec pack, Path dir) throws IOException {
		Files.createDirectories(dir);

		writeYaml(dir.resolve("manifest.yml"), pack.manifest());
		writeYaml(dir.resolve("goal.yml"), pack.goal());

		Path stepsDir = dir.resolve("steps");
		Files.createDirectories(stepsDir);
		clearContents(stepsDir); // drop stale step files from a previous write to this
									// directory
		for (ActionStepSpec step : pack.steps()) {
			writeYaml(stepsDir.resolve(step.name() + ".yml"), step);
		}

		Path typesDir = dir.resolve("types");
		Files.createDirectories(typesDir);
		clearContents(typesDir);
		if (pack.types().isEmpty()) {
			Files.writeString(typesDir.resolve(".gitkeep"), "");
		}
		else {
			for (DynamicTypeDef type : pack.types()) {
				writeYaml(typesDir.resolve(type.name() + ".yml"), type);
			}
		}
		return dir;
	}

	/**
	 * Deletes every regular file in {@code dir} so a re-write is idempotent (no stale
	 * files).
	 * @param dir the directory to clear
	 */
	private void clearContents(Path dir) throws IOException {
		try (var files = Files.list(dir)) {
			for (Path file : files.filter(Files::isRegularFile).toList()) {
				Files.delete(file);
			}
		}
	}

	private void writeYaml(Path file, Object value) throws IOException {
		String content = this.yaml.writeValueAsString(value);
		Files.writeString(file, content, StandardCharsets.UTF_8);
	}

}
