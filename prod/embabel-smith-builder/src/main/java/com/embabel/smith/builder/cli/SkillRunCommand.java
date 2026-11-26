/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;
import com.embabel.smith.builder.core.AiSpecPromptCaller;
import com.embabel.smith.builder.core.ReferenceAwarePromptCaller;
import com.embabel.smith.builder.skill.PublishedSkillHost;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * The {@code smith skill-run} command: answers one request with a <em>published</em>
 * skill folder attached, so the published artifact has a route that loads it rather than
 * only a route that writes it. It reads the directory through {@link PublishedSkillHost},
 * prints which skills loaded and which folders the loader refused, and hands the
 * reference to the model call that answers the request.
 *
 * <p>
 * <b>What this command is not.</b> It is not a second build path — it publishes nothing,
 * evaluates nothing, and rewrites nothing; the folder it reads is an output of
 * {@code smith build --target skill}. Nor is it the eval arm: the with-skill arm of a
 * build stays what it was, because that arm's numbers are the scorer's ground and a
 * reader's host must not move it. The point of the route is that the bytes
 * {@code publish} left on disk are the bytes something can serve, which is a claim about
 * a call site and not about a prompt.
 *
 * <p>
 * <b>The engine is whatever the launcher was able to ask for.</b> It arrives as a
 * constructor argument from the bean provider, so an ordinary run passes null: the skill
 * activates, its body and resources read back, and its scripts are not callable. That is
 * Embabel's default, not a rule invented here — see
 * {@link PublishedSkillHost#PublishedSkillHost(Path, SkillScriptExecutionEngine)}.
 *
 * @author Ivan Rodriguez
 */
@Command(name = "skill-run", description = "Answer a request with a published skill folder's tools attached")
public class SkillRunCommand implements Callable<Integer> {

	@Option(names = "--skill-dir", required = true, description = "Published skill directory to load — the "
			+ "directory a build published into, holding one folder per skill (not a single skill's folder)")
	Path skillDir;

	@Parameters(index = "0", paramLabel = "REQUEST", arity = "1", description = "The request to answer")
	String request;

	private final Ai ai;

	/** Null on a run where nothing offered an engine; see the class javadoc. */
	private final SkillScriptExecutionEngine scriptEngine;

	public SkillRunCommand(Ai ai, SkillScriptExecutionEngine scriptEngine) {
		this.ai = ai;
		this.scriptEngine = scriptEngine;
	}

	@Override
	public Integer call() {
		if (!Files.isDirectory(this.skillDir)) {
			System.err.println("No published skill directory at " + this.skillDir
					+ " — a --target skill build writes one under its --out, and nothing here publishes it.");
			return 1;
		}
		PublishedSkillHost host = new PublishedSkillHost(this.skillDir, this.scriptEngine);
		PublishedSkillHost.Loading loading = host.loading();
		System.out
			.println("skills loaded: " + (loading.loaded().isEmpty() ? "(none)" : String.join(", ", loading.loaded())));
		for (PublishedSkillHost.Dropped dropped : loading.dropped()) {
			System.out.println("skipped: " + dropped.folder() + " — " + dropped.reason());
		}
		if (loading.isEmpty()) {
			System.err.println("Nothing loaded from " + this.skillDir
					+ ", so this run offers no skill. The folders above say why each was refused.");
			return 1;
		}
		try {
			System.out.println(caller().call("", this.request, host.skills()));
			return 0;
		}
		catch (Exception ex) {
			System.err.println((ex.getMessage() == null) ? ex.toString() : ex.getMessage());
			return 1;
		}
	}

	/**
	 * The model layer the request goes through. A seam rather than a call straight at
	 * {@link Ai}: the claim this command carries is that a published folder's tools reach
	 * a model call, and asserting that needs a caller that can say what the request
	 * arrived carrying. The host and the engine stay real behind it.
	 * @return the reference-carrying caller for this run.
	 */
	protected ReferenceAwarePromptCaller caller() {
		return new AiSpecPromptCaller(this.ai);
	}

}
