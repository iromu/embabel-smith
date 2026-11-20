/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.innerloop;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Renders corpus lines into the JSON a detection policy is run against.
 *
 * <p>
 * The rendering is the evidence boundary of the inner loop. It carries what the product's
 * {@code Detector} sees — the trace fields, plus the sidecar and re-eval facts it takes
 * as arguments — and it deliberately drops {@code label} and {@code label_reason}. Those
 * two are the answer a policy is scored against: a script that could read them would
 * report perfect precision and recall without deciding anything, and the loop would be
 * measuring its own leak.
 *
 * @author Ivan Rodriguez
 */
public final class PolicyInput {

	private static final ObjectMapper JSON = new ObjectMapper();

	private PolicyInput() {
	}

	/**
	 * The stdin document for one run of one policy.
	 * @param cases the corpus lines to score; the labels are not included
	 * @return the JSON to hand the script as {@code stdin}.
	 */
	public static String stdinJson(List<TraceCase> cases) {
		ObjectNode root = JSON.createObjectNode();
		ArrayNode out = root.putArray("cases");
		for (TraceCase traceCase : cases) {
			out.add(caseNode(traceCase));
		}
		return root.toString();
	}

	private static ObjectNode caseNode(TraceCase traceCase) {
		ObjectNode node = JSON.createObjectNode();
		node.put("runId", traceCase.runId());
		node.put("success", traceCase.trace().success());
		node.put("error", traceCase.trace().error());
		ArrayNode steps = node.putArray("steps");
		for (var step : traceCase.trace().steps()) {
			ObjectNode stepNode = steps.addObject();
			stepNode.put("status", step.status());
			stepNode.put("durationMs", step.durationMs());
		}
		var pack = traceCase.trace().pack();
		if (pack != null) {
			node.putObject("pack").put("contentHash", pack.contentHash());
		}
		else {
			node.putNull("pack");
		}
		TraceCase.SpecContext context = traceCase.specContext();
		if (context != null) {
			ObjectNode spec = node.putObject("spec_context");
			spec.put("sidecarContentHash", context.sidecarContentHash());
			putNumber(spec, "baselinePassRate", context.baselinePassRate());
			putNumber(spec, "reevalPassRate", context.reevalPassRate());
		}
		return node;
	}

	private static void putNumber(ObjectNode node, String field, Double value) {
		if (value != null) {
			node.put(field, value.doubleValue());
		}
		else {
			node.putNull(field);
		}
	}

}
