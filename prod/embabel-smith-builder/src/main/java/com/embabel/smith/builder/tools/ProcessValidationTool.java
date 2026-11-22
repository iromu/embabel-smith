/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.tools;

import java.io.IOException;
import java.nio.file.Path;

import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.smith.common.io.ProcessSpecReader;
import com.embabel.smith.common.model.ProcessSpec;
import com.embabel.smith.common.validation.ProcessValidationResult;
import com.embabel.smith.common.validation.ProcessValidator;

/**
 * {@link ProcessValidator} as a tool a pack's own agent can call: the cross-agent
 * hand-off checks — goal collisions, unfed inputs, unreachable goals, cycles — returned
 * to whoever drafted the process rather than reported by a repair stage afterwards.
 *
 * <p>
 * The only thing this class adds over the service is the reading of the directory into
 * the {@link ProcessSpec} the service consumes; the findings, the shared-type count, the
 * connectivity flag and any cycle are the service's, passed through untouched. It states
 * no verdict and gates nothing.
 *
 * <p>
 * The tool-reach situation is the one described on {@link AgentSpecValidationTool}: a
 * declared tool group resolves only against the platform's registered groups, and the
 * {@code LlmTool} annotation registers nothing. The spec validator now travels through a
 * registered group — {@link ValidationToolGroup}, {@code validate}. No group is
 * registered for these process checks, and the deploys no longer carry a tool list
 * either, so nothing currently offers them to a step.
 *
 * @author Ivan Rodriguez
 */
public class ProcessValidationTool {

	private final ProcessValidator validator = new ProcessValidator();

	private final ProcessSpecReader reader = new ProcessSpecReader();

	/**
	 * Validates the process written at a directory, reporting exactly what the validator
	 * reported.
	 * @param processDirectory the directory holding the process to validate
	 * @return the validator's own result, unchanged: its findings, shared-type count,
	 * connectivity and cycle
	 * @throws IllegalStateException when the process cannot be read from disk
	 */
	@LlmTool(description = "Validate a multi-agent process in a directory and report its findings: "
			+ "agents that export the same goal, step inputs no agent produces, goals unreachable "
			+ "from the entry, and cycles between agents. Returns nothing when the process is sound.")
	public ProcessValidationResult validateProcess(String processDirectory) {
		ProcessSpec process;
		try {
			process = this.reader.read(Path.of(processDirectory));
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to read process for validation: " + ex.getMessage(), ex);
		}
		return this.validator.validate(process);
	}

}
