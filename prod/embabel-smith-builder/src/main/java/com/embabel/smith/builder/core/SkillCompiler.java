package com.embabel.smith.builder.core;

import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.spec.compile.AgentCompilationResult;

/**
 * The seam for the build's compile step: turns a natural-language description into an
 * {@link AgentCompilationResult}. The production implementation ({@link DefaultSkillCompiler})
 * calls the model; offline tests inject a fake that returns a hand-built result, so the whole
 * pipeline is verifiable without a live model.
 */
public interface SkillCompiler {
    AgentCompilationResult compile(String agentName, String description, DataDictionary dataDictionary);
}
