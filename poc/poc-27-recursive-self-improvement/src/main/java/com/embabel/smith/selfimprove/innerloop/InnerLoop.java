/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.innerloop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The inner loop: generate policies, run them over both corpus splits, score them, keep
 * every one.
 *
 * <p>
 * It is the free loop by design (D3, D5). Nothing here constructs a model client and
 * nothing here calls {@link ModelCallMeter#counted}; the count each record carries is
 * read off the meter anyway, so the zero is a measurement rather than a claim.
 * Convergence is a patience counter on the tuning score — the loop stops when its best
 * has stopped moving, which is the honest end of a search this cheap, and the run reports
 * which end it reached.
 *
 * <p>
 * Every generation is retained with its parent and its score (D6), including the ones
 * that score below their parent and the ones that never scored at all. A generation that
 * reaches for a fixed artifact is the exception the spec settles: it is named, it is
 * recorded, and it is scored as nothing — and the run stops there, because continuing
 * would be continuing past a breach of the boundary the whole claim is relative to.
 *
 * @author Ivan Rodriguez
 */
public final class InnerLoop {

	/** The run's best stopped moving before its budget ran out. */
	public static final String CONVERGED = "converged";

	/** The run used every variant it was given. */
	public static final String BUDGET_EXHAUSTED = "budget_exhausted";

	/** The run stopped at a generation that reached for a fixed artifact. */
	public static final String STOPPED_AT_REACH = "stopped_at_fixed_artifact_reach";

	/**
	 * The file the per-generation records are written to, under the run's working
	 * directory.
	 */
	public static final String RECORD_FILE = "generations.jsonl";

	private static final ObjectMapper JSON = new ObjectMapper();

	/** The engine's words for a {@code readFile} of something the run did not stage. */
	private static final String UNSTAGED = "was not staged";

	private final PolicyRunner runner;

	private final ModelCallMeter meter;

	private final Listener listener;

	/**
	 * Create a loop.
	 * @param runner the actor-side door that runs a policy through POC 26's engine
	 * @param meter the counter every paid call has to pass through
	 * @param listener progress callbacks, for the run book and for tests
	 */
	public InnerLoop(PolicyRunner runner, ModelCallMeter meter, Listener listener) {
		this.runner = runner;
		this.meter = meter;
		this.listener = listener;
	}

	/**
	 * Run the loop.
	 * @param variants the generations, seed first; the caller generates them so a run can
	 * be replayed with an explicit list
	 * @param tuning the split policies are fitted against
	 * @param heldout the split they are judged on and never fitted against
	 * @param patience scored generations without an improvement before the run calls it
	 * converged
	 * @param workDir where to write each policy's skill folder and the per-generation
	 * records
	 * @return the run.
	 */
	public Run run(List<PolicyGenerator.Variant> variants, List<TraceCase> tuning, List<TraceCase> heldout,
			int patience, Path workDir) {
		List<GenerationRecord> records = new ArrayList<>();
		GenerationRecord best = null;
		int sinceBest = 0;
		String stopReason = BUDGET_EXHAUSTED;
		for (PolicyGenerator.Variant variant : variants) {
			GenerationRecord record = generate(variant, tuning, heldout, workDir);
			records.add(record);
			this.listener.afterGeneration(record);
			if (GenerationRecord.REJECTED.equals(record.status())) {
				stopReason = STOPPED_AT_REACH;
				break;
			}
			if (!GenerationRecord.SCORED.equals(record.status())) {
				continue;
			}
			if (record.beats(best)) {
				best = record;
				sinceBest = 0;
			}
			else {
				sinceBest++;
				if (sinceBest >= patience) {
					stopReason = CONVERGED;
					break;
				}
			}
		}
		Path recordFile = workDir.resolve(RECORD_FILE);
		writeRecords(records, recordFile);
		return new Run(List.copyOf(records), (best != null) ? best.variantId() : null, this.meter.count(), stopReason,
				recordFile);
	}

	private GenerationRecord generate(PolicyGenerator.Variant variant, List<TraceCase> tuning, List<TraceCase> heldout,
			Path workDir) {
		int before = this.meter.count();
		this.listener.beforeGeneration(variant);
		long started = System.nanoTime();
		PolicyRun onTuning = this.runner.run(variant.script(), tuning, workDir);
		if (!onTuning.ran()) {
			return refusalOrFailure(variant, onTuning, spent(before), elapsed(started));
		}
		SplitScore tuningScore = SplitScore.of(LabeledTraceReader.TUNING, tuning, onTuning.verdicts());
		PolicyRun onHeldout = this.runner.run(variant.script(), heldout, workDir);
		if (!onHeldout.ran()) {
			return refusalOrFailure(variant, onHeldout, spent(before), elapsed(started));
		}
		SplitScore heldoutScore = SplitScore.of(LabeledTraceReader.HELDOUT, heldout, onHeldout.verdicts());
		GenerationRecord record = GenerationRecord.scored(variant, tuningScore, heldoutScore, spent(before),
				elapsed(started));
		return record;
	}

	private GenerationRecord refusalOrFailure(PolicyGenerator.Variant variant, PolicyRun run, int modelCalls,
			long durationMillis) {
		String fixedArtifact = fixedArtifact(run.diagnostic());
		if (fixedArtifact != null) {
			return GenerationRecord.rejected(variant, fixedArtifact, run.diagnostic(), modelCalls, durationMillis);
		}
		return GenerationRecord.failed(variant, run.diagnostic(), modelCalls, durationMillis);
	}

	/**
	 * The fixed artifact a denial says the script reached for, or null when the denial is
	 * about something else. It is read from the engine's own words, not from the operator
	 * that emitted the probe, so the record's reason is the denial rather than a guess
	 * about intent. The names are the ones {@code evidence/MUTABILITY.md} gives them.
	 * @param diagnostic the engine's explanation for a run that produced no verdicts
	 * @return the artifact name, or null.
	 */
	static String fixedArtifact(String diagnostic) {
		if (diagnostic == null) {
			return null;
		}
		if (diagnostic.contains(UNSTAGED)) {
			String name = quotedBefore(diagnostic, UNSTAGED);
			return (name != null && name.endsWith(".jsonl")) ? "trace-corpus" : "script-engine-limits";
		}
		return diagnostic.contains("not allowed") ? "script-engine-limits" : null;
	}

	// The quoted name the engine puts immediately before its marker, so the artifact
	// comes from the
	// denial's own words about what it refused rather than from the first apostrophe in a
	// message
	// that may also echo the offending source line.
	private static String quotedBefore(String diagnostic, String marker) {
		int close = diagnostic.lastIndexOf('\'', diagnostic.indexOf(marker));
		int open = (close >= 0) ? diagnostic.lastIndexOf('\'', close - 1) : -1;
		return (open >= 0 && close > open) ? diagnostic.substring(open + 1, close) : null;
	}

	private int spent(int when) {
		return this.meter.count() - when;
	}

	private static long elapsed(long startedNanos) {
		return (System.nanoTime() - startedNanos) / 1_000_000L;
	}

	/**
	 * Write the per-generation records, one JSON object per line.
	 * @param records the records to write
	 * @param recordFile the file to write them to
	 */
	public static void writeRecords(List<GenerationRecord> records, Path recordFile) {
		StringBuilder out = new StringBuilder();
		for (GenerationRecord record : records) {
			try {
				out.append(JSON.writeValueAsString(record.asMap())).append('\n');
			}
			catch (IOException ex) {
				throw new IllegalStateException(
						"could not render the generation record " + record.index() + ": " + ex.getMessage(), ex);
			}
		}
		try {
			Files.writeString(recordFile, out.toString(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException("could not write " + recordFile + ": " + ex.getMessage(), ex);
		}
	}

	/**
	 * Progress out of the loop. Both methods default to doing nothing, so a caller that
	 * only wants the records can pass {@link Listener#NONE}.
	 *
	 * @author Ivan Rodriguez
	 */
	public interface Listener {

		/** The listener nobody needs. */
		Listener NONE = new Listener() {
		};

		/**
		 * A generation is about to run.
		 * @param variant the variant about to be scored
		 */
		default void beforeGeneration(PolicyGenerator.Variant variant) {
		}

		/**
		 * A generation has been recorded.
		 * @param record the record written for it
		 */
		default void afterGeneration(GenerationRecord record) {
		}

	}

	/**
	 * What a run produced.
	 *
	 * @param records every generation's record, in order, including the ones that scored
	 * nothing
	 * @param bestVariantId the variant selection settled on, or null when nothing scored
	 * @param modelCalls the total the meter counted for the whole run
	 * @param stopReason why the run ended
	 * @param recordFile the file the records were written to
	 * @author Ivan Rodriguez
	 */
	public record Run(List<GenerationRecord> records, String bestVariantId, int modelCalls, String stopReason,
			Path recordFile) {
	}

}
