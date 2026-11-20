/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.innerloop;

import java.nio.file.Path;
import java.util.List;

import com.embabel.smith.skillscripts.GraalJsSkillScriptExecutionEngine;

/**
 * The inner loop as a command: generate, run, score, record — offline, with no endpoint
 * and no model call. It is the actor side only; the referee is a separate hand-started
 * process ({@code referee.RefereeProcess}) and this class neither starts it nor hands it
 * a script (D9).
 *
 * <p>
 * Flags: {@code --out
 *
<dir>
 * } where the run writes its policies and its records, {@code --seed
 * <long>} the generation seed, {@code --generations <n>} how many variants to generate,
 * {@code --patience <n>} scored generations without an improvement before the run calls
 * itself converged.
 *
 * @author Ivan Rodriguez
 */
public final class InnerLoopMain {

	private InnerLoopMain() {
	}

	/**
	 * Runs the inner loop and prints what it measured.
	 * @param args the flags described on the class
	 */
	public static void main(String[] args) {
		Path out = Path.of(flag(args, "--out", System.getProperty("java.io.tmpdir") + "/poc27-inner-loop"));
		long seed = Long.parseLong(flag(args, "--seed", "27"));
		int generations = Integer.parseInt(flag(args, "--generations", "12"));
		int patience = Integer.parseInt(flag(args, "--patience", "3"));

		List<TraceCase> tuning = LabeledTraceReader.read(LabeledTraceReader.TUNING);
		List<TraceCase> heldout = LabeledTraceReader.read(LabeledTraceReader.HELDOUT);
		PolicyRunner runner = new PolicyRunner(new GraalJsSkillScriptExecutionEngine());
		System.out.println("inner loop — " + tuning.size() + " tuning lines, " + heldout.size()
				+ " held-out lines, free by design (D5)");
		System.out.println("hand-written : " + handwrittenBaseline(runner, heldout, out).describe());

		ModelCallMeter meter = new ModelCallMeter();
		InnerLoop loop = new InnerLoop(runner, meter, printing());
		List<PolicyGenerator.Variant> variants = PolicyGenerator.variants(PolicyPrompt.frozenSeed(), seed, generations,
				PolicyGenerator.SEMANTIC_MUTATIONS);
		InnerLoop.Run run = loop.run(variants, tuning, heldout, patience, out);

		System.out
			.println("generations  : " + run.records().size() + " of " + variants.size() + " — " + run.stopReason());
		System.out.println("best variant : " + run.bestVariantId());
		System.out.println("model calls  : " + run.modelCalls() + " read off the meter");
		System.out.println("records      : " + run.recordFile());
	}

	private static SplitScore handwrittenBaseline(PolicyRunner runner, List<TraceCase> heldout, Path out) {
		PolicyRun baseline = runner.run(DetectionPolicy.handWritten(), heldout, out);
		if (!baseline.ran()) {
			throw new IllegalStateException("the hand-written policy did not run: " + baseline.diagnostic());
		}
		return SplitScore.of(LabeledTraceReader.HELDOUT, heldout, baseline.verdicts());
	}

	private static InnerLoop.Listener printing() {
		return new InnerLoop.Listener() {
			@Override
			public void afterGeneration(GenerationRecord record) {
				System.out.println("  g" + record.index() + " " + record.operator() + " " + record.status() + " ["
						+ record.modelCalls() + " model calls] " + line(record));
			}
		};
	}

	private static String line(GenerationRecord record) {
		return (record.tuning() != null) ? record.tuning().describe() + " / " + record.heldout().describe()
				: record.diagnostic();
	}

	private static String flag(String[] args, String name, String fallback) {
		for (int i = 0; i < args.length - 1; i++) {
			if (name.equals(args[i])) {
				return args[i + 1];
			}
		}
		return fallback;
	}

}
