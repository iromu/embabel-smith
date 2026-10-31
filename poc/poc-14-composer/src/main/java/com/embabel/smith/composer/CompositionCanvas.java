package com.embabel.smith.composer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The composition canvas (doc 03 §2.2): a directed graph whose nodes are skills ({@link SkillNode})
 * and whose edges say "skill A's output feeds skill B's input". This is the headless model behind a
 * visual skill-chain editor — the drag-and-drop "snap" is the edge type-check in
 * {@link #validate()}, and the exported plan is the ordered path from the entry skill to the exit
 * skill returned by {@link #chain()}.
 */
public final class CompositionCanvas {

    /** Short name of the framework entry type a composed chain must start from. */
    public static final String USER_INPUT = "UserInput";
    /** Short name of the framework output type a composed chain must end in. */
    public static final String BACKGROUND_MESSAGE = "BackgroundMessage";

    private final Map<String, SkillNode> nodes = new LinkedHashMap<>();
    private final List<String[]> edges = new ArrayList<>(); // {fromId, toId}

    /** Adds a skill to the canvas. */
    public void addSkill(SkillNode skill) {
        if (nodes.containsKey(skill.id())) {
            throw new IllegalArgumentException("Skill already on the canvas: " + skill.id());
        }
        nodes.put(skill.id(), skill);
    }

    /** Connects {@code fromId}'s output to {@code toId}'s input (a drag-and-drop edge). */
    public void connect(String fromId, String toId) {
        if (!nodes.containsKey(fromId) || !nodes.containsKey(toId)) {
            throw new IllegalArgumentException(
                    "Cannot connect unknown skills: " + fromId + " -> " + toId);
        }
        edges.add(new String[]{fromId, toId});
    }

    public List<SkillNode> skills() {
        return new ArrayList<>(nodes.values());
    }

    public List<String[]> edges() {
        return Collections.unmodifiableList(edges);
    }

    /**
     * Validates the canvas as a single, executable skill chain. Returns a list of human-readable
     * issues (empty when the canvas is a valid chain).
     */
    public List<String> validate() {
        List<String> issues = new ArrayList<>();

        // Every edge must reference existing, type-compatible skills.
        for (String[] edge : edges) {
            SkillNode from = nodes.get(edge[0]);
            SkillNode to = nodes.get(edge[1]);
            if (from == null || to == null) {
                issues.add("edge " + edge[0] + " -> " + edge[1] + " references a missing skill");
                continue;
            }
            if (!compatible(from, to)) {
                issues.add("edge " + from.id() + " -> " + to.id() + " is type-incompatible: '"
                        + from.outputType() + "' is not one of " + to.id() + "'s inputs "
                        + to.externalInputTypes());
            }
        }

        List<SkillNode> entries = entries();
        List<SkillNode> exits = exits();
        if (entries.size() != 1) {
            issues.add("expected exactly one entry skill (input UserInput), found " + entries.size());
        }
        if (exits.size() != 1) {
            issues.add("expected exactly one exit skill (output BackgroundMessage), found " + exits.size());
        }

        // Structure: every non-entry skill has exactly one incoming edge; every non-exit skill has
        // exactly one outgoing edge; and the path covers every skill exactly once.
        Map<String, Integer> inDeg = new LinkedHashMap<>();
        Map<String, Integer> outDeg = new LinkedHashMap<>();
        nodes.keySet().forEach(id -> {
            inDeg.put(id, 0);
            outDeg.put(id, 0);
        });
        for (String[] edge : edges) {
            outDeg.merge(edge[0], 1, Integer::sum);
            inDeg.merge(edge[1], 1, Integer::sum);
        }
        SkillNode entry = entries.size() == 1 ? entries.get(0) : null;
        SkillNode exit = exits.size() == 1 ? exits.get(0) : null;
        for (SkillNode node : nodes.values()) {
            boolean isEntry = entry != null && node.id().equals(entry.id());
            boolean isExit = exit != null && node.id().equals(exit.id());
            int expectedIn = isEntry ? 0 : 1;
            int expectedOut = isExit ? 0 : 1;
            if (inDeg.get(node.id()) != expectedIn) {
                issues.add(node.id() + " has " + inDeg.get(node.id()) + " incoming edge(s), expected "
                        + expectedIn);
            }
            if (outDeg.get(node.id()) != expectedOut) {
                issues.add(node.id() + " has " + outDeg.get(node.id()) + " outgoing edge(s), expected "
                        + expectedOut);
            }
        }

        if (issues.isEmpty()) {
            Set<String> visited = new LinkedHashSet<>(chain().stream().map(SkillNode::id).toList());
            if (visited.size() != nodes.size()) {
                issues.add("the chain does not cover every skill on the canvas");
            }
        }
        return issues;
    }

    /**
     * The ordered path from the entry skill to the exit skill — the plan the exported composite will
     * run.
     *
     * @throws IllegalStateException if the canvas is not a valid single chain (see {@link #validate()})
     */
    public List<SkillNode> chain() {
        List<SkillNode> entries = entries();
        List<SkillNode> exits = exits();
        if (entries.size() != 1 || exits.size() != 1) {
            throw new IllegalStateException(
                    "Canvas is not a single chain (entries=" + entries.size()
                            + ", exits=" + exits.size() + "). Validate first: " + validate());
        }
        SkillNode current = entries.get(0);
        List<SkillNode> path = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        while (current != null) {
            if (!seen.add(current.id())) {
                throw new IllegalStateException("Cycle detected at " + current.id());
            }
            path.add(current);
            current = next(current);
        }
        if (path.get(path.size() - 1) != exits.get(0)) {
            throw new IllegalStateException("Chain does not reach the exit skill " + exits.get(0).id());
        }
        return path;
    }

    /** A compact description of the chain, e.g. {@code extractTopic -> writeExplainer}. */
    public String describeChain() {
        return chain().stream().map(SkillNode::id).reduce((a, b) -> a + " -> " + b).orElse("<empty>");
    }

    private SkillNode next(SkillNode node) {
        for (String[] edge : edges) {
            if (edge[0].equals(node.id())) {
                return nodes.get(edge[1]);
            }
        }
        return null;
    }

    private boolean compatible(SkillNode from, SkillNode to) {
        Set<String> targetInputs = new LinkedHashSet<>();
        to.externalInputTypes().forEach(t -> targetInputs.add(shortName(t)));
        return targetInputs.contains(shortName(from.outputType()));
    }

    private List<SkillNode> entries() {
        List<SkillNode> result = new ArrayList<>();
        for (SkillNode node : nodes.values()) {
            Set<String> inputs = new LinkedHashSet<>();
            node.externalInputTypes().forEach(t -> inputs.add(shortName(t)));
            if (inputs.contains(USER_INPUT)) {
                result.add(node);
            }
        }
        return result;
    }

    private List<SkillNode> exits() {
        List<SkillNode> result = new ArrayList<>();
        for (SkillNode node : nodes.values()) {
            if (BACKGROUND_MESSAGE.equals(shortName(node.outputType()))) {
                result.add(node);
            }
        }
        return result;
    }

    static String shortName(String typeName) {
        int idx = typeName.lastIndexOf('.');
        return idx >= 0 ? typeName.substring(idx + 1) : typeName;
    }
}
