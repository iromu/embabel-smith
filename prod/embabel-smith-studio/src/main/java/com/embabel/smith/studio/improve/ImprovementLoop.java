/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.studio.improve;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.embabel.smith.builder.eval.EvalTask;
import com.embabel.smith.builder.eval.InvokerFactory;
import com.embabel.smith.builder.eval.PromptRewriter;
import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleStamper;
import com.embabel.smith.builder.lifecycle.LifecycleState;
import com.embabel.smith.builder.lifecycle.SpecContentHash;
import com.embabel.smith.builder.publish.PublishResult;
import com.embabel.smith.builder.publish.Publisher;
import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.io.Yaml;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.runtime.trace.TraceRecord;
import com.embabel.smith.runtime.trace.TraceStore;
import com.embabel.smith.specsource.ResolvedSpec;
import com.embabel.smith.specsource.ResolvedSpecSource;
import com.embabel.smith.specsource.SpecSource;

/**
 * The self-improvement loop (change 05): one pass over the spec source that watches the
 * traces (change 03), detects specs that are failing or drifting, refines them (change
 * 04's builder core), gates the candidates (validate → scan → re-eval → beat the
 * baseline), and publishes the approved ones back to the source with a lifecycle
 * transition.
 *
 * <p>
 * Per-spec, the loop:
 * <ol>
 * <li><b>Detect</b> — failed runs (minus trust-tier blocks), content drift, and a live
 * re-eval below the baseline (regression);</li>
 * <li><b>Refine</b> — rewrite the prompts of the failing steps, using the failure traces
 * as evidence;</li>
 * <li><b>Gate</b> — D5 validation, security scan, re-eval, strict baseline
 * comparison;</li>
 * <li><b>Approve</b> — hold the gated candidate in the {@link ApprovalQueue} (default) or
 * auto-publish it;</li>
 * <li><b>Publish</b> — write the candidate to the source and transition the
 * lifecycle;</li>
 * <li><b>Watermark</b> — record the last processed trace so a re-run over unchanged
 * traces publishes nothing.</li>
 * </ol>
 *
 * <p>
 * The loop logic is plain Java: no web, no scheduling (change 06 wires it to a scheduler
 * and REST endpoints). Model calls (refine, re-eval) are endpoint-gated.
 *
 * @author Ivan Rodriguez
 */
public final class ImprovementLoop {

	private final SpecSource source;

	private final TraceWindowReader traceReader;

	private final Detector detector;

	private final Refiner refiner;

	private final GateChain gateChain;

	private final Publisher publisher;

	private final boolean autoPublish;

	private final ApprovalQueue queue = new ApprovalQueue();

	private final InFlightSet inFlight = new InFlightSet();

	private final Map<String, Instant> lastProcessed = new ConcurrentHashMap<>();

	private final Map<String, GateResult> lastRejections = new ConcurrentHashMap<>();

	private final AtomicLong idCounter = new AtomicLong();

	private final AgentSpecReader packReader = new AgentSpecReader();

	private final LifecycleStamper stamper = new LifecycleStamper();

	/**
	 * Convenience constructor: builds the default collaborators from a trace store, a
	 * prompt rewriter, an invoker factory, and the eval tasks.
	 * @param source the spec source to improve
	 * @param traceStore the trace store to watch
	 * @param rewriter the prompt rewriter used to refine failing steps
	 * @param invokerFactory the invoker factory used to deploy and run packs
	 * @param evalTasks the eval tasks used for re-evaluation
	 * @param publisher the publisher that writes approved candidates back to the source
	 * @param autoPublish whether gated candidates are published immediately
	 */
	public ImprovementLoop(SpecSource source, TraceStore traceStore, PromptRewriter rewriter,
			InvokerFactory invokerFactory, List<EvalTask> evalTasks, Publisher publisher, boolean autoPublish) {
		this(source, new TraceWindowReader(traceStore), new Detector(), new Refiner(rewriter),
				new GateChain(invokerFactory, evalTasks), publisher, autoPublish);
	}

	public ImprovementLoop(SpecSource source, TraceWindowReader traceReader, Detector detector, Refiner refiner,
			GateChain gateChain, Publisher publisher, boolean autoPublish) {
		this.source = source;
		this.traceReader = traceReader;
		this.detector = detector;
		this.refiner = refiner;
		this.gateChain = gateChain;
		this.publisher = publisher;
		this.autoPublish = autoPublish;
	}

	/**
	 * The approval queue holding gated candidates awaiting a decision.
	 * @return the approval queue.
	 */
	public ApprovalQueue queue() {
		return this.queue;
	}

	/**
	 * The set of specs with an improvement currently in flight.
	 * @return the in-flight set.
	 */
	public InFlightSet inFlight() {
		return this.inFlight;
	}

	/**
	 * Runs one pass over the spec source. Returns the gated candidates produced this pass
	 * (held in the queue when {@code autoPublish} is off; empty when auto-published).
	 * @return the gated candidates produced this pass.
	 */
	public List<PendingCandidate> runOnce() {
		List<PendingCandidate> produced = new ArrayList<>();
		ResolvedSpecSource resolved = this.source.resolve();
		for (ResolvedSpec pack : resolved.packs()) {
			String spec = pack.name();
			List<TraceRecord> window = this.traceReader.window(spec);
			if (window.isEmpty()) {
				continue;
			}
			TraceRecord latest = window.get(window.size() - 1);
			if (isCaughtUp(spec, latest.timestamp())) {
				continue; // idempotent: no traces newer than the watermark
			}
			AgentSpec current = readPack(pack.root());
			if (current == null) {
				continue;
			}
			LifecycleMetadata sidecar = readSidecar(pack.root(), current);
			if (sidecar.state() == LifecycleState.RETIRED) {
				// Retired packs are excluded from automatic refinement (design state
				// machine); a
				// human revives them via an explicit rebuild (change 06).
				advanceWatermark(spec, latest);
				continue;
			}
			// At most one active improvement per spec — checked before any (re-)eval
			// spend, so an
			// in-flight spec is skipped without spending eval budget.
			if (!this.inFlight.begin(spec)) {
				advanceWatermark(spec, latest);
				continue;
			}
			try {
				Detector.Result detection = detect(spec, current, sidecar, window);
				if (detection.regressed()) {
					sidecar = LifecycleTransitions.regressed(sidecar);
					writeSidecar(pack.root(), sidecar);
				}
				// A degraded pack that regressed to retired in this pass is no longer
				// refined.
				if (!detection.queued() || sidecar.state() == LifecycleState.RETIRED) {
					advanceWatermark(spec, latest);
					continue;
				}
				Refiner.Refinement refinement = this.refiner.refine(current, detection.failureTraces());
				if (refinement.changed()) {
					// Thread the triggering failures into the gate so, when the strict
					// re-eval
					// cannot beat the (ceiling) baseline, the gate proves the actual
					// improvement by
					// re-running the candidate on those inputs.
					GateResult gate = this.gateChain.gate(refinement.candidate(), sidecar.baselinePassRate(),
							failedInputs(detection.failureTraces()));
					if (gate.gated()) {
						PendingCandidate candidate = toCandidate(spec, refinement, gate, sidecar, detection);
						boolean held = this.queue.enqueue(candidate, this.autoPublish, this::publishCandidate);
						if (held) {
							produced.add(candidate);
						}
					}
					else {
						this.lastRejections.put(spec, gate); // record the rejection with
																// its score
					}
				}
				advanceWatermark(spec, latest);
			}
			finally {
				this.inFlight.end(spec);
			}
		}
		return produced;
	}

	/**
	 * The most recent gate rejection for the spec, or null if none.
	 * @param spec the spec to look up
	 * @return the most recent gate rejection for the spec, or null if none.
	 */
	public GateResult lastRejection(String spec) {
		return this.lastRejections.get(spec);
	}

	/**
	 * The per-spec detection: the cheap triggers (failed runs, content drift) plus the
	 * live re-eval of the pack against its baseline. The re-eval is spent when the pack
	 * can regress ({@code active}/{@code degraded}) or when its content has drifted from
	 * the sidecar — in both cases the pack must be re-scored before anything is decided.
	 * @param spec the spec being detected
	 * @param current the pack's current content
	 * @param sidecar the pack's lifecycle sidecar
	 * @param window the spec's trace window
	 * @return the detection outcome for the spec.
	 */
	private Detector.Result detect(String spec, AgentSpec current, LifecycleMetadata sidecar,
			List<TraceRecord> window) {
		boolean regressable = sidecar.state() == LifecycleState.ACTIVE || sidecar.state() == LifecycleState.DEGRADED;
		boolean needsReeval = regressable || Detector.hasDrift(current, sidecar);
		Detector.ReevalHook hook = needsReeval ? (s) -> this.gateChain.reeval(current) : null;
		return this.detector.detect(spec, current, sidecar, window, hook);
	}

	private PendingCandidate toCandidate(String spec, Refiner.Refinement refinement, GateResult gate,
			LifecycleMetadata sidecar, Detector.Result detection) {
		LifecycleState state = LifecycleTransitions.onPublished(sidecar.state());
		LifecycleMetadata proposed = LifecycleTransitions.sidecarFor(refinement.candidate(), gate.score(), state);
		String diff = "rewrote prompts of: " + String.join(", ", refinement.changedSteps());
		return new PendingCandidate(nextId(spec), spec, refinement.candidate(), proposed, gate.score(),
				sidecar.baselinePassRate(), gate.scan(), detection.failureTraces(), diff);
	}

	/**
	 * The triggering failures as gate inputs: each with the failed input and the
	 * (possibly truncated) output excerpt the runtime recorded, so the gate can require
	 * an output that is both non-blank and different from the recorded failure. Distinct,
	 * non-blank inputs only.
	 * @param failureTraces the triggering failure traces
	 * @return the triggering failures as gate inputs.
	 */
	private static List<GateChain.FailedInput> failedInputs(List<TraceRecord> failureTraces) {
		return failureTraces.stream()
			.filter((t) -> t.input() != null && !t.input().isBlank())
			.map((t) -> new GateChain.FailedInput(t.input(), t.outputExcerpt()))
			.distinct()
			.toList();
	}

	private PublishResult publishCandidate(PendingCandidate candidate) {
		try {
			// push=true: the loop writes the improvement back into the spec source, so a
			// git
			// source must be pushed or the commit dies with the publisher's throwaway
			// clone. The
			// folder publisher ignores the flag (a local write is already persistent).
			return this.publisher.publish(candidate.candidate(), candidate.proposedLifecycle(), true, true);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot publish candidate for " + candidate.spec(), ex);
		}
	}

	private String nextId(String spec) {
		return spec + "-" + this.idCounter.incrementAndGet();
	}

	/**
	 * The most recent trace for the spec, or {@code null} when the pack cannot be read.
	 * @param root the pack's root directory
	 * @return the pack's current content, or {@code null} when it cannot be read.
	 */
	private AgentSpec readPack(Path root) {
		try {
			return this.packReader.read(root);
		}
		catch (IOException ex) {
			return null;
		}
	}

	/**
	 * The pack's lifecycle sidecar, or a fresh {@code incubating} sidecar (at the current
	 * content hash, no baseline) when the pack has none.
	 * @param root the pack's root directory
	 * @param current the pack's current content
	 * @return the pack's lifecycle sidecar.
	 */
	private LifecycleMetadata readSidecar(Path root, AgentSpec current) {
		Path sidecar = root.resolve("lifecycle.yml");
		if (Files.isRegularFile(sidecar)) {
			try {
				return Yaml.mapper().readValue(Files.readString(sidecar), LifecycleMetadata.class);
			}
			catch (IOException ex) {
				// fall through to the default sidecar
			}
		}
		return new LifecycleMetadata(SpecContentHash.of(current), 0.0, LifecycleState.INCUBATING,
				Instant.now().toString());
	}

	private void writeSidecar(Path root, LifecycleMetadata metadata) {
		try {
			this.stamper.write(root, metadata);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot write lifecycle sidecar for " + root, ex);
		}
	}

	private boolean isCaughtUp(String spec, Instant latest) {
		Instant last = this.lastProcessed.get(spec);
		return last != null && !last.isBefore(latest);
	}

	private void advanceWatermark(String spec, TraceRecord latest) {
		this.lastProcessed.put(spec, latest.timestamp());
	}

}
