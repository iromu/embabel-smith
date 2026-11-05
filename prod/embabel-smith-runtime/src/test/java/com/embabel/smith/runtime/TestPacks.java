package com.embabel.smith.runtime;

import com.embabel.smith.skillsource.ResolvedSkillSource;
import com.embabel.smith.skillsource.SkillSource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Access to the test skill-packs under {@code src/test/resources/test-packs}: builds temporary
 * folder-source libraries from named packs and resolves them.
 */
public final class TestPacks {

    private TestPacks() {
    }

    public static Path root() {
        try {
            return Path.of(Objects.requireNonNull(TestPacks.class.getResource("/test-packs")).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Copies the named packs into a fresh library directory and returns it. */
    public static Path library(Path target, String... packNames) throws IOException {
        Files.createDirectories(target);
        for (String pack : packNames) {
            copyRecursively(root().resolve(pack), target.resolve(pack));
        }
        return target;
    }

    public static ResolvedSkillSource resolve(Path libraryDir) {
        return SkillSource.parse("folder:" + libraryDir).resolve();
    }

    public static com.embabel.smith.common.model.SkillPack read(String packName) throws IOException {
        return new com.embabel.smith.common.io.SkillPackReader().read(root().resolve(packName));
    }

    private static void copyRecursively(Path from, Path to) throws IOException {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path path : walk.toList()) {
                Path destination = to.resolve(from.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination);
                }
            }
        }
    }
}
