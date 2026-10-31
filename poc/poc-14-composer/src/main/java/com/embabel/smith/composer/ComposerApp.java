package com.embabel.smith.composer;

import com.embabel.smith.common.io.SkillPackReader;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.validation.SkillPackValidator;
import com.embabel.smith.common.validation.ValidationResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * POC 14 launcher — the skill Composer, headless (doc 03 §2.2).
 *
 * <p>Loads two independent skill-packs, places them on a {@link CompositionCanvas}, validates the
 * drag-and-drop edge (type compatibility), prints the GOAP-computed chain, and exports the result as
 * a single new composite skill-pack. It then re-reads and re-validates the exported composite to show
 * it is a well-formed, loadable pack. Fully offline — no model endpoint.
 *
 * <p>Run:
 * <pre>
 *   mvn -pl poc/poc-14-composer exec:java
 * </pre>
 * Paths are overridable via {@code COMPOSE_PACK_A}, {@code COMPOSE_PACK_B}, {@code COMPOSE_NAME},
 * and {@code COMPOSE_OUTPUT_DIR}.
 */
public final class ComposerApp {

    public static void main(String[] args) throws IOException {
        Path skillPacks = findSkillPacksRoot();
        String packA = envOr("COMPOSE_PACK_A", skillPacks.resolve("compose-extract").toString());
        String packB = envOr("COMPOSE_PACK_B", skillPacks.resolve("compose-explain").toString());
        String compositeName = envOr("COMPOSE_NAME", "compose-composite");
        Path outDir = Path.of(envOr("COMPOSE_OUTPUT_DIR", "target/composed"));

        System.out.println("=== POC 14: skill Composer (doc 03 §2.2) ===");

        SkillPackReader reader = new SkillPackReader();
        SkillPackValidator validator = new SkillPackValidator();

        SkillPack a = loadValidated(reader, validator, Path.of(packA));
        SkillPack b = loadValidated(reader, validator, Path.of(packB));
        System.out.println("Loaded skills:");
        System.out.println("  - " + new SkillNode(a));
        System.out.println("  - " + new SkillNode(b));

        CompositionCanvas canvas = new CompositionCanvas();
        canvas.addSkill(new SkillNode(a));
        canvas.addSkill(new SkillNode(b));
        canvas.connect(a.name(), b.name());
        System.out.println("\nCanvas: " + a.name() + " -> " + b.name());

        List<String> issues = canvas.validate();
        if (!issues.isEmpty()) {
            System.out.println("Canvas INVALID:");
            issues.forEach(issue -> System.out.println("  - " + issue));
            System.exit(1);
            return;
        }
        System.out.println("Canvas VALID (edge type-compatible, single entry -> exit chain).");
        System.out.println("Composed chain: " + canvas.describeChain());

        Path compositeDir = new CompositeExporter().export(compositeName, canvas, outDir);
        System.out.println("\nExported composite skill-pack to: " + compositeDir);
        SkillPack composite = reader.read(compositeDir);
        System.out.println("  goal: " + composite.goal().name()
                + " -> " + composite.goal().outputTypeName()
                + " (export=" + composite.goal().isExported() + ")");
        System.out.println("  steps: " + composite.steps().stream().map(s -> s.name()).toList());
        System.out.println("  types: " + composite.types().stream().map(t -> t.name()).toList());

        ValidationResult recheck = validator.validate(compositeDir);
        if (recheck.isValid()) {
            System.out.println("\nExported composite re-validated cleanly (0 issues).");
            System.out.println("\n=== POC 14: skill Composer PROVEN ===");
        } else {
            System.out.println("\nExported composite FAILED re-validation: " + recheck.issues());
            System.exit(1);
        }
    }

    private static SkillPack loadValidated(SkillPackReader reader, SkillPackValidator validator,
                                           Path dir) throws IOException {
        SkillPack pack = reader.read(dir);
        ValidationResult result = validator.validate(dir);
        if (!result.isValid()) {
            throw new IllegalStateException("Component skill-pack " + dir + " failed validation: "
                    + result.issues());
        }
        return pack;
    }

    private static String envOr(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /**
     * Locates the {@code skill-packs} directory by walking up from the current working directory
     * (the exec:java working dir may be the repo root or a module dir, so a fixed relative path is
     * unreliable). Falls back to {@code ./skill-packs} if not found.
     */
    private static Path findSkillPacksRoot() {
        Path dir = Path.of(System.getProperty("user.dir"));
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParent()) {
            if (java.nio.file.Files.isDirectory(dir.resolve("skill-packs/compose-extract"))) {
                return dir.resolve("skill-packs");
            }
        }
        return Path.of("skill-packs");
    }
}
