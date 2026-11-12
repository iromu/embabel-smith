package com.embabel.smith.eval;

import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.samples.SampleAgentSpecs;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline test of the rewrite loop's re-validation half (task 6.2): splicing a revised
 * prompt into a step and re-running the group-1 validation. The endpoint-gated half (the
 * LLM producing the revised prompt via {@code rewritePrompt}) is not exercised here.
 */
class PromptRewriteLoopTest {

	private final PromptRewriteLoop loop = new PromptRewriteLoop();

	@Test
	void applyRevisedPromptSplicesAndRevalidates() {
		AgentSpec pack = SampleAgentSpecs.explainer();
		String revised = "Write a clearer explainer about: {{topicBrief.topic}} for {{topicBrief.audience}}.";

		PromptRewriteLoop.RewriteResult result = loop.applyRevisedPrompt(pack, "writeExplainer", revised);

		assertThat(result.valid()).isTrue();
		assertThat(result.issues()).isEmpty();
		assertThat(result.revisedPrompt()).isEqualTo(revised);
		assertThat(result.revisedPack().step("writeExplainer").prompt()).isEqualTo(revised);
		// The other step is untouched.
		assertThat(result.revisedPack().step("extractTopic").prompt()).isEqualTo(pack.step("extractTopic").prompt());
	}

	@Test
	void applyRevisedPromptReportsWhenTheRevisedDraftIsInvalid() {
		AgentSpec pack = SampleAgentSpecs.explainer();
		// References a variable not bound by the step's input type (TopicBrief ->
		// topicBrief).
		String revised = "Write about {{nonexistent.topic}}.";

		PromptRewriteLoop.RewriteResult result = loop.applyRevisedPrompt(pack, "writeExplainer", revised);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).isNotEmpty();
	}

}
