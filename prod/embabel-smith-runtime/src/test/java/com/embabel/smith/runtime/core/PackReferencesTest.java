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

import com.embabel.agent.api.reference.LlmReference;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.skills.script.ProcessSkillScriptExecutionEngine;
import com.embabel.agent.skills.script.ScriptTool;
import com.embabel.agent.spec.model.StepSpecContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests of what a pack's {@code references.yml} becomes.
 *
 * <p>
 * <b>What is asserted, and why that is the reachable set.</b> A reference reaches a model
 * as three things: the description and notes its {@code contribution()} writes into the
 * prompt, and the tool names its {@code tools()} list under the naming strategy
 * {@code withReference} applies. All three are readable from the value without a model or
 * a deployed scope — which matters, because a deployed {@code AgentScope} publishes no
 * tool list, so nothing downstream could be asked what a step was offered. The last test
 * also walks the values through the seam that puts them in front of a step, so a
 * reference that reads correctly here but is not the object handed over would still fail.
 *
 * <p>
 * The four kinds are the whole construction surface, so the refusals are tested alongside
 * the resolutions: an unknown kind, a path that is not there, and two names that sanitize
 * to one tool prefix. A pack that declares nothing is the control — its carried set is
 * what {@link PackSkills} reads, unchanged.
 */
class PackReferencesTest {

	@TempDir
	Path tmp;

	@Test
	void aDirectoryReferenceArrivesWithItsDescriptionItsNotesAndItsPrefixedTools() throws IOException {
		PackReferences refs = PackReferences.at(pack("""
				references:
				  - name: docs
				    kind: directory
				    path: docs
				"""), "survey");

		LlmReference docs = refs.carried().get("docs");
		assertThat(docs).as("the declared directory reference").isNotNull();
		assertThat(docs.getName()).isEqualTo("docs");
		assertThat(docs.toolPrefix()).isEqualTo("docs");
		// The three things a reference can give a call, each read off the value itself.
		assertThat(docs.getDescription()).contains("docs");
		assertThat(docs.contribution()).contains("Reference: docs").contains("Description:").contains("Notes:");
		assertThat(docs.notes()).contains("read-only");
		assertThat(listed(docs)).contains("docs_readFile", "docs_listFiles", "docs_findFiles");
		assertThat(listed(docs)).allMatch((name) -> name.startsWith("docs_"));
	}

	@Test
	void theDirectoryReferenceAStepIsGivenIsTheOneCarriedUnderItsName() throws IOException {
		PackReferences refs = PackReferences.at(pack("""
				references:
				  - name: docs
				    kind: directory
				    path: docs
				"""), "survey");
		StepSpecContext context = new StepSpecContext("smith-spec-scope",
				DataDictionary.fromClasses("test", UserInput.class), List.of(), List.of(), List.of());

		StepSpecContext carried = ReferenceAwareStepSpecRepository.carrying(context, refs.references());

		assertThat(carried.getAvailableReferences()).extracting(LlmReference::getName).containsExactly("docs");
		// Not a copy of it: the object in the context is the one whose tools and notes a
		// step is given, so what the test above reads off the value is what the call
		// gets.
		assertThat(carried.getAvailableReferences().get(0)).isSameAs(refs.carried().get("docs"));
	}

	@Test
	void aDocumentReferenceBringsTheFileIntoTheNotes() throws IOException {
		PackReferences refs = PackReferences.at(pack("""
				references:
				  - name: domain-guide
				    kind: document
				    path: notes/domain.md
				"""), "survey");

		LlmReference document = refs.carried().get("domain-guide");
		assertThat(document).isNotNull();
		assertThat(document.toolPrefix()).isEqualTo("domain_guide");
		assertThat(document.notes()).contains("Domain of the survey pack");
		// A document says its content rather than offering tools to go and get it.
		assertThat(listed(document)).isEmpty();
	}

	@Test
	void aBriefReferenceHoldsItsTextAndAPathlessOneSaysItSaysNothing() throws IOException {
		PackReferences withText = PackReferences.at(pack("""
				references:
				  - name: house-style
				    kind: brief
				    path: briefs/style.md
				"""), "survey");
		PackReferences withoutPath = PackReferences.at(pack("""
				references:
				  - name: house-style
				    kind: brief
				"""), "survey");

		assertThat(withText.carried().get("house-style").notes()).contains("Answer in one sentence.");
		assertThat(withText.notes()).noneMatch((line) -> line.contains("contributes no text"));
		assertThat(withoutPath.carried()).containsKey("house-style");
		assertThat(withoutPath.notes()).anyMatch((line) -> line.contains("contributes no text"));
	}

	@Test
	void aSkillsEntryBecomesAContainerWhoseToolsAreItsOwn() throws IOException {
		PackReferences refs = PackReferences.at(pack("""
				references:
				  - name: extra-skills
				    kind: skills
				    path: extra
				"""), "survey");

		assertThat(listed(refs.carried().get("extra-skills"))).contains("extra_skills_activate",
				"extra_skills_readResource");
		assertThat(refs.carried().get("extra-skills").notes()).contains("extra-style");
	}

	@Test
	void oneFolderDeclaredTwiceHandsItsScriptsToTheEntryThatAskedAndNotToTheOther() throws IOException {
		Path packDir = pack("""
				references:
				  - name: extra-skills
				    kind: skills
				    path: extra
				  - name: extra-skills-running
				    kind: skills
				    path: extra
				    scripts: true
				""");
		Path scripts = Files.createDirectories(packDir.resolve("extra/extra-style/scripts"));
		Files.writeString(scripts.resolve("map.sh"), "#!/bin/bash\necho mapped\n");
		scripts.resolve("map.sh").toFile().setExecutable(true);

		// The engine is on for both declarations, so the only thing that separates them
		// is
		// the switch — which is the pair 6.3 exists to make declarable.
		PackReferences refs = PackReferences.at(packDir, "survey", Set.of(),
				ProcessSkillScriptExecutionEngine.confinedTo(packDir.toString()));

		assertThat(refs.scriptsAskedFor()).as("only the entry that wrote the switch asked")
			.containsExactly("extra-skills-running");
		assertThat(scriptTools(refs.carried().get("extra-skills-running")))
			.as("the entry that asked hands out the script")
			.isNotEmpty();
		assertThat(scriptTools(refs.carried().get("extra-skills")))
			.as("the plain entry is the reading shape, engine or not")
			.isEmpty();
	}

	@Test
	void askingForScriptsOnAKindThatHandsOutNoneIsNamedRatherThanIgnored() throws IOException {
		PackReferences refs = PackReferences.at(pack("""
				references:
				  - name: domain-guide
				    kind: directory
				    path: notes
				    scripts: true
				"""), "survey");

		// The reference is still carried — it is the switch that means nothing here, not
		// the
		// folder — so the pack author learns that from the run rather than from a missing
		// script tool they were never told to expect.
		assertThat(refs.carried()).containsKey("domain-guide");
		assertThat(refs.scriptsAskedFor()).isEmpty();
		assertThat(refs.notes()).anyMatch((line) -> line.contains("asks for scripts") && line.contains("directory"));
	}

	@Test
	void aScriptsValueThatIsNotABooleanIsNamedAndReadAsOff() throws IOException {
		PackReferences refs = PackReferences.at(pack("""
				references:
				  - name: extra-skills
				    kind: skills
				    path: extra
				    scripts: "true"
				"""), "survey");

		assertThat(refs.scriptsAskedFor()).as("a quoted switch is not a switch").isEmpty();
		assertThat(refs.notes())
			.anyMatch((line) -> line.contains("scripts: true") && line.contains("neither true nor false"));
	}

	@Test
	void aKindWithNoRecipeIsRefusedAndNamedRatherThanBuiltByGuesswork() throws IOException {
		PackReferences refs = PackReferences.at(pack("""
				references:
				  - name: page
				    kind: com.embabel.agent.api.reference.WebPage
				    path: https://example.org
				"""), "survey");

		assertThat(refs.carried()).as("nothing carried by a kind with no arm in the switch").isEmpty();
		// Named, and with the kinds that do work, so the pack author is not left to
		// guess which four the runtime can build. The platform does ship the class named
		// here; refusing it is the point, not an absence of it.
		assertThat(refs.notes()).anyMatch((line) -> line.contains("kind 'com.embabel.agent.api.reference.WebPage'")
				&& line.contains("directory") && line.contains("brief"));
	}

	@Test
	void aDeclaredPathThatIsNotThereIsRefusedAndNamed() throws IOException {
		PackReferences missingFolder = PackReferences.at(pack("""
				references:
				  - name: docs
				    kind: directory
				    path: nowhere
				"""), "survey");
		PackReferences fileWhereFolderExpected = PackReferences.at(pack("""
				references:
				  - name: docs
				    kind: directory
				    path: notes/domain.md
				"""), "survey");
		PackReferences folderWhereFileExpected = PackReferences.at(pack("""
				references:
				  - name: doc
				    kind: document
				    path: docs
				"""), "survey");

		assertThat(missingFolder.carried()).isEmpty();
		assertThat(missingFolder.notes()).anyMatch((line) -> line.contains("points at 'nowhere'"));
		assertThat(fileWhereFolderExpected.carried()).isEmpty();
		assertThat(fileWhereFolderExpected.notes()).anyMatch((line) -> line.contains("points at 'notes/domain.md'"));
		assertThat(folderWhereFileExpected.carried()).isEmpty();
		assertThat(folderWhereFileExpected.notes()).anyMatch((line) -> line.contains("points at 'docs'"));
	}

	@Test
	void twoNamesThatSanitizeToTheSameToolPrefixCarryOnlyTheFirst() throws IOException {
		PackReferences refs = PackReferences.at(pack("""
				references:
				  - name: repo-notes
				    kind: directory
				    path: docs
				  - name: repo.notes
				    kind: directory
				    path: notes
				"""), "survey");

		assertThat(refs.carried()).containsOnlyKeys("repo-notes");
		assertThat(refs.notes()).anyMatch(
				(line) -> line.contains("repo.notes") && line.contains("repo-notes") && line.contains("repo_notes"));
		// The prefix arithmetic is the platform's, so the second name's tools would have
		// overwritten the first's rather than sitting beside them.
		assertThat(listed(refs.carried().get("repo-notes"))).allMatch((name) -> name.startsWith("repo_notes_"));
	}

	@Test
	void aPackThatDeclaresNothingCarriesExactlyWhatItsSkillFoldersCarry() throws IOException {
		Path packDir = pack(null);
		Files.createDirectories(packDir.resolve("skills/own-skill"));
		Files.writeString(packDir.resolve("skills/own-skill/SKILL.md"),
				"---\nname: own-skill\ndescription: A skill for the test\n---\n\nRead these paths.\n");

		PackReferences refs = PackReferences.at(packDir, "survey");
		PackSkills skills = PackSkills.at(packDir, "survey");

		assertThat(refs.carried().keySet()).isEqualTo(skills.carried().keySet());
		// The container, named as PackSkills names it. Compared by name rather than by
		// object: two reads of the same folder build Skills values that are not equal to
		// each other, because the value carries its loaders.
		assertThat(refs.references()).extracting(LlmReference::getName).containsExactly("survey-skills");
		// The unchanged case is silent: no line about a file that was never there.
		assertThat(refs.notes()).containsExactlyElementsOf(skills.notes());
		assertThat(refs.forms().values()).containsOnly(PackReferences.DIRECT);
	}

	@Test
	void theFacadeListsOneToolWhereTheDirectFormListsTheWholeSurface() throws IOException {
		Path packDir = pack("""
				references:
				  - name: docs
				    kind: directory
				    path: docs
				""");

		List<String> direct = listed(PackReferences.at(packDir, "survey").carried().get("docs"));
		List<String> facade = listed(PackReferences.at(packDir, "survey", Set.of("docs")).carried().get("docs"));

		assertThat(direct).hasSizeGreaterThan(1);
		assertThat(facade).containsExactly("docs");
		// What the facade hides is exactly what the direct form lists, so the two forms
		// differ in the listing and in nothing else a step could notice.
		assertThat(facade).doesNotContainAnyElementsOf(direct);
		LlmReference directRef = PackReferences.at(packDir, "survey").carried().get("docs");
		LlmReference facadeRef = PackReferences.at(packDir, "survey", Set.of("docs")).carried().get("docs");
		assertThat(facadeRef.contribution()).isEqualTo(directRef.contribution());
		assertThat(facadeRef.notes()).isEqualTo(directRef.notes());
	}

	@Test
	void theFormEachReferenceWasOfferedInIsStatedAndDefaultsToDirect() throws IOException {
		Path packDir = pack("""
				references:
				  - name: docs
				    kind: directory
				    path: docs
				  - name: notes
				    kind: directory
				    path: notes
				""");

		assertThat(PackReferences.at(packDir, "survey").forms())
			.containsExactlyInAnyOrderEntriesOf(Map.of("docs", PackReferences.DIRECT, "notes", PackReferences.DIRECT));
		Map<String, String> forms = PackReferences.at(packDir, "survey", Set.of("docs")).forms();
		assertThat(forms).containsEntry("docs", PackReferences.FACADE).containsEntry("notes", PackReferences.DIRECT);
		assertThat(PackReferences.at(packDir, "survey", Set.of("docs")).notes())
			.anyMatch((line) -> line.contains("docs") && line.contains("unfolding facade"));
	}

	@Test
	void unfoldingSomethingThatWouldHideNothingIsSaidRatherThanDone() throws IOException {
		Path packDir = pack("""
				references:
				  - name: house-style
				    kind: brief
				    path: briefs/style.md
				""");

		PackReferences refs = PackReferences.at(packDir, "survey", Set.of("house-style"));

		// A brief offers no tools, so a facade over it would list nothing at all and the
		// step would be left unable to reach the reference it was given.
		assertThat(refs.forms()).containsEntry("house-style", PackReferences.DIRECT);
		assertThat(refs.notes()).anyMatch((line) -> line.contains("no tools to hide"));
	}

	@Test
	void unfoldingANameThePackCarriesNothingForIsReported() throws IOException {
		PackReferences refs = PackReferences.at(pack("""
				references:
				  - name: docs
				    kind: directory
				    path: docs
				"""), "survey", Set.of("typo"));

		assertThat(refs.forms()).containsEntry("docs", PackReferences.DIRECT);
		assertThat(refs.notes()).anyMatch((line) -> line.contains("typo"));
	}

	/**
	 * The tool names a reference is listed under: its own tools as the naming strategy it
	 * carries would rename them, which is what {@code withReference} registers.
	 * @param reference the value to list
	 * @return the names, in listing order
	 */
	private static List<String> listed(LlmReference reference) {
		return LlmReference.prefixedTools(reference).stream().map((tool) -> tool.getDefinition().getName()).toList();
	}

	/**
	 * The scripts a reference actually hands out as callable, asked of the reference
	 * rather than inferred from the engine or from the listed names — two names for one
	 * folder differ by prefix, so their listed names would not compare.
	 * @param reference the value to ask
	 * @return the callable scripts, by name
	 */
	private static List<String> scriptTools(LlmReference reference) {
		return reference.tools()
			.stream()
			.filter(ScriptTool.class::isInstance)
			.map((tool) -> tool.getDefinition().getName())
			.toList();
	}

	/**
	 * A definition directory carrying one of each kind of thing a reference can point at,
	 * plus a skill folder, so a declaration in the file has something to resolve against.
	 * @param references the contents of {@code references.yml}, or {@code null} for a
	 * pack that carries no such file
	 * @return the pack directory
	 */
	private Path pack(String references) throws IOException {
		Path packDir = Files.createTempDirectory(this.tmp, "pack-");
		Files.createDirectories(packDir.resolve("docs"));
		Files.writeString(packDir.resolve("docs/readme.md"), "The docs folder.\n");
		Files.createDirectories(packDir.resolve("notes"));
		Files.writeString(packDir.resolve("notes/domain.md"), "Domain of the survey pack\n");
		Files.createDirectories(packDir.resolve("briefs"));
		Files.writeString(packDir.resolve("briefs/style.md"), "Answer in one sentence.\n");
		Files.createDirectories(packDir.resolve("extra/extra-style"));
		Files.writeString(packDir.resolve("extra/extra-style/SKILL.md"),
				"---\nname: extra-style\ndescription: A skill for the test\n---\n\nAnswer in one sentence.\n");
		Files.createDirectories(packDir.resolve("steps"));
		Files.writeString(packDir.resolve("manifest.yml"), "name: survey\nversion: 0.1.0\n");
		if (references != null) {
			Files.writeString(packDir.resolve(PackReferences.REFERENCES_FILE), references);
		}
		return packDir;
	}

}
