/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.common.io;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.AgentsPack;
import com.embabel.smith.common.samples.SampleAgentSpecs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Offline round-trip for an agents-pack: writing it produces one directory per pack, and
 * reading it back returns the same packs in the order the agents-pack runs.
 *
 * @author Ivan Rodriguez
 */
class AgentsPackRoundTripTest {

	private final AgentsPackWriter writer = new AgentsPackWriter();

	private final AgentsPackReader reader = new AgentsPackReader();

	@Test
	void writesOneDirectoryPerPackBesideThePackManifest(@TempDir Path dir) throws Exception {
		this.writer.write(agentsPack("explainer_process", pack("research"), pack("write")), dir);

		assertThat(Files.isRegularFile(dir.resolve("process.yml"))).isTrue();
		assertThat(Files.isRegularFile(dir.resolve("research/manifest.yml"))).isTrue();
		assertThat(Files.isRegularFile(dir.resolve("research/goal.yml"))).isTrue();
		assertThat(Files.isRegularFile(dir.resolve("research/steps/extractTopic.yml"))).isTrue();
		assertThat(Files.isRegularFile(dir.resolve("research/types/TopicBrief.yml"))).isTrue();
		assertThat(Files.isRegularFile(dir.resolve("write/manifest.yml"))).isTrue();
	}

	@Test
	void readsBackTheSamePacks(@TempDir Path dir) throws Exception {
		AgentsPack agentsPack = agentsPack("explainer_process", pack("research"), pack("write"));
		this.writer.write(agentsPack, dir);

		assertThat(this.reader.read(dir)).isEqualTo(agentsPack);
	}

	@Test
	void readsThePacksBackInTheRunOrderNotTheDirectoryOrder(@TempDir Path dir) throws Exception {
		// "alpha" sorts before "zebra", so the directory listing and the run order
		// genuinely disagree: the manifest's order must win.
		AgentsPack agentsPack = agentsPack("trading", pack("zebra"), pack("alpha"));
		this.writer.write(agentsPack, dir);

		assertThat(this.reader.read(dir).agents()).extracting(AgentSpec::name).containsExactly("zebra", "alpha");
	}

	@Test
	void everyPublishedPackIsIngestibleOnItsOwn(@TempDir Path dir) throws Exception {
		this.writer.write(agentsPack("explainer_process", pack("research"), pack("write")), dir);

		// A source recognises a pack by the manifest.yml in its directory: every agent of
		// the pack qualifies, so the runtime can ingest the published agents-pack whole.
		try (var subdirs = Files.list(dir)) {
			assertThat(subdirs.filter(Files::isDirectory))
				.allMatch((d) -> Files.isRegularFile(d.resolve("manifest.yml")));
		}
	}

	private static AgentsPack agentsPack(String name, AgentSpec... agents) {
		return new AgentsPack(name, List.of(agents));
	}

	private static AgentSpec pack(String name) {
		AgentSpec sample = SampleAgentSpecs.explainer();
		return new AgentSpec(new AgentSpecManifest(name, "1.0.0"), sample.goal(), sample.steps(), sample.types());
	}

}
