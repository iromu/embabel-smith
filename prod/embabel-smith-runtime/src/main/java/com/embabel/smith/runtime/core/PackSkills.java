/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.embabel.agent.api.reference.LlmReference;
import com.embabel.agent.skills.Skills;
import com.embabel.agent.skills.support.LoadedSkill;

/**
 * The skills one agent definition can answer a {@code references} declaration with, as
 * the references a step is actually offered.
 *
 * <p>
 * <b>A step names a skill; the model call is offered a container.</b> Upstream resolves a
 * declared name against {@code StepSpecContext.availableReferences} by the
 * <em>reference's</em> name, so a pack's {@code references: [repo-map]} cannot resolve
 * against a container called {@code <agent>-skills} until something translates it. This
 * class owns that translation: the map it returns goes from the name as the pack carries
 * it to the {@link Skills} object that holds it. The container names are load-bearing
 * beyond the lookup — Embabel prefixes every callable name with the sanitized reference
 * name, so {@code <agent>_skills_<skill>_<script>} is what reaches the wire. They are
 * pinned by test for that reason.
 *
 * <p>
 * <b>Two containers, in a fixed order.</b> The agent's own {@code skills/} becomes
 * {@code <agent>-skills}; the {@code skills/} in the definition's <em>immediate</em>
 * parent becomes {@code shared-skills}. An ancestor further up is never consulted: the
 * parent is also the source root when a source points at a folder of definitions, so a
 * stray {@code skills/} higher up would silently be global to all of them. Where both
 * carry a name, the agent's own copy wins and the run says so — a shared copy that
 * answered while an own copy sat unused would otherwise read as a working pack.
 *
 * <p>
 * <b>What a load refusal costs.</b> Nothing: a folder the loader refuses is named in
 * {@link #notes()} and the rest carry on, because a pack is still runnable without an
 * optional capability. The refused folders are found by diffing what loaded against the
 * folders on disk — the loader keeps no refusal list.
 *
 * @author Ivan Rodriguez
 */
public final class PackSkills {

	/** The reference name a folder's shared {@code skills/} directory contributes. */
	public static final String SHARED_REFERENCE_NAME = "shared-skills";

	/** The switch that decides whether a skill's scripts run, off unless set. */
	public static final String SCRIPT_ENGINE_PROPERTY = "smith.tools.scripts.engine";

	private final List<LlmReference> references;

	private final Map<String, LlmReference> carried;

	private final List<String> notes;

	private PackSkills(List<LlmReference> references, Map<String, LlmReference> carried, List<String> notes) {
		this.references = List.copyOf(references);
		this.carried = Map.copyOf(carried);
		this.notes = List.copyOf(notes);
	}

	/**
	 * Reads the skills one definition can reach, from its own directory and its parent's.
	 * @param packDir the definition's directory
	 * @param agentName the definition's name, from its manifest
	 * @return the containers, the name-to-container map, and what the run must say about
	 * both
	 */
	public static PackSkills at(Path packDir, String agentName) {
		List<String> notes = new ArrayList<>();
		List<LlmReference> references = new ArrayList<>();
		Map<String, LlmReference> carried = new LinkedHashMap<>();
		Map<String, Path> servedBy = new LinkedHashMap<>();
		Container own = load(ownReferenceName(agentName), packDir.resolve("skills"), notes);
		Container shared = load(SHARED_REFERENCE_NAME, packDir.toAbsolutePath().getParent().resolve("skills"), notes);
		// Own first: a name the shared folder also carries is served by this
		// definition's copy, and the folder that answered is named rather than left to
		// the reader to guess — the two locations differ by a prefix only the loader
		// knows.
		for (Container container : List.of(own, shared)) {
			references.add(container.reference());
			for (Map.Entry<String, Path> skill : container.folders().entrySet()) {
				LlmReference previous = carried.putIfAbsent(skill.getKey(), container.reference());
				if (previous != null && previous != container.reference()) {
					notes.add("skill '" + skill.getKey() + "' served by " + servedBy.get(skill.getKey())
							+ "; the copy in " + skill.getValue() + " is shadowed by it");
				}
				else {
					servedBy.putIfAbsent(skill.getKey(), skill.getValue());
				}
			}
		}
		references.removeIf((reference) -> carried.values().stream().noneMatch((held) -> held == reference));
		if (!carried.isEmpty()) {
			notes.add(scriptState(carried));
		}
		return new PackSkills(references, carried, notes);
	}

	/**
	 * The reference name this definition's own skills are offered under.
	 * @param agentName the definition's name
	 * @return the container name, which is also the tool-name prefix on the wire
	 */
	public static String ownReferenceName(String agentName) {
		return agentName + "-skills";
	}

	/**
	 * The references to put in front of this definition's steps.
	 * @return the containers that hold at least one carried skill
	 */
	public List<LlmReference> references() {
		return this.references;
	}

	/**
	 * The skills this definition can answer a declaration with, by the name the pack
	 * writes.
	 * @return carried name to the container that holds it
	 */
	public Map<String, LlmReference> carried() {
		return this.carried;
	}

	/**
	 * What the run has to say about this definition's skills: folders the loader refused,
	 * names served by a shadowing copy, and the script state in force.
	 * @return the report lines
	 */
	public List<String> notes() {
		return this.notes;
	}

	/**
	 * Read one directory as a skill container, naming the folders the loader refused.
	 * @param referenceName the name the container is offered under
	 * @param folder the directory to read, which need not exist
	 * @param notes where to record what the reader could not do
	 * @return the container, with the names that loaded and the folder they came from
	 */
	private static Container load(String referenceName, Path folder, List<String> notes) {
		if (!Files.isDirectory(folder)) {
			return new Container(new Skills(referenceName, "no skills at " + folder, List.of()), Map.of());
		}
		Skills skills = new Skills(referenceName, "Skills carried by " + folder, List.of())
			.withLocalSkills(folder.toString());
		Map<String, Path> folders = new LinkedHashMap<>();
		Set<Path> loaded = new LinkedHashSet<>();
		for (LoadedSkill skill : skills.getSkills()) {
			Path skillFolder = skill.getBasePath().toAbsolutePath().normalize();
			folders.put(skill.getName(), skillFolder);
			loaded.add(skillFolder);
		}
		for (Path candidate : skillFolders(folder)) {
			if (!loaded.contains(candidate)) {
				notes.add("skill folder refused by the loader: " + candidate);
			}
		}
		return new Container(skills, folders);
	}

	/**
	 * The folders that look like skills, whether or not the loader accepted them — the
	 * control for a silent refusal.
	 * @param folder the directory holding them
	 * @return the immediate subdirectories carrying a {@code SKILL.md}
	 */
	private static Set<Path> skillFolders(Path folder) {
		Set<Path> found = new LinkedHashSet<>();
		try (Stream<Path> children = Files.list(folder)) {
			children.filter(Files::isDirectory)
				.map((child) -> child.toAbsolutePath().normalize())
				.filter((child) -> Files.isRegularFile(child.resolve("SKILL.md")))
				.forEach(found::add);
		}
		catch (IOException ex) {
			// Unreadable: the loader's own refusal of the directory shows up as every
			// skill
			// it expected missing from the loaded set, which is the same disclosure.
			return found;
		}
		return found;
	}

	/**
	 * The script state in force, named as a switch rather than left as an absence a
	 * reader would otherwise attribute to the trust tier.
	 * @param carried the skills this definition offers
	 * @return the line
	 */
	private static String scriptState(Map<String, LlmReference> carried) {
		boolean scripts = carried.values()
			.stream()
			.filter(Skills.class::isInstance)
			.map(Skills.class::cast)
			.flatMap((skills) -> skills.getSkills().stream())
			.anyMatch(LoadedSkill::hasScripts);
		return "skill scripts: " + (scripts ? "this pack carries them" : "none carried") + ", not executed (switch "
				+ SCRIPT_ENGINE_PROPERTY + " selects an engine)";
	}

	/**
	 * One directory read as a container.
	 *
	 * @param reference the reference the container is offered under
	 * @param folders the skill names that loaded from it, each mapped to the folder that
	 * carried it — the path a report names when it says which folder answered
	 */
	private record Container(Skills reference, Map<String, Path> folders) {
	}

}
