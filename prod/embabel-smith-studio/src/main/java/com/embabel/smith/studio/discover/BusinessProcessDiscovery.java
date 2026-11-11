package com.embabel.smith.studio.discover;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Loads the business-process documents to present and generate from. A {@code path} points at a
 * local directory of docs; a {@code url} is cloned (shallow) into a temp dir first. The {@code .md}
 * files under the location are read and returned, so the workspace can show what it is generating
 * from. This is the "ingest" half of discovery: it surfaces the (already business-level) documents.
 * The model-based extraction of a raw codebase (running the discovery prompt) is a separate, later
 * step and is not performed here.
 */
public class BusinessProcessDiscovery {

    /** Hard cap on the total loaded corpus, in characters (~300K). */
    private static final int MAX_CHARS = 300_000;

    /** The loaded docs plus where they came from. */
    public record DiscoverResult(String sourceType, String location, Map<String, String> docs) {
    }

    public DiscoverResult load(String path, String url) {
        if (path != null && !path.isBlank()) {
            Path dir = expandHome(path.trim());
            if (!Files.isDirectory(dir)) {
                throw new IllegalArgumentException("Not a directory: " + dir);
            }
            return new DiscoverResult("folder", dir.toString(), readDocs(dir));
        }
        if (url != null && !url.isBlank()) {
            Path dir = clone(url.trim());
            return new DiscoverResult("git", url.trim(), readDocs(dir));
        }
        throw new IllegalArgumentException("Provide a docs path or a repository URL.");
    }

    private Map<String, String> readDocs(Path root) {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".md") || n.endsWith(".markdown");
                    })
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read docs from " + root + ": " + e.getMessage(), e);
        }
        Map<String, String> docs = new LinkedHashMap<>();
        int total = 0;
        for (Path file : files) {
            if (total > MAX_CHARS) {
                break;
            }
            String name = root.relativize(file).toString();
            String body;
            try {
                body = Files.readString(file);
            } catch (IOException e) {
                continue;
            }
            docs.put(name, body);
            total += body.length();
        }
        if (docs.isEmpty()) {
            throw new IllegalArgumentException("No business-process documents (.md) found under " + root);
        }
        return docs;
    }

    private Path clone(String url) {
        Path dir;
        try {
            dir = Files.createTempDirectory("smith-discover-");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create a temp dir for the clone: " + e.getMessage(), e);
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "clone", "--depth", "1", url, dir.toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(180, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("git clone timed out for " + url);
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("git clone failed for " + url + ": " + output.trim());
            }
            return dir;
        } catch (IOException e) {
            throw new IllegalStateException("git clone failed for " + url + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("git clone interrupted for " + url, e);
        }
    }

    private static Path expandHome(String p) {
        if (p.equals("~")) {
            return Path.of(System.getProperty("user.home"));
        }
        if (p.startsWith("~/")) {
            return Path.of(System.getProperty("user.home"), p.substring(2));
        }
        return Path.of(p);
    }
}
