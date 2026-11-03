package com.embabel.smith.ui;

import com.embabel.smith.retrieval.scale.LexicalScorer;
import com.embabel.smith.retrieval.scale.ScaleSkillRegistry;
import com.embabel.smith.retrieval.scale.SkillScorer;
import com.embabel.smith.ui.dto.RetrieveRequest;
import com.embabel.smith.ui.dto.RetrieveResponse;
import com.embabel.smith.ui.dto.RetrieveResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The retrieval pane behind the canvas (POC 15): given a natural-language query, rank the catalog's
 * skills by relevance and return the top-k — the "which skills would this task route to" view doc 03
 * §2.1 asks the builder UI to expose. It reuses the same {@link SkillScorer} seam the whole retrieval
 * POC family shares (POC 8 lexical, POC 12 dense, POC 17 GLiNER2, POC 20 real embedding), so the
 * default offline {@link LexicalScorer} is a drop-in for a denser scorer.
 */
@RestController
public class RetrievalController {

    private final ScaleSkillRegistry registry;
    private final SkillScorer scorer;
    private final String scorerName;

    /** Offline default: the POC 8 lexical scorer over the catalog. */
    @Autowired
    public RetrievalController(SkillCatalog catalog) {
        this(catalog, new LexicalScorer(), "lexical");
    }

    /** Pluggable scorer — the seam POC 20's real {@code EmbeddingService} drops into. */
    public RetrievalController(SkillCatalog catalog, SkillScorer scorer, String scorerName) {
        this.scorer = scorer;
        this.scorerName = scorerName;
        this.registry = new ScaleSkillRegistry(catalog.retrievalEntries(), scorer);
    }

    /** Rank the catalog by relevance to the query and return the top-k with their scores. */
    @PostMapping("/api/retrieve")
    public RetrieveResponse retrieve(@RequestBody RetrieveRequest request) {
        String query = request.query() == null ? "" : request.query();
        List<RetrieveResult> results = registry.retrieve(query, request.kOrDefault()).stream()
                .map(e -> new RetrieveResult(e.name(), e.description(),
                        round4(scorer.score(query, e)), e.outputType()))
                .toList();
        return new RetrieveResponse(scorerName, results);
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
