package com.embabel.smith.builder.core;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.spec.compile.AgentCompilationResult;
import com.embabel.agent.spec.compile.DefaultNaturalLanguageCompiler;
import com.embabel.agent.spec.model.StepSpecContext;
import com.embabel.common.ai.model.LlmOptions;

import java.util.List;

/**
 * The production compile step: wraps Embabel's {@link DefaultNaturalLanguageCompiler}.
 * Endpoint-gated — this is the only step in the build that calls the model.
 */
public class DefaultSkillCompiler implements SkillCompiler {

    private final Ai ai;
    private final LlmOptions options;

    public DefaultSkillCompiler(Ai ai) {
        this(ai, LlmOptions.withDefaults());
    }

    public DefaultSkillCompiler(Ai ai, LlmOptions options) {
        this.ai = ai;
        this.options = options;
    }

    @Override
    public AgentCompilationResult compile(String agentName, String description, DataDictionary dataDictionary) {
        DefaultNaturalLanguageCompiler compiler = new DefaultNaturalLanguageCompiler(ai, options);
        StepSpecContext context = new StepSpecContext(agentName, dataDictionary, List.of(), List.of(), List.of());
        return compiler.compileAgent(agentName, description, context);
    }
}
