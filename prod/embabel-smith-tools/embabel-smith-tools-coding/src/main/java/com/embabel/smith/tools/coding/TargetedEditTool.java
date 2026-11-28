/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.embabel.agent.api.tool.Tool;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The targeted edit: replace one span of an existing file, and only when exactly one span
 * matches.
 *
 * <p>
 * <b>Why this is the addon's own when the platform already has an edit tool.</b> The
 * platform's {@code FileWriteTools.editFile(path, oldContent, newContent)} is
 * {@code oldFileContent.replace(oldContent, newContent)}: it replaces <em>every</em>
 * occurrence, so an ambiguous match is rewritten rather than refused; it answers
 * {@code "file edited"} or {@code "no changes made"}, so neither the path nor the reason
 * reaches the caller; and a text the file does not contain is reported as "no changes
 * made", which is also what it says when the caller asked to replace a span with itself.
 * All three of the edit scenarios this addon is specified against are about the answer as
 * much as the write, so the addon authors the edit and — this is the part worth stating
 * plainly — does not publish the platform's method alongside it. Two tools named for one
 * act, one of which silently does the wrong thing with it, is worse than the deviation it
 * costs. Everything else the platform's write tools do (create, overwrite, append,
 * delete, mkdir) is published unchanged.
 *
 * <p>
 * The path is confined the same way every other tool's is: refused naming path and root.
 *
 * @author Ivan Rodriguez
 */
public final class TargetedEditTool {

	/** The tool name a model calls. */
	public static final String EDIT_FILE = "editFile";

	/** The argument carrying the file to edit. */
	public static final String PATH = "path";

	/** The argument carrying the span to replace. */
	public static final String OLD_CONTENT = "oldContent";

	/** The argument carrying the replacement. */
	public static final String NEW_CONTENT = "newContent";

	private static final String DESCRIPTION = "Replace oldContent with newContent in an existing file. The edit "
			+ "requires oldContent to occur exactly once: a span that occurs nowhere, or more than once, is refused "
			+ "and nothing is written. Reports the path it changed.";

	private static final ObjectMapper JSON = new ObjectMapper();

	private TargetedEditTool() {
	}

	/**
	 * The edit tool, confined to one root.
	 * @param root the run root the edited file must stay inside
	 * @return the tool the {@code file} group adds to the platform's write tools
	 */
	public static Tool tool(RunRoot root) {
		return Tool.Companion.of(EDIT_FILE, DESCRIPTION,
				Tool.InputSchema.of(Tool.Parameter.string(PATH, "The file to edit, relative to the run root.", true),
						Tool.Parameter.string(OLD_CONTENT, "The span to replace. Must occur exactly once.", true),
						Tool.Parameter.string(NEW_CONTENT, "What to put in its place.", true)),
				Tool.Metadata.DEFAULT, (Tool.Function) (arguments) -> call(root, arguments));
	}

	/**
	 * The tool's body.
	 * @param root the run root
	 * @param arguments the tool-call arguments as the model sent them
	 * @return the result: the path it changed, or the reason nothing was written
	 */
	static Tool.Result call(RunRoot root, String arguments) {
		String requested = text(argument(arguments, PATH));
		final Path file;
		try {
			file = root.resolve(requested);
		}
		catch (RunRoot.EscapesRootException ex) {
			return Tool.Result.error(ex.getMessage());
		}
		String oldContent = text(argument(arguments, OLD_CONTENT));
		String newContent = text(argument(arguments, NEW_CONTENT));
		if (oldContent.isEmpty()) {
			return Tool.Result.error(root.refusal(requested, "an empty oldContent matches everywhere"));
		}
		if (!Files.isRegularFile(file)) {
			return Tool.Result.error("cannot edit '" + requested + "': no regular file there [path=" + requested
					+ ", root=" + root + "]");
		}
		final String contents;
		try {
			contents = Files.readString(file);
		}
		catch (IOException ex) {
			return Tool.Result.error("cannot read '" + requested + "': " + ex.getMessage() + " [path=" + requested
					+ ", root=" + root + "]");
		}
		int matches = occurrences(contents, oldContent);
		if (matches == 0) {
			return Tool.Result.error("no match: '" + requested + "' does not contain the text to replace, so nothing "
					+ "was written [path=" + requested + ", root=" + root + "]");
		}
		if (matches > 1) {
			return Tool.Result.error("ambiguous: the text to replace occurs " + matches + " times in '" + requested
					+ "', so nothing was written — name a span that occurs once [path=" + requested + ", root=" + root
					+ "]");
		}
		try {
			int at = contents.indexOf(oldContent);
			Files.writeString(file,
					contents.substring(0, at) + newContent + contents.substring(at + oldContent.length()));
		}
		catch (IOException ex) {
			return Tool.Result.error("failed to write '" + requested + "': " + ex.getMessage() + " [path=" + requested
					+ ", root=" + root + "]");
		}
		return Tool.Result.text("edited " + file + ": replaced the one occurrence of the given span" + " [path="
				+ requested + ", root=" + root + "]");
	}

	/**
	 * How many times a span occurs, counting non-overlapping positions.
	 * @param contents the file's text
	 * @param oldContent the span to count
	 * @return the number of matches
	 */
	static int occurrences(String contents, String oldContent) {
		int matches = 0;
		for (int at = contents.indexOf(oldContent); at >= 0; at = contents.indexOf(oldContent,
				at + oldContent.length())) {
			matches++;
		}
		return matches;
	}

	private static JsonNode argument(String arguments, String name) {
		if (arguments == null || arguments.isBlank()) {
			return null;
		}
		try {
			JsonNode root = JSON.readTree(arguments);
			return root.isObject() ? root.get(name) : null;
		}
		catch (RuntimeException ex) {
			return null;
		}
	}

	private static String text(JsonNode node) {
		if (node == null || node.isNull() || !node.isValueNode()) {
			return "";
		}
		return node.asString();
	}

}
