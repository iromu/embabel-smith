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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentsPack;
import com.embabel.smith.common.model.AgentsPackManifest;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads an agents-pack directory into an in-memory {@link AgentsPack}.
 *
 * <p>
 * Locates {@code process.yml} and every pack directory beside it — any immediate
 * subdirectory holding a {@code manifest.yml}, which is what a source uses to recognise a
 * pack — and returns them in the run order the manifest declares rather than the
 * directory listing's alphabetical order, because the order is what wires the agents to
 * each other.
 *
 * @author Ivan Rodriguez
 */
public class AgentsPackReader {

	private final ObjectMapper yaml = Yaml.mapper();

	private final AgentSpecReader packReader = new AgentSpecReader();

	/**
	 * Reads the agents-pack written under {@code dir}.
	 * @param dir the agents-pack directory
	 * @return the agents-pack, its agents in run order
	 */
	public AgentsPack read(Path dir) throws IOException {
		// The manifest filename keeps its pre-agents-pack spelling: it is part of the
		// frozen
		// on-disk format, asserted by the round-trip test and by a frozen POC.
		AgentsPackManifest manifest = this.yaml.readValue(Files.readString(dir.resolve("process.yml")),
				AgentsPackManifest.class);

		Map<String, AgentSpec> byName = new LinkedHashMap<>();
		for (Path packDir : packDirectories(dir)) {
			AgentSpec pack = this.packReader.read(packDir);
			byName.put(pack.name(), pack);
		}

		List<AgentSpec> ordered = new ArrayList<>();
		for (String name : manifest.agents()) {
			AgentSpec pack = byName.remove(name);
			if (pack != null) {
				ordered.add(pack);
			}
		}
		// A pack on disk that the manifest does not list is still part of the
		// agents-pack;
		// appending it keeps the read lossless rather than silently dropping an agent.
		ordered.addAll(byName.values());
		return new AgentsPack(manifest, ordered);
	}

	private List<Path> packDirectories(Path dir) throws IOException {
		try (Stream<Path> stream = Files.list(dir)) {
			return stream.filter(Files::isDirectory)
				.filter((candidate) -> Files.isRegularFile(candidate.resolve("manifest.yml")))
				.sorted(Comparator.comparing(Path::toString))
				.toList();
		}
	}

}
