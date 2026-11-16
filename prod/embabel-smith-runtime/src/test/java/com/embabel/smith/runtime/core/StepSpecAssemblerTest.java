/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import com.embabel.agent.spec.model.PromptedActionSpec;
import com.embabel.agent.spec.model.StepSpec;
import com.embabel.agent.spec.yml.YmlStepSpecRepository;
import com.embabel.smith.common.io.Yaml;
import com.embabel.smith.runtime.TestPacks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StepSpecAssemblerTest {

	private final StepSpecAssembler assembler = new StepSpecAssembler();

	@Test
	void assemblesASinglePackWithRenamedGoalAndSteps(@TempDir Path tmp) throws IOException {
		Path target = assembler.assemble(List.of(TestPacks.root().resolve("extract")), tmp.resolve("steps"));

		assertThat(Files.isRegularFile(target.resolve("goal-0.yml"))).isTrue();
		assertThat(Files.isRegularFile(target.resolve("0-extractTopic.yml"))).isTrue();
		// manifest and types are not step specs
		assertThat(Files.isRegularFile(target.resolve("manifest.yml"))).isFalse();
		assertThat(Files.isDirectory(target.resolve("types"))).isFalse();
	}

	@Test
	void sameNamedStepFilesFromDifferentPacksDoNotClobberEachOther(@TempDir Path tmp) throws IOException {
		Path target = assembler.assemble(List.of(TestPacks.root().resolve("dup-a"), TestPacks.root().resolve("dup-b")),
				tmp.resolve("steps"));

		assertThat(Files.isRegularFile(target.resolve("goal-0.yml"))).isTrue();
		assertThat(Files.isRegularFile(target.resolve("goal-1.yml"))).isTrue();
		assertThat(Files.isRegularFile(target.resolve("0-aStep.yml"))).isTrue();
		assertThat(Files.isRegularFile(target.resolve("1-bStep.yml"))).isTrue();
	}

	@Test
	void aPackWithoutGoalYamlFails(@TempDir Path tmp) {
		assertThatThrownBy(
				() -> assembler.assemble(List.of(TestPacks.root().resolve("dup-a/steps")), tmp.resolve("steps")))
			.isInstanceOf(IOException.class)
			.hasMessageContaining("no goal.yml");
	}

	@Test
	void aPinnedModelReachesTheStepSpecEmabelLoads(@TempDir Path tmp) throws IOException {
		Path target = assembler.assemble(List.of(TestPacks.root().resolve("extract")), tmp.resolve("steps"),
				"claude-sonnet-4-6");

		assertThat(loadActionStep(target).getLlm().getModel()).isEqualTo("claude-sonnet-4-6");
	}

	@Test
	void noPinnedModelLeavesEveryStepAsThePackWroteIt(@TempDir Path tmp) throws IOException {
		Path target = assembler.assemble(List.of(TestPacks.root().resolve("extract")), tmp.resolve("steps"), "");

		assertThat(stagedStep(target, "0-extractTopic.yml")).doesNotContainKey("llm");
	}

	@Test
	void pinningAModelKeepsTheOtherSettingsOfAStepThatAlreadyNamesOne(@TempDir Path tmp) throws IOException {
		Path pack = TestPacks.library(tmp.resolve("lib"), "extract").resolve("extract");
		Map<String, Object> step = stagedStep(pack, "steps/extractTopic.yml");
		step.put("llm", Map.of("temperature", 0.9));
		Yaml.mapper().writeValue(pack.resolve("steps/extractTopic.yml").toFile(), step);

		Path target = assembler.assemble(List.of(pack), tmp.resolve("steps"), "gpt-4.1");

		Map<String, Object> llm = stagedStep(target, "0-extractTopic.yml");
		assertThat(asMap(llm.get("llm"))).containsEntry("model", "gpt-4.1").containsEntry("temperature", 0.9);
	}

	private PromptedActionSpec loadActionStep(Path stepsDir) {
		for (StepSpec<?> step : new YmlStepSpecRepository(stepsDir.toString(), List.of()).findAll()) {
			if (step instanceof PromptedActionSpec action) {
				return action;
			}
		}
		throw new IllegalStateException("no prompted action step loaded from " + stepsDir);
	}

	private Map<String, Object> stagedStep(Path dir, String file) throws IOException {
		return asMap(Yaml.mapper().readValue(dir.resolve(file).toFile(), Object.class));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> asMap(Object value) {
		return (Map<String, Object>) value;
	}

}
