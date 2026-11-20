/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.scripts;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.embabel.smith.builder.core.SpecPromptCaller;
import com.embabel.smith.builder.skill.SkillPrompts;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.skillscripts.GraalJsSkillScriptExecutionEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Test helpers for the generated-script section: the step a skill is generated from, an
 * engine every check shares, and a caller that answers the two model calls so the whole
 * generation and run happens with no endpoint.
 *
 * <p>
 * The canned answers are keyed on the system prompt rather than on call order, which is
 * what lets a test assert that the skill half was asked with the product's own
 * {@link SkillPrompts#generateSkill()} and the script half with this section's. A caller
 * that answered by order would keep answering even if the generator stopped using the
 * product's prompt, which is the claim task 6.1 rests on.
 *
 * @author Ivan Rodriguez
 */
final class ScriptFixtures {

	static final ObjectMapper JSON = new ObjectMapper();

	/**
	 * One engine for the whole section, on POC 26's defaults. It builds a fresh hardened
	 * context per run, so sharing it is free and every script faces the limits a
	 * generated script will face.
	 */
	static final GraalJsSkillScriptExecutionEngine ENGINE = new GraalJsSkillScriptExecutionEngine();

	/**
	 * The step the skills are generated from: the builder's own evaluate stage, chosen so
	 * the generated script has arithmetic to do rather than prose to produce. Its
	 * declared text is the only place these names appear, so a test that finds them in a
	 * prompt knows the step reached the model.
	 */
	static final ActionStepSpec STEP = ActionStepSpec.action("score-draft", "Score a draft against the frozen tasks",
			List.of("DraftedSpec"), "ScoredDraft",
			"Compare {{ draftedSpec }} against every frozen task and report the pass rate.");

	/** The agent that owns {@link #STEP}. */
	static final AgentSpec AGENT = new AgentSpec(new AgentSpecManifest("evaluate-draft", "1.0.0"),
			GoalStepSpec.exportedGoal("draft_scored", "Score a draft against the frozen tasks", "ScoredDraft"),
			List.of(STEP), List.of());

	private ScriptFixtures() {
	}

	/**
	 * A generated skill whose script is the source given, written under {@code workDir}.
	 * @param workDir where to write the skill folder
	 * @param scriptSource the JavaScript the model "answered" with
	 * @return the skill, on disk.
	 */
	static StepSkillGenerator.Generated generated(Path workDir, String scriptSource) {
		Canned caller = new Canned().answer(SkillPrompts.generateSkill(),
				skillAnswer("Scores a draft against the frozen task set and reports its pass rate.",
						"Read the draft, compare it with each frozen task, and report the pass rate."));
		caller.answer(StepSkillGenerator.SCRIPT_PROMPT, scriptAnswer(scriptSource));
		return new StepSkillGenerator(caller).generate(AGENT, STEP, workDir);
	}

	/**
	 * A caller that answers each system prompt once, recording every call it received.
	 */
	static final class Canned implements SpecPromptCaller {

		private final Map<String, String> answers = new HashMap<>();

		private final List<String> systems = new ArrayList<>();

		private final List<String> users = new ArrayList<>();

		/**
		 * @param system the system prompt to answer
		 * @param answer the answer to give it
		 * @return this caller, for chaining.
		 */
		Canned answer(String system, String answer) {
			this.answers.put(system, answer);
			return this;
		}

		@Override
		public String call(String system, String user) {
			this.systems.add(system);
			this.users.add(user);
			String answer = this.answers.get(system);
			if (answer == null) {
				throw new IllegalStateException("No canned answer for a prompt starting '"
						+ system.substring(0, Math.min(60, system.length())) + "'");
			}
			return answer;
		}

		/**
		 * The system prompts that were sent, in call order.
		 * @return the prompts.
		 */
		List<String> systems() {
			return List.copyOf(this.systems);
		}

		/**
		 * The user messages that were sent, in call order.
		 * @return the messages.
		 */
		List<String> users() {
			return List.copyOf(this.users);
		}

	}

	/**
	 * @param description the skill description the model answered with
	 * @param body the skill body the model answered with
	 * @return the answer in the shape {@code SkillGenerator} parses.
	 */
	static String skillAnswer(String description, String body) {
		ObjectNode node = JSON.createObjectNode();
		node.put("description", description);
		node.put("body", body);
		return node.toString();
	}

	/**
	 * @param source the JavaScript the model answered with
	 * @return the answer in the shape {@link StepSkillGenerator} parses.
	 */
	static String scriptAnswer(String source) {
		ObjectNode node = JSON.createObjectNode();
		node.put("script", source);
		return node.toString();
	}

}
