package com.embabel.smith.remote;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Export;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.domain.io.UserInput;

/**
 * The skill published as an MCP tool (POC 13 — the remote/MCP half of C11). A single exported goal
 * — "explain a topic" — annotated {@code @Export(remote = true)} so the Embabel MCP server
 * ({@code embabel-agent-starter-mcpserver}) auto-publishes it as an MCP tool over Streamable HTTP.
 * This is the "skill" side of remote invocation: an exported goal exposed to external MCP clients,
 * the same exported-goal-as-skill surface the no-code runtime ingests, but now published remotely.
 */
@Agent(description = "Explains a topic in plain language; the goal is published as an MCP tool")
public class RemoteExplainerAgent {

    @Action(description = "Explain a topic in plain language")
    @AchievesGoal(description = "Explain a topic in plain language",
            export = @Export(remote = true, name = "explainTopic", startingInputTypes = {UserInput.class}))
    public String explainTopic(UserInput input, OperationContext context) {
        return context.ai().withDefaultLlm().creating(String.class)
                .fromPrompt("Explain this topic in plain language: " + input.getContent());
    }
}
