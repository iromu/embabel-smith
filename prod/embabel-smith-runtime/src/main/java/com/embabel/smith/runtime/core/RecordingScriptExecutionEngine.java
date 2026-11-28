/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import com.embabel.agent.skills.script.ScriptExecutionResult;
import com.embabel.agent.skills.script.ScriptLanguage;
import com.embabel.agent.skills.script.SkillScript;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;

/**
 * The engine the switch selected, with each call written down.
 *
 * <p>
 * <b>Why the recording sits here and not in the platform.</b> The platform's own
 * observability hook is {@code LlmInteraction.toolCallInspectors}, a constructor argument
 * of the object the platform builds when a step calls the model — a Java host embedding
 * the platform never constructs it, so nothing of ours can be added to that list.
 * Wrapping the engine is the one place a script call is unavoidably visible: every script
 * tool ends up in {@code execute}, and the {@link SkillScript} handed over already
 * carries the skill it came from and the callable name it was offered under.
 *
 * <p>
 * <b>It adds nothing to what runs.</b> Languages, refusals and results are the
 * delegate's, passed straight through, so the tool list a step is offered and the answer
 * a script gives are exactly what the selected engine alone would produce. The only
 * addition is a line in {@link ScriptCallLog}. Where a caller needs to know which engine
 * is really underneath — the environment disclosure in {@link PackSkills} is the one
 * place that does — it asks
 * {@link ScriptEngineConfig#underlying(SkillScriptExecutionEngine)} rather than guessing.
 *
 * @author Ivan Rodriguez
 */
public final class RecordingScriptExecutionEngine implements SkillScriptExecutionEngine {

	private final SkillScriptExecutionEngine delegate;

	private final ScriptCallLog calls;

	/**
	 * Wraps the engine a selection installed.
	 * @param delegate the engine that actually runs scripts
	 * @param calls where each call is written
	 */
	public RecordingScriptExecutionEngine(SkillScriptExecutionEngine delegate, ScriptCallLog calls) {
		this.delegate = delegate;
		this.calls = calls;
	}

	/**
	 * The engine underneath, which is the one the switch named.
	 * @return the wrapped engine
	 */
	public SkillScriptExecutionEngine delegate() {
		return this.delegate;
	}

	@Override
	public Set<ScriptLanguage> supportedLanguages() {
		return this.delegate.supportedLanguages();
	}

	@Override
	public ScriptExecutionResult.Denied validate(SkillScript script) {
		return this.delegate.validate(script);
	}

	@Override
	public ScriptExecutionResult execute(SkillScript script, List<String> args, String stdin,
			List<? extends Path> inputFiles) {
		long started = System.nanoTime();
		ScriptExecutionResult result = this.delegate.execute(script, args, stdin, inputFiles);
		this.calls.record(script.getSkillName(), script.getToolName(), this.delegate.getClass().getSimpleName(),
				status(result), (System.nanoTime() - started) / 1_000_000);
		return result;
	}

	/**
	 * The outcome as the engine put it, with nothing read into it that the engine did not
	 * say.
	 * @param result what the delegate returned
	 * @return the status fragment of the recorded line
	 */
	private static String status(ScriptExecutionResult result) {
		if (result instanceof ScriptExecutionResult.Success succeeded) {
			return "exit " + succeeded.getExitCode();
		}
		if (result instanceof ScriptExecutionResult.Failure failed) {
			StringBuilder text = new StringBuilder("failed");
			if (failed.getTimedOut()) {
				text.append(" (timed out)");
			}
			if (failed.getExitCode() != null) {
				text.append(" exit ").append(failed.getExitCode());
			}
			return text.toString();
		}
		if (result instanceof ScriptExecutionResult.Denied denied) {
			return "denied: " + denied.getReason();
		}
		return result.getClass().getSimpleName();
	}

}
