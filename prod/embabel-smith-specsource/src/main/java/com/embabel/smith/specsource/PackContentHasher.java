package com.embabel.smith.specsource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Computes a agent spec's content hash: SHA-256 over the sorted relative paths and bytes of the
 * pack's format-owned files — {@code manifest.yml}, {@code goal.yml}, and the YAML files under
 * {@code steps/} and {@code types/} (the same files {@code smith-common} reads; everything else
 * is ignored).
 *
 * <p>The hash is stable for identical content and changes when any format-owned file changes —
 * the quantity POC 18's drift detection consumes.
 */
public final class PackContentHasher {

    private PackContentHasher() {
    }

    public static String hash(Path packRoot) {
        MessageDigest digest = sha256();
        for (Path file : formatFiles(packRoot)) {
            digest.update(packRoot.relativize(file).toString().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(readBytes(file));
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static List<Path> formatFiles(Path packRoot) {
        List<Path> files = new ArrayList<>();
        for (String name : List.of("manifest.yml", "goal.yml")) {
            Path file = packRoot.resolve(name);
            if (Files.isRegularFile(file)) {
                files.add(file);
            }
        }
        for (String dir : List.of("steps", "types")) {
            Path dirPath = packRoot.resolve(dir);
            if (!Files.isDirectory(dirPath)) {
                continue;
            }
            try (var stream = Files.list(dirPath)) {
                stream.filter(Files::isRegularFile)
                        .filter(PackContentHasher::isYaml)
                        .forEach(files::add);
            } catch (IOException e) {
                throw new UncheckedIOException("cannot list " + dirPath, e);
            }
        }
        files.sort(Comparator.comparing(f -> packRoot.relativize(f).toString()));
        return files;
    }

    private static boolean isYaml(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private static byte[] readBytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
