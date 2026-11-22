/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.embabel.agent.domain.io.UserInput;
import com.embabel.smith.builder.cli.BuildFlow;
import com.embabel.smith.builder.core.AgentSpecGenerator;
import com.embabel.smith.builder.core.AgentSpecRef;
import com.embabel.smith.builder.core.BuilderAgent;
import com.embabel.smith.builder.core.CompiledDraft;
import com.embabel.smith.builder.core.GenerationTechnique;
import com.embabel.smith.builder.core.SingleShotAgentSpecGenerator;
import com.embabel.smith.builder.core.ValidatedDraft;
import com.embabel.smith.builder.eval.EvalTask;
import com.embabel.smith.builder.eval.InvokerFactory;
import com.embabel.smith.builder.eval.PackDrivenAgentSpecGenerator;
import com.embabel.smith.builder.eval.PromptRewriter;
import com.embabel.smith.builder.publish.PublishTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 3.3: the deterministic gates are the same sequence whichever flow issued the
 * generation call. Both flows are driven over one fixture — the same answer text is what
 * the Java generator's caller returns and what the pack's goal returns — and the recorded
 * gate sequence is compared, which is what has to match. The answer is not the point:
 * nothing here asserts that the two flows produce the same draft from a real model, only
 * that from the same text the build does the same things in the same order and stops at
 * the same gate.
 *
 * <p>
 * <b>Which gates are recorded and which are inferred.</b> {@code BuilderService} owns the
 * whole sequence, and its security scanner and lifecycle stamper are private finals it
 * constructs itself (its publisher comes from a private static factory), so neither can
 * be injected with a recorder. The three stages that run through the injectable seams —
 * generate, validate, write — are recorded by a {@code BuilderAgent} subclass that
 * delegates to the real implementation, and the eval deploy by the {@code InvokerFactory}
 * the refinement loop drives. Security scan, lifecycle stamp and publish are shown by the
 * fields a {@link BuildResult} only carries once those gates ran, and by the same
 * {@code stage()} on both flows when a later gate stops the build. That is why every case
 * below asserts the pair of results as well as the pair of lists: the lists alone would
 * not see a gate that cannot be injected.
 *
 * <p>
 * <b>Why the pack flow's own deploy is not in the list.</b> In production
 * {@code BuildCommand} hands one {@code AgentSpecInvokerFactory} to both the generator
 * and the service, so a pack flow deploys through the same object twice for different
 * reasons. Here the two are separate objects so the list holds gates only; the pack's
 * generation deploy is the generate stage, already recorded at the agent seam. Splitting
 * the object cannot reorder anything, because {@code BuilderService} — not the factory —
 * decides when each gate runs.
 *
 * <p>
 * A new class rather than a case in {@code core/BuildCoreTest} or
 * {@code DualCandidateBuildTest} because it drives {@code BuilderService} itself from the
 * {@code eval} generator, and both of those existing classes wire their own single
 * generator through their own helpers.
 *
 * @author Ivan Rodriguez
 */
class FlowGateParityTest {

	private static final List<EvalTask> TASKS = List.of(new EvalTask("runs", "in", "output"));

	/** An eval answer that satisfies the task above. */
	private static final String PASS = "output: fine";

	/** An eval answer that does not. */
	private static final String FAIL = "nothing the eval accepts";

	/**
	 * The drafted pack, as the answer both flows are given. Valid by the same shape
	 * {@code DualCandidateBuildTest} builds in memory: a goal over a built-in type with
	 * one action producing it, so the write step's re-validation passes and the build
	 * reaches the gates.
	 */
	private static final String DRAFTED = """
			{
			  "manifest": { "name": "parity-pack", "version": "1.0.0" },
			  "goal": {
			    "stepType": "goal", "name": "explained", "outputTypeName": "BackgroundMessage",
			    "description": "The explanation was produced", "export": true
			  },
			  "steps": [
			    {
			      "stepType": "action", "name": "say", "inputTypeNames": ["UserInput"],
			      "outputTypeName": "BackgroundMessage", "description": "Says it",
			      "prompt": "Explain: {{userInput}}", "toolGroups": []
			    }
			  ]
			}""";

	/**
	 * The same pack with its goal asking for a type nothing declares or produces, so
	 * validation is the gate that stops it.
	 */
	private static final String DANGLING = DRAFTED.replace("BackgroundMessage", "NonExistentType");

	@TempDir
	Path tmp;

	/**
	 * The claim itself: on a build that gets all the way through, both flows record
	 * generate, validate, write, eval, in that order, and both then pass through the
	 * security scan, the lifecycle stamp and the publish — the last three shown by the
	 * evidence only a build that ran them carries.
	 */
	@Test
	void bothFlowsRunTheSameGatesInTheSameOrderOnABuildThatFinishes() {
		Run java = buildBy(BuildFlow.JAVA, scratch("java"), library("java"), DRAFTED, PASS);
		Run pack = buildBy(BuildFlow.PACK, scratch("pack"), library("pack"), DRAFTED, PASS);

		assertThat(java.gates).as("the gates the Java flow ran")
			.containsExactly("generate", "validate", "write", "eval");
		assertThat(pack.gates).as("the gates the pack flow ran, identical").isEqualTo(java.gates);
		assertThat(java.result.isBuilt()).as(() -> java.result.report()).isTrue();
		assertThat(pack.result.isBuilt()).as(() -> pack.result.report()).isTrue();

		// The three gates no test can inject: the scan report, the lifecycle stamp and
		// the publish result are only populated once their gate ran.
		assertThat(pack.result.scan()).as("security scan ran on the pack flow as on the Java one")
			.isEqualTo(java.result.scan());
		assertThat(pack.result.lifecycle().state()).isEqualTo(java.result.lifecycle().state());
		assertThat(pack.result.published().pushed()).isEqualTo(java.result.published().pushed());
		assertThat(Path.of(pack.result.published().location()).resolve("lifecycle.yml")).isRegularFile();
		assertThat(Path.of(java.result.published().location()).resolve("lifecycle.yml")).isRegularFile();
	}

	/**
	 * A draft that fails validation stops both flows at the same gate, before either the
	 * eval or the publish, and neither writes anything to its library.
	 */
	@Test
	void bothFlowsStopAtTheSameGateWhenTheDraftIsInvalid() {
		Run java = buildBy(BuildFlow.JAVA, scratch("java"), library("java"), DANGLING, PASS);
		Run pack = buildBy(BuildFlow.PACK, scratch("pack"), library("pack"), DANGLING, PASS);

		assertThat(pack.gates).as("same gates, same order")
			.isEqualTo(java.gates)
			.containsExactly("generate", "validate");
		assertThat(java.result.isBuilt()).isFalse();
		assertThat(pack.result.stage()).isEqualTo(java.result.stage()).isEqualTo("build");
		assertThat(pack.result.issues()).isEqualTo(java.result.issues());
		assertThat(library("java")).doesNotExist();
		assertThat(library("pack")).doesNotExist();
	}

	/**
	 * Below the score gate both flows stop at eval with the same round count: the
	 * refinement loop is the same loop whoever issued the generation call, so the
	 * repeated eval entries in the list are equal too rather than merely both present.
	 */
	@Test
	void bothFlowsStopAtTheSameGateBelowTheScoreGate() {
		Run java = buildBy(BuildFlow.JAVA, scratch("java"), library("java"), DRAFTED, FAIL);
		Run pack = buildBy(BuildFlow.PACK, scratch("pack"), library("pack"), DRAFTED, FAIL);

		assertThat(java.gates).as("the refinement loop ran the same rounds on both flows")
			.isEqualTo(pack.gates)
			.startsWith("generate", "validate", "write", "eval");
		assertThat(pack.result.stage()).isEqualTo(java.result.stage()).isEqualTo("eval");
		assertThat(pack.result.score()).isEqualTo(java.result.score());
		assertThat(library("java")).doesNotExist();
		assertThat(library("pack")).doesNotExist();
	}

	/**
	 * The publish collision, which is the last gate and the one furthest from the flow
	 * flag: a second build of the same pack name is refused by both flows at the publish
	 * stage, having run every earlier gate identically.
	 */
	@Test
	void bothFlowsStopAtTheSameGateOnAPublishCollision() {
		buildBy(BuildFlow.JAVA, scratch("java"), library("java"), DRAFTED, PASS);
		Run java = buildBy(BuildFlow.JAVA, scratch("java"), library("java"), DRAFTED, PASS);
		buildBy(BuildFlow.PACK, scratch("pack"), library("pack"), DRAFTED, PASS);
		Run pack = buildBy(BuildFlow.PACK, scratch("pack"), library("pack"), DRAFTED, PASS);

		assertThat(pack.gates).as("every gate up to publish ran again, in the same order")
			.isEqualTo(java.gates)
			.containsExactly("generate", "validate", "write", "eval");
		assertThat(java.result.isBuilt()).isFalse();
		assertThat(pack.result.stage()).isEqualTo(java.result.stage()).isEqualTo("publish");
		// The published name is the drafted pack's, not the builder pack's: the flow does
		// not get to rename what it generated.
		assertThat(pack.result.packName()).isEqualTo(java.result.packName()).isEqualTo("parity-pack");
	}

	// --- wiring -------------------------------------------------------------

	/** What one flow's build did: the gates it recorded, and what it reported. */
	private record Run(List<String> gates, BuildResult result) {

	}

	/**
	 * One build, driven by one flow, with the model replaced at whichever seam that flow
	 * uses and the eval replaced always.
	 * @param flow the flow to drive
	 * @param scratch where the build writes its candidate
	 * @param library the folder it publishes to
	 * @param drafted the text the generation step's answer carries
	 * @param evalAnswer the text the eval run's answer carries
	 * @return the gates the build recorded and the result it reported
	 */
	private Run buildBy(BuildFlow flow, Path scratch, Path library, String drafted, String evalAnswer) {
		Recorder recorder = new Recorder();
		InvokerFactory evalInvokers = (pack) -> {
			recorder.gate("eval");
			return (input) -> evalAnswer;
		};
		AgentSpecGenerator generator;
		if (flow == BuildFlow.PACK) {
			InvokerFactory packInvokers = (pack) -> (input) -> drafted;
			generator = new PackDrivenAgentSpecGenerator(packInvokers, PackDrivenAgentSpecGenerator.BUILDER_PACK);
		}
		else {
			generator = new SingleShotAgentSpecGenerator((system, user) -> drafted,
					SingleShotAgentSpecGenerator.defaultPrompt());
		}
		PromptRewriter rewriter = (current, feedback) -> current;
		BuilderService service = new BuilderService(
				new RecordingBuilderAgent(Map.of(GenerationTechnique.SINGLE_SHOT, generator), scratch.toString(),
						recorder),
				evalInvokers, rewriter, TASKS);

		BuildResult result = service.build("Explain what changed", PublishTarget.parse("folder:" + library),
				BuildOptions.defaults());
		return new Run(List.copyOf(recorder.gates), result);
	}

	private Path scratch(String name) {
		return this.tmp.resolve(name).resolve("scratch");
	}

	private Path library(String name) {
		return this.tmp.resolve(name).resolve("library");
	}

	/** The gate log one build wrote. */
	private static final class Recorder {

		private final List<String> gates = new ArrayList<>();

		void gate(String gate) {
			this.gates.add(gate);
		}

	}

	/**
	 * The real {@code BuilderAgent} with its three stages noted as they run. Every call
	 * goes to {@code super}, so the validation, the write and the write's re-validation
	 * are the shipped ones; only the logging is added.
	 */
	private static final class RecordingBuilderAgent extends BuilderAgent {

		private final Recorder recorder;

		RecordingBuilderAgent(Map<GenerationTechnique, AgentSpecGenerator> generators, String outputDir,
				Recorder recorder) {
			super(generators, outputDir);
			this.recorder = recorder;
		}

		@Override
		public CompiledDraft compileFromDescription(UserInput description, GenerationTechnique technique,
				String model) {
			this.recorder.gate("generate");
			return super.compileFromDescription(description, technique, model);
		}

		@Override
		public ValidatedDraft validateDraft(CompiledDraft draft) {
			this.recorder.gate("validate");
			return super.validateDraft(draft);
		}

		@Override
		public AgentSpecRef writeAgentSpec(ValidatedDraft draft) {
			this.recorder.gate("write");
			return super.writeAgentSpec(draft);
		}

	}

}
