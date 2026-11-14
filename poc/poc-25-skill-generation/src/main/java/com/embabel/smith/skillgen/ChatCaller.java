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

package com.embabel.smith.skillgen;

/**
 * The model-call seam for POC 25: a system prompt plus a user prompt in, the raw completion
 * text out. Two implementations run side by side — a raw OpenAI-compatible call (the
 * lightweight arm) and the Embabel {@code Ai} runtime (the runtime arm) — so the same with/
 * without-skill comparison is measured over both transports.
 *
 * @author Ivan Rodriguez
 */
@FunctionalInterface
public interface ChatCaller {

	String call(String system, String user);

	/**
	 * A call whose completion is expected to be a single JSON object. The raw transport requests
	 * constrained JSON decoding so the answer is syntactically valid — which is what the
	 * decomposer, skill generator, eval-drafter and grader parse; other transports fall back to
	 * {@link #call}. Every JSON-parsing step reaches this through {@link LlmJson#callAndParse}.
	 * @param system the system prompt.
	 * @param user the user prompt.
	 * @return the raw completion text, expected to be a JSON object.
	 */
	default String callJson(String system, String user) {
		return call(system, user);
	}

}
