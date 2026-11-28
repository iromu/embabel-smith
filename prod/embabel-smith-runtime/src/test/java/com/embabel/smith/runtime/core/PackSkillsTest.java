/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests of the skill containers a definition is offered.
 *
 * <p>
 * <b>The names are pinned, not checked for shape.</b> Embabel prefixes every callable
 * name with the sanitized reference name, so {@code survey_skills_repo-map_read} — not
 * {@code repo-map_read} — is what reaches the wire. A test that only asserted "some
 * reference exists" would keep passing if the naming changed, and the pack's skill text,
 * which points at paths rather than tool names, would stop matching reality silently.
 *
 * <p>
 * Everything here is disk and front matter: no platform, no model.
 */
class PackSkillsTest {

	@TempDir
	Path tmp;

	@Test
	void anAgentsOwnSkillsAreOfferedUnderItsOwnName() throws IOException {
		Path pack = packWith("solo", "own", "notes", "Where things live.");

		PackSkills skills = PackSkills.at(pack, "solo");

		assertThat(skills.carried().get("notes")).isNotNull();
		assertThat(skills.carried().get("notes").getName()).isEqualTo("solo-skills");
		assertThat(skills.references()).extracting((reference) -> reference.getName()).containsExactly("solo-skills");
	}

	@Test
	void aFoldersSharedSkillsAreOfferedUnderSharedSkills() throws IOException {
		sharedSkill("shared-notes", "Where things live, for everyone in this folder.");
		Path pack = packWith("solo", null, null, null);

		PackSkills skills = PackSkills.at(pack, "solo");

		assertThat(skills.carried().get("shared-notes").getName()).isEqualTo(PackSkills.SHARED_REFERENCE_NAME);
	}

	@Test
	void aShadowedNameIsServedByTheAgentsOwnCopyAndSaysWhichFolderAnswered() throws IOException {
		sharedSkill("notes", "The shared copy.");
		Path pack = packWith("solo", "own", "notes", "The agent's own copy.");

		PackSkills skills = PackSkills.at(pack, "solo");

		assertThat(skills.carried().get("notes").getName()).isEqualTo("solo-skills");
		// Both folders are named by Path, which is the ruling this case exists for: the
		// answer to "which copy ran" is read from where the loader says it loaded from,
		// not from the skill's own text.
		assertThat(skills.notes())
			.anyMatch((line) -> line.contains("shadowed") && line.contains(pack.resolve("skills/own").toString())
					&& line.contains(this.tmp.resolve("skills/notes").toString()));
	}

	@Test
	void aFolderTheLoaderRefusesIsNamedAndTheRestCarryOn() throws IOException {
		Path pack = packWith("solo", "own", "good", "Readable.");
		// A metadata value that is a list rather than a string drops the whole skill from
		// the loader, which is the refusal this asserts rather than a refusal invented to
		// suit the test.
		Path broken = Files.createDirectories(pack.resolve("skills/broken"));
		Files.writeString(broken.resolve("SKILL.md"),
				"---\nname: broken\ndescription: Refused\nmetadata:\n  tag:\n    - one\n---\n\nBody.\n");

		PackSkills skills = PackSkills.at(pack, "solo");

		assertThat(skills.carried()).containsKey("good");
		assertThat(skills.carried()).doesNotContainKey("broken");
		assertThat(skills.notes()).anyMatch((line) -> line.contains("refused") && line.contains("broken"));
	}

	@Test
	void theScriptStateIsStatedWithTheSwitchThatChangesIt() throws IOException {
		Path pack = packWith("solo", "own", "notes", "Notes with scripts.");
		Files.createDirectories(pack.resolve("skills/own/scripts"));
		Files.writeString(pack.resolve("skills/own/scripts/count.sh"), "echo 1\n");

		PackSkills skills = PackSkills.at(pack, "solo");

		assertThat(skills.notes()).anyMatch((line) -> line.contains("skill scripts")
				&& line.contains(PackSkills.SCRIPT_ENGINE_PROPERTY) && line.contains("not executed"));
	}

	@Test
	void aPackWithNoSkillsAnywhereReportsNothing() throws IOException {
		Path pack = packWith("solo", null, null, null);

		assertThat(PackSkills.at(pack, "solo").notes()).isEmpty();
	}

	/**
	 * A definition directory, optionally carrying its own skills.
	 * @param agentName the definition's name
	 * @param ownFolder the name of its own skills folder, or {@code null} for none
	 * @param skillName the name the skill inside it declares
	 * @param description what it says about itself
	 * @return the pack directory
	 */
	private Path packWith(String agentName, String ownFolder, String skillName, String description) throws IOException {
		Path pack = Files.createDirectories(this.tmp.resolve(agentName));
		Files.writeString(pack.resolve("manifest.yml"), "name: " + agentName + "\nversion: 0.1.0\n");
		if (skillName != null) {
			Path skill = Files.createDirectories(pack.resolve("skills/" + (ownFolder != null ? ownFolder : skillName)));
			Files.writeString(skill.resolve("SKILL.md"),
					"---\nname: " + skillName + "\ndescription: " + description + "\n---\n\nRead the paths.\n");
		}
		return pack;
	}

	/**
	 * A skill in the folder's shared {@code skills/} directory — the parent of every
	 * definition beside it.
	 * @param skillName the name it declares
	 * @param description what it says about itself
	 */
	private void sharedSkill(String skillName, String description) throws IOException {
		Path skill = Files.createDirectories(this.tmp.resolve("skills/" + skillName));
		Files.writeString(skill.resolve("SKILL.md"),
				"---\nname: " + skillName + "\ndescription: " + description + "\n---\n\nRead the paths.\n");
	}

}
