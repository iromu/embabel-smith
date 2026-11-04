package com.embabel.smith.unified;

import com.embabel.smith.retrieval.SkillEntry;
import com.embabel.smith.retrieval.scale.ScaleRetrievalRunner;
import com.embabel.smith.retrieval.scale.ScaleSkillLibrary;
import com.embabel.smith.retrieval.scale.ScaleSkillRegistry;
import com.embabel.smith.retrieval.scale.SkillScorer;
import com.embabel.smith.unified.dto.ScalePoint;
import com.embabel.smith.unified.dto.ScaleResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * The Scale pane (POC 22): quantifies selection accuracy (precision@1) of the active
 * {@link SkillScorer} as a same-typed library grows and as near-duplicate (shadowing) skills
 * accumulate, via POC 12's {@link ScaleRetrievalRunner} over {@link ScaleSkillLibrary}. Pure and
 * offline (the lexical scorer is the default; a denser scorer drops in via the shared seam).
 */
@RestController
public class ScaleController {

    private static final int CURVE_POINTS = 12;

    private final SkillScorer scorer;
    private final String scorerName;

    public ScaleController(ScorerBinding binding) {
        this.scorer = binding.scorer();
        this.scorerName = binding.name();
    }

    /** Precision@1 for the core and full libraries, plus a sampled growth curve. */
    @GetMapping("/api/scale")
    public ScaleResponse scale() {
        List<SkillEntry> core = ScaleSkillLibrary.core();
        List<SkillEntry> full = ScaleSkillLibrary.full();
        List<ScaleSkillLibrary.QueryTarget> benchmark = ScaleSkillLibrary.benchmark();
        ScaleRetrievalRunner runner = new ScaleRetrievalRunner();
        double coreP1 = runner.precisionAt1(new ScaleSkillRegistry(core, scorer), benchmark);
        double fullP1 = runner.precisionAt1(new ScaleSkillRegistry(full, scorer), benchmark);
        List<ScaleRetrievalRunner.CurvePoint> curve =
                runner.growthCurve(core, ScaleSkillLibrary.growthSequence(), scorer, benchmark);
        return new ScaleResponse(scorerName, core.size(), round4(coreP1), full.size(), round4(fullP1),
                sample(curve, CURVE_POINTS));
    }

    /** Evenly sample up to {@code n} points from the growth curve to keep the response compact. */
    private static List<ScalePoint> sample(List<ScaleRetrievalRunner.CurvePoint> curve, int n) {
        if (curve.size() <= n) {
            return curve.stream().map(p -> point(p)).toList();
        }
        List<ScalePoint> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int idx = (int) Math.round((double) i * (curve.size() - 1) / (n - 1));
            out.add(point(curve.get(idx)));
        }
        return out;
    }

    private static ScalePoint point(ScaleRetrievalRunner.CurvePoint p) {
        return new ScalePoint(p.librarySize(), round4(p.precisionAt1()));
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
