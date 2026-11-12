package com.embabel.smith.nlcompile;

import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.spec.model.StepSpecContext;

import java.util.List;

/**
 * Builds the {@link StepSpecContext} the natural-language compiler needs: the platform's data
 * dictionary (the types it may reference) plus the available tool groups, tools and LLM
 * references. The POC agent specs are pure-LLM, so the tool/tool/reference lists are empty.
 */
public class StepSpecContextFactory {

    public StepSpecContext forAgent(String agentName, DataDictionary dataDictionary) {
        return new StepSpecContext(agentName, dataDictionary, List.of(), List.of(), List.of());
    }
}
