/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.eval;

import com.embabel.agent.core.Action;
import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentScope;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.core.Goal;
import com.embabel.smith.builder.core.BuilderPackPaths;
import com.embabel.smith.builder.improve.Refiner;
import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.ProcessSpec;
import com.embabel.smith.common.trace.TraceRecord;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Falsifier for the claim that POC 27's improvement loop improves the builder's
 * <em>collaboration structure</em>. It does not: the loop's operator rewrites step
 * prompts, and neither the agent order inside a process nor a step's planner conditions
 * is on the surface it can reach. A future reader who writes "the loop improves
 * collaboration structure" should hit this file, and this file should fail them.
 *
 * <p>
 * <b>The two halves, and where each is decided.</b> Ordering lives in
 * {@code common/model/ProcessSpec} — {@code agents()} is an ordered list and
 * {@code withAgents(List)} at {@code :100} is the only method that returns a process with
 * a different one. Conditions live in
 * {@code common/model/ActionStepSpec.pre}/{@code .post}. The mutation operator lives in
 * {@code improve/Refiner}, whose {@code refine} takes an {@link AgentSpec} and returns a
 * candidate built by {@code withPrompt} — a copy that names every one of the step's other
 * twelve fields, {@code pre} and {@code post} among them, and changes only the prompt. So
 * the operator's <em>signature</em> is already part of the proof: it is handed a pack,
 * and a pack has no agent order to reorder.
 *
 * <p>
 * <b>Where the shipped deploy reads the order.</b> The order is not decorative at deploy:
 * {@code AgentSpecInvokerFactory.entryGoalName} takes the <em>last</em> agent's goal as
 * the goal the run has to deliver, and narrows the deployed agent to it. That is the
 * observable the ordering tests below use, because it is the one place a reordered
 * process provably deploys differently. {@code withAgents} is what moves it — the control
 * test reorders through it and watches the entry goal move, which is what makes the
 * survival assertions non-vacuous rather than a tautology about an untouched object.
 *
 * <p>
 * <b>What the deployed scope does <em>not</em> carry, and why no ordering assertion is
 * made on it.</b> The assembled step directory names each file
 * {@code <packIndex>-<stepFile>.yml} ({@code eval/StepSpecAssembler}), so the pack index
 * is written to disk — but nothing reads it back into an ordering. The repository
 * {@code AgentSpecInvokerFactory} hands the scope builder is
 * {@code YmlStepSpecRepository}, whose {@code findAll} iterates an unsorted
 * {@code File.listFiles()} (read from the 1.5.4 bytecode: {@code listFiles} then a
 * per-file {@code readValue}, no {@code sortedBy} anywhere). So the scope's action list
 * arrives in filesystem order, which is unspecified by the JDK. Asserting that it groups
 * by pack would be a test that passes on one filesystem and fails on another, and it is
 * deliberately not asserted. What the scope is claimed to carry is its <em>content</em>:
 * every step and every goal of all three packs.
 *
 * <p>
 * <b>Why {@code pre} and {@code post} being off the operator matters.</b>
 * {@code smith-common}'s {@code validation/AgentSpecValidator} inspects the exported-goal
 * flag, type references and prompt variables, and never reads {@code pre} or
 * {@code post}; the spec source resolves no condition names either. An operator that
 * rewrote a condition name would therefore sail through validation and fail at
 * <em>deploy</em> — a silent, expensive break rather than a rejected candidate. That is
 * the same defect class as {@link ToolGroupBindingTest}'s misspelled tool group and the
 * unnamed prompt variable that cost POC 27 its run, with the difference that the prompt
 * variable at least acquired a validator check.
 *
 * <p>
 * <b>What this cannot catch.</b> It pins ordering through the operator that ships, not
 * through a hypothetical one: if a future operator takes a {@link ProcessSpec} instead of
 * a pack, or a future {@code Refiner} rebuilds steps from a fresh record rather than
 * copying the old one, nothing here fails — the control test is what would then read as
 * "ordering is reachable", which is the point of running it. It also cannot say the loop
 * <em>improves</em> anything: it says only what the loop is incapable of changing. And
 * the shipped builder packs declare {@code pre: []} and {@code post: []} on all twelve
 * steps, so the condition assertion on those packs is an absence; the non-vacuous case is
 * the synthetic pack below, which names conditions.
 *
 * <p>
 * <b>The platform double</b> is {@link ToolGroupBindingTest}'s, so every class under
 * examination — {@link AgentSpecInvokerFactory}, {@link StepSpecAssembler},
 * {@code AgentSpecTypeMapper}, Embabel's {@code StepSpecAgentScopeBuilder} and
 * {@code Agent.withSingleGoal} — runs unmocked. Two bytecode facts make it faithful:
 * {@code StepSpecAgentScopeBuilder.deploy()} pops the platform's return value and returns
 * the scope it built itself, so {@code createAgent} runs as the real default method on a
 * real scope; and {@code AgentPlatform.deploy(Agent)} is a map insertion plus an event,
 * so narrowing and deploying need no live planner. Nothing here invokes the returned
 * invoker — that would be a model call — so the whole class is offline and carries no
 * {@code live} tag.
 *
 * @author Ivan Rodriguez
 */
class CollaborationStructureIsFixedByConstructionTest {

	/**
	 * The builder's own packs, in the order the change's process lists them, addressed as
	 * {@code core/BuilderAgentSpecPackTest} addresses them: through the class loader, at
	 * this module's {@code specs/} resources, so the process is assembled from the packs
	 * the module ships rather than from whatever directory the test run started in.
	 */
	private static final List<String> PACK_NAMES = List.of("builder-single-spec", "builder-process-composition",
			"builder-skill-from-process");

	/**
	 * Marks a prompt the rewriter touched, so "the prompts did change" is checkable
	 * rather than assumed. Answered by the two-argument half of {@link PromptRewriter};
	 * {@code Refiner} calls the three-argument overload, whose default delegates here.
	 */
	private static final String REWRITTEN = "[rewritten] ";

	private static Path packDir(String packName) {
		return BuilderPackPaths.directory(packName);
	}

	private static AgentSpec load(String packName) throws IOException {
		Path dir = packDir(packName);
		assertThat(dir.resolve("manifest.yml")).as("pack directory %s exists on disk", dir).isRegularFile();
		return new AgentSpecReader().read(dir);
	}

	/**
	 * The three builder packs as one process, in {@link #PACK_NAMES} order — the artifact
	 * the assertions are about.
	 */
	private static ProcessSpec builderProcess() throws IOException {
		List<AgentSpec> packs = new ArrayList<>();
		for (String packName : PACK_NAMES) {
			packs.add(load(packName));
		}
		return new ProcessSpec("builder-improvement", packs);
	}

	/**
	 * The candidate the improvement loop produces for a process: every pack through
	 * {@link Refiner} with a rewriter that changes every prompt it is asked about, and
	 * every step of every pack marked {@code failed} in the traces so the operator is
	 * given the maximum amount to do. {@code improve/} refines one pack at a time —
	 * {@code ImprovementLoop} calls {@code refine(current, traces, model)} per pack — and
	 * no shipped code rebuilds a process from refined packs, so the reconstruction here
	 * is the minimal faithful one: the same packs, the same order, the parent's manifest.
	 */
	private static ProcessSpec mutated(ProcessSpec parent) {
		Refiner refiner = new Refiner((prompt, feedback) -> REWRITTEN + prompt);
		List<AgentSpec> candidates = new ArrayList<>();
		for (AgentSpec pack : parent.agents()) {
			candidates.add(refiner.refine(pack, failedTraces(pack), "test-model").candidate());
		}
		return new ProcessSpec(parent.manifest(), candidates);
	}

	/**
	 * One failed run whose trace implicates every step of the pack, which is what asks
	 * the rewriter for all of them.
	 */
	private static List<TraceRecord> failedTraces(AgentSpec pack) {
		List<TraceRecord.StepRecord> steps = pack.steps()
			.stream()
			.map((step) -> new TraceRecord.StepRecord(step.name(), "failed", 12L, "not JSON"))
			.toList();
		return List.of(new TraceRecord("run-1", Instant.now(), null, null, pack.goal().name(),
				"Describe the process of drafting an agent spec", "clef", "standard", steps, false,
				"the goal output was not parseable JSON", null));
	}

	/**
	 * Deploys a process through the production {@link AgentSpecInvokerFactory} against a
	 * mock platform and reports what the platform was handed: the scope the step-spec
	 * builder deployed, and the agent the factory narrowed and deployed. The returned
	 * invoker is never called.
	 */
	private static Deployment deploy(ProcessSpec process) {
		AgentPlatform platform = mock(AgentPlatform.class);
		// The factory widens the platform with the packs' types; a double that
		// contributes
		// no types of its own returns its argument, which is the dictionary the real path
		// would have compiled with.
		when(platform.plus(any(DataDictionary.class))).thenAnswer((invocation) -> invocation.getArgument(0));
		List<AgentScope> scopes = new ArrayList<>();
		when(platform.deploy(any(AgentScope.class))).thenAnswer((invocation) -> {
			scopes.add(invocation.getArgument(0, AgentScope.class));
			return platform;
		});
		List<Agent> agents = new ArrayList<>();
		when(platform.deploy(any(Agent.class))).thenAnswer((invocation) -> {
			agents.add(invocation.getArgument(0, Agent.class));
			return platform;
		});
		new AgentSpecInvokerFactory(platform).invokerFor(process);
		return new Deployment(scopes, agents);
	}

	/**
	 * What a deploy put on the platform. {@code scopes} and {@code agents} are the two
	 * artifacts, in call order; the derived views read the single scope's content and the
	 * single narrowed agent's goal.
	 */
	private record Deployment(List<AgentScope> scopes, List<Agent> agents) {

		private List<String> actionNames() {
			assertThat(this.scopes).as("one scope was deployed").hasSize(1);
			return this.scopes.get(0).getActions().stream().map(Action::getName).toList();
		}

		private List<String> scopeGoalNames() {
			assertThat(this.scopes).as("one scope was deployed").hasSize(1);
			return this.scopes.get(0).getGoals().stream().map(Goal::getName).toList();
		}

		private List<String> entryGoalNames() {
			assertThat(this.agents).as("one narrowed agent was deployed").hasSize(1);
			return this.agents.get(0).getGoals().stream().map(Goal::getName).toList();
		}

		private String entryGoal() {
			List<String> goals = entryGoalNames();
			assertThat(goals).as("the deployed agent is narrowed to the single goal the run delivers").hasSize(1);
			return goals.get(0);
		}
	}

	private static List<String> agentNamesOf(ProcessSpec process) {
		return process.agents().stream().map(AgentSpec::name).toList();
	}

	private static Map<String, String> promptsOf(AgentSpec pack) {
		Map<String, String> prompts = new LinkedHashMap<>();
		for (ActionStepSpec step : pack.steps()) {
			prompts.put(step.name(), step.prompt());
		}
		return prompts;
	}

	/**
	 * Each step's planner conditions as {@code [pre, post]} — the pair the operator must
	 * not touch. {@code ActionStepSpec}'s canonical constructor normalizes a null to an
	 * empty list, so these are never null.
	 */
	private static Map<String, List<List<String>>> conditionsOf(AgentSpec pack) {
		Map<String, List<List<String>>> conditions = new LinkedHashMap<>();
		for (ActionStepSpec step : pack.steps()) {
			conditions.put(step.name(), List.of(step.pre(), step.post()));
		}
		return conditions;
	}

	/**
	 * A pack whose steps <em>name</em> planner conditions. The shipped builder packs all
	 * declare {@code pre: []} and {@code post: []}, so on their own they could not tell
	 * an operator that preserves conditions from one that blanks them; this is the
	 * control that makes the condition assertion an assertion.
	 */
	private static AgentSpec packNamingConditions() {
		ActionStepSpec step = new ActionStepSpec(ActionStepSpec.STEP_TYPE, "assemble", "Assemble the spec",
				List.of("ProcessScope"), "AgentSpecJson", "Assemble from {{processScope.content}}",
				List.of("planScopeDeclared"), List.of("jsonWellFormed"), null, null, null, null, List.of());
		return new AgentSpec(new AgentSpecManifest("condition-probe", "1.0.0"),
				GoalStepSpec.exportedGoal("specDrafted", "A spec was drafted", "AgentSpecJson"), List.of(step),
				List.of());
	}

	/**
	 * The three packs deploy as one scope rather than three agents, and that scope holds
	 * every step and every goal of all three — which is what lets one plan chain across
	 * the hand-off types. A step the compiler dropped, or a pack that arrived as its own
	 * agent, breaks this.
	 */
	@Test
	void theThreeBuilderPacksDeployAsASingleScopeHoldingEveryStepAndGoalOfAllThree() throws IOException {
		ProcessSpec process = builderProcess();
		Deployment deployed = deploy(process);

		assertThat(deployed.scopes()).as("three packs, one scope — not one agent per pack").hasSize(1);

		List<String> stepNames = new ArrayList<>();
		List<String> goalNames = new ArrayList<>();
		for (AgentSpec pack : process.agents()) {
			pack.steps().forEach((step) -> stepNames.add(step.name()));
			goalNames.add(pack.goal().name());
		}
		assertThat(stepNames).as("the three packs share no step name").doesNotHaveDuplicates();
		assertThat(deployed.actionNames()).as("every step of all three packs compiled into the one scope")
			.containsExactlyInAnyOrderElementsOf(stepNames);
		assertThat(goalNames).as("the three packs export distinct goals").doesNotHaveDuplicates();
		assertThat(deployed.scopeGoalNames()).as("every pack's goal is callable from the one scope")
			.containsExactlyInAnyOrderElementsOf(goalNames);
	}

	/**
	 * The loop's operator cannot reorder a process. The candidate's agent list is the
	 * parent's, in order, and the goal the deploy runs — the last agent's, chosen by
	 * {@code entryGoalName} — is the parent's too. The mutation is real, not a no-op:
	 * every step prompt in the candidate carries the rewriter's mark, so the order
	 * survived an operator that was actually given work to do.
	 */
	@Test
	void theImprovementOperatorLeavesTheAgentOrderAndTheGoalTheDeployRunsUnchanged() throws IOException {
		ProcessSpec parent = builderProcess();
		ProcessSpec candidate = mutated(parent);

		assertThat(agentNamesOf(candidate)).as("candidate agent order, after every pack was refined")
			.isEqualTo(agentNamesOf(parent));
		assertThat(candidate.agents()).as("no pack was dropped or duplicated").hasSameSizeAs(parent.agents());

		Deployment parentDeployment = deploy(parent);
		Deployment candidateDeployment = deploy(candidate);
		assertThat(candidateDeployment.entryGoal())
			.as("the entry goal is derived from the last pack, so it moves " + "with the order — and it did not move")
			.isEqualTo(parentDeployment.entryGoal());
		assertThat(parentDeployment.entryGoal())
			.as("the entry goal the deploy runs is the last pack's own goal, which is why it is order-derived")
			.isEqualTo(load(PACK_NAMES.get(PACK_NAMES.size() - 1)).goal().name());
		assertEveryPromptRewritten(parent, candidate);
	}

	/**
	 * The control that keeps the ordering claim from being a tautology. Reordering
	 * through {@code withAgents} — the only method in the model that changes it, and the
	 * one neither {@code improve/} nor {@code eval/} calls — does move the goal the
	 * deploy runs. So the survival asserted above is a statement about a property the
	 * deploy would have shown had it changed, not about a property nothing could ever
	 * observe.
	 */
	@Test
	void reorderingTheProcessThroughWithAgentsIsWhatMovesTheDeployedEntryGoal() throws IOException {
		ProcessSpec parent = builderProcess();
		List<AgentSpec> reversed = new ArrayList<>(parent.agents());
		Collections.reverse(reversed);
		ProcessSpec reordered = parent.withAgents(reversed);

		assertThat(agentNamesOf(reordered)).as("withAgents really did reorder the process")
			.isEqualTo(agentNamesOf(parent).reversed());

		assertThat(deploy(reordered).entryGoal()).as("the deploy reads the order, so a reorder is observable")
			.isNotEqualTo(deploy(parent).entryGoal());
	}

	/**
	 * A step's {@code pre} and {@code post} are outside the operator: after the rewrite
	 * they are element-for-element what the parent said, while every prompt did change.
	 * The synthetic pack carries the claim (its conditions are named); the three shipped
	 * packs carry the absence (they declare none, so there is nothing for an operator to
	 * lose there — and nothing it can lose). An operator that edited a condition name
	 * would not be caught by {@code AgentSpecValidator}, which never reads either field,
	 * but at deploy.
	 */
	@Test
	void theOperatorRewritesPromptsAndCarriesPreAndPostThroughUnchanged() throws IOException {
		AgentSpec named = packNamingConditions();
		AgentSpec namedCandidate = mutated(new ProcessSpec("probe", List.of(named))).agents().get(0);

		assertThat(named.steps().get(0).pre()).as("control: this pack's steps do name conditions").isNotEmpty();
		assertThat(named.steps().get(0).post()).as("control: this pack's steps do name conditions").isNotEmpty();
		assertThat(conditionsOf(namedCandidate)).as("conditions of the rewritten probe pack")
			.isEqualTo(conditionsOf(named));
		assertThat(promptsOf(namedCandidate)).as("and its prompt was rewritten anyway")
			.isEqualTo(Map.of("assemble", REWRITTEN + promptsOf(named).get("assemble")));

		ProcessSpec parent = builderProcess();
		ProcessSpec candidate = mutated(parent);
		for (int i = 0; i < parent.agents().size(); i++) {
			AgentSpec before = parent.agents().get(i);
			AgentSpec after = candidate.agents().get(i);

			assertThat(conditionsOf(after)).as("pre/post of every step of %s", before.name())
				.isEqualTo(conditionsOf(before));
			assertThat(after.steps().stream().flatMap((step) -> step.pre().stream()))
				.as("%s declares no preconditions at all", before.name())
				.isEmpty();
			assertThat(after.steps().stream().flatMap((step) -> step.post().stream()))
				.as("%s declares no postconditions at all", before.name())
				.isEmpty();
		}
		assertEveryPromptRewritten(parent, candidate);
	}

	/**
	 * Premise for the claims above: every step prompt of every pack of {@code candidate}
	 * carries {@link #REWRITTEN} and differs from its parent's, so an unchanged ordering
	 * or an unchanged condition list is a survivor of work that was done, not the trace
	 * of a call that did nothing.
	 */
	private static void assertEveryPromptRewritten(ProcessSpec parent, ProcessSpec candidate) {
		assertThat(candidate.agents()).as("one candidate per pack").hasSameSizeAs(parent.agents());
		for (int i = 0; i < parent.agents().size(); i++) {
			AgentSpec before = parent.agents().get(i);
			AgentSpec after = candidate.agents().get(i);
			Map<String, String> expected = new LinkedHashMap<>();
			promptsOf(before).forEach((step, prompt) -> expected.put(step, REWRITTEN + prompt));

			assertThat(promptsOf(after))
				.as("every prompt of every step of %s was rewritten by the operator", before.name())
				.isEqualTo(expected);
		}
	}

}
