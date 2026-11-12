package com.embabel.smith.retrieval.remote;

import com.embabel.smith.retrieval.SpecEntry;
import com.embabel.smith.retrieval.scale.LexicalScorer;
import com.embabel.smith.retrieval.scale.ScaleRetrievalRunner;
import com.embabel.smith.retrieval.scale.ScaleSpecLibrary;
import com.embabel.smith.retrieval.scale.ScaleSpecRegistry;
import com.embabel.smith.retrieval.scale.SemanticScorer;
import com.embabel.smith.retrieval.scale.SpecScorer;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

/**
 * The remote-embedding spec-selection-at-scale report (POC 23). It plugs a genuine <em>remote</em>
 * {@code EmbeddingService} ({@link RemoteEmbedder} — the nomic-embed-text-v2 OpenAI-compatible
 * endpoint) into POC 12's {@link SpecScorer} seam and runs two scenarios:
 *
 * <ol>
 *   <li><b>C10-comparable</b> — POC 12/20's identical 200+ same-typed shadowing library, so the
 *       remote embedding is directly comparable to lexical (POC 8), dense feature-hashing (POC 12),
 *       MiniLM (POC 20) and GLiNER2 (POC 17).</li>
 *   <li><b>Scale</b> — a larger generated library, measuring the one-time embedding cost and the
 *       steady-state per-query retrieval latency as the library grows.</li>
 * </ol>
 *
 * <p>Endpoint-gated: the live benchmark runs only when the endpoint is reachable and serves the
 * model; offline unit tests exercise the same {@link #run} path with a stub transport.
 *
 * <p>Run: {@code mvn -pl poc/poc-23-remote-embedding-scale exec:java}
 */
public final class RemoteEmbeddingApp {

    public static final String DEFAULT_BASE_URL = "http://spark.local:4000/v1";
    public static final String DEFAULT_MODEL = "nomic-embed-text-v2";
    private static final String DEFAULT_SCALE_SIZES = "100,200,400,800,1000";

    private RemoteEmbeddingApp() {
    }

    public static void main(String[] args) {
        String baseUrl = env("EMBED_BASE_URL", DEFAULT_BASE_URL);
        String apiKey = env("EMBED_API_KEY", System.getenv("OPENAI_API_KEY"));
        String model = env("EMBED_MODEL", DEFAULT_MODEL);
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("No API key. Set EMBED_API_KEY (or OPENAI_API_KEY) and re-run.");
            System.exit(1);
        }
        RemoteEmbeddingGate.Result gate =
                RemoteEmbeddingGate.check(RemoteEmbeddingGate.httpModels(baseUrl, apiKey), model);
        if (!gate.ready()) {
            System.err.println("Cannot run the remote-embedding benchmark: " + gate.failure());
            System.exit(1);
        }
        try (RemoteEmbedder embedder = RemoteEmbedder.create(baseUrl, apiKey, model)) {
            run(embedder, baseUrl, model, parseSizes(env("EMBED_SCALE_SIZES", DEFAULT_SCALE_SIZES)), System.out);
        } catch (Exception e) {
            System.err.println("Failed to run the remote-embedding benchmark: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * The two-scenario report. Pure apart from the {@link RemoteEmbedder} (the caller supplies it —
     * the real endpoint in {@code main}, a stub transport in tests), so the report logic is
     * testable offline.
     */
    public static void run(RemoteEmbedder embedder, String baseUrl, String model,
                           List<Integer> scaleSizes, PrintStream out) {
        List<Integer> sizes = scaleSizes.stream().sorted().toList();
        out.println("=== POC 23: remote EmbeddingService spec selection at scale ===");
        out.println("Endpoint: " + baseUrl + "   model: " + model);
        out.println();

        runC10Comparable(embedder, out);
        out.println();
        runScale(embedder, sizes, out);
    }

    /** Scenario 1: POC 12/20's identical 200+ same-typed shadowing library, C10-comparable. */
    private static void runC10Comparable(RemoteEmbedder embedder, PrintStream out) {
        ScaleRetrievalRunner runner = new ScaleRetrievalRunner();
        List<ScaleSpecLibrary.QueryTarget> queries = ScaleSpecLibrary.benchmark();
        List<SpecEntry> core = ScaleSpecLibrary.core();
        List<SpecEntry> full = ScaleSpecLibrary.full();

        SpecScorer lexical = new LexicalScorer();
        SpecScorer dense = new SemanticScorer();
        SpecScorer remote = new RemoteEmbeddingScorer(embedder);

        out.println("-- Scenario 1: C10-comparable (POC 12/20's " + core.size() + "/" + full.size()
                + " same-typed library) --");
        out.printf("  %-26s  core@1  full@1%n", "method");
        printRow(out, runner, core, full, queries, "lexical (word TF)", lexical);
        printRow(out, runner, core, full, queries, "dense (feature hash)", dense);
        printRow(out, runner, core, full, queries, "embedding (nomic, remote)", remote);
        out.println();
        out.println("  MiniLM (POC 20) and GLiNER2 (POC 17) numbers for the same scenario are in the");
        out.println("  shared results section (docs/04 §7.1); the remote nomic embedding is a fourth,");
        out.println("  network-backed data point on claim C10.");
    }

    private static void printRow(PrintStream out, ScaleRetrievalRunner runner, List<SpecEntry> core,
                                 List<SpecEntry> full, List<ScaleSpecLibrary.QueryTarget> queries,
                                 String label, SpecScorer scorer) {
        out.printf("  %-26s  %5s  %5s%n", label,
                pct(runner.precisionAt1(new ScaleSpecRegistry(core, scorer), queries)),
                pct(runner.precisionAt1(new ScaleSpecRegistry(full, scorer), queries)));
    }

    /** Scenario 2: the scaling dimension over a larger generated library. */
    private static void runScale(RemoteEmbedder embedder, List<Integer> scaleSizes, PrintStream out) {
        int max = scaleSizes.get(scaleSizes.size() - 1);
        List<SpecEntry> library = LargeScaleLibrary.generate(max);
        List<ScaleSpecLibrary.QueryTarget> queries = LargeScaleLibrary.benchmark();

        out.println("-- Scenario 2: scaling dimension (generated " + library.size()
                + "-spec same-typed library) --");
        out.println("  Embedding index: each spec embedded ONCE (amortised). Per-query retrieval is");
        out.println("  pure-CPU cosine over cached vectors -- O(n), no model call. A per-candidate");
        out.println("  LLM/transformer selector would need n model calls PER QUERY.");
        out.println();
        out.printf("  %-8s  %-10s  %-16s  %-16s  %s%n",
                "size", "P@1", "index-build ms", "per-query ms", "model calls/query");
        ScalingMeasurement measurement = new ScalingMeasurement();
        for (ScalingMeasurement.Point p : measurement.sweep(embedder, library, queries, scaleSizes)) {
            out.printf("  %-8d  %-10s  %-16d  %-16.3f  %s%n",
                    p.librarySize(), pct(p.precisionAt1()), p.indexBuildMillis(),
                    p.perQueryMillis(), "0 (cached; " + p.librarySize() + " one-time)");
        }
        out.println();
        out.println("  Verdict: the one-time embedding cost is the index (n embeddings, paid once);");
        out.println("  steady-state retrieval stays cheap and linear in n with zero per-query model");
        out.println("  calls -- the reason a remote EmbeddingService is the selection mechanism of");
        out.println("  choice as the spec library scales beyond what per-candidate selectors handle.");
    }

    private static List<Integer> parseSizes(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .sorted()
                .toList();
    }

    private static String pct(double value) {
        return String.format("%.0f%%", value * 100);
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
