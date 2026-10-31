package com.embabel.smith.disclosure;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.SkillPack;

import java.util.StringJoiner;

/**
 * Measures the context a skill's plan and execution carry (POC 11).
 *
 * <p>Two distinct contexts, mirroring Embabel's progressive disclosure (doc 02 §2):
 * <ul>
 *   <li><b>Plan context</b> — what the GOAP planner loads: the goal and each action's
 *       name/description/types. It never includes the action prompt or any bundled reference, so it
 *       is bounded regardless of how much reference material the skill ships.</li>
 *   <li><b>Execution context</b> — the prompt an action sends to the LLM. Under <em>eager</em>
 *       disclosure the bundled reference is inlined here (grows with the reference); under
 *       <em>progressive</em> disclosure it is fetched on demand, so the base prompt stays small.</li>
 * </ul>
 */
public class PlanContextMeter {

    /** The metadata the planner loads: goal + action + type names and descriptions. */
    public String planContext(SkillPack pack) {
        StringBuilder sb = new StringBuilder();
        sb.append("goal: ").append(pack.goal().name())
                .append(" — ").append(pack.goal().description()).append('\n');
        for (ActionStepSpec step : pack.steps()) {
            sb.append("action: ").append(step.name())
                    .append(" [").append(join(step.inputTypeNames())).append(" -> ")
                    .append(step.outputTypeName()).append("] ")
                    .append(step.description()).append('\n');
        }
        for (DynamicTypeDef type : pack.types()) {
            sb.append("type: ").append(type.name()).append(" — ").append(type.description()).append('\n');
        }
        return sb.toString();
    }

    public int planContextChars(SkillPack pack) {
        return planContext(pack).length();
    }

    /** Execution context under progressive disclosure: just the base prompt (reference on demand). */
    public int progressivePromptChars(SkillPack pack) {
        return pack.steps().stream().mapToInt(s -> length(s.prompt())).sum();
    }

    /** Execution context under eager disclosure: the base prompt with the reference inlined. */
    public int eagerPromptChars(SkillPack pack, String reference) {
        return pack.steps().stream().mapToInt(s -> length(s.prompt()) + reference.length()).sum();
    }

    /** A rough token estimate (about 4 chars/token for English-ish text). */
    public static int estimateTokens(int chars) {
        return chars / 4;
    }

    private static int length(String s) {
        return s == null ? 0 : s.length();
    }

    private static String join(java.util.List<String> names) {
        StringJoiner joiner = new StringJoiner(", ");
        names.forEach(joiner::add);
        return joiner.toString();
    }
}
