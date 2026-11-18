/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.blueprint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Loads the business-process documents to present and generate from. A {@code path}
 * points at a local directory of docs; a {@code url} is cloned (shallow) into a temp dir
 * first. The {@code .md} files under the location are read and returned, so the workspace
 * can show what it is generating from. This is the "ingest" half of discovery: it
 * surfaces the (already business-level) documents. The model-based extraction of a raw
 * codebase (running the discovery prompt) is a separate, later step and is not performed
 * here.
 *
 * @author Ivan Rodriguez
 */
public class BusinessProcessDiscovery {

	/**
	 * Hard cap on the corpus this loader presents, in characters (~300K). The cap on the
	 * corpus a generation runs from is a different, smaller boundary in
	 * {@link BlueprintLoader}; the two are deliberately not the same value.
	 */
	private static final int MAX_CHARS = 300_000;

	public DiscoverResult load(String path, String url) {
		if (path != null && !path.isBlank()) {
			Path dir = expandHome(path.trim());
			if (!Files.isDirectory(dir)) {
				throw new IllegalArgumentException("Not a directory: " + dir);
			}
			return new DiscoverResult("folder", dir.toString(), readDocs(dir));
		}
		if (url != null && !url.isBlank()) {
			Path dir = cloneRepo(url.trim());
			try {
				return new DiscoverResult("git", url.trim(), readDocs(dir));
			}
			finally {
				deleteQuietly(dir);
			}
		}
		throw new IllegalArgumentException("Provide a docs path or a repository URL.");
	}

	private Map<String, String> readDocs(Path root) {
		List<Path> files;
		try (Stream<Path> walk = Files.walk(root)) {
			files = walk.filter(Files::isRegularFile).filter((p) -> {
				String n = p.getFileName().toString().toLowerCase();
				return n.endsWith(".md") || n.endsWith(".markdown");
			}).sorted(Comparator.comparing(Path::toString)).toList();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to read docs from " + root + ": " + ex.getMessage(), ex);
		}
		Map<String, String> docs = new LinkedHashMap<>();
		int total = 0;
		for (Path file : files) {
			if (total > MAX_CHARS) {
				break;
			}
			String name = root.relativize(file).toString();
			String body;
			try {
				body = Files.readString(file);
			}
			catch (IOException ex) {
				continue;
			}
			docs.put(name, body);
			total += body.length();
		}
		if (docs.isEmpty()) {
			throw new IllegalArgumentException("No business-process documents (.md) found under " + root);
		}
		return docs;
	}

	/**
	 * Shallow-clones a repository into a fresh temp directory and returns it. The caller
	 * owns the directory and is expected to {@link #deleteQuietly delete} it when done.
	 * @param url the repository URL to clone
	 * @return the temp directory holding the clone
	 */
	static Path cloneRepo(String url) {
		Path dir;
		try {
			dir = Files.createTempDirectory("smith-discover-");
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to create a temp dir for the clone: " + ex.getMessage(), ex);
		}
		try {
			runClone(url, dir);
		}
		catch (IllegalStateException ex) {
			// The caller never received the directory, so no cleanup downstream of this
			// call can reach it — and a failed clone is a timeout or an unreadable URL,
			// which is exactly the run that repeats.
			deleteQuietly(dir);
			throw ex;
		}
		return dir;
	}

	private static void runClone(String url, Path dir) {
		try {
			ProcessBuilder pb = cloneProcess(url, dir);
			Process process = pb.start();
			String output = new String(process.getInputStream().readAllBytes());
			boolean finished = process.waitFor(180, TimeUnit.SECONDS);
			if (!finished) {
				process.destroyForcibly();
				throw new IllegalStateException("git clone timed out for " + url);
			}
			if (process.exitValue() != 0) {
				throw new IllegalStateException("git clone failed for " + url + ": " + output.trim());
			}
		}
		catch (IOException ex) {
			throw new IllegalStateException("git clone failed for " + url + ": " + ex.getMessage(), ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("git clone interrupted for " + url, ex);
		}
	}

	/**
	 * Removes a directory tree, ignoring every failure. Used for the temp clone a
	 * generation no longer needs, where a leftover directory in the system temp is a
	 * worse outcome than an aborted cleanup.
	 * @param dir the directory to remove, or null to do nothing
	 */
	static void deleteQuietly(Path dir) {
		if (dir == null || !Files.exists(dir)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(dir)) {
			walk.sorted(Comparator.reverseOrder()).forEach((path) -> {
				try {
					Files.delete(path);
				}
				catch (IOException ignored) {
					// best-effort cleanup of a temp directory
				}
			});
		}
		catch (IOException ignored) {
			// best-effort cleanup of a temp directory
		}
	}

	/**
	 * The shallow-clone process for a repository, in the same non-interactive posture
	 * {@code GitPublisher} uses: a private repository has to fail rather than wait for
	 * credentials nobody is at a terminal to type.
	 * @param url the repository URL to clone.
	 * @param dir the directory to clone into.
	 * @return the process builder, not yet started.
	 */
	static ProcessBuilder cloneProcess(String url, Path dir) {
		ProcessBuilder pb = new ProcessBuilder("git", "-c", "core.askpass=false", "clone", "--depth", "1", url,
				dir.toString());
		pb.environment().put("GIT_TERMINAL_PROMPT", "0");
		pb.redirectErrorStream(true);
		return pb;
	}

	static Path expandHome(String p) {
		if (p.equals("~")) {
			return Path.of(System.getProperty("user.home"));
		}
		if (p.startsWith("~/")) {
			return Path.of(System.getProperty("user.home"), p.substring(2));
		}
		return Path.of(p);
	}

	/**
	 * The loaded docs plus where they came from.
	 *
	 * @param sourceType the source kind ("folder" or "git").
	 * @param location the docs path or repository URL.
	 * @param docs the loaded documents keyed by their relative path.
	 */
	public record DiscoverResult(String sourceType, String location, Map<String, String> docs) {
	}

}
