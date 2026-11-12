package com.embabel.smith.retrieval;

import java.util.List;

/**
 * A lightweight index entry for a spec in the registry — the metadata the pre-filter needs to
 * route a task, without loading the spec's full action chain (progressive disclosure: only this
 * metadata is held hot; the pack is read on demand once a spec is selected).
 *
 * @param id          stable spec id
 * @param name        spec name
 * @param description what the spec does — the text that gets matched against the task
 * @param inputTypes  the input type names the spec's action consumes
 * @param outputType  the output type name the spec produces
 */
public record SpecEntry(String id, String name, String description, List<String> inputTypes, String outputType) {

    public SpecEntry {
        inputTypes = inputTypes == null ? List.of() : List.copyOf(inputTypes);
    }
}
