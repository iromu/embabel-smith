package com.embabel.smith.retrieval.gliner;

import com.embabel.smith.retrieval.SpecEntry;
import com.embabel.smith.retrieval.scale.ScaleSpecLibrary;
import io.gravitee.lab.gliner4j.schema.ClassificationLabel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The POC 17 launcher (design D6): loads the GLiNER2 model, runs the three-method shadowing
 * benchmark, and prints the Precision@1 comparison table (transformer / lexical / dense).
 *
 * <p>Local-model-gated: without the ONNX bundle it prints the clear missing-model error and exits
 * non-zero (a {@code GlinerModel} gate), rather than an opaque ONNX failure.
 *
 * <p>Run: {@code mvn -pl poc/poc-17-gliner-retrieval exec:java} (from the repo root, bundle present).
 */
public final class GlinerRetrievalApp {

    private GlinerRetrievalApp() {
    }

    public static void main(String[] args) {
        System.out.println("=== POC 17: GLiNER2 transformer spec classification ===");

        GlinerModel model;
        try {
            model = GlinerModel.defaultModel();
        } catch (IllegalStateException e) {
            System.out.println();
            System.out.println(e.getMessage());
            System.exit(1);
            return;
        }

        System.out.println("Model bundle: " + model.modelDir());
        System.out.println("Shadowing scenario: " + ScaleSpecLibrary.benchmark().size()
                + " targets x 2 candidate-set variants (matching + strict)");
        System.out.println();

        ShadowingRunner runner = new ShadowingRunner();
        // Load the classifier once with the full label set; each select() call overrides with the
        // target's candidate-set labels.
        List<ClassificationLabel> loadTimeLabels = GlinerSelector.labelsFor(ScaleSpecLibrary.full());

        Map<String, SpecSelector> methods = new LinkedHashMap<>();
        try (GlinerSelector gliner = GlinerSelector.fromModel(model, loadTimeLabels)) {
            methods.put("transformer (GLiNER2)", gliner);
            methods.put("lexical (word TF)", new LexicalSelector());
            methods.put("dense (feature hash)", new DenseSelector());

            System.out.println("-- Precision@1 per method, per candidate-set variant --");
            runner.printComparisonTable(methods);
        }

        System.out.println();
        System.out.println("matching = canonical + all near-duplicates (POC-12 comparable);");
        System.out.println("strict   = canonical + near-duplicates minus the near-exact duplicate.");
    }
}
