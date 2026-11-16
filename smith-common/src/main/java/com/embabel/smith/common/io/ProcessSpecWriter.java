/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.common.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.ProcessSpec;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Writes a {@link ProcessSpec} to disk as one directory per pack.
 *
 * <p>
 * Produces {@code process.yml} (the manifest, which carries the run order) and, beside
 * it, one agent-spec directory per agent, written by the shared {@link AgentSpecWriter}.
 * That layout is what makes a published process directly ingestible: a source that scans
 * a directory for pack candidates finds every agent of the process and can compose them
 * into one world.
 *
 * @author Ivan Rodriguez
 */
public class ProcessSpecWriter {

	private final ObjectMapper yaml = Yaml.mapper();

	private final AgentSpecWriter packWriter = new AgentSpecWriter();

	/**
	 * Writes the process under {@code dir}, creating directories as needed.
	 * @param process the process to write
	 * @param dir the directory to write the process under
	 * @return the directory the process was written to
	 */
	public Path write(ProcessSpec process, Path dir) throws IOException {
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("process.yml"), this.yaml.writeValueAsString(process.manifest()),
				StandardCharsets.UTF_8);

		int index = 0;
		for (AgentSpec pack : process.agents()) {
			this.packWriter.write(pack, dir.resolve(packDirectoryName(pack, index++)));
		}
		return dir;
	}

	/**
	 * The directory a pack is written under: its own name, which is also the name its
	 * manifest declares. An unnamed pack — one the model returned without a manifest name
	 * — falls back to its position so no pack is written into a shared directory.
	 * @param pack the pack to name a directory for
	 * @param index the pack's position in the process
	 * @return the directory name
	 */
	private static String packDirectoryName(AgentSpec pack, int index) {
		String name = pack.name();
		return (name != null && !name.isBlank()) ? name : "agent-" + index;
	}

}
