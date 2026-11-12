package com.embabel.smith.specsource;

import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.samples.SampleAgentSpecs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for per-pack content hashing: stable for identical content, sensitive to edits of
 * format-owned files, blind to non-format files.
 */
class PackContentHasherTest {

	@TempDir
	Path temp;

	private final AgentSpecWriter writer = new AgentSpecWriter();

	@Test
	void hashIsStableForIdenticalContent() throws IOException {
		Path packA = writer.write(SampleAgentSpecs.explainer(), temp.resolve("a"));
		Path packB = writer.write(SampleAgentSpecs.explainer(), temp.resolve("b"));

		assertThat(PackContentHasher.hash(packA)).isEqualTo(PackContentHasher.hash(packA))
			.isEqualTo(PackContentHasher.hash(packB));
	}

	@Test
	void hashChangesWhenAStepFileIsEdited() throws IOException {
		Path pack = writer.write(SampleAgentSpecs.explainer(), temp.resolve("pack"));
		String before = PackContentHasher.hash(pack);

		Files.writeString(pack.resolve("steps").resolve("extractTopic.yml"),
				"# edited\n" + Files.readString(pack.resolve("steps").resolve("extractTopic.yml")));

		assertThat(PackContentHasher.hash(pack)).isNotEqualTo(before);
	}

	@Test
	void hashChangesWhenTheManifestOrGoalIsEdited() throws IOException {
		Path pack = writer.write(SampleAgentSpecs.explainer(), temp.resolve("pack"));
		String before = PackContentHasher.hash(pack);

		Files.writeString(pack.resolve("goal.yml"), Files.readString(pack.resolve("goal.yml")) + "\n# edited");

		assertThat(PackContentHasher.hash(pack)).isNotEqualTo(before);
	}

	@Test
	void hashIgnoresNonFormatFiles() throws IOException {
		Path pack = writer.write(SampleAgentSpecs.singleStep(), temp.resolve("pack"));
		String before = PackContentHasher.hash(pack);

		Files.writeString(pack.resolve("README.md"), "notes about the pack");
		Files.writeString(pack.resolve("steps").resolve("scratch.txt"), "not a step");
		Files.writeString(pack.resolve("types").resolve(".gitkeep"), "changed");

		assertThat(PackContentHasher.hash(pack)).isEqualTo(before);
	}

}
