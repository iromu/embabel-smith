/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.embabel.agent.api.common.PlannerType;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.runtime.AgentSpecRunService;
import com.embabel.smith.runtime.TestPacks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Planner precedence stated, not merely applied.
 *
 * <p>
 * Two settings can name a planner — a pack's {@code manifest.defaultPlanner} and the
 * run's {@code --planner} — and {@link PlannerResolver} already ranks them: run choice,
 * then pack default, then the platform's GOAP. Ranking is not the whole of the promise,
 * though. A run that printed only the strategy would leave a reader holding two files and
 * guessing which one won, so the resolver reports the winner alongside the answer and the
 * run's notes say so in one line. The cases below pin all four shapes of that line, pin
 * the algorithm note a line carries when the strategy name is not an algorithm, and the
 * last one follows a real run through the service to show the line reaches the notes
 * rather than staying inside the resolver.
 *
 * <p>
 * Runs with no model and no endpoint. The service case points the platform at a dead
 * address on purpose: the run fails at its first model call, which is after the planner
 * has been resolved, and it is the notes that are under test, not the goal's achievement.
 *
 * @author Ivan Rodriguez
 */
@SpringBootTest(properties = { "OPENAI_CUSTOM_BASE_URL=http://127.0.0.1:9/v1", "OPENAI_CUSTOM_API_KEY=test",
		"OPENAI_CUSTOM_MODELS=test-model", "embabel.models.default-llm=test-model",
		// Fail fast on action failures (ActionQos defaults to 5 attempts with backoff).
		"embabel.agent.platform.action-qos.default.max-attempts=1" })
class PlannerReportTest {

	private final PlannerResolver resolver = new PlannerResolver();

	@Autowired
	private AgentPlatform platform;

	@Test
	void theRunChoiceWinsAndNamesThePackDefaultItOverrode() {
		PlannerResolver.Decision choice = this.resolver.decide("SUPERVISOR", pack("GOAP"));

		assertThat(choice.planner()).isEqualTo(PlannerType.SUPERVISOR);
		assertThat(choice.origin()).isEqualTo(PlannerResolver.Origin.RUN_CHOICE);
		assertThat(choice.describe()).as("the line says the flag won and what it stepped over")
			.contains("SUPERVISOR")
			.contains("the run's choice")
			.contains("overriding the pack's default GOAP");
		assertThat(this.resolver.resolve("SUPERVISOR", pack("GOAP"))).as("and the planner a run gets is unchanged")
			.isEqualTo(PlannerType.SUPERVISOR);
	}

	@Test
	void aSupervisorLineNamesTheAlgorithmThatActuallyPlans() {
		assertThat(this.resolver.decide("SUPERVISOR", pack("GOAP")).describe())
			.as("SUPERVISOR is a name the factory routes to A* GOAP, not a fourth algorithm")
			.contains("SUPERVISOR")
			.contains("A* GOAP");
		assertThat(this.resolver.decide("GOAP", pack("GOAP")).describe())
			.as("and a planner that is what it sounds like gains nothing")
			.isEqualTo("planner: GOAP (the run's choice, overriding the pack's default GOAP)");
	}

	@Test
	void aRunChoiceWithNothingToOverstepSaysSoRatherThanClaimingAnOverride() {
		PlannerResolver.Decision choice = this.resolver.decide("SUPERVISOR", pack(null));

		assertThat(choice.origin()).isEqualTo(PlannerResolver.Origin.RUN_CHOICE);
		assertThat(choice.describe()).contains("the run's choice")
			.contains("the pack named no default")
			.doesNotContain("overriding");
	}

	@Test
	void thePackDefaultWinsAloneAndIsNamedAsThePackRatherThanAsAnOverride() {
		PlannerResolver.Decision choice = this.resolver.decide(null, pack("UTILITY"));

		assertThat(choice.planner()).isEqualTo(PlannerType.UTILITY);
		assertThat(choice.origin()).isEqualTo(PlannerResolver.Origin.PACK_DEFAULT);
		assertThat(choice.describe()).contains("UTILITY")
			.contains("the pack manifest's defaultPlanner")
			.doesNotContain("overriding")
			.doesNotContain("run's choice");
	}

	@Test
	void withNeitherSettingTheDefaultSaysNobodyNamedOne() {
		PlannerResolver.Decision choice = this.resolver.decide(null, pack(null));

		assertThat(choice.planner()).isEqualTo(PlannerType.GOAP);
		assertThat(choice.origin()).isEqualTo(PlannerResolver.Origin.PLATFORM_DEFAULT);
		assertThat(choice.describe()).contains("GOAP").contains("neither the run nor the pack named one");
	}

	@Test
	void theNotesOfARealRunSayWhichOfTheTwoSettingsWon(@TempDir Path tmp) throws IOException {
		Path library = TestPacks.library(tmp, "explain");
		Path manifest = library.resolve("explain").resolve("manifest.yml");
		Files.writeString(manifest, Files.readString(manifest) + "defaultPlanner: GOAP\n");

		AgentSpecRunService.RunResult overridden = service(tmp).run("folder:" + library, "explainDone", "input",
				"SUPERVISOR", TrustTier.STANDARD);
		AgentSpecRunService.RunResult asThePackSays = service(tmp.resolve("second")).run("folder:" + library,
				"explainDone", "input", null, TrustTier.STANDARD);

		assertThat(overridden.notes()).as("the flag won, and the notes say what it stepped over")
			.anyMatch((line) -> line.contains("SUPERVISOR") && line.contains("overriding the pack's default GOAP"));
		assertThat(asThePackSays.notes()).as("and with no flag the same pack's own default is named as the pack's")
			.anyMatch((line) -> line.contains("GOAP") && line.contains("the pack manifest's defaultPlanner"));
	}

	/**
	 * A service writing its traces where the test can see them, with the dead endpoint
	 * the class-level properties point the platform at.
	 * @param root the directory to write traces under
	 * @return the service
	 */
	private AgentSpecRunService service(Path root) {
		return new AgentSpecRunService(this.platform, root.resolve("traces/runs.jsonl"));
	}

	/**
	 * A pack whose manifest names a default planner, or carries no such line.
	 * @param defaultPlanner the declared default, or {@code null}
	 * @return the spec
	 */
	private static AgentSpec pack(String defaultPlanner) {
		return new AgentSpec(new AgentSpecManifest("pack", "1.0.0", defaultPlanner, null), null, List.of(), List.of());
	}

}
