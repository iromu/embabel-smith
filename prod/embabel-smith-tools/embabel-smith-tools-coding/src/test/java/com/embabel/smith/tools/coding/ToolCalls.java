/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.core.ToolGroup;

/**
 * Calling tools the way the platform calls them: a tool name, a JSON argument object, and
 * the text the model would have read back.
 *
 * <p>
 * Errors are rendered with an {@code ERROR:} prefix rather than thrown, because these
 * tests are largely about what a refusal <em>says</em> — a tool that refuses by returning
 * an error result is doing its job, and the assertion is on the sentence.
 *
 * @author Ivan Rodriguez
 */
final class ToolCalls {

	private ToolCalls() {
	}

	/**
	 * Invoke a tool with string arguments.
	 * @param tool the tool as offered by a group
	 * @param arguments alternating name, value pairs
	 * @return the answer as text, prefixed {@code ERROR:} when the tool refused
	 */
	static String call(Tool tool, String... arguments) {
		return render(tool.call(json(arguments)));
	}

	/**
	 * The argument object a model would have sent.
	 * @param arguments alternating name, value pairs
	 * @return the JSON
	 */
	static String json(String... arguments) {
		if (arguments.length % 2 != 0) {
			throw new IllegalArgumentException("expected name/value pairs");
		}
		StringBuilder json = new StringBuilder("{");
		for (int at = 0; at < arguments.length; at += 2) {
			json.append((at == 0) ? "" : ",")
				.append('"')
				.append(escape(arguments[at]))
				.append("\":\"")
				.append(escape(arguments[at + 1]))
				.append('"');
		}
		return json.append("}").toString();
	}

	/**
	 * The text half of a result.
	 * @param result what the tool returned
	 * @return the text, or the error message prefixed to show it was a refusal
	 */
	static String render(Tool.Result result) {
		if (result instanceof Tool.Result.Text text) {
			return text.getContent();
		}
		if (result instanceof Tool.Result.Error error) {
			return "ERROR: " + error.getMessage();
		}
		return String.valueOf(result);
	}

	/**
	 * The names a group offers a model.
	 * @param group the registered group
	 * @return the tool names, in the group's own order
	 */
	static List<String> names(ToolGroup group) {
		return group.getTools().stream().map(ToolCalls::name).toList();
	}

	/**
	 * The parameter names one offered tool declares.
	 * @param group the registered group
	 * @param toolName the tool to look at
	 * @return the parameter names in declaration order
	 */
	static List<String> parameters(ToolGroup group, String toolName) {
		return group.getTools()
			.stream()
			.filter((tool) -> name(tool).equals(toolName))
			.findFirst()
			.orElseThrow(() -> new AssertionError("tool '" + toolName + "' is not offered; offered " + names(group)))
			.getDefinition()
			.getInputSchema()
			.getParameters()
			.stream()
			.map(Tool.Parameter::getName)
			.toList();
	}

	/**
	 * A tool by name.
	 * @param group the registered group
	 * @param toolName the name to find
	 * @return the tool
	 */
	static Tool tool(ToolGroup group, String toolName) {
		return group.getTools()
			.stream()
			.filter((tool) -> name(tool).equals(toolName))
			.findFirst()
			.orElseThrow(() -> new AssertionError("tool '" + toolName + "' is not offered; offered " + names(group)));
	}

	/**
	 * The roles the context registered as tool groups.
	 * @param groups the {@code ToolGroup} beans, by name
	 * @return the roles, sorted, so a set difference reads as a name
	 */
	static List<String> roles(Map<String, ToolGroup> groups) {
		return groups.values().stream().map((group) -> group.getMetadata().getRole()).sorted().toList();
	}

	private static String name(Tool tool) {
		return tool.getDefinition().getName();
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
	}

	/**
	 * A fresh directory for one test class's run root, removed when the JVM exits.
	 * @param prefix the temp-directory prefix
	 * @return the directory
	 */
	static Path root(String prefix) {
		final Path dir;
		try {
			dir = Files.createTempDirectory(prefix);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		Runtime.getRuntime().addShutdownHook(new Thread(() -> remove(dir)));
		return dir;
	}

	private static void remove(Path dir) {
		try (var paths = Files.walk(dir)) {
			List<Path> reverse = paths.sorted(Comparator.reverseOrder()).collect(Collectors.toList());
			for (Path path : reverse) {
				try {
					Files.deleteIfExists(path);
				}
				catch (IOException ignored) {
					// Best effort at shutdown; the directory is outside the repo.
				}
			}
		}
		catch (IOException ignored) {
			// Nothing left to do with a directory that cannot be walked.
		}
	}

}
