/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.blueprint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The repository-to-blueprint step: a codebase in, the business-process document set out.
 * It is the job {@code prompts/business_process_discovery.jinja} was written for, and the
 * one step that runs <em>before</em> a build rather than inside one — its output is a
 * blueprint directory of the shape {@code examples/blueprints/<Name>/} has, which is what
 * {@code smith build --docs} consumes.
 *
 * <p>
 * The order is load, extract, split, write, and each stage can fail loudly on its own: a
 * corpus that fits no context window is bounded by {@link CodebaseCorpus}; an answer
 * whose headings name none of the deliverables is kept whole in
 * {@code DISCOVERY_ANSWER.md} rather than invented into one unlabelled document; an
 * answer that carries four of the seven writes those four and reports the three it did
 * not. A blueprint is a claim about a repository, so the parts of it that are missing
 * have to be as visible as the parts that are present.
 *
 * @author Ivan Rodriguez
 */
public class BlueprintGenerator {

	/**
	 * Where an answer that named no deliverable is written, so the call that produced it
	 * is not lost — the JVM does not log a plain-text completion.
	 */
	static final String RAW_ANSWER = "DISCOVERY_ANSWER.md";

	private final ProcessExtractor extractor;

	public BlueprintGenerator(ProcessExtractor extractor) {
		this.extractor = extractor;
	}

	/**
	 * Generates a blueprint from a codebase.
	 * @param source the repository to read, by git URL or local directory
	 * @param out the directory to write the documents to
	 * @param overwrite whether to replace the documents already in {@code out}
	 * @return the result, carrying the documents written and the deliverables missing
	 * @throws IllegalStateException if {@code out} holds documents and {@code overwrite}
	 * is false, or if the clone, archive or model call fails
	 */
	public BlueprintResult generate(CodebaseSource source, Path out, boolean overwrite) {
		refuseOverwrite(out, overwrite);
		Path fetched = materialise(source);
		try {
			Path root = narrow(source,
					(fetched != null) ? fetched : BusinessProcessDiscovery.expandHome(source.path()));
			CodebaseCorpus.Corpus corpus = new CodebaseCorpus().load(root);
			String answer = this.extractor.extractCodebase(corpus.text());
			Map<String, String> documents = new BlueprintSplitter().split(answer);
			List<String> missing = BlueprintSplitter.DELIVERABLES.stream()
				.filter((deliverable) -> !documents.containsKey(deliverable))
				.toList();
			// The call has already been paid for. An answer that names none of the
			// deliverables is still the run's only product, and the JVM does not log it,
			// so keep it rather than reporting a failure with nothing to show for it.
			Map<String, String> files = documents.isEmpty() ? Map.of(RAW_ANSWER, answer) : documents;
			return new BlueprintResult(missing.isEmpty(), out, write(out, files, source, corpus, missing, overwrite),
					missing, source, corpus);
		}
		finally {
			BusinessProcessDiscovery.deleteQuietly(fetched);
		}
	}

	/**
	 * The directory the codebase has to be brought into, or null when it is already one.
	 * @param source the codebase the run names
	 * @return a fresh temp directory holding the codebase, for this run to delete
	 */
	private static Path materialise(CodebaseSource source) {
		if (source.fromGit()) {
			return BusinessProcessDiscovery.cloneRepo(source.url().trim());
		}
		return source.fromArchive() ? ZipArchive.extract(Path.of(source.archive().trim())) : null;
	}

	/**
	 * Narrows a repository root to the subdirectory the run asked for, refusing one that
	 * escapes the clone.
	 * @param source the codebase the run names
	 * @param root the directory holding the codebase
	 * @return the directory to read, {@code root} itself when no subdirectory was named
	 */
	private static Path narrow(CodebaseSource source, Path root) {
		if (source.subdir() == null || source.subdir().isBlank()) {
			return root;
		}
		Path base = root.normalize();
		Path narrowed = base.resolve(source.subdir().trim()).normalize();
		if (!narrowed.startsWith(base)) {
			throw new IllegalArgumentException(
					"--subdir has to name a directory inside the repository: " + source.subdir());
		}
		if (!Files.isDirectory(narrowed)) {
			throw new IllegalArgumentException("No such directory in the codebase: " + source.subdir());
		}
		return narrowed;
	}

	/**
	 * Refuses to run over a directory that already holds documents. A blueprint is an
	 * authored artifact — the examples' blueprints are hand-written business
	 * documentation — so replacing one has to be the deliberate act {@code --overwrite}
	 * states rather than the default of a rerun.
	 * @param out the directory the blueprint would be written to
	 * @param overwrite whether replacing what is there was asked for
	 */
	private static void refuseOverwrite(Path out, boolean overwrite) {
		if (overwrite || !Files.isDirectory(out)) {
			return;
		}
		try (var entries = Files.list(out)) {
			if (entries.findAny().isPresent()) {
				throw new IllegalStateException(
						out + " already holds documents. Pass --overwrite to replace them, or --out a new directory.");
			}
		}
		catch (IOException ex) {
			throw new IllegalStateException("Cannot inspect " + out + ": " + ex.getMessage(), ex);
		}
	}

	/**
	 * Removes the documents a previous run of this command wrote, and only those. A rerun
	 * that produces fewer of them than the last one would otherwise leave the earlier
	 * documents in the directory beside a report saying they are missing, which is a
	 * blueprint that contradicts itself.
	 * @param out the directory about to be written
	 */
	private static void clearPreviousRun(Path out) {
		List<String> previous = new ArrayList<>(BlueprintSplitter.DELIVERABLES);
		previous.add("README.md");
		previous.add(RAW_ANSWER);
		for (String name : previous) {
			try {
				Files.deleteIfExists(out.resolve(name));
			}
			catch (IOException ex) {
				throw new IllegalStateException("Failed to replace " + out.resolve(name) + ": " + ex.getMessage(), ex);
			}
		}
	}

	/**
	 * Writes the deliverables plus the index that says where they came from, returning
	 * each file with the size it was written at. An overwrite clears the previous run's
	 * documents here rather than at the start of {@code generate}: the model call is the
	 * step most likely to fail, and a run that fails has to leave the blueprint it was
	 * given rather than an empty directory.
	 * @param out the directory to write to
	 * @param documents the deliverables the answer carried
	 * @param source the codebase the blueprint comes from
	 * @param corpus the corpus the model was shown, for the index's provenance line
	 * @param missing the deliverables the answer did not carry
	 * @param overwrite whether the documents this command wrote earlier may be removed
	 * first
	 * @return the files written, keyed by name with the characters written as value
	 */
	private Map<String, Integer> write(Path out, Map<String, String> documents, CodebaseSource source,
			CodebaseCorpus.Corpus corpus, List<String> missing, boolean overwrite) {
		Map<String, String> files = new LinkedHashMap<>(documents);
		files.put("README.md", readme(source, corpus, documents, missing));
		Map<String, Integer> written = new LinkedHashMap<>();
		try {
			Files.createDirectories(out);
			if (overwrite) {
				clearPreviousRun(out);
			}
			for (Map.Entry<String, String> file : files.entrySet()) {
				Files.writeString(out.resolve(file.getKey()), file.getValue());
				written.put(file.getKey(), file.getValue().length());
			}
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to write the blueprint to " + out + ": " + ex.getMessage(), ex);
		}
		return written;
	}

	/**
	 * The index document: what the model was shown, which documents exist, and which it
	 * never produced.
	 * @param source the codebase the blueprint comes from
	 * @param corpus the corpus the model was shown
	 * @param documents the deliverables that were written
	 * @param missing the deliverables that were not
	 * @return the README text
	 */
	private static String readme(CodebaseSource source, CodebaseCorpus.Corpus corpus, Map<String, String> documents,
			List<String> missing) {
		StringBuilder readme = new StringBuilder().append("# ")
			.append(source.title())
			.append(" — Business Process Documentation\n\n")
			.append("Business-level description of what this application does, who uses it and the processes it ")
			.append("follows, in business terms rather than implementation terms.\n\n")
			.append("Generated by `smith blueprint` from `")
			.append(source.describe())
			.append("` on ")
			.append(LocalDate.now())
			.append(".\n\n")
			.append("The model was shown ")
			.append(corpus.filesIncluded())
			.append(" files, ")
			.append(String.format(Locale.ROOT, "%,d", corpus.text().length()))
			.append(" characters");
		if (corpus.filesOmitted() > 0) {
			readme.append("; ")
				.append(corpus.filesOmitted())
				.append(" further files were beyond the corpus cap and are not reflected here");
		}
		readme.append(".\n\n## Documents\n\n");
		documents.forEach((name, body) -> readme.append("- [")
			.append(name)
			.append("](")
			.append(name)
			.append(")")
			.append(titleOf(body).map((title) -> " — " + title).orElse(""))
			.append('\n'));
		if (!missing.isEmpty()) {
			readme.append("\nThe discovery answer produced none of these deliverables: ")
				.append(String.join(", ", missing))
				.append(".\n");
		}
		return readme.toString();
	}

	private static Optional<String> titleOf(String body) {
		return body.lines()
			.filter((line) -> line.stripLeading().startsWith("#"))
			.map((line) -> line.replaceFirst("^#+\\s*", "").strip())
			.filter((title) -> !title.isEmpty())
			.findFirst();
	}

	/**
	 * Where a blueprint comes from: a git repository or a local checkout, optionally
	 * narrowed to one directory of it.
	 *
	 * @param url the repository URL to clone, or null for a local directory
	 * @param path the local directory to read, or null for a repository URL
	 * @param archive the local archive to read, or null for a directory or a URL
	 * @param subdir a directory inside the source to build the blueprint from, or null
	 * for the whole of it
	 */
	public record CodebaseSource(String url, String path, String archive, String subdir) {

		/**
		 * Checks that exactly one of the three locations was named.
		 * @param url the repository URL to clone, or null
		 * @param path the local directory to read, or null
		 * @param archive the local archive to read, or null
		 * @param subdir a directory inside the source to build the blueprint from, or
		 * null for the whole of it
		 * @throws IllegalArgumentException if other than exactly one of {@code url},
		 * {@code path} and {@code archive} is given
		 */
		public CodebaseSource {
			int named = (given(url) ? 1 : 0) + (given(path) ? 1 : 0) + (given(archive) ? 1 : 0);
			if (named != 1) {
				throw new IllegalArgumentException(
						"A codebase comes from one of a git URL, a directory or an archive.");
			}
		}

		private static boolean given(String location) {
			return location != null && !location.isBlank();
		}

		/**
		 * Whether this source is cloned rather than read where it already sits.
		 * @return true when the source names a git URL
		 */
		public boolean fromGit() {
			return given(this.url);
		}

		/**
		 * Whether this source has to be extracted before it can be read.
		 * @return true when the source names an archive
		 */
		public boolean fromArchive() {
			return given(this.archive);
		}

		/**
		 * The source as a reader names it in the provenance line of the index.
		 * @return the URL, the directory path or the archive path
		 */
		public String describe() {
			return (fromGit() ? this.url : (fromArchive() ? this.archive : this.path)).trim();
		}

		/**
		 * The short name of the source, used as the blueprint's title and as the example
		 * script's directory name.
		 * @return the last path segment, without a {@code .git} or {@code .zip} suffix
		 */
		public String title() {
			String location = describe().replaceAll("/+$", "");
			int slash = location.lastIndexOf('/');
			String last = (slash < 0) ? location : location.substring(slash + 1);
			return last.replaceFirst("\\.(git|zip)$", "");
		}

	}

	/**
	 * What a blueprint run produced.
	 *
	 * @param complete whether every deliverable the prompt asks for was written
	 * @param directory the blueprint directory
	 * @param documents the files written, keyed by name with the characters written as
	 * value
	 * @param missing deliverables the answer carried no heading for
	 * @param source the codebase the blueprint was generated from
	 * @param corpus the corpus the model was actually shown
	 */
	public record BlueprintResult(boolean complete, Path directory, Map<String, Integer> documents,
			List<String> missing, CodebaseSource source, CodebaseCorpus.Corpus corpus) {

		/**
		 * The run report, starting with the verdict line the CLI's callers cut the report
		 * out on.
		 * @return the report text, ending with a newline
		 */
		public String report() {
			List<String> lines = new ArrayList<>();
			lines.add((this.complete ? "BUILD OK: " : "BUILD BLOCKED: ") + "blueprint of " + this.source.describe()
					+ " — " + this.documents.size() + plural(this.documents.size(), " document") + " in "
					+ this.directory);
			StringBuilder corpusLine = new StringBuilder("  corpus    : ").append(this.corpus.filesIncluded())
				.append(plural(this.corpus.filesIncluded(), " file"))
				.append(", ")
				.append(String.format(Locale.ROOT, "%,d", this.corpus.text().length()))
				.append(" chars shown to the model");
			if (this.corpus.filesOmitted() > 0) {
				corpusLine.append(", ").append(this.corpus.filesOmitted()).append(" more left out");
			}
			lines.add(corpusLine.toString());
			this.documents.forEach((name, chars) -> lines
				.add("  - " + name + "  " + String.format(Locale.ROOT, "%,d", chars) + " chars"));
			if (!this.missing.isEmpty()) {
				lines.add("  missing   : " + String.join(", ", this.missing));
			}
			return String.join("\n", lines) + "\n";
		}

		private static String plural(int count, String noun) {
			return (count == 1) ? noun : noun + "s";
		}

	}

}
