package com.embabel.smith.common.io;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads a agent spec directory into an in-memory {@link AgentSpec}.
 *
 * <p>Locates {@code manifest.yml}, {@code goal.yml}, the action steps under {@code steps/} and
 * the intermediate types under {@code types/}. Files that are not {@code .yml}/{@code .yaml}
 * (e.g. the {@code .gitkeep} placed in an empty {@code types/}) are ignored.
 */
public class AgentSpecReader {

    private final ObjectMapper yaml = Yaml.mapper();

    public AgentSpec read(Path dir) throws IOException {
        AgentSpecManifest manifest = readYaml(dir.resolve("manifest.yml"), AgentSpecManifest.class);
        GoalStepSpec goal = readYaml(dir.resolve("goal.yml"), GoalStepSpec.class);

        List<ActionStepSpec> steps = new ArrayList<>();
        for (Path file : ymlFiles(dir.resolve("steps"))) {
            steps.add(readYaml(file, ActionStepSpec.class));
        }

        List<DynamicTypeDef> types = new ArrayList<>();
        for (Path file : ymlFiles(dir.resolve("types"))) {
            types.add(readYaml(file, DynamicTypeDef.class));
        }

        return new AgentSpec(manifest, goal, steps, types);
    }

    private List<Path> ymlFiles(Path dir) throws IOException {
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

    private <T> T readYaml(Path file, Class<T> type) throws IOException {
        return yaml.readValue(Files.readString(file), type);
    }
}
