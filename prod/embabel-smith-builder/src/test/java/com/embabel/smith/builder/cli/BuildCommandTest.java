/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.cli;

import com.embabel.smith.builder.BuildOptions;
import com.embabel.smith.builder.BuildResult;
import com.embabel.smith.builder.BuilderService;
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

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests for the {@code smith build} CLI's generation-technique and output-target
 * flags: both are advertised in the usage text, both default to the agent-spec pipeline,
 * both reach the build options, and an unknown value for either is a usage error. The
 * build services are substituted, so the target's routing is observable without a model
 * endpoint.
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

	/**
	 * The CLI with both build services substituted, so the flags' wiring is observable
	 * without a model endpoint. A service that is called without being substituted would
	 * reach the real pipeline and fail the test.
	 */
	private static class RecordingBuildCommand extends BuildCommand {

		private final AtomicReference<BuildOptions> seen;

		private final AtomicReference<BuildOptions> skillSeen;

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
					return BuildResult.built("sample", 1.0, null, null, null);
				}
			};
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
