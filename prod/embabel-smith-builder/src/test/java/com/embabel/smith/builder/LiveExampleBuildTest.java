/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.builder.blueprint.BlueprintLoader;
import com.embabel.smith.builder.core.AgentSpecGenerators;
import com.embabel.smith.builder.core.AiSpecPromptCaller;
import com.embabel.smith.builder.core.BuildTarget;
import com.embabel.smith.builder.core.BuilderAgent;
import com.embabel.smith.builder.core.GenerationTechnique;
import com.embabel.smith.builder.core.SingleShotProcessSpecGenerator;
import com.embabel.smith.builder.eval.AgentSpecInvokerFactory;
import com.embabel.smith.builder.eval.AiPromptRewriter;
import com.embabel.smith.builder.eval.EvalTask;
import com.embabel.smith.builder.publish.PublishTarget;
import com.embabel.smith.builder.skill.EvalSuite;
import com.embabel.smith.builder.skill.SkillBuildResult;
import com.embabel.smith.builder.skill.SkillBuilderService;
import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.model.AgentSpec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Endpoint-gated example test: builds a real agent spec and a real skill from one
 * hand-written blueprint and asserts what the blueprint dictates rather than what the
 * model chose.
 *
 * <p>
 * This is the test the example scripts could not be. {@code examples/generate-spec.sh}
 * and {@code examples/generate-skill.sh} record a build's outcome; they cannot assert
 * one, and the blueprints they run on are 31-44 KB documents whose smallest run cost 18
 * endpoint timeouts over 12 minutes. So this test brings its own fixture — a 1.5 KB
 * process document with three stages and one named deliverable — and asserts the
 * deliverable, the stage count and the published layout.
 *
 * <p>
 * The assertions are deliberately about shape. Nothing here names a step, a type property
 * or a sentence the model wrote, because this repo's known live-test failure is a test
 * that breaks on model variance rather than on a code change (see the three
 * endpoint-gated tests that fail on live output alone). The one content assertion is the
 * {@value #DELIVERABLE} token, which the fixture states twice in its own prose, so a
 * build that loses it lost the thing the document was about.
 *
 * @author Ivan Rodriguez
 */
@SpringBootTest
@Tag("live")
@EnabledIfEnvironmentVariable(named = "OPENAI_CUSTOM_BASE_URL", matches = ".+")
class LiveExampleBuildTest {

	/**
	 * The fixture directory, resolved the way the module's other fixture tests resolve
	 * theirs — from the module working directory, not the classloader.
	 */
	private static final Path BLUEPRINT = Path.of("src/test/resources/blueprints/parcel-claims");

	/** The terminal deliverable the fixture names twice in its own words. */
	private static final String DELIVERABLE = "ClaimDecision";

	@Autowired
	Ai ai;

	@Autowired
	AgentPlatform platform;

	/**
	 * Proves the build reaches publish from a blueprint it was actually given, and that
	 * the pack it published is the one the blueprint describes: the goal's output type is
	 * the deliverable the document names, and the process has the several stages it sets
	 * out.
	 *
	 * <p>
	 * The spec is read back from the published directory with the product's own
	 * {@link AgentSpecReader}, because a successful {@link BuildResult} carries the pack
	 * name, score and publish location but not the spec itself — the name it was
	 * published under is the only handle the result leaves, and reading the artifact back
	 * is what a consumer of the library would do. Shape rather than content is asserted
	 * for the same reason {@code LiveBuildIntegrationTest} stops at the pack layout: step
	 * names, prompts and type properties are the model's choice and vary run to run.
	 */
	@Test
	void smallBlueprintBuildsASpecWhoseDeliverableIsTheOneTheBlueprintNames(@TempDir Path library) throws Exception {
		BuildResult result = service(library).build(description(), PublishTarget.parse("folder:" + library),
				BuildOptions.defaults());

		assertThat(result.isBuilt()).as(() -> result.report()).isTrue();
		Path published = Path.of(result.published().location());
		AgentSpec pack = new AgentSpecReader().read(published);

		assertThat(pack.goal().outputTypeName()).as(() -> result.report()).endsWith(DELIVERABLE);
		assertThat(pack.steps()).as(() -> result.report()).hasSizeGreaterThanOrEqualTo(2);
		pack.steps().forEach((step) -> assertThat(step.outputTypeName()).as(() -> result.report()).isNotBlank());

		assertThat(published.resolve("manifest.yml")).as(() -> result.report()).isRegularFile();
		assertThat(published.resolve("goal.yml")).as(() -> result.report()).isRegularFile();
		assertThat(published.resolve("lifecycle.yml")).as(() -> result.report()).isRegularFile();
		// Asserted on the read-back pack rather than on a directory listing: the writer
		// drops a .gitkeep into an empty types/, so a listing passes on a pack that
		// declares nothing. The steps are already asserted above.
		assertThat(pack.types()).as("the pack declares types under types/").isNotEmpty();
	}

	/**
	 * Proves the same blueprint also builds a publishable skill, and that the skill
	 * carries the contract it was accepted on: one {@code SKILL.md}, one
	 * {@code evals.json}, and a suite with at least one scenario to grade against.
	 *
	 * <p>
	 * Shape rather than content, again, and for the skill target the reason is sharper:
	 * its gate is the with/without-skill lift, whose judge is non-deterministic, so the
	 * number it produces is not assertable across runs. The published layout is. The
	 * scenario count is asserted because the suite is what makes the lift reproducible —
	 * a skill published with an empty suite would report a lift nobody can re-measure.
	 */
	@Test
	void smallBlueprintBuildsASkillThatPassesItsOwnShapeContract(@TempDir Path library) throws Exception {
		// maxRefine 0: this asserts the published shape, not the lift gate, and each
		// refinement round is paid for in model calls. minLift 0.0 is the product default
		// — any lift that beats the baseline publishes — so the gate is left as the
		// product
		// runs it rather than loosened for the test.
		BuildOptions options = new BuildOptions(0, BuildOptions.DEFAULT_SCORE_GATE, false, false,
				GenerationTechnique.SINGLE_SHOT, BuildTarget.SKILL, 0.0);
		// The wiring BuildCommand.newSkillService() builds with, so the live run
		// exercises
		// the product's skill pipeline rather than a configuration no product path uses.
		SkillBuildResult result = new SkillBuilderService(new AiSpecPromptCaller(this.ai)).build(description(),
				PublishTarget.parse("folder:" + library), options);

		assertThat(result.isBuilt()).as(() -> result.report()).isTrue();
		List<Path> evals = filesNamed(library, "evals.json");
		assertThat(filesNamed(library, "SKILL.md")).as(() -> result.report()).hasSize(1);
		assertThat(evals).as(() -> result.report()).hasSize(1);

		// Published beside the SKILL.md so the lift stays reproducible; assert it has
		// something to reproduce.
		EvalSuite suite = EvalSuite.parse(Files.readString(evals.get(0)));
		assertThat(suite.evals()).as(() -> result.report()).isNotEmpty();
	}

	/**
	 * The blueprint text, assembled by the loader the CLI uses. Asserted before any model
	 * call so a missing or unreadable fixture fails as a fixture failure rather than as
	 * an unexplained build failure twelve minutes later.
	 * @return the description to build from.
	 */
	private static String description() {
		String description = new BlueprintLoader().resolve(null, BLUEPRINT.toString());
		assertThat(description).as("the blueprint under %s assembles to a description", BLUEPRINT).isNotBlank();
		return description;
	}

	private BuilderService service(Path library) throws Exception {
		Path scratch = Files.createTempDirectory("smith-example-scratch-");
		BuilderAgent agent = new BuilderAgent(AgentSpecGenerators.production(this.ai, this.platform),
				scratch.toString());
		// The wiring the CLI builds with, so the live run exercises the dual-candidate
		// pipeline rather than a configuration no product path uses.
		AgentSpecInvokerFactory invokers = new AgentSpecInvokerFactory(this.platform);
		return new BuilderService(agent, invokers, invokers, new SingleShotProcessSpecGenerator(this.ai),
				new AiPromptRewriter(this.ai),
				List.of(new EvalTask("runs-and-produces-output", "Describe your use case.", "")));
	}

	private static List<Path> filesNamed(Path root, String fileName) throws IOException {
		try (Stream<Path> walk = Files.walk(root)) {
			return walk.filter(Files::isRegularFile)
				.filter((file) -> fileName.equals(file.getFileName().toString()))
				.toList();
		}
	}

}
