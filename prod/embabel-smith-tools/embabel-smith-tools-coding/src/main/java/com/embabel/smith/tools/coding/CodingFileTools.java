/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.tools.file.FileReadTools;
import com.embabel.agent.tools.file.FileTools;
import com.embabel.agent.tools.file.FileWriteTools;
import com.embabel.agent.tools.file.LocalDirectory;
import com.embabel.agent.tools.file.PatternSearch;

/**
 * The platform's own file tools, standing at one run root.
 *
 * <p>
 * <b>Three instances, one tool list.</b> The addon publishes the platform's
 * {@link FileReadTools}, {@link FileWriteTools} and {@link PatternSearch} rather than a
 * second implementation of the same acts. Those three interfaces overlap — the write
 * implementation carries the read methods too, and {@link LocalDirectory} is both a
 * reader and the shipped search — so the list is taken in that order and deduplicated by
 * tool name, which means a read tool is the one the read-only instance answers with.
 * Nothing here instantiates a class a property named: the three are built through
 * {@code FileTools.readOnly}, {@code FileTools.readWrite} and {@code LocalDirectory}'s
 * own constructor.
 *
 * <p>
 * <b>One method is deliberately absent.</b> The platform's {@code editFile} replaces
 * every occurrence of the span it is given and answers {@code "file edited"} without
 * naming the path, so an ambiguous match is rewritten and the caller cannot tell what
 * changed. This addon's edit scenarios require the opposite, so the platform's method is
 * dropped from the list and {@link TargetedEditTool} takes its place under the same name
 * — one targeted-edit tool per group, not two that disagree. The constant naming it is
 * the seam a later capability map has to honour: a capability built straight from
 * {@code FileTools.readWrite(root).tools()} reintroduces the platform's {@code editFile}
 * beside this one, because the drop happens here and nowhere else. Route any composed
 * capability through {@link #tools()} or filter by {@link #SUPERSEDED_TOOLS}.
 *
 * <p>
 * <b>Writes are off with nothing configured, and off means not built.</b>
 * {@code readWrite} is constructed only when {@code smith.tools.coding.writes} says so,
 * so an untouched deployment's {@code file} group offers the reads, the listings and the
 * searches and no {@code createFile}, {@code writeFile}, {@code editFile},
 * {@code appendFile} or {@code delete} — not because a filter removed them from a built
 * list, but because no writer exists to offer them. {@link #writeTools()} answers
 * {@code null} for exactly that reason, and the boot test asserts it: a deployment that
 * merely hid a writer it had already built would still be a deployment holding one.
 *
 * <p>
 * <b>The edit tool follows the same switch.</b> {@link TargetedEditTool} replaces the
 * platform's {@code editFile}, and it writes, so it is offered only when writes are on —
 * a read-only group carrying an edit tool would be the one hole in the switch.
 *
 * <p>
 * <b>Why this is a bean and not a static call.</b> The platform's readers log what they
 * read ({@code FileReadLog.getPathsRead()}), and that log belongs to the instance the
 * tools were made from. Holding the instances as a bean is what lets a host — and the
 * addon's own boot test — report which paths a run touched, instead of only claiming that
 * it could.
 *
 * @author Ivan Rodriguez
 */
public final class CodingFileTools {

	/**
	 * The offered write method this addon supersedes with its own. {@code editFile} is
	 * the only platform method whose result contract the addon cannot honour as shipped.
	 */
	public static final Set<String> SUPERSEDED_TOOLS = Set.of(TargetedEditTool.EDIT_FILE);

	/** The description the search instance carries for the root. */
	private static final String DIRECTORY_DESCRIPTION = "The run root: the directory this deployment is confined to.";

	private final RunRoot root;

	private final FileReadTools readTools;

	private final FileWriteTools writeTools;

	private final PatternSearch searchTools;

	private CodingFileTools(RunRoot root, FileReadTools readTools, FileWriteTools writeTools,
			PatternSearch searchTools) {
		this.root = root;
		this.readTools = readTools;
		this.writeTools = writeTools;
		this.searchTools = searchTools;
	}

	/**
	 * Build the platform instances at one root, with a writer only if asked for one.
	 * @param root the run root every one of them is confined to
	 * @param writes whether to build {@code FileTools.readWrite} at all. False leaves
	 * this object holding no writer, which is the state the switch's documented default
	 * is meant to be in
	 * @return the file tools for that root
	 */
	public static CodingFileTools at(RunRoot root, boolean writes) {
		String path = root.path().toString();
		return new CodingFileTools(root, FileTools.readOnly(path), writes ? FileTools.readWrite(path) : null,
				new LocalDirectory(path, DIRECTORY_DESCRIPTION));
	}

	/**
	 * The reader, which is also the instance that records what was read.
	 * @return the platform's read-only file tools
	 */
	public FileReadTools readTools() {
		return this.readTools;
	}

	/**
	 * The writer, if this deployment built one.
	 * @return the platform's read-write file tools, whose edit method is not offered;
	 * {@code null} when writes are off — which is the point rather than an oversight,
	 * since nothing here holds a writer a deployment did not ask for
	 */
	public FileWriteTools writeTools() {
		return this.writeTools;
	}

	/**
	 * Whether a writer was built, which is the same question as whether an edit tool is
	 * offered.
	 * @return {@code true} when this object holds a writer
	 */
	public boolean writes() {
		return this.writeTools != null;
	}

	/**
	 * The content search.
	 * @return the platform's pattern search over the root
	 */
	public PatternSearch searchTools() {
		return this.searchTools;
	}

	/**
	 * The tools the {@code file} group offers: the platform's instances' offered methods,
	 * deduplicated by name, minus {@link #SUPERSEDED_TOOLS}, plus the addon's own
	 * targeted edit when writes are on — and every one of them confined, so a location
	 * argument that resolves outside the root is refused before the platform's own
	 * lexical check is reached.
	 * @return the file group's tool list, in read, write, search, edit order; with writes
	 * off the write and edit entries are absent, because nothing was built to source them
	 */
	public List<Tool> tools() {
		Map<String, Tool> offered = new LinkedHashMap<>();
		Tool.fromInstance(this.readTools).forEach((tool) -> offered.putIfAbsent(name(tool), tool));
		if (this.writeTools != null) {
			Tool.fromInstance(this.writeTools).forEach((tool) -> offered.putIfAbsent(name(tool), tool));
		}
		Tool.fromInstance(this.searchTools).forEach((tool) -> offered.putIfAbsent(name(tool), tool));
		SUPERSEDED_TOOLS.forEach(offered::remove);
		List<Tool> withEdit = new ArrayList<>(offered.values());
		if (this.writeTools != null) {
			withEdit.add(TargetedEditTool.tool(this.root));
		}
		return ConfiningTool.confine(withEdit, this.root);
	}

	private static String name(Tool tool) {
		return tool.getDefinition().getName();
	}

}
