/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill.script;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

import com.embabel.agent.skills.script.ScriptArtifact;
import com.embabel.agent.skills.script.ScriptExecutionResult;
import kotlin.time.Duration;
import kotlin.time.DurationKt;
import kotlin.time.DurationUnit;

/**
 * Builds Embabel's {@link ScriptExecutionResult} variants from Java — the whole of the
 * Kotlin interop this engine needs, kept in one place.
 *
 * <p>
 * <b>Why reflection.</b> {@code Success} and {@code Failure} are Kotlin classes whose
 * only usable constructors are the private primaries: every parameter declares a Kotlin
 * default, so the compiler emits the all-args constructor as {@code private} plus a
 * {@code @JvmSynthetic} synthetic one that Java cannot see. There is no builder and no
 * factory on the sealed class, so a Java caller has exactly one door. {@code Success}'s
 * duration is also the {@code kotlin.time.Duration} value class, so its constructor takes
 * the encoded {@code long} rather than the object, and {@code Failure}'s takes the boxed
 * object — hence the two encodings here.
 *
 * <p>
 * The same approach the repo already uses where Kotlin mangles a name Java cannot write
 * (POC 7's {@code ToolGroupMetadata} factory lookup), and for the same reason: it is one
 * reflective site with a clear failure, rather than a Kotlin source file in a Java
 * module. The static initializer fails fast, with the exception in its message, if an
 * Embabel bump changes either signature — which is the only way this coupling can be paid
 * for.
 *
 * @author Ivan Rodriguez
 */
final class ScriptExecutionResults {

	private static final Constructor<ScriptExecutionResult.Success> SUCCESS;

	private static final Constructor<ScriptExecutionResult.Failure> FAILURE;

	private static final Method DURATION_BOX;

	static {
		try {
			SUCCESS = ScriptExecutionResult.Success.class.getDeclaredConstructor(String.class, String.class, int.class,
					long.class, List.class);
			SUCCESS.setAccessible(true);
			FAILURE = ScriptExecutionResult.Failure.class.getDeclaredConstructor(String.class, String.class,
					Integer.class, boolean.class, Duration.class);
			FAILURE.setAccessible(true);
			DURATION_BOX = Duration.class.getDeclaredMethod("box-impl", long.class);
			DURATION_BOX.setAccessible(true);
		}
		catch (NoSuchMethodException ex) {
			throw new ExceptionInInitializerError("Embabel ScriptExecutionResult constructors not found: " + ex);
		}
	}

	/**
	 * Utility class — static API only.
	 */
	private ScriptExecutionResults() {
	}

	/**
	 * A completed run: the script reached an exit code, whether or not that code was
	 * zero.
	 * @param stdout the captured standard output
	 * @param stderr the captured standard error
	 * @param exitCode the process-style exit code
	 * @param durationMillis how long the run took
	 * @param artifacts files the script wrote, or an empty list
	 * @return the result to hand back to Embabel
	 */
	static ScriptExecutionResult success(String stdout, String stderr, int exitCode, long durationMillis,
			List<ScriptArtifact> artifacts) {
		try {
			return SUCCESS.newInstance(stdout, stderr, exitCode, encode(durationMillis), artifacts);
		}
		catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("Failed to build ScriptExecutionResult.Success", ex);
		}
	}

	/**
	 * A run that failed, timed out, or was stopped mid-flight — the shape a script that
	 * raises gets, so the loop sees an exit-code-shaped answer rather than a stack.
	 * @param error the failure message the model should see
	 * @param stderr the captured standard error
	 * @param exitCode the exit code, or {@code null} when the script never reached one
	 * @param timedOut whether a limit stopped the run
	 * @param durationMillis how long the run took
	 * @return the result to hand back to Embabel
	 */
	static ScriptExecutionResult failure(String error, String stderr, Integer exitCode, boolean timedOut,
			long durationMillis) {
		try {
			return FAILURE.newInstance(error, stderr, exitCode, timedOut, box(encode(durationMillis)));
		}
		catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("Failed to build ScriptExecutionResult.Failure", ex);
		}
	}

	private static long encode(long durationMillis) {
		return DurationKt.toDuration(durationMillis, DurationUnit.MILLISECONDS);
	}

	private static Object box(long encoded) throws ReflectiveOperationException {
		return DURATION_BOX.invoke(null, encoded);
	}

}
