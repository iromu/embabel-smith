/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.retrieval.embedding;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.embabel.smith.retrieval.SpecEntry;
import com.embabel.smith.retrieval.scale.LexicalScorer;
import com.embabel.smith.retrieval.scale.ScaleRetrievalRunner;
import com.embabel.smith.retrieval.scale.ScaleRetrievalRunner.CurvePoint;
import com.embabel.smith.retrieval.scale.ScaleSpecLibrary;
import com.embabel.smith.retrieval.scale.ScaleSpecRegistry;
import com.embabel.smith.retrieval.scale.SemanticScorer;
import com.embabel.smith.retrieval.scale.SpecScorer;

/**
 * The real-embedding retrieval report (POC 20). It plugs a genuine transformer
 * {@link Embedder} (a local ONNX sentence-encoder, {@link MiniLmEmbedder}) into POC 12's
 * {@link SpecScorer} seam and runs the *identical* 200+ same-typed shadowing scenario POC
 * 12 and POC 17 used, to answer the one question POC 12 left open: does a **real**
 * embedding index — not the feature-hashing stand-in — cure the near-duplicate shadowing
 * collapse that lexical and dense text-similarity both suffer?
 *
 * <p>
 * Local-model-gated: the live benchmark runs only when the one-time ONNX bundle is
 * present; offline unit tests exercise the same {@link #run} path with a deterministic
 * stub embedder.
 *
 * <p>
 * Run: {@code mvn -pl poc/poc-20-semantic-embedding exec:java}
 *
 * @author Ivan Rodriguez
 */
public final class EmbeddingRetrievalApp {

	/**
	 * Default model dir, relative to the project root (the {@code exec:java} working
	 * directory — the same convention POC 17 uses for its ONNX bundle).
	 */
	public static final String DEFAULT_MODEL_DIR = "poc/poc-20-semantic-embedding/models/all-MiniLM-L6-v2";

	private static final String MODEL_DIR_ENV = "EMBED_MODEL_DIR";

	private EmbeddingRetrievalApp() {
	}

	public static void main(String[] args) {
		Path modelDir = Path.of(System.getenv().getOrDefault(MODEL_DIR_ENV, DEFAULT_MODEL_DIR));
		if (!isModelPresent(modelDir)) {
			System.err.println("Missing local embedding model at: " + modelDir.toAbsolutePath());
			System.err.println("Download it once (see README 'One-time model download'), then re-run:");
			System.err.println("  mvn -pl poc/poc-20-semantic-embedding exec:java");
			System.exit(1);
		}
		try (MiniLmEmbedder embedder = MiniLmEmbedder.load(modelDir)) {
			run(embedder, modelDir, System.out);
		}
		catch (Exception ex) {
			System.err.println("Failed to run the real-embedding benchmark: " + ex.getMessage());
			System.exit(1);
		}
	}

	/**
	 * Whether the model bundle (ONNX graph + tokenizer) is present under
	 * {@code modelDir}.
	 * @param modelDir the directory that should contain the model bundle.
	 * @return {@code true} when both the ONNX graph and the tokenizer file are present.
	 */
	public static boolean isModelPresent(Path modelDir) {
		return Files.exists(modelDir.resolve("onnx/model_quantized.onnx"))
				&& Files.exists(modelDir.resolve("tokenizer.json"));
	}

	/**
	 * The three-method benchmark. Pure apart from the {@link Embedder} (which the caller
	 * supplies — the real ONNX model in {@code main}, a deterministic stub in tests), so
	 * the report logic is testable offline.
	 * @param embedder the embedding backend to benchmark.
	 * @param modelDir the model bundle directory, reported in the output.
	 * @param out the stream the report is written to.
	 */
	public static void run(Embedder embedder, Path modelDir, PrintStream out) {
		ScaleRetrievalRunner runner = new ScaleRetrievalRunner();
		List<ScaleSpecLibrary.QueryTarget> queries = ScaleSpecLibrary.benchmark();
		List<SpecEntry> core = ScaleSpecLibrary.core();
		List<SpecEntry> full = ScaleSpecLibrary.full();

		SpecScorer lexical = new LexicalScorer();
		SpecScorer dense = new SemanticScorer();
		SpecScorer real = new RealEmbeddingScorer(embedder);

		out.println("=== POC 20: real semantic EmbeddingService ===");
		out.println("Model bundle: " + modelDir.toAbsolutePath());
		out.println("Core library (distinct topics): " + core.size() + " specs, all " + ScaleSpecLibrary.USER_INPUT
				+ " -> " + ScaleSpecLibrary.BACKGROUND_MESSAGE);
		out.println("Full library (+ near-duplicate shadowing): " + full.size() + " specs");
		out.println();

		out.println("-- Precision as the library grows (distinct topics, no shadowing) --");
		out.printf("  %-22s  core@1  core@5%n", "scorer");
		printCoreRow(out, runner, core, queries, "lexical (word TF)", lexical);
		printCoreRow(out, runner, core, queries, "dense (feature hash)", dense);
		printCoreRow(out, runner, core, queries, "embedding (MiniLM)", real);
		out.println();

		out.println("-- Precision at full scale (with same-topic near-duplicates) --");
		out.printf("  %-22s  full@1  full@5%n", "scorer");
		printFullRow(out, runner, full, queries, "lexical (word TF)", lexical);
		printFullRow(out, runner, full, queries, "dense (feature hash)", dense);
		printFullRow(out, runner, full, queries, "embedding (MiniLM)", real);
		out.println();

		out.println("-- Growth curve: precision@1 as same-topic near-duplicates are added --");
		List<CurvePoint> lexCurve = runner.growthCurve(core, ScaleSpecLibrary.growthSequence(), lexical, queries);
		List<CurvePoint> denseCurve = runner.growthCurve(core, ScaleSpecLibrary.growthSequence(), dense, queries);
		List<CurvePoint> realCurve = runner.growthCurve(core, ScaleSpecLibrary.growthSequence(), real, queries);
		out.printf("  %-6s  %-9s  %-9s  %s%n", "size", "lexical", "dense", "embedding");
		for (int i = 0; i < lexCurve.size(); i++) {
			CurvePoint p = lexCurve.get(i);
			if (p.librarySize() <= core.size() || (p.librarySize() - core.size()) % 10 == 0
					|| i == lexCurve.size() - 1) {
				out.printf("  %-6d  %-9s  %-9s  %s%n", p.librarySize(), pct(p.precisionAt1()),
						pct(denseCurve.get(i).precisionAt1()), pct(realCurve.get(i).precisionAt1()));
			}
		}
		out.println();

		out.println("-- Per-target top-1 at full scale (embedding) --");
		ScaleSpecRegistry fullReal = new ScaleSpecRegistry(full, real);
		for (ScaleSpecLibrary.QueryTarget q : queries) {
			String top = runner.topHitId(fullReal, q.query());
			boolean canonical = top != null && top.equals(q.targetId());
			out.printf("  %-24s -> top-1=%-24s [%s]%n", q.query(), top, canonical ? "canonical" : "shadowed");
		}
		out.println();

		double lexFull = runner.precisionAt1(new ScaleSpecRegistry(full, lexical), queries);
		double denseFull = runner.precisionAt1(new ScaleSpecRegistry(full, dense), queries);
		double realFull = runner.precisionAt1(new ScaleSpecRegistry(full, real), queries);
		double realCore = runner.precisionAt1(new ScaleSpecRegistry(core, real), queries);
		out.println("-- Verdict --");
		out.printf("  1. Distinct topics: the real embedding index hits %.0f%% at %d specs — a genuine%n",
				realCore * 100, core.size());
		out.println("     EmbeddingService (not a stand-in) retrieves cleanly when the library is distinct.");
		out.printf(
				"  2. Near-duplicate shadowing at full scale: lexical %.0f%%, dense %.0f%%, real embedding %.0f%%.%n",
				lexFull * 100, denseFull * 100, realFull * 100);
		out.println("     A real transformer embedding is a different (meaning-based) signal than hashed");
		out.println("     n-grams — see whether it holds up where text-similarity collapses, or shares");
		out.println("     the same near-duplicate ambiguity (the canonical spec is still not unique).");
	}

	private static void printCoreRow(PrintStream out, ScaleRetrievalRunner runner, List<SpecEntry> core,
			List<ScaleSpecLibrary.QueryTarget> queries, String label, SpecScorer scorer) {
		out.printf("  %-22s  %5s  %5s%n", label, pct(runner.precisionAt1(new ScaleSpecRegistry(core, scorer), queries)),
				pct(runner.precisionInTopK(new ScaleSpecRegistry(core, scorer), queries, 5)));
	}

	private static void printFullRow(PrintStream out, ScaleRetrievalRunner runner, List<SpecEntry> full,
			List<ScaleSpecLibrary.QueryTarget> queries, String label, SpecScorer scorer) {
		out.printf("  %-22s  %5s  %5s%n", label, pct(runner.precisionAt1(new ScaleSpecRegistry(full, scorer), queries)),
				pct(runner.precisionInTopK(new ScaleSpecRegistry(full, scorer), queries, 5)));
	}

	private static String pct(double value) {
		return String.format("%.0f%%", value * 100);
	}

}
