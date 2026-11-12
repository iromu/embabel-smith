/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleStamper;
import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.model.AgentSpec;

/**
 * Publishes a agent spec into a git repository's working tree (design D3): clones the
 * source, writes the pack (plus its {@code lifecycle.yml} sidecar) as a subdirectory
 * named after the pack, commits it, and — only when {@code --push} is set — pushes to the
 * remote. A same-named pack already in the repository is a collision unless
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

	private final AgentSpecWriter writer = new AgentSpecWriter();

	private final LifecycleStamper stamper = new LifecycleStamper();

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
	public PublishResult publish(AgentSpec pack, LifecycleMetadata lifecycle, boolean overwrite, boolean push)
			throws IOException {
		Path packDir = this.workDir.resolve(pack.name());
		if (Files.isDirectory(packDir) && !overwrite) {
			throw new CollisionException("A agent spec named '" + pack.name() + "' already exists in git source '"
					+ this.target.location() + "'; pass --overwrite to replace it");
		}
		if (Files.isDirectory(packDir)) {
			deleteTree(packDir);
		}
		this.writer.write(pack, packDir);
		this.stamper.write(packDir, lifecycle);

		git(this.workDir, "add", "-A");
		// An overwrite of an unchanged pack stages nothing; only commit when there is
		// something to commit.
		if (gitExitCode(this.workDir, "diff", "--cached", "--quiet") != 0) {
			git(this.workDir, "-c", "user.email=smith-builder@embabel.local", "-c", "user.name=Smith Builder", "commit",
					"-m", "smith: publish " + pack.name() + " (baseline "
							+ String.format("%.3f", lifecycle.baselinePassRate()) + ")");
		}

		boolean pushed = false;
		if (push) {
			git(this.workDir, "push", "origin", "HEAD");
			pushed = true;
		}
		return new PublishResult(packDir, pushed);
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
