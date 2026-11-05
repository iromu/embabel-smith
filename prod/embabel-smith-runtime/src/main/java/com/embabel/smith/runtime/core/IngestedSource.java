package com.embabel.smith.runtime.core;

import com.embabel.agent.core.Agent;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.skillsource.ResolvedSkillSource;

import java.util.List;

/**
 * The outcome of ingesting a resolved skill source into the platform: the loaded packs, the
 * deployed agent holding all of their goals, and the source they came from.
 */
public record IngestedSource(ResolvedSkillSource source, List<SkillPack> packs, Agent agent) {

    public IngestedSource {
        packs = List.copyOf(packs);
    }

    /** The goal names exported by the ingested packs. */
    public List<String> goalNames() {
        return packs.stream().map(pack -> pack.goal().name()).toList();
    }

    /** The pack that exports the named goal. */
    public SkillPack packForGoal(String goalName) {
        return packs.stream()
                .filter(pack -> goalName.equals(pack.goal().name()))
                .findFirst()
                .orElseThrow(() -> new GoalNotFoundException(goalName, goalNames()));
    }
}
