/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.tools;

import java.util.ArrayList;
import java.util.List;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.core.ToolGroup;
import com.embabel.smith.common.validation.AgentsPackValidationResult;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The {@code process} tool group: the cross-agent checks a multi-agent draft needs,
 * available to the step that wrote it.
 *
 * <p>
 * {@code tools/AgentsPackValidationTool} existed before this group existed, and that is
 * the pattern this change is about: an annotated adapter is not reach. {@code @LlmTool}
 * registers nothing in Embabel 1.5.4, so until a {@code ToolGroup} bean published the
 * role the step declares, nothing offered these checks to anyone. The adapter's argument
 * changed in the same breath — a directory a drafting step cannot name became the process
 * document it is holding — for the reason recorded on {@link AgentsPackValidationTool}.
 *
 * @author Ivan Rodriguez
 */
@Configuration
public class AgentsPackValidationToolGroup {

	/** The tool-group role a pack step declares to run the process checks. */
	public static final String ROLE = "process";

	/** The tool name, kept equal to the annotated method it wraps. */
	public static final String VALIDATE_PROCESS = "validateProcess";

	/** The argument's name: the process document the step is about to return. */
	public static final String PROCESS_JSON = "processJson";

	/**
	 * The group the platform registers under {@link #ROLE}.
	 * @param surface what the driver opened for this build; its memo answers an identical
	 * repeat of the call
	 * @return the {@code process} tool group
	 */
	@Bean
	public ToolGroup processChecks(ToolSurface surface) {
		return ToolGroups.ofTools(ROLE, "Validate the multi-agent process this step is about to return and report its "
				+ "findings: colliding goals, unfed inputs, unreachable goals, cycles. The checks are the build's own; "
				+ "what they cost is the gates' decision.", List.of(validateProcess(surface)));
	}

	private static Tool validateProcess(ToolSurface surface) {
		return Tool.Companion.of(VALIDATE_PROCESS,
				"Validate a multi-agent process and report its findings: agents exporting the same goal, step inputs "
						+ "no agent produces, goals unreachable from the entry, and cycles between agents. The argument "
						+ "is the process document you are about to return, not a path.",
				Tool.InputSchema.of(Tool.Parameter.string(PROCESS_JSON,
						"The process this step is about to return, as "
								+ "its JSON document (the manifest and its agents in run order)",
						true)),
				Tool.Metadata.DEFAULT,
				(Tool.Function) (arguments) -> Tool.Result.text(ToolOutput.cap(surface.remembered(VALIDATE_PROCESS,
						arguments, () -> findings(ToolArguments.string(arguments, PROCESS_JSON))))));
	}

	/**
	 * The validator's findings for a process document, as text.
	 * @param processJson the process as the model sent it
	 * @return one line per finding plus the connectivity and cycle the validator
	 * reported, or the reason there are none
	 */
	static String findings(String processJson) {
		if (processJson.isBlank()) {
			return VALIDATE_PROCESS + " needs the '" + PROCESS_JSON + "' argument: the process JSON this step is "
					+ "about to return.";
		}
		AgentsPackValidationResult result;
		try {
			result = new AgentsPackValidationTool().validateProcess(processJson);
		}
		catch (IllegalArgumentException ex) {
			return VALIDATE_PROCESS + " could not read that as a process document: " + ex.getMessage();
		}
		List<String> lines = new ArrayList<>(result.issues());
		if (result.issues().isEmpty()) {
			lines.add("No findings: the process is sound.");
		}
		lines.add("shared types: " + result.sharedTypes() + "; goals reachable from the entry: " + result.connected()
				+ "; cycle between agents: " + result.hasCycle());
		return String.join("\n", lines);
	}

}
