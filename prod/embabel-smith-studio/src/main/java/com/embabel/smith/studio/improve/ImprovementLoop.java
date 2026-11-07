package com.embabel.smith.studio.improve;

import com.embabel.smith.builder.eval.EvalTask;
import com.embabel.smith.builder.eval.InvokerFactory;
import com.embabel.smith.builder.eval.PromptRewriter;
import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleState;
import com.embabel.smith.builder.lifecycle.LifecycleStamper;
import com.embabel.smith.builder.lifecycle.SkillContentHash;
import com.embabel.smith.builder.publish.Publisher;
import com.embabel.smith.builder.publish.PublishResult;
import com.embabel.smith.common.io.SkillPackReader;
import com.embabel.smith.common.io.Yaml;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.runtime.trace.JsonlTraceStore;
import com.embabel.smith.runtime.trace.TraceRecord;
import com.embabel.smith.skillsource.ResolvedPack;
import com.embabel.smith.skillsource.ResolvedSkillSource;
import com.embabel.smith.skillsource.SkillSource;

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

/**
 * The self-improvement loop (change 05): one pass over the skill source that watches the traces
 * (change 03), detects skills that are failing or drifting, refines them (change 04's builder
 * core), gates the candidates (validate → scan → re-eval → beat the baseline), and publishes the
 * approved ones back to the source with a lifecycle transition.
 *
 * <p>Per-skill, the loop:
 * <ol>
 *   <li><b>Detect</b> — failed runs (minus trust-tier blocks), content drift, and a live re-eval
 *       below the baseline (regression);</li>
 *   <li><b>Refine</b> — rewrite the prompts of the failing steps, using the failure traces as
 *       evidence;</li>
 *   <li><b>Gate</b> — D5 validation, security scan, re-eval, strict baseline comparison;</li>
 *   <li><b>Approve</b> — hold the gated candidate in the {@link ApprovalQueue} (default) or
 *       auto-publish it;</li>
 *   <li><b>Publish</b> — write the candidate to the source and transition the lifecycle;</li>
 *   <li><b>Watermark</b> — record the last processed trace so a re-run over unchanged traces
 *       publishes nothing.</li>
 * </ol>
 *
 * <p>The loop logic is plain Java: no web, no scheduling (change 06 wires it to a scheduler and
 * REST endpoints). Model calls (refine, re-eval) are endpoint-gated.
 */
public final class ImprovementLoop {

    private final SkillSource source;
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

    private final SkillPackReader packReader = new SkillPackReader();
    private final LifecycleStamper stamper = new LifecycleStamper();

    /**
     * Convenience constructor: builds the default collaborators from a trace store, a prompt
     * rewriter, an invoker factory, and the eval tasks.
     */
    public ImprovementLoop(SkillSource source, JsonlTraceStore traceStore, PromptRewriter rewriter,
                           InvokerFactory invokerFactory, List<EvalTask> evalTasks,
                           Publisher publisher, boolean autoPublish) {
        this(source, new TraceWindowReader(traceStore), new Detector(), new Refiner(rewriter),
                new GateChain(invokerFactory, evalTasks), publisher, autoPublish);
    }

    public ImprovementLoop(SkillSource source, TraceWindowReader traceReader, Detector detector,
                           Refiner refiner, GateChain gateChain, Publisher publisher,
                           boolean autoPublish) {
        this.source = source;
        this.traceReader = traceReader;
        this.detector = detector;
        this.refiner = refiner;
        this.gateChain = gateChain;
        this.publisher = publisher;
        this.autoPublish = autoPublish;
    }

    /** The approval queue holding gated candidates awaiting a decision. */
    public ApprovalQueue queue() {
        return queue;
    }

    /** The set of skills with an improvement currently in flight. */
    public InFlightSet inFlight() {
        return inFlight;
    }

    /**
     * Runs one pass over the skill source. Returns the gated candidates produced this pass (held in
     * the queue when {@code autoPublish} is off; empty when auto-published).
     */
    public List<PendingCandidate> runOnce() {
        List<PendingCandidate> produced = new ArrayList<>();
        ResolvedSkillSource resolved = source.resolve();
        for (ResolvedPack pack : resolved.packs()) {
            String skill = pack.name();
            List<TraceRecord> window = traceReader.window(skill);
            if (window.isEmpty()) {
                continue;
            }
            TraceRecord latest = window.get(window.size() - 1);
            if (isCaughtUp(skill, latest.timestamp())) {
                continue; // idempotent: no traces newer than the watermark
            }
            SkillPack current = readPack(pack.root());
            if (current == null) {
                continue;
            }
            LifecycleMetadata sidecar = readSidecar(pack.root(), current);
            if (sidecar.state() == LifecycleState.RETIRED) {
                // Retired packs are excluded from automatic refinement (design state machine); a
                // human revives them via an explicit rebuild (change 06).
                advanceWatermark(skill, latest);
                continue;
            }
            // At most one active improvement per skill — checked before any (re-)eval spend, so an
            // in-flight skill is skipped without spending eval budget.
            if (!inFlight.begin(skill)) {
                advanceWatermark(skill, latest);
                continue;
            }
            try {
                Detector.Result detection = detect(skill, current, sidecar, window);
                if (detection.regressed()) {
                    sidecar = LifecycleTransitions.regressed(sidecar);
                    writeSidecar(pack.root(), sidecar);
                }
                // A degraded pack that regressed to retired in this pass is no longer refined.
                if (!detection.queued() || sidecar.state() == LifecycleState.RETIRED) {
                    advanceWatermark(skill, latest);
                    continue;
                }
                Refiner.Refinement refinement = refiner.refine(current, detection.failureTraces());
                if (refinement.changed()) {
                    GateResult gate = gateChain.gate(refinement.candidate(), sidecar.baselinePassRate());
                    if (gate.gated()) {
                        PendingCandidate candidate = toCandidate(skill, refinement, gate, sidecar, detection);
                        boolean held = queue.enqueue(candidate, autoPublish, this::publishCandidate);
                        if (held) {
                            produced.add(candidate);
                        }
                    } else {
                        lastRejections.put(skill, gate); // record the rejection with its score
                    }
                }
                advanceWatermark(skill, latest);
            } finally {
                inFlight.end(skill);
            }
        }
        return produced;
    }

    /** The most recent gate rejection for the skill, or null if none. */
    public GateResult lastRejection(String skill) {
        return lastRejections.get(skill);
    }

    /**
     * The per-skill detection: the cheap triggers (failed runs, content drift) plus the live re-eval
     * of the pack against its baseline. The re-eval is spent when the pack can regress
     * ({@code active}/{@code degraded}) or when its content has drifted from the sidecar — in both
     * cases the pack must be re-scored before anything is decided.
     */
    private Detector.Result detect(String skill, SkillPack current, LifecycleMetadata sidecar,
                                  List<TraceRecord> window) {
        boolean regressable = sidecar.state() == LifecycleState.ACTIVE
                || sidecar.state() == LifecycleState.DEGRADED;
        boolean needsReeval = regressable || Detector.hasDrift(current, sidecar);
        Detector.ReevalHook hook = needsReeval ? s -> gateChain.reeval(current) : null;
        return detector.detect(skill, current, sidecar, window, hook);
    }

    private PendingCandidate toCandidate(String skill, Refiner.Refinement refinement, GateResult gate,
                                         LifecycleMetadata sidecar, Detector.Result detection) {
        LifecycleState state = LifecycleTransitions.onPublished(sidecar.state());
        LifecycleMetadata proposed = LifecycleTransitions.sidecarFor(refinement.candidate(), gate.score(), state);
        String diff = "rewrote prompts of: " + String.join(", ", refinement.changedSteps());
        return new PendingCandidate(
                nextId(skill), skill, refinement.candidate(), proposed,
                gate.score(), sidecar.baselinePassRate(), gate.scan(),
                detection.failureTraces(), diff);
    }

    private PublishResult publishCandidate(PendingCandidate candidate) {
        try {
            return publisher.publish(candidate.candidate(), candidate.proposedLifecycle(), true, false);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot publish candidate for " + candidate.skill(), e);
        }
    }

    private String nextId(String skill) {
        return skill + "-" + idCounter.incrementAndGet();
    }

    /** The most recent trace for the skill, or {@code null} when the pack cannot be read. */
    private SkillPack readPack(Path root) {
        try {
            return packReader.read(root);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * The pack's lifecycle sidecar, or a fresh {@code incubating} sidecar (at the current content
     * hash, no baseline) when the pack has none.
     */
    private LifecycleMetadata readSidecar(Path root, SkillPack current) {
        Path sidecar = root.resolve("lifecycle.yml");
        if (Files.isRegularFile(sidecar)) {
            try {
                return Yaml.mapper().readValue(Files.readString(sidecar), LifecycleMetadata.class);
            } catch (IOException e) {
                // fall through to the default sidecar
            }
        }
        return new LifecycleMetadata(SkillContentHash.of(current), 0.0, LifecycleState.INCUBATING,
                Instant.now().toString());
    }

    private void writeSidecar(Path root, LifecycleMetadata metadata) {
        try {
            stamper.write(root, metadata);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write lifecycle sidecar for " + root, e);
        }
    }

    private boolean isCaughtUp(String skill, Instant latest) {
        Instant last = lastProcessed.get(skill);
        return last != null && !last.isBefore(latest);
    }

    private void advanceWatermark(String skill, TraceRecord latest) {
        lastProcessed.put(skill, latest.timestamp());
    }
}
