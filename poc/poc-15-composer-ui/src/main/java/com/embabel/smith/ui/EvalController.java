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

package com.embabel.smith.ui;

import java.util.List;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.composer.SpecNode;
import com.embabel.smith.refinement.RefinementRunner;
import com.embabel.smith.refinement.SpecEvalTask;
import com.embabel.smith.ui.dto.EvalRequest;
import com.embabel.smith.ui.dto.EvalResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The eval/refinement pane behind the canvas (POC 15): given a spec from the palette, run
 * a POC 5 eval over a set of tasks and report the composite score + the failing inputs;
 * when tasks fail, offer a POC 9-style prompt rewrite from the failures and re-score — "a
 * spec can be checked and refined in one place." Endpoint-gated (the spec runs and the
 * rewrite both call the model): without a configured endpoint it returns a clear
 * configuration error before any model call.
 *
 * <p>
 * Delegates the whole loop to POC 9's {@link RefinementRunner} — deploy the spec,
 * evaluate, rewrite the prompt from the failing tasks, re-validate, redeploy, and
 * re-evaluate — so the pane reuses the proven, tested refinement flow rather than
 * reimplementing it.
 *
 * @author Ivan Rodriguez
 */
@RestController
public class EvalController {

	private final SpecCatalog catalog;

	private final AgentPlatform platform;

	private final ModelEndpointConfig endpoint;

	@Autowired
	public EvalController(SpecCatalog catalog, AgentPlatform platform,
			@Value("${OPENAI_CUSTOM_BASE_URL:}") String baseUrl, @Value("${OPENAI_CUSTOM_API_KEY:}") String apiKey,
			@Value("${OPENAI_CUSTOM_MODELS:}") String models,
			@Value("${EMBABEL_MODELS_DEFAULT_LLM:}") String defaultLlm) {
		this.catalog = catalog;
		this.platform = platform;
		this.endpoint = new ModelEndpointConfig(baseUrl, apiKey, models, defaultLlm);
	}

	/**
	 * Evaluate (and, on failure, refine) a spec; a clear error when no endpoint is
	 * configured.
	 * @param request the eval request (spec name and tasks).
	 * @return the eval outcome (score, failing inputs, refined prompt).
	 */
	@PostMapping("/api/eval")
	public EvalResponse eval(@RequestBody EvalRequest request) {
		String specName = (request.specName() == null) ? "" : request.specName();
		if (specName.isBlank()) {
			return EvalResponse.error("No spec selected; pick a spec to evaluate first.");
		}
		SpecNode node = this.catalog.get(specName);
		if (node == null) {
			return EvalResponse.error("Unknown spec: " + specName + ". Choose one from the palette.");
		}
		String configError = this.endpoint.configError();
		if (configError != null) {
			return EvalResponse.error(configError);
		}
		AgentSpec pack = node.pack();
		String stepName = pickStep(pack);
		List<SpecEvalTask> tasks = request.tasksOrDefault()
			.stream()
			.map((t) -> new SpecEvalTask(t.input(), t.acceptanceCriteria(),
					(out) -> out != null && !out.isBlank()
							&& out.toLowerCase().contains(t.mustContain().toLowerCase())))
			.toList();
		RefinementRunner runner = new RefinementRunner(this.platform);
		RefinementRunner.Endpoint ep = new RefinementRunner.Endpoint(this.endpoint.baseUrl(),
				this.endpoint.apiKey(), this.endpoint.defaultLlm());
		RefinementRunner.RefinementOutcome outcome = runner.improve(pack, stepName, tasks, ep);
		return EvalResponse.from(outcome, specName, stepName);
	}

	/**
	 * The step whose prompt to refine: the first step that carries a prompt.
	 * @param pack the agent spec to pick a step from.
	 * @return the name of the first step with a prompt, or the first step's name when none does.
	 */
	private String pickStep(AgentSpec pack) {
		return pack.steps()
			.stream()
			.filter((s) -> s.prompt() != null && !s.prompt().isBlank())
			.map(ActionStepSpec::name)
			.findFirst()
			.orElseGet(() -> pack.steps().isEmpty() ? null : pack.steps().get(0).name());
	}

}
