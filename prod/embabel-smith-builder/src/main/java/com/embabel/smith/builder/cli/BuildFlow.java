/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.cli;

/**
 * What drives a build, as {@code smith build --flow} names it. This is the CLI's own
 * switch, not a build option: there is deliberately no {@code smith.builder.flow}
 * property and no field for it in {@code BuildOptions} (design D3), because the flag is
 * per-invocation and visible in the transcript, which is what the parity and cost
 * comparison in task 4.2 needs to attribute a run to a flow.
 *
 * <p>
 * It is a different axis from {@code --technique}: {@code pack} changes who issues the
 * generation call, not how the description is turned into a spec. The two are independent
 * except where the pack flow has no pack to run, which is a refusal (task 3.5) rather
 * than a fallback.
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
	PACK("pack");

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
		throw new IllegalArgumentException(
				"Unknown build flow '" + value + "': expected " + JAVA.id + " or " + PACK.id);
	}

}
