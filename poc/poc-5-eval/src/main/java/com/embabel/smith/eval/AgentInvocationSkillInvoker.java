package com.embabel.smith.eval;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.spec.model.BackgroundMessage;

import java.util.Map;

/**
 * The non-chat invocation path (design D6): runs the goal-type skill via {@link AgentInvocation}
 * and returns the goal output's content. This is the "via AgentInvocation" wiring — the skill is
 * invoked as a single typed goal run, not as a chat session.
 *
 * <p>Endpoint-gated: the invocation calls the model.
 */
public class AgentInvocationSkillInvoker implements SkillInvoker {

    private final AgentInvocation<BackgroundMessage> invocation;

    public AgentInvocationSkillInvoker(AgentInvocation<BackgroundMessage> invocation) {
        this.invocation = invocation;
    }

    @Override
    public String invoke(String input) {
        BackgroundMessage result = invocation.invoke(Map.of("userInput", new UserInput(input)));
        return result == null ? null : result.getContent();
    }
}
