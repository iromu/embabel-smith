/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.improve;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.builder.eval.AgentSpecInvokerFactory;
import com.embabel.smith.builder.eval.AiPromptRewriter;
import com.embabel.smith.builder.eval.EvalTask;
import com.embabel.smith.builder.improve.CandidateStore;
import com.embabel.smith.builder.improve.ImprovementLoop;
import com.embabel.smith.builder.improve.LoopPublishPolicy;
import com.embabel.smith.builder.improve.PendingCandidate;
import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleState;
import com.embabel.smith.builder.publish.FolderPublisher;
import com.embabel.smith.builder.publish.PublishTarget;
import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.io.Yaml;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.runtime.trace.JsonlTraceStore;
import com.embabel.smith.specsource.FolderSpecSource;
import com.embabel.smith.specsource.SpecSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Endpoint-gated loop integration test (task 3.2): one full loop iteration on a live
 * pack, with the real refine and re-eval, publishes an improved pack to a local folder
 * source. Skipped when the model endpoint is not configured.
 */
@SpringBootTest
@Tag("live")
@EnabledIfEnvironmentVariable(named = "OPENAI_CUSTOM_BASE_URL", matches = ".+")
class LiveLoopIntegrationTest {

	@Autowired
	Ai ai;

	@Autowired
	AgentPlatform platform;

	@Test
	void oneIterationPublishesAnImprovedPack(@TempDir Path tmp) throws Exception {
		// A live pack in a folder source, with a failing trace to trigger refinement.
		Path sourceRoot = tmp.resolve("source");
		AgentSpec original = livePack();
		new AgentSpecWriter().write(original, sourceRoot.resolve("summarize"));
		SpecSource source = new FolderSpecSource("folder:" + sourceRoot, sourceRoot);

		JsonlTraceStore store = new JsonlTraceStore(tmp.resolve("traces.jsonl"));
		store.append(TestFixtures.failedTrace("r1", "summarize", "Describe your use case.",
				TestFixtures.step("summarize", "failed", "produced no summary")));

		AiPromptRewriter rewriter = new AiPromptRewriter(this.ai);
		// An empty-expected task passes on any non-null output, so the gate reliably
		// clears the
		// (zero) baseline once the real re-eval produces output.
		List<EvalTask> tasks = List.of(new EvalTask("runs-and-produces-output", "Describe your use case.", ""));
		FolderPublisher publisher = new FolderPublisher(PublishTarget.parse("folder:" + sourceRoot));

		ImprovementLoop loop = new ImprovementLoop(source, store, rewriter, new AgentSpecInvokerFactory(platform),
				tasks, publisher,
				// The store row a publish leaves is covered offline; this test asserts
				// the
				// folder write, so nothing here needs a bound database.
				new LoopPublishPolicy(false, "folder:" + sourceRoot, mock(CandidateStore.class)), true); // auto-publish

		List<PendingCandidate> held = loop.runOnce();

		assertThat(held).isEmpty(); // auto-published, not held
		Path improved = sourceRoot.resolve("summarize");
		assertThat(improved.resolve("manifest.yml")).isRegularFile();
		assertThat(improved.resolve("lifecycle.yml")).isRegularFile();
		// The published pack transitioned incubating -> active (a first proven
		// improvement).
		LifecycleMetadata lifecycle = Yaml.mapper()
			.readValue(Files.readString(improved.resolve("lifecycle.yml")), LifecycleMetadata.class);
		assertThat(lifecycle.state()).isEqualTo(LifecycleState.ACTIVE);
		assertThat(new AgentSpecReader().read(improved).step("summarize")).isNotNull();
	}

	/**
	 * A realistic single-step summarizer spec, deployable by the runtime (canonical FQN
	 * types).
	 */
	private static AgentSpec livePack() {
		return new AgentSpec(new AgentSpecManifest("summarize", "1.0.0"),
				GoalStepSpec.exportedGoal("summarize", "Summarize an input", BACKGROUND_MESSAGE),
				List.of(ActionStepSpec.action("summarize", "Summarize the input", List.of(USER_INPUT),
						BACKGROUND_MESSAGE, "Summarize the following text concisely: {{userInput.content}}")),
				List.of());
	}

	/**
	 * The canonical returnable output type and entry input type (the builder's normalized
	 * form).
	 */
	private static final String BACKGROUND_MESSAGE = "com.embabel.agent.spec.model.BackgroundMessage";

	private static final String USER_INPUT = "com.embabel.agent.domain.io.UserInput";

}
