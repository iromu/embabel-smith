package com.embabel.smith.runtime;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Assembles the flat step-spec directory that Embabel's {@code YmlStepSpecRepository} reads.
 *
 * <p>A skill-pack keeps {@code goal.yml} at its root and the action steps under {@code steps/},
 * but the repository loads from a single directory. This copies {@code goal.yml} and every
 * {@code steps/*.yml} into one target directory so the goal and its action chain are ingested
 * together (and thus planned together). The source pack is left untouched.
 */
public class StepSpecAssembler {

    private static final CopyOption[] COPY = {StandardCopyOption.REPLACE_EXISTING};

    /**
     * Copies {@code packDir/goal.yml} and each {@code packDir/steps/*.yml} into {@code targetDir}.
     *
     * @return the target directory now holding the goal plus all action steps
     */
    public Path assemble(Path packDir, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);

        Path goal = packDir.resolve("goal.yml");
        if (!Files.isRegularFile(goal)) {
            throw new IOException("skill-pack has no goal.yml: " + packDir);
        }
        Files.copy(goal, targetDir.resolve("goal.yml"), COPY);

        Path stepsDir = packDir.resolve("steps");
        for (Path step : yamlFiles(stepsDir)) {
            Files.copy(step, targetDir.resolve(step.getFileName().toString()), COPY);
        }
        return targetDir;
    }

    private List<Path> yamlFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(this::isYaml)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    private boolean isYaml(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }
}
