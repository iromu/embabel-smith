/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

/**
 * The seam for the build's generation step: a natural-language description in, a draft
 * {@link com.embabel.smith.common.model.AgentSpec} out. One implementation per
 * {@link GenerationTechnique}, so the techniques are interchangeable and the pipeline
 * downstream of generation never sees a technique-specific type — the Embabel compiler's
 * own result type is confined to {@link EmbabelAgentSpecGenerator}.
 *
 * <p>
 * The production implementations call the model (endpoint-gated); offline tests inject a
 * fake that returns a hand-built draft, so the whole pipeline is verifiable without a
 * live model.
 *
 * @author Ivan Rodriguez
 */
public interface AgentSpecGenerator {

	/**
	 * Generates a draft spec from a description.
	 * @param description the natural-language description to generate from.
	 * @return the draft, its name, and any issues the generator surfaced for review.
	 */
	CompiledDraft generate(String description);

	/**
	 * Generates a draft spec from a description, asking for one particular LLM. The
	 * default ignores the request, so an offline fake that answers for the configured
	 * model keeps working; the production generators honour it.
	 * @param description the natural-language description to generate from.
	 * @param model the LLM to generate with, or empty for the configured default.
	 * @return the draft, its name, and any issues the generator surfaced for review.
	 */
	default CompiledDraft generate(String description, String model) {
		return generate(description);
	}

}
