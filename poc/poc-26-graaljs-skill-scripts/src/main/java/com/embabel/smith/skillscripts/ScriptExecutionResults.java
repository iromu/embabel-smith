/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.skillscripts;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

import com.embabel.agent.skills.script.ScriptArtifact;
import com.embabel.agent.skills.script.ScriptExecutionResult;
import kotlin.time.Duration;
import kotlin.time.DurationKt;
import kotlin.time.DurationUnit;

/**
 * Builds Embabel's {@link ScriptExecutionResult} variants from Java.
 *
 * <p>
 * {@code Success} and {@code Failure} are Kotlin classes whose only usable constructors are the
 * private primaries: every parameter declares a Kotlin default, so the compiler emits the
 * all-args constructor as {@code private} plus a {@code @JvmSynthetic} synthetic one that Java
 * cannot see. {@code Success}'s duration is also the {@code kotlin.time.Duration} value class, so
 * its constructor takes the encoded {@code long} rather than the object. This factory reaches the
 * primaries reflectively and encodes the duration with {@link DurationKt#toDuration}, keeping the
 * Kotlin interop in one place — the same approach {@code ShellToolGroupFactory} uses for the
 * mangled {@code ToolGroupMetadata} factory in POC 7.
 * @author Ivan Rodriguez
 */
public final class ScriptExecutionResults {

	private static final Constructor<ScriptExecutionResult.Success> SUCCESS;

	private static final Constructor<ScriptExecutionResult.Failure> FAILURE;

	private static final Method DURATION_BOX;

	static {
		try {
			SUCCESS = ScriptExecutionResult.Success.class
				.getDeclaredConstructor(String.class, String.class, int.class, long.class, List.class);
			SUCCESS.setAccessible(true);
			FAILURE = ScriptExecutionResult.Failure.class
				.getDeclaredConstructor(String.class, String.class, Integer.class, boolean.class, Duration.class);
			FAILURE.setAccessible(true);
			DURATION_BOX = Duration.class.getDeclaredMethod("box-impl", long.class);
			DURATION_BOX.setAccessible(true);
		}
		catch (NoSuchMethodException ex) {
			throw new ExceptionInInitializerError("Embabel ScriptExecutionResult constructors not found: " + ex);
		}
	}

	/** Utility class — static API only. */
	private ScriptExecutionResults() {
	}

	/**
	 * A completed run.
	 * @param stdout the captured standard output
	 * @param stderr the captured standard error
	 * @param exitCode the process-style exit code
	 * @param durationMillis how long the run took
	 * @param artifacts files the script wrote, or an empty list
	 * @return the result to hand back to Embabel.
	 */
	public static ScriptExecutionResult success(String stdout, String stderr, int exitCode, long durationMillis,
			List<ScriptArtifact> artifacts) {
		try {
			return SUCCESS.newInstance(stdout, stderr, exitCode, encode(durationMillis), artifacts);
		}
		catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("Failed to build ScriptExecutionResult.Success", ex);
		}
	}

	/**
	 * A run that failed, timed out, or was rejected mid-flight.
	 * @param error the failure message the model should see
	 * @param stderr the captured standard error
	 * @param exitCode the exit code, or {@code null} when the script never reached one
	 * @param timedOut whether the time limit stopped the run
	 * @param durationMillis how long the run took
	 * @return the result to hand back to Embabel.
	 */
	public static ScriptExecutionResult failure(String error, String stderr, Integer exitCode, boolean timedOut,
			long durationMillis) {
		try {
			return FAILURE.newInstance(error, stderr, exitCode, timedOut, box(encode(durationMillis)));
		}
		catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("Failed to build ScriptExecutionResult.Failure", ex);
		}
	}

	/**
	 * A script refused before it ran.
	 * @param reason why the script was refused
	 * @return the result to hand back to Embabel.
	 */
	public static ScriptExecutionResult denied(String reason) {
		return new ScriptExecutionResult.Denied(reason);
	}

	private static long encode(long durationMillis) {
		return DurationKt.toDuration(durationMillis, DurationUnit.MILLISECONDS);
	}

	private static Object box(long encoded) throws ReflectiveOperationException {
		return DURATION_BOX.invoke(null, encoded);
	}

}
