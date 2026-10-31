package com.embabel.smith.disclosure;

import com.embabel.smith.common.io.SkillPackReader;
import com.embabel.smith.common.model.SkillPack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The progressive-disclosure context-cost report (POC 11). Offline: loads a skill-pack that ships a
 * bundled reference, and shows the plan context stays bounded as the reference grows, while an eager
 * (inlined) prompt grows linearly.
 *
 * <p>Run: {@code mvn -pl poc/poc-11-progressive-disclosure exec:java}
 */
public final class ProgressiveApp {

    private ProgressiveApp() {
    }

    public static void main(String[] args) throws IOException {
        Path packDir = findPack("reference-heavy");
        SkillPack pack = new SkillPackReader().read(packDir);
        String baseReference = Files.readString(packDir.resolve("references/domain.md"));

        ProgressiveDisclosureRunner runner = new ProgressiveDisclosureRunner();

        System.out.println("=== POC 11: progressive-disclosure stress test ===");
        System.out.println("Skill: " + pack.name() + " — ships a bundled reference (references/domain.md)");
        System.out.printf("%n%-12s %-16s %-18s %-18s %-10s%n",
                "reference", "plan context", "progressive prompt", "eager prompt", "eager/progressive");
        System.out.println("-".repeat(76));

        for (int scale : new int[]{1, 10, 50, 100}) {
            String reference = repeat(baseReference, scale);
            ProgressiveDisclosureRunner.ContextCost cost = runner.measure(pack, reference);
            System.out.printf("%-12s %-16d %-18d %-18d %-10.2fx%n",
                    kb(cost.referenceChars()),
                    cost.planContextChars(),
                    cost.progressivePromptChars(),
                    cost.eagerPromptChars(),
                    cost.eagerToProgressiveRatio());
        }

        ProgressiveDisclosureRunner.ContextCost small = runner.measure(pack, baseReference);
        System.out.printf("%nPlan context is constant (%d chars) while the reference grows — the planner never loads the%n",
                small.planContextChars());
        System.out.println("bundled reference. Eager disclosure pays the reference cost in every prompt; progressive disclosure");
        System.out.println("fetches it on demand, keeping the loaded context bounded.");
    }

    private static String repeat(String text, int times) {
        StringBuilder sb = new StringBuilder(text.length() * times);
        for (int i = 0; i < times; i++) {
            sb.append(text);
        }
        return sb.toString();
    }

    private static String kb(int chars) {
        return String.format("%.1f KB", chars / 1024.0);
    }

    private static Path findPack(String name) {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("skill-packs").resolve(name);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not locate skill-packs/" + name);
    }
}
