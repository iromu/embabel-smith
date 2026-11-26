/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

import com.embabel.smith.builder.security.AgentSpecSecurityScanner;
import com.embabel.smith.builder.security.ScanReport;
import com.embabel.smith.builder.security.TrustTier;
import com.embabel.smith.builder.security.TrustTierPolicy;
import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.samples.SampleAgentSpecs;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 8.3's before/after claim, measured through the production scanner: the
 * {@code builder-single-spec} drafting step now declares the {@code validate} tool group,
 * and {@link AgentSpecSecurityScanner#scan(AgentSpec)} reports a tier that is no longer
 * {@link TrustTier#T1_COMMUNITY}.
 *
 * <p>
 * <b>Why the drafting step is {@code assembleAgentSpec}.</b> Of the pack's five steps it
 * is the only one whose prompt asks the model for spec content. {@code scopeCoreProcess}
 * returns a scoping decision, {@code enumerateStages} a list of stages,
 * {@code declareIntermediateTypes} a name-plus-description catalogue and
 * {@code wireStepBindings} a report on wiring already decided — each an input to the
 * spec, none the spec. {@code assembleAgentSpec} emits the manifest, goal, steps, types
 * and prompts as one JSON document, and its own description says "Java parses this text
 * into the spec it writes". That is the document {@code AgentSpecValidator} reports
 * findings against, so it is the step at which a {@code validate} call can be read and
 * re-drafted — the self-repair {@code BuilderService} performs today as numbered Java
 * stages (design D9).
 *
 * <p>
 * <b>What a moved tier does not mean.</b>
 * {@link TrustTierPolicy#check(AgentSpec, TrustTier)} filters the <em>declared
 * strings</em> against each tier's allowlist and resolves nothing against a
 * {@code ToolGroup} bean or a {@code CoreToolGroups} role. So any declaration at all
 * moves {@code recommendedTier()}: {@code web} or {@code search} gives T2, {@code file}
 * or {@code shell} gives T3, and any other name — a typo, or one of the builder's own
 * roles now that {@code tools/*ToolGroup} publishes them — lands on
 * {@link TrustTier#T4_PLATFORM}, because T4 allows {@code *} and {@code minimumTier}
 * therefore always terminates there. The control at the end of this class pins that: a
 * pack declaring a name that provably resolves to nothing reports the same tier as every
 * declaring pack reports. A moved tier is evidence the scanner read the step, never
 * evidence that a tool resolves — which is design D10's amendment to the "a pack's tier
 * is what bounds it" reading. Whether a role reaches a tool at all is a different test,
 * on a booted platform, in {@code tools/ExposedToolGroupsTest}.
 *
 * <p>
 * <b>Locating the packs</b> is {@code core/BuilderAgentSpecPackTest}'s convention: the
 * packs are classpath resources under {@code src/main/resources/specs}, addressed through
 * the class loader by {@code BuilderPackPaths.directory(name)} rather than resolved
 * against the working directory, and the load assertion names the path it tried.
 * Everything here is offline — a scanner over a model, no platform, no model call.
 *
 * @author Ivan Rodriguez
 */
class BuilderPackTierTest {

	/**
	 * The pack task 8.3 edits, and the step it edits. {@code assembleAgentSpec} is the
	 * step that drafts spec content; the other four produce planning artifacts (class
	 * javadoc).
	 */
	private static final String DRAFTING_PACK = "builder-single-spec";

	private static final String DRAFTING_STEP = "assembleAgentSpec";

	/**
	 * The fourth builder pack's single step. It is the same drafting act as
	 * {@code assembleAgentSpec} — the whole spec in one call, from
	 * {@code core/SingleShotAgentSpecGenerator.defaultPrompt()} — so it carries a
	 * declaration too, which is why the placement assertion below names two steps rather
	 * than one.
	 */
	private static final String ONE_SHOT_PACK = "builder-spec-one-shot";

	private static final String ONE_SHOT_STEP = "draftAgentSpec";

	/**
	 * The fifth builder pack: the agentic route. Both of its steps declare groups — the
	 * surveying step reads the blueprint documents and the library through
	 * {@code blueprint} and {@code library}, the drafting step reaches the validator and
	 * the scanner as well. Those declarations are the subject of the change rather than
	 * an addendum to a prompt, so both placements are named below.
	 */
	private static final String AGENTIC_PACK = "builder-agentic-blueprint";

	private static final String AGENTIC_SURVEY_STEP = "surveyBlueprint";

	private static final String AGENTIC_DRAFTING_STEP = "draftAgentSpecFromBlueprint";

	/**
	 * The tool group named by design D9 ("the tool that decides it is {@code validate}")
	 * and published by {@code tools/AgentSpecValidationTool}. No better-grounded name
	 * exists in this repository: that tool carries no group member on its
	 * {@code @LlmTool}, and the roles upstream publishes are {@code web}, {@code math},
	 * {@code maps}, {@code github} and {@code browser_automation}, none of which is
	 * validation.
	 */
	private static final String VALIDATE_GROUP = "validate";

	/**
	 * The packs whose steps declare nothing, which is where the T1 baseline comes from.
	 * The drafting pack is absent from this list by construction — it is the pack that
	 * changed.
	 */
	private static final List<String> UNDECLARED_PACKS = List.of("builder-process-composition",
			"builder-skill-from-process");

	/**
	 * The packs whose steps do declare a group, which is the set the tier assertions run
	 * over: the {@code builder-single-spec} drafting step task 8.3 edited, the one-shot
	 * pack's equivalent step, and both steps of the agentic pack.
	 */
	private static final List<String> DECLARING_PACKS = List.of(DRAFTING_PACK, ONE_SHOT_PACK, AGENTIC_PACK);

	private static final List<String> ALL_PACKS = List.of("builder-single-spec", "builder-process-composition",
			"builder-skill-from-process", "builder-spec-one-shot", AGENTIC_PACK);

	/**
	 * A group name that resolves to nothing, deliberately spelled so it cannot be one of
	 * the four names the {@code TrustTier} allowlists carry. It is the control half of a
	 * pair now: the builder's own groups ({@code validate}, {@code blueprint},
	 * {@code library}, {@code security}) are published beans, and this name is not — yet
	 * the scanner cannot tell them apart, which is the point of the test below.
	 */
	private static final String UNRESOLVABLE_GROUP = "no-such-group";

	private static Path packDir(String packName) {
		return BuilderPackPaths.directory(packName);
	}

	private static AgentSpec load(String packName) throws IOException {
		Path dir = packDir(packName);
		assertThat(dir.resolve("manifest.yml")).as("pack directory %s exists on disk", dir).isRegularFile();
		return new AgentSpecReader().read(dir);
	}

	private static String declaredGroupsOf(AgentSpec pack) {
		return new TrustTierPolicy().declaredToolGroups(pack).toString();
	}

	/**
	 * A one-step pack built in memory whose only step declares {@code group}. No extra
	 * pack directory is added for this: the on-disk packs supply the T1 baseline, and the
	 * control needs only a step with a declaration on it. The prompt interpolates its
	 * declared input so the control is a well-formed step rather than one the validator
	 * would reject for a reason unrelated to tool groups. Its output type is
	 * {@code BackgroundMessage} because nothing validates the control — the packs'
	 * no-{@code BackgroundMessage} rule governs shipped packs, and
	 * {@code core/BuilderAgentSpecPackTest} asserts it there.
	 */
	private static AgentSpec packDeclaring(String group) {
		ActionStepSpec step = new ActionStepSpec(ActionStepSpec.STEP_TYPE, "say", "Say the input back",
				List.of(SampleAgentSpecs.USER_INPUT), SampleAgentSpecs.BACKGROUND_MESSAGE,
				"Answer this: {{userInput.content}}", List.of(), List.of(), null, null, null, null, List.of(group));
		GoalStepSpec goal = GoalStepSpec.exportedGoal("done", "A single-step spec",
				SampleAgentSpecs.BACKGROUND_MESSAGE);
		return new AgentSpec(new AgentSpecManifest("tier-probe", "1.0.0"), goal, List.of(step), List.of());
	}

	/**
	 * The baseline the change is measured against: a pack whose steps declare no tool
	 * group scans at {@code T1_COMMUNITY}, whose allowlist is empty. This is the tier
	 * {@code builder-single-spec} reported before task 8.3, so if it ever stops holding,
	 * the "moved off T1" assertion below stops meaning anything.
	 * <p>
	 * Mutation: declare any group on any step of either pack and this fails.
	 */
	@Test
	void aPackThatDeclaresNoToolGroupScansAtTheCommunityTier() throws IOException {
		AgentSpecSecurityScanner scanner = new AgentSpecSecurityScanner();
		for (String packName : UNDECLARED_PACKS) {
			AgentSpec pack = load(packName);

			assertThat(scanner.scan(pack).recommendedTier())
				.as("recommendedTier() of %s, whose %d steps declare %s — the T1 baseline the drafting pack's"
						+ " move is measured against", packName, pack.steps().size(), declaredGroupsOf(pack))
				.isEqualTo(TrustTier.T1_COMMUNITY);
		}
	}

	/**
	 * The declaration is on the steps that draft spec content, and on no other step of
	 * any builder pack. Task 8.3 names one step; a declaration spread across the scoping
	 * and enumeration steps would move the tier identically while claiming a capability
	 * the planner never uses, so the placement is asserted as well as the presence.
	 * <p>
	 * {@code builder-spec-one-shot} declares too — its one step is that same drafting act
	 * expressed as a pack — and the agentic pack declares on both of its steps, because
	 * reading the blueprint through a tool is that pack's purpose rather than an addendum
	 * to a prompt. So this is a four-placement claim, where it was one step while three
	 * packs were all that existed.
	 * <p>
	 * Mutation: revert any of the four to {@code toolGroups: []} and the list drops; move
	 * one to another step and the name is wrong.
	 */
	@Test
	void theDraftingStepsAreTheOnlyBuilderPackStepsThatDeclareAToolGroup() throws IOException {
		List<String> declaring = new ArrayList<>();
		List<String> draftedWith = List.of();
		for (String packName : ALL_PACKS) {
			for (ActionStepSpec step : load(packName).steps()) {
				if (!step.toolGroups().isEmpty()) {
					declaring.add(packName + "/steps/" + step.name());
					if (packName.equals(DRAFTING_PACK) && step.name().equals(DRAFTING_STEP)) {
						draftedWith = step.toolGroups();
					}
				}
			}
		}

		assertThat(declaring).as("specs/ steps carrying a tool-group declaration")
			.containsExactlyInAnyOrder(DRAFTING_PACK + "/steps/" + DRAFTING_STEP,
					ONE_SHOT_PACK + "/steps/" + ONE_SHOT_STEP, AGENTIC_PACK + "/steps/" + AGENTIC_SURVEY_STEP,
					AGENTIC_PACK + "/steps/" + AGENTIC_DRAFTING_STEP);
		assertThat(draftedWith).as("groups declared by %s/%s", DRAFTING_PACK, DRAFTING_STEP)
			.containsExactly(VALIDATE_GROUP);
	}

	/**
	 * The "declaration reaches the scanner" half of task 8.3, now over every pack that
	 * declares: for each, {@code recommendedTier()} is above {@code T1_COMMUNITY}, which
	 * is what {@code minimumTier} reports only once a step's declared groups are
	 * non-empty. Read with the control below — this says the scanner read the step, not
	 * that {@code validate} names a capability.
	 * <p>
	 * Mutation: revert a declaration to {@code toolGroups: []} and that pack's tier is T1
	 * again.
	 */
	@Test
	void aBuilderPackThatDeclaresAToolGroupNoLongerScansAtTheCommunityTier() throws IOException {
		AgentSpecSecurityScanner scanner = new AgentSpecSecurityScanner();
		for (String packName : DECLARING_PACKS) {
			ScanReport report = scanner.scan(load(packName));

			assertThat(report.recommendedTier())
				.as("recommendedTier() of %s after its drafting step declared %s. A tier above T1 proves the"
						+ " scanner read the step; it does NOT prove the group resolves to a tool — see the T4"
						+ " control", packName, VALIDATE_GROUP)
				.isNotEqualTo(TrustTier.T1_COMMUNITY);
		}
	}

	/**
	 * The control, and the reason the assertion above must never be cited as a reach
	 * test. A pack whose only step declares a name that resolves to nothing reports
	 * {@code T4_PLATFORM} — the same tier every declaring pack reports, including the
	 * ones whose names now do resolve. It reaches T4 by exhaustion rather than by grant:
	 * T3 refuses the name, and T4 is the tier whose allowlist is {@code *}, so
	 * {@code minimumTier} has nowhere further to go. Since the tool surface landed, this
	 * is a stronger claim than it was: {@code blueprint}, {@code library}, {@code
	 * security} and {@code validate} are published {@code ToolGroup} beans on the booted
	 * platform (asserted in {@code tools/ExposedToolGroupsTest}), and the scanner reports
	 * for them exactly what it reports for {@code no-such-group}. A moved tier is
	 * evidence the scanner read the step and never evidence that a tool resolves — design
	 * D10's finding about the tier as a containment mechanism, now with the resolved half
	 * filled in.
	 * <p>
	 * Mutations: add {@code no-such-group} to {@code TrustTier.T3_PARTNER}'s allowlist
	 * and the T3 refusal fails; give {@code T4_PLATFORM} an allowlist without {@code *}
	 * and the terminal tier fails; and if a declaring pack's groups ever became an
	 * allowlisted role ({@code web}, {@code search}, {@code file}, {@code shell}) its
	 * tier drops below T4 and the last assertion fails — a real divergence worth
	 * reviewing, not a flake.
	 */
	@Test
	void aGroupNameThatResolvesToNothingStillMovesTheTierToT4Platform() throws IOException {
		AgentSpec control = packDeclaring(UNRESOLVABLE_GROUP);
		ScanReport controlReport = new AgentSpecSecurityScanner().scan(control);

		assertThat(controlReport.recommendedTier())
			.as("recommendedTier() of a pack declaring '%s', a name no ToolGroup bean and no CoreToolGroups"
					+ " role publishes", UNRESOLVABLE_GROUP)
			.isEqualTo(TrustTier.T4_PLATFORM);

		assertThat(new TrustTierPolicy().check(control, TrustTier.T3_PARTNER).allowed())
			.as("T3_PARTNER refuses '%s', so T4 is reached because T4 allows '*' and minimumTier terminates"
					+ " there — not because anything granted the group", UNRESOLVABLE_GROUP)
			.isFalse();

		AgentSpecSecurityScanner scanner = new AgentSpecSecurityScanner();
		for (String packName : DECLARING_PACKS) {
			TrustTier declaredTier = scanner.scan(load(packName)).recommendedTier();
			assertThat(declaredTier)
				.as("%s's tier is the tier an admittedly-unresolvable name also produces, so a moved tier is"
						+ " evidence the scanner read the step and never evidence that a tool resolves", packName)
				.isEqualTo(controlReport.recommendedTier());
		}
	}

}
