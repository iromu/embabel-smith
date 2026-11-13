package com.embabel.smith.builder.cli;

import com.embabel.smith.builder.BuildOptions;
import com.embabel.smith.builder.BuildResult;
import com.embabel.smith.builder.BuilderService;
import com.embabel.smith.builder.core.GenerationTechnique;
import com.embabel.smith.builder.publish.PublishTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests for the {@code smith build} CLI's generation-technique flag: it is
 * advertised in the usage text, defaults to single-shot, and reaches the build options;
 * an unknown value is a usage error.
 */
class BuildCommandTest {

	@Test
	void usageAdvertisesTheTechniqueFlag() {
		CommandLine smith = new CommandLine(new RecordingBuildCommand(new AtomicReference<>()));

		assertThat(smith.getUsageMessage()).contains("--technique");
	}

	@Test
	void theDefaultTechniqueIsSingleShot(@TempDir Path out) {
		AtomicReference<BuildOptions> seen = new AtomicReference<>();
		int exit = new CommandLine(new RecordingBuildCommand(seen)).execute("a description", "--out",
				"folder:" + out);

		assertThat(exit).isZero();
		assertThat(seen.get().technique()).isEqualTo(GenerationTechnique.SINGLE_SHOT);
	}

	@Test
	void theEmbabelTechniqueReachesTheBuildOptions(@TempDir Path out) {
		AtomicReference<BuildOptions> seen = new AtomicReference<>();
		int exit = new CommandLine(new RecordingBuildCommand(seen)).execute("a description", "--out",
				"folder:" + out, "--technique", "embabel");

		assertThat(exit).isZero();
		assertThat(seen.get().technique()).isEqualTo(GenerationTechnique.EMBABEL);
	}

	@Test
	void anUnknownTechniqueIsAUsageError(@TempDir Path out) {
		AtomicReference<BuildOptions> seen = new AtomicReference<>();

		int exit = new CommandLine(new RecordingBuildCommand(seen)).execute("a description", "--out",
				"folder:" + out, "--technique", "crystal-ball");

		assertThat(exit).isEqualTo(2);
		assertThat(seen.get()).isNull();
	}

	/**
	 * The CLI with its service substituted, so the flag's wiring can be observed without
	 * a model endpoint.
	 */
	private static class RecordingBuildCommand extends BuildCommand {

		private final AtomicReference<BuildOptions> seen;

		RecordingBuildCommand(AtomicReference<BuildOptions> seen) {
			super(null, null, "", "", "");
			this.seen = seen;
		}

		@Override
		protected BuilderService newService(Path scratch) {
			return new BuilderService(null, null, null, List.of()) {
				@Override
				public BuildResult build(String description, PublishTarget target, BuildOptions options) {
					RecordingBuildCommand.this.seen.set(options);
					return BuildResult.built("sample", 1.0, null, null, null);
				}
			};
		}

	}

}
