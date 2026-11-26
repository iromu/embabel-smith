/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.tools;

import java.util.ArrayList;
import java.util.List;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.core.ToolGroup;
import com.embabel.smith.builder.improve.Detector;
import com.embabel.smith.builder.improve.TraceWindowReader;
import com.embabel.smith.common.trace.TraceRecord;
import com.embabel.smith.common.trace.TraceStore;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The {@code traces} tool group: what this pack's recent runs did, and whether they
 * regressed, read through the builder's own {@link TraceWindowReader} and
 * {@link Detector}.
 *
 * <p>
 * <b>Where the store comes from.</b> The driver's. {@code TraceStore} is a
 * {@code smith-common} SPI, which this module depends on, but its file implementation
 * {@code JsonlTraceStore} lives in {@code embabel-smith-runtime}, which this module
 * deliberately does not depend on — so a trace tool does not go looking for a JSONL file
 * and inventing a path. A driver that wants its runs readable hands the store over on
 * {@link ToolSurface#openedTraces}; a driver that does not gets the honest answer below,
 * which is the same fact stated rather than a fabricated empty window.
 *
 * <p>
 * <b>Which regression triggers are reported.</b> The failed-run trigger, and only that.
 * The other two {@code Detector} knows — content drift against the lifecycle sidecar, and
 * a re-eval below the recorded baseline — need the pack's sidecar and a live re-eval,
 * which are a stage's inputs, not a drafting step's. Reporting two of three triggers and
 * naming the reason is the honest version of a regression tool; reporting a clean result
 * from a partial check would not be.
 *
 * @author Ivan Rodriguez
 */
@Configuration
public class TraceToolGroup {

	/** The tool-group role a pack step declares to read its run history. */
	public static final String ROLE = "traces";

	/** The tool name for the recent window. */
	public static final String TRACE_WINDOW = "trace_window";

	/** The tool name for the regression check. */
	public static final String DETECT_REGRESSIONS = "detect_regressions";

	/** The argument's name: the spec whose runs to read. */
	public static final String SPEC = "spec";

	/** How many runs of the window the listing shows. */
	static final int LISTED_RUNS = 20;

	/**
	 * The group the platform registers under {@link #ROLE}.
	 * @param surface what the driver opened for this build
	 * @return the {@code traces} tool group
	 */
	@Bean
	public ToolGroup traceTools(ToolSurface surface) {
		return ToolGroups.ofTools(ROLE, "Read what this spec's recent runs did — succeeded, failed, and why — and ask "
				+ "whether it has regressed. Read-only history; nothing here changes what a run is allowed to do.",
				List.of(traceWindow(surface), detectRegressions(surface)));
	}

	private static Tool traceWindow(ToolSurface surface) {
		return Tool.Companion.of(TRACE_WINDOW,
				"List the most recent recorded runs of a spec, newest first, with each run's outcome and failure "
						+ "reason.",
				Tool.InputSchema.of(Tool.Parameter.string(SPEC, "The spec whose runs to read", true)),
				Tool.Metadata.DEFAULT,
				(Tool.Function) (arguments) -> Tool.Result.text(ToolOutput.cap(surface.remembered(TRACE_WINDOW,
						arguments, () -> window(surface, ToolArguments.string(arguments, SPEC))))));
	}

	private static Tool detectRegressions(ToolSurface surface) {
		return Tool.Companion.of(DETECT_REGRESSIONS,
				"Report whether a spec has regressed: failed runs that were neither a trust-tier block nor a "
						+ "lifecycle block. Content drift and below-baseline re-eval are checked by the build's own "
						+ "stages, not here.",
				Tool.InputSchema.of(Tool.Parameter.string(SPEC, "The spec to check", true)), Tool.Metadata.DEFAULT,
				(Tool.Function) (arguments) -> Tool.Result.text(ToolOutput.cap(surface.remembered(DETECT_REGRESSIONS,
						arguments, () -> regressions(surface, ToolArguments.string(arguments, SPEC))))));
	}

	/**
	 * The window as text: how many runs, how many failed, and the recent ones.
	 * @param surface the driver's trace store, if any
	 * @param spec the spec named
	 * @return the window, or the reason there is none
	 */
	static String window(ToolSurface surface, String spec) {
		TraceStore store = surface.traceStore();
		if (store == null) {
			return NO_STORE;
		}
		if (spec.isBlank()) {
			return TRACE_WINDOW + " needs the '" + SPEC + "' argument" + specsHint(store) + ".";
		}
		List<TraceRecord> window = new TraceWindowReader(store).window(spec);
		if (window.isEmpty()) {
			return "No recorded runs for '" + spec + "'" + specsHint(store) + ".";
		}
		List<String> lines = new ArrayList<>();
		lines.add(window.size() + " recorded runs of '" + spec + "', " + failures(window) + " failed:");
		window.subList(Math.max(0, window.size() - LISTED_RUNS), window.size())
			.reversed()
			.forEach((record) -> lines.add("  " + describe(record)));
		return String.join("\n", lines);
	}

	/**
	 * The failed-run half of {@link Detector}'s check, reported rather than acted on:
	 * what the improvement loop would queue on, minus the policy blocks a prompt cannot
	 * fix.
	 * @param surface the driver's trace store, if any
	 * @param spec the spec named
	 * @return the regression report, or the reason there is none
	 */
	static String regressions(ToolSurface surface, String spec) {
		TraceStore store = surface.traceStore();
		if (store == null) {
			return NO_STORE;
		}
		if (spec.isBlank()) {
			return DETECT_REGRESSIONS + " needs the '" + SPEC + "' argument" + specsHint(store) + ".";
		}
		List<TraceRecord> window = new TraceWindowReader(store).window(spec);
		if (window.isEmpty()) {
			return "No recorded runs for '" + spec + "', so there is nothing to compare" + specsHint(store) + ".";
		}
		List<TraceRecord> failures = Detector.failedNonPolicyBlock(window);
		List<String> lines = new ArrayList<>();
		lines
			.add(failures.isEmpty() ? "No regression evidence in the last " + window.size() + " runs of '" + spec + "'."
					: failures.size() + " failed runs of '" + spec + "' that were neither a tier block nor a lifecycle "
							+ "block — the evidence an improvement loop would queue on.");
		if (!failures.isEmpty()) {
			failures.forEach((record) -> lines.add("  " + describe(record)));
		}
		lines.add("(content drift and below-baseline re-eval are the build's own checks, run against the pack's "
				+ "lifecycle sidecar)");
		return String.join("\n", lines);
	}

	/**
	 * The answer when no driver wired a store, which is the builder CLI's answer today.
	 */
	static final String NO_STORE = "No trace store is wired into this build, so there is no run history to read. "
			+ "The builder records no traces of its own; the store is the driver's to supply.";

	private static String describe(TraceRecord record) {
		String pack = (record.pack() != null) ? record.pack().name() : "(no pack)";
		return (record.success() ? "succeeded" : "FAILED") + " " + record.goal() + " of " + pack
				+ ((record.error() == null || record.error().isBlank()) ? "" : " — " + record.error());
	}

	private static long failures(List<TraceRecord> window) {
		return window.stream().filter((record) -> !record.success()).count();
	}

	private static String specsHint(TraceStore store) {
		List<String> specs = new TraceWindowReader(store).specs();
		return specs.isEmpty() ? "" : "; specs with traces are " + specs;
	}

}
