package com.embabel.smith.unified;

import com.embabel.smith.lifecycle.LifecycleState;
import com.embabel.smith.retrieval.SkillEntry;
import com.embabel.smith.retrieval.scale.ScaleSkillRegistry;
import com.embabel.smith.retrieval.scale.SkillScorer;
import com.embabel.smith.unified.dto.RetrieveRequest;
import com.embabel.smith.unified.dto.RetrieveResponse;
import com.embabel.smith.unified.dto.RetrieveResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The Retrieve pane (POC 22): given a natural-language query, rank the catalog's skills by relevance
 * and return the top-k — the "which skills would this task route to" view. It reuses the same
 * {@link SkillScorer} seam the whole retrieval POC family shares (POC 8 lexical, POC 12 dense, POC 17
 * GLiNER2, POC 20 real embedding); the active scorer is a {@link ScorerBinding} bean, so a denser
 * scorer drops in without changing the pane's interface, and the response carries the active scorer
 * name.
 *
 * <p>On top of the raw relevance ranking, the pane applies POC 18's library curation via
 * {@link SkillCuration}: a retired skill is dropped from the results entirely, and a degraded one is
 * de-ranked (its score halved, so it falls in the ranking) — the library is curated, not just scored.
 */
@RestController
public class RetrieveController {

    private final ScaleSkillRegistry registry;
    private final SkillScorer scorer;
    private final String scorerName;
    private final SkillCuration curation;

    public RetrieveController(SkillCatalog catalog, SkillCuration curation, ScorerBinding binding) {
        this.scorer = binding.scorer();
        this.scorerName = binding.name();
        this.curation = curation;
        this.registry = new ScaleSkillRegistry(catalog.retrievalEntries(), scorer);
    }

    /** Rank the catalog by relevance to the query, curate it, and return the top-k with scores. */
    @PostMapping("/api/retrieve")
    public RetrieveResponse retrieve(@RequestBody RetrieveRequest request) {
        String query = request.query() == null ? "" : request.query();
        int k = request.kOrDefault();
        List<RetrieveResult> results = registry.retrieve(query, registry.size()).stream()
                .map(e -> curated(query, e))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(RetrieveResult::score).reversed())
                .limit(k)
                .toList();
        return new RetrieveResponse(scorerName, results);
    }

    /**
     * Curate one ranked hit: drop it if its skill is retired, otherwise de-rank it if degraded and
     * stamp its lifecycle state. Returns null for a retired (out-of-library) skill.
     */
    private RetrieveResult curated(String query, SkillEntry e) {
        LifecycleState state = curation.currentState(e.name());
        if (!curation.surfaced(state)) {
            return null;
        }
        double adjusted = round4(scorer.score(query, e) * curation.deRankFactor(state));
        return new RetrieveResult(e.name(), e.description(), adjusted, e.outputType(), state.name());
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
