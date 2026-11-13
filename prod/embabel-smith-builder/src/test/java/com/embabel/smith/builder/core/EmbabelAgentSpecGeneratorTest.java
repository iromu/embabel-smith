package com.embabel.smith.builder.core;

import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.core.DynamicType;
import com.embabel.agent.core.ValuePropertyDefinition;
import com.embabel.agent.spec.compile.AgentCompilationResult;
import com.embabel.agent.spec.compile.CompilationError;
import com.embabel.agent.spec.model.GoalSpec;
import com.embabel.agent.spec.model.PromptedActionSpec;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests for the embabel generation technique: the compile seam is stubbed (no
 * LLM) and the mapping of the compiler's result to the shared agent spec is checked,
 * including the name derivation and the compiler's issue passthrough.
 */
class EmbabelAgentSpecGeneratorTest {

	// The compiler seam is stubbed and ignores the dictionary, so null is fine here.
	private final DataDictionary dictionary = null;

	@Test
	void mapsCompiledActionsGoalAndTypesToAnExportedAgentSpec() {
		PromptedActionSpec extract = new PromptedActionSpec("extractTopic", "Extract the topic",
				LlmOptions.withDefaults(), Set.of("UserInput"), "TopicBrief", List.of(), List.of(), 1.0, 1.0, true,
				"Extract the topic: {{userInput.content}}", List.of(), List.of(), List.of(), false, false, "action");
		GoalSpec goal = new GoalSpec("explainer", "Explain the topic", "BackgroundMessage", true, "goal");
		DynamicType topicBrief = new DynamicType("TopicBrief", "A short brief about the topic",
				List.of(new ValuePropertyDefinition("topic", "string")), List.of(), true);
		AgentCompilationResult result = new AgentCompilationResult(List.of(extract), goal, List.of(topicBrief),
				List.of());

		AgentSpec pack = new EmbabelAgentSpecGenerator((name, description, dd) -> result, this.dictionary)
			.generate("a business process")
			.pack();

		assertThat(pack.goal().isExported()).isTrue();
		assertThat(pack.goalOutputType()).isEqualTo("BackgroundMessage");
		assertThat(pack.steps()).hasSize(1);
		ActionStepSpec step = pack.steps().get(0);
		assertThat(step.stepType()).isEqualTo("action");
		assertThat(step.name()).isEqualTo("extractTopic");
		assertThat(step.inputTypeNames()).containsExactly("UserInput");
		assertThat(step.outputTypeName()).isEqualTo("TopicBrief");
		assertThat(pack.types()).hasSize(1);
	}

	@Test
	void compilesWithTheNameSluggedFromTheDescription() {
		GoalSpec goal = new GoalSpec("g", "done", "BackgroundMessage", true, "goal");
		AgentCompilationResult result = new AgentCompilationResult(List.of(), goal, List.of(), List.of());
		AtomicReference<String> seenName = new AtomicReference<>();

		EmbabelAgentSpecGenerator generator = new EmbabelAgentSpecGenerator((name, description, dd) -> {
			seenName.set(name);
			return result;
		}, this.dictionary);
		CompiledDraft draft = generator.generate("Order Intake Process");

		assertThat(seenName.get()).isEqualTo("order-intake-process");
		assertThat(draft.agentName()).isEqualTo("order-intake-process");
		assertThat(draft.pack().name()).isEqualTo("order-intake-process");
		assertThat(draft.pack().goal().isExported()).isTrue();
	}

	@Test
	void passesCompilerIssuesThroughWithTheDraftForReview() {
		GoalSpec goal = new GoalSpec("g", "done", "BackgroundMessage", true, "goal");
		AgentCompilationResult result = new AgentCompilationResult(List.of(), goal, List.of(),
				List.of(new CompilationError("ambiguous goal: cannot determine the spec's intent", "compiler", null)));

		CompiledDraft draft = new EmbabelAgentSpecGenerator((name, description, dd) -> result, this.dictionary)
			.generate("ambiguous");

		assertThat(draft.hasIssues()).isTrue();
		assertThat(draft.issues()).anyMatch(issue -> issue.contains("ambiguous"));
		assertThat(draft.pack()).isNotNull();
	}

}
