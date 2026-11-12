/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.runtime.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.embabel.agent.api.common.PlannerType;
import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.core.DomainType;
import com.embabel.agent.core.DynamicType;
import com.embabel.agent.core.Goal;
import com.embabel.agent.core.JvmType;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.spec.StepSpecAgentScopeBuilder;
import com.embabel.agent.spec.model.BackgroundMessage;
import com.embabel.agent.spec.yml.YmlStepSpecRepository;
import com.embabel.plan.common.condition.ConditionDetermination;
import com.embabel.plan.common.condition.ConditionStep;
import com.embabel.plan.common.condition.ConditionWorldState;
import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.specsource.ResolvedSpec;
import com.embabel.smith.specsource.ResolvedSpecSource;

/**
 * The product runtime core (extracted from POC 1, generalized to multi-pack per POC 6):
 * ingests the agent specs of a resolved source into one platform and runs an exported
 * goal over them, with per-run planner selection and trust-tier enforcement (POC 10).
 *
 * <p>
 * Everything executed here is <em>data</em> (the agent spec YAML) — ingestion uses
 * {@link YmlStepSpecRepository} + {@link StepSpecAgentScopeBuilder#deploy()}; planning
 * uses a non-model planner (default GOAP) so the step sequence is reproducible and only
 * the action bodies invoke the model.
 *
 * @author Ivan Rodriguez
 */
public class RuntimeCore {

	private final AgentPlatform platform;

	private final AgentSpecReader reader = new AgentSpecReader();

	private final AgentSpecTypeMapper typeMapper = new AgentSpecTypeMapper();

	private final StepSpecAssembler assembler = new StepSpecAssembler();

	private final PlannerResolver plannerResolver = new PlannerResolver();

	private final TrustTierPolicy tierPolicy = new TrustTierPolicy();

	public RuntimeCore(AgentPlatform platform) {
		this.platform = platform;
	}

	/**
	 * Ingests every pack of the resolved source into one agent: a shared data dictionary,
	 * all goals and actions deployed together so the planner can route across packs.
	 * @param source the resolved spec source to ingest
	 * @return the ingested packs with their deployed agent
	 * @throws IllegalStateException when the source has no packs or two packs export the
	 * same goal name
	 */
	public IngestedSource ingest(ResolvedSpecSource source) {
		List<AgentSpec> packs = new ArrayList<>();
		for (ResolvedSpec resolved : source.packs()) {
			try {
				packs.add(this.reader.read(resolved.root()));
			}
			catch (IOException ex) {
				throw new IllegalStateException("Failed to read agent spec '" + resolved.name() + "' from source '"
						+ source.origin().uri() + "': " + ex.getMessage(), ex);
			}
		}
		if (packs.isEmpty()) {
			throw new IllegalStateException("spec source '" + source.origin().uri() + "' contains no agent specs");
		}
		checkGoalNameCollisions(packs);

		DataDictionary types = this.platform
			.plus(DataDictionary.fromDomainTypes("smith-spec-types", combinedTypes(packs)));

		Path stepsDir;
		try {
			stepsDir = this.assembler.assemble(source.packs().stream().map(ResolvedSpec::root).toList(),
					Files.createTempDirectory("smith-steps-"));
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to assemble step specs from source '" + source.origin().uri()
					+ "': " + ex.getMessage(), ex);
		}

		String suffix = Long.toHexString(System.nanoTime());
		StepSpecAgentScopeBuilder builder = new StepSpecAgentScopeBuilder("smith-spec-scope-" + suffix, this.platform,
				new YmlStepSpecRepository(stepsDir.toString(), List.of()), types, List.of(), List.of());
		Agent agent = builder.deploy()
			.createAgent("smith-spec-agent-" + suffix, "spec", "agent specs from " + source.origin().uri());
		this.platform.deploy(agent);
		return new IngestedSource(source, packs, agent);
	}

	/**
	 * Runs the named goal over the ingested packs, binding {@code input} to the
	 * {@code UserInput} type. The trust tier is enforced before execution; the planner
	 * defaults to GOAP.
	 *
	 * <p>
	 * The platform rethrows action errors to the caller instead of recording them on the
	 * process, so a model-call failure (e.g. an unreachable endpoint) is converted here
	 * into a failed outcome rather than propagating out of the run.
	 * @param ingested the ingested packs with their deployed agent
	 * @param goalName the goal to run
	 * @param input the input bound to the goal's {@code UserInput}
	 * @param plannerOverride an explicit planner name, or {@code null} for the default
	 * @param tier the trust tier the run executes under
	 * @return the run outcome
	 * @throws GoalNotFoundException when no pack exports the goal (error lists available
	 * goals)
	 * @throws TrustTierBlockedException when a pack declares tool groups the tier does
	 * not allow
	 */
	public RunOutcome run(IngestedSource ingested, String goalName, String input, String plannerOverride,
			TrustTier tier) {
		AgentSpec goalPack = ingested.packForGoal(goalName);
		checkTier(ingested.packs(), tier);
		PlannerType planner = this.plannerResolver.resolve(plannerOverride, goalPack);

		Goal goal = findGoal(ingested.agent(), goalName);
		Agent goalAgent = ingested.agent().withSingleGoal(goal);
		this.platform.deploy(goalAgent);

		try {
			AgentProcess process = this.platform
				.runAgentFrom(goalAgent, ProcessOptions.DEFAULT.withPlannerType(planner),
						Map.of("userInput", new UserInput(input)));
			return outcome(process, goal, planner);
		}
		catch (RuntimeException ex) {
			return new RunOutcome(null, false, null, "Goal '" + goalName + "' run aborted: " + ex.getMessage(),
					planner.name());
		}
	}

	/**
	 * The domain types the run needs: the built-in I/O types plus every pack's
	 * intermediate types, deduplicated by name — the shared type is the cross-pack
	 * composition's glue (POC 6).
	 * @param packs the ingested agent specs
	 * @return the combined domain types
	 */
	public List<DomainType> combinedTypes(List<AgentSpec> packs) {
		List<DomainType> types = new ArrayList<>();
		types.add(new JvmType(UserInput.class));
		types.add(new JvmType(BackgroundMessage.class));
		Set<String> seen = new HashSet<>();
		for (AgentSpec pack : packs) {
			for (DynamicType type : this.typeMapper.toDynamicTypes(pack)) {
				if (seen.add(type.getName())) {
					types.add(type);
				}
			}
		}
		return types;
	}

	private void checkGoalNameCollisions(List<AgentSpec> packs) {
		Map<String, List<String>> goalToPacks = new LinkedHashMap<>();
		for (AgentSpec pack : packs) {
			goalToPacks.computeIfAbsent(pack.goal().name(), (key) -> new ArrayList<>()).add(pack.name());
		}
		for (Map.Entry<String, List<String>> entry : goalToPacks.entrySet()) {
			if (entry.getValue().size() > 1) {
				throw new IllegalStateException(
						"duplicate goal name '" + entry.getKey() + "' is exported by multiple packs: "
								+ entry.getValue() + "; a source cannot ingest them unambiguously");
			}
		}
	}

	private void checkTier(List<AgentSpec> packs, TrustTier tier) {
		List<TrustTierPolicy.Violation> violations = this.tierPolicy.violations(packs, tier);
		if (!violations.isEmpty()) {
			throw new TrustTierBlockedException(tier, violations);
		}
	}

	private Goal findGoal(Agent agent, String name) {
		return agent.getGoals()
			.stream()
			.filter((goal) -> name.equals(goal.getName()))
			.findFirst()
			.orElseThrow(
					() -> new IllegalStateException("goal '" + name + "' is not present in the deployed agent; goals: "
							+ agent.getGoals().stream().map(Goal::getName).toList()));
	}

	private RunOutcome outcome(AgentProcess process, Goal goal, PlannerType planner) {
		if (process.getStatus() == AgentProcessStatusCode.COMPLETED) {
			Object last = process.lastResult();
			if (last != null) {
				String output = (last instanceof BackgroundMessage message) ? message.getContent()
						: String.valueOf(last);
				return new RunOutcome(process, true, output, null, planner.name());
			}
		}
		return new RunOutcome(process, false, null, failureMessage(process, goal), planner.name());
	}

	private String failureMessage(AgentProcess process, Goal goal) {
		String status = process.getStatus().name();
		List<String> unmet = unmetPreconditions(process, goal);
		if (unmet != null) {
			return "Goal '" + goal.getName() + "' not achieved (status=" + status + "): unmet precondition(s) " + unmet
					+ ". If the input does not satisfy the spec's preconditions, the plan cannot reach the goal.";
		}
		StringBuilder message = new StringBuilder("Goal '").append(goal.getName())
			.append("' not achieved (status=")
			.append(status)
			.append(")");
		if (process.getFailureInfo() != null) {
			message.append(" Failure info: ").append(process.getFailureInfo());
		}
		message.append(" If the input does not satisfy the spec's preconditions, the plan cannot reach the goal.");
		return message.toString();
	}

	/**
	 * The goal's precondition conditions that are not TRUE in the final world state, if
	 * readable.
	 * @param process the executed agent process
	 * @param goal the goal whose preconditions are checked
	 * @return the unmet precondition names, or {@code null} when not readable
	 */
	private List<String> unmetPreconditions(AgentProcess process, Goal goal) {
		if (!(process.getLastWorldState() instanceof ConditionWorldState worldState)
				|| !(goal instanceof ConditionStep)) {
			return null;
		}
		List<String> unmet = new ArrayList<>();
		for (String precondition : ((ConditionStep) goal).getPreconditions().keySet()) {
			if (worldState.getState().get(precondition) != ConditionDetermination.TRUE) {
				unmet.add(precondition);
			}
		}
		return unmet.isEmpty() ? null : unmet;
	}

}
