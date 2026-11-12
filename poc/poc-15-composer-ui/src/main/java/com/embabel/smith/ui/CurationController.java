package com.embabel.smith.ui;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.composer.SpecNode;
import com.embabel.smith.lifecycle.DriftReport;
import com.embabel.smith.lifecycle.LifecycleState;
import com.embabel.smith.lifecycle.LiveEvalRunner;
import com.embabel.smith.lifecycle.SpecEvalTask;
import com.embabel.smith.ui.dto.CurateRequest;
import com.embabel.smith.ui.dto.CurateResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The curation re-eval behind the retrieval pane (POC 15): re-score a spec with POC 18's
 * {@link LiveEvalRunner} and move it through the lifecycle, so the library is curated, not just
 * scored. This is the endpoint-gated half — the pure content-hash / drift / state-machine half
 * (POC 18's {@link com.embabel.smith.lifecycle.LifecycleManager} via {@link SpecCuration}) is what
 * the retrieval pane applies offline. Without a configured endpoint it returns a clear configuration
 * error before any model call.
 */
@RestController
public class CurationController {

    private final SpecCatalog catalog;
    private final SpecCuration curation;
    private final AgentPlatform platform;
    private final ModelEndpointConfig endpoint;

    @Autowired
    public CurationController(SpecCatalog catalog,
                              SpecCuration curation,
                              AgentPlatform platform,
                              @Value("${OPENAI_CUSTOM_BASE_URL:}") String baseUrl,
                              @Value("${OPENAI_CUSTOM_API_KEY:}") String apiKey,
                              @Value("${OPENAI_CUSTOM_MODELS:}") String models,
                              @Value("${EMBABEL_MODELS_DEFAULT_LLM:}") String defaultLlm) {
        this.catalog = catalog;
        this.curation = curation;
        this.platform = platform;
        this.endpoint = new ModelEndpointConfig(baseUrl, apiKey, models, defaultLlm);
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
        AgentSpec pack = node.pack();
        List<SpecEvalTask> tasks = request.tasksOrDefault().stream()
                .map(t -> new SpecEvalTask(t.input(), t.acceptanceCriteria(),
                        out -> out != null && !out.isBlank()
                                && out.toLowerCase().contains(t.mustContain().toLowerCase())))
                .toList();
        LifecycleState before = curation.stateBefore(specName);
        double measured = new LiveEvalRunner(platform).evaluate(pack, tasks);
        LifecycleState after = curation.curate(specName, measured);
        SpecCuration.Baseline b = curation.baseline(specName);
        DriftReport report = new DriftReport(specName, before, after,
                curation.contentDrift(specName), b.score(), measured,
                b.contentHash(), curation.currentHash(specName));
        return CurateResponse.from(specName, report, curation.surfaced(after));
    }
}
