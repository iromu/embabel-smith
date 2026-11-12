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

package com.embabel.smith.ui.dto;

import java.util.List;

import com.embabel.agent.spec.compile.AgentCompilationResult;
import com.embabel.agent.spec.model.PromptedActionSpec;
import com.embabel.smith.common.model.DraftQuality;

/**
 * The result of compiling a natural-language description into a draft: the goal shape,
 * the actions, the {@link DraftQuality} verdict, and any issues to review. {@link #error}
 * is non-null when the request could not be compiled (no endpoint configured, blank
 * description) — in which case the draft fields are empty.
 *
 * @param error the error message when the request could not be compiled
 * @param goalName the compiled goal's name
 * @param goalOutputType the compiled goal's output type
 * @param goalExport whether the goal is exported
 * @param actions the compiled actions
 * @param qualityVerdict the DraftQuality verdict
 * @param degenerate whether the draft is degenerate
 * @param qualityReason the reason for the quality verdict
 * @param issues the issues to review
 * @author Ivan Rodriguez
 */
public record CompileResponse(String error, String goalName, String goalOutputType, boolean goalExport,
		List<ActionInfo> actions, String qualityVerdict, boolean degenerate, String qualityReason,
		List<String> issues) {

	public static CompileResponse noEndpoint(String error) {
		return new CompileResponse(error, null, null, false, List.of(), null, false, null, List.of());
	}

	public static CompileResponse badRequest(String error) {
		return new CompileResponse(error, null, null, false, List.of(), null, false, null, List.of());
	}

	public static CompileResponse from(AgentCompilationResult result) {
		String goalOutput = (result.getGoal() == null) ? null : result.getGoal().getOutputTypeName();
		List<String> actionOutputs = result.getActions().stream().map(PromptedActionSpec::getOutputTypeName).toList();
		DraftQuality.Assessment quality = DraftQuality.assess(goalOutput, actionOutputs);
		List<ActionInfo> actions = result.getActions()
			.stream()
			.map((a) -> new ActionInfo(a.getName(), List.copyOf(a.getInputTypeNames()), a.getOutputTypeName()))
			.toList();
		List<String> issues = result.getErrors()
			.stream()
			.map((e) -> e.getMessage() + " [" + e.getSource() + "]")
			.toList();
		return new CompileResponse(null, (result.getGoal() == null) ? null : result.getGoal().getName(), goalOutput,
				result.getGoal() != null && result.getGoal().getExport(), actions, quality.verdict().name(),
				quality.isDegenerate(), quality.reason(), issues);
	}
}
