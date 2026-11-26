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
import java.util.Map;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.core.ToolGroup;
import com.embabel.smith.builder.blueprint.BlueprintLoader;
import com.embabel.smith.builder.blueprint.BusinessProcessDiscovery;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The {@code blueprint} tool group: the documents the build was given, readable by a pack
 * step in pieces instead of only as the corpus the driver already loaded.
 *
 * <p>
 * <b>Why this group exists at all.</b> The ingestion caps are the product's, not a
 * limitation to route around: {@code blueprint/BusinessProcessDiscovery} stops at 300,000
 * characters and {@code blueprint/BlueprintLoader} at 200,000, and the loader says so
 * inside the corpus it returns. Past that ceiling a model today simply sees less, with no
 * way to ask for more. Listing and paging the same documents is what a tool is for —
 * which is also why the corpus tool keeps the loader's marker rather than summarising it
 * away.
 *
 * <p>
 * <b>What a step may ask for.</b> A document it was shown. The roots come from
 * {@link ToolSurface}, which the driver filled from the command line; the only argument
 * {@code read_doc} takes is a document name {@code list_docs} printed. A name outside the
 * opened roots is refused, so the group cannot become a filesystem view.
 *
 * @author Ivan Rodriguez
 */
@Configuration
public class BlueprintToolGroup {

	/** The tool-group role a pack step declares to read the build's documents. */
	public static final String ROLE = "blueprint";

	/** Lists the documents the driver opened. */
	public static final String LIST_DOCS = "list_docs";

	/** Reads one listed document, from an optional offset. */
	public static final String READ_DOC = "read_doc";

	/** Assembles the corpus the Java flow would have used, marker included. */
	public static final String ASSEMBLE_CORPUS = "assemble_corpus";

	/** The document argument of {@link #READ_DOC}: a name {@link #LIST_DOCS} printed. */
	public static final String DOCUMENT = "document";

	/** The paging argument of {@link #READ_DOC}: characters to skip. */
	public static final String OFFSET = "offsetChars";

	/**
	 * The characters {@link #READ_DOC} keeps back out of {@code ToolOutput.MAX_CHARS} for
	 * its own header and its paging hint.
	 */
	static final int HEADER_ALLOWANCE = 256;

	/**
	 * The group the platform registers under {@link #ROLE}.
	 * @param surface what the driver opened for this build
	 * @return the {@code blueprint} tool group
	 */
	@Bean
	public ToolGroup documentTools(ToolSurface surface) {
		return ToolGroups.ofTools(ROLE, "Read the business-process documents this build was given: list them, "
				+ "read one of them, or assemble the whole corpus. Every document is one the operator supplied; a "
				+ "path outside them is refused.",
				List.of(listDocs(surface), readDoc(surface), assembleCorpus(surface)));
	}

	private static Tool listDocs(ToolSurface surface) {
		return Tool.Companion.of(LIST_DOCS,
				"List the business-process documents the build was given, with each one's size, so you can decide "
						+ "which to read. No arguments.",
				Tool.InputSchema.of(), Tool.Metadata.DEFAULT, (Tool.Function) (arguments) -> Tool.Result
					.text(ToolOutput.cap(surface.remembered(LIST_DOCS, arguments, () -> listing(surface)))));
	}

	private static Tool readDoc(ToolSurface surface) {
		return Tool.Companion.of(READ_DOC,
				"Read one business-process document, named exactly as list_docs printed it. Returns the document's "
						+ "text up to the tool's cap; pass the offset it reports to continue a longer one.",
				Tool.InputSchema.of(
						Tool.Parameter.string(DOCUMENT, "A document path exactly as list_docs printed it", true),
						Tool.Parameter.integer(OFFSET, "Characters to skip from the start of the document", false)),
				Tool.Metadata.DEFAULT,
				(Tool.Function) (arguments) -> Tool.Result.text(ToolOutput.cap(surface.remembered(READ_DOC, arguments,
						() -> read(surface, ToolArguments.string(arguments, DOCUMENT), offset(arguments))))));
	}

	private static Tool assembleCorpus(ToolSurface surface) {
		return Tool.Companion.of(ASSEMBLE_CORPUS,
				"Assemble the whole document corpus the build would have used, in the loader's own format. It is "
						+ "capped, and where the cap bites the result says which file and everything after it was left "
						+ "out. No arguments.",
				Tool.InputSchema.of(), Tool.Metadata.DEFAULT, (Tool.Function) (arguments) -> Tool.Result
					.text(ToolOutput.cap(surface.remembered(ASSEMBLE_CORPUS, arguments, () -> corpus(surface)))));
	}

	/**
	 * The listing the model is offered: one line per document the driver opened,
	 * absolute, with its size, so the argument to {@code read_doc} is always a string
	 * copied from here.
	 * @param surface the driver's opened roots
	 * @return the listing, or the reason there is none
	 */
	static String listing(ToolSurface surface) {
		List<Path> roots = surface.blueprintRoots();
		if (roots.isEmpty()) {
			return "This build was given no documents, so there is nothing to list.";
		}
		List<String> lines = new ArrayList<>();
		for (Path root : roots) {
			try {
				Map<String, String> docs = new BusinessProcessDiscovery().load(root.toString(), null).docs();
				lines.add(root + " — " + docs.size() + " documents, " + total(docs) + " characters:");
				docs.forEach((name, body) -> lines.add("  " + root.resolve(name) + " (" + body.length() + " chars)"));
			}
			catch (IllegalArgumentException ex) {
				lines.add(root + " — " + ex.getMessage());
			}
		}
		return String.join("\n", lines);
	}

	/**
	 * One document, from {@code offset}. The name has to be a document under an opened
	 * root and one the loader would have accepted, which is the whole of the containment:
	 * the model chooses among what it was shown, it does not choose where to look.
	 * @param surface the driver's opened roots
	 * @param document the name the model sent
	 * @param offset characters to skip
	 * @return the document text, or the reason there is none
	 */
	static String read(ToolSurface surface, String document, int offset) {
		List<Path> roots = surface.blueprintRoots();
		if (document.isBlank()) {
			return READ_DOC + " needs the '" + DOCUMENT + "' argument: a document path exactly as " + LIST_DOCS
					+ " printed it.";
		}
		if (roots.isEmpty()) {
			return "This build was given no documents, so there is nothing to read. Call " + LIST_DOCS + " to confirm.";
		}
		Path named = Path.of(document).normalize();
		Path root = roots.stream()
			.filter((candidate) -> named.startsWith(candidate.normalize()))
			.findFirst()
			.orElse(null);
		if (root == null) {
			return "'" + document + "' is not inside a directory this build was given. The opened roots are " + roots
					+ "; call " + LIST_DOCS + " for the documents you can read.";
		}
		if (!isMarkdown(named)) {
			return "'" + document + "' is not a .md or .markdown document, which is what this build's ingestion "
					+ "reads.";
		}
		String body = readQuietly(named);
		if (body == null) {
			return "'" + document + "' could not be read. Call " + LIST_DOCS + " for the documents that exist.";
		}
		if (offset >= body.length()) {
			return "[end of " + document + ": " + body.length() + " characters, all already returned]";
		}
		String slice = body.substring(Math.max(0, offset));
		// The room is taken off the cap rather than added to it, so the header and the
		// paging hint survive the cap every tool's result goes through — a cut that
		// removed the instruction for how to continue would be a truncation that hid the
		// only part the step could act on.
		int room = ToolOutput.MAX_CHARS - HEADER_ALLOWANCE;
		String cut = slice.substring(0, Math.min(slice.length(), room));
		int next = Math.max(0, offset) + cut.length();
		return document + ": " + body.length() + " characters, " + cut.length() + " returned from offset " + offset
				+ System.lineSeparator() + cut + ((next < body.length()) ? System.lineSeparator()
						+ "[more remains; call " + READ_DOC + " again with " + OFFSET + "=" + next + "]" : "");
	}

	/**
	 * The corpus as {@code BlueprintLoader} renders it, so what a step reads through a
	 * tool is byte for byte what the Java flow puts in the prompt — truncation marker
	 * included.
	 * @param surface the driver's opened roots
	 * @return the corpus, or the reason there is none
	 */
	static String corpus(ToolSurface surface) {
		List<Path> roots = surface.blueprintRoots();
		if (roots.isEmpty()) {
			return "This build was given no documents, so there is no corpus to assemble.";
		}
		BlueprintLoader loader = new BlueprintLoader();
		List<String> parts = new ArrayList<>();
		for (Path root : roots) {
			try {
				parts.add(loader.resolve(null, root.toString()));
			}
			catch (IllegalArgumentException ex) {
				parts.add(root + ": " + ex.getMessage());
			}
		}
		return String.join("\n\n", parts);
	}

	private static int offset(String arguments) {
		String raw = ToolArguments.string(arguments, OFFSET);
		try {
			return raw.isBlank() ? 0 : Integer.parseInt(raw.trim());
		}
		catch (NumberFormatException ex) {
			return 0;
		}
	}

	private static boolean isMarkdown(Path file) {
		String name = file.getFileName().toString().toLowerCase();
		return name.endsWith(".md") || name.endsWith(".markdown");
	}

	private static String readQuietly(Path file) {
		try {
			return Files.readString(file);
		}
		catch (IOException ex) {
			return null;
		}
	}

	private static int total(Map<String, String> docs) {
		return docs.values().stream().mapToInt(String::length).sum();
	}

}
