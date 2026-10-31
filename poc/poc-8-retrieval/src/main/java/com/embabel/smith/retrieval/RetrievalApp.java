package com.embabel.smith.retrieval;

import java.util.List;

/**
 * The retrieval / skill-shadowing report (POC 8). Offline and deterministic: it builds a same-typed
 * skill library, shows the shadowing factor a type-based router faces, the pre-filter's top hits,
 * precision@1 on a benchmark, and the growth curve as distractors are added.
 *
 * <p>Run: {@code mvn -pl poc/poc-8-retrieval exec:java}
 */
public final class RetrievalApp {

    private RetrievalApp() {
    }

    public static void main(String[] args) {
        RetrievalRunner runner = new RetrievalRunner();

        List<SkillEntry> base = SampleSkillLibrary.base();
        SkillRegistry registry = new SkillRegistry(base);
        List<String> taskInputs = List.of(SampleSkillLibrary.USER_INPUT);
        String taskOutput = SampleSkillLibrary.BACKGROUND_MESSAGE;

        System.out.println("=== POC 8: skill retrieval at scale / skill shadowing ===");
        System.out.println("Library size:              " + registry.size() + " skills (all "
                + SampleSkillLibrary.USER_INPUT + " -> " + SampleSkillLibrary.BACKGROUND_MESSAGE + ")");
        System.out.println("Shadowing factor (type-):  "
                + runner.shadowingFactor(registry, taskInputs, taskOutput)
                + " skills are type-compatible, so a pure type-based (GOAP) router cannot disambiguate");

        System.out.println("\n-- Top-3 pre-filter hits for benchmark queries --");
        for (SampleSkillLibrary.QueryTarget q : SampleSkillLibrary.benchmarkQueries()) {
            List<SkillEntry> top = registry.retrieve(q.query(), 3);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < top.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(top.get(i).id()).append(i == 0 ? " (top)" : "");
            }
            boolean hit = !top.isEmpty() && top.get(0).id().equals(q.targetId());
            System.out.printf("  %-52s -> %s   [%s]%n", q.query(), sb, hit ? "correct" : "WRONG");
        }

        double p1 = runner.precisionAt1(registry, SampleSkillLibrary.benchmarkQueries());
        System.out.printf("%nPrecision@1 (benchmark): %.1f%%%n", p1 * 100);

        System.out.println("\n-- Growth curve: 'coffee' target as same-verb explain-X distractors are added --");
        List<SkillEntry> distractors = SampleSkillLibrary.withExplainDistractors().subList(base.size(),
                SampleSkillLibrary.withExplainDistractors().size());
        for (RetrievalRunner.GrowthPoint point : runner.growthCurve(base, distractors,
                "explain how a coffee machine works in plain language", "coffee-explainer")) {
            System.out.printf("  library=%-3d  coffee-explainer top-1: %s%n",
                    point.librarySize(), point.targetTop1() ? "yes" : "NO");
        }
    }
}
