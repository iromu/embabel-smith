/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The script calls a run actually made, kept as lines for whoever prints the run.
 *
 * <p>
 * <b>Why the calls are remembered at all.</b> Everything else this module reports is
 * known at ingest: which folders loaded, which names resolved, which engine was selected.
 * A script call is the one fact that only exists once a model has acted, so it needs a
 * place that outlives the call and is still reachable when the run is printed. That is
 * this object: one per boot, filled by {@link RecordingScriptExecutionEngine} as calls
 * happen, drained by the CLI after the run.
 *
 * <p>
 * <b>What a line claims and what it does not.</b> It names the skill the script came
 * from, the callable the model reached for, the engine that ran it, the status that came
 * back and how long it took — the five things 6.5 asks a run to be able to say. It does
 * not claim the call succeeded in the sense the step cared about: the status is the
 * script's exit status, quoted from the engine, and a zero that the model then ignored is
 * still a zero.
 *
 * <p>
 * <b>Why a list rather than a log.</b> The same core is embedded by the studio and driven
 * by the CLI (see {@link RunReport}), and a fact that only reaches a logger is invisible
 * to whichever of the two did not configure the appender. The list is thread-safe because
 * a tool call happens on whatever thread the tool loop is on, not on the one that started
 * the run.
 *
 * @author Ivan Rodriguez
 */
public final class ScriptCallLog {

	private final List<Call> calls = new CopyOnWriteArrayList<>();

	/**
	 * Remember one script call.
	 * @param skill the skill that carried the script
	 * @param callable the name the model called it under, before any reference prefix
	 * @param engine the engine that ran it, named rather than implied, because the switch
	 * says which was selected and this says which answered
	 * @param status the outcome as the engine described it
	 * @param elapsedMs whole milliseconds the call took
	 */
	public void record(String skill, String callable, String engine, String status, long elapsedMs) {
		this.calls.add(new Call(skill, callable, engine, status, elapsedMs));
	}

	/**
	 * The calls as lines, in the order they were made.
	 * @return one line per call, empty when nothing ran
	 */
	public List<String> lines() {
		return this.calls.stream().map(Call::line).toList();
	}

	/**
	 * Whether the run made no script call at all.
	 * @return true when nothing was recorded
	 */
	public boolean isEmpty() {
		return this.calls.isEmpty();
	}

	/**
	 * The calls as text, one per line, for a caller that prints rather than lists.
	 * @return the lines joined, empty when there were none
	 */
	public String describe() {
		return this.calls.isEmpty() ? "" : String.join("\n", lines()) + "\n";
	}

	/**
	 * One recorded call.
	 *
	 * @param skill the skill that carried the script
	 * @param callable the callable name it was offered under
	 * @param engine the engine class that ran it
	 * @param status the outcome as the engine described it
	 * @param elapsedMs whole milliseconds it took
	 */
	public record Call(String skill, String callable, String engine, String status, long elapsedMs) {

		/**
		 * The call as one line of the run's output.
		 * @return the line
		 */
		public String line() {
			return "script call: skill '" + this.skill + "' " + this.callable + " via " + this.engine + ": "
					+ this.status + " in " + this.elapsedMs + "ms";
		}

	}

}
