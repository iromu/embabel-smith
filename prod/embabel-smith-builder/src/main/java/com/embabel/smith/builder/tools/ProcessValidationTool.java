/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.tools;

import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.smith.builder.core.AgentSpecNormalizer;
import com.embabel.smith.common.model.ProcessSpec;
import com.embabel.smith.common.validation.ProcessValidationResult;
import com.embabel.smith.common.validation.ProcessValidator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link ProcessValidator} as a tool a pack's own agent can call: the cross-agent
 * hand-off checks — goal collisions, unfed inputs, unreachable goals, cycles — returned
 * to whoever drafted the process rather than reported by a repair stage afterwards.
 *
 * <p>
 * The only thing this class adds over the service is reading the document into the
 * {@link ProcessSpec} the service consumes; the findings, the shared-type count, the
 * connectivity flag and any cycle are the service's, passed through untouched. It states
 * no verdict and gates nothing.
 *
 * <p>
 * <b>Why the argument is the process document.</b> It used to be a directory, which is
 * the shape that made {@code validatePack}'s directory argument cost a measured 116 tool
 * calls against paths a drafting step had invented ({@code docs/12-backlog.md}): during a
 * drafting step there is no process on disk to point at — the thing the step wants
 * checked is its own pending answer. A process <em>is</em> a directory on disk in this
 * product ({@code io/ProcessSpecReader} reads {@code process.yml} plus one pack directory
 * per agent), and the document form is the same aggregate before it is written: the
 * manifest and the agents, as the multi-agent generator already has them in
 * {@code core/SingleShotProcessSpecGenerator}.
 *
 * <p>
 * The tool-reach situation is the one described on {@link AgentSpecValidationTool}: a
 * declared tool group resolves only against the platform's registered groups, and the
 * {@code LlmTool} annotation registers nothing. The process checks now travel through a
 * registered group — {@link ProcessValidationToolGroup}, {@code process}.
 *
 * @author Ivan Rodriguez
 */
public class ProcessValidationTool {

	private static final ObjectMapper JSON = new ObjectMapper()
		.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	private final ProcessValidator validator = new ProcessValidator();

	/**
	 * Validates the process the drafting step is about to return, reporting exactly what
	 * the validator reported.
	 * @param processJson the process as a JSON document — its manifest and its agents, in
	 * run order; markdown fences and prose around it are tolerated, as every generator's
	 * parse tolerates them
	 * @return the validator's own result, unchanged: its findings, shared-type count,
	 * connectivity and cycle
	 */
	@LlmTool(description = "Validate a multi-agent process and report its findings: agents that export the same goal, "
			+ "step inputs no agent produces, goals unreachable from the entry, and cycles between agents. The argument "
			+ "is the process document you are about to return, not a path to a process on disk. Returns nothing when "
			+ "the process is sound.")
	public ProcessValidationResult validateProcess(String processJson) {
		return this.validator.validate(read(processJson));
	}

	/**
	 * The document as the aggregate the service consumes.
	 * @param processJson the process as JSON text
	 * @return the process
	 * @throws IllegalArgumentException when the text is not a process document, which is
	 * the same named failure the directory reader raised for a directory it could not
	 * read
	 */
	static ProcessSpec read(String processJson) {
		try {
			return JSON.readValue(AgentSpecNormalizer.extractJson(processJson), ProcessSpec.class);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Failed to read the process document: " + ex.getMessage(), ex);
		}
	}

}
