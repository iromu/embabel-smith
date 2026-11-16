/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.embabel.agent.api.common.Ai;
import com.embabel.smith.common.model.AgentSpec;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The single-shot generation technique: one model call emits the whole spec as JSON. It
 * renders the {@code process_to_flow} prompt against the model and parses the answer into
 * the shared agent-spec format, so the result is exactly what the runtime ingests and the
 * studio's editor renders. POC 24 measured this as the stronger technique for the
 * single-pack path, so it is the build's default.
 *
 * <p>
 * The model call is behind the {@link SpecPromptCaller} seam; parsing and normalisation
 * are pure and offline-testable.
 *
 * @author Ivan Rodriguez
 */
public class SingleShotAgentSpecGenerator implements AgentSpecGenerator {

	/** Classpath location of the bundled generation prompt. */
	private static final String PROMPT_RESOURCE = "/prompts/process_to_flow.jinja";

	private final SpecPromptCaller caller;

	private final String template;

	private final ObjectMapper json = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
			false);

	public SingleShotAgentSpecGenerator(Ai ai) {
		this(new AiSpecPromptCaller(ai), defaultPrompt());
	}

	public SingleShotAgentSpecGenerator(Ai ai, String template) {
		this(new AiSpecPromptCaller(ai), template);
	}

	public SingleShotAgentSpecGenerator(SpecPromptCaller caller, String template) {
		this.caller = caller;
		this.template = template;
	}

	/**
	 * The bundled {@code process_to_flow} prompt, which asks for the agent-spec JSON.
	 * @return the prompt template text.
	 */
	public static String defaultPrompt() {
		return bundledPrompt(PROMPT_RESOURCE);
	}

	/**
	 * Reads a bundled prompt from the builder's resources.
	 * @param resource the classpath location of the prompt
	 * @return the prompt template text
	 * @throws IllegalStateException when the resource is missing or unreadable
	 */
	static String bundledPrompt(String resource) {
		try (InputStream in = SingleShotAgentSpecGenerator.class.getResourceAsStream(resource)) {
			if (in == null) {
				throw new IllegalStateException("prompt resource not found: " + resource);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to load prompt " + resource + ": " + ex.getMessage(), ex);
		}
	}

	@Override
	public CompiledDraft generate(String description) {
		return generate(description, null);
	}

	@Override
	public CompiledDraft generate(String description, String model) {
		String user = "Below is a business-process description. Produce the agent spec JSON for the "
				+ "core end-to-end process.\n\n" + description;
		String raw = this.caller.call(this.template, user, model);
		AgentSpec pack = parse(raw);
		return new CompiledDraft(pack.name(), pack, List.of());
	}

	private AgentSpec parse(String raw) {
		String packed = AgentSpecNormalizer.extractJson(raw);
		AgentSpec pack;
		try {
			pack = this.json.readValue(packed, AgentSpec.class);
		}
		catch (Exception ex) {
			throw new IllegalStateException("The model did not return a valid agent spec JSON: " + ex.getMessage(), ex);
		}
		return AgentSpecNormalizer.normalize(pack);
	}

}
