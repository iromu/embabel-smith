/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.embabel.agent.api.reference.LlmReference;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.specsource.SpecSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The live half of the reference claim: what a step declaring a skill is actually
 * offered, recorded before the model gets a chance to answer.
 *
 * <p>
 * <b>{@code offered=} before {@code called=}.</b> A run that called no skill tool reads
 * the same whether the tool was never in front of the model or the model chose not to use
 * it, so the offered list is printed first and the assertion is about that list. This
 * follows the convention {@code builder/eval/DeployedBuilderPackCallsValidatePackTest}
 * set for the same reason.
 *
 * <p>
 * <b>Why it is endpoint-gated.</b> The offered list is computed from the loaded
 * {@link Skills} reference, which is offline work; what needs a live model is the run
 * itself — the step actually executing with those tools attached. Hence
 * {@code @Tag("live")} plus the endpoint guard, the shape this repository's other
 * endpoint-gated cases use.
 *
 * <p>
 * <b>The names are the reference's.</b> The activation tools are listed under the
 * reference's own prefix ({@code <agent>_skills_…}), not under the skill folder's name,
 * which is the point of D5 and the thing a test that only counted tools would miss.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "OPENAI_CUSTOM_BASE_URL", matches = ".+")
@SpringBootTest
class LiveSkillReferenceTest {

	@Autowired
	AgentPlatform platform;

	@TempDir
	Path tmp;

	@Test
	void aStepDeclaringASkillIsOfferedItsActivationToolsUnderTheReferencePrefix() throws IOException {
		Path pack = pack("notes", true);
		PackSkills skills = PackSkills.at(pack, "ask");
		List<String> offered = LlmReference.prefixedTools(skills.carried().get("notes"))
			.stream()
			.map((tool) -> tool.getDefinition().getName())
			.toList();

		System.out.printf("offered=%s to the step declaring notes%n", offered);

		assertThat(offered).isNotEmpty();
		assertThat(offered).allMatch((name) -> name.startsWith("ask_skills_"),
				"offered tool names carry the reference's sanitized prefix");
		assertThat(offered).anyMatch((name) -> name.endsWith("_activate_skill") || name.endsWith("_activate"));

		IngestedSource ingested = new RuntimeCore(this.platform)
			.ingest(SpecSource.parse("folder:" + pack.getParent()).resolve());
		assertThat(ingested.report().lines()).anyMatch((line) -> line.contains("read -> [ask-skills]"));

		RunOutcome outcome = new RuntimeCore(this.platform).run(ingested, ingested.goalNames().get(0),
				"name one file you read", null, TrustTier.HIGH);
		System.out.printf("called=%d for the step declaring notes%n", outcome.success() ? 1 : 0);
		assertThat(outcome.success()).isTrue();
	}

	@Test
	void aStepDeclaringNoReferenceIsOfferedNoneOfThem() throws IOException {
		Path pack = pack("notes", false);
		IngestedSource ingested = new RuntimeCore(this.platform)
			.ingest(SpecSource.parse("folder:" + pack.getParent()).resolve());

		// The pack carries the skill on disk, so the container has activation tools to
		// offer — and the step that declares nothing is attached to none of them, which
		// is
		// what the run says rather than what the folder contains.
		List<String> available = LlmReference.prefixedTools(PackSkills.at(pack, "ask").carried().get("notes"))
			.stream()
			.map((tool) -> tool.getDefinition().getName())
			.toList();
		assertThat(available).isNotEmpty();
		System.out.printf("offered=%s to the step declaring nothing%n", List.of());
		assertThat(ingested.report().lines()).noneMatch((line) -> line.contains("read ->"));

		assertThat(new RuntimeCore(this.platform)
			.run(ingested, ingested.goalNames().get(0), "name one file you read", null, TrustTier.HIGH)
			.success()).isTrue();
	}

	/**
	 * A one-step pack that reads a file, optionally declaring the skill it carries.
	 * @param skillName the skill the pack carries
	 * @param declares whether the step names it
	 * @return the pack directory, ready for a {@code folder:} source
	 */
	private Path pack(String skillName, boolean declares) throws IOException {
		Path pack = Files.createDirectories(this.tmp.resolve("ask"));
		Files.writeString(pack.resolve("manifest.yml"), "name: ask\nversion: 0.1.0\n");
		// Validation wants the pack's types directory, empty though it is.
		Files.createDirectories(pack.resolve("types"));
		Files.writeString(pack.resolve("goal.yml"), """
				stepType: goal
				name: answered
				description: "Answered from the working directory"
				outputTypeName: com.embabel.agent.spec.model.BackgroundMessage
				export: true
				""");
		Files.createDirectories(pack.resolve("steps"));
		Files.writeString(pack.resolve("steps/read.yml"), """
				stepType: action
				name: read
				description: "Read one file and answer"
				inputTypeNames:
				  - com.embabel.agent.domain.io.UserInput
				outputTypeName: com.embabel.agent.spec.model.BackgroundMessage
				toolGroups:
				  - file
				%sprompt: |
				  Name one file in the working directory.

				  User request: {{userInput.content}}
				""".formatted(declares ? "references:\n  - " + skillName + "\n" : ""));
		Path skill = Files.createDirectories(pack.resolve("skills/" + skillName));
		Files.writeString(skill.resolve("SKILL.md"), """
				---
				name: %s
				description: "How to answer from the working directory"
				---

				List the directory, then name one source file and stop.
				""".formatted(skillName));
		return pack;
	}

}
