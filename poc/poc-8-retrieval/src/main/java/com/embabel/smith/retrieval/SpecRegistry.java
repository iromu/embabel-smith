package com.embabel.smith.retrieval;

import java.util.Comparator;
import java.util.List;

/**
 * A minimal SpecRegistry: an in-memory index over {@link SpecEntry} metadata with two operations
 * that expose the retrieval problem doc 03 §2.1 and doc 02 caveat #1 describe.
 *
 * <ul>
 *   <li>{@link #typeCompatibleCount} — the <em>shadowing factor</em>: how many specs the type-based
 *       GOAP planner alone would have to consider for a task. When specs share input/output types,
 *       this is large and type matching cannot disambiguate.</li>
 *   <li>{@link #retrieve} — the semantic pre-filter: rank specs by relevance to the task and return
 *       the top-k, so the planner only sees a narrow, on-topic candidate set.</li>
 * </ul>
 */
public class SpecRegistry {

    private final List<SpecEntry> entries;

    public SpecRegistry(List<SpecEntry> entries) {
        this.entries = List.copyOf(entries);
    }

    public int size() {
        return entries.size();
    }

    /**
     * The shadowing factor: the number of specs whose types match a task needing
     * {@code inputTypes -> outputType}. This is the candidate set a pure type-based (GOAP) router
     * would face before any semantic narrowing.
     */
    public int typeCompatibleCount(List<String> inputTypes, String outputType) {
        return (int) entries.stream()
                .filter(e -> e.inputTypes().containsAll(inputTypes) && e.outputType().equals(outputType))
                .count();
    }

    /**
     * The semantic pre-filter: rank specs by lexical similarity between the task and each spec's
     * {@code name + description}, returning the top {@code k}.
     */
    public List<SpecEntry> retrieve(String query, int k) {
        List<String> queryTokens = LexicalSimilarity.tokenize(query);
        return entries.stream()
                .map(entry -> new Scored(entry, score(queryTokens, entry)))
                .sorted(Comparator.comparingDouble((Scored s) -> s.score).reversed())
                .limit(Math.max(0, k))
                .map(Scored::entry)
                .toList();
    }

    private double score(List<String> queryTokens, SpecEntry entry) {
        String text = entry.name() + " " + entry.description();
        return LexicalSimilarity.cosine(
                LexicalSimilarity.tf(queryTokens),
                LexicalSimilarity.tf(LexicalSimilarity.tokenize(text)));
    }

    private record Scored(SpecEntry entry, double score) {
    }
}
