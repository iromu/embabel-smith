package com.embabel.smith.studio.rest;

import com.embabel.smith.builder.eval.EvalTask;
import com.embabel.smith.builder.eval.RefinementOutcome;
import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.studio.rest.dto.EvalRequest;
import com.embabel.smith.studio.rest.dto.EvalResponse;
import com.embabel.smith.studio.rest.dto.EvalTaskDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The Eval pane (change 06): evaluate a skill from a source over a task set through the change-04
 * builder's bounded eval → refine loop. Endpoint-gated — without a configured model endpoint it
 * returns the structured {@code MODEL_NOT_CONFIGURED} error before any evaluation. The evaluation is
 * behind the {@link EvalFn} seam so the endpoint-present path is testable with a stub.
 */
@RestController
public class EvalController {

    /** The model-dependent evaluation: a pack and its tasks to a refinement outcome. */
    @FunctionalInterface
    public interface EvalFn {
        RefinementOutcome eval(SkillPack pack, List<EvalTask> tasks);
    }

    private final EvalFn evaluator;
    private final SourceService sourceService;
    private final ModelEndpointConfig endpoint;

    public EvalController(EvalFn evaluator, SourceService sourceService, ModelEndpointConfig endpoint) {
        this.evaluator = evaluator;
        this.sourceService = sourceService;
        this.endpoint = endpoint;
    }

    @PostMapping("/api/eval")
    public EvalResponse eval(@RequestBody EvalRequest request) {
        if (request.source() == null || request.source().isBlank()
                || request.skill() == null || request.skill().isBlank()) {
            return EvalResponse.error("No source or skill selected; pick one to evaluate.");
        }
        String configError = endpoint.configError();
        if (configError != null) {
            return EvalResponse.noEndpoint(configError);
        }
        try {
            SkillPack pack = sourceService.readPack(request.source(), request.skill());
            List<EvalTask> tasks = toTasks(request.tasks());
            RefinementOutcome outcome = evaluator.eval(pack, tasks);
            String revisedPrompt = outcome.refinementCount() > 0
                    ? terminalPrompt(outcome.pack()) : null;
            return EvalResponse.ok(request.skill(), outcome.score(), outcome.refinementCount(),
                    outcome.failingTasks(), outcome.passed(), revisedPrompt);
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            return EvalResponse.error("Eval failed: " + message);
        }
    }

    private static List<EvalTask> toTasks(List<EvalTaskDto> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of(new EvalTask("runs-and-produces-output", "Describe your use case.", ""));
        }
        return tasks.stream().map(t -> new EvalTask(t.name(), t.input(), t.expected())).toList();
    }

    /** The terminal action's prompt (the one producing the goal output); the last step as fallback. */
    private static String terminalPrompt(SkillPack pack) {
        String goalOutput = pack.goal() == null ? null : pack.goal().outputTypeName();
        ActionStepSpec terminal = pack.steps().stream()
                .filter(s -> goalOutput != null && goalOutput.equals(s.outputTypeName()))
                .reduce((a, b) -> b)
                .orElseGet(() -> pack.steps().isEmpty() ? null : pack.steps().get(pack.steps().size() - 1));
        return terminal == null ? null : terminal.prompt();
    }
}
