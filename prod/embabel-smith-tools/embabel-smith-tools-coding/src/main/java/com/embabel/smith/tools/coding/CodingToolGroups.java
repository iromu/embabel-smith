/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.core.ToolGroup;
import com.embabel.agent.core.ToolGroupDescription;
import com.embabel.agent.core.ToolGroupMetadata;
import com.embabel.agent.core.ToolGroupPermission;

/**
 * The two groups this addon publishes, and the one place it turns a role and a tool list
 * into a {@link ToolGroup}.
 *
 * <p>
 * <b>Why a group bean is the route at all.</b> A step's {@code toolGroups} entry becomes
 * a {@code ToolGroupRequirement} that the platform resolves by role against the
 * {@link ToolGroup} beans in the context that booted it. The deploy's {@code tools}
 * channel is the other way in, but a step selects from that list by a {@code tools:} name
 * an {@code ActionStepSpec} cannot express, so a tool wired there reaches no model. A
 * group bean is the only route a pack can declare against.
 *
 * <p>
 * <b>Why the metadata is built reflectively.</b> {@link ToolGroupMetadata}'s factories
 * are Kotlin companion functions whose JVM names contain a hyphen
 * ({@code invoke-bYD-3ao}), so {@code javac} cannot write the name, and
 * {@code MinimalToolGroupMetadata} is package-private. The companion is therefore
 * searched by <em>signature</em> — the
 * {@code (ToolGroupDescription, String, String, Set, String)} overload — through
 * {@code getMethods()}. An Embabel version that changes that signature fails here with a
 * named {@link IllegalStateException} rather than shipping a group that resolves to
 * nothing. The recipe is the one
 * {@code prod/embabel-smith-builder/.../tools/ToolGroups.java} documents, duplicated
 * rather than imported because {@code smith-common} stays framework-free and this module
 * depends on none of this repo's modules at all. The same shape is this repository's
 * frozen POC 7 evidence
 * ({@code poc/poc-7-runtime-adapter/.../ShellToolGroupFactory.java}), read for the
 * pattern and never depended on.
 *
 * <p>
 * <b>The permission set.</b> {@code HOST_ACCESS}: the file group reads and writes the
 * host's filesystem and the command group starts a process, which is what that permission
 * states. It is descriptive — {@code ToolGroupPermission} has two values and enforces
 * nothing — so the real bounds are the run root, the command timeout and the output
 * allowance, plus the trust-tier gate on the declaration.
 *
 * @author Ivan Rodriguez
 */
public final class CodingToolGroups {

	/**
	 * The role a step declares to reach the file tools. It is a name the runtime's own
	 * trust vocabulary already carries, which is why this group registers by default.
	 */
	public static final String FILE_ROLE = "file";

	/**
	 * The role a step declares to reach the command tool. Off until asked: the command
	 * role sits in the tier vocabulary's highest band, not the default one.
	 */
	public static final String SHELL_ROLE = "shell";

	/** The roles this addon knows how to register, which is what a switch can name. */
	public static final Set<String> ROLES = Set.of(FILE_ROLE, SHELL_ROLE);

	private static final String FILE_DESCRIPTION = "Read, list, search and edit files inside the run's working "
			+ "root. A path that resolves outside that root is refused, naming the path and the root.";

	private static final String SHELL_DESCRIPTION = "Run a shell command inside the run's working root, with its "
			+ "exit code, captured output and a wall-clock limit.";

	/** The vendor recorded in both groups' asset coordinates. */
	private static final String VENDOR = "embabel-smith";

	/** The version recorded in both groups' asset coordinates. */
	private static final String VERSION = "1.0.0";

	private CodingToolGroups() {
	}

	/**
	 * The group the platform registers under {@link #FILE_ROLE}.
	 * @param fileTools the platform's file tools, already standing at the run root
	 * @return the {@code file} group
	 */
	public static ToolGroup fileGroup(CodingFileTools fileTools) {
		return ofTools(FILE_ROLE, FILE_DESCRIPTION, fileTools.tools());
	}

	/**
	 * The group the platform registers under {@link #SHELL_ROLE}, carrying exactly the
	 * command tool — a step that declares only {@code shell} is offered no file tool.
	 * @param root the run root the command runs inside, and the boundary a named working
	 * directory must stay within
	 * @param timeout the command's wall-clock limit
	 * @param maxOutputChars how much of each stream the result keeps
	 * @return the {@code shell} group
	 */
	public static ToolGroup shellGroup(RunRoot root, Duration timeout, int maxOutputChars) {
		return ofTools(SHELL_ROLE, SHELL_DESCRIPTION,
				ConfiningTool.confine(List.of(CommandTool.tool(root, timeout, maxOutputChars)), root));
	}

	/**
	 * The group the platform registers under {@code role}, carrying exactly
	 * {@code tools}.
	 * @param role the name a step's {@code toolGroups} entry resolves against
	 * @param description the text a model reads for the group
	 * @param tools the tools the group offers — the whole of what a declaring step can
	 * call
	 * @return the group to publish as a bean
	 * @throws IllegalStateException when the installed Embabel exposes no metadata
	 * factory of the expected signature
	 */
	public static ToolGroup ofTools(String role, String description, List<Tool> tools) {
		return ToolGroup.Companion.ofTools(metadata(ToolGroupDescription.create(description, role), role), tools);
	}

	/**
	 * The group's metadata, built by calling the Kotlin companion factory this JVM cannot
	 * name.
	 * @param description the role and the text a model reads for the group
	 * @param role the group's role, which is also its identity in the registry
	 * @return metadata usable by {@code ToolGroup.Companion.ofTools}
	 * @throws IllegalStateException when the installed Embabel exposes no factory of that
	 * signature
	 */
	private static ToolGroupMetadata metadata(ToolGroupDescription description, String role) {
		Object companion = ToolGroupMetadata.Companion;
		for (Method method : companion.getClass().getMethods()) {
			// The (ToolGroupDescription, role, vendor, permissions, version) overload.
			if (method.getParameterCount() == 5 && method.getParameterTypes()[0] == ToolGroupDescription.class
					&& method.getParameterTypes()[4] == String.class) {
				try {
					return (ToolGroupMetadata) method.invoke(companion, description, role, VENDOR,
							Set.of(ToolGroupPermission.HOST_ACCESS), VERSION);
				}
				catch (ReflectiveOperationException ex) {
					throw new IllegalStateException("Failed to build the '" + role + "' ToolGroupMetadata", ex);
				}
			}
		}
		throw new IllegalStateException(
				"No 5-arg ToolGroupMetadata(ToolGroupDescription, ...) factory found; " + "Embabel version mismatch?");
	}

}
