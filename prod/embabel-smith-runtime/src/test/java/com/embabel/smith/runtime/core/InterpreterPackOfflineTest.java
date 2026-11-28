/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.specsource.SpecSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shipped generic pack, run offline for what an offline run can settle.
 *
 * <p>
 * The pack ({@code agent-specs/skill-interpreter}) carries no flow syntax at all — seven
 * definitions whose steps name the types they consume and the types they produce, and the
 * planner decides the order. So the claim that its progression is <i>typed</i> rather
 * than ordered is settled by subtraction, not by reading a sequence: with every
 * definition in one source the exported goal is reachable, and take away any one of the
 * definitions that feeds it and the goal stops being reachable — the run says the goal's
 * precondition went unmet, and the ingest says which definition it lost. Deleting the
 * definition that does <i>not</i> feed the goal is run alongside, and it is the leg that
 * makes the others mean something: the same deletion and the same kind of source, with
 * the goal still reachable.
 *
 * <p>
 * The goal under test is {@code taskReported}, the one the {@code verify} definition
 * exports, and that choice is forced rather than preferred. A folder source validates
 * each definition before it hands it over, and a definition whose only step was deleted
 * fails that check for having an empty {@code steps/} — it is then skipped by the source
 * and its goal is never exported at all, which is a different finding from the one this
 * box asks for. So the attempted goal belongs to a definition no leg of this test prunes,
 * and each pruned leg names its producer by naming the definition the source dropped.
 *
 * <p>
 * The other half is the degradation path: a source that carries <b>no skill at all</b>.
 * The pack's {@code select} and {@code script} steps each declare a reference, and with
 * nothing to resolve it those two definitions are refused by the source — which says so,
 * naming both the definition and the name it could not answer — while the rest of the
 * pack still deploys and the run still goes through. That is what "a pack with no skill
 * still runs" has to mean for a reader who checks it without an endpoint: not that the
 * missing skill is unnoticed, but that it is the only thing said about it. The same pack
 * with its skill folder intact is run alongside as the control, since a line that never
 * appears in either case would satisfy the skill-free assertion just as well.
 *
 * <p>
 * Runs with no model and no endpoint. Every run here is expected to fail at its first
 * model call; what is under test is <i>where</i> it fails — at a plan it cannot build, at
 * a model it cannot reach, or nowhere near the missing skill.
 *
 * @author Ivan Rodriguez
 */
@SpringBootTest(properties = { "OPENAI_CUSTOM_BASE_URL=http://127.0.0.1:9/v1", "OPENAI_CUSTOM_API_KEY=test",
		"OPENAI_CUSTOM_MODELS=test-model", "embabel.models.default-llm=test-model",
		// Fail fast on action failures (ActionQos defaults to 5 attempts with backoff).
		"embabel.agent.platform.action-qos.default.max-attempts=1" })
class InterpreterPackOfflineTest {

	/** The goal every leg attempts: the report at the end of the pack's progression. */
	private static final String GOAL = "taskReported";

	/** The definitions the goal depends on, each with the type only it produces. */
	private static final Map<String, String> PRODUCES = produces();

	/**
	 * The one definition the goal does not depend on, which is the control that keeps the
	 * pruned legs from passing on any failure whatsoever.
	 */
	private static final String NOT_A_PRODUCER = "escalate";

	@TempDir
	Path tmp;

	@Autowired
	private AgentPlatform platform;

	@Test
	void theChainStandsOnTypesBecauseEveryDefinitionItRemovesNamesWhatItUnproduced() throws IOException {
		Attempt whole = run(shipped(this.tmp.resolve("whole"), null), GOAL);

		assertThat(whole.outcome().error())
			.as("with every definition present the plan reaches the goal, so it fails at the model instead")
			.doesNotContain("unmet precondition");

		for (Map.Entry<String, String> producer : PRODUCES.entrySet()) {
			Attempt pruned = run(shipped(this.tmp.resolve("without-" + producer.getKey()), producer.getKey()), GOAL);

			assertThat(pruned.ingested().report().lines())
				.as("%s is the only producer of %s, so the source has to say it lost the definition", producer.getKey(),
						producer.getValue())
				.anyMatch((line) -> line.startsWith("pack '" + producer.getKey() + "' failed validation"));
			assertThat(pruned.outcome().error()).as("and the goal that type feeds is unmet, not the run failed")
				.contains("unmet precondition")
				.contains(GOAL, "TaskReport");
		}

		Attempt control = run(shipped(this.tmp.resolve("without-" + NOT_A_PRODUCER), NOT_A_PRODUCER), GOAL);

		assertThat(control.ingested().report().lines()).as("the control's deletion is the same kind of deletion")
			.anyMatch((line) -> line.startsWith("pack '" + NOT_A_PRODUCER + "' failed validation"));
		assertThat(control.outcome().error())
			.as("but the goal does not run through that definition, so it stays reachable")
			.doesNotContain("unmet precondition");
	}

	@Test
	void aSourceCarryingNoSkillStillRunsItsDefinitions() throws IOException {
		Path bare = shipped(this.tmp.resolve("no-skill"), null);
		deleteSkills(bare);

		RuntimeCore core = core();
		IngestedSource ingested = core.ingest(SpecSource.parse("folder:" + bare).resolve());

		assertThat(ingested.report().lines()).as("the two definitions that asked for the skill are refused, by name")
			.allMatch((line) -> !line.contains("failed validation")
					|| (line.contains("workspace-inventory") && line.contains("not something the pack carries")));
		assertThat(ingested.report().lines()).anyMatch((line) -> line.startsWith("pack 'select' failed validation"));
		assertThat(ingested.report().lines()).anyMatch((line) -> line.startsWith("pack 'script' failed validation"));
		assertThat(ingested.goalNames()).as("the definitions that asked for nothing still deploy")
			.contains(GOAL, "runBriefEstablished", "intentionsPlanned", "workPerformed")
			.doesNotContain("skillChosen", "figuresFromScript");

		RunOutcome shallow = core.run(ingested, "runBriefEstablished", "list what is here", null, TrustTier.HIGH);
		assertThat(shallow.error())
			.as("a goal upstream of the missing skill is still plannable, so it fails at the model instead")
			.contains("runBriefEstablished")
			.doesNotContain("unmet precondition")
			.doesNotContain("workspace-inventory");

		RunOutcome deep = core.run(ingested, GOAL, "list what is here", null, TrustTier.HIGH);
		assertThat(deep.error())
			.as("and the goal downstream of it is the missing skill's doing, said as the goal's own unmet precondition")
			.contains("unmet precondition")
			.doesNotContain("workspace-inventory");
	}

	@Test
	void theSamePackWithItsSkillResolvesTheSameTwoDeclarations(@TempDir Path control) throws IOException {
		IngestedSource ingested = core().ingest(SpecSource.parse("folder:" + shipped(control, null)).resolve());

		assertThat(ingested.report().lines()).as("the control: with the skill present both declarations resolve")
			.contains("select -> [shared-skills]", "script -> [shared-skills]");
		assertThat(ingested.report().lines()).as("and nothing was dropped on the way in")
			.noneMatch((line) -> line.contains("failed validation"));
	}

	/**
	 * One run of one goal over one source.
	 * @param source the source directory holding the definitions
	 * @param goal the goal to attempt
	 * @return the ingest and its outcome together, since the report lives on the ingest
	 */
	private Attempt run(Path source, String goal) {
		RuntimeCore core = core();
		IngestedSource ingested = core.ingest(SpecSource.parse("folder:" + source).resolve());
		return new Attempt(ingested, core.run(ingested, goal, "carry the task through", null, TrustTier.HIGH));
	}

	private RuntimeCore core() {
		return new RuntimeCore(this.platform);
	}

	/**
	 * Copy the shipped generic pack into a test directory, optionally without one
	 * definition's step.
	 * @param into where to copy it
	 * @param withoutStep the definition to leave without its producing step, or
	 * {@code null} for the pack as shipped
	 * @return the directory holding the copied pack
	 * @throws IOException if the pack cannot be copied or pruned
	 */
	private Path shipped(Path into, String withoutStep) throws IOException {
		Path pack = agentSpecs().resolve("skill-interpreter");
		copyTree(pack, into);
		if (withoutStep != null) {
			Files.delete(into.resolve(withoutStep).resolve("steps").resolve(withoutStep + ".yml"));
		}
		return into;
	}

	/**
	 * Remove every skill folder from a copied pack, leaving its definitions and their
	 * declared references in place.
	 * @param source the copied source
	 * @throws IOException if the folders cannot be removed
	 */
	private static void deleteSkills(Path source) throws IOException {
		try (Stream<Path> walk = Files.walk(source)) {
			List<Path> folders = walk.filter(Files::isDirectory)
				.filter((dir) -> dir.getFileName().toString().equals("skills"))
				.toList();
			for (Path folder : folders) {
				deleteTree(folder);
			}
		}
	}

	private static void deleteTree(Path root) throws IOException {
		try (Stream<Path> walk = Files.walk(root)) {
			for (Path path : walk.sorted((left, right) -> right.getNameCount() - left.getNameCount()).toList()) {
				Files.deleteIfExists(path);
			}
		}
	}

	private static void copyTree(Path from, Path to) throws IOException {
		try (Stream<Path> walk = Files.walk(from)) {
			for (Path path : walk.toList()) {
				Path destination = to.resolve(from.relativize(path).toString());
				if (Files.isDirectory(path)) {
					Files.createDirectories(destination);
				}
				else {
					Files.createDirectories(destination.getParent());
					Files.copy(path, destination);
				}
			}
		}
	}

	/**
	 * Locates the repository's {@code agent-specs/} by walking up from the working
	 * directory, which is the module directory under Maven — the same route the
	 * spec-source tests take to the checked-in packs.
	 * @return the directory holding the shipped definitions
	 */
	private static Path agentSpecs() {
		Path dir = Path.of("").toAbsolutePath();
		while (dir != null) {
			Path candidate = dir.resolve("agent-specs");
			if (Files.isDirectory(candidate)) {
				return candidate;
			}
			dir = dir.getParent();
		}
		throw new IllegalStateException("Could not locate the repository's agent-specs directory");
	}

	private static Map<String, String> produces() {
		Map<String, String> produced = new LinkedHashMap<>();
		produced.put("orient", "RunBrief");
		produced.put("select", "SkillDirective");
		produced.put("plan", "ActionPlan");
		produced.put("act", "WorkLog");
		produced.put("script", "ScriptEvidence");
		return Map.copyOf(produced);
	}

	/**
	 * One offline attempt: what the ingest reported and what the run returned.
	 *
	 * @param ingested the packs as ingested
	 * @param outcome the run's outcome
	 * @author Ivan Rodriguez
	 */
	private record Attempt(IngestedSource ingested, RunOutcome outcome) {
	}

}
