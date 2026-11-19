/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.embabel.smith.builder.core.AgentSpecGenerator;
import com.embabel.smith.builder.core.BuilderAgent;
import com.embabel.smith.builder.core.CandidateShape;
import com.embabel.smith.builder.core.CompiledDraft;
import com.embabel.smith.builder.core.ProcessDraft;
import com.embabel.smith.builder.core.ProcessSpecGenerator;
import com.embabel.smith.builder.eval.EvalTask;
import com.embabel.smith.builder.eval.InvokerFactory;
import com.embabel.smith.builder.eval.ProcessInvokerFactory;
import com.embabel.smith.builder.eval.PromptRewriter;
import com.embabel.smith.builder.publish.PublishTarget;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.ProcessSpec;
import com.embabel.smith.common.model.PropertyDef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Offline tests for the dual-candidate build: both candidates are generated and scored,
 * the better one is recommended and published, both are stored, and a process that cannot
 * be made sound leaves the single-agent spec standing on its own. The model is replaced
 * at its two seams — the generators and the invokers — so the whole decision is
 * verifiable with no endpoint configured.
 *
 * @author Ivan Rodriguez
 */
class DualCandidateBuildTest {

	private static final List<EvalTask> TASKS = List.of(new EvalTask("runs", "the input", "output"));

	/** An output that satisfies the eval task above. */
	private static final String PASS = "output: fine";

	/** An output that fails it. */
	private static final String FAIL = "nothing the eval accepts";

	@TempDir
	Path tmp;

	@Test
	void theBuildGeneratesAndScoresBothCandidatesFromOneDescription() {
		AtomicInteger packCalls = new AtomicInteger();
		AtomicInteger processCalls = new AtomicInteger();
		AgentSpecGenerator packGenerator = (description) -> {
			packCalls.incrementAndGet();
			return new CompiledDraft("trading-pack", pack(), List.of());
		};
		ProcessSpecGenerator processGenerator = (description) -> {
			processCalls.incrementAndGet();
			return new ProcessDraft("trading", soundProcess(), List.of());
		};

		BuildResult result = service(packGenerator, processGenerator, PASS, PASS, scratch()).build("run the process",
				PublishTarget.parse("folder:" + library()), BuildOptions.defaults());

		assertThat(result.isBuilt()).as(() -> result.report()).isTrue();
		assertThat(packCalls).hasValue(1);
		assertThat(processCalls).hasValue(1);
		assertThat(result.recommendation().single().score()).isEqualTo(1.0);
		assertThat(result.recommendation().process().score()).isEqualTo(1.0);
	}

	@Test
	void theHigherScoringProcessIsPublishedAsOneDirectoryPerPack() throws Exception {
		BuildResult result = build(FAIL, processGenerator(DualCandidateBuildTest::soundProcess), PASS);

		assertThat(result.isBuilt()).as(() -> result.report()).isTrue();
		assertThat(result.packName()).isEqualTo("trading");
		assertThat(result.recommendation().shape()).isEqualTo(CandidateShape.MULTI_AGENT);
		Path published = Path.of(result.published().location());
		assertThat(Files.isRegularFile(published.resolve("process.yml"))).isTrue();
		assertThat(Files.isRegularFile(published.resolve("research/manifest.yml"))).isTrue();
		assertThat(Files.isRegularFile(published.resolve("research/steps/analyze_market.yml"))).isTrue();
		assertThat(Files.isRegularFile(published.resolve("decision/manifest.yml"))).isTrue();
		assertThat(Files.isRegularFile(published.resolve("lifecycle.yml"))).isTrue();
		assertThat(result.report()).contains("multi-agent process").contains("out-scored");
	}

	@Test
	void theSingleAgentSpecIsPublishedWhenTheProcessScoresLower() throws Exception {
		BuildResult result = build(PASS, processGenerator(DualCandidateBuildTest::soundProcess), FAIL);

		assertThat(result.isBuilt()).as(() -> result.report()).isTrue();
		assertThat(result.recommendation().shape()).isEqualTo(CandidateShape.SINGLE_AGENT);
		Path published = Path.of(result.published().location());
		assertThat(published.getFileName().toString()).isEqualTo("trading-pack");
		assertThat(Files.isRegularFile(published.resolve("manifest.yml"))).isTrue();
		assertThat(Files.isRegularFile(published.resolve("goal.yml"))).isTrue();
	}

	@Test
	void bothCandidatesStayOnDiskWhateverTheBuildPublishes() throws Exception {
		Path scratch = scratch();
		BuildResult result = service(packGenerator(), processGenerator(() -> soundProcess()), PASS, PASS, scratch)
			.build("run the process", PublishTarget.parse("folder:" + library()), BuildOptions.defaults());

		assertThat(result.isBuilt()).as(() -> result.report()).isTrue();
		assertThat(scratch.resolve("trading-pack")).isDirectory();
		assertThat(scratch.resolve("trading/process.yml")).isRegularFile();
	}

	@Test
	void aCycleTheReorderSettlesIsRepairedStoredAndPublished() throws Exception {
		Path scratch = scratch();
		BuildResult result = service(packGenerator(), processGenerator(DualCandidateBuildTest::wrongOrderProcess), FAIL,
				PASS, scratch)
			.build("run the process", PublishTarget.parse("folder:" + library()), BuildOptions.defaults());

		assertThat(result.isBuilt()).as(() -> result.report()).isTrue();
		assertThat(result.recommendation().shape()).isEqualTo(CandidateShape.MULTI_AGENT);
		// The repair put each producer before the agent it feeds.
		assertThat(Files.isRegularFile(Path.of(result.published().location()).resolve("research/manifest.yml")))
			.isTrue();
		assertThat(scratch.resolve("trading/process.yml")).isRegularFile();
	}

	@Test
	void aProcessThatCannotBeMadeSoundIsNeitherStoredNorPublished() throws Exception {
		Path scratch = scratch();
		BuildResult result = service(packGenerator(),
				processGenerator(DualCandidateBuildTest::mutuallyDependentProcess), PASS, PASS, scratch)
			.build("run the process", PublishTarget.parse("folder:" + library()), BuildOptions.defaults());

		assertThat(result.isBuilt()).as(() -> result.report()).isTrue();
		assertThat(result.recommendation().shape()).isEqualTo(CandidateShape.SINGLE_AGENT);
		assertThat(result.recommendation().reason()).contains("did not validate");
		assertThat(Path.of(result.published().location()).resolve("manifest.yml")).isRegularFile();
		// The unsound process never reaches the store: a stored process is one someone
		// could ingest.
		assertThat(Files.exists(scratch.resolve("trading"))).isFalse();
	}

	@Test
	void aProcessThatFailsToGenerateStillDeliversTheSingleAgentSpec() {
		ProcessSpecGenerator failing = (description) -> {
			throw new IllegalStateException("the model returned no JSON");
		};
		BuildResult result = service(packGenerator(), failing, PASS, PASS, scratch()).build("run the process",
				PublishTarget.parse("folder:" + library()), BuildOptions.defaults());

		assertThat(result.isBuilt()).as(() -> result.report()).isTrue();
		assertThat(result.recommendation().shape()).isEqualTo(CandidateShape.SINGLE_AGENT);
		assertThat(result.recommendation().reason()).contains("the model returned no JSON");
	}

	@Test
	void theScoreGateIsAppliedToTheCandidateTheBuildRecommends() throws Exception {
		// The pack never passes its eval, but the process does — and the process is what
		// the build ships, so the gate is what that has to clear.
		BuildResult result = build(FAIL, processGenerator(DualCandidateBuildTest::soundProcess), PASS);

		assertThat(result.isBuilt()).as(() -> result.report()).isTrue();
		assertThat(result.recommendation().single().score()).isZero();
		assertThat(Path.of(result.published().location()).resolve("process.yml")).isRegularFile();
	}

	@Test
	void bothCandidatesBelowTheGateBlocksTheBuildAndPublishesNothing() {
		Path library = this.tmp.resolve("library");
		BuildResult result = service(packGenerator(), processGenerator(() -> soundProcess()), FAIL, FAIL, scratch())
			.build("run the process", PublishTarget.parse("folder:" + library), BuildOptions.defaults());

		assertThat(result.isBuilt()).isFalse();
		assertThat(result.stage()).isEqualTo("eval");
		assertThat(result.issues()).anyMatch((i) -> i.contains("below the gate"));
		assertThat(Files.exists(library)).isFalse();
	}

	// --- wiring -------------------------------------------------------------

	private BuildResult build(String packOutput, ProcessSpecGenerator processGenerator, String processOutput) {
		return service(packGenerator(), processGenerator, packOutput, processOutput, scratch()).build("run the process",
				PublishTarget.parse("folder:" + library()), BuildOptions.defaults());
	}

	private BuilderService service(AgentSpecGenerator packGenerator, ProcessSpecGenerator processGenerator,
			String packOutput, String processOutput, Path scratch) {
		InvokerFactory invokers = (pack) -> (input) -> packOutput;
		ProcessInvokerFactory processInvokers = (process) -> (input) -> processOutput;
		PromptRewriter rewriter = (current, feedback) -> current;
		return new BuilderService(new BuilderAgent(packGenerator, scratch.toString()), invokers, processInvokers,
				processGenerator, rewriter, TASKS);
	}

	private static AgentSpecGenerator packGenerator() {
		return (description) -> new CompiledDraft("trading-pack", pack(), List.of());
	}

	private static ProcessSpecGenerator processGenerator(ProcessSupplier supplier) {
		return (description) -> new ProcessDraft("trading", supplier.get(), List.of());
	}

	private Path library() {
		return this.tmp.resolve("library");
	}

	private Path scratch() {
		return this.tmp.resolve("scratch");
	}

	@FunctionalInterface
	private interface ProcessSupplier {

		ProcessSpec get();

	}

	// --- candidates ---------------------------------------------------------

	private static AgentSpec pack() {
		GoalStepSpec goal = GoalStepSpec.exportedGoal("explain", "the explainer", "BackgroundMessage");
		ActionStepSpec say = ActionStepSpec.action("say", "Say it", List.of("UserInput"), "BackgroundMessage",
				"Repeat: {{userInput}}");
		return new AgentSpec(new AgentSpecManifest("trading-pack", "1.0.0"), goal, List.of(say), List.of());
	}

	private static ProcessSpec soundProcess() {
		return new ProcessSpec("trading",
				List.of(agent("research", "research_goal", "MarketReport", List.of("UserInput"), "analyze_market",
						"Analyse {{userInput}}"),
						agent("decision", "decision_goal", "FinalDecision", List.of("MarketReport"), "decide",
								"Decide on {{marketReport}}")));
	}

	/**
	 * The same process with its agents declared the wrong way round — a reorderable
	 * cycle.
	 */
	private static ProcessSpec wrongOrderProcess() {
		ProcessSpec sound = soundProcess();
		return sound.withAgents(List.of(sound.agents().get(1), sound.agents().get(0)));
	}

	/** Two agents that each need what the other produces — no order can satisfy them. */
	private static ProcessSpec mutuallyDependentProcess() {
		AgentSpec market = new AgentSpec(new AgentSpecManifest("market", "1.0.0"),
				GoalStepSpec.exportedGoal("market_goal", "the market view", "MarketReport"),
				List.of(ActionStepSpec.action("analyze", "a", List.of("UserInput"), "MarketReport",
						"Analyse {{userInput}}"),
						ActionStepSpec.action("refine", "r", List.of("RiskReport"), "MarketReport",
								"Refine with {{riskReport}}")),
				List.of(type("MarketReport"), type("RiskReport")));
		AgentSpec risk = new AgentSpec(new AgentSpecManifest("risk", "1.0.0"),
				GoalStepSpec.exportedGoal("risk_goal", "the risk view", "RiskReport"),
				List.of(ActionStepSpec.action("assess", "a", List.of("UserInput"), "RiskReport",
						"Assess {{userInput}}"),
						ActionStepSpec.action("check", "c", List.of("MarketReport"), "RiskReport",
								"Check against {{marketReport}}")),
				List.of(type("MarketReport"), type("RiskReport")));
		return new ProcessSpec("trading", List.of(market, risk));
	}

	/**
	 * A pack declaring every type it references — a pack that consumed an undeclared type
	 * would fail the structural validation the build holds each of its packs to.
	 */
	private static AgentSpec agent(String name, String goalName, String goalOutput, List<String> stepInputs,
			String stepName, String prompt) {
		List<DynamicTypeDef> declared = new ArrayList<>();
		for (String input : stepInputs) {
			if (!"UserInput".equals(input)) {
				declared.add(type(input));
			}
		}
		declared.add(type(goalOutput));
		return new AgentSpec(new AgentSpecManifest(name, "1.0.0"),
				GoalStepSpec.exportedGoal(goalName, "the " + goalName + " output", goalOutput),
				List.of(ActionStepSpec.action(stepName, "the step", stepInputs, goalOutput, prompt)), declared);
	}

	private static DynamicTypeDef type(String name) {
		return new DynamicTypeDef(name, "the " + name + " data",
				List.of(new PropertyDef("content", "string", "payload")), List.of(), true);
	}

}
