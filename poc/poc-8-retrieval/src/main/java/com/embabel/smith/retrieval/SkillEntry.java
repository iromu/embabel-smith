package com.embabel.smith.retrieval;

import java.util.List;

/**
 * A lightweight index entry for a skill in the registry — the metadata the pre-filter needs to
 * route a task, without loading the skill's full action chain (progressive disclosure: only this
 * metadata is held hot; the pack is read on demand once a skill is selected).
 *
 * @param id          stable skill id
 * @param name        skill name
 * @param description what the skill does — the text that gets matched against the task
 * @param inputTypes  the input type names the skill's action consumes
 * @param outputType  the output type name the skill produces
 */
public record SkillEntry(String id, String name, String description, List<String> inputTypes, String outputType) {

    public SkillEntry {
        inputTypes = inputTypes == null ? List.of() : List.copyOf(inputTypes);
    }
}
