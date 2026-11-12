/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.studio.generate;

/**
 * The model-based process extraction: a corpus of business-process documents in, a
 * business-process description (markdown) out. It is a second prompt run against the same
 * {@link FlowPromptCaller} seam as generation, but with the
 * {@code business_process_discovery} prompt and a plain-text answer (no JSON pack to
 * parse). The extracted description is the blueprint that generation consumes.
 *
 * @author Ivan Rodriguez
 */
public class ProcessExtractor {

	private final FlowPromptCaller caller;

	private final String template;

	public ProcessExtractor(FlowPromptCaller caller, String template) {
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
		String user = "Below are the business-process documents for an application. Reconstruct the "
				+ "business processes and workflows they describe.\n\n" + corpus;
		return this.caller.call(this.template, user);
	}

}
