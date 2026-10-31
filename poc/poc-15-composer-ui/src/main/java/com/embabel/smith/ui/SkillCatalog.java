package com.embabel.smith.ui;

import com.embabel.smith.common.io.SkillPackReader;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.composer.SkillNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The set of skills the canvas can place: every valid, exported skill-pack under the skill-packs
 * directory, exposed as a {@link SkillNode} (its name, external input types, and output type). This
 * is the "palette" the drag-and-drop editor offers.
 */
@Component
public class SkillCatalog {

    private final Map<String, SkillNode> skills = new LinkedHashMap<>();

    public SkillCatalog(@Value("${builder.skill-packs-dir:}") String dirOverride) {
        Path dir = resolveSkillPacksDir(dirOverride);
        if (dir != null && Files.isDirectory(dir)) {
            try (var subs = Files.list(dir)) {
                for (Path sub : subs.filter(Files::isDirectory).toList()) {
                    loadIfSkillPack(sub);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to list skill-packs under " + dir, e);
            }
        }
        if (skills.isEmpty()) {
            throw new IllegalStateException(
                    "No skill-packs found under " + dir
                            + "; point builder.skill-packs-dir at a directory of skill-packs.");
        }
    }

    private void loadIfSkillPack(Path sub) {
        if (!Files.isRegularFile(sub.resolve("goal.yml")) || !Files.isDirectory(sub.resolve("steps"))) {
            return;
        }
        try {
            SkillPack pack = new SkillPackReader().read(sub);
            if (pack.goal() == null || !pack.goal().isExported() || pack.name() == null) {
                return;
            }
            skills.put(pack.name(), new SkillNode(pack));
        } catch (Exception e) {
            // Skip directories that are not well-formed skill-packs (e.g. nested samples).
        }
    }

    public Map<String, SkillNode> all() {
        return Collections.unmodifiableMap(skills);
    }

    public SkillNode get(String name) {
        return skills.get(name);
    }

    /** Uses the override if given; otherwise walks up from the working dir to find skill-packs. */
    private Path resolveSkillPacksDir(String override) {
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        Path dir = Path.of(System.getProperty("user.dir"));
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve("skill-packs/compose-extract"))) {
                return dir.resolve("skill-packs");
            }
        }
        return Path.of("skill-packs");
    }
}
