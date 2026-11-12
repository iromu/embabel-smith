package com.embabel.smith.ui;

import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.composer.SpecNode;
import com.embabel.smith.retrieval.SpecEntry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The set of specs the canvas can place: every valid, exported agent spec under the agent-specs
 * directory, exposed as a {@link SpecNode} (its name, external input types, and output type). This
 * is the "palette" the drag-and-drop editor offers.
 */
@Component
public class SpecCatalog {

    private final Map<String, SpecNode> specs = new LinkedHashMap<>();

    public SpecCatalog(@Value("${builder.agent-specs-dir:}") String dirOverride) {
        Path dir = resolveAgentSpecsDir(dirOverride);
        if (dir != null && Files.isDirectory(dir)) {
            try (var subs = Files.list(dir)) {
                for (Path sub : subs.filter(Files::isDirectory).toList()) {
                    loadIfAgentSpec(sub);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to list agent specs under " + dir, e);
            }
        }
        if (specs.isEmpty()) {
            throw new IllegalStateException(
                    "No agent specs found under " + dir
                            + "; point builder.agent-specs-dir at a directory of agent specs.");
        }
    }

    private void loadIfAgentSpec(Path sub) {
        if (!Files.isRegularFile(sub.resolve("goal.yml")) || !Files.isDirectory(sub.resolve("steps"))) {
            return;
        }
        try {
            AgentSpec pack = new AgentSpecReader().read(sub);
            if (pack.goal() == null || !pack.goal().isExported() || pack.name() == null) {
                return;
            }
            specs.put(pack.name(), new SpecNode(pack));
        } catch (Exception e) {
            // Skip directories that are not well-formed agent specs (e.g. nested samples).
        }
    }

    public Map<String, SpecNode> all() {
        return Collections.unmodifiableMap(specs);
    }

    public SpecNode get(String name) {
        return specs.get(name);
    }

    /**
     * The catalog as retrieval index entries (POC 8's {@link SpecEntry}) — the same metadata the
     * retrieval pre-filter (POC 8/12/17/20) scores against. The description is synthesized from the
     * goal and step descriptions plus the I/O types, so a natural-language query matches on meaning
     * words, not just type names.
     */
    public List<SpecEntry> retrievalEntries() {
        return specs.values().stream().map(this::toEntry).toList();
    }

    private SpecEntry toEntry(SpecNode node) {
        AgentSpec pack = node.pack();
        StringBuilder text = new StringBuilder(node.id());
        if (pack.goal() != null && pack.goal().description() != null) {
            text.append(' ').append(pack.goal().description());
        }
        pack.steps().forEach(step -> {
            if (step.description() != null) {
                text.append(' ').append(step.description());
            }
        });
        text.append(' ').append(String.join(" ", node.externalInputTypes()))
                .append(" ").append(node.outputType());
        return new SpecEntry(node.id(), node.id(), text.toString(),
                List.copyOf(node.externalInputTypes()), node.outputType());
    }

    /** Uses the override if given; otherwise walks up from the working dir to find agent specs. */
    private Path resolveAgentSpecsDir(String override) {
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        Path dir = Path.of(System.getProperty("user.dir"));
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve("agent-specs/compose-extract"))) {
                return dir.resolve("agent-specs");
            }
        }
        return Path.of("agent-specs");
    }
}
