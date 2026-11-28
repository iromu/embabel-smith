/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import com.embabel.agent.api.reference.LlmReference;
import com.embabel.agent.skills.Skills;
import com.embabel.agent.skills.script.ProcessSkillScriptExecutionEngine;
import com.embabel.agent.skills.support.LoadedSkill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two surfaces box 6.3 is about, and the platform fact that decides how they are
 * built.
 *
 * <p>
 * <b>The platform's filter is not the lever.</b> {@code SkillFilter.WITHOUT_SCRIPTS}
 * reads as the way to hand a step the same skills without their scripts, and it is what
 * the change's design named. It does nothing in the platform version this repo builds
 * against: {@code Skills} stores the filter and never consults it — the field is read
 * only by the data class's own {@code copy}, {@code equals}, {@code hashCode} and
 * {@code toString}, and no class in the jar calls {@code accept} — so applying it, before
 * or after the folder is loaded, leaves {@code tools()} still listing the script tool.
 * What does work is the engine: a {@code Skills} asks the engine it holds which languages
 * it claims, so handing that same container the platform's no-op engine yields a second
 * surface over the same skills. This test pins that mechanism, and pins the split as a
 * difference between two objects rather than as a mutation of one.
 *
 * <p>
 * <b>What the twin is allowed to change.</b> Only the tool list. The twin lists the same
 * skills under the same reference name — which matters because Embabel prefixes every
 * callable name with that name, so a second container would invent a second wire name for
 * one skill. The first case below says exactly that: one surface offers the script tool,
 * the other offers only {@code activate}, {@code listResources} and {@code readResource},
 * and the two agree on everything a report or a wire name depends on.
 *
 * <p>
 * <b>Why the negative legs travel with a control.</b> An absence asserted on the
 * script-free surface alone is satisfied by the fixture having no script to offer, so it
 * is compared against the same pack and the same engine on the surface a step meant to
 * execute is given. And a pack carrying no script offers no script tool on either
 * surface, which is the reading of "scripts switched on" that must not invent a tool.
 *
 * <p>
 * Runs with no model and no endpoint. An engine is constructed and never invoked: the
 * question asked of each surface is what it offers, never what running a script would do.
 *
 * @author Ivan Rodriguez
 */
class ScriptSurfaceTest {

	/**
	 * The three tools a skill offers when nothing claims the language its scripts are in.
	 */
	private static final Set<String> READING_TOOLS = Set.of("activate", "listResources", "readResource");

	@TempDir
	Path tmp;

	@Test
	void theTwoSurfacesDifferByTheScriptToolAndNothingElse() throws IOException {
		PackSkills pack = PackSkills.at(scriptBearingPack(), "surface",
				ProcessSkillScriptExecutionEngine.confinedTo(this.tmp.toString()));

		assertThat(names(executing(pack))).as("the surface a step meant to run scripts is given")
			.contains("counter_count");
		assertThat(names(reading(pack))).as("the surface that offers the skill without its scripts")
			.containsExactlyInAnyOrderElementsOf(READING_TOOLS);
		assertThat(skillNames(reading(pack))).as("the same skills, not a thinner pack")
			.isEqualTo(skillNames(executing(pack)));
		assertThat(referenceName(reading(pack))).as("one reference name, so one wire prefix for one skill")
			.isEqualTo(referenceName(executing(pack)));
		assertThat(reading(pack)).as("and two objects, since the difference is what they offer")
			.isNotSameAs(executing(pack));
	}

	@Test
	void aSkillCarryingNoScriptOffersNoScriptToolOnEitherSurface() throws IOException {
		PackSkills pack = PackSkills.at(scriptlessPack(), "surface",
				ProcessSkillScriptExecutionEngine.confinedTo(this.tmp.toString()));

		assertThat(skillNames(executing(pack))).as("the fixture loaded").containsExactly("counter");
		assertThat(executing(pack).getSkills().get(0).hasScripts()).as("and carries no script").isFalse();
		assertThat(names(executing(pack))).containsExactlyInAnyOrderElementsOf(READING_TOOLS);
		assertThat(names(reading(pack))).containsExactlyInAnyOrderElementsOf(READING_TOOLS);
	}

	@Test
	void withNoEngineSelectedThereIsNoSecondSurfaceToChoose() throws IOException {
		PackSkills pack = PackSkills.at(scriptBearingPack(), "surface");

		assertThat(reading(pack)).as("nothing was ever offered, so nothing was split off").isSameAs(executing(pack));
		assertThat(names(reading(pack))).containsExactlyInAnyOrderElementsOf(READING_TOOLS);
	}

	/**
	 * The container a step is given when this run selected an engine.
	 * @param pack what the definition carries
	 * @return its container
	 */
	private static Skills executing(PackSkills pack) {
		return (Skills) pack.carried().values().iterator().next();
	}

	/**
	 * The container a step is given when it was not meant to have the scripts.
	 * @param pack what the definition carries
	 * @return the same skills with their scripts off the tool list
	 */
	private static Skills reading(PackSkills pack) {
		return (Skills) pack.scriptFree().values().iterator().next();
	}

	/**
	 * A definition whose own {@code skills/} holds one skill with one script, with no
	 * shared folder above it.
	 * @return the pack directory
	 * @throws IOException if the fixture cannot be written
	 */
	private Path scriptBearingPack() throws IOException {
		Path skill = pack().resolve("skills").resolve("counter");
		Files.createDirectories(skill.resolve("scripts"));
		Files.writeString(skill.resolve("SKILL.md"), "---\nname: counter\ndescription: Counts things\n---\n\n"
				+ "Run the counter over the paths you were given.\n");
		Files.writeString(skill.resolve("scripts").resolve("count.sh"), "echo 1\n");
		return pack();
	}

	/**
	 * The same shape with the script left out.
	 * @return the pack directory
	 * @throws IOException if the fixture cannot be written
	 */
	private Path scriptlessPack() throws IOException {
		Path skill = pack().resolve("skills").resolve("counter");
		Files.createDirectories(skill);
		Files.writeString(skill.resolve("SKILL.md"),
				"---\nname: counter\ndescription: Counts things\n---\n\n" + "Read the paths you were given.\n");
		return pack();
	}

	/**
	 * The directory standing in for one agent definition.
	 * @return a child of the temp folder, so the shared folder above it does not exist
	 */
	private Path pack() {
		return this.tmp.resolve("pack");
	}

	/**
	 * The callable names a container offers.
	 * @param skills the container
	 * @return its tool names
	 */
	private static List<String> names(Skills skills) {
		return skills.tools().stream().map((tool) -> tool.getDefinition().getName()).toList();
	}

	/**
	 * The skills a container loaded.
	 * @param skills the container
	 * @return their names
	 */
	private static List<String> skillNames(Skills skills) {
		return skills.getSkills().stream().map(LoadedSkill::getName).toList();
	}

	/**
	 * The name a container's callables are prefixed with on the wire.
	 * @param skills the container
	 * @return its reference name
	 */
	private static String referenceName(Skills skills) {
		return ((LlmReference) skills).getName();
	}

}
