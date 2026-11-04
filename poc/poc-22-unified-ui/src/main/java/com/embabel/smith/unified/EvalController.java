package com.embabel.smith.unified;

import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.composer.SkillNode;
import com.embabel.smith.refinement.RefinementRunner;
import com.embabel.smith.refinement.SkillEvalTask;
import com.embabel.smith.unified.dto.EvalRequest;
import com.embabel.smith.unified.dto.EvalResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The Eval pane (POC 22): given a skill from the palette, run a POC 5 eval over a set of tasks and
 * report the composite score + the failing inputs; when tasks fail, offer a POC 9-style prompt rewrite
 * from the failures and re-score. Endpoint-gated (the skill runs and the rewrite both call the model):
 * without a configured endpoint it returns a clear configuration error before any model call. The whole
 * loop is delegated to POC 9's {@link RefinementRunner} behind the {@link EvalFn} seam, so the
 * endpoint-present path is testable with a stub.
 */
@RestController
public class EvalController {

    /** The model-dependent eval + refine: skill name + tasks to the {@link RefinementRunner} outcome. */
    @FunctionalInterface
    public interface EvalFn {
        RefinementRunner.RefinementOutcome eval(String skillName, List<SkillEvalTask> tasks) throws Exception;
    }

    private final SkillCatalog catalog;
    private final EvalFn evaluator;
    private final ModelEndpointConfig endpoint;

    public EvalController(SkillCatalog catalog, EvalFn evaluator, ModelEndpointConfig endpoint) {
        this.catalog = catalog;
        this.evaluator = evaluator;
        this.endpoint = endpoint;
    }

    /** Evaluate (and, on failure, refine) a skill; a clear error when no endpoint is configured. */
    @PostMapping("/api/eval")
    public EvalResponse eval(@RequestBody EvalRequest request) {
        String skillName = request.skillName() == null ? "" : request.skillName();
        if (skillName.isBlank()) {
            return EvalResponse.error("No skill selected; pick a skill to evaluate first.");
        }
        SkillNode node = catalog.get(skillName);
        if (node == null) {
            return EvalResponse.error("Unknown skill: " + skillName + ". Choose one from the palette.");
        }
        String configError = endpoint.configError();
        if (configError != null) {
            return EvalResponse.error(configError);
        }
        try {
            List<SkillEvalTask> tasks = request.tasksOrDefault().stream()
                    .map(t -> new SkillEvalTask(t.input(), t.acceptanceCriteria(),
                            out -> out != null && !out.isBlank()
                                    && out.toLowerCase().contains(t.mustContain().toLowerCase())))
                    .toList();
            String stepName = pickStep(node.pack());
            RefinementRunner.RefinementOutcome outcome = evaluator.eval(skillName, tasks);
            return EvalResponse.ok(skillName, stepName, outcome.beforeScore(), outcome.afterScore(),
                    outcome.revisedPrompt() != null, outcome.improved(), outcome.revisedPrompt(),
                    outcome.failingInputs());
        } catch (Exception e) {
            return EvalResponse.error("Eval failed: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
        }
    }

    /** The step whose prompt to refine: the first step that carries a prompt. */
    private static String pickStep(SkillPack pack) {
        return pack.steps().stream()
                .filter(s -> s.prompt() != null && !s.prompt().isBlank())
                .map(ActionStepSpec::name)
                .findFirst()
                .orElseGet(() -> pack.steps().isEmpty() ? null : pack.steps().get(0).name());
    }
}
