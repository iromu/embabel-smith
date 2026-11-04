package com.embabel.smith.unified;

import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.composer.SkillNode;
import com.embabel.smith.lifecycle.LifecycleManager;
import com.embabel.smith.lifecycle.LifecycleState;
import com.embabel.smith.lifecycle.SkillContentHash;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Library curation for the retrieval + lifecycle panes, reusing POC 18's pure lifecycle machinery:
 * a skill's contract is its eval pass-rate, and its lifecycle (incubating → active → degraded →
 * retired) is driven by POC 18's {@link LifecycleManager}. The retrieval pane uses this to curate
 * the library — retired skills are dropped and degraded ones de-ranked — rather than just scoring
 * it.
 *
 * <p>Each catalog skill is seeded as a validated {@link LifecycleState#ACTIVE} baseline (its live
 * content hash, an assumed-passing score of 1.0). {@link #curate} records a freshly-measured
 * pass-rate and moves the skill through the lifecycle, <em>persisting the new state</em> so later
 * re-evals build on it (POC 18's state machine is stateful — retirement is terminal, promotion is
 * from incubation). The content-hash / drift / state-machine half is pure and offline; only the
 * measured score (from a live re-eval) is endpoint-gated.
 */
@Component
public class SkillCuration {

    /** The de-rank factor applied to a degraded skill's retrieval score. */
    private static final double DEGRADED_FACTOR = 0.5;

    /** A skill's recorded baseline: the content hash and eval pass-rate it was validated at. */
    public record Baseline(String contentHash, double score) {}

    private final LifecycleManager lifecycle = LifecycleManager.defaults();
    private final Map<String, SkillPack> packs = new LinkedHashMap<>();
    private final Map<String, Baseline> baselines = new LinkedHashMap<>();
    private final Map<String, LifecycleState> states = new LinkedHashMap<>();
    private final Map<String, Double> currentScores = new HashMap<>();

    public SkillCuration(SkillCatalog catalog) {
        catalog.all().forEach((name, node) -> {
            SkillPack pack = node.pack();
            packs.put(name, pack);
            baselines.put(name, new Baseline(SkillContentHash.of(pack), 1.0));
            states.put(name, LifecycleState.ACTIVE);
        });
    }

    /**
     * The skill's current lifecycle state, re-derived by POC 18's state machine from its recorded
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
        boolean contentDrift = !b.contentHash().equals(SkillContentHash.of(packs.get(name)));
        return lifecycle.transition(state, contentDrift, b.score(), currentScore);
    }

    /** The skill's state before a re-eval (its recorded state re-derived from the last measured score). */
    public LifecycleState stateBefore(String name) {
        return currentState(name);
    }

    /** Record a freshly-measured eval pass-rate and move the skill to its (possibly new) state. */
    public LifecycleState curate(String name, double measuredScore) {
        currentScores.put(name, measuredScore);
        LifecycleState newState = currentState(name);
        states.put(name, newState);
        return newState;
    }

    /** The skill's recorded baseline (content hash, score). */
    public Baseline baseline(String name) {
        return baselines.get(name);
    }

    /** The skill's live content hash (POC 18's {@link SkillContentHash}). */
    public String currentHash(String name) {
        return SkillContentHash.of(packs.get(name));
    }

    /** True when the skill's content no longer matches its recorded baseline hash. */
    public boolean contentDrift(String name) {
        Baseline b = baselines.get(name);
        return b != null && !b.contentHash().equals(currentHash(name));
    }

    /** A retired skill is out of the library — it is not surfaced by retrieval. */
    public boolean surfaced(LifecycleState state) {
        return state != LifecycleState.RETIRED;
    }

    /** Degraded skills are de-ranked (their retrieval score is halved); the rest keep their score. */
    public double deRankFactor(LifecycleState state) {
        return state == LifecycleState.DEGRADED ? DEGRADED_FACTOR : 1.0;
    }
}
