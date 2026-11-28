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
 * The pack that carries nothing, said differently from the pack that carries scripts and
 * is switched off.
 *
 * <p>
 * <b>Why two sentences rather than one with a flag.</b> Both packs offer a step no script
 * tool, and a run that reports only that would read the same whichever reason it had. So
 * the run says two things and they vary independently: whether the pack carries scripts
 * at all, and whether the selected engine executes them. This class pins that the pair is
 * different for the two packs, by comparing the lines rather than reading them one at a
 * time — an implementation that collapsed both into one sentence would fail the
 * comparison rather than passing quietly.
 *
 * <p>
 * <b>What the per-step line does here.</b> It says the same thing for both packs —
 * neither step was handed a script — which is the point of having it separately: it is
 * the state the model sees, and the two-pack difference lives in the pack-level line.
 *
 * <p>
 * Runs with no model and no endpoint, and executes no script.
 *
 * @author Ivan Rodriguez
 */
class ScriptCarriageTest {

	@TempDir
	Path tmp;

	@Test
	void aPackCarryingNoScriptSaysNoneCarriedRatherThanWithheld() throws IOException {
		PackSkills carried = PackSkills.at(scriptsIn(false), "survey",
				ProcessSkillScriptExecutionEngine.confinedTo(this.tmp.toString()));

		assertThat(scriptLine(carried)).as("an engine that runs scripts, but nothing to run").contains("none carried");
		assertThat(scriptLine(carried)).as("and nothing withheld, because the engine does execute")
			.doesNotContain("not executed");
		assertThat(offers(scriptsIn(false), ProcessSkillScriptExecutionEngine.confinedTo(this.tmp.toString())))
			.as("while the step is still handed no script, which is what the model sees")
			.containsExactly("locate offers no script tool");
	}

	@Test
	void aPackSwitchedOffSaysItCarriesThemAfterAll() throws IOException {
		PackSkills carried = PackSkills.at(scriptsIn(true), "survey", NoOpExecutionEngine.INSTANCE);

		assertThat(scriptLine(carried)).as("the scripts are there, the switch is what stops them")
			.contains("this pack carries them (repo-map)")
			.contains("not executed");
		assertThat(offers(scriptsIn(true), NoOpExecutionEngine.INSTANCE)).as("and the step sees the same nothing")
			.containsExactly("locate offers no script tool");
	}

	@Test
	void carryingNothingAndBeingSwitchedOffAreTwoDifferentSentences() {
		String absent = scriptLine(PackSkills.at(scriptsIn(false), "survey",
				ProcessSkillScriptExecutionEngine.confinedTo(this.tmp.toString())));
		String withheld = scriptLine(PackSkills.at(scriptsIn(true), "survey", NoOpExecutionEngine.INSTANCE));

		assertThat(absent)
			.as("the pack with nothing to run and the pack kept from running are not told the same way: one names "
					+ "what it carries, the other names the switch")
			.isNotEqualTo(withheld);
		assertThat(absent).contains("none carried").doesNotContain("repo-map");
		assertThat(withheld).contains("repo-map").doesNotContain("none carried");
	}

	/**
	 * The one line a definition's notes state the script state in.
	 * @param carried what the run read from the pack
	 * @return the line
	 */
	private static String scriptLine(PackSkills carried) {
		List<String> states = carried.notes().stream().filter((line) -> line.contains("skill scripts")).toList();
		assertThat(states).as("exactly one line states the script state").hasSize(1);
		return states.get(0);
	}

	/**
	 * What the step of that pack was handed in the way of scripts.
	 * @param pack the definition directory
	 * @param engine the engine the run selected
	 * @return the per-step script offers
	 */
	private List<String> offers(Path pack, com.embabel.agent.skills.script.SkillScriptExecutionEngine engine) {
		ReferenceAwareStepSpecRepository steps = new ReferenceAwareStepSpecRepository(
				new YmlStepSpecRepository(steps().toString(), List.of()),
				Map.of("locate", PackReferences.at(pack, "survey", Set.of(), engine)));
		steps.findAll();
		return steps.scriptsOffered();
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
	 * A definition carrying one skill, with or without a script in it — the two packs the
	 * box is about.
	 * @param withScript whether the skill's {@code scripts/} folder carries a file
	 * @return the pack directory
	 */
	private Path scriptsIn(boolean withScript) {
		try {
			Path skill = Files
				.createDirectories(this.tmp.resolve((withScript ? "with" : "without") + "/skills/repo-map"));
			Files.writeString(skill.resolve("SKILL.md"),
					"---\nname: repo-map\ndescription: A skill for the test\n---\n\nRead these paths.\n");
			if (withScript) {
				Path scripts = Files.createDirectories(skill.resolve("scripts"));
				Path script = scripts.resolve("map.sh");
				Files.writeString(script, "#!/bin/bash\necho mapped\n");
				script.toFile().setExecutable(true);
			}
			Path packDir = this.tmp.resolve((withScript ? "with" : "without"));
			Files.createDirectories(packDir.resolve("steps"));
			Files.writeString(packDir.resolve("manifest.yml"), "name: survey\nversion: 0.1.0\n");
			return packDir;
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
