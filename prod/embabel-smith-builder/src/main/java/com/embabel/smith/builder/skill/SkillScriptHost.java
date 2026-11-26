/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.embabel.agent.skills.Skills;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;

/**
 * The builder's own skill host: it writes a {@link Skill} out as an Agent Skill folder
 * and hands back Embabel's {@link Skills} loaded from it, with a
 * {@link SkillScriptExecutionEngine} installed — so the skill's scripts are callable
 * tools for whoever the reference is handed to, not just text in a prompt.
 *
 * <p>
 * <b>Why a folder rather than an in-memory {@code LoadedSkill}.</b> Embabel discovers
 * scripts by reading {@code scripts/} from disk
 * ({@code DefaultDirectorySkillDefinitionLoader}), and the loader that validates file
 * references is the one that rejects a skill whose body names a file the folder lacks.
 * Writing the folder is therefore the only way to ask the framework's own question — what
 * tools does <em>this</em> skill offer — and it has the side benefit of being the same
 * two writes {@code SkillPublishable} performs, so the host and a reader of the published
 * artifact are asking about the same bytes.
 *
 * <p>
 * <b>What decides whether anything is callable.</b> Only the engine. Embabel lists a
 * script as a tool when an engine that claims its language is installed
 * ({@code LoadedSkill.getScriptTools} filters on {@code supportedLanguages()}), and the
 * engine exists as a bean only behind {@code smith.builder.skill-scripts.enabled}. A host
 * built without one — which is what an ordinary build gets, because
 * {@code SkillBuilderService} takes a null host unless the bean was present — is not a
 * quieter version of this class, it is absent: nothing here runs, and nothing here is
 * written.
 *
 * <p>
 * <b>The folder outlives the call, on purpose.</b> A {@code Skills} pack lists its
 * scripts by asking the folder: {@code LoadedSkill.listResources} reads {@code scripts/}
 * from disk rather than a list captured at load time, and what the tool list is built
 * from turned out to be the folder's live contents at the moment of the call — an
 * inference from behaviour, not a line read from source: an earlier cut deleted this
 * folder when the run ended, and the arm then offered no script tool while the same skill
 * still activated and its body still read back. That is the least visible way to lose
 * this feature, so the folder is registered for removal when the JVM exits rather than
 * deleted at the end of a call, and the one thing that decides whether a tool appears at
 * all stays where it should be: the engine's {@code supportedLanguages()}.
 * <p>
 * The folder is scratch space: one subfolder per skill name under a temporary directory,
 * rewritten on each request. The name is the one the frontmatter gate already accepted
 * before the eval arms were reached.
 *
 * @author Ivan Rodriguez
 */
public final class SkillScriptHost {

	private final SkillScriptExecutionEngine engine;

	private Path root;

	public SkillScriptHost(SkillScriptExecutionEngine engine) {
		this.engine = engine;
	}

	/**
	 * The skill as a callable capability.
	 * @param skill the skill under evaluation
	 * @return the pack to attach to a model call, offering this skill's scripts as tools
	 * @throws IOException if the scratch folder could not be written
	 */
	public Skills skillsFor(Skill skill) throws IOException {
		Path folder = root().resolve(skill.name());
		Files.createDirectories(folder);
		Files.writeString(folder.resolve("SKILL.md"), skill.toSkillMd(), StandardCharsets.UTF_8);
		for (SkillScript script : skill.scripts()) {
			// Confined to scripts/<name>.js by SkillScript, so it lands inside the folder
			// above whatever the model declared.
			Path target = folder.resolve(script.path());
			Files.createDirectories(target.getParent());
			Files.writeString(target, script.content(), StandardCharsets.UTF_8);
		}
		disposeOnExit(folder);
		return new Skills(skill.name(), skill.description(), List.of()).withLocalSkills(root().toString())
			.withScriptExecutionEngine(this.engine);
	}

	/**
	 * Queues the scratch folder for removal at exit: it must stay readable for as long as
	 * a reference made from it can still be asked for its tools.
	 * @param folder the folder just written.
	 * @throws IOException if the folder could not be listed.
	 */
	private static void disposeOnExit(Path folder) throws IOException {
		try (Stream<Path> paths = Files.walk(folder)) {
			paths.forEach((path) -> path.toFile().deleteOnExit());
		}
	}

	private Path root() throws IOException {
		if (this.root == null) {
			this.root = Files.createTempDirectory("smith-skill-host");
			this.root.toFile().deleteOnExit();
		}
		return this.root;
	}

}
