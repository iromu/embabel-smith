/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.tools;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.core.ToolGroup;
import com.embabel.agent.core.ToolGroupDescription;
import com.embabel.agent.core.ToolGroupMetadata;
import com.embabel.agent.core.ToolGroupPermission;

/**
 * The one place this module turns a role and a tool list into a {@link ToolGroup}, so a
 * step's {@code toolGroups} entry has something to resolve against.
 *
 * <p>
 * <b>Why a group bean is the route at all.</b> A step's {@code toolGroups} entry becomes
 * a {@code ToolGroupRequirement} that the platform resolves by role against its own
 * registry at prompt time, and that registry is the {@link ToolGroup} beans in the
 * context which booted the platform. The deploy's {@code tools} channel is the other way
 * in, but {@code PromptedActionSpec.emit} selects from it by the step's {@code tools:}
 * name list, which {@code ActionStepSpec} cannot express — so a tool wired there is
 * unselectable by a pack and reaches no model. {@code eval.AgentSpecInvokerFactory}
 * passes an empty list for exactly that reason.
 *
 * <p>
 * <b>Why the metadata is built reflectively.</b> {@link ToolGroupMetadata}'s factories
 * are Kotlin companion functions whose JVM names contain a hyphen
 * ({@code invoke-bYD-3ao}), so {@code javac} cannot call them by name, and
 * {@code MinimalToolGroupMetadata} is package-private. The companion is therefore
 * searched by <em>signature</em> — the
 * {@code (ToolGroupDescription, String, String, Set, String)} overload — through
 * {@code getMethods()}. An Embabel version that changes that signature fails here with a
 * named {@link IllegalStateException} rather than silently shipping a pack that cannot
 * reach anything. The same recipe is this repository's frozen POC 7 evidence
 * ({@code poc/poc-7-runtime-adapter/.../ShellToolGroupFactory.java}), which is the
 * configuration proven to resolve at runtime; {@code poc/} is read for the pattern and
 * never depended on.
 *
 * <p>
 * <b>The permission set.</b> {@code HOST_ACCESS}: every group made here opens nothing —
 * no directory the model named, no socket, no process — but {@code HOST_ACCESS} and
 * {@code INTERNET_ACCESS} are the only two permissions Embabel 1.5.4 offers, so a group
 * has to name one and the narrower answer to "this tool reaches nothing" does not exist.
 * None of these groups carries {@code INTERNET_ACCESS}, and resolution is by role rather
 * than by permission, so the set states what the tool does rather than gating whether it
 * is offered.
 *
 * @author Ivan Rodriguez
 */
public final class ToolGroups {

	// A group-publishing @Bean method in one of these classes is named for what it
	// publishes
	// (`documentTools`, `traceTools`), not after its own class: Spring's default bean
	// name for
	// `@Configuration class BlueprintToolGroup` is already `blueprintToolGroup`, and an
	// @Bean
	// method of the same name is a BeanDefinitionOverrideException at context start-up.

	/** The vendor recorded in every builder group's asset coordinates. */
	private static final String VENDOR = "embabel-smith";

	/** The version recorded in every builder group's asset coordinates. */
	private static final String VERSION = "1.0.0";

	private ToolGroups() {
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
