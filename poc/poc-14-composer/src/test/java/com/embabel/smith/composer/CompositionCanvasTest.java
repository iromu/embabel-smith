package com.embabel.smith.composer;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.PropertyDef;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests for the composition canvas (POC 14): the drag-and-drop edge type-check, entry/exit
 * detection, and the composed chain.
 */
class CompositionCanvasTest {

    private static final String BG = "com.embabel.agent.spec.model.BackgroundMessage";

    @Test
    void aValidTwoSpecChainPassesValidation() {
        AgentSpec extract = extractPack();
        AgentSpec explain = explainPack();

        CompositionCanvas canvas = new CompositionCanvas();
        canvas.addSpec(new SpecNode(extract));
        canvas.addSpec(new SpecNode(explain));
        canvas.connect("compose-extract", "compose-explain");

        assertThat(canvas.validate()).isEmpty();
        assertThat(canvas.chain()).extracting(SpecNode::id)
                .containsExactly("compose-extract", "compose-explain");
        assertThat(canvas.describeChain()).isEqualTo("compose-extract -> compose-explain");
    }

    @Test
    void aTypeIncompatibleEdgeIsRejected() {
        AgentSpec extract = extractPack(); // outputs TopicBrief
        // A spec that consumes a type the extractor does not produce.
        AgentSpec needsOther = pack("compose-explain", BG, "writeExplainer",
                "com.embabel.agent.spec.model.OtherBrief", BG);

        CompositionCanvas canvas = new CompositionCanvas();
        canvas.addSpec(new SpecNode(extract));
        canvas.addSpec(new SpecNode(needsOther));
        canvas.connect("compose-extract", "compose-explain");

        assertThat(canvas.validate())
                .anyMatch(issue -> issue.contains("type-incompatible"));
    }

    @Test
    void aChainWithNoExitSpecIsFlagged() {
        AgentSpec extract = extractPack();
        // A second spec that ends in TopicBrief (not BackgroundMessage), so there is no exit.
        AgentSpec alsoExtract = pack("compose-extract-2", "TopicBrief", "extractAgain",
                "UserInput", "TopicBrief");

        CompositionCanvas canvas = new CompositionCanvas();
        canvas.addSpec(new SpecNode(extract));
        canvas.addSpec(new SpecNode(alsoExtract));
        canvas.connect("compose-extract", "compose-extract-2");

        assertThat(canvas.validate())
                .anyMatch(issue -> issue.contains("exit spec"));
    }

    @Test
    void aDisconnectedNodeIsFlagged() {
        AgentSpec extract = extractPack();
        AgentSpec explain = explainPack();

        CompositionCanvas canvas = new CompositionCanvas();
        canvas.addSpec(new SpecNode(extract));
        canvas.addSpec(new SpecNode(explain));
        // No edge at all: explain has an unexpected incoming-degree of 0.
        assertThat(canvas.validate())
                .anyMatch(issue -> issue.contains("compose-explain"));
    }

    // --- test fixtures -------------------------------------------------------

    private static AgentSpec extractPack() {
        return pack("compose-extract", "TopicBrief", "extractTopic", "UserInput", "TopicBrief",
                "Extract the topic: {{userInput.content}}", topicBriefType());
    }

    private static AgentSpec explainPack() {
        return pack("compose-explain", BG, "writeExplainer", "TopicBrief", BG,
                "Write the explainer: {{topicBrief.topic}}", topicBriefType());
    }

    private static AgentSpec pack(String name, String goalOutput, String actionName,
                                  String actionInput, String actionOutput) {
        return pack(name, goalOutput, actionName, actionInput, actionOutput, "prompt", null);
    }

    private static AgentSpec pack(String name, String goalOutput, String actionName,
                                  String actionInput, String actionOutput, String prompt,
                                  DynamicTypeDef type) {
        AgentSpecManifest manifest = new AgentSpecManifest(name, "1.0.0", "GOAP", null);
        GoalStepSpec goal = GoalStepSpec.exportedGoal(name + "Goal", "desc", goalOutput);
        ActionStepSpec action = ActionStepSpec.action(actionName, "desc",
                List.of(actionInput), actionOutput, prompt);
        return new AgentSpec(manifest, goal, List.of(action),
                type == null ? List.of() : List.of(type));
    }

    private static DynamicTypeDef topicBriefType() {
        return new DynamicTypeDef("TopicBrief", "desc", List.of(
                new PropertyDef("topic", "string", "what"),
                new PropertyDef("audience", "string", "who"),
                new PropertyDef("tone", "string", "voice")),
                List.of(), true);
    }
}
