/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.cli;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * What drives a build, as {@code smith build --flow} names it. This is the CLI's own
 * switch, not a build option: there is deliberately no {@code smith.builder.flow}
 * property and no field for it in {@code BuildOptions} (design D3), because the flag is
 * per-invocation, and {@code BuildResult.report()} prints the flow that ran, which is
 * what lets a parity or cost run be attributed from its output rather than from how it
 * was launched.
 *
 * <p>
 * It is a different axis from {@code --technique}: {@code pack} changes who issues the
 * generation call, not how the description is turned into a spec. The two are independent
 * except where the pack flow has no pack to run, which is a refusal (task 3.5) rather
 * than a fallback.
 *
 * <p>
 * {@code agentic} differs from {@code pack} in one further respect: the checks are not
 * only offered to the pack, they are run by the build.
 * {@code eval.AgenticAgentSpecGenerator} validates and scans each draft in Java and
 * re-invokes the same pack with the findings when they report something, because a step
 * that was told to validate its own draft measurably declines to (design D9's finding).
 * Neither pack flow falls back to the Java generator; {@code java} remains the shipped
 * default and nothing here changes what it does.
 *
 * <p>
 * {@code harness} is the fourth, and it differs from {@code agentic} in who decides what
 * to read. The agentic pack's two steps are handed the corpus by the driver and see the
 * same blob; the harness pack's steps are given the document <em>roots</em> and choose
 * among them — {@code list_docs} for what exists, {@code read_doc} for what it decides it
 * needs — across three steps (survey, plan, draft) that the driver re-enters with what
 * its own checks said. The roots stay the driver's, as they are for every flow
 * ({@code tools/ToolSurface}); only the choice of which document, in what order, moves to
 * the step. The checks are forced in the same way, by
 * {@code eval.HarnessAgentSpecGenerator}.
 *
 * @author Ivan Rodriguez
 */
public enum BuildFlow {

	/**
	 * The shipped behaviour: the Java stages call the generator directly (the default).
	 */
	JAVA("java"),

	/**
	 * The builder's own {@code builder-spec-one-shot} pack issues the generation call.
	 * Its step declares the {@code validate} tool group, and the builder registers that
	 * group ({@code tools/ValidationToolGroup}), so what the pack can reach is now partly
	 * the pack's own declaration rather than wholly the driver's.
	 */
	PACK("pack"),

	/**
	 * The builder's own {@code builder-agentic-blueprint} pack issues the generation
	 * call, and the driver forces the deterministic checks between model calls: it runs
	 * the validator and the scanner in Java on each draft and, where they report
	 * findings, re-invokes the same pack with those findings appended rather than
	 * shipping a draft nobody checked. The pack's steps declare the {@code blueprint},
	 * {@code library}, {@code validate} and {@code security} groups; the driver opened
	 * the documents and the library they read, so a step asks for content rather than
	 * naming a path it guessed.
	 */
	AGENTIC("agentic"),

	/**
	 * The builder's own {@code builder-harness} pack issues the generation call over
	 * three steps that read the build's document roots through tools — survey what is
	 * there, decide what to read next, draft the spec — instead of receiving a corpus the
	 * driver assembled. The roots come from the command line via
	 * {@code tools/ToolSurface} and a step can only name a document a listing printed;
	 * the choice of which document, in what order, is the step's. As on the agentic flow,
	 * the validator and the scanner are run by the build on each draft
	 * ({@code eval.HarnessAgentSpecGenerator}), so a step that declines the tool it was
	 * offered still cannot ship an unchecked draft.
	 */
	HARNESS("harness");

	private final String id;

	BuildFlow(String id) {
		this.id = id;
	}

	/**
	 * The user-facing name of the flow, as accepted by {@code --flow}.
	 * @return the flow id.
	 */
	public String id() {
		return this.id;
	}

	/**
	 * Parses a flow name strictly, so a typo is a usage error rather than a silent choice
	 * of the default.
	 * @param value the name to parse, in either id ({@code java}) or constant
	 * ({@code JAVA}) form, case-insensitively.
	 * @return the flow named.
	 * @throws IllegalArgumentException if the value names no flow.
	 */
	public static BuildFlow parse(String value) {
		for (BuildFlow flow : values()) {
			if (flow.id.equalsIgnoreCase(value) || flow.name().equalsIgnoreCase(value)) {
				return flow;
			}
		}
		throw new IllegalArgumentException("Unknown build flow '" + value + "': expected "
				+ Arrays.stream(values()).map(BuildFlow::id).collect(Collectors.joining(", ")));
	}

}
