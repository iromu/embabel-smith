package com.embabel.smith.studio.generate;

/**
 * The model-based process extraction: a corpus of business-process documents in, a business-process
 * description (markdown) out. It is a second prompt run against the same {@link FlowPromptCaller}
 * seam as generation, but with the {@code business_process_discovery} prompt and a plain-text
 * answer (no JSON pack to parse). The extracted description is the blueprint that generation
 * consumes.
 */
public class ProcessExtractor {

    private final FlowPromptCaller caller;
    private final String template;

    public ProcessExtractor(FlowPromptCaller caller, String template) {
        this.caller = caller;
        this.template = template;
    }

    /** Runs the discovery prompt over the docs corpus, returning the business-process description. */
    public String extract(String corpus) {
        String user = "Below are the business-process documents for an application. Reconstruct the "
                + "business processes and workflows they describe.\n\n" + corpus;
        return caller.call(template, user);
    }
}
