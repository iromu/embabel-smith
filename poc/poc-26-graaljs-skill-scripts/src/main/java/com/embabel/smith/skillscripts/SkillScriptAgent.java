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

package com.embabel.smith.skillscripts;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.skills.Skills;

/**
 * The end-to-end case: an LLM agent that answers an invoicing question by calling the
 * skill's JavaScript, which the {@link GraalJsSkillScriptExecutionEngine} runs inside this
 * JVM.
 *
 * <p>
 * The arithmetic is deliberately not in the prompt. {@link Skills} is an
 * {@link com.embabel.agent.api.reference.LlmReference}, so attaching it gives the model the
 * skill's tools — including the {@code compute_totals} tool that only exists once a script
 * engine is installed — and the numbers come back from the script. The model cannot invent
 * them and does not need to be good at floating point.
 *
 * <p>
 * Requires a live model endpoint; the offline proof of the same behaviour, without paying an
 * LLM, is {@code SkillScriptRuntimeTest}, which calls the same tool through
 * {@link SkillScriptRuntime#callTool}.
 *
 * @author Ivan Rodriguez
 */
@Agent(description = "Answers invoice questions by running the invoice-totalling skill's JavaScript")
public class SkillScriptAgent {

	/**
	 * Prompt that keeps the model on the script instead of doing the arithmetic itself.
	 */
	static final String SYSTEM_PROMPT = """
			You are an invoicing assistant. You must not compute any figure yourself.
			Activate the invoice-totalling skill and call its compute_totals script with the
			invoice as JSON on stdin and any --discount or --approval flags the customer asked
			for, then report exactly the figures the script returned. If the script rejects the
			invoice, report its rejection reason verbatim and do not retry with changed numbers.
			""";

	private final Skills skills;

	public SkillScriptAgent(Skills skills) {
		this.skills = skills;
	}

	/**
	 * Ask the model, with the skill (and therefore its script) available as tools.
	 * @param request the customer's request, containing the invoice
	 * @param context the operation context supplying the LLM
	 * @return the model's report of the script's output
	 */
	@AchievesGoal(description = "The invoice figures have been computed by the skill's script")
	@Action(description = "Compute invoice figures by calling the skill's JavaScript")
	public ScriptedAnswer totalInvoice(UserInput request, OperationContext context) {
		String answer = context.ai()
			.withDefaultLlm()
			.withReference(this.skills)
			.withSystemPrompt(SYSTEM_PROMPT)
			.generateText(request.getContent());
		return new ScriptedAnswer(answer);
	}

	/**
	 * The answer, produced by the script rather than the model.
	 * @param content the model's report of the script's figures
	 * @author Ivan Rodriguez
	 */
	public record ScriptedAnswer(String content) {
	}

}
