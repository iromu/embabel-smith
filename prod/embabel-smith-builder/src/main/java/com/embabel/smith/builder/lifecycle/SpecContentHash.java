/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.lifecycle;

import java.util.List;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.DynamicTypeDef;

/**
 * A deterministic content hash of a agent spec's load-bearing surface: the goal (name +
 * output type), each action step (name, inputs, output, tool groups, prompt), and each
 * dynamic type.
 *
 * <p>
 * Any change to what the spec <em>is</em> — its prompt, its referenced types, its tool
 * groups, a step's declared references — changes the hash. That is the <em>content
 * drift</em> signal: the "silent breakage" doc 02 §5 caveat 4 warns about, where a spec
 * quietly diverges from the version its baseline eval was recorded against. Extracted
 * from POC 18.
 *
 * <p>
 * <b>A reference declaration is load-bearing, and it is written only when there is
 * one.</b> A step's {@code references} ask for the pack's skills to be in play for its
 * model call, so a pack whose only edit was to that list is a pack whose behaviour can
 * differ from the version its baseline was measured against — which is exactly what this
 * hash exists to notice. The clause is appended only when the step declares something,
 * and that is deliberate: a step with nothing to declare canonicalises as the byte
 * sequence it canonicalised before the component existed, so this adds a case to drift
 * detection rather than re-labelling every sidecar in the existing corpus as drifted. The
 * clause goes ahead of the prompt rather than after it, for the same reason it is
 * conditional — the prompt is free text and stays last so nothing appended beside it can
 * be mistaken for it.
 *
 * @author Ivan Rodriguez
 */
public final class SpecContentHash {

	private SpecContentHash() {
	}

	public static String of(AgentSpec pack) {
		StringBuilder canonical = new StringBuilder();
		canonical.append("goal:")
			.append(pack.goal().name())
			.append('|')
			.append(pack.goal().outputTypeName())
			.append('\n');
		for (ActionStepSpec step : pack.steps()) {
			canonical.append("step:")
				.append(step.name())
				.append("|in:")
				.append(step.inputTypeNames())
				.append("|out:")
				.append(step.outputTypeName())
				.append("|tools:")
				.append(step.toolGroups());
			// Ahead of the prompt, and only when there is a declaration to
			// write: a step with nothing to declare keeps the bytes — and so
			// the hash — its sidecar already records.
			List<String> references = step.references();
			if (!references.isEmpty()) {
				canonical.append("|refs:").append(references);
			}
			canonical.append("|prompt:").append(step.prompt()).append('\n');
		}
		for (DynamicTypeDef type : pack.types()) {
			canonical.append("type:").append(type.name()).append("|props:").append(type.ownProperties()).append('\n');
		}
		return ContentHash.sha256Hex(canonical.toString());
	}

}
