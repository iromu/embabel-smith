package com.embabel.smith.retrieval.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.function.Function;

/**
 * Offline test support: builds a {@link RemoteEmbedder} whose transport is a
 * deterministic stub. The stub parses the OpenAI-compatible request, looks up each input
 * text's vector via the supplied {@code vectors} function, and returns a well-formed
 * embeddings response — so the request/response handling and the retrieval logic are
 * exercised without any endpoint.
 */
final class StubEmbedding {

	private StubEmbedding() {
	}

	/** An embedder that maps each input text to {@code vectors.apply(text)}. */
	static RemoteEmbedder embedder(Function<String, double[]> vectors) {
		ObjectMapper mapper = new ObjectMapper();
		RemoteEmbedder.EmbeddingTransport transport = json -> {
			JsonNode input = mapper.readTree(json).get("input");
			ObjectNode resp = mapper.createObjectNode();
			ArrayNode data = resp.putArray("data");
			int i = 0;
			for (JsonNode t : input) {
				double[] v = vectors.apply(t.asText());
				ObjectNode item = data.addObject();
				item.put("index", i);
				ArrayNode emb = item.putArray("embedding");
				for (double x : v) {
					emb.add(x);
				}
				i++;
			}
			return mapper.writeValueAsString(resp);
		};
		return RemoteEmbedder.withTransport("stub-model", transport);
	}

	/** A deterministic (per-text) 8-dim vector — same text, same vector. */
	static double[] hashVec(String s) {
		double[] v = new double[8];
		for (int i = 0; i < s.length(); i++) {
			int h = (s.charAt(i) * 31 + i) * 131;
			v[Math.floorMod(h, 8)] += Math.floorMod(h, 97) / 97.0;
		}
		return v;
	}

}
