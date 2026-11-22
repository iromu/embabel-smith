/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.scripts;

import java.nio.file.Path;
import java.util.List;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.skills.script.ScriptExecutionResult;
import com.embabel.smith.builder.core.AiSpecPromptCaller;
import com.embabel.smith.selfimprove.SelfImproveApp;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tasks 6.1–6.3, live half: a real model writes both halves of the skill for one step of
 * the builder's own pipeline, and the script it wrote is then run by POC 26's engine.
 *
 * <p>
 * <b>Two model calls, made once, shared by both checks.</b> The skill and its script are
 * generated in {@link #generateOneSkillFromOneStep()} so the frontmatter assertion and
 * the execution assertion describe the same answer; a second generation would let them
 * disagree for no reason but model noise, and the paid budget is the POC's to account
 * for.
 *
 * <p>
 * The assertions are about shape, deliberately, for the reason this module already
 * records against its live tests: a test that breaks on model variance rather than on a
 * code change is a false finding in a directory that is frozen afterwards. So nothing
 * here requires the model's script to <em>succeed</em> — a model can write a script that
 * has a bug, and that is a finding about the model, not about this section. What is
 * asserted is that the frontmatter clears the product's gate, that a JavaScript file
 * landed in the folder, and that the engine accepted the script for execution rather than
 * refusing it. Whether it earned credit, and what it said if it did not, is printed for
 * the record.
 *
 * <p>
 * Spring is started by hand rather than with a Spring test annotation, because this
 * module's test classpath carries JUnit and AssertJ only — no
 * {@code spring-boot-starter-test} — and adding a dependency to the shared POM is not
 * this section's to do. The caller is the product's {@link AiSpecPromptCaller}, so the
 * live run goes through the same Embabel {@code Ai} layer a build does.
 *
 * @author Ivan Rodriguez
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "OPENAI_CUSTOM_BASE_URL", matches = ".+")
class GeneratedSkillScriptLiveTest {

	@TempDir
	static Path workDir;

	private static ConfigurableApplicationContext context;

	private static StepSkillGenerator.Generated skill;

	private static SkillCredit.Ledger ledger;

	@BeforeAll
	static void generateOneSkillFromOneStep() {
		context = new SpringApplicationBuilder(SelfImproveApp.class).web(WebApplicationType.NONE)
			.bannerMode(Banner.Mode.OFF)
			.run();
		Ai ai = context.getBean(Ai.class);
		skill = new StepSkillGenerator(new AiSpecPromptCaller(ai)).generate(ScriptFixtures.AGENT, ScriptFixtures.STEP,
				workDir);
		ledger = new SkillCredit(ScriptFixtures.ENGINE).run(skill, "draft: a compiled spec that names every stage");
		System.out.println("poc-27 6.2: generated skill '" + skill.skillName() + "' carrying " + skill.scriptFiles()
				+ "; credit ledger says " + ledger.creditedSkills() + " / withheld " + ledger.withheld());
	}

	@AfterAll
	static void stopTheEndpoint() {
		if (context != null) {
			context.close();
		}
	}

	@Test
	void theLiveGeneratedSkillClearsTheFrontmatterGateAndCarriesAJavaScript() {
		assertThat(skill.frontmatterIssues()).as(() -> skill.skillName() + " -> " + skill.frontmatterIssues())
			.isEmpty();
		assertThat(skill.scriptFiles()).as(() -> "scripts/ of " + skill.skillDir()).isNotEmpty();
		assertThat(skill.scriptFiles()).allMatch((name) -> name.endsWith(".js"));
		assertThat(skill.skillDir().getFileName().toString()).isEqualTo(skill.skillName());
	}

	@Test
	void theLiveGeneratedScriptIsAcceptedByTheEngine() {
		ScriptExecutionResult result = ScriptFixtures.ENGINE.execute(skill.script(skill.scriptFileName()), List.of(),
				"draft: a compiled spec that names every stage", List.of());

		// Denied is the engine refusing to run the file at all — not JavaScript, not on
		// disk, too large. That is the one outcome no generated script is allowed to
		// produce, because it means the candidate wrote something the interpreter will
		// never execute.
		assertThat(result).isNotInstanceOf(ScriptExecutionResult.Denied.class);
		// And the run that reached the ledger was a run, not a sandbox refusal: POC 26's
		// hardening is still the one in force on this path.
		assertThat(ledger.withheld()).allMatch((outcome) -> !outcome.diagnostic().contains("not allowed")
				&& !outcome.diagnostic().contains("was not staged"));
	}

}
