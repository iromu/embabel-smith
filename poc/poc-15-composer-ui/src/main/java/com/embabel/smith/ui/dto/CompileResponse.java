package com.embabel.smith.ui.dto;

import com.embabel.agent.spec.compile.AgentCompilationResult;
import com.embabel.agent.spec.model.PromptedActionSpec;
import com.embabel.smith.common.model.DraftQuality;

import java.util.List;

/**
 * The result of compiling a natural-language description into a draft: the goal shape, the actions,
 * the {@link DraftQuality} verdict, and any issues to review. {@link #error} is non-null when the
 * request could not be compiled (no endpoint configured, blank description) — in which case the
 * draft fields are empty.
 */
public record CompileResponse(
        String error,
        String goalName,
        String goalOutputType,
        boolean goalExport,
        List<ActionInfo> actions,
        String qualityVerdict,
        boolean degenerate,
        String qualityReason,
        List<String> issues) {

    public static CompileResponse noEndpoint(String error) {
        return new CompileResponse(error, null, null, false, List.of(), null, false, null, List.of());
    }

    public static CompileResponse badRequest(String error) {
        return new CompileResponse(error, null, null, false, List.of(), null, false, null, List.of());
    }

    public static CompileResponse from(AgentCompilationResult result) {
        String goalOutput = result.getGoal() == null ? null : result.getGoal().getOutputTypeName();
        List<String> actionOutputs = result.getActions().stream()
                .map(PromptedActionSpec::getOutputTypeName)
                .toList();
        DraftQuality.Assessment quality = DraftQuality.assess(goalOutput, actionOutputs);
        List<ActionInfo> actions = result.getActions().stream()
                .map(a -> new ActionInfo(a.getName(), List.copyOf(a.getInputTypeNames()),
                        a.getOutputTypeName()))
                .toList();
        List<String> issues = result.getErrors().stream()
                .map(e -> e.getMessage() + " [" + e.getSource() + "]")
                .toList();
        return new CompileResponse(
                null,
                result.getGoal() == null ? null : result.getGoal().getName(),
                goalOutput,
                result.getGoal() != null && result.getGoal().getExport(),
                actions,
                quality.verdict().name(),
                quality.isDegenerate(),
                quality.reason(),
                issues);
    }
}
