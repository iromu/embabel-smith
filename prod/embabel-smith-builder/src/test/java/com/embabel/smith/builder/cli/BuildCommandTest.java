/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.cli;

import com.embabel.smith.builder.BuildOptions;
import com.embabel.smith.builder.BuildResult;
import com.embabel.smith.builder.BuilderService;
import com.embabel.smith.builder.blueprint.ProcessExtractor;
import com.embabel.smith.builder.core.BuildTarget;
import com.embabel.smith.builder.core.GenerationTechnique;
import com.embabel.smith.builder.lifecycle.LifecycleStamper;
import com.embabel.smith.builder.publish.PublishResult;
import com.embabel.smith.builder.publish.PublishTarget;
import com.embabel.smith.builder.skill.Benchmark;
import com.embabel.smith.builder.skill.SkillBuildResult;
import com.embabel.smith.builder.skill.SkillBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests for the {@code smith build} CLI's generation-technique, output-target and
 * documents flags: all three are advertised in the usage text, the first two default to
 * the agent-spec pipeline and reach the build options, and an unknown value for either is
 * a usage error. The build services are substituted, so the target's routing is
 * observable without a model endpoint.
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
		int exit = new CommandLine(new RecordingBuildCommand(seen)).execute("a description", "--out", "folder:" + out);

		assertThat(exit).isZero();
		assertThat(seen.get().technique()).isEqualTo(GenerationTechnique.SINGLE_SHOT);
	}

	@Test
	void theEmbabelTechniqueReachesTheBuildOptions(@TempDir Path out) {
		AtomicReference<BuildOptions> seen = new AtomicReference<>();
		int exit = new CommandLine(new RecordingBuildCommand(seen)).execute("a description", "--out", "folder:" + out,
				"--technique", "embabel");

		assertThat(exit).isZero();
		assertThat(seen.get().technique()).isEqualTo(GenerationTechnique.EMBABEL);
	}

	@Test
	void anUnknownTechniqueIsAUsageError(@TempDir Path out) {
		AtomicReference<BuildOptions> seen = new AtomicReference<>();

		int exit = new CommandLine(new RecordingBuildCommand(seen)).execute("a description", "--out", "folder:" + out,
				"--technique", "crystal-ball");

		assertThat(exit).isEqualTo(2);
		assertThat(seen.get()).isNull();
	}

	@Test
	void usageAdvertisesTheTargetFlag() {
		CommandLine smith = new CommandLine(new RecordingBuildCommand(new AtomicReference<>()));

		assertThat(smith.getUsageMessage()).contains("--target");
	}

	@Test
	void theDefaultTargetIsTheAgentSpecPipeline(@TempDir Path out) {
		AtomicReference<BuildOptions> specSeen = new AtomicReference<>();
		AtomicReference<BuildOptions> skillSeen = new AtomicReference<>();

		int exit = new CommandLine(new RecordingBuildCommand(specSeen, skillSeen)).execute("a description", "--out",
				"folder:" + out);

		assertThat(exit).isZero();
		assertThat(specSeen.get()).isNotNull();
		assertThat(specSeen.get().target()).isEqualTo(BuildTarget.AGENT_SPEC);
		// The skill service is never constructed for a spec build.
		assertThat(skillSeen.get()).isNull();
	}

	@Test
	void theSkillTargetRoutesToTheSkillPipeline(@TempDir Path out) {
		AtomicReference<BuildOptions> specSeen = new AtomicReference<>();
		AtomicReference<BuildOptions> skillSeen = new AtomicReference<>();

		int exit = new CommandLine(new RecordingBuildCommand(specSeen, skillSeen)).execute("a description", "--out",
				"folder:" + out, "--target", "skill");

		assertThat(exit).isZero();
		assertThat(skillSeen.get()).isNotNull();
		assertThat(skillSeen.get().target()).isEqualTo(BuildTarget.SKILL);
		assertThat(skillSeen.get().minLift()).isEqualTo(BuildOptions.DEFAULT_MIN_LIFT);
		// The pack pipeline is never entered for a skill build.
		assertThat(specSeen.get()).isNull();
	}

	@Test
	void anUnknownTargetIsAUsageError(@TempDir Path out) {
		AtomicReference<BuildOptions> specSeen = new AtomicReference<>();
		AtomicReference<BuildOptions> skillSeen = new AtomicReference<>();

		int exit = new CommandLine(new RecordingBuildCommand(specSeen, skillSeen)).execute("a description", "--out",
				"folder:" + out, "--target", "podcast");

		assertThat(exit).isEqualTo(2);
		assertThat(specSeen.get()).isNull();
		assertThat(skillSeen.get()).isNull();
	}

	@Test
	void usageAdvertisesTheDocumentInputFlags() {
		CommandLine smith = new CommandLine(new RecordingBuildCommand(new AtomicReference<>()));

		assertThat(smith.getUsageMessage()).contains("--docs").contains("--extract");
	}

	@Test
	void namingNeitherADescriptionNorDocumentsIsAUsageError(@TempDir Path out) {
		AtomicReference<BuildOptions> seen = new AtomicReference<>();

		int exit = new CommandLine(new RecordingBuildCommand(seen)).execute("--out", "folder:" + out);

		assertThat(exit).isEqualTo(2);
		assertThat(seen.get()).isNull();
	}

	@Test
	void namingBothADescriptionAndDocumentsIsAUsageError(@TempDir Path docs, @TempDir Path out) {
		AtomicReference<BuildOptions> seen = new AtomicReference<>();

		int exit = new CommandLine(new RecordingBuildCommand(seen)).execute("a description", "--out", "folder:" + out,
				"--docs", docs.toString());

		assertThat(exit).isEqualTo(2);
		assertThat(seen.get()).isNull();
	}

	@Test
	void askingToExtractWithoutDocumentsIsAUsageError(@TempDir Path out) {
		AtomicReference<BuildOptions> seen = new AtomicReference<>();

		int exit = new CommandLine(new RecordingBuildCommand(seen)).execute("a description", "--out", "folder:" + out,
				"--extract");

		assertThat(exit).isEqualTo(2);
		assertThat(seen.get()).isNull();
	}

	@Test
	void documentsAloneBuildTheAssembledCorpusAndNeverExtract(@TempDir Path docs, @TempDir Path out)
			throws IOException {
		Files.writeString(docs.resolve("process.md"), "The order is confirmed.");
		RecordingBuildCommand command = new RecordingBuildCommand(new AtomicReference<>());

		int exit = new CommandLine(command).execute("--docs", docs.toString(), "--out", "folder:" + out);

		assertThat(exit).isZero();
		assertThat(command.extractCalls).isZero();
		assertThat(command.description.get()).isEqualTo("--- process.md ---\nThe order is confirmed.");
	}

	@Test
	void extractRunsTheDiscoveryPromptOnceBeforeTheBuild(@TempDir Path docs, @TempDir Path out) throws IOException {
		Files.writeString(docs.resolve("process.md"), "The order is confirmed.");
		RecordingBuildCommand command = new RecordingBuildCommand(new AtomicReference<>());

		int exit = new CommandLine(command).execute("--docs", docs.toString(), "--extract", "--out", "folder:" + out);

		assertThat(exit).isZero();
		assertThat(command.extractCalls).isEqualTo(1);
		assertThat(command.systemPrompt.get()).isEqualTo("DISCOVERY-PROMPT");
		assertThat(command.description.get()).isEqualTo("EXTRACTED");
	}

	@Test
	void anExtractionThatYieldsNothingStopsTheBuildNamingIngestion(@TempDir Path docs, @TempDir Path out)
			throws IOException {
		Files.writeString(docs.resolve("process.md"), "The order is confirmed.");
		RecordingBuildCommand command = new RecordingBuildCommand(new AtomicReference<>());
		command.emptyExtraction = true;
		PrintStream err = System.err;
		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
		try {
			assertThat(
					new CommandLine(command).execute("--docs", docs.toString(), "--extract", "--out", "folder:" + out))
				.isEqualTo(1);
		}
		finally {
			System.setErr(err);
		}

		// Generation was never reached, and the stage that stopped the build is named.
		assertThat(command.description.get()).isNull();
		assertThat(captured.toString(StandardCharsets.UTF_8)).contains("Ingestion");
	}

	/**
	 * The CLI with both build services substituted, so the flags' wiring is observable
	 * without a model endpoint. A service that is called without being substituted would
	 * reach the real pipeline and fail the test.
	 */
	private static class RecordingBuildCommand extends BuildCommand {

		private final AtomicReference<BuildOptions> seen;

		private final AtomicReference<BuildOptions> skillSeen;

		private final AtomicReference<String> description = new AtomicReference<>();

		private final AtomicReference<String> systemPrompt = new AtomicReference<>();

		private boolean emptyExtraction;

		private int extractCalls;

		RecordingBuildCommand(AtomicReference<BuildOptions> seen) {
			this(seen, new AtomicReference<>());
		}

		RecordingBuildCommand(AtomicReference<BuildOptions> seen, AtomicReference<BuildOptions> skillSeen) {
			super(null, null);
			this.seen = seen;
			this.skillSeen = skillSeen;
		}

		@Override
		protected BuilderService newService(Path scratch) {
			return new BuilderService(null, null, null, List.of()) {
				@Override
				public BuildResult build(String description, PublishTarget target, BuildOptions options) {
					RecordingBuildCommand.this.seen.set(options);
					RecordingBuildCommand.this.description.set(description);
					return BuildResult.built("sample", 1.0, null, null, null);
				}
			};
		}

		@Override
		protected ProcessExtractor newExtractor() {
			return new ProcessExtractor((system, user) -> {
				RecordingBuildCommand.this.extractCalls++;
				RecordingBuildCommand.this.systemPrompt.set(system);
				return RecordingBuildCommand.this.emptyExtraction ? "  " : "EXTRACTED";
			}, "DISCOVERY-PROMPT");
		}

		@Override
		protected SkillBuilderService newSkillService() {
			return new SkillBuilderService((system, user) -> {
				throw new IllegalStateException("no model call expected: the skill service is substituted");
			}, "GEN", "EVALS", "GRADE", "WITH", "BASE") {
				@Override
				public SkillBuildResult build(String description, PublishTarget target, BuildOptions options) {
					RecordingBuildCommand.this.skillSeen.set(options);
					return SkillBuildResult.built("sample-skill", new Benchmark.Summary(1.0, 0.0, 1.0, List.of()), null,
							new LifecycleStamper().stamp("0123456789abcdef0123456789abcdef", 1.0),
							new PublishResult(Path.of("sample-skill"), false));
				}
			};
		}

	}

}
