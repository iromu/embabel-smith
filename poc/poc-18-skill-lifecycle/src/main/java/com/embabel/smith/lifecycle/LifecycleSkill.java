package com.embabel.smith.lifecycle;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.model.SkillPackManifest;
import com.embabel.smith.common.samples.SampleSkillPacks;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * The demo skill for POC 18: a tagline writer with three variants that differ <em>only in
 * prompt</em> — same goal, same I/O types, so any drift is attributable to the prompt/behavior:
 *
 * <ul>
 *   <li><em>healthy</em> — the prompt requires the brand name, so its eval passes (~1.0);</li>
 *   <li><em>reworded</em> — the healthy prompt with a cosmetic edit (behavior unchanged, eval still
 *       passes, but the content hash differs → pure <em>content drift</em>);</li>
 *   <li><em>regressed</em> — the prompt forbids the brand name, so its eval fails (~0.0) → content
 *       and score drift, the "skill silently breaks" scenario.</li>
 * </ul>
 */
public final class LifecycleSkill {

    public static final String SKILL_ID = "tagline-writer";
    private static final String ACCEPTANCE = "the tagline must include the brand name 'Nimbus'";

    private LifecycleSkill() {
    }

    public static SkillPack healthy() {
        return pack("Write a short marketing tagline that explicitly includes the brand name.\n\n"
                + "Brand brief: {{userInput.content}}");
    }

    public static SkillPack reworded() {
        return pack("Write a brief marketing tagline that explicitly includes the brand name.\n\n"
                + "Brand brief: {{userInput.content}}");
    }

    public static SkillPack regressed() {
        return pack("Write a short, generic marketing tagline. Do NOT include the brand name.\n\n"
                + "Brand brief: {{userInput.content}}");
    }

    private static SkillPack pack(String prompt) {
        ActionStepSpec write = ActionStepSpec.action(
                "writeTagline",
                "Write a marketing tagline for a coffee brand",
                List.of(SampleSkillPacks.USER_INPUT),
                SampleSkillPacks.BACKGROUND_MESSAGE,
                prompt);
        return new SkillPack(
                new SkillPackManifest(SKILL_ID, "1.0.0"),
                GoalStepSpec.exportedGoal("taglineDone", "A tagline has been written",
                        SampleSkillPacks.BACKGROUND_MESSAGE),
                List.of(write), List.of());
    }

    /** The eval tasks: the tagline must include the brand name "Nimbus". */
    public static List<SkillEvalTask> tasks() {
        Predicate<String> containsNimbus =
                out -> out != null && out.toLowerCase(Locale.ROOT).contains("nimbus");
        return List.of(
                new SkillEvalTask("The brand is Nimbus, a sustainable single-origin coffee roaster.",
                        ACCEPTANCE, containsNimbus),
                new SkillEvalTask("The brand is Nimbus, focused on office coffee subscriptions.",
                        ACCEPTANCE, containsNimbus));
    }
}
