/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.util.List;

/**
 * What an ingest noticed that a reader needs and the run itself would not otherwise say:
 * skill folders the loader refused, names that resolved to nothing, which folder answered
 * a shadowed name, and the script state in force.
 *
 * <p>
 * Carried by the ingest rather than logged, because the same core is embedded by the
 * studio and driven by the CLI, and a silent absence in one of them cannot be fixed by a
 * log line in the other. The CLI prints these to stderr so the goal's own output stays
 * clean on stdout.
 *
 * @param lines the lines to show, in the order the ingest produced them
 * @author Ivan Rodriguez
 */
public record RunReport(List<String> lines) {

	public RunReport {
		lines = List.copyOf(lines);
	}

	/**
	 * The report with nothing to say — an ingest of packs that carry no skills.
	 * @return the empty report
	 */
	public static RunReport none() {
		return new RunReport(List.of());
	}

	/**
	 * Whether there is anything worth printing.
	 * @return true when the ingest noticed something
	 */
	public boolean isEmpty() {
		return this.lines.isEmpty();
	}

	/**
	 * The lines as text, one per line, for a caller that prints rather than lists.
	 * @return the report as text, empty when there is nothing to say
	 */
	public String describe() {
		return this.lines.isEmpty() ? "" : String.join("\n", this.lines) + "\n";
	}

}
