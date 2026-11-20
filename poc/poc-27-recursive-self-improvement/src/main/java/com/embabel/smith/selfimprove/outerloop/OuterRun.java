/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.outerloop;

import java.nio.file.Path;
import java.util.List;

/**
 * What an outer-loop run leaves behind: a move per generation, what each generation cost,
 * what the run stopped at, and what it never got to measure.
 *
 * <p>
 * There is deliberately no score on this type. A single number for a run is exactly the
 * reading the POC is trying to avoid — it flattens a series of paired deltas into a
 * leaderboard entry, and it lets a run that stopped at its budget look like a run that
 * finished. Every number here belongs to a generation or to a variant, and the two
 * reference values it was judged against, the {@link Baseline} and the
 * {@link NoiseFloor}, travel with their provenance so an injected one cannot read as a
 * measured one.
 *
 * @param generations one report per generation that reached a score
 * @param archive every variant scored, losers included, queryable by parent
 * @param curves archive and greedy selection over those identical generations
 * @param outcome what the run may claim, read off how many generations completed
 * @param modelCalls the metered calls the run spent
 * @param budget the ceiling declared before the first call was made
 * @param stopReason why the run stopped
 * @param notMeasured the measurements the run did not take, named
 * @param baseline the reference pass-rate, with its provenance
 * @param floor the noise floor improvement was judged against, with its provenance
 * @param heldIds the candidates handed to human control
 * @param recordFile the file the per-variant records were written to
 * @author Ivan Rodriguez
 */
public record OuterRun(List<GenerationReport> generations, Archive archive, SelectionCurves.Curves curves,
		RunOutcome outcome, int modelCalls, Budget budget, String stopReason, List<String> notMeasured,
		Baseline baseline, NoiseFloor floor, List<String> heldIds, Path recordFile) {

	/**
	 * The run report, as the run states it.
	 * @return the report text.
	 */
	public String describe() {
		List<VariantRecord> records = this.archive.all();
		StringBuilder out = new StringBuilder();
		out.append("outer loop: ")
			.append(this.generations.size())
			.append(" generation(s) scored, ")
			.append(records.size())
			.append(" variants, ")
			.append(this.modelCalls)
			.append(" model calls spent against a declared budget of ")
			.append(this.budget.describe())
			.append(System.lineSeparator());
		out.append("baseline ")
			.append(this.baseline.describe())
			.append(", noise floor ")
			.append(this.floor.describe())
			.append(System.lineSeparator());
		for (GenerationReport generation : this.generations) {
			out.append(generation.describe()).append(System.lineSeparator());
		}
		for (VariantRecord record : records) {
			out.append("  ").append(record.describe()).append(System.lineSeparator());
		}
		out.append(this.curves.describe()).append(System.lineSeparator());
		out.append("held for approval: ").append(this.heldIds).append(System.lineSeparator());
		out.append("stopped: ").append(this.stopReason).append(System.lineSeparator());
		if (!this.notMeasured.isEmpty()) {
			out.append("not measured:").append(System.lineSeparator());
			for (String gap : this.notMeasured) {
				out.append("  - ").append(gap).append(System.lineSeparator());
			}
		}
		out.append(this.outcome.statement());
		return out.toString();
	}

}
