/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.eval;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import com.embabel.agent.api.event.AgenticEventListener;
import com.embabel.agent.api.event.AgentProcessEvent;
import com.embabel.agent.api.event.ToolCallRequestEvent;
import com.embabel.agent.api.event.ToolLoopStartEvent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.model.AgentSpec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The live half of task 4.1 of {@code builder-executes-its-own-packs}: whether the
 * deployed builder pack's drafting step is <em>offered</em> the validator it declares,
 * and what the live model then <em>called</em>. Those are two facts about two different
 * seams, so they are stated apart here and treated apart in the test: offered is
 * asserted, called is recorded. The other half of 4.1 — that the tool exists and carries
 * one argument named {@code specJson} — is offline and already held by
 * {@code BuilderPackToolsTest} and {@code ValidationToolGroupTest}; nothing here repeats
 * it.
 *
 * <p>
 * <b>Offered is asserted, and is a fact about the registration seam.</b> A step's
 * {@code toolGroups} entry becomes a {@code ToolGroupRequirement} that
 * {@code PromptRunner.withToolGroups} resolves by role against the {@code ToolGroup}
 * beans of the context that booted the platform
 * ({@code AgentPlatformConfiguration.toolGroupResolver}). Two shipped files decide the
 * answer, and nothing model-side can change either: {@code tools/ValidationToolGroup}
 * registers the {@code validate} group carrying
 * {@link BuilderPackTools#validationTool(com.embabel.smith.builder.tools.ToolSurface)},
 * and {@code builder-spec-one-shot/steps/draftAgentSpec.yml} declares that role on the
 * drafting step. Why this route and not the deploy's own tool list is that class's own
 * record, not restated here. A run whose offered list omits {@code validatePack} is
 * therefore a resolution failure, which is what the assertion below fails on. The
 * measurement is the names carried by the run's {@code ToolLoopStartEvent}s, the same
 * names the platform logs as {@code starting tool loop [<names>]}: before the bean
 * existed this deploy warned that the declared role resolved to nothing and started
 * {@code []}; with it the same run starts {@code [validatePack]}. That pair of live
 * observations is the whole of what this class asserts about the validator, and it says
 * only that the validator is reachable from the deployed step and offered to it.
 *
 * <p>
 * <b>Called is recorded, and is a fact about model behaviour.</b> Whether a model that is
 * offered the tool reaches for it is not a property of this repository's code, and the
 * repo deliberately does not pin it. The two seams that run this pack are not evidence
 * about each other: a call has been observed on the {@code smith build --flow
 * pack} CLI path and not on the deploy seam this case runs on, and either answer can
 * change with a model or a prompt without touching this repository's code. So the call
 * count and the argument strings the model sent stay printed for tasks 4.1 and 4.3 to
 * record — task 4.3's own wording is that a step which never calls the validator is "a
 * finding to record, not a success to celebrate" — and the printed line leads with
 * {@code offered=} so that a {@code called=0} is attributable: with {@code offered=true}
 * it is the model's choice, and {@code offered=false} cannot reach the print as a passing
 * run because the assertion above fails first. No assertion is made about the call count,
 * and nothing here claims the validator gets used.
 *
 * <p>
 * <b>The observation, and why this one.</b> Both numbers are read off Embabel's own event
 * stream, not off a wrapped {@code Tool.Function} body. Wrapping the body was the first
 * candidate and is not available: {@link AgentSpecInvokerFactory} authors the tool list
 * itself and passes it straight into {@code StepSpecAgentScopeBuilder}, a deployed
 * {@code AgentScope} publishes no tool list to read back, and no seam lets a test
 * substitute a recording tool for the real one. What the platform does emit is
 * {@code ToolLoopStartEvent}, which names the tools offered to each LLM request, and
 * {@code ToolCallRequestEvent}, which {@code ToolDecorators.EventPublishingTool}
 * publishes around every invocation of an offered tool, carrying the tool name and the
 * argument string the model sent. Both reach every {@code AgenticEventListener} bean,
 * because {@code AgentPlatformConfiguration.eventListener(List<AgenticEventListener>)}
 * composes them and {@code ProcessContext} fans every process event out to that
 * composite. So {@link ToolCallRecordings} below is a record of the run rather than a
 * hook into the builder: it observes the same channel the platform's own tool statistics
 * use.
 *
 * <p>
 * <b>The control that keeps "not offered" from being a dead recorder.</b> Ahead of the
 * offered assertion, the test asserts it saw at least one tool-loop event during the run.
 * A listener that never fired offers nothing, and without that control a broken recording
 * would be reported as a resolution failure instead of as what it is. The call event
 * travels the same fan-out as the tool-loop event, so a channel proven to carry one can
 * carry the other, which is what makes a recorded {@code called=0} worth reading.
 *
 * <p>
 * <b>Gating.</b> {@code @Tag("live")} plus the {@code OPENAI_CUSTOM_BASE_URL} environment
 * gate, copied from {@code ToolGroupBindingTest}'s {@code OnABootedPlatform} and
 * {@code LiveBuildIntegrationTest} rather than restated as a second convention. The
 * reactor excludes the {@code live} group, so an offline run never loads this context; a
 * run with an endpoint spends one model call.
 *
 * <p>
 * <b>What this cannot establish.</b> Nothing about a pack other than the one shipped, and
 * nothing about {@code --flow java}. The offered list is decided by shipped code, so it
 * is a property and is asserted; the call count is one model's behaviour on one
 * description, so it is a measurement and is only reported. It also cannot see a tool
 * called by a path that does not pass through {@code EventPublishingTool}; the control
 * above is what keeps that from being a silent pass.
 *
 * @author Ivan Rodriguez
 */
@SpringBootTest
@Tag("live")
@EnabledIfEnvironmentVariable(named = "OPENAI_CUSTOM_BASE_URL", matches = ".+")
class DeployedBuilderPackCallsValidatePackTest {

	/**
	 * A business process with stages, which is what the pack's step asks to model. Kept
	 * short: the point is one pack call that can reach a tool, not a hard generation
	 * problem.
	 */
	private static final String DESCRIPTION = "A specialty coffee subscription: a customer places a "
			+ "recurring order, the roastery roasts the beans for that order and logs the batch, "
			+ "quality control approves or rejects the batch, shipping picks a delivery date and "
			+ "dispatches the box, and the customer gets a renewal reminder before the next roast.";

	@Autowired
	AgentPlatform platform;

	@Autowired
	ToolCallRecordings recordings;

	@Test
	void theDeployedPackOffersTheValidatorToALiveModelWhichMayCallIt() throws IOException {
		assertThat(PackDrivenAgentSpecGenerator.builderPack().resolve("manifest.yml"))
			.as("pack directory %s exists on disk", PackDrivenAgentSpecGenerator.builderPack())
			.isRegularFile();
		this.recordings.reset();
		AgentSpec pack = new AgentSpecReader().read(PackDrivenAgentSpecGenerator.builderPack());

		// The deploy is the production seam the pack flow uses, so the group the step
		// declares resolves the way it does in a build. The answer is not asserted:
		// whether the goal produced parseable spec JSON is a different number, measured
		// elsewhere.
		new AgentSpecInvokerFactory(this.platform).invokerFor(pack).invoke(DESCRIPTION);

		Set<String> offered = this.recordings.offeredToolNames();
		List<String> validatorArguments = this.recordings.validatorArguments();
		System.out.printf(
				"4.1 resolution: %s offered=%b to %d tool loop(s); called=%d with %s; " + "schema argument named %s%n",
				BuilderPackTools.VALIDATE_PACK, offered.contains(BuilderPackTools.VALIDATE_PACK),
				this.recordings.toolLoopCount(), validatorArguments.size(), validatorArguments,
				BuilderPackTools.SPEC_JSON);
		System.out.printf("4.1 resolution: every tool offered was %s; every tool called was %s%n", offered,
				this.recordings.calledToolNames());

		assertThat(this.recordings.toolLoopCount())
			.as("tool-loop events seen during the run — the same fan-out carries the tool-call "
					+ "event, so a dead recorder would fail here instead of reporting a clean zero")
			.isPositive();

		// Offered is decided by shipped code, so it is asserted. Whether the model then
		// called the tool is not, which is why the call count and arguments printed above
		// stay a record.
		assertThat(offered)
			.as("%s was offered to none of the %d tool loop(s) this run started, so the step's "
					+ "`toolGroups: [validate]` resolved to nothing — a resolution failure at the "
					+ "ValidationToolGroup bean, not a model choice. Tools offered were %s.",
					BuilderPackTools.VALIDATE_PACK, this.recordings.toolLoopCount(), offered)
			.contains(BuilderPackTools.VALIDATE_PACK);
	}

	/**
	 * Publishes {@link ToolCallRecordings} so the platform's composite
	 * {@code AgenticEventListener} includes it. A {@code @TestConfiguration} bean rather
	 * than a hook in the builder: the platform collects every listener bean of its own
	 * accord, so the recording is a fact about the run and the shipped code is untouched
	 * by it.
	 */
	@TestConfiguration
	static class ToolCallRecordingsConfiguration {

		@Bean
		ToolCallRecordings toolCallRecordings() {
			return new ToolCallRecordings();
		}

	}

	/**
	 * What the platform offered and what the model called, held in lists safe for the
	 * platform's threads. Names of tools other than the validator are kept as well,
	 * because "no tool at all" and "another tool instead" are different findings and this
	 * is the only place both are visible.
	 */
	static final class ToolCallRecordings implements AgenticEventListener {

		private final List<List<String>> offeredPerLoop = new CopyOnWriteArrayList<>();

		private final List<String> calledTools = new CopyOnWriteArrayList<>();

		private final List<String> validatorArguments = new CopyOnWriteArrayList<>();

		@Override
		public void onProcessEvent(AgentProcessEvent event) {
			if (event instanceof ToolLoopStartEvent start) {
				this.offeredPerLoop.add(List.copyOf(start.getToolNames()));
			}
			else if (event instanceof ToolCallRequestEvent call) {
				this.calledTools.add(call.getTool());
				if (BuilderPackTools.VALIDATE_PACK.equals(call.getTool())) {
					this.validatorArguments.add(String.valueOf(call.getToolInput()));
				}
			}
		}

		private int toolLoopCount() {
			return this.offeredPerLoop.size();
		}

		private Set<String> offeredToolNames() {
			Set<String> names = new LinkedHashSet<>();
			this.offeredPerLoop.forEach(names::addAll);
			return names;
		}

		private List<String> calledToolNames() {
			return List.copyOf(this.calledTools);
		}

		private List<String> validatorArguments() {
			return new ArrayList<>(this.validatorArguments);
		}

		private void reset() {
			this.offeredPerLoop.clear();
			this.calledTools.clear();
			this.validatorArguments.clear();
		}

	}

}
