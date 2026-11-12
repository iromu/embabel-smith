package com.embabel.smith.builder.lifecycle;

import com.embabel.smith.common.io.Yaml;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.samples.SampleAgentSpecs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests of the lifecycle stamper (task 3.3): the metadata records the content
 * hash, the baseline pass-rate, and the state; the builder always records the initial
 * state as INCUBATING (the incubating → active promotion is a later transition owned by
 * change 05); and the {@code
 * lifecycle.yml} sidecar carries that content and round-trips.
 */
class LifecycleStamperTest {

	private final LifecycleStamper stamper = new LifecycleStamper();

	@Test
	void stampsTheBaselineMetadata() {
		AgentSpec pack = SampleAgentSpecs.explainer();

		LifecycleMetadata metadata = stamper.stamp(pack, 1.0);

		assertThat(metadata.contentHash()).isEqualTo(SpecContentHash.of(pack));
		assertThat(metadata.baselinePassRate()).isEqualTo(1.0);
		assertThat(metadata.state()).isEqualTo(LifecycleState.INCUBATING); // builder
																			// always
																			// records the
																			// initial
																			// state
		assertThat(metadata.updatedAt()).isNotBlank();
	}

	@Test
	void aWeakBaselineStaysIncubating() {
		LifecycleMetadata metadata = stamper.stamp(SampleAgentSpecs.explainer(), 0.5);
		assertThat(metadata.state()).isEqualTo(LifecycleState.INCUBATING);
	}

	@Test
	void writesTheSidecarWithTheRecordedContent(@TempDir Path dir) throws Exception {
		AgentSpec pack = SampleAgentSpecs.explainer();
		LifecycleMetadata metadata = stamper.stamp(pack, 1.0);

		stamper.write(dir, metadata);

		String yaml = Files.readString(dir.resolve("lifecycle.yml"));
		assertThat(yaml).contains(metadata.contentHash());
		assertThat(yaml).contains("INCUBATING");

		LifecycleMetadata read = Yaml.mapper().readValue(yaml, LifecycleMetadata.class);
		assertThat(read.contentHash()).isEqualTo(metadata.contentHash());
		assertThat(read.baselinePassRate()).isEqualTo(1.0);
		assertThat(read.state()).isEqualTo(LifecycleState.INCUBATING);
	}

}
