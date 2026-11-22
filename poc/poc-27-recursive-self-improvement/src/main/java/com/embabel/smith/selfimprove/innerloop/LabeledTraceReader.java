/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.innerloop;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.embabel.smith.common.trace.TraceRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads a split of the frozen labelled corpus into {@link TraceCase} records.
 *
 * <p>
 * This reader exists because the product's cannot: {@code TraceRecordJson} is built on a
 * bare {@code ObjectMapper}, which fails on unknown properties, so every labelled line in
 * this corpus would be reported corrupt by {@code JsonlTraceStore}. The corpus is POC
 * evidence rather than live traffic, so the loop reads it here with a mapper that
 * tolerates the three fields the corpus adds. The product's codec is not modified —
 * {@code prod/} is out of reach for this POC.
 *
 * <p>
 * Reads only. The corpus is a fixed artifact ({@code evidence/MUTABILITY.md},
 * {@code trace-corpus}) and nothing in this package writes it.
 *
 * @author Ivan Rodriguez
 */
public final class LabeledTraceReader {

	/** The split a generated policy is fitted against. */
	public static final String TUNING = "tuning";

	/** The split a fitted policy is judged on, never fitted against. */
	public static final String HELDOUT = "heldout";

	private static final Set<String> SPLITS = Set.of(TUNING, HELDOUT);

	private static final ObjectMapper LENIENT_JSON = new ObjectMapper();

	private LabeledTraceReader() {
	}

	/**
	 * Read one corpus split from the classpath.
	 * @param split {@value #TUNING} or {@value #HELDOUT}
	 * @return the labelled lines in file order
	 */
	public static List<TraceCase> read(String split) {
		if (!SPLITS.contains(split)) {
			throw new IllegalArgumentException("unknown corpus split '" + split + "'; this POC has " + SPLITS);
		}
		String resource = "/corpus/traces/" + split + ".jsonl";
		try (InputStream in = LabeledTraceReader.class.getResourceAsStream(resource)) {
			if (in == null) {
				throw new IllegalStateException("corpus split '" + split + "' is not on the classpath at " + resource);
			}
			return readLines(split, in);
		}
		catch (IOException ex) {
			throw new IllegalStateException("could not read the corpus split '" + split + "': " + ex.getMessage(), ex);
		}
	}

	private static List<TraceCase> readLines(String split, InputStream in) throws IOException {
		List<TraceCase> cases = new ArrayList<>();
		try (BufferedReader lines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			String line;
			while ((line = lines.readLine()) != null) {
				if (!line.isBlank()) {
					cases.add(parse(split, line));
				}
			}
		}
		return List.copyOf(cases);
	}

	private static TraceCase parse(String split, String line) {
		JsonNode root;
		try {
			root = LENIENT_JSON.readTree(line);
		}
		catch (IOException ex) {
			throw new IllegalStateException("corrupt corpus line in '" + split + "': " + ex.getMessage(), ex);
		}
		String runId = text(root, "runId");
		String label = text(root, "label");
		if (runId == null || label == null) {
			throw new IllegalStateException("corpus line in '" + split + "' has no runId or label");
		}
		JsonNode context = root.get("spec_context");
		return new TraceCase(runId, trace(root), label, text(root, "label_reason"),
				(context != null && context.isObject()) ? specContext(context) : null);
	}

	private static TraceRecord trace(JsonNode root) {
		JsonNode source = root.get("source");
		JsonNode pack = root.get("pack");
		List<TraceRecord.StepRecord> steps = new ArrayList<>();
		JsonNode recorded = root.get("steps");
		if (recorded != null && recorded.isArray()) {
			for (JsonNode step : recorded) {
				steps.add(new TraceRecord.StepRecord(text(step, "name"), text(step, "status"),
						step.path("durationMs").asLong(), text(step, "outputExcerpt")));
			}
		}
		String timestamp = text(root, "timestamp");
		return new TraceRecord(text(root, "runId"), (timestamp != null) ? Instant.parse(timestamp) : null,
				(source != null) ? new TraceRecord.SourceRef(text(source, "uri"), text(source, "ref"),
						text(source, "resolvedAt")) : null,
				(pack != null && pack.isObject())
						? new TraceRecord.PackRef(text(pack, "name"), text(pack, "contentHash")) : null,
				text(root, "goal"), text(root, "input"), text(root, "planner"), text(root, "trustTier"),
				List.copyOf(steps), root.path("success").asBoolean(), text(root, "error"), text(root, "outputExcerpt"));
	}

	private static TraceCase.SpecContext specContext(JsonNode context) {
		return new TraceCase.SpecContext(text(context, "sidecarContentHash"), number(context, "baselinePassRate"),
				number(context, "reevalPassRate"));
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return (value == null || value.isNull()) ? null : value.asText();
	}

	private static Double number(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return (value == null || !value.isNumber()) ? null : value.asDouble();
	}

}
