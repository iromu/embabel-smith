/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.embabel.smith.builder.core.BuilderPackPaths;

/**
 * Reading {@code toolGroups} declarations out of the shipped builder step files, for the
 * tests whose claim is "this registered role is a name a pack really declares".
 *
 * <p>
 * <b>Why a reader and not the pack API.</b> {@code AgentSpecReader} would give the same
 * list through {@code ActionStepSpec.toolGroups()}, but the assertion these tests make is
 * about the text a pack ships — the step file a human edits under
 * {@code src/main/resources/specs} — so it is read as text. The packs are classpath
 * resources, which changes how the files are found and not what is read:
 * {@link BuilderPackPaths} hands over the directory through the class loader, and the
 * {@code toolGroups:} key in it is still the one typed by hand. Matching the word
 * anywhere would be wrong twice over: the step prompts discuss validating, scanning and
 * reading documents in prose, and the example spec embedded inside a prompt carries a
 * quoted {@code "toolGroups": []} of its own. So only a top-level {@code toolGroups:} key
 * followed by list entries counts, at any position in that list.
 *
 * @author Ivan Rodriguez
 */
final class PackStepFiles {

	/** A top-level {@code toolGroups:} key and the list lines that follow it. */
	private static final Pattern BLOCK = Pattern.compile("^toolGroups:[ \t]*\n((?:[ \t]*-.*\n?)*)", Pattern.MULTILINE);

	private static final Pattern ENTRY = Pattern.compile("-[ \t]*([A-Za-z_][A-Za-z0-9_-]*)");

	private PackStepFiles() {
	}

	/** Every shipped builder step file, by path. */
	static List<Path> all() throws IOException {
		try (var files = Files.walk(BuilderPackPaths.root())) {
			return files.filter((path) -> path.getFileName().toString().endsWith(".yml"))
				.filter((path) -> path.getParent().getFileName().toString().equals("steps"))
				.toList();
		}
	}

	/**
	 * Whether {@code stepFile} declares {@code role} as one of its {@code toolGroups}
	 * entries.
	 */
	static boolean declares(Path stepFile, String role) {
		try {
			return rolesIn(Files.readString(stepFile)).contains(role);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Unreadable step file " + stepFile, ex);
		}
	}

	/** The declared names, in the order they appear. */
	static List<String> roles(Path stepFile) throws IOException {
		return rolesIn(Files.readString(stepFile));
	}

	private static List<String> rolesIn(String stepText) {
		List<String> roles = new ArrayList<>();
		Matcher block = BLOCK.matcher(stepText);
		while (block.find()) {
			Matcher entry = ENTRY.matcher(block.group(1));
			while (entry.find()) {
				roles.add(entry.group(1));
			}
		}
		return roles;
	}

}
