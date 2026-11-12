package com.embabel.smith.builder;

import com.embabel.agent.core.DynamicType;
import com.embabel.agent.core.ValuePropertyDefinition;
import com.embabel.agent.spec.compile.AgentCompilationResult;
import com.embabel.agent.spec.model.GoalSpec;
import com.embabel.agent.spec.model.PromptedActionSpec;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.AgentSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline test of the compile-seam (task 5.5): an {@link AgentCompilationResult} is built
 * by hand (no LLM) and the {@link DraftToAgentSpec} conversion is checked to produce the
 * right shared agent spec. The endpoint-gated part (the NL compiler actually calling the
 * model) is not exercised here.
 */
class DraftToAgentSpecTest {

	private final DraftToAgentSpec mapper = new DraftToAgentSpec();

	@Test
	void mapsActionsGoalAndTypesToTheSharedAgentSpec() {
		PromptedActionSpec extract = new PromptedActionSpec("extractTopic",
				"Extract the topic from the user's question", LlmOptions.withDefaults(), Set.of("UserInput"),
				"TopicBrief", List.of(), List.of(), 1.0, 1.0, true, "Extract the topic: {{userInput.content}}",
				List.of(), List.of(), List.of(), false, false, "action");

		GoalSpec goal = new GoalSpec("explainer", "Explain the topic", "BackgroundMessage", true, "goal");

		DynamicType topicBrief = new DynamicType("TopicBrief", "A short brief about the topic",
				List.of(new ValuePropertyDefinition("topic", "string")), List.of(), true);

		AgentCompilationResult result = new AgentCompilationResult(List.of(extract), goal, List.of(topicBrief),
				List.of());

		AgentSpec pack = mapper.toAgentSpec(result, "explainer");

		assertThat(pack.name()).isEqualTo("explainer");
		assertThat(pack.version()).isEqualTo("1.0.0");
		assertThat(pack.goal().isExported()).isTrue();
		assertThat(pack.goalOutputType()).isEqualTo("BackgroundMessage");

		assertThat(pack.steps()).hasSize(1);
		ActionStepSpec step = pack.steps().get(0);
		assertThat(step.stepType()).isEqualTo("action");
		assertThat(step.name()).isEqualTo("extractTopic");
		assertThat(step.inputTypeNames()).containsExactly("UserInput");
		assertThat(step.outputTypeName()).isEqualTo("TopicBrief");
		assertThat(step.prompt()).contains("{{userInput.content}}");

		assertThat(pack.types()).hasSize(1);
		DynamicTypeDef type = pack.types().get(0);
		assertThat(type.name()).isEqualTo("TopicBrief");
		assertThat(type.isCreationPermitted()).isTrue();
		assertThat(type.ownProperties()).hasSize(1);
		assertThat(type.ownProperties().get(0).name()).isEqualTo("topic");
		assertThat(type.ownProperties().get(0).type()).isEqualTo("string");
	}

	@Test
	void normalizesAVoidGoalOutputToBackgroundMessage() {
		PromptedActionSpec action = new PromptedActionSpec("explainAndPost",
				"Explain the topic and post it as a background message", LlmOptions.withDefaults(), Set.of("UserInput"),
				"void", List.of(), List.of(), 0.0, 0.0, false, "Explain the topic: {{userInput.content}}", List.of(),
				List.of(), List.of(), false, false, "action");

		// The NL compiler emits a non-exported goal whose output is the degenerate type
		// 'void'.
		GoalSpec goal = new GoalSpec("my-spec-goal", "Explained and posted", "void", false, "goal");
		DynamicType voidType = new DynamicType("void", "Auto-inferred type", List.of(), List.of(), true);

		AgentCompilationResult result = new AgentCompilationResult(List.of(action), goal, List.of(voidType), List.of());

		AgentSpec pack = mapper.toAgentSpec(result, "my-spec");

		assertThat(pack.goal().isExported()).isTrue();
		assertThat(pack.goalOutputType()).isEqualTo("com.embabel.agent.spec.model.BackgroundMessage");
		assertThat(pack.steps()).hasSize(1);
		assertThat(pack.steps().get(0).outputTypeName()).isEqualTo("com.embabel.agent.spec.model.BackgroundMessage");
		assertThat(pack.steps().get(0).inputTypeNames()).containsExactly("UserInput");
		// The degenerate 'void' type is no longer referenced, so it is dropped.
		assertThat(pack.types()).isEmpty();
	}

	@Test
	void remapsOnlyTheTerminalDegenerateActionInAChain() {
		// A -> string -> B -> void(goal). Only B is terminal; A's 'string' output feeds
		// B.
		PromptedActionSpec extract = new PromptedActionSpec("extractBrief", "Extract a brief",
				LlmOptions.withDefaults(), Set.of("UserInput"), "string", List.of(), List.of(), 0.0, 0.0, false,
				"Brief: {{userInput.content}}", List.of(), List.of(), List.of(), false, false, "action");
		PromptedActionSpec post = new PromptedActionSpec("postIt", "Post the brief", LlmOptions.withDefaults(),
				Set.of("string"), "void", List.of(), List.of(), 0.0, 0.0, false, "Post: {{string}}", List.of(),
				List.of(), List.of(), false, false, "action");

		GoalSpec goal = new GoalSpec("g", "done", "void", false, "goal");
		DynamicType stringType = new DynamicType("string", "s", List.of(), List.of(), true);
		DynamicType voidType = new DynamicType("void", "v", List.of(), List.of(), true);

		AgentCompilationResult result = new AgentCompilationResult(List.of(extract, post), goal,
				List.of(stringType, voidType), List.of());

		AgentSpec pack = mapper.toAgentSpec(result, "chain");

		assertThat(pack.goalOutputType()).isEqualTo("com.embabel.agent.spec.model.BackgroundMessage");
		// Non-terminal action (its output feeds another action) is left untouched.
		assertThat(pack.step("extractBrief").outputTypeName()).isEqualTo("string");
		// Terminal degenerate action is remapped so the chain's final output is
		// returnable.
		assertThat(pack.step("postIt").outputTypeName()).isEqualTo("com.embabel.agent.spec.model.BackgroundMessage");
		// The now-unused 'void' type is dropped; 'string' (still referenced) is kept.
		assertThat(pack.types()).extracting(t -> t.name()).containsExactly("string");
	}

}
