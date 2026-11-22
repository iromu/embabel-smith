/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.blueprint;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Assembles the corpus a repository presents to the discovery prompt. Unlike
 * {@link BlueprintLoader}, which reads the business-process documents a build consumes,
 * this reads the implementation as well, because
 * {@code prompts/business_process_discovery.jinja} asks for a codebase.
 *
 * <p>
 * No real repository fits a context window — a mid-size one is megabytes of source — so
 * the corpus is bounded rather than complete. The bound is split rather than shared: the
 * documents may take at most half the cap, and the rest of the cap goes to source. One
 * common pool would let a heavily documented repository crowd its own code out of the
 * corpus entirely, and the prompt would then reconstruct a business process from
 * marketing prose.
 *
 * <p>
 * Every file is labelled with its path relative to the root and truncated at
 * {@link #MAX_FILE_CHARS}. Files the cap left out are counted and returned rather than
 * swallowed: a blueprint built from a slice of a repository has to be able to say so.
 *
 * @author Ivan Rodriguez
 */
public class CodebaseCorpus {

	/**
	 * Per-file bound, in characters. One oversized generated or vendored file must not
	 * consume the corpus on its own.
	 */
	static final int MAX_FILE_CHARS = 20_000;

	/**
	 * The most bytes read from any one file. A character is at most four UTF-8 bytes, so
	 * this always yields at least {@link #MAX_FILE_CHARS} characters from a file big
	 * enough to need truncating — and never reads the rest of one. A vendored bundle or a
	 * database dump is megabytes, and reading it whole to throw 95% of it away is how a
	 * corpus bounded at 200K characters manages to exhaust the heap anyway.
	 */
	private static final int MAX_FILE_BYTES = MAX_FILE_CHARS * 4;

	/** Total bound on the corpus this loader assembles, in characters (~200K). */
	static final int MAX_CHARS = 200_000;

	/**
	 * The share of {@link #MAX_CHARS} the documents may take. Source gets whatever the
	 * documents leave, and the documents cannot take more than this.
	 */
	static final int DOC_SHARE = MAX_CHARS / 2;

	/**
	 * Directory names that hold build output, dependencies or tooling state rather than
	 * code.
	 */
	private static final Set<String> SKIPPED_DIRECTORIES = Set.of(".git", ".gradle", ".idea", ".next", ".venv",
			"__pycache__", "build", "coverage", "dist", "node_modules", "out", "target", "venv", "vendor");

	/** Extensions treated as documentation, and read before any source file. */
	private static final Set<String> DOCUMENT_EXTENSIONS = Set.of("adoc", "markdown", "md", "rst", "txt");

	/**
	 * Extensions treated as source. An allowlist rather than a denylist because the
	 * alternative reads every binary in the repository — an allowlist's only failure mode
	 * is a language nobody added yet.
	 */
	private static final Set<String> SOURCE_EXTENSIONS = Set.of("c", "cc", "cpp", "cs", "css", "go", "gradle", "groovy",
			"h", "hpp", "html", "java", "js", "jsx", "kt", "kts", "py", "rb", "rs", "scala", "sh", "sql", "swift",
			"toml", "ts", "tsx", "vue", "xml", "yaml", "yml");

	/**
	 * Reads a repository into the corpus to run the discovery prompt over.
	 * @param root the directory to read, usually a fresh clone or a checkout
	 * @return the corpus and what it cost to assemble it
	 * @throws IllegalArgumentException if {@code root} is not a directory or holds no
	 * file with a recognised extension
	 */
	public Corpus load(Path root) {
		if (!Files.isDirectory(root)) {
			throw new IllegalArgumentException("Not a directory: " + root);
		}
		List<Path> files;
		try (Stream<Path> walk = Files.walk(root)) {
			Path realRoot = root.toRealPath();
			files = walk.filter(Files::isRegularFile)
				.filter(CodebaseCorpus::isRecognised)
				.filter((path) -> staysInside(realRoot, path))
				.filter((path) -> !underSkippedDirectory(root, path))
				.sorted(Comparator.comparing((Path path) -> root.relativize(path).toString().toLowerCase(Locale.ROOT)))
				.toList();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to read the codebase under " + root + ": " + ex.getMessage(), ex);
		}
		if (files.isEmpty()) {
			throw new IllegalArgumentException("No source files or documents with a recognised extension under " + root
					+ " — pass --subdir to narrow the corpus to the part that holds them.");
		}
		List<Path> documents = files.stream().filter(CodebaseCorpus::isDocument).toList();
		List<Path> source = files.stream().filter((path) -> !isDocument(path)).toList();
		CorpusBuilder corpus = new CorpusBuilder();
		corpus.appendAll(root, documents, DOC_SHARE);
		corpus.appendAll(root, source, MAX_CHARS);
		return new Corpus(corpus.text.toString().strip(), corpus.included, corpus.omitted);
	}

	private static boolean isRecognised(Path file) {
		String extension = extensionOf(file);
		return DOCUMENT_EXTENSIONS.contains(extension) || SOURCE_EXTENSIONS.contains(extension);
	}

	private static boolean isDocument(Path file) {
		return DOCUMENT_EXTENSIONS.contains(extensionOf(file));
	}

	private static String extensionOf(Path file) {
		String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
		int dot = name.lastIndexOf('.');
		return (dot < 0) ? "" : name.substring(dot + 1);
	}

	private static boolean underSkippedDirectory(Path root, Path file) {
		for (Path segment : root.relativize(file)) {
			if (SKIPPED_DIRECTORIES.contains(segment.toString())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether a file is the repository's own rather than a link aimed somewhere else. A
	 * clone can carry a symlink named like documentation and pointed at a private key,
	 * and the corpus is sent over the network to a model endpoint, so what leaves the
	 * machine has to be decided by the repository's contents rather than by its links.
	 * {@code Files.walk} does not descend into linked directories, so only linked files
	 * need this.
	 * @param realRoot the repository root, links resolved
	 * @param file the candidate file
	 * @return true when reading the file reads the repository
	 */
	private static boolean staysInside(Path realRoot, Path file) {
		try {
			return file.toRealPath().startsWith(realRoot);
		}
		catch (IOException ex) {
			// A link with nothing at the end of it has no body to put in the corpus.
			return false;
		}
	}

	/**
	 * Greedy accumulator of the corpus: documents first, then source, each under an
	 * absolute character ceiling of its own. A file that does not fit is counted as
	 * omitted and the fill continues, so one oversized file late in the order cannot
	 * starve the smaller files after it.
	 */
	private static final class CorpusBuilder {

		private final StringBuilder text = new StringBuilder();

		private int included;

		private int omitted;

		private void appendAll(Path root, List<Path> files, int cap) {
			for (int file = 0; file < files.size(); file++) {
				Path path = files.get(file);
				String header = "\n\n--- " + root.relativize(path).toString().replace('\\', '/') + " ---\n";
				if (this.text.length() + header.length() > cap) {
					// The corpus only ever grows, so once a path label no longer fits,
					// nothing behind it can fit either. Count the rest and stop, rather
					// than opening thousands of files a monorepo has left after the cap.
					this.omitted += files.size() - file;
					return;
				}
				append(path, header, cap);
			}
		}

		private void append(Path file, String header, int cap) {
			String body = read(file);
			if (body.length() > MAX_FILE_CHARS) {
				body = body.substring(0, MAX_FILE_CHARS) + "\n[... file truncated ...]";
			}
			if (this.text.length() + header.length() + body.length() > cap) {
				this.omitted++;
				return;
			}
			this.text.append(header).append(body);
			this.included++;
		}

		private static String read(Path file) {
			try (InputStream in = Files.newInputStream(file)) {
				return new String(in.readNBytes(MAX_FILE_BYTES), StandardCharsets.UTF_8);
			}
			catch (IOException ex) {
				throw new IllegalStateException("Failed to read " + file + ": " + ex.getMessage(), ex);
			}
		}

	}

	/**
	 * The corpus that will be handed to the model, and the accounting a blueprint needs
	 * to state how much of the repository it did not see.
	 *
	 * @param text the corpus, every file labelled with its path relative to the root
	 * @param filesIncluded the number of files whose body made it into the corpus
	 * @param filesOmitted the number of recognised files the cap left out
	 */
	public record Corpus(String text, int filesIncluded, int filesOmitted) {
	}

}
