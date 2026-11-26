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
import com.embabel.smith.common.model.AgentsPack;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Writes an {@link AgentsPack} to disk as one directory per pack.
 *
 * <p>
 * Produces {@code process.yml} (the manifest, which carries the run order) and, beside
 * it, one agent-spec directory per agent, written by the shared {@link AgentSpecWriter}.
 * That layout is what makes a published agents-pack directly ingestible: a source that
 * scans a directory for pack candidates finds every agent of the agents-pack and can
 * compose them into one world.
 *
 * @author Ivan Rodriguez
 */
public class AgentsPackWriter {

	private final ObjectMapper yaml = Yaml.mapper();

	private final AgentSpecWriter packWriter = new AgentSpecWriter();

	/**
	 * Writes the agents-pack under {@code dir}, creating directories as needed.
	 * @param agentsPack the agents-pack to write
	 * @param dir the directory to write the agents-pack under
	 * @return the directory the agents-pack was written to
	 */
	public Path write(AgentsPack agentsPack, Path dir) throws IOException {
		Files.createDirectories(dir);
		// The manifest filename keeps its pre-agents-pack spelling: it is part of the
		// frozen
		// on-disk format, asserted by the round-trip test and by a frozen POC.
		Files.writeString(dir.resolve("process.yml"), this.yaml.writeValueAsString(agentsPack.manifest()),
				StandardCharsets.UTF_8);

		int index = 0;
		for (AgentSpec pack : agentsPack.agents()) {
			this.packWriter.write(pack, dir.resolve(packDirectoryName(pack, index++)));
		}
		return dir;
	}

	/**
	 * The directory a pack is written under: its own name, which is also the name its
	 * manifest declares. An unnamed pack — one the model returned without a manifest name
	 * — falls back to its position so no pack is written into a shared directory.
	 * @param pack the pack to name a directory for
	 * @param index the pack's position in the agents-pack
	 * @return the directory name
	 */
	private static String packDirectoryName(AgentSpec pack, int index) {
		String name = pack.name();
		return (name != null && !name.isBlank()) ? name : "agent-" + index;
	}

}
