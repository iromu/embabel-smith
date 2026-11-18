/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.embabel.smith.builder.blueprint.BlueprintGenerator;
import com.embabel.smith.builder.blueprint.BlueprintGenerator.BlueprintResult;
import com.embabel.smith.builder.blueprint.BlueprintGenerator.CodebaseSource;
import com.embabel.smith.builder.blueprint.CodebaseCorpus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests for the {@code smith blueprint} CLI: the codebase input is one of a
 * repository URL, a local directory or an archive and never more than one, {@code --out}
 * is required, and the exit code follows whether the generator reported every
 * deliverable. The generator is substituted, so no model endpoint is involved.
 */
class BlueprintCommandTest {

	@Test
	void usageAdvertisesTheCodebaseFlags() {
		CommandLine smith = new CommandLine(new RecordingBlueprintCommand(new AtomicReference<>()));

		assertThat(smith.getUsageMessage()).contains("--repo")
			.contains("--path")
			.contains("--zip")
			.contains("--subdir");
	}

	@Test
	void aRepositoryUrlReachesTheGenerator(@TempDir Path out) {
		AtomicReference<CodebaseSource> seen = new AtomicReference<>();

		int exit = execute(seen, "--repo", "git@example.invalid:acme/shop.git", "--out", out.toString());

		assertThat(exit).isZero();
		assertThat(seen.get().url()).isEqualTo("git@example.invalid:acme/shop.git");
		assertThat(seen.get().fromGit()).isTrue();
	}

	@Test
	void anArchiveReachesTheGenerator(@TempDir Path out) {
		AtomicReference<CodebaseSource> seen = new AtomicReference<>();

		int exit = execute(seen, "--zip", "/downloads/acme-main.zip", "--out", out.toString());

		assertThat(exit).isZero();
		assertThat(seen.get().archive()).isEqualTo("/downloads/acme-main.zip");
		assertThat(seen.get().fromArchive()).isTrue();
	}

	@Test
	void aLocalPathAndSubdirectoryReachTheGenerator(@TempDir Path out) {
		AtomicReference<CodebaseSource> seen = new AtomicReference<>();

		int exit = execute(seen, "--path", "/src/checkout", "--subdir", "apps/shop", "--out", out.toString());

		assertThat(exit).isZero();
		assertThat(seen.get().path()).isEqualTo("/src/checkout");
		assertThat(seen.get().subdir()).isEqualTo("apps/shop");
	}

	@Test
	void theOverwriteFlagReachesTheGenerator(@TempDir Path out) {
		AtomicBoolean overwrite = new AtomicBoolean();
		AtomicReference<CodebaseSource> seen = new AtomicReference<>();
		RecordingBlueprintCommand command = new RecordingBlueprintCommand(seen);
		command.overwriteSeen = overwrite;

		int exit = new CommandLine(command).execute("--path", "/src/checkout", "--out", out.toString(), "--overwrite");

		assertThat(exit).isZero();
		assertThat(overwrite).isTrue();
	}

	@Test
	void namingNeitherCodebaseInputIsAUsageError(@TempDir Path out) {
		AtomicReference<CodebaseSource> seen = new AtomicReference<>();

		int exit = execute(seen, "--out", out.toString());

		assertThat(exit).isEqualTo(2);
		assertThat(seen.get()).isNull();
	}

	@Test
	void namingBothCodebaseInputsIsAUsageError(@TempDir Path out) {
		AtomicReference<CodebaseSource> seen = new AtomicReference<>();

		int exit = execute(seen, "--repo", "git@example.invalid:acme/shop.git", "--path", "/src/checkout", "--out",
				out.toString());

		assertThat(exit).isEqualTo(2);
		assertThat(seen.get()).isNull();
	}

	@Test
	void aMissingOutIsAUsageError() {
		assertThat(execute(new AtomicReference<>(), "--path", "/src/checkout")).isEqualTo(2);
	}

	@Test
	void anIncompleteBlueprintExitsOneAndSaysWhatIsMissing(@TempDir Path out) {
		RecordingBlueprintCommand command = new RecordingBlueprintCommand(new AtomicReference<>());
		command.complete = false;
		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		PrintStream original = System.out;
		System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));

		int exit;
		try {
			exit = new CommandLine(command).execute("--path", "/src/checkout", "--out", out.toString());
		}
		finally {
			System.setOut(original);
		}

		assertThat(exit).isEqualTo(1);
		assertThat(captured.toString(StandardCharsets.UTF_8)).startsWith("BUILD BLOCKED:").contains("WORKFLOWS.md");
	}

	private static int execute(AtomicReference<CodebaseSource> seen, String... args) {
		return new CommandLine(new RecordingBlueprintCommand(seen)).execute(args);
	}

	/**
	 * The command with its generator substituted, so a run records what it was asked for
	 * and returns a verdict without touching a model or the network.
	 */
	private static class RecordingBlueprintCommand extends BlueprintCommand {

		private final AtomicReference<CodebaseSource> seen;

		private boolean complete = true;

		private AtomicBoolean overwriteSeen = new AtomicBoolean();

		RecordingBlueprintCommand(AtomicReference<CodebaseSource> seen) {
			super(null);
			this.seen = seen;
		}

		@Override
		protected BlueprintGenerator newGenerator() {
			return new BlueprintGenerator(null) {
				@Override
				public BlueprintResult generate(CodebaseSource source, Path out, boolean overwrite) {
					RecordingBlueprintCommand.this.seen.set(source);
					RecordingBlueprintCommand.this.overwriteSeen.set(overwrite);
					return new BlueprintResult(RecordingBlueprintCommand.this.complete, out,
							Map.of("BUSINESS_OVERVIEW.md", 120),
							RecordingBlueprintCommand.this.complete ? List.of() : List.of("WORKFLOWS.md"), source,
							new CodebaseCorpus.Corpus("corpus", 3, 1));
				}
			};
		}

	}

}
