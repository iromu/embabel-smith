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

package com.embabel.smith.gencmp;

import java.util.List;

import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.GoalStepSpec;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Approach A — the studio's single-shot generation: one model call runs a prompt that emits
 * the whole agent spec (or a multi-agent process) as JSON, which is parsed straight into the
 * shared {@link AgentSpec} format. This mirrors the studio's {@code ProcessFlowGenerator}
 * without depending on the studio module.
 *
 * @author Ivan Rodriguez
 */
public class SingleShotGenerator {

	private final LlmCaller caller;

	private final String flatPrompt;

	private final String multiPrompt;

	private final ObjectMapper json = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
			false);

	public SingleShotGenerator(LlmCaller caller, String flatPrompt, String multiPrompt) {
		this.caller = caller;
		this.flatPrompt = flatPrompt;
		this.multiPrompt = multiPrompt;
	}

	/**
	 * The flat baseline: one agent, all roles as steps.
	 * @param blueprint the business-process description to generate from.
	 * @return the single-agent process.
	 */
	public ProcessSpec generateFlat(String blueprint) {
		String raw = this.caller.call(this.flatPrompt, wrap(blueprint, "Produce the agent spec JSON for the core "
				+ "end-to-end process."));
		AgentSpec agent = normalize(this.read(extractJson(raw), AgentSpec.class));
		return ProcessSpec.single(agent.name(), agent);
	}

	/**
	 * The multi-agent mode: the model decomposes the process into several agents in one call.
	 * @param blueprint the business-process description to decompose.
	 * @return the multi-agent process.
	 */
	public ProcessSpec generateMulti(String blueprint) {
		String raw = this.caller.call(this.multiPrompt, wrap(blueprint,
				"Produce the multi-agent process JSON for the core end-to-end process."));
		MultiProcessResponse response = this.read(extractJson(raw), MultiProcessResponse.class);
		List<AgentSpec> agents = response.agents().stream().map(this::normalize).toList();
		String process = (response.process() != null && !response.process().isBlank()) ? response.process()
				: slug(agents.get(0).name());
		return new ProcessSpec(process, agents);
	}

	private <T> T read(String json, Class<T> type) {
		try {
			return this.json.readValue(json, type);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to parse the model answer as " + type.getSimpleName() + ": "
					+ ex.getMessage(), ex);
		}
	}

	private static String wrap(String blueprint, String instruction) {
		return "Below is a business-process description. " + instruction + "\n\n" + blueprint;
	}

	/**
	 * Fills the format invariants the model may omit: a manifest and an exported goal.
	 * @param pack the parsed agent spec.
	 * @return the normalised agent spec.
	 */
	private AgentSpec normalize(AgentSpec pack) {
		AgentSpecManifest manifest = pack.manifest();
		if (manifest == null || manifest.name() == null || manifest.name().isBlank()) {
			String name = (pack.goal() != null && pack.goal().name() != null) ? slug(pack.goal().name())
					: "generated-agent";
			manifest = new AgentSpecManifest(name, "1.0.0");
		}
		GoalStepSpec goal = pack.goal();
		if (goal != null && !goal.isExported()) {
			goal = GoalStepSpec.exportedGoal(goal.name(), goal.description(), goal.outputTypeName());
		}
		return new AgentSpec(manifest, goal, pack.steps(), pack.types());
	}

	/**
	 * Strips markdown fences and any surrounding prose, returning the outermost JSON object.
	 * The prompt asks for JSON only, but models occasionally wrap it.
	 * @param raw the raw model answer.
	 * @return the outermost JSON object found in the answer.
	 */
	static String extractJson(String raw) {
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

	private static String slug(String name) {
		String s = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
		return s.isEmpty() ? "generated-process" : s;
	}

	/**
	 * The multi-agent response: a process name plus the ordered agents.
	 * @param process the process name.
	 * @param agents the ordered agents.
	 */
	record MultiProcessResponse(String process, List<AgentSpec> agents) {
	}

}
