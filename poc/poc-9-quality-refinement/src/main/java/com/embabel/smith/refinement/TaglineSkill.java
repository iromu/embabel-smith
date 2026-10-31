package com.embabel.smith.refinement;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.model.SkillPackManifest;
import com.embabel.smith.common.samples.SampleSkillPacks;

import java.util.List;
import java.util.Locale;

/**
 * The LLM-authored skill under refinement (POC 9): a tagline writer whose prompt was authored
 * without the one requirement its eval tasks check — that the tagline include the brand name. The
 * weak prompt even tells the model to keep it brand-free, so the eval reliably fails before the
 * rewrite and passes after.
 */
public final class TaglineSkill {

    public static final String STEP_NAME = "writeTagline";

    private TaglineSkill() {
    }

    /** The weak, LLM-authored skill. */
    public static SkillPack weak() {
        ActionStepSpec write = new ActionStepSpec(
                "action",
                STEP_NAME,
                "Write a marketing tagline for a coffee brand",
                List.of(SampleSkillPacks.USER_INPUT),
                SampleSkillPacks.BACKGROUND_MESSAGE,
                """
                Write a short, evocative marketing tagline (one line) for the coffee brand described below.
                Keep it generic and image-focused; do not include the brand name.

                Brand brief: {{userInput.content}}
                """,
                null, null, null, null, null, null, List.of());
        return new SkillPack(
                new SkillPackManifest("tagline-writer", "0.1.0"),
                GoalStepSpec.exportedGoal("taglineDone", "A tagline has been written",
                        SampleSkillPacks.BACKGROUND_MESSAGE),
                List.of(write), List.of());
    }

    /** The eval tasks: the tagline must include the brand name "Nimbus". */
    public static List<SkillEvalTask> tasks() {
        PredicateContains nimbus = new PredicateContains("Nimbus");
        return List.of(
                new SkillEvalTask("The brand is Nimbus, a sustainable single-origin coffee roaster.",
                        "the tagline must include the brand name 'Nimbus'", nimbus),
                new SkillEvalTask("The brand is Nimbus, focused on office coffee subscriptions.",
                        "the tagline must include the brand name 'Nimbus'", nimbus));
    }

    /** Case-insensitive "contains" acceptance check. */
    private record PredicateContains(String needle) implements java.util.function.Predicate<String> {
        @Override
        public boolean test(String output) {
            return output != null && output.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
        }
    }
}
