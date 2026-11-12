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

package com.embabel.smith.studio.generate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Resolves the generation blueprint. A {@code path} points at a directory of
 * business-process documents (the discovery deliverables, or a docs tree): the
 * {@code .md}/{@code .markdown}/ {@code .txt} files under it are read, sorted, and
 * concatenated into one description. A {@code blueprint} string is used as-is. The corpus
 * is capped so an oversized tree cannot blow the model's context.
 *
 * @author Ivan Rodriguez
 */
public class BlueprintLoader {

	/** Hard cap on the concatenated corpus, in characters (~200K). */
	private static final int MAX_CHARS = 200_000;

	/**
	 * Returns the blueprint to generate from: the docs under {@code path}, or
	 * {@code text}.
	 * @param text the blueprint text to use when no path is given.
	 * @param path a directory of business-process documents, or null.
	 * @return the blueprint to generate from, or null when both are empty.
	 */
	public String resolve(String text, String path) {
		if (path != null && !path.isBlank()) {
			return readDocs(expandHome(path.trim()));
		}
		return (text == null || text.isBlank()) ? null : text;
	}

	private String readDocs(Path dir) {
		if (!Files.isDirectory(dir)) {
			throw new IllegalArgumentException("Not a directory: " + dir);
		}
		List<Path> files;
		try (Stream<Path> walk = Files.walk(dir)) {
			files = walk.filter(Files::isRegularFile)
				.filter(BlueprintLoader::isDoc)
				.sorted(Comparator.comparing(Path::toString))
				.toList();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to read docs from " + dir + ": " + ex.getMessage(), ex);
		}
		if (files.isEmpty()) {
			throw new IllegalArgumentException("No document files (.md/.markdown/.txt) under " + dir);
		}
		StringBuilder corpus = new StringBuilder();
		for (Path file : files) {
			String name = dir.relativize(file).toString();
			String body = read(file);
			if (corpus.length() + body.length() > MAX_CHARS) {
				corpus.append("\n\n[corpus truncated at ")
					.append(MAX_CHARS)
					.append(" characters; ")
					.append(name)
					.append(" and later files were not included]\n");
				break;
			}
			corpus.append("\n\n--- ").append(name).append(" ---\n").append(body);
		}
		return corpus.toString().strip();
	}

	private static String read(Path file) {
		try {
			return Files.readString(file);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to read " + file + ": " + ex.getMessage(), ex);
		}
	}

	private static boolean isDoc(Path file) {
		String name = file.getFileName().toString().toLowerCase();
		return name.endsWith(".md") || name.endsWith(".markdown") || name.endsWith(".txt");
	}

	private static Path expandHome(String p) {
		if (p.equals("~")) {
			return Path.of(System.getProperty("user.home"));
		}
		if (p.startsWith("~/")) {
			return Path.of(System.getProperty("user.home"), p.substring(2));
		}
		return Path.of(p);
	}

}
