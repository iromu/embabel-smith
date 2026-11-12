package com.embabel.smith.unified;

import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.composer.SpecNode;
import com.embabel.smith.lifecycle.DriftReport;
import com.embabel.smith.lifecycle.LifecycleState;
import com.embabel.smith.lifecycle.LiveEvalRunner;
import com.embabel.smith.lifecycle.SpecEvalTask;
import com.embabel.smith.unified.dto.CurateRequest;
import com.embabel.smith.unified.dto.CurateResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The Lifecycle pane (POC 22): re-score a spec with POC 18's {@link LiveEvalRunner} and move it
 * through the lifecycle, so the library is curated, not just scored. The live re-eval is the
 * endpoint-gated half behind the {@link LiveEvalFn} seam; the pure content-hash / drift / state-machine
 * half (POC 18's {@link com.embabel.smith.lifecycle.LifecycleManager} via {@link SpecCuration}) is
 * what runs offline. Without a configured endpoint it returns a clear configuration error before any
 * model call, and the endpoint-present path is testable with a stub.
 */
@RestController
public class LifecycleController {

    /** The model-dependent re-eval: spec name + tasks to the measured live eval pass-rate. */
    @FunctionalInterface
    public interface LiveEvalFn {
        double reEval(String specName, List<SpecEvalTask> tasks) throws Exception;
    }

    private final SpecCatalog catalog;
    private final SpecCuration curation;
    private final LiveEvalFn evaluator;
    private final ModelEndpointConfig endpoint;

    public LifecycleController(SpecCatalog catalog, SpecCuration curation, LiveEvalFn evaluator,
                               ModelEndpointConfig endpoint) {
        this.catalog = catalog;
        this.curation = curation;
        this.evaluator = evaluator;
        this.endpoint = endpoint;
    }

    /** Re-evaluate a spec's pass-rate and record its (possibly new) lifecycle state. */
    @PostMapping("/api/curate")
    public CurateResponse curate(@RequestBody CurateRequest request) {
        String specName = request.specName() == null ? "" : request.specName();
        if (specName.isBlank()) {
            return CurateResponse.error("No spec selected; pick a spec to re-evaluate first.");
        }
        SpecNode node = catalog.get(specName);
        if (node == null) {
            return CurateResponse.error("Unknown spec: " + specName + ". Choose one from the palette.");
        }
        String configError = endpoint.configError();
        if (configError != null) {
            return CurateResponse.error(configError);
        }
        try {
            List<SpecEvalTask> tasks = request.tasksOrDefault().stream()
                    .map(t -> new SpecEvalTask(t.input(), t.acceptanceCriteria(),
                            out -> out != null && !out.isBlank()
                                    && out.toLowerCase().contains(t.mustContain().toLowerCase())))
                    .toList();
            LifecycleState before = curation.stateBefore(specName);
            double measured = evaluator.reEval(specName, tasks);
            LifecycleState after = curation.curate(specName, measured);
            SpecCuration.Baseline b = curation.baseline(specName);
            DriftReport report = new DriftReport(specName, before, after,
                    curation.contentDrift(specName), b.score(), measured,
                    b.contentHash(), curation.currentHash(specName));
            return CurateResponse.from(specName, report, curation.surfaced(after));
        } catch (Exception e) {
            return CurateResponse.error("Re-eval failed: "
                    + (e.getMessage() == null ? e.toString() : e.getMessage()));
        }
    }
}
