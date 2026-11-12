/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.studio.rest;

import java.util.List;

import com.embabel.smith.builder.eval.EvalTask;
import com.embabel.smith.builder.eval.RefinementOutcome;
import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.studio.rest.dto.EvalRequest;
import com.embabel.smith.studio.rest.dto.EvalResponse;
import com.embabel.smith.studio.rest.dto.EvalTaskDto;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Eval pane (change 06): evaluate a spec from a source over a task set through the
 * change-04 builder's bounded eval → refine loop. Endpoint-gated — without a configured
 * model endpoint it returns the structured {@code MODEL_NOT_CONFIGURED} error before any
 * evaluation. The evaluation is behind the {@link EvalFn} seam so the endpoint-present
 * path is testable with a stub.
 *
 * @author Ivan Rodriguez
 */
@RestController
public class EvalController {

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
		if (request.source() == null || request.source().isBlank() || request.spec() == null
				|| request.spec().isBlank()) {
			return EvalResponse.error("No source or spec selected; pick one to evaluate.");
		}
		String configError = this.endpoint.configError();
		if (configError != null) {
			return EvalResponse.noEndpoint(configError);
		}
		try {
			AgentSpec pack = this.sourceService.readPack(request.source(), request.spec());
			List<EvalTask> tasks = toTasks(request.tasks());
			RefinementOutcome outcome = this.evaluator.eval(pack, tasks);
			String revisedPrompt = (outcome.refinementCount() > 0) ? terminalPrompt(outcome.pack()) : null;
			return EvalResponse.ok(request.spec(), outcome.score(), outcome.refinementCount(), outcome.failingTasks(),
					outcome.passed(), revisedPrompt);
		}
		catch (Exception ex) {
			String message = (ex.getMessage() == null) ? ex.toString() : ex.getMessage();
			return EvalResponse.error("Eval failed: " + message);
		}
	}

	private static List<EvalTask> toTasks(List<EvalTaskDto> tasks) {
		if (tasks == null || tasks.isEmpty()) {
			return EvalTask.smokeDefault();
		}
		return tasks.stream().map((t) -> new EvalTask(t.name(), t.input(), t.expected())).toList();
	}

	/**
	 * The terminal action's prompt (the one producing the goal output); the last step as
	 * fallback.
	 * @param pack the spec whose terminal prompt is read.
	 * @return the terminal step's prompt, or {@code null} when the spec has no steps.
	 */
	private static String terminalPrompt(AgentSpec pack) {
		String goalOutput = (pack.goal() == null) ? null : pack.goal().outputTypeName();
		ActionStepSpec terminal = pack.steps()
			.stream()
			.filter((s) -> goalOutput != null && goalOutput.equals(s.outputTypeName()))
			.reduce((a, b) -> b)
			.orElseGet(() -> pack.steps().isEmpty() ? null : pack.steps().get(pack.steps().size() - 1));
		return (terminal != null) ? terminal.prompt() : null;
	}

	/** The model-dependent evaluation: a pack and its tasks to a refinement outcome. */
	@FunctionalInterface
	public interface EvalFn {

		RefinementOutcome eval(AgentSpec pack, List<EvalTask> tasks);

	}

}
