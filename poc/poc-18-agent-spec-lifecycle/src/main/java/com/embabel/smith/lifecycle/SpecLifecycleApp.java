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

package com.embabel.smith.lifecycle;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.common.model.AgentSpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The spec lifecycle / drift-detection launcher (POC 18). Always runs the deterministic
 * lifecycle walkthrough (content-hash drift + score drift + the state machine, no model
 * needed); when a model endpoint is configured it additionally runs the <em>live</em>
 * re-eval — the real eval pass-rate of the healthy vs. regressed spec — as the
 * score-drift evidence.
 *
 * <p>
 * Run: {@code mvn -pl poc/poc-18-spec-lifecycle spring-boot:run} (endpoint optional; the
 * deterministic walkthrough runs either way).
 *
 * @author Ivan Rodriguez
 */
@SpringBootApplication
public class SpecLifecycleApp implements CommandLineRunner {

	private final AgentPlatform platform;

	private final ModelEndpointConfig endpoint;

	private final LifecycleManager lifecycle = LifecycleManager.defaults();

	public SpecLifecycleApp(AgentPlatform platform, @Value("${OPENAI_CUSTOM_BASE_URL:}") String baseUrl,
			@Value("${OPENAI_CUSTOM_API_KEY:}") String apiKey, @Value("${OPENAI_CUSTOM_MODELS:}") String models,
			@Value("${EMBABEL_MODELS_DEFAULT_LLM:}") String defaultLlm) {
		this.platform = platform;
		this.endpoint = new ModelEndpointConfig(baseUrl, apiKey, models, defaultLlm);
	}

	public static void main(String[] args) {
		SpringApplication.run(SpecLifecycleApp.class, args);
	}

	@Override
	public void run(String... args) {
		System.out.println("=== POC 18: spec lifecycle / drift detection (doc 03 §1, doc 02 §5 #4) ===");
		System.out.println("A spec's contract is its eval pass-rate; drift = its content or score diverging");
		System.out.println("from the recorded baseline. Lifecycle: incubating -> active -> degraded -> retired.");
		System.out.println();

		runDeterministicWalkthrough();
		System.out.println();
		runLiveReEval();
	}

	/**
	 * The deterministic half: content-hash drift + score drift + the state machine, no
	 * model.
	 */
	private void runDeterministicWalkthrough() {
		AgentSpec healthy = LifecycleSpec.healthy();
		AgentSpec reworded = LifecycleSpec.reworded();
		AgentSpec regressed = LifecycleSpec.regressed();
		String healthyHash = SpecContentHash.of(healthy);

		System.out.println("-- Deterministic walkthrough (no model) --");
		System.out.printf("  content hash (healthy): %s%n", shortHash(healthyHash));
		System.out.printf("  content hash (reworded): %s  (prompt edited)%n", shortHash(SpecContentHash.of(reworded)));
		System.out.printf("  content hash (regressed): %s  (behavior changed)%n",
				shortHash(SpecContentHash.of(regressed)));
		System.out.println();

		System.out.println("  [0] incubation -> active (clean baseline, no drift)");
		printReport(this.lifecycle.assess(LifecycleSpec.SPEC_ID, LifecycleState.INCUBATING, healthyHash, healthy, 1.0,
				1.0));

		System.out.println("  [1] content drift (prompt edited, behavior unchanged)");
		printReport(this.lifecycle.assess(LifecycleSpec.SPEC_ID, LifecycleState.ACTIVE, healthyHash, reworded, 1.0,
				1.0));

		System.out.println("  [2] score drift / regression (behavior degraded)");
		printReport(this.lifecycle.assess(LifecycleSpec.SPEC_ID, LifecycleState.ACTIVE, healthyHash, regressed, 1.0,
				0.0));

		System.out.println("  [3] stable (no drift)");
		printReport(this.lifecycle.assess(LifecycleSpec.SPEC_ID, LifecycleState.ACTIVE, healthyHash, healthy, 1.0,
				1.0));
	}

	/** The endpoint-gated half: the real eval pass-rate of healthy vs. regressed. */
	private void runLiveReEval() {
		if (this.endpoint.configError() != null) {
			System.out.println("-- Live re-eval --");
			System.out.println("  skipped (no model endpoint configured); the deterministic");
			System.out.println("  walkthrough above covers the mechanism.");
			return;
		}

		System.out.println("-- Live re-eval (real model) --");
		LiveEvalRunner eval = new LiveEvalRunner(this.platform);
		var tasks = LifecycleSpec.tasks();
		double baseline = eval.evaluate(LifecycleSpec.healthy(), tasks);
		double current = eval.evaluate(LifecycleSpec.regressed(), tasks);
		System.out.printf("  healthy   eval pass-rate: %.0f%%%n", baseline * 100);
		System.out.printf("  regressed eval pass-rate: %.0f%%%n", current * 100);
		System.out.println();

		String recordedHash = SpecContentHash.of(LifecycleSpec.healthy());
		DriftReport report = this.lifecycle.assess(LifecycleSpec.SPEC_ID, LifecycleState.ACTIVE, recordedHash,
				LifecycleSpec.regressed(), baseline, current);
		printReport(report);
		System.out.println();
		System.out.println((report.drifted())
				? "RESULT: the proactive re-eval caught the drift — the regressed spec is flagged/retired."
				: "RESULT: no drift detected this run (LLM variance); the loop ran end-to-end.");
	}

	private void printReport(DriftReport report) {
		System.out.printf("    %-8s -> %-8s  (content-drift=%s, score %.2f -> %.2f)%n", report.before(), report.after(),
				report.contentDrift(), report.baselineScore(), report.currentScore());
		System.out.println("    " + report.verdict());
	}

	private String shortHash(String hash) {
		return (hash.length() <= 12) ? hash : hash.substring(0, 12) + "...";
	}

}
