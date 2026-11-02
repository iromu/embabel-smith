package com.embabel.smith.retrieval.gliner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Locates and verifies the GLiNER2 ONNX bundle on disk. This is the local-model gate for POC 17:
 * the benchmark runs only when the bundle is present, and a missing bundle produces a clear,
 * actionable error (naming the missing files and the one-time download command) rather than an
 * opaque ONNX failure — mirroring the project's endpoint-gated pattern.
 *
 * <p>The expected bundle layout (a {@code gravitee-io/gliner4j-gliner2-base-v1} download) is:
 * <pre>{@code
 *   <modelDir>/gliner4j_config.json
 *   <modelDir>/tokenizer.json
 *   <modelDir>/onnx/classifier_full.onnx
 * }</pre>
 */
public final class GlinerModel {

    /** Default bundle location, relative to the repo root (the module's {@code models/} dir). */
    public static final String DEFAULT_MODEL_DIR =
            "poc/poc-17-gliner-retrieval/models/gliner2-base-v1";

    /** Env var to override the bundle location (follows the existing {@code BUILDER_*} convention). */
    public static final String ENV_MODEL_DIR = "BUILDER_GLINER_MODEL_DIR";

    /**
     * The ONNX variant to load. Default is the INT8-quantized graph (fast on CPU); the bundle also
     * ships {@code onnx} (FP32) and {@code onnx_fp16} as accuracy references.
     */
    public static final String VARIANT = "onnx_quantized";

    /** The files the classifier needs to load, relative to the bundle root. */
    private static final List<String> EXPECTED_FILES =
            List.of("gliner4j_config.json", "tokenizer.json", VARIANT + "/classifier_full.onnx");

    private final Path modelDir;

    public GlinerModel(Path modelDir) {
        this.modelDir = modelDir;
    }

    /**
     * Resolve the bundle path from {@link #ENV_MODEL_DIR} (default {@link #DEFAULT_MODEL_DIR}) and
     * verify it is present and complete.
     */
    public static GlinerModel defaultModel() {
        String override = System.getenv(ENV_MODEL_DIR);
        Path dir = (override == null || override.isBlank()) ? Path.of(DEFAULT_MODEL_DIR) : Path.of(override);
        return new GlinerModel(dir).requireAvailable();
    }

    public Path modelDir() {
        return modelDir;
    }

    /** The ONNX variant folder this model loads (see {@link #VARIANT}). */
    public String variant() {
        return VARIANT;
    }

    /**
     * Verify the expected bundle files exist. When any are missing, throw a
     * {@link IllegalStateException} that names the missing files and the one-time download command.
     *
     * @return this, for fluent use once the bundle is confirmed available
     */
    public GlinerModel requireAvailable() {
        List<String> missing = new ArrayList<>();
        if (!Files.isDirectory(modelDir)) {
            missing.add("<bundle root> " + modelDir);
        } else {
            for (String name : EXPECTED_FILES) {
                if (!Files.isRegularFile(modelDir.resolve(name))) {
                    missing.add(name);
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(buildMissingBundleError(missing));
        }
        return this;
    }

    private String buildMissingBundleError(List<String> missing) {
        String downloadCommand = "hf download gravitee-io/gliner4j-gliner2-base-v1 "
                + "--include \"*.json\" \"" + VARIANT + "/classifier_full.onnx\" "
                + "--local-dir " + DEFAULT_MODEL_DIR;
        return "GLiNER2 model bundle is missing or incomplete at: " + modelDir + "\n"
                + "  missing: " + String.join(", ", missing) + "\n"
                + "This benchmark is local-model-gated. Fetch the INT8 bundle (one-time):\n"
                + "  " + downloadCommand + "\n"
                + "or point " + ENV_MODEL_DIR + " at a directory that already has it.";
    }
}
