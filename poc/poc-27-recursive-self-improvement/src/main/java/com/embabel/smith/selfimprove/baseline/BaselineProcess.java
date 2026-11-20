/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.baseline;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.embabel.agent.api.common.Ai;
import com.embabel.smith.builder.core.AiSpecPromptCaller;
import com.embabel.smith.builder.core.SpecPromptCaller;
import com.embabel.smith.selfimprove.SelfImproveApp;
import com.embabel.smith.selfimprove.innerloop.ModelCallMeter;
import com.embabel.smith.selfimprove.referee.FrozenTaskSet;
import com.embabel.smith.selfimprove.referee.Referee;

import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Tasks 2.4 and 2.5 as one command: measure the unmodified process against the frozen
 * task set, record its baseline, then score the same unchanged process again and record
 * the repeat-to-repeat noise floor — both before any prompt has been tuned.
 *
 * <p>
 * It writes the two artifacts the referee refuses to start without, at the paths the
 * referee looks for them ({@link Referee#BASELINE}, {@link Referee#NOISE_FLOOR}), which
 * is what settles the caveat task 3.4 left open about who names those files.
 *
 * <p>
 * <b>The budget is declared before the first call.</b> The cost is the frozen task count
 * times {@link NoiseFloor#MIN_REPEATS}, printed before anything is asked, per task 7.6.
 * The headroom decision is taken after the first pass only: if there is none, the run
 * records that the outer loop cannot run and stops without spending the remaining repeats
 * — which also leaves the noise floor unrecorded, so the referee's own presence check is
 * what keeps a run with no headroom from starting later.
 *
 * <p>
 * Started by hand from this module's classpath, the way {@code RefereeProcess} is; the
 * POC's launcher does not start it. Unlike the launcher, this one needs an endpoint and
 * says so rather than printing a partial measurement.
 *
 * <pre>
 *   mvn -Ppoc -pl poc/poc-27-recursive-self-improvement spring-boot:run \
 *     -Dspring-boot.run.main-class=com.embabel.smith.selfimprove.baseline.BaselineProcess
 * </pre>
 *
 * @author Ivan Rodriguez
 */
public final class BaselineProcess {

	private BaselineProcess() {
	}

	/**
	 * Measures the unmodified process and writes both evidence files.
	 * @param tasks the frozen set to ask
	 * @param answers the answer seam; the live run passes the product's Embabel-backed
	 * caller, an offline test passes a canned one
	 * @param resourcesDir the module's resources directory, under which the evidence is
	 * written
	 * @return {@code 0} when both artifacts were recorded, {@code 1} when the baseline
	 * left no headroom and the outer loop may not run
	 */
	static int recordEvidence(FrozenTaskSet tasks, SpecPromptCaller answers, Path resourcesDir) {
		ModelCallMeter meter = new ModelCallMeter();
		BaselineRun run = new BaselineRun(tasks, answers, meter);

		ScoredAnswers first = run.measure();
		Baseline baseline = Baseline.of(tasks, first, meter.count());
		Path baselineFile = resourcesDir.resolve(Referee.BASELINE);
		EvidenceWriter.write(baseline, baselineFile);
		System.out.println("primary        : " + baseline.baselineScore() + " — the product's substring score, the"
				+ " number the gate and task 7.4 read; failing " + baseline.baselineFailingTaskIds());
		System.out.println("human label    : " + baseline.humanLabelScore() + " — each task file's own verdict"
				+ ", the known gap; failing " + baseline.humanLabelFailingTaskIds());
		if (baseline.armsDiverge()) {
			System.out.println("DIVERGENCE     : " + baseline.divergingTaskIds() + " pass the primary baseline and"
					+ " fail the human label — the primary baseline is weaker there, and it is what the loop is"
					+ " gated by");
		}
		System.out.println("narrowed       : " + baseline.narrowedTaskIds().size() + " of " + tasks.ids().size()
				+ " task(s) carry conditions the primary scorer cannot express, so both numbers are kept");
		System.out.println("model calls    : " + baseline.modelCalls() + " read off the meter");
		System.out.println("recorded       : " + baselineFile);
		System.out.println("verdict        : " + baseline.verdict());

		if (!baseline.headroom()) {
			System.out.println();
			System.out.println("The remaining repeats were not spent, so " + Referee.NOISE_FLOOR + " was not written.");
			return 1;
		}

		List<ScoredAnswers> passes = new ArrayList<>();
		passes.add(first);
		passes.addAll(run.repeats(NoiseFloor.MIN_REPEATS - 1));
		NoiseFloor floor = NoiseFloor.of(tasks, passes);
		Path noiseFloorFile = resourcesDir.resolve(Referee.NOISE_FLOOR);
		EvidenceWriter.write(floor, noiseFloorFile);
		System.out.println();
		System.out.println("repeats        : " + floor.repeats() + " passes over the same unchanged process");
		System.out.println("primary floor  : " + floor.gateFloor() + " across " + floor.gateScores()
				+ " — the only threshold task 7.4 may use");
		System.out.println("label floor    : " + floor.humanLabelFloor() + " across " + floor.humanLabelScores()
				+ " — recorded beside it, never a threshold");
		System.out.println("moved primary  : " + floor.gateFlippingTaskIds() + (floor.answersMoved() ? ""
				: " — nothing moved under the primary scorer, so the honest floor is zero"));
		System.out.println("moved label    : " + floor.humanLabelFlippingTaskIds()
				+ " — tasks the primary floor cannot see moving");
		System.out.println("model calls    : " + run.modelCalls() + " read off the meter");
		System.out.println("recorded       : " + noiseFloorFile);
		System.out.println();
		System.out.println("Both artifacts the referee requires are recorded; task 7.4 gates every later delta"
				+ " against the floor above.");
		return 0;
	}

	/**
	 * Runs the measurement against a live endpoint.
	 * @param args none are read; the resources directory comes from
	 * {@code -Dsmith.self-improve.dir}, defaulting to {@code src/main/resources}, the way
	 * the POC's launcher resolves it
	 */
	public static void main(String[] args) {
		Path resources = Path.of(System.getProperty("smith.self-improve.dir", "src/main/resources"));
		System.out.println("baseline and noise floor — task 2.4 and task 2.5, measured before any prompt is tuned");
		System.out.println("usage: started by hand from this module's classpath; no arguments.");
		System.out.println("       resources dir: -Dsmith.self-improve.dir=<dir>, default src/main/resources");
		System.out.println("writes           : " + Referee.BASELINE + " and " + Referee.NOISE_FLOOR + " under it");
		System.out.println("This process holds a model client and no scorer of its own beyond the frozen check; the"
				+ " referee that grades candidates is a separate process this one never starts.");

		FrozenTaskSet tasks = FrozenTaskSet.load(resources.resolve(Referee.TASKS));
		tasks.assertNoRestatedPhrases();
		int budget = tasks.ids().size() * NoiseFloor.MIN_REPEATS;
		System.out.println();
		System.out.println("frozen tasks   : " + tasks.ids());
		System.out.println("budget         : at most " + budget + " model calls — " + tasks.ids().size() + " prompts x "
				+ NoiseFloor.MIN_REPEATS + " repeats, declared before the first call (task 7.6)");
		System.out.println("                 the first " + tasks.ids().size() + " of them are spent before the headroom"
				+ " decision, and a run with no headroom spends nothing further");

		if (!endpointConfigured()) {
			System.out.println();
			System.out.println("REFUSED — no model endpoint configured. This measurement is of a live model answering"
					+ " the frozen prompts with nothing in front of them, so it cannot be taken offline. Set"
					+ " OPENAI_CUSTOM_BASE_URL, OPENAI_CUSTOM_API_KEY and EMBABEL_MODELS_DEFAULT_LLM.");
			System.exit(1);
			return;
		}

		ConfigurableApplicationContext context = new SpringApplicationBuilder(SelfImproveApp.class)
			.web(WebApplicationType.NONE)
			.bannerMode(Banner.Mode.OFF)
			.run();
		int exitCode;
		try {
			exitCode = recordEvidence(tasks, new AiSpecPromptCaller(context.getBean(Ai.class)), resources);
		}
		finally {
			// Closed before exiting rather than after: System.exit does not return, so a
			// finally that relied on running past it would leave the context open.
			context.close();
		}
		System.exit(exitCode);
	}

	private static boolean endpointConfigured() {
		return isSet("OPENAI_CUSTOM_BASE_URL") && isSet("OPENAI_CUSTOM_API_KEY") && isSet("EMBABEL_MODELS_DEFAULT_LLM");
	}

	private static boolean isSet(String name) {
		String value = System.getenv(name);
		return value != null && !value.isBlank();
	}

}
