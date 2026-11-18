/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

import java.util.List;

import com.embabel.agent.api.common.Ai;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.ProcessSpec;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The multi-agent candidate: one model call with the bundled
 * {@code process_to_flow_multi} prompt decomposes the description into a whole process —
 * an ordered list of agent specs that hand off through shared types — and the answer is
 * parsed into the same shared format the single-agent candidate uses. POC 24 measured
 * this as the only multi-agent technique worth running (its Embabel-compiler counterpart
 * produced a disconnected process every time), which is why the process candidate is
 * generated single-shot whatever technique the single-agent candidate used.
 *
 * <p>
 * As with {@link SingleShotAgentSpecGenerator}, the model call sits behind the
 * {@link SpecPromptCaller} seam and the parsing is pure, so a canned answer exercises the
 * whole step offline.
 *
 * @author Ivan Rodriguez
 */
public class SingleShotProcessSpecGenerator implements ProcessSpecGenerator {

	/** Classpath location of the bundled multi-agent generation prompt. */
	private static final String PROMPT_RESOURCE = "/prompts/process_to_flow_multi.jinja";

	private final SpecPromptCaller caller;

	private final String template;

	private final ObjectMapper json = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
			false);

	public SingleShotProcessSpecGenerator(Ai ai) {
		this(new AiSpecPromptCaller(ai), defaultPrompt());
	}

	public SingleShotProcessSpecGenerator(Ai ai, String template) {
		this(new AiSpecPromptCaller(ai), template);
	}

	public SingleShotProcessSpecGenerator(SpecPromptCaller caller, String template) {
		this.caller = caller;
		this.template = template;
	}

	/**
	 * The bundled {@code process_to_flow_multi} prompt, which asks for the process JSON.
	 * @return the prompt template text
	 */
	public static String defaultPrompt() {
		return SingleShotAgentSpecGenerator.bundledPrompt(PROMPT_RESOURCE);
	}

	@Override
	public ProcessDraft generateProcess(String description) {
		return generateProcess(description, null);
	}

	@Override
	public ProcessDraft generateProcess(String description, String model) {
		String user = "Below is a business-process description. Produce the multi-agent process JSON for "
				+ "the core end-to-end process.\n\n" + PromptData.block("document", description);
		String raw = this.caller.call(this.template, user, model);
		MultiProcessResponse response = parse(raw);

		List<AgentSpec> agents = response.agents().stream().map(AgentSpecNormalizer::normalize).toList();
		if (agents.isEmpty()) {
			throw new IllegalStateException("The model decomposed the description into no agents");
		}
		if (agents.size() < 2) {
			// One pack is the other candidate, not a second shape of this one — shipping
			// it
			// as a "multi-agent process" would misreport the build.
			throw new IllegalStateException(
					"The model decomposed the description into a single agent, which is not a multi-agent process");
		}
		String name = (response.process() != null && !response.process().isBlank()) ? response.process()
				: AgentSpecNormalizer.slug(agents.get(0).name());
		return new ProcessDraft(name, new ProcessSpec(name, agents), List.of());
	}

	private MultiProcessResponse parse(String raw) {
		String packed = AgentSpecNormalizer.extractJson(raw);
		try {
			MultiProcessResponse response = this.json.readValue(packed, MultiProcessResponse.class);
			return (response != null) ? response : new MultiProcessResponse(null, List.of());
		}
		catch (Exception ex) {
			throw new IllegalStateException("The model did not return a valid process JSON: " + ex.getMessage(), ex);
		}
	}

	/**
	 * The shape of the model's answer: a process name and its agents in run order.
	 *
	 * @param process the process name the model chose
	 * @param agents the agents, in the order the process runs
	 */
	record MultiProcessResponse(String process, List<AgentSpec> agents) {

		MultiProcessResponse {
			agents = (agents != null) ? List.copyOf(agents) : List.of();
		}
	}

}
