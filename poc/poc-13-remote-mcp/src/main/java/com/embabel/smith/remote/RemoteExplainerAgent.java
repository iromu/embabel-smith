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

package com.embabel.smith.remote;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Export;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.domain.io.UserInput;

/**
 * The spec published as an MCP tool (POC 13 — the remote/MCP half of C11). A single
 * exported goal — "explain a topic" — annotated {@code @Export(remote = true)} so the
 * Embabel MCP server ({@code embabel-agent-starter-mcpserver}) auto-publishes it as an
 * MCP tool over Streamable HTTP. This is the "spec" side of remote invocation: an
 * exported goal exposed to external MCP clients, the same exported-goal-as-spec surface
 * the no-code runtime ingests, but now published remotely.
 *
 * @author Ivan Rodriguez
 */
@Agent(description = "Explains a topic in plain language; the goal is published as an MCP tool")
public class RemoteExplainerAgent {

	@Action(description = "Explain a topic in plain language")
	@AchievesGoal(description = "Explain a topic in plain language",
			export = @Export(remote = true, name = "explainTopic", startingInputTypes = { UserInput.class }))
	public String explainTopic(UserInput input, OperationContext context) {
		return context.ai()
			.withDefaultLlm()
			.creating(String.class)
			.fromPrompt("Explain this topic in plain language: " + input.getContent());
	}

}
