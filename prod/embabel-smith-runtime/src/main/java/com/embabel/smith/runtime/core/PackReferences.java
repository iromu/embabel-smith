/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.embabel.agent.api.reference.LiteralText;
import com.embabel.agent.api.reference.LlmReference;
import com.embabel.agent.api.reference.SpringResource;
import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.skills.Skills;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;
import com.embabel.agent.tools.file.LocalDirectory;
import com.embabel.smith.common.io.Yaml;

/**
 * The references one agent definition can answer a {@code references} declaration with:
 * the skill folders {@link PackSkills} reads, plus what the pack's optional
 * {@code references.yml} declares.
 *
 * <p>
 * <b>The file is three fields per entry.</b> {@code references.yml} holds a
 * {@code references:} list of {@code name}, {@code kind} and {@code path}. There is no
 * fourth field, on purpose: a format that took a class name, a factory name or a
 * property-driven type would let the pack or the deployment nominate a class for the
 * runtime to instantiate. What happens instead is a {@code switch} over four literals,
 * each arm calling a platform constructor, so the set of things a pack can make the
 * runtime build is closed at compile time. A kind outside those four is refused by name
 * rather than passed to something that might exist.
 *
 * <p>
 * <b>What each kind becomes.</b> A {@code skills} folder becomes a {@link Skills}
 * container through the same {@code withLocalSkills} route {@link PackSkills} uses. A
 * {@code directory} becomes a {@link LocalDirectory} whose tools are offered under the
 * declared name — see {@link #directory(String, Path)} for why the platform's
 * {@code LocalDirectoryReference} is not the value attached. A {@code document} becomes a
 * {@link SpringResource} over the file, which puts the file's content into the notes. A
 * {@code brief} becomes {@link LiteralText}, which is the same thing with the text
 * already in memory rather than read at call time.
 *
 * <p>
 * <b>Refused, not dropped.</b> An entry that cannot be constructed — an unknown kind, a
 * path-backed kind whose path is not there in the shape that kind needs, a name already
 * taken by another name that sanitizes to the same tool prefix — is left out of
 * {@link #carried()} and stated in {@link #notes()}. That matters because a step naming a
 * reference nothing carries is reported by {@link ReferenceAwareStepSpecRepository} as
 * unresolved, and an entry silently omitted here would make that report read as a typo in
 * the step.
 *
 * <p>
 * <b>Unfolding is an option, and the reason is the clock.</b> An Embabel interaction tops
 * out around sixty seconds and that budget includes the tool round-trips, so a reference
 * that puts a wide tool surface in front of a step which also has other work to do spends
 * that step's time on the reference. {@link #at(Path, String, Set)} therefore takes the
 * names to offer behind the platform's unfolding facade, which lists one tool instead of
 * the reference's whole surface and reveals the inner tools when it is called. It is off
 * by default — a direct listing is what a step normally wants, and the facade costs a
 * round-trip — and the switch exists for that timeout, not as a general preference about
 * how many tools a prompt should show.
 *
 * @author Ivan Rodriguez
 */
public final class PackReferences {

	/** The file a pack declares its non-skill references in. */
	public static final String REFERENCES_FILE = "references.yml";

	/** The form in which a reference's tools are listed under their prefixed names. */
	public static final String DIRECT = "direct";

	/** The form in which a reference is listed as one facade tool. */
	public static final String FACADE = "unfolding";

	/** The kinds this runtime can construct, in the order a refusal lists them. */
	private static final Set<String> KINDS = new TreeSet<>(Set.of("skills", "directory", "document", "brief"));

	/**
	 * The kinds that need a path to construct anything from; a brief carries its own
	 * text.
	 */
	private static final Set<String> PATH_BACKED_KINDS = Set.of("skills", "directory", "document");

	private final List<LlmReference> references;

	private final Map<String, LlmReference> carried;

	private final Map<String, String> forms;

	private final List<String> notes;

	private PackReferences(Map<String, LlmReference> carried, Map<String, String> forms, List<String> notes) {
		this.carried = Map.copyOf(carried);
		this.forms = Map.copyOf(forms);
		this.notes = List.copyOf(notes);
		List<LlmReference> distinct = new ArrayList<>();
		for (LlmReference reference : carried.values()) {
			if (distinct.stream().noneMatch((held) -> held == reference)) {
				distinct.add(reference);
			}
		}
		this.references = List.copyOf(distinct);
	}

	/**
	 * Reads the references one definition can answer with, every one of them offered
	 * directly.
	 * @param packDir the definition's directory
	 * @param agentName the definition's name, from its manifest
	 * @return the carried set
	 */
	public static PackReferences at(Path packDir, String agentName) {
		return at(packDir, agentName, Set.of());
	}

	/**
	 * Reads the references one definition can answer with, offering named references
	 * behind the unfolding facade.
	 *
	 * <p>
	 * The default is direct, and naming a reference here is the only way to change it. A
	 * name that matches a skills container changes the container, which is what the tools
	 * are attached to, so every skill in it is offered behind the same facade.
	 * @param packDir the definition's directory
	 * @param agentName the definition's name, from its manifest
	 * @param unfolded reference names to offer behind a facade, as the pack writes them;
	 * an empty set is {@link #at(Path, String)}
	 * @return the carried set, with {@link #forms()} saying which shape each name ended
	 * up in
	 */
	public static PackReferences at(Path packDir, String agentName, Set<String> unfolded) {
		return at(packDir, agentName, unfolded, null);
	}

	/**
	 * As {@link #at(Path, String, Set)}, with the engine a carried skill script would run
	 * in. The engine arrives from the caller rather than being chosen here, exactly as it
	 * does for {@link PackSkills#at(Path, String, SkillScriptExecutionEngine)}: an ingest
	 * runs wherever it was built, so the host that wants scripts running says so by
	 * handing one over. {@code null} — which is what the shorter forms above pass —
	 * leaves Embabel's default engine in place, which claims no language and so offers
	 * nothing.
	 * @param packDir the definition's directory
	 * @param agentName the definition's name, from its manifest
	 * @param unfolded reference names to offer behind a facade
	 * @param scriptEngine the engine this run selected, or {@code null} for none
	 * @return the carried set, with {@link #forms()} saying which shape each name ended
	 * up in
	 */
	public static PackReferences at(Path packDir, String agentName, Set<String> unfolded,
			SkillScriptExecutionEngine scriptEngine) {
		List<String> notes = new ArrayList<>();
		PackSkills skills = PackSkills.at(packDir, agentName, scriptEngine);
		// The skill folders' own disclosures come first: a refused folder and the script
		// state are what a reader needs to know whatever the references file says.
		notes.addAll(skills.notes());
		Map<String, LlmReference> carried = new LinkedHashMap<>(skills.carried());
		Map<String, String> owners = new LinkedHashMap<>();
		carried.keySet().forEach((name) -> owners.put(prefixOf(name), "skill '" + name + "'"));
		for (Declared declared : read(packDir, notes)) {
			LlmReference value = construct(declared, packDir, notes);
			if (value == null) {
				continue;
			}
			String owner = owners.get(prefixOf(declared.name()));
			if (owner != null) {
				notes.add("reference '" + declared.name() + "' and " + owner + " both sanitize to the tool prefix '"
						+ prefixOf(declared.name()) + "', so only the first is carried");
				continue;
			}
			owners.put(prefixOf(declared.name()), "reference '" + declared.name() + "'");
			carried.put(declared.name(), value);
		}
		Map<LlmReference, LlmReference> facaded = new IdentityHashMap<>();
		Set<String> chosen = new LinkedHashSet<>();
		for (Map.Entry<String, LlmReference> held : carried.entrySet()) {
			if (!unfolded.contains(held.getKey()) || facaded.containsKey(held.getValue())) {
				continue;
			}
			chosen.add(held.getKey());
			int listed = LlmReference.prefixedTools(held.getValue()).size();
			if (listed == 0) {
				notes.add("unfolding asked for reference '" + held.getKey()
						+ "', which offers no tools to hide, so it stays listed directly");
				continue;
			}
			facaded.put(held.getValue(), held.getValue().withUnfolding());
			notes.add("reference '" + held.getValue().getName() + "' offered behind its unfolding facade: one tool "
					+ "listed where " + listed + " would be listed directly");
		}
		for (String asked : new TreeSet<>(unfolded)) {
			if (!chosen.contains(asked) && !carried.containsKey(asked)) {
				notes.add("unfolding asked for '" + asked + "', which this definition carries nothing under");
			}
		}
		Map<String, LlmReference> shaped = new LinkedHashMap<>();
		Map<String, String> forms = new LinkedHashMap<>();
		carried.forEach((name, reference) -> {
			LlmReference value = facaded.getOrDefault(reference, reference);
			shaped.put(name, value);
			forms.put(name, (facaded.containsKey(reference)) ? FACADE : DIRECT);
		});
		return new PackReferences(shaped, forms, notes);
	}

	/**
	 * The references to put in front of this definition's steps, each already in the form
	 * it is offered in.
	 * @return the references, in the order they were read
	 */
	public List<LlmReference> references() {
		return this.references;
	}

	/**
	 * The names this definition can answer a declaration with, as {@link PackSkills}
	 * spells it, so a step's {@code references} entry resolves against the definition
	 * that wrote it.
	 * @return carried name to the reference that holds it
	 */
	public Map<String, LlmReference> carried() {
		return this.carried;
	}

	/**
	 * Which shape each carried name was offered in. A run report needs this rather than
	 * the attached names alone: a facade and a direct listing are indistinguishable in
	 * the latter, and only one of them spends a round-trip on the reference.
	 * @return carried name to {@link #DIRECT} or {@link #FACADE}
	 */
	public Map<String, String> forms() {
		return this.forms;
	}

	/**
	 * What the run has to say about this definition's references: entries it refused to
	 * construct, and the references it offered behind a facade.
	 * @return the report lines
	 */
	public List<String> notes() {
		return this.notes;
	}

	/**
	 * Read the pack's references file, refusing what cannot be read rather than reading
	 * it as nothing.
	 * @param packDir the definition's directory
	 * @param notes where to record what the file could not be made to say
	 * @return the entries, in the order the file lists them
	 */
	private static List<Declared> read(Path packDir, List<String> notes) {
		Path file = packDir.resolve(REFERENCES_FILE);
		if (!Files.isRegularFile(file)) {
			return List.of();
		}
		Map<?, ?> root;
		try {
			root = Yaml.mapper().readValue(Files.readString(file), Map.class);
		}
		catch (IOException | RuntimeException ex) {
			notes.add(REFERENCES_FILE + " could not be read as a list of references: " + ex.getMessage());
			return List.of();
		}
		Object listed = (root != null) ? root.get("references") : null;
		if (!(listed instanceof List<?> entries)) {
			notes.add(REFERENCES_FILE + " carries no references list, so the pack declares nothing to construct");
			return List.of();
		}
		List<Declared> declared = new ArrayList<>();
		for (Object entry : entries) {
			if (!(entry instanceof Map<?, ?> fields)) {
				notes.add(REFERENCES_FILE + " lists a reference that is not a name, kind and path");
				continue;
			}
			declared.add(new Declared(text(fields.get("name")), text(fields.get("kind")), text(fields.get("path"))));
		}
		return declared;
	}

	/**
	 * Build the one platform value a declared entry can mean. This is the whole of the
	 * construction question: four literals, four platform constructors, and a refusal
	 * that names the kind for anything else. Nothing here reads a class name, a bean name
	 * or a property to decide what to instantiate.
	 * @param declared the entry as the file writes it
	 * @param packDir the definition's directory, which a declared path resolves against
	 * @param notes where to record what was refused
	 * @return the reference to carry, or {@code null} when the entry was refused
	 */
	private static LlmReference construct(Declared declared, Path packDir, List<String> notes) {
		if (declared.name() == null || declared.name().isBlank()) {
			notes.add("a reference in " + REFERENCES_FILE + " carries no name, so no step can ask for it");
			return null;
		}
		if (declared.kind() == null || !KINDS.contains(declared.kind())) {
			notes.add("reference '" + declared.name() + "' declares kind '" + declared.kind() + "', which this runtime "
					+ "cannot construct. The kinds it can are " + KINDS);
			return null;
		}
		Path target = (declared.path() != null && !declared.path().isBlank())
				? packDir.resolve(declared.path()).toAbsolutePath().normalize() : null;
		if (PATH_BACKED_KINDS.contains(declared.kind())) {
			if (target == null) {
				notes.add("reference '" + declared.name() + "' of kind '" + declared.kind()
						+ "' declares no path to construct from");
				return null;
			}
			boolean carried = "document".equals(declared.kind()) ? Files.isRegularFile(target)
					: Files.isDirectory(target);
			if (!carried) {
				notes.add("reference '" + declared.name() + "' of kind '" + declared.kind() + "' points at '"
						+ declared.path() + "', which the pack does not carry");
				return null;
			}
		}
		return switch (declared.kind()) {
			case "skills" -> skills(declared.name(), target, notes);
			case "directory" -> directory(declared.name(), target);
			case "document" -> document(declared.name(), target);
			default -> brief(declared.name(), target, notes);
		};
	}

	/**
	 * A folder of skills declared outside the two folders {@link PackSkills} reads.
	 * @param name the name a step declares
	 * @param folder the folder, already checked to exist
	 * @param notes where to record a folder the loader refused
	 * @return the container
	 */
	private static LlmReference skills(String name, Path folder, List<String> notes) {
		Skills skills = new Skills(name, "Skills carried by " + folder, List.of());
		try {
			return skills.withLocalSkills(folder.toString());
		}
		catch (RuntimeException ex) {
			notes.add("skills folder " + folder + " was refused: " + ex.getMessage());
			return skills;
		}
	}

	/**
	 * A folder of files a step can read.
	 *
	 * <p>
	 * The platform's own {@code LocalDirectoryReference} is not the value attached, and
	 * the reason is measurable rather than stylistic: its {@code tools()} is empty, so a
	 * step given it would be told about the folder and handed no way to read it. Here the
	 * folder is a {@link LocalDirectory} — the platform's read-only view of a directory,
	 * confined to it — and its tools are offered under the <em>declared</em> name, which
	 * is also what the validator's prefix arithmetic assumes. A {@code LocalDirectory}
	 * named from its own path would prefix its tools with the folder's basename instead,
	 * so a pack renaming the reference in the file would move the tools without saying
	 * so.
	 * @param name the name a step declares
	 * @param folder the folder, already checked to exist
	 * @return the reference
	 */
	private static LlmReference directory(String name, Path folder) {
		String description = "Files under " + folder.getFileName() + " carried by the pack";
		String usage = "The folder travels with the pack and is read-only. List and read it with the tools prefixed "
				+ "with this reference's name; paths outside it are refused.";
		LocalDirectory view = new LocalDirectory(folder.toString(), description, usage);
		return LlmReference.of(name, description, Tool.fromInstance(view), usage);
	}

	/**
	 * A document whose content the model is given.
	 * @param name the name a step declares
	 * @param file the file, already checked to exist
	 * @return the reference, whose notes are the file's content read at call time
	 */
	private static LlmReference document(String name, Path file) {
		return new SpringResource("file:" + file, name, "Document " + file.getFileName() + " carried by the pack");
	}

	/**
	 * A brief: the same content as a document, held in memory as the platform's literal
	 * text rather than read from disk each call.
	 * @param name the name a step declares
	 * @param file the file holding the text, which a brief need not have
	 * @param notes where to record a brief that says nothing
	 * @return the reference
	 */
	private static LlmReference brief(String name, Path file, List<String> notes) {
		String text = "";
		if (file == null) {
			notes.add("brief '" + name + "' declares no path, so it contributes no text");
		}
		else if (Files.isRegularFile(file)) {
			try {
				text = Files.readString(file);
			}
			catch (IOException ex) {
				notes.add("brief '" + name + "' could not be read from " + file);
			}
		}
		else {
			notes.add("brief '" + name + "' points at " + file
					+ ", which the pack does not carry, so it contributes no " + "text");
		}
		return new LiteralText(name, "Brief carried by the pack", text);
	}

	/**
	 * The tool prefix the platform derives from a name, which is why two names that
	 * sanitize alike cannot both be carried.
	 * @param name the name as the pack writes it
	 * @return the prefix
	 */
	private static String prefixOf(String name) {
		return name.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase(Locale.ROOT);
	}

	private static String text(Object value) {
		return (value instanceof String string) ? string : null;
	}

	/**
	 * One entry of {@code references.yml}, as written.
	 *
	 * @param name what a step declares
	 * @param kind which of the four kinds it is, or something the runtime cannot build
	 * @param path what to build it from, relative to the pack
	 */
	private record Declared(String name, String kind, String path) {
	}

}
