package com.embabel.smith.ui;

import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.composer.SpecNode;
import com.embabel.smith.lifecycle.LifecycleManager;
import com.embabel.smith.lifecycle.LifecycleState;
import com.embabel.smith.lifecycle.SpecContentHash;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Library curation for the retrieval pane (POC 15), reusing POC 18's pure lifecycle machinery: a
 * spec's contract is its eval pass-rate, and its lifecycle (incubating → active → degraded →
 * retired) is driven by POC 18's {@link LifecycleManager}. The retrieval pane uses this to curate
 * the library — retired specs are dropped and degraded ones de-ranked — rather than just scoring it.
 *
 * <p>Each catalog spec is seeded as a validated {@link LifecycleState#ACTIVE} baseline (its live
 * content hash, an assumed-passing score of 1.0). {@link #curate} records a freshly-measured
 * pass-rate and moves the spec through the lifecycle, <em>persisting the new state</em> so later
 * re-evals build on it (POC 18's state machine is stateful — retirement is terminal, promotion is
 * from incubation). The content-hash / drift / state-machine half is pure and offline; only the
 * measured score (from a live re-eval) is endpoint-gated.
 */
@Component
public class SpecCuration {

    /** The de-rank factor applied to a degraded spec's retrieval score. */
    private static final double DEGRADED_FACTOR = 0.5;

    /** A spec's recorded baseline: the content hash and eval pass-rate it was validated at. */
    public record Baseline(String contentHash, double score) {}

    private final LifecycleManager lifecycle = LifecycleManager.defaults();
    private final Map<String, AgentSpec> packs = new LinkedHashMap<>();
    private final Map<String, Baseline> baselines = new LinkedHashMap<>();
    private final Map<String, LifecycleState> states = new LinkedHashMap<>();
    private final Map<String, Double> currentScores = new HashMap<>();

    public SpecCuration(SpecCatalog catalog) {
        catalog.all().forEach((name, node) -> {
            AgentSpec pack = node.pack();
            packs.put(name, pack);
            baselines.put(name, new Baseline(SpecContentHash.of(pack), 1.0));
            states.put(name, LifecycleState.ACTIVE);
        });
    }

    /**
     * The spec's current lifecycle state, re-derived by POC 18's state machine from its recorded
     * state, the live content hash (content drift if the pack changed since baseline), and the
     * latest measured score (score drift if it fell off the baseline).
     */
    public LifecycleState currentState(String name) {
        LifecycleState state = states.get(name);
        if (state == null) {
            return LifecycleState.INCUBATING;
        }
        Baseline b = baselines.get(name);
        double currentScore = currentScores.getOrDefault(name, b.score());
        boolean contentDrift = !b.contentHash().equals(SpecContentHash.of(packs.get(name)));
        return lifecycle.transition(state, contentDrift, b.score(), currentScore);
    }

    /** The spec's state before a re-eval (its recorded state re-derived from the last measured score). */
    public LifecycleState stateBefore(String name) {
        return currentState(name);
    }

    /** Record a freshly-measured eval pass-rate and move the spec to its (possibly new) state. */
    public LifecycleState curate(String name, double measuredScore) {
        currentScores.put(name, measuredScore);
        LifecycleState newState = currentState(name);
        states.put(name, newState);
        return newState;
    }

    /** The spec's recorded baseline (content hash, score). */
    public Baseline baseline(String name) {
        return baselines.get(name);
    }

    /** The spec's live content hash (POC 18's {@link SpecContentHash}). */
    public String currentHash(String name) {
        return SpecContentHash.of(packs.get(name));
    }

    /** True when the spec's content no longer matches its recorded baseline hash. */
    public boolean contentDrift(String name) {
        Baseline b = baselines.get(name);
        return b != null && !b.contentHash().equals(currentHash(name));
    }

    /** A retired spec is out of the library — it is not surfaced by retrieval. */
    public boolean surfaced(LifecycleState state) {
        return state != LifecycleState.RETIRED;
    }

    /** Degraded specs are de-ranked (their retrieval score is halved); the rest keep their score. */
    public double deRankFactor(LifecycleState state) {
        return state == LifecycleState.DEGRADED ? DEGRADED_FACTOR : 1.0;
    }
}
