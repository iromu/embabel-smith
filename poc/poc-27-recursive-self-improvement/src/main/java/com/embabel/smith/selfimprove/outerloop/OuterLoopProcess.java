/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.outerloop;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.builder.eval.AgentSpecInvokerFactory;
import com.embabel.smith.builder.eval.InvokerFactory;
import com.embabel.smith.selfimprove.SelfImproveApp;
import com.embabel.smith.selfimprove.control.HeldForApproval;
import com.embabel.smith.selfimprove.innerloop.ModelCallMeter;
import com.embabel.smith.selfimprove.innerloop.PolicyPrompt;
import com.embabel.smith.selfimprove.referee.FrozenTaskSet;
import com.embabel.smith.selfimprove.referee.Referee;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Task 10.2's launcher: one live pass of the outer loop, end to end, producing the three
 * things that task names — the archive, the per-generation report and the held-candidate
 * count — in the same pass.
 *
 * <p>
 * <b>It reads the recorded evidence rather than checking that the files exist.</b>
 * {@link Referee#assertOuterLoopMayStart()} is a presence check —
 * {@code Files.isRegularFile} over the two artifact paths — so a {@code baseline.json}
 * recording {@code headroom: false} beside a {@code noise-floor.json} would let a paid
 * run start, which is the exact case task 2.4's stop-and-record path exists to prevent.
 * This launcher parses both artifacts and refuses on their contents before Spring is
 * started and before a call is made. The {@code gateFloor} it reads is passed through to
 * {@link NoiseFloor#measured(double, Path)} unchanged: the measured floor is {@code 0.0},
 * and a run that quietly substituted a higher threshold for safety would be moving the
 * one artifact {@code evidence/MUTABILITY.md} fixes as the noise floor.
 *
 * <p>
 * <b>The budget is declared before the first call</b> (task 7.6), and the number is
 * arithmetic rather than a guess: the unchanged policy plus one variant per scored
 * mutation is what {@link PolicyImprover#propose} offers, each variant costs one model
 * call per frozen task because the referee records a candidate's answers per prompt, and
 * two generations is the minimum that exercises the recursive claim at all
 * ({@link RunOutcome#HILL_CLIMBING}). On the checked-in frozen set and operator list that
 * is {@code (1 + 4) x 6 x 2 = 60} calls. Every call is charged through
 * {@link MeteredInvokerFactory}, so the run's cost is read off a counter afterwards.
 *
 * <p>
 * <b>A pass that improves nothing exits zero and says so.</b> No candidate held is the
 * finding, not a failed run; the exit code is reserved for the refusals that stopped a
 * pass from being taken at all. The held count is printed as a reported number, which is
 * what {@code evidence/MUTABILITY.md} makes it, and nothing here publishes a held
 * candidate.
 *
 * <p>
 * Started by hand from this module's classpath, the way {@code RefereeProcess} and
 * {@code BaselineProcess} are. This module has several {@code main} classes, so the
 * main-class flag is mandatory:
 *
 * <pre>
 *   mvn -Ppoc -pl poc/poc-27-recursive-self-improvement spring-boot:run \
 *     -Dspring-boot.run.main-class=com.embabel.smith.selfimprove.outerloop.OuterLoopProcess
 * </pre>
 *
 * @author Ivan Rodriguez
 */
public final class OuterLoopProcess {

	/**
	 * The generations one pass runs. Two, because one completed generation is one round
	 * of hill-climbing and the recursive claim is untested by it; more than two costs the
	 * same per generation and buys nothing the claim needs.
	 */
	private static final int GENERATIONS = 2;

	/** The file the per-generation reports are written to, one JSON object per line. */
	public static final String GENERATION_REPORT_FILE = "generation-reports.jsonl";

	private static final ObjectMapper JSON = new ObjectMapper();

	private OuterLoopProcess() {
	}

	/**
	 * Runs one pass against a live endpoint and prints what it produced.
	 * @param resources the module's resources directory, holding {@code benchmark/tasks}
	 * and the two recorded artifacts
	 * @param invokers how a candidate gets deployed and run; the live pass passes the
	 * product's {@code AgentSpecInvokerFactory}, an offline test passes a canned table
	 * @param workDir where the archive, the variant records and the generation reports go
	 * @return the run, whose held identifiers are the held-candidate count.
	 */
	static OuterRun runPass(Path resources, InvokerFactory invokers, Path workDir) {
		ModelCallMeter meter = new ModelCallMeter();
		HeldForApproval held = new HeldForApproval();
		Referee referee = Referee.start(resources, null, new MeteredInvokerFactory(invokers, meter));
		referee.assertOuterLoopMayStart();
		FrozenTaskSet frozen = referee.frozen();
		Baseline baseline = recordedBaseline(resources);
		NoiseFloor floor = recordedNoiseFloor(resources);
		Budget budget = budget(frozen);
		PolicyImprover improver = PolicyImprover.surviving(PolicyPrompt.frozenSeed());

		System.out.println();
		System.out.println("frozen tasks   : " + frozen.ids());
		System.out.println("gen-1 improver : " + improver.id() + " — the frozen seed prompt the offline inner loop"
				+ " converged on (task 7.1), offered " + PolicyImprover.SCORED_MUTATIONS.size() + " mutations");
		System.out.println("baseline       : " + baseline.describe());
		System.out.println("noise floor    : " + floor.describe());
		System.out.println("budget         : " + budget.describe() + " — (" + variantsPerGeneration() + " variants x "
				+ frozen.ids().size() + " frozen tasks x " + GENERATIONS + " generations), declared before the first"
				+ " call (task 7.6)");

		OuterRun run = new OuterLoop(referee, meter, held)
			.run(new OuterLoop.Request(improver, PolicyImprover.SCORED_MUTATIONS, baseline, floor, budget, workDir));
		Path archiveFile = run.archive().write(workDir.resolve(Archive.RECORD_FILE));
		Path reportFile = writeGenerationReports(run.generations(), workDir);

		System.out.println();
		System.out.print(run.describe());
		System.out.println();
		System.out.println("generation reports : " + reportFile + " — " + run.generations().size() + " generation(s),"
				+ " one paired delta each");
		System.out.println("archive            : " + archiveFile + " — " + run.archive().size() + " variant(s)"
				+ " retained, the ones that lost included");
		System.out.println(
				"held for approval  : " + held.held() + " of " + run.archive().size() + " variants — " + run.heldIds());
		System.out.println("                   : a reported count, not a target; nothing was published, and each"
				+ " held candidate is a human decision still waiting to be made");
		if (held.held() == 0) {
			System.out.println();
			System.out.println("NOTHING BEAT THE BASELINE on this pass: no variant cleared its reference by more than"
					+ " the floor, so no candidate was gated and none was held. That is the result of this run, not a"
					+ " failure to take one.");
		}
		return run;
	}

	/**
	 * The ceiling one pass commits to, computed from the loop's own shape rather than
	 * chosen.
	 * @param frozen the frozen set, whose size is what one candidate costs in calls
	 * @return the declared budget.
	 */
	static Budget budget(FrozenTaskSet frozen) {
		return Budget.declare(variantsPerGeneration() * frozen.ids().size() * GENERATIONS, GENERATIONS);
	}

	/**
	 * Why this pass may not be taken, or nothing when it may.
	 *
	 * <p>
	 * The content half of this is the guard {@link Referee#assertOuterLoopMayStart()}
	 * does not provide: it checks that the two artifacts are regular files and stops
	 * there, so it waves through a recorded baseline that says there is nothing to
	 * improve.
	 * @param resources the directory holding the two recorded artifacts
	 * @return the reason to refuse, or null when the recorded evidence permits a run
	 */
	static String refusalReason(Path resources) {
		List<String> missing = new ArrayList<>();
		for (Path artifact : List.of(Referee.BASELINE, Referee.NOISE_FLOOR)) {
			if (!Files.isRegularFile(resources.resolve(artifact))) {
				missing.add(artifact + " (looked for at " + resources.resolve(artifact) + ")");
			}
		}
		if (!missing.isEmpty()) {
			return "the evidence to compare against is not recorded: missing " + missing
					+ ". An improvement claimed against an unrecorded baseline is the self-grading this POC exists"
					+ " to prevent, so record them first (tasks 2.4 and 2.5).";
		}
		Path baselineFile = resources.resolve(Referee.BASELINE);
		JsonNode baseline = read(baselineFile);
		if (!baseline.path("headroom").asBoolean(false)) {
			return baselineFile + " records headroom: false, and its verdict reads: "
					+ baseline.path("verdict").asText("(no verdict recorded)")
					+ " The referee's own check is for the file's presence, so it would let this through; the gate's"
					+ " comparison is strict, so no candidate can beat a baseline already at the ceiling, and a pass"
					+ " taken here would be a pass against a measurement that says there is nothing to improve.";
		}
		return null;
	}

	/**
	 * The pass-rate the run is gated by, read from the recorded artifact.
	 * @param resources the module's resources directory
	 * @return the baseline, marked measured.
	 */
	static Baseline recordedBaseline(Path resources) {
		Path file = resources.resolve(Referee.BASELINE);
		return Baseline.recorded(requiredNumber(read(file), "baselineScore", file), file);
	}

	/**
	 * The threshold a delta has to clear, read from the recorded artifact. The measured
	 * floor is {@code 0.0} and it is passed through at {@code 0.0}: raising it here would
	 * move an artifact {@code evidence/MUTABILITY.md} fixes.
	 * @param resources the module's resources directory
	 * @return the floor, marked measured.
	 */
	static NoiseFloor recordedNoiseFloor(Path resources) {
		Path file = resources.resolve(Referee.NOISE_FLOOR);
		return NoiseFloor.measured(requiredNumber(read(file), "gateFloor", file), file);
	}

	/**
	 * Runs the pass against a live endpoint.
	 * @param args none are read; the resources directory comes from
	 * {@code -Dsmith.self-improve.dir} and the run's artifacts go under
	 * {@code -Dsmith.self-improve.out}, resolved the way the POC's launcher resolves its
	 * own paths
	 */
	public static void main(String[] args) {
		Path resources = Path.of(System.getProperty("smith.self-improve.dir", "src/main/resources"));
		Path workDir = Path.of(System.getProperty("smith.self-improve.out",
				System.getProperty("java.io.tmpdir") + "/poc27-outer-loop"));
		System.out.println("outer loop — task 10.2: one pass, producing the archive, the per-generation report and"
				+ " the held-candidate count");
		System.out.println("usage: started by hand from this module's classpath with"
				+ " -Dspring-boot.run.main-class naming this class; no arguments.");
		System.out.println("       resources dir: -Dsmith.self-improve.dir=<dir>, default src/main/resources");
		System.out.println("       run artifacts: -Dsmith.self-improve.out=<dir>, default <tmp>/poc27-outer-loop");
		System.out.println("This process holds the model client and the actor side. The gate it scores through is"
				+ " the product's own, constructed here; the candidates it clears are held for a human and published"
				+ " by nothing.");

		String refusal = refusalReason(resources);
		if (refusal != null) {
			System.out.println();
			System.out.println("REFUSED — " + refusal);
			System.exit(1);
			return;
		}
		if (!endpointConfigured()) {
			System.out.println();
			System.out.println("REFUSED — no model endpoint configured. The pass scores a live model answering the"
					+ " frozen prompts through a deployed candidate, so it cannot be taken offline. Set"
					+ " OPENAI_CUSTOM_BASE_URL, OPENAI_CUSTOM_API_KEY and EMBABEL_MODELS_DEFAULT_LLM.");
			System.exit(1);
			return;
		}

		ConfigurableApplicationContext context = new SpringApplicationBuilder(SelfImproveApp.class)
			.web(WebApplicationType.NONE)
			.bannerMode(Banner.Mode.OFF)
			.run();
		try {
			runPass(resources, new AgentSpecInvokerFactory(context.getBean(AgentPlatform.class)), workDir);
		}
		finally {
			// Closed before exiting rather than after: System.exit does not return, so a
			// finally that relied on running past it would leave the context open.
			context.close();
		}
		System.exit(0);
	}

	private static int variantsPerGeneration() {
		return 1 + PolicyImprover.SCORED_MUTATIONS.size();
	}

	private static Path writeGenerationReports(List<GenerationReport> generations, Path workDir) {
		StringBuilder out = new StringBuilder();
		for (GenerationReport generation : generations) {
			Improvement move = generation.improvement();
			Map<String, Object> line = new LinkedHashMap<>();
			line.put("generation", generation.generation());
			line.put("improverId", generation.improverId());
			line.put("derivedFrom", generation.derivedFrom());
			line.put("scoredVariants", generation.scoredVariants());
			line.put("bestVariantId", generation.bestVariantId());
			line.put("bestScore", generation.bestScore());
			line.put("referenceVariantId", move.referenceVariantId());
			line.put("referenceScore", move.referenceScore());
			line.put("delta", move.delta());
			line.put("floor", move.floor());
			line.put("verdict", move.verdict());
			line.put("modelCalls", generation.modelCalls());
			try {
				out.append(JSON.writeValueAsString(line)).append('\n');
			}
			catch (IOException ex) {
				throw new UncheckedIOException("cannot serialise the report for generation " + generation.generation(),
						ex);
			}
		}
		Path file = workDir.resolve(GENERATION_REPORT_FILE);
		try {
			Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot write the per-generation reports to " + file, ex);
		}
		return file;
	}

	private static JsonNode read(Path file) {
		try {
			return JSON.readTree(file.toFile());
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot read " + file, ex);
		}
	}

	private static double requiredNumber(JsonNode record, String field, Path file) {
		JsonNode value = record.path(field);
		if (!value.isNumber()) {
			throw new IllegalStateException(file + " carries no numeric '" + field + "', so the pass has no"
					+ " recorded reference value to gate against and will not invent one");
		}
		return value.asDouble();
	}

	private static boolean endpointConfigured() {
		return isSet("OPENAI_CUSTOM_BASE_URL") && isSet("OPENAI_CUSTOM_API_KEY") && isSet("EMBABEL_MODELS_DEFAULT_LLM");
	}

	private static boolean isSet(String name) {
		String value = System.getenv(name);
		return value != null && !value.isBlank();
	}

}
