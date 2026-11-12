package com.embabel.smith.nlcompile;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.spec.compile.AgentCompilationResult;
import com.embabel.agent.spec.compile.DefaultNaturalLanguageCompiler;
import com.embabel.agent.spec.model.StepSpecContext;
import com.embabel.common.ai.model.LlmOptions;

/**
 * De-risks the NL-&gt;draft path (POC 2): turns a plain-language description into an
 * {@link AgentCompilationResult} (actions + exported goal + intermediate types + issues to review)
 * via Embabel's {@link DefaultNaturalLanguageCompiler}.
 *
 * <p>The result is a <em>draft</em>, not a finished spec: when the description is ambiguous the
 * compiler returns a draft together with explicit issues ({@code getErrors()}) to review, rather
 * than silently guessing.
 */
public class NlCompileRunner {

    private final DefaultNaturalLanguageCompiler compiler;
    private final StepSpecContextFactory contextFactory;
    private final DataDictionary dataDictionary;

    public NlCompileRunner(Ai ai, DataDictionary dataDictionary, LlmOptions llmOptions) {
        this.compiler = new DefaultNaturalLanguageCompiler(ai, llmOptions);
        this.contextFactory = new StepSpecContextFactory();
        this.dataDictionary = dataDictionary;
    }

    public AgentCompilationResult compile(String agentName, String description) {
        StepSpecContext context = contextFactory.forAgent(agentName, dataDictionary);
        return compiler.compileAgent(agentName, description, context);
    }
}
