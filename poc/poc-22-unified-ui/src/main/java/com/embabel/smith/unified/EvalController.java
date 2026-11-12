package com.embabel.smith.unified;

import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.composer.SpecNode;
import com.embabel.smith.refinement.RefinementRunner;
import com.embabel.smith.refinement.SpecEvalTask;
import com.embabel.smith.unified.dto.EvalRequest;
import com.embabel.smith.unified.dto.EvalResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The Eval pane (POC 22): given a spec from the palette, run a POC 5 eval over a set of tasks and
 * report the composite score + the failing inputs; when tasks fail, offer a POC 9-style prompt rewrite
 * from the failures and re-score. Endpoint-gated (the spec runs and the rewrite both call the model):
 * without a configured endpoint it returns a clear configuration error before any model call. The whole
 * loop is delegated to POC 9's {@link RefinementRunner} behind the {@link EvalFn} seam, so the
 * endpoint-present path is testable with a stub.
 */
@RestController
public class EvalController {

    /** The model-dependent eval + refine: spec name + tasks to the {@link RefinementRunner} outcome. */
    @FunctionalInterface
    public interface EvalFn {
        RefinementRunner.RefinementOutcome eval(String specName, List<SpecEvalTask> tasks) throws Exception;
    }

    private final SpecCatalog catalog;
    private final EvalFn evaluator;
    private final ModelEndpointConfig endpoint;

    public EvalController(SpecCatalog catalog, EvalFn evaluator, ModelEndpointConfig endpoint) {
        this.catalog = catalog;
        this.evaluator = evaluator;
        this.endpoint = endpoint;
    }

    /** Evaluate (and, on failure, refine) a spec; a clear error when no endpoint is configured. */
    @PostMapping("/api/eval")
    public EvalResponse eval(@RequestBody EvalRequest request) {
        String specName = request.specName() == null ? "" : request.specName();
        if (specName.isBlank()) {
            return EvalResponse.error("No spec selected; pick a spec to evaluate first.");
        }
        SpecNode node = catalog.get(specName);
        if (node == null) {
            return EvalResponse.error("Unknown spec: " + specName + ". Choose one from the palette.");
        }
        String configError = endpoint.configError();
        if (configError != null) {
            return EvalResponse.error(configError);
        }
        try {
            List<SpecEvalTask> tasks = request.tasksOrDefault().stream()
                    .map(t -> new SpecEvalTask(t.input(), t.acceptanceCriteria(),
                            out -> out != null && !out.isBlank()
                                    && out.toLowerCase().contains(t.mustContain().toLowerCase())))
                    .toList();
            String stepName = pickStep(node.pack());
            RefinementRunner.RefinementOutcome outcome = evaluator.eval(specName, tasks);
            return EvalResponse.ok(specName, stepName, outcome.beforeScore(), outcome.afterScore(),
                    outcome.revisedPrompt() != null, outcome.improved(), outcome.revisedPrompt(),
                    outcome.failingInputs());
        } catch (Exception e) {
            return EvalResponse.error("Eval failed: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
        }
    }

    /** The step whose prompt to refine: the first step that carries a prompt. */
    private static String pickStep(AgentSpec pack) {
        return pack.steps().stream()
                .filter(s -> s.prompt() != null && !s.prompt().isBlank())
                .map(ActionStepSpec::name)
                .findFirst()
                .orElseGet(() -> pack.steps().isEmpty() ? null : pack.steps().get(0).name());
    }
}
