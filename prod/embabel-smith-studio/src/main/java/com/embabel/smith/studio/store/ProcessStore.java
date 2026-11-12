package com.embabel.smith.studio.store;

import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.model.AgentSpec;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The durable store for generated agent specs and discovery history (task 5.2). Rooted at a
 * configurable directory, it outlives a run so previously generated packs can be listed and
 * reloaded and prior discoveries reviewed.
 *
 * <p>Saved packs are full pack directories written by the shared {@link AgentSpecWriter} under
 * {@code packs/<name>/}; listing derives each record by reading the pack (manifest name, goal,
 * step count, directory mtime), so there is no separate index to drift from. Discovery history is
 * an append-only {@code discovery-history.jsonl} — one record per successful discovery.
 */
public class ProcessStore {

    /** A saved pack's listing metadata. */
    public record PackRecord(String name, String goal, int steps, String savedAt) {
    }

    /** A recorded discovery. */
    public record DiscoveryRecord(String timestamp, String sourceType, String location, int docCount) {
    }

    private static final String HISTORY_FILE = "discovery-history.jsonl";

    private final Path root;
    private final AgentSpecWriter writer = new AgentSpecWriter();
    private final AgentSpecReader reader = new AgentSpecReader();
    private final ObjectMapper json = new ObjectMapper();

    public ProcessStore(Path root) {
        this.root = root;
    }

    public Path root() {
        return root;
    }

    // ---- Packs --------------------------------------------------------------

    /** Writes the pack to the store and returns its listing record. */
    public PackRecord savePack(AgentSpec pack) {
        String name = requireName(pack);
        Path dir = packsDir().resolve(name);
        try {
            writer.write(pack, dir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save pack '" + name + "': " + e.getMessage(), e);
        }
        return recordOf(dir, pack);
    }

    /** The saved packs, most recently saved first. */
    public List<PackRecord> listPacks() {
        Path dir = packsDir();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Path> candidates;
        try (Stream<Path> subs = Files.list(dir)) {
            candidates = subs
                    .filter(Files::isDirectory)
                    .filter(p -> Files.isRegularFile(p.resolve("manifest.yml")))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list saved packs: " + e.getMessage(), e);
        }
        return candidates.stream()
                .map(this::recordOf)
                .sorted(Comparator.comparing(ProcessStore.PackRecord::savedAt).reversed())
                .toList();
    }

    /** Loads a saved pack by name, or {@code null} when no such pack is stored. */
    public AgentSpec loadPack(String name) {
        Path dir = packsDir().resolve(sanitize(name));
        if (!Files.isRegularFile(dir.resolve("manifest.yml"))) {
            return null;
        }
        try {
            return reader.read(dir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load pack '" + name + "': " + e.getMessage(), e);
        }
    }

    private PackRecord recordOf(Path dir, AgentSpec pack) {
        return new PackRecord(
                pack.name(),
                pack.goal() == null ? null : pack.goal().name(),
                pack.steps().size(),
                savedAt(dir));
    }

    private PackRecord recordOf(Path dir) {
        try {
            return recordOf(dir, reader.read(dir));
        } catch (IOException e) {
            // A directory that cannot be read is surfaced as a minimal record rather than dropped.
            return new PackRecord(dir.getFileName().toString(), null, 0, savedAt(dir));
        }
    }

    private String savedAt(Path dir) {
        try {
            return Instant.ofEpochMilli(Files.getLastModifiedTime(dir).toMillis()).toString();
        } catch (IOException e) {
            return "";
        }
    }

    // ---- Discovery history --------------------------------------------------

    /** Appends a discovery record to the durable history. */
    public void recordDiscovery(String sourceType, String location, int docCount) {
        DiscoveryRecord rec =
                new DiscoveryRecord(Instant.now().toString(), sourceType, location, docCount);
        try {
            Files.createDirectories(root);
            Path file = root.resolve(HISTORY_FILE);
            Files.writeString(file, json.writeValueAsString(rec) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to record discovery: " + e.getMessage(), e);
        }
    }

    /** The recorded discoveries, most recent first. */
    public List<DiscoveryRecord> discoveries() {
        Path file = root.resolve(HISTORY_FILE);
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read discovery history: " + e.getMessage(), e);
        }
        List<DiscoveryRecord> all = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            try {
                all.add(json.readValue(line, DiscoveryRecord.class));
            } catch (IOException ignored) {
                // A malformed line is skipped rather than failing the whole history read.
            }
        }
        Collections.reverse(all);
        return all;
    }

    private Path packsDir() {
        return root.resolve("packs");
    }

    private static String requireName(AgentSpec pack) {
        String name = pack.name();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("The pack has no name to save");
        }
        return sanitize(name);
    }

    /** A pack directory name must be a single safe path segment. */
    private static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
