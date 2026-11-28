/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.core.ToolGroup;
import com.embabel.agent.tools.file.FileTools;
import com.embabel.agent.tools.file.LocalDirectory;

/**
 * The closed vocabulary behind {@code smith.tools.expose.<role>}: what an operator can
 * ask for without writing any code, and the reason nothing else can be asked for.
 *
 * <p>
 * <b>Why a map of names rather than a list of classes.</b> Upstream already offers an
 * operator-authored role — {@code embabel.agent.platform.tools.includes.<role>} — and
 * that one composes <em>MCP tools by name</em> out of whatever servers the deployment
 * happens to be connected to. This map is the same idea pointed at this addon's own
 * classes: each entry is one of four names, and each name is wired to a specific platform
 * factory call written in this file. Nothing reads a property and instantiates what it
 * names, which is the difference between a configuration surface and a code-execution
 * surface. An entry outside the four therefore refuses the boot and names the offender,
 * rather than resolving to nothing and leaving a run to discover that a role it declared
 * answers with no tools.
 *
 * <p>
 * <b>The four entries.</b> {@value #FILE_READ} and {@value #FILE_WRITE} are
 * {@code FileTools.readOnly} and {@code FileTools.readWrite} at the run root — the
 * platform's own factories, public from Java, so the surface a role offers is the surface
 * the platform publishes for that instance rather than a hand-kept list of method names
 * that drifts when the platform adds one. {@value #SEARCH} is {@code LocalDirectory}, the
 * platform's own directory-and-pattern search. {@value #COMMAND} is this addon's
 * {@link CommandTool}, with the same timeout and output allowance the {@code shell} group
 * carries. {@value #FILE_WRITE} includes the reads, because {@code readWrite} extends
 * {@code readOnly} — a role composed of both is a role with reads listed once.
 *
 * <p>
 * <b>The edit tool is the addon's, here too.</b> {@link CodingFileTools#SUPERSEDED_TOOLS}
 * documents that the platform's {@code editFile} replaces every occurrence it is given
 * and reports no path, so this addon offers its own under the same name. A composed role
 * honours the same rule: the platform's method is dropped and one
 * {@link TargetedEditTool} takes its place, so composing {@code file-write} cannot
 * reintroduce the contract the addon rejected.
 *
 * <p>
 * <b>These groups reach the platform through the provider channel.</b> They are published
 * as one bean of type {@code List<ToolGroup>}, which is the channel upstream's own
 * {@code includedToolGroups} uses — {@code AgentPlatformConfiguration.toolGroupResolver}
 * appends every such bean's contents after the individual {@code ToolGroup} beans. The
 * consequence worth knowing: a composed role never shadows one of the addon's own two,
 * and never shadows an MCP-backed group either, because both of those are already in the
 * list before this one is appended.
 *
 * @author Ivan Rodriguez
 */
public final class ExposedToolGroups {

	/**
	 * The platform's read-only file tools at the run root: read, list, stat, exists and
	 * the file search, with nothing that changes a file.
	 */
	public static final String FILE_READ = "file-read";

	/**
	 * The platform's read-write file tools at the root, which carry the reads too, plus
	 * this addon's targeted edit.
	 */
	public static final String FILE_WRITE = "file-write";

	/**
	 * The platform's own directory listing and content search over the root.
	 */
	public static final String SEARCH = "search";

	/**
	 * This addon's command tool: one {@code bash -lc} inside the root, with the
	 * configured timeout and output allowance.
	 */
	public static final String COMMAND = "command";

	/** The whole vocabulary an expose entry may name. */
	public static final Set<String> CAPABILITIES = Set.of(FILE_READ, FILE_WRITE, SEARCH, COMMAND);

	/** The property prefix every composed role is declared under. */
	public static final String PREFIX = "smith.tools.expose.";

	/** The description the search instance carries for the root. */
	private static final String DIRECTORY_DESCRIPTION = "The run root: the directory this deployment is confined to.";

	private ExposedToolGroups() {
	}

	/**
	 * The groups the configuration composes, one per named role.
	 * @param expose role name to the capability names it asks for, as bound from
	 * {@code smith.tools.expose.<role>}
	 * @param root the run root every composed tool is confined to
	 * @param timeout the command timeout {@value #COMMAND} carries
	 * @param maxOutputChars the output allowance {@value #COMMAND} carries
	 * @param reads where the reads a composed capability answers are noted. A composed
	 * role and the {@code file} group are two doors to the same reader, so a run that
	 * asked for one of them still gets one record per read rather than a record per door
	 * @return the composed groups, in role order; empty when nothing is configured, which
	 * is the default
	 * @throws IllegalStateException when an entry names something outside
	 * {@link #CAPABILITIES}, or a role names no capability at all. The message names the
	 * property, the entry, and the vocabulary.
	 */
	public static List<ToolGroup> groups(Map<String, List<String>> expose, RunRoot root, Duration timeout,
			int maxOutputChars, ReadPathLog reads) {
		List<ToolGroup> groups = new ArrayList<>();
		for (String role : new TreeSet<>(expose.keySet())) {
			List<String> capabilities = expose.get(role);
			groups.add(group(role, capabilities, root, timeout, maxOutputChars, reads));
		}
		return groups;
	}

	/**
	 * One composed role.
	 * @param role the role name the operator chose
	 * @param capabilities the capabilities it named
	 * @param root the run root
	 * @param timeout the command timeout
	 * @param maxOutputChars the output allowance
	 * @param reads the log answered reads are noted in
	 * @return the group answering that role
	 */
	private static ToolGroup group(String role, List<String> capabilities, RunRoot root, Duration timeout,
			int maxOutputChars, ReadPathLog reads) {
		String property = PREFIX + role;
		if (capabilities == null || capabilities.isEmpty()) {
			throw new IllegalStateException(property + " names a role but no capability, so it would register a "
					+ "group that offers nothing. Name one of " + vocabulary() + ".");
		}
		Map<String, Tool> offered = new LinkedHashMap<>();
		for (String capability : capabilities) {
			toolsFor(property, capability, root, timeout, maxOutputChars)
				.forEach((tool) -> offered.putIfAbsent(name(tool), tool));
		}
		CodingFileTools.SUPERSEDED_TOOLS.forEach(offered::remove);
		List<Tool> withEdit = new ArrayList<>(offered.values());
		if (capabilities.contains(FILE_WRITE)) {
			withEdit.add(TargetedEditTool.tool(root));
		}
		return CodingToolGroups.ofTools(role, describe(role, capabilities),
				ConfiningTool.confine(withEdit, root, reads));
	}

	/**
	 * The tools one capability contributes, built by the factory named for it here.
	 * @param property the property that named it, for the refusal message
	 * @param capability the entry as written
	 * @param root the run root
	 * @param timeout the command timeout
	 * @param maxOutputChars the output allowance
	 * @return the tools that capability offers
	 */
	private static List<Tool> toolsFor(String property, String capability, RunRoot root, Duration timeout,
			int maxOutputChars) {
		if (!CAPABILITIES.contains(capability)) {
			throw new IllegalStateException(property + " names '" + capability
					+ "', which is not one of the capabilities this addon " + "exposes " + vocabulary() + "."
					+ (looksLikeAClass(capability)
							? " It is not instantiated: this addon builds a composed role only from the names above, "
									+ "never from a class a property names."
							: "")
					+ " Remove the entry or correct the spelling.");
		}
		String path = root.path().toString();
		return switch (capability) {
			case FILE_READ -> Tool.fromInstance(FileTools.readOnly(path));
			case FILE_WRITE -> Tool.fromInstance(FileTools.readWrite(path));
			case SEARCH -> Tool.fromInstance(new LocalDirectory(path, DIRECTORY_DESCRIPTION));
			default -> List.of(CommandTool.tool(root, timeout, maxOutputChars));
		};
	}

	/**
	 * Whether an entry reads as a class name rather than a capability, which is the case
	 * the message has to answer directly.
	 * @param capability the entry as written
	 * @return {@code true} when it carries a package separator or starts upper-case
	 */
	private static boolean looksLikeAClass(String capability) {
		String trimmed = capability.trim();
		return trimmed.contains(".") || (!trimmed.isEmpty() && Character.isUpperCase(trimmed.charAt(0)));
	}

	/**
	 * The text a model reads for a composed role.
	 * @param role the role name
	 * @param capabilities what the deployment named for it
	 * @return the description
	 */
	private static String describe(String role, List<String> capabilities) {
		return "Operator-composed role '" + role + "': the capabilities this deployment named ("
				+ String.join(", ", capabilities) + "), each confined to the run root.";
	}

	/**
	 * The vocabulary, sorted, so a refusal lists it the same way every time.
	 * @return the capability names in a stable order
	 */
	private static String vocabulary() {
		return new TreeSet<>(CAPABILITIES).toString();
	}

	private static String name(Tool tool) {
		return tool.getDefinition().getName();
	}

}
