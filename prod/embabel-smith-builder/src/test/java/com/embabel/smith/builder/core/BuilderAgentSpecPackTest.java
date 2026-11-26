/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

import com.embabel.agent.core.Action;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentScope;
import com.embabel.agent.core.DataDictionary;
import com.embabel.smith.builder.eval.AgentSpecInvokerFactory;
import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.validation.AgentSpecValidator;
import com.embabel.smith.common.validation.ValidationResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The five builder packs under {@code specs/} are loadable, valid and deployable, so they
 * are capabilities rather than a directory nobody reads.
 *
 * <p>
 * <b>Why the deploy is the assertion that matters.</b> The builder's flow is reachable
 * today only through Java — {@code core/BuilderAgent} and
 * {@code core/SingleShotAgentsPackGenerator} — so a pack that merely parses would be an
 * artifact no code loads, which is the exact gap this change exists to close. Each pack
 * is therefore pushed all the way through the production path: {@link AgentSpecReader}
 * reads the checked-in directory, {@link AgentSpecValidator} checks it, and
 * {@link AgentSpecInvokerFactory#invokerFor(AgentSpec)} assembles, compiles and deploys
 * it. That last step is what proves the pack is expressed in terms Embabel's
 * {@code StepSpecAgentScopeBuilder} can consume, and it is where a pack written for a
 * format this repository does not implement would fail.
 *
 * <p>
 * <b>What a clean validation does and does not say.</b>
 * {@link AgentSpecValidator#validate(AgentSpec)} checks the exported-goal flag, that
 * every type name the goal and steps use is declared in the pack's own {@code types/},
 * that every {@code {{var}}} in a step prompt is bound by a declared input of that step,
 * that every such variable names a property its type declares, and that a step naming
 * inputs interpolates at least one of them. It does <em>not</em> read {@code toolGroups},
 * and it does not resolve {@code pre} or {@code post} condition names — a name resolving
 * to nothing is indistinguishable from one resolving correctly, the same defect class as
 * {@code eval/ToolGroupBindingTest}'s misspelled group. So a green run here is a
 * statement about layout, references and prompts reaching the model; it is not a claim
 * that the packs' planner conditions or tool groups mean anything.
 *
 * <p>
 * <b>Locating the packs.</b> The packs are classpath resources under
 * {@code src/main/resources/specs}, so they are addressed through the class loader by
 * {@link BuilderPackPaths} rather than resolved against the working directory, and this
 * run does not depend on the directory it was launched from.
 * {@link BuilderPackPaths#directory(String)} hands back a real directory — the class
 * directory itself while the module's classes are exploded, a temporary copy of the
 * resource once it travels inside a jar — which is why {@link AgentSpecReader} reads a
 * pack here on the same terms it reads one from anywhere else on disk. The packs are
 * resources of the module that runs them, so nothing has to be standing in
 * {@code prod/embabel-smith-builder/} for a pack to be found. The directory assertion
 * below is what fails, naming the path it tried, if the resource ever stops resolving.
 *
 * <p>
 * <b>The platform double</b> is {@code eval/ToolGroupBindingTest}'s, unchanged: a mock
 * {@link AgentPlatform} whose {@code plus(DataDictionary)} returns its argument (the real
 * {@code AgentPlatform.deploy(AgentScope)} contributes no types of its own, and the
 * {@code deploy} stub must return the platform because the scope builder chains
 * {@code createAgent} onto it) and whose {@code deploy(AgentScope)} records the scope's
 * actions. Everything under examination — the writer, the assembler, the type mapper,
 * {@code PromptedActionSpec.emit} — runs unmocked. The returned invoker is never called:
 * that would be a model call, and this test is about the artifact, not about the answers.
 *
 * <p>
 * <b>The fourth pack is the builder's own generation.</b> {@code builder-spec-one-shot}
 * carries a single step whose prompt is
 * {@code core/SingleShotAgentSpecGenerator.defaultPrompt()} restated as an AgentSpec step
 * plus the one-key reply envelope its own goal type declares (see
 * {@link #theStepThatProducesTheGoalOutputNamesTheEnvelopeItsGoalTypeDeclares()}), so it
 * is the Java flow's one model call with a tool group declared on that step rather than
 * the rules redistributed across steps, because agency here is tool reach and not step
 * count.
 *
 * <p>
 * <b>The fifth pack is the agentic route.</b> {@code builder-agentic-blueprint} reads the
 * blueprint documents through tools rather than receiving a corpus the driver already
 * assembled: its first step surveys them with the {@code blueprint} and {@code library}
 * groups, its second drafts the spec with {@code blueprint}, {@code validate} and
 * {@code security}. It is the pack {@code --flow agentic} deploys, and it is where the
 * group roles published by {@code tools/*ToolGroup} have to resolve — which is why the
 * deploy assertion below covers it on the same terms as the other four.
 *
 * <p>
 * <b>The sixth pack is the harness route.</b> {@code builder-harness} is the pack
 * {@code --flow harness} deploys, and it is the one whose steps decide what to read: a
 * survey step that lists the document roots, a planning step that says what is still
 * outstanding, a drafting step that reads what the plan named before it writes. Its type
 * rule is the same as the others' — every step output is a type the pack declares — which
 * is what {@link #everyBuilderPackDeclaresItsOwnTypeForEveryStepAndGoalOutput()} now
 * checks for six directories rather than five, and why the pack is in {@link #PACK_NAMES}
 * rather than covered by a test of its own.
 *
 * <p>
 * <b>What fails when a pack goes missing.</b> {@link #load(String)} asserts the manifest
 * is a regular file and prints the path it tried, so hiding
 * {@code builder-spec-one-shot/} — or any other pack directory named by
 * {@link #PACK_NAMES} — fails every test in this class at that assertion rather than
 * passing quietly on the packs that remain.
 *
 * @author Ivan Rodriguez
 */
class BuilderAgentSpecPackTest {

	/**
	 * The packs under test, addressed as classpath resources through
	 * {@link BuilderPackPaths}. A pack's directory name is also its manifest name, which
	 * the first assertion below checks.
	 */
	private static final List<String> PACK_NAMES = List.of("builder-single-spec", "builder-process-composition",
			"builder-skill-from-process", "builder-spec-one-shot", "builder-agentic-blueprint", "builder-harness");

	private static Path packDir(String packName) {
		return BuilderPackPaths.directory(packName);
	}

	private static AgentSpec load(String packName) throws IOException {
		Path dir = packDir(packName);
		assertThat(dir.resolve("manifest.yml")).as("pack directory %s exists on disk", dir).isRegularFile();
		return new AgentSpecReader().read(dir);
	}

	/**
	 * Deploys a pack through the production factory and returns the actions the platform
	 * was handed, which is the compiled form of the pack's steps.
	 */
	private static List<Action> actionsDeployedBy(AgentSpec pack) {
		AgentPlatform platform = mock(AgentPlatform.class);
		when(platform.plus(any(DataDictionary.class))).thenAnswer((invocation) -> invocation.getArgument(0));
		List<Action> deployed = new ArrayList<>();
		when(platform.deploy(any(AgentScope.class))).thenAnswer((invocation) -> {
			deployed.addAll(invocation.getArgument(0, AgentScope.class).getActions());
			return platform;
		});
		new AgentSpecInvokerFactory(platform).invokerFor(pack);
		return deployed;
	}

	private static List<String> namesOf(List<Action> actions) {
		return actions.stream().map(Action::getName).sorted().toList();
	}

	/**
	 * Each pack is a directory the product reader understands, carrying its own name, one
	 * exported goal and at least one step: a pack that parses but is not the pack its
	 * directory claims to be is not the capability anyone is pointing at.
	 */
	@Test
	void eachBuilderPackLoadsFromItsDirectoryAsThePackItNames() throws IOException {
		for (String packName : PACK_NAMES) {
			AgentSpec pack = load(packName);

			assertThat(pack.name()).as("manifest name of %s", packName).isEqualTo(packName);
			assertThat(pack.goal()).as("goal.yml of %s", packName).isNotNull();
			assertThat(pack.goal().isExported()).as("goal of %s is callable", packName).isTrue();
			assertThat(pack.steps()).as("steps/ of %s", packName).isNotEmpty();
		}
	}

	/**
	 * Every output the pack produces is one of its own declared {@code DynamicTypeDef}s,
	 * including the goal's. This is the structural form of the packs'
	 * no-{@code BackgroundMessage} rule: {@code BackgroundMessage} is on the validator's
	 * framework list, so validation passes whether or not a step names it, and the
	 * polymorphic {@code parts} of that type is what cost POC 27 its run.
	 */
	@Test
	void everyBuilderPackDeclaresItsOwnTypeForEveryStepAndGoalOutput() throws IOException {
		for (String packName : PACK_NAMES) {
			AgentSpec pack = load(packName);

			for (ActionStepSpec step : pack.steps()) {
				assertThat(pack.type(step.outputTypeName()))
					.as("output type '%s' of step %s in %s is declared under types/", step.outputTypeName(),
							step.name(), packName)
					.isNotNull();
			}
			assertThat(pack.type(pack.goalOutputType()))
				.as("goal output type '%s' of %s is declared under types/", pack.goalOutputType(), packName)
				.isNotNull();
		}
	}

	/**
	 * Each pack is clean through the validator. Read with the javadoc above: the pass
	 * says the layout is complete, the goal exported, the type references resolved, every
	 * prompt variable bound by a declared input and declared by its type, and every
	 * step's inputs interpolated into its own prompt. It says nothing about
	 * {@code toolGroups} or condition names, which the validator never reads.
	 */
	@Test
	void eachBuilderPackPassesTheAgentSpecValidator() throws IOException {
		for (String packName : PACK_NAMES) {
			ValidationResult result = new AgentSpecValidator().validate(load(packName));

			assertThat(result.isValid()).as("validation issues on %s: %s", packName, result.issues()).isTrue();
		}
	}

	/**
	 * The deploy: every step of each pack compiled into an action and reached the
	 * platform, under the name the pack gave it. One step dropped, renamed or refused by
	 * the compiler would leave this list unequal to the pack's step list, and a pack
	 * whose steps Embabel cannot express would throw before returning.
	 */
	@Test
	void eachBuilderPackDeploysEveryStepThroughTheProductionInvokerFactory() throws IOException {
		for (String packName : PACK_NAMES) {
			AgentSpec pack = load(packName);

			List<Action> actions = actionsDeployedBy(pack);
			List<String> stepNames = pack.steps().stream().map(ActionStepSpec::name).sorted().toList();

			assertThat(actions).as("%s deployed one action per step", packName).hasSize(pack.steps().size());
			assertThat(namesOf(actions)).as("deployed action names of %s", packName).isEqualTo(stepNames);
		}
	}

	/**
	 * The four exported goals are named differently from each other, which is the cheap
	 * pre-check for deploying all four into one scope: a shared scope holds one goal per
	 * name. It is a pre-check and not a substitute for deploying them together — the
	 * packs also share the type name {@code ProcessScope} between two of them with
	 * different properties, and {@code builder-spec-one-shot} reuses the type name
	 * {@code AgentSpecJson} that {@code builder-single-spec} declares, there with an
	 * identical definition, so a single scope is not free and this test deploys them one
	 * at a time.
	 * <p>
	 * The method name still says "three" because
	 * {@code META-INF/native-image/.../reachability-metadata.json} lists it verbatim; the
	 * count comes from {@link #PACK_NAMES}, which the fourth pack is now in.
	 */
	@Test
	void theThreeBuilderPacksExportDistinctGoalNames() throws IOException {
		List<String> goals = new ArrayList<>();
		for (String packName : PACK_NAMES) {
			goals.add(load(packName).goal().name());
		}

		assertThat(goals).as("exported goal names, in %s order", PACK_NAMES)
			.hasSize(PACK_NAMES.size())
			.doesNotHaveDuplicates();
	}

	/**
	 * The envelope the pack flow reads back is declared in the format rather than moved
	 * into Java. {@code builder-spec-one-shot/goal.yml} exports its goal on a type with
	 * exactly one own-property, which is the key the answer has to use — and the step
	 * that produces that type has to name it, because the platform only <em>tells</em>
	 * the model about it: {@code PromptedActionSpecAction} contributes a fixed "You
	 * response must adhere to the following JSON schema" prompt for a {@code DynamicType}
	 * output and then parses the reply with no validation against that schema. A step
	 * prompt that describes a different object as the reply is therefore a second,
	 * competing shape instruction, and following it is task 4.2's unwrapped answer (the
	 * {@code ('m'} variant of the same crash). Read from the pack rather than hardcoded,
	 * so renaming the property without renaming it in the prompt fails here rather than
	 * at a live build. Three packs are claimed: {@code builder-spec-one-shot}, which
	 * {@code --flow pack} deploys; {@code builder-agentic-blueprint}, which
	 * {@code --flow agentic} deploys; and {@code builder-harness}, which
	 * {@code --flow harness} deploys. The three planning packs are not, because their
	 * goals carry planning artifacts that Java consumes field by field.
	 * @throws IOException when the pack is not where the other tests read it
	 */
	@Test
	void theStepThatProducesTheGoalOutputNamesTheEnvelopeItsGoalTypeDeclares() throws IOException {
		for (String packName : List.of("builder-spec-one-shot", "builder-agentic-blueprint", "builder-harness")) {
			AgentSpec pack = load(packName);
			DynamicTypeDef goalType = pack.type(pack.goalOutputType());

			assertThat(goalType.ownProperties())
				.as("goal output type %s of %s is a one-key envelope", pack.goalOutputType(), packName)
				.hasSize(1);
			String envelope = goalType.ownProperties().get(0).name();
			List<String> producers = pack.steps()
				.stream()
				.filter((step) -> pack.goalOutputType().equals(step.outputTypeName()))
				.map(ActionStepSpec::name)
				.toList();

			assertThat(producers).as("steps of %s producing its goal output", packName).isNotEmpty();
			for (String stepName : producers) {
				assertThat(pack.step(stepName).prompt())
					.as("step %s of %s shows the reply as an object keyed by the property its own output type declares",
							stepName, packName)
					.contains("\"" + envelope + "\"");
			}
		}
	}

}
