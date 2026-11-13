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

}
