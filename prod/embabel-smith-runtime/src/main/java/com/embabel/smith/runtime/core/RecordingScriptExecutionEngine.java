/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import com.embabel.agent.skills.script.ScriptExecutionResult;
import com.embabel.agent.skills.script.ScriptLanguage;
import com.embabel.agent.skills.script.SkillScript;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;

/**
 * The engine the switch selected, with each call written down and its output bounded.
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
 * <b>Why the ceiling is here too.</b> A script that prints without bound hands all of it
 * over: {@code ScriptTool} appends the captured streams as they came and the platform's
 * IO pump caps nothing, so a runaway printer reaches the model entire. The engine is the
 * last place that output exists as one value before it is formatted into a tool result,
 * which makes it the cheapest place to bound it — and the only reachable one, since a
 * {@code Success} cannot be constructed by a Java caller. It can be <i>rewritten</i>,
 * though: the value class's {@code copy} method is public, and it is resolved by shape —
 * return type and parameter types, never by the mangled spelling — so a platform release
 * that renames it trips a pin-test at build time rather than silently uncapping every
 * run. Where the rewrite is ever unavailable the result passes through untouched and the
 * run says the cap could not be applied, because a ceiling quietly not in force is worse
 * than no ceiling at all.
 *
 * <p>
 * <b>It adds nothing else to what runs.</b> Languages, refusals and results are the
 * delegate's, passed straight through, so the tool list a step is offered and the answer
 * a script gives are what the selected engine alone would produce, less any output past
 * the ceiling. The other addition is a line in {@link ScriptCallLog}. Where a caller
 * needs to know which engine is really underneath — the environment disclosure in
 * {@link PackSkills} is the one place that does — it asks
 * {@link ScriptEngineConfig#underlying(SkillScriptExecutionEngine)} rather than guessing.
 *
 * @author Ivan Rodriguez
 */
public final class RecordingScriptExecutionEngine implements SkillScriptExecutionEngine {

	/**
	 * How many characters one call may hand over when the run says nothing — a generous
	 * reading of "the model can read this", not a page limit.
	 */
	public static final int DEFAULT_OUTPUT_CAP = 16_000;

	private final SkillScriptExecutionEngine delegate;

	private final ScriptCallLog calls;

	private final int outputCap;

	private final SuccessRewriter rewriter;

	/**
	 * Wraps the engine a selection installed, with the default ceiling on output.
	 * @param delegate the engine that actually runs scripts
	 * @param calls where each call is written
	 */
	public RecordingScriptExecutionEngine(SkillScriptExecutionEngine delegate, ScriptCallLog calls) {
		this(delegate, calls, DEFAULT_OUTPUT_CAP);
	}

	/**
	 * Wraps the engine with a chosen ceiling.
	 * @param delegate the engine that actually runs scripts
	 * @param calls where each call is written
	 * @param outputCap characters one call may hand over; zero or less leaves it uncapped
	 */
	public RecordingScriptExecutionEngine(SkillScriptExecutionEngine delegate, ScriptCallLog calls, int outputCap) {
		this(delegate, calls, outputCap, RecordingScriptExecutionEngine::shortened);
	}

	/**
	 * Wraps the engine with a chosen ceiling and a named way to shorten a result, which
	 * exists so the branch where shortening is impossible is assertable rather than
	 * argued about.
	 * @param delegate the engine that actually runs scripts
	 * @param calls where each call is written
	 * @param outputCap characters one call may hand over
	 * @param rewriter how a printed result is shortened
	 */
	RecordingScriptExecutionEngine(SkillScriptExecutionEngine delegate, ScriptCallLog calls, int outputCap,
			SuccessRewriter rewriter) {
		this.delegate = delegate;
		this.calls = calls;
		this.outputCap = outputCap;
		this.rewriter = rewriter;
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
		ScriptExecutionResult result = capped(this.delegate.execute(script, args, stdin, inputFiles));
		this.calls.record(script.getSkillName(), script.getToolName(), this.delegate.getClass().getSimpleName(),
				status(result), (System.nanoTime() - started) / 1_000_000);
		return result;
	}

	/**
	 * The result with its printed part brought under the ceiling.
	 * @param result what the delegate returned
	 * @return the same result when it fits, is not a printed result, or cannot be
	 * rewritten
	 */
	private ScriptExecutionResult capped(ScriptExecutionResult result) {
		if (!(result instanceof ScriptExecutionResult.Success succeeded) || this.outputCap <= 0) {
			return result;
		}
		if (succeeded.getStdout().length() + succeeded.getStderr().length() <= this.outputCap) {
			return result;
		}
		ScriptExecutionResult.Success shortened = this.rewriter.shorten(succeeded, this.outputCap);
		if (shortened == null) {
			this.calls.outputUncapped(succeeded.getStdout().length() + succeeded.getStderr().length());
			return result;
		}
		return shortened;
	}

	/**
	 * Keeps the first {@code cap} characters the script printed and says, in the output
	 * itself, that it did. Which stream the sentence lands in follows the cut: a script
	 * whose standard output was the long one gets the sentence at the end of what remains
	 * of it, and one that printed nothing but roared on standard error gets it there.
	 * @param succeeded what the engine returned
	 * @param cap characters to keep
	 * @return the rewritten result, or {@code null} when the platform offers no way to
	 * build one
	 */
	private static ScriptExecutionResult.Success shortened(ScriptExecutionResult.Success succeeded, int cap) {
		String stdout = succeeded.getStdout();
		String stderr = succeeded.getStderr();
		boolean stdoutCut = stdout.length() > cap;
		int room = stdoutCut ? 0 : cap - Math.min(stdout.length(), cap);
		String keptErr = stderr.substring(0, Math.min(stderr.length(), room));
		boolean stderrCut = keptErr.length() < stderr.length();
		String said = "\n=== output truncated: " + (stdout.length() + stderr.length()) + " characters printed, "
				+ "showing the first "
				+ (stdoutCut ? Math.min(stdout.length(), cap) + keptErr.length() : keptErr.length()) + " ===";
		try {
			Method copy = copyMethod();
			if (copy == null) {
				return null;
			}
			copy.setAccessible(true);
			return (ScriptExecutionResult.Success) copy.invoke(succeeded,
					stdoutCut ? stdout.substring(0, Math.min(stdout.length(), cap)) + said : stdout,
					stderrCut ? keptErr + said : stderr, Integer.valueOf(succeeded.getExitCode()), duration(succeeded),
					succeeded.getArtifacts());
		}
		catch (ReflectiveOperationException | RuntimeException ex) {
			return null;
		}
	}

	/**
	 * The value class's copy method, found by what it takes and returns rather than by
	 * how the mangler spelled it, so a rename trips the pin-test instead of uncapping
	 * runs.
	 * @return the method, or {@code null} when this platform version has no such shape
	 */
	private static Method copyMethod() {
		for (Method candidate : ScriptExecutionResult.Success.class.getDeclaredMethods()) {
			if (candidate.getReturnType().equals(ScriptExecutionResult.Success.class)
					&& java.util.Arrays.equals(candidate.getParameterTypes(),
							new Class<?>[] { String.class, String.class, int.class, long.class, List.class })) {
				return candidate;
			}
		}
		return null;
	}

	/**
	 * The duration to carry into the copy, read the same way — by shape, not spelling.
	 * @param succeeded what the engine returned
	 * @return the encoded duration to hand back unchanged
	 */
	private static long duration(ScriptExecutionResult.Success succeeded) throws ReflectiveOperationException {
		for (Method candidate : ScriptExecutionResult.Success.class.getDeclaredMethods()) {
			if (candidate.getName().startsWith("getDuration") && candidate.getParameterCount() == 0
					&& candidate.getReturnType().equals(long.class)) {
				candidate.setAccessible(true);
				return ((Long) candidate.invoke(succeeded)).longValue();
			}
		}
		throw new NoSuchMethodException("no duration reader on " + ScriptExecutionResult.Success.class.getName());
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

	/**
	 * How a successful result is cut down to the ceiling, named so that the branch where
	 * it cannot be done is something a test can ask for.
	 */
	@FunctionalInterface
	interface SuccessRewriter {

		/**
		 * Cut the printed part of a result down to the ceiling.
		 * @param succeeded what the engine returned
		 * @param cap how many characters may be kept
		 * @return the shortened result, or {@code null} when it cannot be built
		 */
		ScriptExecutionResult.Success shorten(ScriptExecutionResult.Success succeeded, int cap);

	}

}
