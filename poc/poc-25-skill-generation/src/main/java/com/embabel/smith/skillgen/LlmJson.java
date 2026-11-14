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
 * The JSON seam for the LLM steps. The model is asked to emit JSON but is non-deterministic and
 * occasionally wraps it in fences or emits a malformed object; {@link #callAndParse} retries the
 * call a few times before giving up, so a single flaky response does not abort an arm.
 *
 * @author Ivan Rodriguez
 */
public final class LlmJson {

	private LlmJson() {
	}

	/**
	 * Calls the model and parses its answer, retrying up to {@code attempts} times when the
	 * answer cannot be parsed.
	 * @param <T> the parsed type.
	 * @param caller the model-call seam.
	 * @param system the system prompt.
	 * @param user the user prompt.
	 * @param attempts the number of attempts before giving up.
	 * @param label a label for the failure message.
	 * @param parser parses the extracted JSON object into the result.
	 * @return the parsed result.
	 */
	public static <T> T callAndParse(ChatCaller caller, String system, String user, int attempts, String label,
			ThrowingJsonParser<T> parser) {
		Exception last = null;
		for (int attempt = 0; attempt < attempts; attempt++) {
			try {
				String raw = caller.callJson(system, user);
				return parser.parse(extractJson(raw));
			}
			catch (Exception ex) {
				last = ex;
				if (attempt < attempts - 1) {
					backoff(attempt);
				}
			}
		}
		throw new IllegalStateException("Failed to obtain " + label + " after " + attempts + " attempts: "
				+ ((last != null) ? last.getMessage() : "unknown"), last);
	}

	/**
	 * Rides out transient endpoint blips — a 500 from the upstream, a dropped connection — with a
	 * short linear backoff between attempts, so one brief hiccup does not abort a whole run.
	 * @param attempt the zero-based attempt that just failed.
	 */
	private static void backoff(int attempt) {
		try {
			Thread.sleep(2000L * (attempt + 1));
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Strips markdown fences and any surrounding prose, returning the outermost JSON object.
	 * The prompt asks for JSON only, but models occasionally wrap it.
	 * @param raw the raw model answer.
	 * @return the outermost JSON object found in the answer.
	 */
	public static String extractJson(String raw) {
		String s = (raw != null) ? raw.trim() : "";
		int fence = s.indexOf("```");
		if (fence >= 0) {
			String after = s.substring(fence + 3);
			int nl = after.indexOf('\n');
			if (nl >= 0) {
				after = after.substring(nl + 1);
			}
			int close = after.lastIndexOf("```");
			if (close >= 0) {
				after = after.substring(0, close);
			}
			s = after.trim();
		}
		int start = s.indexOf('{');
		int end = s.lastIndexOf('}');
		if (start < 0 || end < 0 || end < start) {
			throw new IllegalStateException("No JSON object found in the model answer");
		}
		return s.substring(start, end + 1);
	}

	/**
	 * A JSON parser that may throw (e.g. Jackson's checked exceptions).
	 * @param <T> the parsed type.
	 */
	@FunctionalInterface
	public interface ThrowingJsonParser<T> {

		T parse(String json) throws Exception;

	}

}
