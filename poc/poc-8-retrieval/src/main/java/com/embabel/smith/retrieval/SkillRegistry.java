package com.embabel.smith.retrieval;

import java.util.Comparator;
import java.util.List;

/**
 * A minimal SkillRegistry: an in-memory index over {@link SkillEntry} metadata with two operations
 * that expose the retrieval problem doc 03 §2.1 and doc 02 caveat #1 describe.
 *
 * <ul>
 *   <li>{@link #typeCompatibleCount} — the <em>shadowing factor</em>: how many skills the type-based
 *       GOAP planner alone would have to consider for a task. When skills share input/output types,
 *       this is large and type matching cannot disambiguate.</li>
 *   <li>{@link #retrieve} — the semantic pre-filter: rank skills by relevance to the task and return
 *       the top-k, so the planner only sees a narrow, on-topic candidate set.</li>
 * </ul>
 */
public class SkillRegistry {

    private final List<SkillEntry> entries;

    public SkillRegistry(List<SkillEntry> entries) {
        this.entries = List.copyOf(entries);
    }

    public int size() {
        return entries.size();
    }

    /**
     * The shadowing factor: the number of skills whose types match a task needing
     * {@code inputTypes -> outputType}. This is the candidate set a pure type-based (GOAP) router
     * would face before any semantic narrowing.
     */
    public int typeCompatibleCount(List<String> inputTypes, String outputType) {
        return (int) entries.stream()
                .filter(e -> e.inputTypes().containsAll(inputTypes) && e.outputType().equals(outputType))
                .count();
    }

    /**
     * The semantic pre-filter: rank skills by lexical similarity between the task and each skill's
     * {@code name + description}, returning the top {@code k}.
     */
    public List<SkillEntry> retrieve(String query, int k) {
        List<String> queryTokens = LexicalSimilarity.tokenize(query);
        return entries.stream()
                .map(entry -> new Scored(entry, score(queryTokens, entry)))
                .sorted(Comparator.comparingDouble((Scored s) -> s.score).reversed())
                .limit(Math.max(0, k))
                .map(Scored::entry)
                .toList();
    }

    private double score(List<String> queryTokens, SkillEntry entry) {
        String text = entry.name() + " " + entry.description();
        return LexicalSimilarity.cosine(
                LexicalSimilarity.tf(queryTokens),
                LexicalSimilarity.tf(LexicalSimilarity.tokenize(text)));
    }

    private record Scored(SkillEntry entry, double score) {
    }
}
