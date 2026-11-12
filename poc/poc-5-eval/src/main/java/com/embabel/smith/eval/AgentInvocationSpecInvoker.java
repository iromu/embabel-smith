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

package com.embabel.smith.eval;

import java.util.Map;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.spec.model.BackgroundMessage;

/**
 * The non-chat invocation path (design D6): runs the goal-type spec via
 * {@link AgentInvocation} and returns the goal output's content. This is the "via
 * AgentInvocation" wiring — the spec is invoked as a single typed goal run, not as a chat
 * session.
 *
 * <p>
 * Endpoint-gated: the invocation calls the model.
 *
 * @author Ivan Rodriguez
 */
public class AgentInvocationSpecInvoker implements SpecInvoker {

	private final AgentInvocation<BackgroundMessage> invocation;

	public AgentInvocationSpecInvoker(AgentInvocation<BackgroundMessage> invocation) {
		this.invocation = invocation;
	}

	@Override
	public String invoke(String input) {
		BackgroundMessage result = this.invocation.invoke(Map.of("userInput", new UserInput(input)));
		return (result != null) ? result.getContent() : null;
	}

}
