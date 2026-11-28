/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.embabel.agent.skills.script.NoOpExecutionEngine;
import com.embabel.agent.skills.script.ProcessSkillScriptExecutionEngine;
import com.embabel.agent.spec.yml.YmlStepSpecRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The per-step half of the script statement: not whether a pack has scripts, which the
 * pack-level line already says, but whether the step in front of a model was handed one.
 *
 * <p>
 * <b>Why it has to be said per step.</b> {@link PackSkills}' line is true of a definition
 * — it carries scripts, an engine does or does not offer them — and a run of several
 * steps reads it once. Whether a particular step could reach a script is decided where a
 * declaration becomes a reference handed to that step, so that is where it is asked, and
 * it is asked of the reference: the reference is asked what it offers, so a script the
 * engine refused never counts as offered here either.
 *
 * <p>
 * <b>The two states the box insists stay distinct.</b> A pack with scripts and the switch
 * off still says the pack carries them, at pack level, while saying of the step that it
 * offers no script tool; a pack with scripts and the engine on says both. Neither is left
 * for the reader to infer from the other.
 *
 * <p>
 * Runs with no model and no endpoint, and executes no script — the question is what is
 * offered.
 *
 * @author Ivan Rodriguez
 */
class ScriptOfferPerStepTest {

	@TempDir
	Path tmp;

	@Test
	void aStepAttachedToAScriptBearingContainerIsToldSo() throws IOException {
		Path pack = packWithScript();
		ReferenceAwareStepSpecRepository steps = new ReferenceAwareStepSpecRepository(
				new YmlStepSpecRepository(steps().toString(), List.of()), Map.of("locate", PackReferences.at(pack,
						"survey", Set.of(), ProcessSkillScriptExecutionEngine.confinedTo(pack.toString()))));

		steps.findAll();

		assertThat(steps.scriptsOffered()).as("the step is named, and so is the container that handed it the callable")
			.containsExactly("locate can call a script via [survey-skills]");
	}

	@Test
	void theSameStepWithTheEngineOffOffersNoScriptWhileThePackStillCarriesOne() throws IOException {
		Path pack = packWithScript();
		ReferenceAwareStepSpecRepository steps = new ReferenceAwareStepSpecRepository(
				new YmlStepSpecRepository(steps().toString(), List.of()),
				Map.of("locate", PackReferences.at(pack, "survey", Set.of(), NoOpExecutionEngine.INSTANCE)));

		steps.findAll();

		assertThat(steps.scriptsOffered()).as("switched off, the step is given no script to call")
			.containsExactly("locate offers no script tool");
		assertThat(PackReferences.at(pack, "survey", Set.of(), NoOpExecutionEngine.INSTANCE).notes())
			.as("while the pack still says it carries scripts, naming the skill that holds them")
			.anyMatch((line) -> line.contains("skill scripts") && line.contains("(repo-map)"));
	}

	/**
	 * Write a staged step directory with the one step under test, declaring the skill.
	 * @return the directory a {@code YmlStepSpecRepository} reads
	 */
	private Path steps() {
		try {
			Path dir = Files.createDirectories(this.tmp.resolve("steps"));
			Files.writeString(dir.resolve("0-locate.yml"), """
					stepType: action
					name: locate
					description: "Locate the files the request is about"
					inputTypeNames:
					  - com.embabel.agent.domain.io.UserInput
					outputTypeName: RepoMap
					toolGroups:
					  - file
					references:
					  - repo-map
					prompt: |
					  Find the files. {{userInput.content}}
					""");
			return dir;
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/**
	 * A definition carrying one script-bearing skill, so the only thing that differs
	 * between the two states above is the engine.
	 * @return the pack directory
	 */
	private Path packWithScript() {
		try {
			Path skill = Files.createDirectories(this.tmp.resolve("survey/skills/repo-map"));
			Files.writeString(skill.resolve("SKILL.md"),
					"---\nname: repo-map\ndescription: A skill for the test\n---\n\nRead these paths.\n");
			Path script = skill.resolve("scripts");
			Files.createDirectories(script);
			Files.writeString(script.resolve("map.sh"), "#!/bin/bash\necho mapped\n");
			script.resolve("map.sh").toFile().setExecutable(true);
			Files.createDirectories(this.tmp.resolve("survey/steps"));
			Files.writeString(this.tmp.resolve("survey/manifest.yml"), "name: survey\nversion: 0.1.0\n");
			return this.tmp.resolve("survey");
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
