package com.embabel.smith.composer;

import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.validation.AgentSpecValidator;
import com.embabel.smith.common.validation.ValidationResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * POC 14 launcher — the spec Composer, headless (doc 03 §2.2).
 *
 * <p>Loads two independent agent specs, places them on a {@link CompositionCanvas}, validates the
 * drag-and-drop edge (type compatibility), prints the GOAP-computed chain, and exports the result as
 * a single new composite agent spec. It then re-reads and re-validates the exported composite to show
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
        Path agentSpecs = findAgentSpecsRoot();
        String packA = envOr("COMPOSE_PACK_A", agentSpecs.resolve("compose-extract").toString());
        String packB = envOr("COMPOSE_PACK_B", agentSpecs.resolve("compose-explain").toString());
        String compositeName = envOr("COMPOSE_NAME", "compose-composite");
        Path outDir = Path.of(envOr("COMPOSE_OUTPUT_DIR", "target/composed"));

        System.out.println("=== POC 14: spec Composer (doc 03 §2.2) ===");

        AgentSpecReader reader = new AgentSpecReader();
        AgentSpecValidator validator = new AgentSpecValidator();

        AgentSpec a = loadValidated(reader, validator, Path.of(packA));
        AgentSpec b = loadValidated(reader, validator, Path.of(packB));
        System.out.println("Loaded specs:");
        System.out.println("  - " + new SpecNode(a));
        System.out.println("  - " + new SpecNode(b));

        CompositionCanvas canvas = new CompositionCanvas();
        canvas.addSpec(new SpecNode(a));
        canvas.addSpec(new SpecNode(b));
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
        System.out.println("\nExported composite agent spec to: " + compositeDir);
        AgentSpec composite = reader.read(compositeDir);
        System.out.println("  goal: " + composite.goal().name()
                + " -> " + composite.goal().outputTypeName()
                + " (export=" + composite.goal().isExported() + ")");
        System.out.println("  steps: " + composite.steps().stream().map(s -> s.name()).toList());
        System.out.println("  types: " + composite.types().stream().map(t -> t.name()).toList());

        ValidationResult recheck = validator.validate(compositeDir);
        if (recheck.isValid()) {
            System.out.println("\nExported composite re-validated cleanly (0 issues).");
            System.out.println("\n=== POC 14: spec Composer PROVEN ===");
        } else {
            System.out.println("\nExported composite FAILED re-validation: " + recheck.issues());
            System.exit(1);
        }
    }

    private static AgentSpec loadValidated(AgentSpecReader reader, AgentSpecValidator validator,
                                           Path dir) throws IOException {
        AgentSpec pack = reader.read(dir);
        ValidationResult result = validator.validate(dir);
        if (!result.isValid()) {
            throw new IllegalStateException("Component agent spec " + dir + " failed validation: "
                    + result.issues());
        }
        return pack;
    }

    private static String envOr(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /**
     * Locates the {@code agent-specs} directory by walking up from the current working directory
     * (the exec:java working dir may be the repo root or a module dir, so a fixed relative path is
     * unreliable). Falls back to {@code ./agent-specs} if not found.
     */
    private static Path findAgentSpecsRoot() {
        Path dir = Path.of(System.getProperty("user.dir"));
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParent()) {
            if (java.nio.file.Files.isDirectory(dir.resolve("agent-specs/compose-extract"))) {
                return dir.resolve("agent-specs");
            }
        }
        return Path.of("agent-specs");
    }
}
