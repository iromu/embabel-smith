/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.innerloop;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.embabel.agent.skills.script.ScriptExecutionResult;
import com.embabel.agent.skills.script.SkillScript;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Runs a detection policy through POC 26's engine and reads its verdicts back.
 *
 * <p>
 * This is the actor's execution door and the only place in this package that opens a
 * script context. The engine is POC 26's, with its hardening and its limits untouched: a
 * generated policy gets the same wall-clock and statement bounds as any other skill
 * script, and a sandbox denial is an outcome the loop records rather than a crash it has
 * to survive. Design D9 keeps the referee on the other side of a process boundary from
 * this class.
 *
 * @author Ivan Rodriguez
 */
public final class PolicyRunner {

	private static final ObjectMapper JSON = new ObjectMapper();

	private final SkillScriptExecutionEngine engine;

	/**
	 * Create a runner.
	 * @param engine the script engine to run policies on — in this POC,
	 * {@code GraalJsSkillScriptExecutionEngine} with its default limits
	 */
	public PolicyRunner(SkillScriptExecutionEngine engine) {
		this.engine = engine;
	}

	/**
	 * Run one policy over a corpus split.
	 * @param policy the policy to run
	 * @param cases the split's lines, rendered to stdin with their labels stripped
	 * @param workDir where to write the policy's skill folder
	 * @return the run: verdicts when the policy answered, a diagnostic when it did not.
	 */
	public PolicyRun run(DetectionPolicy policy, List<TraceCase> cases, Path workDir) {
		SkillScript script = policy.toSkillScript(workDir);
		long started = System.nanoTime();
		ScriptExecutionResult result = this.engine.execute(script, List.of(), PolicyInput.stdinJson(cases), List.of());
		long took = (System.nanoTime() - started) / 1_000_000L;
		if (result instanceof ScriptExecutionResult.Denied denied) {
			return PolicyRun.denied(policy.name(), denied.getReason());
		}
		if (result instanceof ScriptExecutionResult.Failure failure) {
			return PolicyRun.failed(policy.name(), failure.getError(), took);
		}
		ScriptExecutionResult.Success success = (ScriptExecutionResult.Success) result;
		if (success.getExitCode() != 0) {
			return PolicyRun.failed(policy.name(),
					"policy exited with code " + success.getExitCode() + ": " + success.getStderr(), took);
		}
		return readVerdicts(policy.name(), success.getStdout(), took);
	}

	private PolicyRun readVerdicts(String policyName, String stdout, long took) {
		List<PolicyRun.Verdict> verdicts = new ArrayList<>();
		for (String line : stdout.split("\\R")) {
			if (line.isBlank()) {
				continue;
			}
			try {
				JsonNode node = JSON.readTree(line);
				JsonNode runId = node.get("runId");
				JsonNode defect = node.get("defect");
				if (runId == null || defect == null || !defect.isBoolean()) {
					return PolicyRun.failed(policyName,
							"policy printed a verdict without a runId or a boolean defect: " + line, took);
				}
				verdicts.add(new PolicyRun.Verdict(runId.asText(), defect.asBoolean(), reasons(node)));
			}
			catch (IOException ex) {
				return PolicyRun.failed(policyName, "policy printed something that is not a verdict: " + line, took);
			}
		}
		if (verdicts.isEmpty()) {
			return PolicyRun.failed(policyName, "policy printed no verdicts at all", took);
		}
		return PolicyRun.ran(policyName, verdicts, took);
	}

	private static List<String> reasons(JsonNode node) {
		JsonNode reasons = node.get("reasons");
		if (reasons == null || !reasons.isArray()) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		reasons.forEach((reason) -> out.add(reason.asText()));
		return List.copyOf(out);
	}

}
