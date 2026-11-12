package com.embabel.smith.refinement;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline check for the re-validation half of the rewrite (POC 9): splicing a revised prompt into
 * the named step produces a re-validated draft, and the weak spec's template variables still bind.
 */
class PromptRewriterTest {

    private final PromptRewriter rewriter = new PromptRewriter();

    @Test
    void theWeakSpecIsWellFormed() {
        assertThat(new com.embabel.smith.common.validation.AgentSpecValidator().validate(TaglineSpec.weak()).isValid())
                .isTrue();
    }

    @Test
    void applyRevisedPromptSplicesIntoTheNamedStepAndRevalidates() {
        var pack = TaglineSpec.weak();
        String revised = "Write a tagline that includes the brand name. Brand brief: {{userInput.content}}";

        PromptRewriter.RewriteResult result = rewriter.applyRevisedPrompt(pack, TaglineSpec.STEP_NAME, revised);

        assertThat(result.valid()).isTrue();
        assertThat(result.revisedPack().step(TaglineSpec.STEP_NAME).prompt()).isEqualTo(revised);
        // the original pack is not mutated
        assertThat(pack.step(TaglineSpec.STEP_NAME).prompt()).isNotEqualTo(revised);
    }

    @Test
    void aRevisedPromptThatDropsTheTemplateVariableFailsValidation() {
        var pack = TaglineSpec.weak();
        // references an undeclared variable -> the validator must flag it
        PromptRewriter.RewriteResult result =
                rewriter.applyRevisedPrompt(pack, TaglineSpec.STEP_NAME, "Write: {{notABoundVar}}");
        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).isNotEmpty();
    }
}
