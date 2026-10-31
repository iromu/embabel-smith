package com.embabel.smith.common.io;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.model.SkillPackManifest;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes a {@link SkillPack} to disk in the skill-pack directory format.
 *
 * <p>Produces {@code manifest.yml}, {@code goal.yml}, one file per action under {@code steps/},
 * and one file per intermediate type under {@code types/}. An empty {@code types/} directory is
 * still created (with a {@code .gitkeep}) so the layout is complete even for packs with no
 * intermediate types.
 */
public class SkillPackWriter {

    private final ObjectMapper yaml = Yaml.mapper();

    /**
     * Writes the pack under {@code dir}, creating directories as needed.
     *
     * @return the directory the pack was written to
     */
    public Path write(SkillPack pack, Path dir) throws IOException {
        Files.createDirectories(dir);

        writeYaml(dir.resolve("manifest.yml"), pack.manifest());
        writeYaml(dir.resolve("goal.yml"), pack.goal());

        Path stepsDir = dir.resolve("steps");
        Files.createDirectories(stepsDir);
        clearContents(stepsDir); // drop stale step files from a previous write to this directory
        for (ActionStepSpec step : pack.steps()) {
            writeYaml(stepsDir.resolve(step.name() + ".yml"), step);
        }

        Path typesDir = dir.resolve("types");
        Files.createDirectories(typesDir);
        clearContents(typesDir);
        if (pack.types().isEmpty()) {
            Files.writeString(typesDir.resolve(".gitkeep"), "");
        } else {
            for (DynamicTypeDef type : pack.types()) {
                writeYaml(typesDir.resolve(type.name() + ".yml"), type);
            }
        }
        return dir;
    }

    /** Deletes every regular file in {@code dir} so a re-write is idempotent (no stale files). */
    private void clearContents(Path dir) throws IOException {
        try (var files = Files.list(dir)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Files.delete(file);
            }
        }
    }

    private void writeYaml(Path file, Object value) throws IOException {
        String content = yaml.writeValueAsString(value);
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
