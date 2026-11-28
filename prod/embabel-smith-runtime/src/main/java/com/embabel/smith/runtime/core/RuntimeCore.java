/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
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
import com.embabel.agent.core.ToolGroupDescription;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;
import com.embabel.agent.spec.StepSpecAgentScopeBuilder;
import com.embabel.agent.spec.model.BackgroundMessage;
import com.embabel.agent.spec.yml.YmlStepSpecRepository;
import com.embabel.plan.common.condition.ConditionDetermination;
import com.embabel.plan.common.condition.ConditionStep;
import com.embabel.plan.common.condition.ConditionWorldState;
import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.model.ActionStepSpec;
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

	/**
	 * The engine a pack's skill scripts run in, or {@code null} for none. It is a
	 * constructor argument rather than a reading of the switch because an ingest runs
	 * wherever it was built — the studio and an A2A host embed this class without ever
	 * booting the CLI, and a host that wants scripts running says so by passing an engine
	 * here. {@link ScriptEngineConfig} is what turns {@code smith.tools.scripts.engine}
	 * into one.
	 */
	private final SkillScriptExecutionEngine scriptEngine;

	public RuntimeCore(AgentPlatform platform) {
		this(platform, null);
	}

	/**
	 * Over the same platform, with the engine a pack's skill scripts run in.
	 * @param platform the booted agent platform
	 * @param scriptEngine the engine this core's ingests offer scripts to, or
	 * {@code null} to leave them unoffered
	 */
	public RuntimeCore(AgentPlatform platform, SkillScriptExecutionEngine scriptEngine) {
		this.platform = platform;
		this.scriptEngine = scriptEngine;
	}

	/**
	 * Ingests every pack of the resolved source into one agent: a shared data dictionary,
	 * all goals and actions deployed together so the planner can route across packs.
	 * @param source the resolved spec source to ingest
	 * @return the ingested packs with their deployed agent
	 * @throws IllegalStateException when the source has no packs left once retired packs
	 * are excluded, or two packs export the same goal name
	 */
	public IngestedSource ingest(ResolvedSpecSource source) {
		return ingest(source, null);
	}

	/**
	 * As {@link #ingest(ResolvedSpecSource)}, running every action step through one
	 * particular LLM. The model is pinned onto the <em>staged</em> step specs, so an
	 * ingest never rewrites the source it was given.
	 * @param source the resolved spec source to ingest
	 * @param model the LLM every action step should use, or empty to leave each step on
	 * the model its own YAML names
	 * @return the ingested packs with their deployed agent
	 * @throws IllegalStateException when the source has no packs left once retired packs
	 * are excluded, or two packs export the same goal name
	 */
	public IngestedSource ingest(ResolvedSpecSource source, String model) {
		List<AgentSpec> packs = new ArrayList<>();
		List<IngestedSource.ExcludedPack> excluded = new ArrayList<>();
		List<ResolvedSpec> deployed = new ArrayList<>();
		for (ResolvedSpec resolved : source.packs()) {
			AgentSpec pack;
			try {
				pack = this.reader.read(resolved.root());
			}
			catch (IOException ex) {
				throw new IllegalStateException("Failed to read agent spec '" + resolved.name() + "' from source '"
						+ source.origin().uri() + "': " + ex.getMessage(), ex);
			}
			// A pack the lifecycle state machine retired is out of the library, so it is
			// not
			// deployed at all: left in the platform its actions stay plannable from a
			// live
			// pack's goal. No sidecar is no state recorded, which stays runnable.
			if (LifecycleSidecar.retired(resolved.root())) {
				excluded.add(new IngestedSource.ExcludedPack(pack.name(), pack.goal().name()));
			}
			else {
				packs.add(pack);
				deployed.add(resolved);
			}
		}
		if (packs.isEmpty()) {
			// A pack the validator turned away is left out of the result with a warning
			// naming it and the reason, and on this path nothing later gets to print it:
			// the run ends here. So the refusal rides on the message that ends it, which
			// is the only place a reader of a four-second exit 1 will look.
			throw new IllegalStateException("spec source '" + source.origin().uri() + "' contains no agent specs"
					+ retiredNote(excluded) + refusedNote(source.warnings()));
		}
		checkGoalNameCollisions(packs);

		DataDictionary types = this.platform
			.plus(DataDictionary.fromDomainTypes("smith-spec-types", combinedTypes(packs)));

		Path stepsDir;
		try {
			stepsDir = this.assembler.assemble(deployed.stream().map(ResolvedSpec::root).toList(),
					Files.createTempDirectory("smith-steps-"), model);
		}
		catch (IOException ex) {
			throw new IllegalStateException(
					"Failed to assemble step specs from source '" + source.origin().uri() + "': " + ex.getMessage(),
					ex);
		}

		String suffix = Long.toHexString(System.nanoTime());
		Map<String, PackReferences> referencesByStep = referencesOf(packs, deployed);
		ReferenceAwareStepSpecRepository steps = new ReferenceAwareStepSpecRepository(
				new YmlStepSpecRepository(stepsDir.toString(), List.of()), referencesByStep);
		StepSpecAgentScopeBuilder builder = new StepSpecAgentScopeBuilder("smith-spec-scope-" + suffix, this.platform,
				steps, types, List.of(), List.of());
		Agent agent = builder.deploy()
			.createAgent("smith-spec-agent-" + suffix, "spec", "agent specs from " + source.origin().uri());
		this.platform.deploy(agent);
		// Read after the deploy: the scope builder is what asks the repository for its
		// steps, so a declaration that resolved to nothing is only known once it has.
		return new IngestedSource(source, packs, excluded, agent, report(source.warnings(), referencesByStep,
				steps.attached(), steps.unresolved(), steps.scriptsOffered(), unresolvedRoles(packs)));
	}

	/**
	 * The references each step can be offered, keyed by step name so a declaration
	 * resolves against the definition that wrote it and not against whatever else shares
	 * the source.
	 *
	 * <p>
	 * A step's {@code references} entry is answered by whatever its definition carries
	 * under that name, and a skill folder is only one of the four things that can hold
	 * it: {@link PackReferences} also reads the pack's {@code references.yml}, so a step
	 * declaring a document and a step declaring a skill go through this one seam and are
	 * resolved by the same lookup. Reading only the skill folders would answer every
	 * single-reference step correctly and drop a non-skill reference from a step that
	 * declares two, which is the mistake this shape cannot make.
	 * <p>
	 * The key is the step's name because that is the only handle the step repository
	 * offers back; a name written by two packs resolves to the first of them, which is
	 * the same order the source lists its packs in.
	 * @param packs the packs being deployed, in source order
	 * @param deployed their directories, in the same order
	 * @return step name to the references of the definition that owns it
	 */
	private Map<String, PackReferences> referencesOf(List<AgentSpec> packs, List<ResolvedSpec> deployed) {
		Map<String, PackReferences> byStep = new LinkedHashMap<>();
		for (int i = 0; i < packs.size(); i++) {
			AgentSpec pack = packs.get(i);
			PackReferences references = PackReferences.at(deployed.get(i).root(), pack.name(), Set.of(),
					this.scriptEngine);
			for (ActionStepSpec step : pack.steps()) {
				byStep.putIfAbsent(step.name(), references);
			}
		}
		return byStep;
	}

	/**
	 * What the ingest saw: the definitions the source dropped before handing them over,
	 * the references it could not construct, what it attached to each step, the
	 * declarations that resolved to nothing, and the script state in force.
	 * @param dropped what the source itself refused to resolve, which it reports as
	 * warnings rather than as an error — a definition it dropped is stated first, because
	 * a run that quietly lost two of them otherwise reads as a run that had them
	 * @param referencesByStep the per-step reference sets, whose notes name refused
	 * folders and refused entries
	 * @param attached what each step was attached to, as read from the wrapper
	 * @param unresolved the declarations that resolved to nothing, as read from the
	 * wrapper
	 * @param scriptOffer which step was handed a script it could call, as read from the
	 * wrapper, which is the per-step half of a statement the pack-level line makes only
	 * once
	 * @param unresolvedRoles the tool-group roles a step declares that this platform
	 * answers with nothing, stated per declaring step
	 * @return the report to print with the run
	 */
	private RunReport report(List<String> dropped, Map<String, PackReferences> referencesByStep, List<String> attached,
			List<String> unresolved, List<String> scriptOffer, List<String> unresolvedRoles) {
		List<String> lines = new ArrayList<>(dropped);
		for (PackReferences references : referencesByStep.values().stream().distinct().toList()) {
			lines.addAll(references.notes());
		}
		lines.addAll(attached);
		lines.addAll(scriptOffer);
		lines.addAll(unresolved);
		lines.addAll(unresolvedRoles);
		return new RunReport(lines);
	}

	/**
	 * The roles a step declares that nothing answers on this platform.
	 *
	 * <p>
	 * <b>Why the run has to say it.</b> A declared role is a pass-through: nothing reads
	 * the name before the prompt asks the resolver for it, and a name nothing answers
	 * leaves the step with an empty tool loop — which looks exactly like a model that
	 * chose not to use a tool, and the two call for different fixes. The addon's boot
	 * line accounts for the roles that addon owns; a name that is nobody's, a typo among
	 * them, is in no addon's account, so this is the line that names it.
	 *
	 * <p>
	 * <b>Computed against what resolves, not against a list of names.</b> The comparison
	 * is the resolver's own {@code availableToolGroups()}, so a role this project's addon
	 * shadows — {@code file}, which upstream also builds from its includes map — reads as
	 * resolving, because it does. The reason is the addon's phrasing,
	 * {@code role: reason}, rather than a new one; what a switch took away is stated by
	 * the addon's boot line, which knows the property that decided it, and this line says
	 * what it knows: nothing answers the name.
	 * @param packs the packs being deployed
	 * @return one line per step declaring a role nothing answers, in source order
	 */
	private List<String> unresolvedRoles(List<AgentSpec> packs) {
		List<String> available = this.platform.getToolGroupResolver()
			.availableToolGroups()
			.stream()
			.map(ToolGroupDescription::getRole)
			.toList();
		List<String> lines = new ArrayList<>();
		for (AgentSpec pack : packs) {
			for (ActionStepSpec step : pack.steps()) {
				List<String> unanswered = step.toolGroups()
					.stream()
					.filter((role) -> !available.contains(role))
					.toList();
				if (!unanswered.isEmpty()) {
					String line = "step '" + step.name() + "' declares " + unanswered + ", registered nothing from "
							+ unanswered.stream().map((role) -> role + ": no tool group answers the role").toList()
							+ "; the roles this platform resolves are " + available;
					if (!lines.contains(line)) {
						lines.add(line);
					}
				}
			}
		}
		return lines;
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
	 * @throws LifecycleBlockedException when the goal was exported only by a pack retired
	 * out of the library
	 * @throws TrustTierBlockedException when a pack declares tool groups the tier does
	 * not allow
	 */
	public RunOutcome run(IngestedSource ingested, String goalName, String input, String plannerOverride,
			TrustTier tier) {
		checkLifecycle(ingested, goalName);
		AgentSpec goalPack = ingested.packForGoal(goalName);
		checkTier(ingested.packs(), tier);
		PlannerResolver.Decision choice = this.plannerResolver.decide(plannerOverride, goalPack);
		PlannerType planner = choice.planner();

		Goal goal = findGoal(ingested.agent(), goalName);
		Agent goalAgent = ingested.agent().withSingleGoal(goal);
		this.platform.deploy(goalAgent);

		try {
			AgentProcess process = this.platform.runAgentFrom(goalAgent,
					ProcessOptions.DEFAULT.withPlannerType(planner), Map.of("userInput", new UserInput(input)));
			return outcome(process, goal, planner, choice);
		}
		catch (RuntimeException ex) {
			return new RunOutcome(null, false, null, "Goal '" + goalName + "' run aborted: " + ex.getMessage(),
					planner.name(), choice);
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

	/**
	 * The goal a retirement took away, named as the policy block it is rather than left
	 * to read as a goal nobody ever exported. A goal no pack exported still falls through
	 * to {@link GoalNotFoundException}, and a live pack that exports the goal wins.
	 * @param ingested the ingested packs with their exclusions
	 * @param goalName the goal the run asked for
	 */
	private void checkLifecycle(IngestedSource ingested, String goalName) {
		if (!ingested.goalNames().contains(goalName)) {
			ingested.excludedPackForGoal(goalName).ifPresent((excluded) -> {
				throw new LifecycleBlockedException(excluded.name(), goalName);
			});
		}
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

	/**
	 * Names the packs an ingest excluded as retired, so the report that says nothing was
	 * deployed also says which packs it left out.
	 * @param excluded the packs left out of the source
	 * @return the exclusion note, or empty when nothing was excluded
	 */
	private static String retiredNote(List<IngestedSource.ExcludedPack> excluded) {
		return excluded.isEmpty() ? ""
				: " (excluded as retired: " + excluded.stream().map(IngestedSource.ExcludedPack::name).toList() + ")";
	}

	/**
	 * What a source refused rather than resolved. A pack dropped at validation is not in
	 * the result, so the run report built for a resolved source never runs and its naming
	 * of refused declarations never prints; this is the same information on the only line
	 * that survives an ingest that resolves to nothing.
	 * @param warnings the warnings the source reported alongside its empty result
	 * @return the note, or the empty string when the source refused nothing
	 */
	private static String refusedNote(List<String> warnings) {
		return warnings.isEmpty() ? "" : " (refused by the source: " + String.join("; ", warnings) + ")";
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

	private RunOutcome outcome(AgentProcess process, Goal goal, PlannerType planner, PlannerResolver.Decision choice) {
		if (process.getStatus() == AgentProcessStatusCode.COMPLETED) {
			Object last = process.lastResult();
			if (last != null) {
				String output = (last instanceof BackgroundMessage message) ? message.getContent()
						: String.valueOf(last);
				return new RunOutcome(process, true, output, null, planner.name(), choice);
			}
		}
		return new RunOutcome(process, false, null, failureMessage(process, goal), planner.name(), choice);
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
