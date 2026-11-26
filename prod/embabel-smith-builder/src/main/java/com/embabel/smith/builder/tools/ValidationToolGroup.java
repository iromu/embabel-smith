/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.tools;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import com.embabel.agent.core.ToolGroup;
import com.embabel.agent.core.ToolGroupDescription;
import com.embabel.agent.core.ToolGroupMetadata;
import com.embabel.agent.core.ToolGroupPermission;
import com.embabel.smith.builder.eval.BuilderPackTools;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The {@code validate} tool group as a platform bean, so a builder pack step that
 * declares {@code toolGroups: [validate]} resolves to the validator instead of to
 * nothing.
 *
 * <p>
 * <b>Why a group and not the deploy's tool list.</b> A step's {@code toolGroups} entry
 * becomes a {@code ToolGroupRequirement} that {@code PromptRunner.withToolGroups}
 * resolves by role against the platform's registry, and that registry is the
 * {@link ToolGroup} beans in the context which booted the platform
 * ({@code AgentPlatformConfiguration.toolGroupResolver}). The deploy's {@code tools}
 * channel is the other way in, but {@code PromptedActionSpec.emit} selects from it by the
 * step's own {@code tools:} name list, which {@code ActionStepSpec} cannot express — so a
 * tool wired there is unselectable by a pack and reaches no model. That is why
 * {@code eval.AgentSpecInvokerFactory} no longer wires one and why this bean is what
 * makes the existing {@code toolGroups: [validate]} line in
 * {@code builder-spec-one-shot/steps/draftAgentSpec.yml} true.
 *
 * <p>
 * <b>Why the metadata is built reflectively.</b> {@link ToolGroupMetadata}'s factories
 * are Kotlin companion functions whose JVM names contain a hyphen
 * ({@code invoke-bYD-3ao}), so {@code javac} cannot call them by name, and
 * {@code MinimalToolGroupMetadata} is package-private. The companion is therefore
 * searched by <em>signature</em> — the
 * {@code (ToolGroupDescription, String, String, Set, String)} overload — through
 * {@code getMethods()}, and an Embabel version that changes that signature fails here
 * with a named {@link IllegalStateException} rather than silently shipping a pack that
 * cannot reach its validator. The same recipe is in this repository's frozen POC 7
 * evidence ({@code poc/poc-7-runtime-adapter/.../ShellToolGroupFactory.java}), which is
 * the configuration proven to resolve at runtime.
 *
 * <p>
 * <b>The permission set.</b> {@code HOST_ACCESS}: the validator is handed the spec text
 * and opens nothing — no directory, no socket — but {@code HOST_ACCESS} and
 * {@code INTERNET_ACCESS} are the only two permissions the platform offers in 1.5.4, so a
 * group has to name one and the narrower answer to "this tool reaches nothing" does not
 * exist. The group carries no {@code INTERNET_ACCESS}, and resolution is by role, so the
 * set states what the tool does rather than gating whether it is offered.
 *
 * @author Ivan Rodriguez
 */
@Configuration
public class ValidationToolGroup {

	/** The tool-group role a pack step declares to reach the validator. */
	public static final String ROLE = "validate";

	/** The vendor recorded in the group's asset coordinates. */
	private static final String VENDOR = "embabel-smith";

	/** The version recorded in the group's asset coordinates. */
	private static final String VERSION = "1.0.0";

	/**
	 * The group the platform registers under {@link #ROLE}, holding exactly the tool
	 * {@code eval.BuilderPackTools.validationTool()} authors.
	 * @param surface what the driver opened for this build; of it this tool uses only the
	 * memo, which answers an identical repeat of the same call from the first one
	 * @return the {@code validate} tool group
	 */
	@Bean
	public ToolGroup validateToolGroup(ToolSurface surface) {
		ToolGroupDescription description = ToolGroupDescription
			.create("Validates the agent spec a drafting step is about to return with the builder's own "
					+ "validator and reports its findings, so the step can re-draft against them.", ROLE);
		return ToolGroup.Companion.ofTools(metadata(description), List.of(BuilderPackTools.validationTool(surface)));
	}

	/**
	 * The group's metadata, built by calling the Kotlin companion factory this JVM cannot
	 * name.
	 * @param description the role and the text a model reads for the group
	 * @return metadata usable by {@code ToolGroup.Companion.ofTools}
	 * @throws IllegalStateException when the installed Embabel exposes no factory of that
	 * signature
	 */
	private static ToolGroupMetadata metadata(ToolGroupDescription description) {
		Object companion = ToolGroupMetadata.Companion;
		for (Method method : companion.getClass().getMethods()) {
			// The (ToolGroupDescription, role, vendor, permissions, version) overload.
			if (method.getParameterCount() == 5 && method.getParameterTypes()[0] == ToolGroupDescription.class
					&& method.getParameterTypes()[4] == String.class) {
				try {
					return (ToolGroupMetadata) method.invoke(companion, description, ROLE, VENDOR,
							Set.of(ToolGroupPermission.HOST_ACCESS), VERSION);
				}
				catch (ReflectiveOperationException ex) {
					throw new IllegalStateException("Failed to build the '" + ROLE + "' ToolGroupMetadata", ex);
				}
			}
		}
		throw new IllegalStateException(
				"No 5-arg ToolGroupMetadata(ToolGroupDescription, ...) factory found; " + "Embabel version mismatch?");
	}

}
