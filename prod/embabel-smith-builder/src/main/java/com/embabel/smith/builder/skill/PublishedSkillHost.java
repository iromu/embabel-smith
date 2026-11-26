/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.embabel.agent.skills.Skills;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;
import com.embabel.agent.skills.support.DefaultDirectorySkillDefinitionLoader;
import com.embabel.agent.skills.support.LoadedSkill;

/**
 * The reader's host: a skill folder that <em>already exists on disk</em> — the one
 * {@code publish} wrote — loaded through Embabel's own directory loader and handed over
 * as a reference for a call that is answering somebody.
 *
 * <p>
 * <b>What makes this a host and not a description.</b> It points
 * {@link Skills#withLocalSkills(String)} at the published directory and returns what the
 * framework's loader read from it. Nothing here parses {@code SKILL.md}, re-derives a
 * skill from a spec, or lists a script's tools by hand:
 * {@code LoadedSkill.getScriptTools} already filters {@code scripts/} by the installed
 * engine's {@code supportedLanguages()}, and re-deriving that join is how a reader ends
 * up with a tool list its own loader disagrees with. A surface that only names a skill —
 * an agent card built from a spec source, say — offers no script tool and is not a host.
 *
 * <p>
 * <b>The engine is asked for, not required.</b> It arrives through the same bean provider
 * the CLI already uses, so an ordinary run passes {@code null} and gets a working host:
 * the skill activates, its body reads back, its resources list, and only the script tool
 * is missing. That is Embabel's own default ({@code NoOpExecutionEngine} claims no
 * language), which means the off state here is the framework's off state rather than a
 * second switch this class invented.
 *
 * <p>
 * <b>It adds no rule about what a script may do.</b> Two components decide a published
 * script's reach and this is not a third: {@code AgentSpecSecurityScanner.scanSkill}
 * refused to publish the dangerous ones, and the engine denies host access while running
 * the rest. Nor does it consult a trust tier — a tier admits a source by the tool-group
 * roles its packs declare, and a script declares none, so a tier check here would be
 * reading a list that never contains its subject.
 *
 * <p>
 * <b>A folder the loader refuses is reported, not swallowed.</b>
 * {@code DefaultDirectorySkillDefinitionLoader.loadAll} skips a folder it cannot load and
 * logs the reason where nobody downstream can see it, which is the one failure shape that
 * makes a missing skill look like an absent one. {@link #loading()} names the folders
 * that loaded and the ones that did not, with the loader's own reason for each.
 *
 * @author Ivan Rodriguez
 */
public final class PublishedSkillHost {

	/**
	 * The name of the reference rather than of any skill inside it: a published directory
	 * can carry several, and the skill names that matter are the ones the loader read
	 * from the folders.
	 */
	private static final String REFERENCE_NAME = "smith-published-skills";

	private final Path root;

	/**
	 * Null on an ordinary run — see
	 * {@link #PublishedSkillHost(Path, SkillScriptExecutionEngine)}.
	 */
	private final SkillScriptExecutionEngine engine;

	/**
	 * A host with no execution engine: the framework's own default, so the published
	 * scripts are text the model can read and not tools it can call.
	 * @param publishedRoot the directory {@code publish} wrote into
	 */
	public PublishedSkillHost(Path publishedRoot) {
		this(publishedRoot, null);
	}

	/**
	 * A host over a published directory.
	 * @param publishedRoot the directory {@code publish} wrote into — a library root
	 * holding one folder per skill, not a single skill's folder
	 * @param engine the engine the caller was able to obtain, or {@code null} when
	 * nothing offered one. Never constructed here: whether scripts are callable is the
	 * switch's decision, made where the bean is asked for.
	 */
	public PublishedSkillHost(Path publishedRoot, SkillScriptExecutionEngine engine) {
		if (publishedRoot == null) {
			throw new IllegalArgumentException("no published skill directory to host");
		}
		this.root = publishedRoot.toAbsolutePath();
		this.engine = engine;
	}

	/**
	 * The reference to attach to a call.
	 * @return the pack the loader read from disk, with the engine installed only when one
	 * was handed in
	 */
	public Skills skills() {
		Skills skills = new Skills(REFERENCE_NAME, "Skills published under " + this.root, List.of())
			.withLocalSkills(this.root.toString());
		return (this.engine == null) ? skills : skills.withScriptExecutionEngine(this.engine);
	}

	/**
	 * What the loader made of this directory, read at the same moment {@link #skills()}
	 * would read it.
	 * @return the skills that loaded and the folders that were refused, each with the
	 * loader's reason
	 */
	public Loading loading() {
		Skills skills = skills();
		Set<Path> loadedFolders = skills.getSkills()
			.stream()
			.map(PublishedSkillHost::folderOf)
			.collect(Collectors.toSet());
		List<String> loaded = skills.getSkills().stream().map(LoadedSkill::getName).toList();
		List<Dropped> dropped = new ArrayList<>();
		for (Path folder : skillFolders()) {
			if (!loadedFolders.contains(folder)) {
				dropped.add(new Dropped(folder, reason(folder)));
			}
		}
		return new Loading(loaded, List.copyOf(dropped));
	}

	/**
	 * The folders a reader would consider: the immediate children of the root that carry
	 * the file Embabel's loader looks for. Not a recursive walk — that would report
	 * directories the loader never looks at as though it had declined them.
	 * @return the candidate skill folders, sorted by path so two runs report the same
	 * order
	 */
	private List<Path> skillFolders() {
		try (Stream<Path> children = Files.list(this.root)) {
			return children.filter(Files::isDirectory)
				.filter((child) -> Files
					.isRegularFile(child.resolve(DefaultDirectorySkillDefinitionLoader.SKILL_FILE_NAME)))
				.map(Path::toAbsolutePath)
				.sorted()
				.toList();
		}
		catch (IOException unreadable) {
			throw new IllegalStateException("Cannot list the published skill directory " + this.root, unreadable);
		}
	}

	/**
	 * The loader's own verdict on a folder it refused, obtained by asking it to load that
	 * one folder: it throws where {@code loadAll} skips, so the message is the
	 * framework's and not a guess rebuilt here.
	 * @param folder the folder that did not make it into the pack
	 * @return why it did not, or a sentence saying the loader gave no reason
	 */
	private String reason(Path folder) {
		try {
			new DefaultDirectorySkillDefinitionLoader().load(folder);
			return "loaded on its own but absent from the pack";
		}
		catch (RuntimeException refused) {
			String message = refused.getMessage();
			return (message == null || message.isBlank()) ? refused.getClass().getSimpleName() : message;
		}
	}

	private static Path folderOf(LoadedSkill skill) {
		return skill.getBasePath().toAbsolutePath();
	}

	/**
	 * What a load of a published directory produced, for whoever asked for it to say out
	 * loud which skills it is standing on.
	 *
	 * @param loaded the names of the skills that loaded
	 * @param dropped the folders in that directory that did not load, each with the
	 * loader's reason
	 */
	public record Loading(List<String> loaded, List<Dropped> dropped) {

		/**
		 * Whether anything at all loaded — the check a caller makes before it offers a
		 * published skill as a capability.
		 * @return true when the loader loaded at least one skill from the directory
		 */
		public boolean isEmpty() {
			return this.loaded.isEmpty();
		}

	}

	/**
	 * A folder the loader refused.
	 *
	 * @param folder the directory that was skipped
	 * @param reason the loader's own explanation for refusing it
	 */
	public record Dropped(Path folder, String reason) {

	}

}
