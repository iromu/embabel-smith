/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.generate;

import com.embabel.smith.builder.core.SpecPromptCaller;

/**
 * The model-based process extraction: a corpus of business-process documents in, a
 * business-process description (markdown) out. It is a second prompt run against the same
 * {@link SpecPromptCaller} seam as generation, but with the
 * {@code business_process_discovery} prompt and a plain-text answer (no JSON pack to
 * parse). The extracted description is the blueprint that generation consumes.
 *
 * @author Ivan Rodriguez
 */
public class ProcessExtractor {

	private final SpecPromptCaller caller;

	private final String template;

	public ProcessExtractor(SpecPromptCaller caller, String template) {
		this.caller = caller;
		this.template = template;
	}

	/**
	 * Runs the discovery prompt over the docs corpus, returning the business-process
	 * description.
	 * @param corpus the business-process documents to extract from.
	 * @return the business-process description produced by the model.
	 */
	public String extract(String corpus) {
		return extract(corpus, null);
	}

	/**
	 * As {@link #extract(String)}, running the discovery prompt on one named LLM.
	 * @param corpus the business-process documents to extract from.
	 * @param model the LLM to extract with, or empty for the configured default.
	 * @return the business-process description produced by the model.
	 */
	public String extract(String corpus, String model) {
		String user = "Below are the business-process documents for an application. Reconstruct the "
				+ "business processes and workflows they describe.\n\n" + corpus;
		return this.caller.call(this.template, user, model);
	}

}
