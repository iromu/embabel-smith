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

package com.embabel.smith.specsource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * A spec source backed by a git repository (local path or remote URL). The repository is
 * materialized in a local cache — one directory per source URI under the cache root
 * (default {@code ~/.smith/spec-sources}) — cloned on first use, fetched on
 * re-resolution, and checked out at the pinned ref (branch, tag, or commit SHA; the
 * repository's default branch when unpinned). A missing or corrupt cache (no
 * {@code .git}) is replaced by a fresh clone.
 *
 * <p>
 * All git commands are run non-interactively against the system {@code git} CLI. The
 * source is read-only: publishing into a git source is the builder's separate write path.
 *
 * @author Ivan Rodriguez
 */
public final class GitSpecSource implements SpecSource {

	private final String uri;

	private final String url;

	private final String ref;

	private final Path cacheRoot;

	public GitSpecSource(String uri, String url, String ref) {
		this(uri, url, ref, defaultCacheRoot());
	}

	public GitSpecSource(String uri, String url, String ref, Path cacheRoot) {
		this.uri = uri;
		this.url = url;
		this.ref = ref;
		this.cacheRoot = cacheRoot;
	}

	/** The repository location (local path or remote URL).
	 * @return the repository location (local path or remote URL)
	 */
	public String url() {
		return this.url;
	}

	/** The pinned ref, or {@code null} to follow the repository's default branch.
	 * @return the pinned ref, or {@code null} to follow the repository's default branch
	 */
	public String ref() {
		return this.ref;
	}

	/** The directory under which per-source caches live.
	 * @return the directory under which per-source caches live
	 */
	public Path cacheRoot() {
		return this.cacheRoot;
	}

	public static Path defaultCacheRoot() {
		return Path.of(System.getProperty("user.home"), ".smith", "spec-sources");
	}

	@Override
	public ResolvedSpecSource resolve() {
		Path cache = this.cacheRoot.resolve(cacheKey());
		if (!isUsableClone(cache)) {
			deleteTree(cache);
			clone(this.url, cache);
		}
		else {
			git(cache, "fetch", "--all", "--prune");
		}
		checkoutRef(cache);
		return new FolderSpecSource(this.uri, cache).resolve();
	}

	private void checkoutRef(Path cache) {
		String target = (this.ref != null) ? this.ref : defaultBranch(cache);
		try {
			git(cache, "checkout", "--force", target);
		}
		catch (SpecSourceException ex) {
			throw new SpecSourceException(
					"cannot checkout ref '" + target + "' in git source '" + this.url + "': " + ex.getMessage(), ex);
		}
		if (remoteBranchExists(cache, "refs/remotes/origin/" + target)) {
			git(cache, "reset", "--hard", "origin/" + target);
		}
	}

	private String defaultBranch(Path cache) {
		String branch = gitOutput(cache, "symbolic-ref", "--short", "HEAD").trim();
		if (branch.isBlank()) {
			throw new SpecSourceException(
					"git source '" + this.url + "' has a detached HEAD in its cache; pin an explicit ref");
		}
		return branch;
	}

	private boolean remoteBranchExists(Path cache, String refName) {
		try {
			git(cache, "rev-parse", "--verify", "--quiet", refName);
			return true;
		}
		catch (SpecSourceException ex) {
			return false;
		}
	}

	private void clone(String url, Path cache) {
		try {
			runGit(List.of("git", "-c", "core.askpass=false", "clone", "--", url, cache.toString()), false);
		}
		catch (SpecSourceException ex) {
			throw new SpecSourceException("cannot clone git source '" + url + "': " + ex.getMessage(), ex);
		}
	}

	private boolean isUsableClone(Path cache) {
		return Files.isDirectory(cache) && Files.isDirectory(cache.resolve(".git"));
	}

	private String cacheKey() {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(this.uri.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
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

	private static void git(Path dir, String... args) {
		runGit(fullArgs(dir, args), false);
	}

	private static String gitOutput(Path dir, String... args) {
		return runGit(fullArgs(dir, args), true).trim();
	}

	private static List<String> fullArgs(Path dir, String... args) {
		List<String> command = new ArrayList<>(List.of("git", "-c", "core.askpass=false", "-C", dir.toString()));
		command.addAll(List.of(args));
		return command;
	}

	private static String runGit(List<String> command, boolean wantStdout) {
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.environment().put("GIT_TERMINAL_PROMPT", "0");
		try {
			Process process = builder.start();
			String stdout = wantStdout ? new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
					: "";
			String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
			int exit = process.waitFor();
			if (exit != 0) {
				throw new SpecSourceException(
						"git " + String.join(" ", command) + " failed (exit " + exit + "): " + stderr.trim());
			}
			return stdout;
		}
		catch (IOException ex) {
			throw new SpecSourceException("cannot run git " + String.join(" ", command), ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new SpecSourceException("interrupted running git " + String.join(" ", command), ex);
		}
	}

}
