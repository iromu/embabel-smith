/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.publish;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Publishes a {@link Publishable} into a git repository's working tree (design D3):
 * clones the source, writes the artifact's files as a subdirectory named after it,
 * commits them, and — only when {@code --push} is set — pushes to the remote. A
 * same-named directory already in the repository is a collision unless
 * {@code --overwrite} replaces it.
 *
 * <p>
 * All git commands run non-interactively against the system {@code git} CLI (the same
 * approach as {@code GitSpecSource}). The default is a local commit only; no push.
 *
 * @author Ivan Rodriguez
 */
public class GitPublisher implements Publisher {

	private final PublishTarget target;

	private final Path workDir;

	public GitPublisher(PublishTarget target) {
		this.target = target;
		try {
			this.workDir = Files.createTempDirectory("smith-git-publish-");
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot create git publish work dir", ex);
		}
		clone(target.location(), target.ref(), this.workDir);
	}

	@Override
	public PublishResult publish(Publishable unit, boolean overwrite, boolean push) throws IOException {
		Path unitDir = this.workDir.resolve(unit.name());
		if (Files.isDirectory(unitDir) && !overwrite) {
			throw new CollisionException(
					"A published artifact named '" + unit.name() + "' already exists in git source '"
							+ this.target.location() + "'; pass --overwrite to replace it");
		}
		if (Files.isDirectory(unitDir)) {
			deleteTree(unitDir);
		}
		Files.createDirectories(unitDir);
		unit.writeTo(unitDir);

		git(this.workDir, "add", "-A");
		// An overwrite of an unchanged artifact stages nothing; only commit when there is
		// something to commit.
		if (gitExitCode(this.workDir, "diff", "--cached", "--quiet") != 0) {
			git(this.workDir, "-c", "user.email=smith-builder@embabel.local", "-c", "user.name=Smith Builder", "commit",
					"-m", "smith: publish " + unit.name() + " (baseline "
							+ String.format("%.3f", unit.baselinePassRate()) + ")");
		}

		boolean pushed = false;
		if (push) {
			git(this.workDir, "push", "origin", "HEAD");
			pushed = true;
		}
		// Not unitDir: that sits inside this.workDir, which close() deletes. The
		// artifact lives as a commit in the source, so report it in the source's terms.
		return new PublishResult("git:" + this.target.location() + "#" + unit.name(), pushed);
	}

	@Override
	public void close() {
		deleteTree(this.workDir);
	}

	private static void clone(String url, String ref, Path dest) {
		List<String> command = new ArrayList<>(List.of("git", "-c", "core.askpass=false", "clone", "--quiet"));
		if (ref != null) {
			command.add("-b");
			command.add(ref);
		}
		command.add("--");
		command.add(url);
		command.add(dest.toString());
		runGit(command);
	}

	private static void git(Path dir, String... args) {
		List<String> command = new ArrayList<>(List.of("git", "-c", "core.askpass=false", "-C", dir.toString()));
		command.addAll(List.of(args));
		runGit(command);
	}

	/**
	 * Runs a git command and returns its exit code without throwing. Used for checks like
	 * {@code diff --cached --quiet}, where a non-zero exit is an expected, non-error
	 * result.
	 * @param dir the working directory the git command runs in
	 * @param args the git subcommand and its arguments
	 * @return the process exit code
	 */
	private static int gitExitCode(Path dir, String... args) {
		List<String> command = new ArrayList<>(List.of("git", "-c", "core.askpass=false", "-C", dir.toString()));
		command.addAll(List.of(args));
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.environment().put("GIT_TERMINAL_PROMPT", "0");
		builder.redirectErrorStream(true);
		try {
			Process process = builder.start();
			process.getInputStream().readAllBytes();
			return process.waitFor();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot run git " + String.join(" ", command), ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new UncheckedIOException("interrupted running git " + String.join(" ", command), new IOException(ex));
		}
	}

	private static void runGit(List<String> command) {
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.environment().put("GIT_TERMINAL_PROMPT", "0");
		builder.redirectErrorStream(true);
		try {
			Process process = builder.start();
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
			int exit = process.waitFor();
			if (exit != 0) {
				throw new UncheckedIOException(
						new IOException("git " + String.join(" ", command) + " failed (exit " + exit + "): " + output));
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot run git " + String.join(" ", command), ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new UncheckedIOException("interrupted running git " + String.join(" ", command), new IOException(ex));
		}
	}

	private static void deleteTree(Path dir) {
		if (!Files.exists(dir)) {
			return;
		}
		try (Stream<Path> stream = Files.walk(dir)) {
			stream.sorted(Comparator.reverseOrder()).forEach((path) -> {
				try {
					Files.delete(path);
				}
				catch (IOException ex) {
					throw new UncheckedIOException("cannot delete " + path, ex);
				}
			});
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot walk " + dir, ex);
		}
	}

}
