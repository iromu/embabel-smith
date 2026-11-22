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
import java.util.Optional;

import com.embabel.smith.selfimprove.control.HeldForApproval;
import com.embabel.smith.selfimprove.innerloop.ModelCallMeter;
import com.embabel.smith.selfimprove.innerloop.PolicyGenerator.MutationOperator;
import com.embabel.smith.selfimprove.referee.Referee;
import com.embabel.smith.selfimprove.referee.RefereeReport;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The outer loop: generations of an improver that improves itself, each generation scored
 * by the referee and retained in the archive, until the declared budget is spent.
 *
 * <p>
 * One pass of the loop is: take the improver, let it propose its variants, score each one
 * through {@link Referee} — the product's own gate over the frozen task set — retain
 * every result including the ones that lost, then build the next generation's improver
 * out of the variant the archive selects. The next improver is not chosen by a human and
 * not re-seeded from the original prompt, which is the whole difference between this and
 * another round of the inner loop.
 *
 * <p>
 * <b>This is a POC-local loop.</b> It is not the product's improvement loop and it
 * publishes nothing: a candidate that clears the gate and clears the noise floor goes to
 * {@link HeldForApproval}, which is where POC 27's boundary with the product sits. A
 * generation that could push its own output to a shared remote would not need to improve
 * anything in order to do damage.
 *
 * <p>
 * Improvement is judged twice, deliberately. The run asks the product's gate to beat a
 * baseline <em>raised by the noise floor</em>, so the gate's own strict "beat it"
 * comparison refuses a move inside the floor; and the run pairs the score against the
 * recorded parent and refuses the same move on its own terms. The two agree by
 * construction, and a variant that fails either one is still retained.
 *
 * <p>
 * <b>Cost is read, never assumed.</b> Every model call goes through
 * {@link MeteredInvokerFactory}, so the per-variant cost in each record is a delta off a
 * counter, and the budget is checked against that counter <em>before</em> a candidate is
 * scored — by the time a score comes back the calls are already spent. What a candidate
 * is charged against the ceiling is arithmetic rather than a number carried here: one
 * deploy plus one invocation per frozen task, from {@link Budget#costPerCandidate(int)}.
 * The deploy makes no metered call, so a run can spend fewer calls than it declared, and
 * the gap is the deploys it reserved rather than calls it failed to account for.
 *
 * @author Ivan Rodriguez
 */
public final class OuterLoop {

	/** The file the run writes its per-variant records to, one JSON object per line. */
	public static final String RECORD_FILE = "outer-generations.jsonl";

	/** The run attempted every generation its budget allowed. */
	public static final String GENERATION_BUDGET_SPENT = "generation budget spent";

	/** The run stopped with generations still owed because the calls ran out first. */
	public static final String MODEL_CALL_BUDGET_SPENT = "model-call budget spent";

	private static final ObjectMapper JSON = new ObjectMapper();

	private final Referee referee;

	private final ModelCallMeter meter;

	private final HeldForApproval held;

	/**
	 * Builds the loop over a referee that is already standing.
	 * @param referee the referee every candidate is scored by; hand it a
	 * {@link MeteredInvokerFactory} or the cost columns will be zeros
	 * @param meter the counter those calls were charged to
	 * @param held where a candidate that passed goes, which is a queue and not a
	 * publisher
	 */
	public OuterLoop(Referee referee, ModelCallMeter meter, HeldForApproval held) {
		this.referee = referee;
		this.meter = meter;
		this.held = held;
	}

	/**
	 * Runs the loop until its budget is spent.
	 * @param request the first improver, the operators it may use, the reference values
	 * and the declared budget
	 * @return the run report, including whatever the budget cut off.
	 */
	public OuterRun run(Request request) {
		// One deploy plus one invocation per frozen task, from the frozen count: the same
		// arithmetic the launcher declares the ceiling with (task 7.1).
		int costPerCandidate = Budget.costPerCandidate(this.referee.frozen().ids().size());
		Archive archive = new Archive();
		Map<String, PolicyImprover.Proposal> proposals = new LinkedHashMap<>();
		List<VariantRecord> records = new ArrayList<>();
		List<GenerationReport> generations = new ArrayList<>();
		List<String> notMeasured = new ArrayList<>();
		List<String> heldIds = new ArrayList<>();
		String stopReason = GENERATION_BUDGET_SPENT;
		PolicyImprover improver = request.first();
		double referenceBest = request.baseline().passRate();
		String referenceBestId = null;
		for (int generation = 1; generation <= request.budget().maxGenerations(); generation++) {
			if (generation > 1) {
				PolicyImprover.Proposal chosen = selectFrom(archive, proposals);
				if (chosen == null) {
					notMeasured.add("generation " + generation
							+ " onward was not run: nothing was retained to derive an improver from");
					stopReason = MODEL_CALL_BUDGET_SPENT;
					break;
				}
				improver = improver.derive(chosen);
				referenceBest = archive.frozenScoreOf(chosen.variantId());
				referenceBestId = chosen.variantId();
			}
			PolicyImprover.Batch batch = improver.propose(request.operators());
			if (!batch.notOffered().isEmpty()) {
				notMeasured.add("generation " + generation + " could not apply " + batch.notOffered().size() + " of "
						+ request.operators().size() + " operators: " + batch.notOffered());
			}
			List<VariantRecord> scored = new ArrayList<>();
			int callsBefore = this.meter.count();
			boolean drained = false;
			for (PolicyImprover.Proposal proposal : batch.proposals()) {
				if (!request.budget().admits(this.meter.count(), costPerCandidate)) {
					drained = true;
					notMeasured.add("variant " + proposal.variantId() + " (generation " + generation
							+ ") was not scored: " + this.meter.count() + " of " + request.budget().maxModelCalls()
							+ " declared model calls were already spent");
					continue;
				}
				Scored result = score(proposal, generation, improver.id(), request, archive, notMeasured);
				archive.retain(result.record());
				proposals.put(result.record().variantId(), proposal);
				records.add(result.record());
				scored.add(result.record());
				if (result.record().gated() && result.record().improvement().accepted()) {
					this.held.hold(proposal.candidate(), result.report(), result.gateBaseline());
					heldIds.add(result.record().variantId());
				}
			}
			if (scored.isEmpty()) {
				stopReason = MODEL_CALL_BUDGET_SPENT;
				break;
			}
			VariantRecord best = bestOf(scored);
			generations.add(
					new GenerationReport(generation, improver.id(), improver.parentVariantId(), scored.size(),
							best.variantId(), best.frozenScore(), Improvement.paired(referenceBest, best.frozenScore(),
									request.floor(), best.variantId(), referenceBestId),
							this.meter.count() - callsBefore));
			referenceBest = best.frozenScore();
			referenceBestId = best.variantId();
			if (drained) {
				stopReason = MODEL_CALL_BUDGET_SPENT;
				notMeasured.add("generation " + (generation + 1) + " onward was not run: the declared budget of "
						+ request.budget().describe() + " ran out first");
				break;
			}
		}
		Path recordFile = writeRecords(records, request.workDir());
		return new OuterRun(List.copyOf(generations), archive, SelectionCurves.over(records),
				RunOutcome.of(generations), this.meter.count(), request.budget(), stopReason, List.copyOf(notMeasured),
				request.baseline(), request.floor(), List.copyOf(heldIds), recordFile);
	}

	private Scored score(PolicyImprover.Proposal proposal, int generation, String improverId, Request request,
			Archive archive, List<String> notMeasured) {
		// What the meter should show for this candidate: one call per frozen task. The
		// deploy the ceiling reserves for is not metered, so it is not looked for here.
		int invocations = this.referee.frozen().ids().size();
		String parentId = proposal.parentVariantId();
		double reference = (parentId != null) ? archive.frozenScoreOf(parentId) : request.baseline().passRate();
		double gateBaseline = reference + request.floor().delta();
		int callsBefore = this.meter.count();
		long started = System.nanoTime();
		RefereeReport report = this.referee.score(proposal.candidate(), gateBaseline);
		int calls = this.meter.count() - callsBefore;
		if (calls != invocations) {
			notMeasured.add("variant " + proposal.variantId() + " cost " + calls + " model calls, not the "
					+ invocations + " the frozen set implies");
		}
		long millis = (System.nanoTime() - started) / 1_000_000L;
		Improvement improvement = Improvement.paired(reference, report.frozenScore(), request.floor(),
				proposal.variantId(), parentId);
		return new Scored(new VariantRecord(generation, proposal.variantId(), parentId, improverId, proposal.operator(),
				report.gated(), report.gateStage(), report.gateScore(), report.frozenScore(), improvement, calls,
				millis), report, gateBaseline);
	}

	private static PolicyImprover.Proposal selectFrom(Archive archive, Map<String, PolicyImprover.Proposal> proposals) {
		Optional<VariantRecord> best = archive.best();
		return best.flatMap((record) -> Optional.ofNullable(proposals.get(record.variantId()))).orElse(null);
	}

	private static VariantRecord bestOf(List<VariantRecord> scored) {
		return scored.stream()
			.max((left, right) -> Double.compare(left.frozenScore(), right.frozenScore()))
			.orElseThrow();
	}

	private static Path writeRecords(List<VariantRecord> records, Path workDir) {
		StringBuilder out = new StringBuilder();
		for (VariantRecord record : records) {
			try {
				out.append(JSON.writeValueAsString(record.asMap())).append('\n');
			}
			catch (IOException ex) {
				throw new UncheckedIOException("cannot serialise the record for " + record.variantId(), ex);
			}
		}
		Path file = workDir.resolve(RECORD_FILE);
		try {
			Files.createDirectories(workDir);
			Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot write the outer-loop records to " + file, ex);
		}
		return file;
	}

	/**
	 * One candidate's record next to the referee report and the baseline it was gated
	 * against, which the hold needs and the record does not carry.
	 *
	 * @param record what goes in the archive
	 * @param report the referee's own report, handed to human control unchanged
	 * @param gateBaseline the raised baseline the gate was asked to beat
	 * @author Ivan Rodriguez
	 */
	private record Scored(VariantRecord record, RefereeReport report, double gateBaseline) {
	}

	/**
	 * What a run needs before it starts: the improver generation 1 runs, the mutations it
	 * is allowed to try, the two reference values improvement is judged against, and the
	 * budget it may not exceed.
	 *
	 * <p>
	 * The budget is a parameter rather than something the loop discovers, because a limit
	 * set after the first call is not a limit. The reference values carry their own
	 * provenance for the same reason: the report has to be able to say whether the
	 * baseline was read from {@code evidence/baseline.json} or handed in.
	 *
	 * <p>
	 * An empty operator list is refused at construction, because an improver with nothing
	 * to change cannot produce a second generation, and a run with no second generation
	 * has nothing to compare the first against.
	 *
	 * @param first the improver generation 1 runs, normally
	 * {@link PolicyImprover#surviving}
	 * @param operators the mutations it may apply, one per variant
	 * @param baseline the pass-rate generation 1 is measured against
	 * @param floor the noise floor a paired delta has to clear
	 * @param budget the ceiling declared before the first call
	 * @param workDir where to write the records
	 * @author Ivan Rodriguez
	 */
	public record Request(PolicyImprover first, List<MutationOperator> operators, Baseline baseline, NoiseFloor floor,
			Budget budget, Path workDir) {

		public Request {
			if (operators.isEmpty()) {
				throw new IllegalArgumentException("an improver with no operator to apply cannot produce a second"
						+ " generation to compare, so there is nothing for this run to measure");
			}
		}

	}

}
