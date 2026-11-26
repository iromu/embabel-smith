/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

/**
 * The seam for the build's multi-agent generation step: a business-process description
 * in, a draft {@link com.embabel.smith.common.model.AgentsPack} out — the build's second
 * candidate beside the single-agent one produced through {@link AgentSpecGenerator}.
 *
 * <p>
 * The production implementation calls the model (endpoint-gated); offline tests inject a
 * fake returning a canned process, so the dual-candidate pipeline is verifiable without a
 * live model.
 *
 * @author Ivan Rodriguez
 */
public interface AgentsPackGenerator {

	/**
	 * Generates an agents-pack from a description.
	 * @param description the natural-language description to decompose
	 * @return the draft pack, its name, and any issues the generator surfaced for review
	 */
	AgentsPackDraft generateAgentsPack(String description);

	/**
	 * Generates an agents-pack from a description, asking for one particular LLM. The
	 * default ignores the request, so an offline fake that answers for the configured
	 * model keeps working; the production generator honours it.
	 * @param description the natural-language description to decompose
	 * @param model the LLM to decompose with, or empty for the configured default
	 * @return the draft process, its name, and any issues the generator surfaced for
	 * review
	 */
	default AgentsPackDraft generateAgentsPack(String description, String model) {
		return generateAgentsPack(description);
	}

}
