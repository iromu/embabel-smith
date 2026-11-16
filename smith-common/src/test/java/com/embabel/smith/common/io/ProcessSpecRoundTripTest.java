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
import com.embabel.smith.common.model.ProcessSpec;
import com.embabel.smith.common.samples.SampleAgentSpecs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Offline round-trip for a process: writing it produces one directory per pack, and
 * reading it back returns the same packs in the order the process runs.
 *
 * @author Ivan Rodriguez
 */
class ProcessSpecRoundTripTest {

	private final ProcessSpecWriter writer = new ProcessSpecWriter();

	private final ProcessSpecReader reader = new ProcessSpecReader();

	@Test
	void writesOneDirectoryPerPackBesideTheProcessManifest(@TempDir Path dir) throws Exception {
		this.writer.write(process("explainer_process", pack("research"), pack("write")), dir);

		assertThat(Files.isRegularFile(dir.resolve("process.yml"))).isTrue();
		assertThat(Files.isRegularFile(dir.resolve("research/manifest.yml"))).isTrue();
		assertThat(Files.isRegularFile(dir.resolve("research/goal.yml"))).isTrue();
		assertThat(Files.isRegularFile(dir.resolve("research/steps/extractTopic.yml"))).isTrue();
		assertThat(Files.isRegularFile(dir.resolve("research/types/TopicBrief.yml"))).isTrue();
		assertThat(Files.isRegularFile(dir.resolve("write/manifest.yml"))).isTrue();
	}

	@Test
	void readsBackTheSamePacks(@TempDir Path dir) throws Exception {
		ProcessSpec process = process("explainer_process", pack("research"), pack("write"));
		this.writer.write(process, dir);

		assertThat(this.reader.read(dir)).isEqualTo(process);
	}

	@Test
	void readsThePacksBackInTheRunOrderNotTheDirectoryOrder(@TempDir Path dir) throws Exception {
		// "alpha" sorts before "zebra", so the directory listing and the run order
		// genuinely disagree: the manifest's order must win.
		ProcessSpec process = process("trading", pack("zebra"), pack("alpha"));
		this.writer.write(process, dir);

		assertThat(this.reader.read(dir).agents()).extracting(AgentSpec::name).containsExactly("zebra", "alpha");
	}

	@Test
	void everyPublishedPackIsIngestibleOnItsOwn(@TempDir Path dir) throws Exception {
		this.writer.write(process("explainer_process", pack("research"), pack("write")), dir);

		// A source recognises a pack by the manifest.yml in its directory: every agent of
		// the process qualifies, so the runtime can ingest the published process whole.
		try (var subdirs = Files.list(dir)) {
			assertThat(subdirs.filter(Files::isDirectory))
				.allMatch((d) -> Files.isRegularFile(d.resolve("manifest.yml")));
		}
	}

	private static ProcessSpec process(String name, AgentSpec... agents) {
		return new ProcessSpec(name, List.of(agents));
	}

	private static AgentSpec pack(String name) {
		AgentSpec sample = SampleAgentSpecs.explainer();
		return new AgentSpec(new AgentSpecManifest(name, "1.0.0"), sample.goal(), sample.steps(), sample.types());
	}

}
