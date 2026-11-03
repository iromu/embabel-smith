package com.embabel.smith.retrieval.embedding;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A real dense {@link Embedder}: mean-pooled, L2-normalised sentence embeddings from a local ONNX
 * BERT sentence-encoder ({@code all-MiniLM-L6-v2}, 384-dim). This is the {@code EmbeddingService}
 * doc 03 §2.1 asks for — a genuine transformer embedding index, run fully offline (no model
 * endpoint), in contrast to POC 12's feature-hashing stand-in.
 *
 * <p>The ONNX graph (verified) takes {@code input_ids} / {@code attention_mask} /
 * {@code token_type_ids} (INT64, {@code [batch, seq]}) and returns {@code last_hidden_state}
 * (FLOAT, {@code [batch, seq, 384]}); the sentence vector is the mask-aware mean of the token
 * hidden states, L2-normalised.
 */
public final class MiniLmEmbedder implements Embedder, Closeable {

    private static final String INPUT_IDS = "input_ids";
    private static final String ATTENTION_MASK = "attention_mask";
    private static final String TOKEN_TYPE_IDS = "token_type_ids";
    private static final String LAST_HIDDEN_STATE = "last_hidden_state";
    /** all-MiniLM-L6-v2 hidden size (the model this POC ships; also the ONNX output's last dim). */
    private static final int HIDDEN_DIM = 384;

    private final OrtEnvironment env;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;

    private MiniLmEmbedder(OrtEnvironment env, OrtSession session, HuggingFaceTokenizer tokenizer) {
        this.env = env;
        this.session = session;
        this.tokenizer = tokenizer;
    }

    /**
     * Load the ONNX model + tokenizer from {@code modelDir}, which must contain
     * {@code onnx/model_quantized.onnx} and {@code tokenizer.json}.
     */
    public static MiniLmEmbedder load(Path modelDir) throws IOException {
        try {
            OrtEnvironment env = OrtEnvironment.getEnvironment();
            OrtSession session =
                    env.createSession(modelDir.resolve("onnx/model_quantized.onnx").toString());
            HuggingFaceTokenizer tokenizer =
                    HuggingFaceTokenizer.newInstance(modelDir.resolve("tokenizer.json"));
            return new MiniLmEmbedder(env, session, tokenizer);
        } catch (Exception e) {
            throw new IOException("failed to load the MiniLM ONNX embedder from " + modelDir, e);
        }
    }

    @Override
    public float[] embed(String text) {
        Encoding enc = tokenizer.encode(text == null ? "" : text);
        long[] ids = enc.getIds();
        long[] mask = enc.getAttentionMask();
        long[] types = enc.getTypeIds();
        int seq = ids.length;

        Map<String, OnnxTensor> inputs = new LinkedHashMap<>();
        inputs.put(INPUT_IDS, tensor(ids, seq));
        inputs.put(ATTENTION_MASK, tensor(mask, seq));
        inputs.put(TOKEN_TYPE_IDS, tensor(types, seq));

        try (OrtSession.Result result = session.run(inputs, Set.of(LAST_HIDDEN_STATE))) {
            OnnxTensor out = (OnnxTensor) result.get(0);
            float[][] hidden = toSeqDim(out.getValue(), seq);
            return EmbeddingMath.normalize(EmbeddingMath.meanPool(hidden, mask));
        } catch (Exception e) {
            throw new IllegalStateException("MiniLM ONNX inference failed", e);
        }
    }

    private OnnxTensor tensor(long[] flat, int seq) {
        long[][] shaped = new long[1][seq];
        shaped[0] = flat;
        try {
            return OnnxTensor.createTensor(env, shaped);
        } catch (Exception e) {
            throw new IllegalStateException("failed to build an ONNX input tensor", e);
        }
    }

    /** Coerce the ONNX output value (rank 3 {@code [1][seq][dim]} or rank 2 {@code [seq][dim]}) to {@code [seq][dim]}. */
    private static float[][] toSeqDim(Object value, int seq) {
        if (value instanceof float[][][] f3) {
            return f3.length == 1 ? f3[0] : f3[0];
        }
        if (value instanceof float[][] f2) {
            return f2;
        }
        if (value instanceof float[] f1) {
            float[][] hidden = new float[seq][HIDDEN_DIM];
            int n = Math.min(f1.length, seq * HIDDEN_DIM);
            for (int i = 0; i < n; i++) {
                hidden[i / HIDDEN_DIM][i % HIDDEN_DIM] = f1[i];
            }
            return hidden;
        }
        throw new IllegalStateException("unexpected last_hidden_state value type: " + value.getClass());
    }

    @Override
    public void close() throws IOException {
        try {
            session.close();
        } catch (Exception e) {
            throw new IOException("failed to close the ONNX session", e);
        } finally {
            tokenizer.close();
        }
    }
}
