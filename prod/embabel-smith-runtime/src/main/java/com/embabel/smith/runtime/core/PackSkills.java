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
import com.embabel.agent.skills.script.NoOpExecutionEngine;
import com.embabel.agent.skills.script.ProcessSkillScriptExecutionEngine;
import com.embabel.agent.skills.script.ScriptExecutionResult;
import com.embabel.agent.skills.script.ScriptLanguage;
import com.embabel.agent.skills.script.SkillScript;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;
import com.embabel.agent.skills.support.LoadedSkill;
import com.embabel.agent.skills.support.ResourceType;

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

	private final Map<String, LlmReference> scriptFree;

	private final List<String> notes;

	private PackSkills(List<LlmReference> references, Map<String, LlmReference> carried,
			Map<String, LlmReference> scriptFree, List<String> notes) {
		this.references = List.copyOf(references);
		this.carried = Map.copyOf(carried);
		this.scriptFree = Map.copyOf(scriptFree);
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
		return at(packDir, agentName, null);
	}

	/**
	 * As {@link #at(Path, String)}, with the engine a carried script would run in.
	 *
	 * <p>
	 * The engine is the whole difference between a pack's script being inert text and
	 * being a callable tool, and it arrives here rather than being chosen here:
	 * {@link ScriptEngineConfig} reads {@code smith.tools.scripts.engine} once at
	 * start-up and hands that one engine to every definition in the run. {@code null} is
	 * the ordinary case — it is what the two-argument form above passes — and it leaves
	 * Embabel's default engine in place, which claims no language and so offers nothing.
	 * @param packDir the definition's directory
	 * @param agentName the definition's name, from its manifest
	 * @param scriptEngine the engine this run selected, or {@code null} for none
	 * @return the containers, the name-to-container map, and what the run must say about
	 * both
	 */
	public static PackSkills at(Path packDir, String agentName, SkillScriptExecutionEngine scriptEngine) {
		List<String> notes = new ArrayList<>();
		List<LlmReference> references = new ArrayList<>();
		Map<String, LlmReference> carried = new LinkedHashMap<>();
		Map<String, Path> servedBy = new LinkedHashMap<>();
		Container own = load(ownReferenceName(agentName), packDir.resolve("skills"), scriptEngine, notes);
		Container shared = load(SHARED_REFERENCE_NAME, packDir.toAbsolutePath().getParent().resolve("skills"),
				scriptEngine, notes);
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
		Map<String, LlmReference> scriptFree = new LinkedHashMap<>();
		for (Map.Entry<String, LlmReference> skill : carried.entrySet()) {
			scriptFree.put(skill.getKey(), readingOf(skill.getValue(), own, shared));
		}
		if (!carried.isEmpty()) {
			notes.add(scriptState(carried, scriptEngine));
		}
		return new PackSkills(references, carried, scriptFree, notes);
	}

	/**
	 * The script-free twin of the container a name resolves to.
	 * @param container the container the carried map hands out
	 * @param own this definition's own container
	 * @param shared the folder's shared container
	 * @return the same container offering no script tool
	 */
	private static LlmReference readingOf(LlmReference container, Container own, Container shared) {
		if (container == own.reference()) {
			return own.reading();
		}
		return (container == shared.reference()) ? shared.reading() : container;
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
	 * The same definitions' skills offered without a script tool, by the same names.
	 *
	 * <p>
	 * <b>Why a twin rather than a filter.</b> The platform's own {@code SkillFilter} is
	 * never consulted by {@code Skills.tools()} in the version this repo builds against —
	 * the field is read only by the data class's own {@code copy}, {@code equals},
	 * {@code hashCode} and {@code toString}, and no class in the jar calls {@code accept}
	 * — so it cannot take a script off a tool list. What does work is the engine: a
	 * {@code Skills} asks the engine it holds which languages it claims, so handing it
	 * the platform's no-op engine yields the same loaded skills, under the same reference
	 * name, offering only {@code activate}, {@code listResources} and
	 * {@code readResource}. The reference name surviving is what makes this usable — the
	 * naming strategy prefixes every callable name with it, so a twin does not invent a
	 * second wire name for the same skill.
	 * @return carried name to the container that holds it with scripts off the tool list
	 */
	public Map<String, LlmReference> scriptFree() {
		return this.scriptFree;
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
	 * @param scriptEngine the engine this run selected, or {@code null} to leave
	 * Embabel's default in place
	 * @param notes where to record what the reader could not do
	 * @return the container, with the names that loaded and the folder they came from
	 */
	private static Container load(String referenceName, Path folder, SkillScriptExecutionEngine scriptEngine,
			List<String> notes) {
		if (!Files.isDirectory(folder)) {
			Skills empty = new Skills(referenceName, "no skills at " + folder, List.of());
			return new Container(empty, empty, Map.of());
		}
		Skills skills = new Skills(referenceName, "Skills carried by " + folder, List.of())
			.withLocalSkills(folder.toString());
		if (scriptEngine != null) {
			skills = skills.withScriptExecutionEngine(scriptEngine);
		}
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
		// Only once an engine is claiming languages: with none selected the state line
		// already says scripts do not run, and naming every script in the pack would read
		// as a list of refusals for a run that was never offered any.
		if (scriptEngine != null && !scriptEngine.supportedLanguages().isEmpty()) {
			scriptsTheEngineWillNotRun(skills, scriptEngine, notes);
		}
		// The twin is the same container with the platform's no-op engine, which is the
		// only way this version of Skills offers a skill without offering its scripts:
		// the loaded list and the reference name both survive, so the wire prefix does
		// not
		// change. With no engine selected there is nothing to take off, and the twin is
		// the container itself.
		return new Container(skills,
				(scriptEngine != null) ? skills.withScriptExecutionEngine(NoOpExecutionEngine.INSTANCE) : skills,
				folders);
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
	 * Name the carried scripts this engine will not run, so their absence from the tool
	 * list is a sentence the run states rather than a capability the reader infers.
	 *
	 * <p>
	 * <b>Why the asking is ours to make.</b> The platform decides whether a script
	 * becomes a tool by testing one thing —
	 * {@code language in engine.supportedLanguages()} — and drops the script in silence
	 * when that fails; its {@code validate} hook, which is the one that explains itself,
	 * runs only when a script is actually executed, by which point no tool existed to
	 * execute. So this asks the engine the same question the tool-list build asks,
	 * through {@code validate} so the reason comes from the engine rather than from a
	 * guess here, and says the answer out loud. A script whose extension is not a
	 * language at all is named too: the platform has no hook that ever learns of it, so
	 * without this it is a file in a folder that simply never appears anywhere.
	 * <p>
	 * design.md D4 settles the placement: the loader declares no script metadata, the
	 * input schema is fixed and language comes from the file extension, "so any
	 * per-script policy is authored here, on top of the engine's {@code validate()}
	 * hook".
	 * @param skills the container as loaded, before any tool list was taken from it
	 * @param scriptEngine the engine this run selected, which is the one being asked
	 * @param notes where to record what it refused
	 */
	private static void scriptsTheEngineWillNotRun(Skills skills, SkillScriptExecutionEngine scriptEngine,
			List<String> notes) {
		for (LoadedSkill skill : skills.getSkills()) {
			for (String fileName : skill.listResources(ResourceType.SCRIPTS)) {
				ScriptLanguage language = ScriptLanguage.Companion.fromFileName(fileName);
				if (language == null) {
					notes.add("script '" + fileName + "' of skill '" + skill.getName() + "' (extension '"
							+ extension(fileName)
							+ "') is no language the platform knows, so it is offered as no tool");
					continue;
				}
				ScriptExecutionResult.Denied refusal = scriptEngine
					.validate(new SkillScript(skill.getName(), fileName, language, skill.getBasePath()));
				if (refusal != null) {
					notes.add("script '" + fileName + "' of skill '" + skill.getName() + "' (" + language.name()
							+ ") is refused by the engine, so it is offered as no tool: " + refusal.getReason());
				}
			}
		}
	}

	/**
	 * The extension of a script file name, as the way to name a language the platform's
	 * own table does not contain.
	 * @param fileName the file as the loader listed it
	 * @return the part after the last dot, or the empty string when there is none
	 */
	private static String extension(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return (dot < 0) ? "" : fileName.substring(dot + 1);
	}

	/**
	 * The script state in force, named as a switch rather than left as an absence a
	 * reader would otherwise attribute to the trust tier.
	 *
	 * <p>
	 * <b>Why the engine's own claim decides the wording.</b> The platform decides whether
	 * a carried script becomes a tool on one test — an engine that claims the script's
	 * language — so this asks the engine the same question rather than assuming that an
	 * engine object being present means scripts run. The {@code none} selection installs
	 * the platform's no-op engine, which claims no language, and is reported as not
	 * executed for the same reason a missing engine is.
	 * @param carried the skills this definition offers
	 * @param scriptEngine the engine this run selected, or {@code null} for none
	 * @return the line
	 */
	private static String scriptState(Map<String, LlmReference> carried, SkillScriptExecutionEngine scriptEngine) {
		boolean scripts = carried.values()
			.stream()
			.filter(Skills.class::isInstance)
			.map(Skills.class::cast)
			.flatMap((skills) -> skills.getSkills().stream())
			.anyMatch(LoadedSkill::hasScripts);
		boolean offered = (scriptEngine != null) && !scriptEngine.supportedLanguages().isEmpty();
		String state = (offered) ? "offered as tools by the engine " + SCRIPT_ENGINE_PROPERTY + " selects"
				: "not executed (switch " + SCRIPT_ENGINE_PROPERTY + " selects an engine)";
		return "skill scripts: " + (scripts ? "this pack carries them" : "none carried") + ", " + state
				+ environment(scriptEngine, offered);
	}

	/**
	 * The environment a script will actually see, said on the same line as the script
	 * state, because a reader told that scripts are offered is owed what the script can
	 * read.
	 *
	 * <p>
	 * <b>Why the leak is stated rather than the boundary promised.</b> The platform's
	 * process engine ships an allowlist and applies it only to an engine constructed with
	 * host inheritance switched off; the forms of that constructor that carry the switch
	 * are marked synthetic, so a Java caller reaches the no-argument engine and
	 * {@code confinedTo(root)}, both of which leave inheritance on. Measured, a script
	 * run through the engine this switch installs prints every variable the JVM was
	 * started with, API keys among them. So the line names the allowlist as what the
	 * engine <em>would</em> have applied rather than saying <em>confined</em>, which here
	 * would be false, and it says so on the run's own line rather than in a document an
	 * operator need not have read. The container selections are the ones that do isolate;
	 * see {@link ScriptEngineConfig}.
	 * @param scriptEngine the engine this run selected, since the claim is the process
	 * engine's and no other engine's
	 * @param offered whether scripts execute at all, as the environment of a script that
	 * never runs is not a fact about this run
	 * @return the clause, empty when nothing runs or when it is not the process engine
	 */
	private static String environment(SkillScriptExecutionEngine scriptEngine, boolean offered) {
		if (!offered || !(scriptEngine instanceof ProcessSkillScriptExecutionEngine)) {
			return "";
		}
		return "; script environment: the host's in full — the engine's own allowlist ["
				+ String.join(", ", ProcessSkillScriptExecutionEngine.Companion.getSAFE_ENV_ALLOWLIST())
				+ "] is applied only to an engine constructed with inheritance off, which Java cannot ask for";
	}

	/**
	 * One directory read as a container.
	 *
	 * @param reference the reference the container is offered under
	 * @param reading the same container with its scripts off the tool list, the same
	 * object when there was no engine to take off
	 * @param folders the skill names that loaded from it, each mapped to the folder that
	 * carried it — the path a report names when it says which folder answered
	 */
	private record Container(Skills reference, Skills reading, Map<String, Path> folders) {
	}

}
