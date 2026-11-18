/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.blueprint;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads a repository archive — the {@code .zip} a forge hands out for a download — into a
 * directory {@link CodebaseCorpus} can walk. It is the cheaper sibling of a clone:
 * nothing goes over the network, nothing has to be fetched, and an archive already on
 * disk is read where it lies, which is what makes it the source to reach for when
 * iterating on an example rather than on the fetch.
 *
 * <p>
 * Two things about archives shape this. An entry list is somebody else's list of paths,
 * so every one of them is checked before it is written: an archive can name files all
 * around the directory it is unpacked into, and the entries of a hostile one are named to
 * reach them. And an archive from GitHub or Bitbucket wraps the whole tree in one
 * directory named after the repository and its ref, which is lifted away so the corpus
 * labels stay the paths a reader of that repository knows.
 *
 * @author Ivan Rodriguez
 */
final class ZipArchive {

	private ZipArchive() {
	}

	/**
	 * Extracts an archive into a temp directory.
	 * @param archive the {@code .zip} to read
	 * @return the directory to read the codebase from, for the caller to
	 * {@link BusinessProcessDiscovery#deleteQuietly delete} when done
	 * @throws IllegalArgumentException if the archive is not there, holds nothing, or
	 * carries an entry that would write outside it
	 */
	static Path extract(Path archive) {
		if (!Files.isRegularFile(archive)) {
			throw new IllegalArgumentException("No such archive: " + archive);
		}
		Path staging;
		try {
			staging = Files.createTempDirectory("smith-archive-");
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to create a temp dir for " + archive + ": " + ex.getMessage(), ex);
		}
		try {
			if (extractEntries(archive, staging) == 0) {
				throw new IllegalArgumentException("The archive holds no files: " + archive);
			}
			return liftWrapper(staging);
		}
		catch (IOException ex) {
			BusinessProcessDiscovery.deleteQuietly(staging);
			throw new IllegalStateException("Failed to extract " + archive + ": " + ex.getMessage(), ex);
		}
		catch (IllegalArgumentException ex) {
			BusinessProcessDiscovery.deleteQuietly(staging);
			throw ex;
		}
	}

	private static int extractEntries(Path archive, Path staging) throws IOException {
		int files = 0;
		try (ZipInputStream in = new ZipInputStream(Files.newInputStream(archive), StandardCharsets.UTF_8)) {
			for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
				Path target = targetFor(staging, entry);
				if (entry.isDirectory()) {
					Files.createDirectories(target);
					continue;
				}
				Files.createDirectories(target.getParent());
				try (OutputStream out = Files.newOutputStream(target)) {
					in.transferTo(out);
				}
				files++;
			}
		}
		return files;
	}

	/**
	 * Where an entry belongs, refusing one that would land outside the extraction
	 * directory.
	 * @param staging the directory being extracted into
	 * @param entry the entry being written
	 * @return the path to write the entry at
	 */
	private static Path targetFor(Path staging, ZipEntry entry) {
		Path target = staging.resolve(entry.getName()).normalize();
		if (!target.startsWith(staging)) {
			throw new IllegalArgumentException("Archive entry would write outside the codebase: " + entry.getName());
		}
		return target;
	}

	/**
	 * Lifts the single wrapper directory a forge archive puts around its tree, so the
	 * directory handed back holds the repository itself. Corpus labels then read
	 * {@code docs/architecture.md} rather than repeating the archive's ref in every one,
	 * and {@code --subdir} names a directory of the project instead of the wrapper. The
	 * wrapper is moved out rather than read in place so that the path returned is the
	 * whole of what the caller has to delete.
	 * @param staging the directory the entries were written under
	 * @return the directory to read the codebase from
	 */
	private static Path liftWrapper(Path staging) throws IOException {
		try (Stream<Path> children = Files.list(staging)) {
			List<Path> top = children.toList();
			if (top.size() != 1 || !Files.isDirectory(top.get(0))) {
				return staging;
			}
			Path root = staging.resolveSibling(staging.getFileName() + "-repo");
			Files.move(top.get(0), root);
			Files.delete(staging);
			return root;
		}
	}

}
