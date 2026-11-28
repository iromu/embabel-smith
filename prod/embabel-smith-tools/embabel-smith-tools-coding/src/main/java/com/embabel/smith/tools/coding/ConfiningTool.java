/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.embabel.agent.api.tool.DelegatingTool;
import com.embabel.agent.api.tool.Tool;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The wrapper that makes a tool the addon did not write obey the run root.
 *
 * <p>
 * <b>Why wrapping rather than withholding.</b> Design D8 says the addon either withholds
 * an unconfined method or wraps it — it does not publish an unconfined reader under a
 * role the default tier allows. Withholding is the wrong half here: the methods that skip
 * the link check are the file tools a coding agent needs, and the platform's own
 * instances are the ones the addon is required to publish. So every tool the addon
 * publishes passes through this wrapper, whose only addition is to re-check the
 * path-bearing arguments of a call against {@link RunRoot} before delegating. Upstream's
 * lexical check still runs underneath; this one adds the link-following half it cannot
 * do.
 *
 * <p>
 * <b>What is checked.</b> A string argument named {@code path}, {@code glob} or
 * {@code workingDirectory} — the names every offered tool in this group uses for a
 * location. The regex arguments of the content search are not locations and are not
 * checked; the search itself walks the root and does not follow links, so it cannot be
 * aimed outside.
 *
 * <p>
 * <b>What is bounded, on the way back.</b> The two search methods answer with a list of
 * matches, and an unbounded one is the worst-shaped result a model can be given: a short
 * list that reads as the whole answer, because nothing in it says otherwise. So the same
 * wrapper that checks where a call was aimed caps what the two of them report, at
 * {@link #SEARCH_CAP} matches, and a search that hit the limit says so in the result
 * rather than leaving the omission to be inferred. It is this decorator rather than an
 * edit of the platform's instances, which is what keeps the boundary and the cap in the
 * one place that sees every tool the addon publishes — including the composed roles the
 * expose map builds, which come through here too.
 *
 * <p>
 * <b>What is noted, on the way out.</b> The same wrapper sees which tool answered and
 * which path it was aimed at, which is the pair nothing else in this JVM has: upstream's
 * read log belongs to the read-only instance and names paths alone. So a group built with
 * a {@link ReadPathLog} can tell a run what it read, in the same per-call shape a script
 * call is reported in — and a refused call is not in it, because nothing was read.
 *
 * <p>
 * A refusal is returned as a tool error rather than thrown, so the run continues and the
 * tool result is where the operator sees it — the same shape upstream's own traversal
 * refusal takes.
 *
 * @author Ivan Rodriguez
 */
public final class ConfiningTool implements DelegatingTool {

	/**
	 * The argument names that carry a location, and so are checked before the call lands.
	 */
	static final Set<String> LOCATION_ARGUMENTS = Set.of("path", "glob", "workingDirectory");

	/**
	 * The offered methods that answer with a list of matches. The cap is scoped to these
	 * two names rather than applied to every result this wrapper passes back, because the
	 * rest of them answer with a file's contents or a single value, and truncating a
	 * {@code readFile} would be a different tool from the one the platform describes.
	 */
	static final Set<String> SEARCH_TOOLS = Set.of("findFiles", "findPatternInProject");

	/**
	 * The most matches either search method reports. Stated rather than tuned per
	 * deployment: it is the number below which a model can treat the answer as a list it
	 * has seen all of, and a knob invites a deployment to raise it until the listing
	 * stops meaning anything.
	 */
	public static final int SEARCH_CAP = 200;

	private static final ObjectMapper JSON = new ObjectMapper();

	private final Tool delegate;

	private final RunRoot root;

	private final ReadPathLog reads;

	ConfiningTool(Tool delegate, RunRoot root) {
		this(delegate, root, new ReadPathLog());
	}

	ConfiningTool(Tool delegate, RunRoot root, ReadPathLog reads) {
		this.delegate = delegate;
		this.root = root;
		this.reads = reads;
	}

	@Override
	public Tool getDelegate() {
		return this.delegate;
	}

	@Override
	public Tool.Definition getDefinition() {
		return this.delegate.getDefinition();
	}

	@Override
	public Tool.Result call(String input) {
		return settled(input, () -> this.delegate.call(input));
	}

	@Override
	public Tool.Result call(String input, com.embabel.agent.api.tool.ToolCallContext context) {
		return settled(input, () -> this.delegate.call(input, context));
	}

	/**
	 * The one route every call takes: confine, then delegate, then cap.
	 *
	 * <p>
	 * The order is the claim. The refusal is worked out before the delegate is touched,
	 * so a call aimed outside the root never reaches the platform's instance rather than
	 * reaching it and having its result edited afterwards. And a read that was refused is
	 * not recorded, because nothing was read.
	 * @param input the tool-call arguments as the model sent them
	 * @param call the delegate call, taken only once the arguments are known to stay
	 * inside
	 * @return the refusal, or the delegate's answer with the search cap applied
	 */
	private Tool.Result settled(String input, java.util.function.Supplier<Tool.Result> call) {
		JsonNode arguments = arguments(input);
		String refusal = refusalFor(arguments);
		if (refusal != null) {
			return Tool.Result.error(refusal);
		}
		return bounded(recorded(arguments, call.get()));
	}

	/**
	 * Note an answered read, and hand the answer back unchanged.
	 * @param arguments the call's arguments, already parsed
	 * @param result what the platform's instance answered
	 * @return {@code result}
	 */
	private Tool.Result recorded(JsonNode arguments, Tool.Result result) {
		String tool = getDefinition().getName();
		if (result instanceof Tool.Result.Error || !ReadPathLog.records(tool)) {
			return result;
		}
		// A search is recorded against the directory it searched, because its argument is
		// a
		// pattern rather than a location and a record naming a pattern reads as a file
		// that
		// no file matches.
		String file = (SEARCH_TOOLS.contains(tool)) ? this.root.path().toString() : touched(arguments);
		this.reads.record(file, tool, this.root.path().toString());
		return result;
	}

	/**
	 * The place a call was aimed at, inside the root.
	 * @param arguments the call's arguments
	 * @return the resolved path, or the root itself when the call named no location
	 */
	private String touched(JsonNode arguments) {
		String requested = location(arguments);
		if (requested == null) {
			return this.root.path().toString();
		}
		try {
			return this.root.resolve(requested).toString();
		}
		catch (RunRoot.EscapesRootException ex) {
			// Unreachable on the ordinary route, since refusalFor refused this call
			// first.
			// Named as the root rather than as the escape, so a record never states a
			// path
			// outside the root.
			return this.root.path().toString();
		}
	}

	/**
	 * The cap applied to a search answer, leaving every other answer alone.
	 *
	 * <p>
	 * The two shapes are the ones the two methods actually return — measured, not
	 * assumed: {@code findFiles} answers with a list of paths carried as an artifact
	 * beside its JSON text, and {@code findPatternInProject} answers with newline-joined
	 * matches. Anything else this tool passes back is returned untouched, because a cap
	 * that quietly clipped a {@code readFile} would make this wrapper into a different
	 * tool than the one the platform describes.
	 * @param result what the platform's instance answered
	 * @return the same answer, or its first {@link #SEARCH_CAP} matches saying that was
	 * all of them it kept
	 */
	private Tool.Result bounded(Tool.Result result) {
		if (!SEARCH_TOOLS.contains(getDefinition().getName()) || result instanceof Tool.Result.Error) {
			return result;
		}
		if (result instanceof Tool.Result.WithArtifact artifact && artifact.getArtifact() instanceof List<?> paths) {
			return (paths.size() <= SEARCH_CAP) ? result : capped(paths.stream().map(String::valueOf).toList(), true);
		}
		if (result instanceof Tool.Result.Text text) {
			List<String> lines = text.getContent().lines().toList();
			return (lines.size() <= SEARCH_CAP) ? result : capped(lines, false);
		}
		return result;
	}

	/**
	 * The truncated answer, saying what it left out. The count it names is the count that
	 * was found, so the reader can tell a search that just missed from one that was cut —
	 * and the sentence is in the result rather than in a log line, because the model
	 * deciding whether to narrow the search is the one reading this.
	 * @param matches every match the search found, in the order it found them
	 * @param carried whether the answer travels as a list beside its text, which is how
	 * {@code findFiles} answers
	 * @return the first {@link #SEARCH_CAP} of them, plus the statement
	 */
	private static Tool.Result capped(List<String> matches, boolean carried) {
		List<String> kept = List.copyOf(matches.subList(0, SEARCH_CAP));
		String statement = "[cap reached: " + SEARCH_CAP + " of " + matches.size() + " matches listed; the other "
				+ (matches.size() - SEARCH_CAP) + " were not returned, so this is not the whole answer. Narrow the "
				+ "pattern or the glob and search again.]";
		return (carried) ? Tool.Result.withArtifact(listed(kept) + "\n" + statement, kept)
				: Tool.Result.text(String.join("\n", kept) + "\n" + statement);
	}

	/**
	 * The kept paths as the text the platform's own answer carried.
	 * @param kept the paths within the cap
	 * @return the JSON array
	 */
	private static String listed(List<String> kept) {
		return JSON.writeValueAsString(kept);
	}

	/**
	 * The call's arguments, parsed once so that the check and the record read the same
	 * values.
	 * @param input the tool-call arguments as the model sent them
	 * @return the object node, or {@code null} when the call carries nothing to aim
	 * anywhere
	 */
	private static JsonNode arguments(String input) {
		if (input == null || input.isBlank()) {
			return null;
		}
		final JsonNode parsed;
		try {
			parsed = JSON.readTree(input);
		}
		catch (RuntimeException ex) {
			// Not JSON: nothing to aim anywhere. The delegate's own argument parsing is
			// the
			// thing that reports a malformed call, which is its business and its message.
			return null;
		}
		return (parsed != null && parsed.isObject()) ? parsed : null;
	}

	/**
	 * Why this call would leave the root, if it would.
	 * @param arguments the parsed arguments, or {@code null}
	 * @return the refusal text naming path and root, or {@code null} to let the call
	 * through
	 */
	String refusalFor(JsonNode arguments) {
		if (arguments == null) {
			return null;
		}
		for (Map.Entry<String, JsonNode> entry : arguments.properties()) {
			String requested = locationOf(entry);
			if (requested == null) {
				continue;
			}
			try {
				this.root.resolve(requested);
			}
			catch (RunRoot.EscapesRootException ex) {
				return ex.getMessage();
			}
		}
		return null;
	}

	/**
	 * The place a call names, which is what a read is recorded against.
	 * @param arguments the parsed arguments, or {@code null}
	 * @return the first location-bearing argument, or {@code null} when the call names
	 * none
	 */
	private static String location(JsonNode arguments) {
		if (arguments == null) {
			return null;
		}
		for (Map.Entry<String, JsonNode> entry : arguments.properties()) {
			String requested = locationOf(entry);
			if (requested != null) {
				return requested;
			}
		}
		return null;
	}

	private static String locationOf(Map.Entry<String, JsonNode> entry) {
		if (!LOCATION_ARGUMENTS.contains(entry.getKey()) || !entry.getValue().isTextual()) {
			return null;
		}
		return entry.getValue().asString();
	}

	/**
	 * Confine every tool in a list to one root, recording nothing.
	 * @param tools the tools as their authors made them
	 * @param root the run root to check their location arguments against
	 * @return the same tools, each wrapped once
	 */
	public static List<Tool> confine(List<Tool> tools, RunRoot root) {
		return confine(tools, root, new ReadPathLog());
	}

	/**
	 * Confine every tool in a list to one root, recording its reads.
	 * @param tools the tools as their authors made them
	 * @param root the run root to check their location arguments against
	 * @param reads the log answered reads are noted in
	 * @return the same tools, each wrapped once
	 */
	public static List<Tool> confine(List<Tool> tools, RunRoot root, ReadPathLog reads) {
		return tools.stream().map((tool) -> confine(tool, root, reads)).toList();
	}

	/**
	 * Confine one tool, recording nothing.
	 * @param tool the tool to wrap
	 * @param root the run root to check its location arguments against
	 * @return the confined tool
	 */
	public static Tool confine(Tool tool, RunRoot root) {
		return confine(tool, root, new ReadPathLog());
	}

	/**
	 * Confine one tool, leaving it alone when it is confined already, so a tool that
	 * checks its own arguments is not wrapped twice for the same answer.
	 *
	 * <p>
	 * A tool confined already keeps the log it was confined with, so a group that wants
	 * its reads recorded passes the log to the first confine its tools see, not to a
	 * later one.
	 * @param tool the tool to wrap
	 * @param root the run root to check its location arguments against
	 * @param reads the log answered reads are noted in
	 * @return the confined tool
	 */
	public static Tool confine(Tool tool, RunRoot root, ReadPathLog reads) {
		return (tool instanceof ConfiningTool) ? tool : new ConfiningTool(tool, root, reads);
	}

}
