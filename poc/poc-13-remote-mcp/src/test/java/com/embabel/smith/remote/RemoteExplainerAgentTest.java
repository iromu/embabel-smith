package com.embabel.smith.remote;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Export;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.domain.io.UserInput;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline structural check (POC 13): the spec's goal is annotated to be exported as a remote MCP
 * tool. The actual MCP round-trip (server + client over Streamable HTTP) is exercised by the live
 * run, not here — it needs a live web server and endpoint.
 */
class RemoteExplainerAgentTest {

    @Test
    void theAgentIsAnnotated() {
        assertThat(RemoteExplainerAgent.class.getAnnotation(Agent.class)).isNotNull();
    }

    @Test
    void theGoalIsExportedAsARemoteMcpTool() throws NoSuchMethodException {
        var goal = RemoteExplainerAgent.class.getMethod("explainTopic", UserInput.class, OperationContext.class);
        AchievesGoal achievesGoal = goal.getAnnotation(AchievesGoal.class);
        assertThat(achievesGoal).isNotNull();
        Export export = achievesGoal.export();
        assertThat(export).isNotNull();
        assertThat(export.remote()).isTrue();
        assertThat(export.name()).isEqualTo("explainTopic");
        assertThat(export.startingInputTypes()).contains(UserInput.class);
    }
}
