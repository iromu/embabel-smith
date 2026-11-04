package com.embabel.smith.unified;

import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.composer.SkillNode;
import com.embabel.smith.lifecycle.DriftReport;
import com.embabel.smith.lifecycle.LifecycleState;
import com.embabel.smith.lifecycle.LiveEvalRunner;
import com.embabel.smith.lifecycle.SkillEvalTask;
import com.embabel.smith.unified.dto.CurateRequest;
import com.embabel.smith.unified.dto.CurateResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The Lifecycle pane (POC 22): re-score a skill with POC 18's {@link LiveEvalRunner} and move it
 * through the lifecycle, so the library is curated, not just scored. The live re-eval is the
 * endpoint-gated half behind the {@link LiveEvalFn} seam; the pure content-hash / drift / state-machine
 * half (POC 18's {@link com.embabel.smith.lifecycle.LifecycleManager} via {@link SkillCuration}) is
 * what runs offline. Without a configured endpoint it returns a clear configuration error before any
 * model call, and the endpoint-present path is testable with a stub.
 */
@RestController
public class LifecycleController {

    /** The model-dependent re-eval: skill name + tasks to the measured live eval pass-rate. */
    @FunctionalInterface
    public interface LiveEvalFn {
        double reEval(String skillName, List<SkillEvalTask> tasks) throws Exception;
    }

    private final SkillCatalog catalog;
    private final SkillCuration curation;
    private final LiveEvalFn evaluator;
    private final ModelEndpointConfig endpoint;

    public LifecycleController(SkillCatalog catalog, SkillCuration curation, LiveEvalFn evaluator,
                               ModelEndpointConfig endpoint) {
        this.catalog = catalog;
        this.curation = curation;
        this.evaluator = evaluator;
        this.endpoint = endpoint;
    }

    /** Re-evaluate a skill's pass-rate and record its (possibly new) lifecycle state. */
    @PostMapping("/api/curate")
    public CurateResponse curate(@RequestBody CurateRequest request) {
        String skillName = request.skillName() == null ? "" : request.skillName();
        if (skillName.isBlank()) {
            return CurateResponse.error("No skill selected; pick a skill to re-evaluate first.");
        }
        SkillNode node = catalog.get(skillName);
        if (node == null) {
            return CurateResponse.error("Unknown skill: " + skillName + ". Choose one from the palette.");
        }
        String configError = endpoint.configError();
        if (configError != null) {
            return CurateResponse.error(configError);
        }
        try {
            List<SkillEvalTask> tasks = request.tasksOrDefault().stream()
                    .map(t -> new SkillEvalTask(t.input(), t.acceptanceCriteria(),
                            out -> out != null && !out.isBlank()
                                    && out.toLowerCase().contains(t.mustContain().toLowerCase())))
                    .toList();
            LifecycleState before = curation.stateBefore(skillName);
            double measured = evaluator.reEval(skillName, tasks);
            LifecycleState after = curation.curate(skillName, measured);
            SkillCuration.Baseline b = curation.baseline(skillName);
            DriftReport report = new DriftReport(skillName, before, after,
                    curation.contentDrift(skillName), b.score(), measured,
                    b.contentHash(), curation.currentHash(skillName));
            return CurateResponse.from(skillName, report, curation.surfaced(after));
        } catch (Exception e) {
            return CurateResponse.error("Re-eval failed: "
                    + (e.getMessage() == null ? e.toString() : e.getMessage()));
        }
    }
}
