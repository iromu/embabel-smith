/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder;

import com.embabel.smith.builder.core.BuildTarget;
import com.embabel.smith.builder.core.GenerationTechnique;

/**
 * The per-build knobs (design D1): the output target, the generation technique, the
 * refinement iteration bound, the eval score gate, the skill lift gate, and the publish
 * flags. Defaults: the agent-spec target, the single-shot technique, up to 3 refinement
 * rounds, a score gate of 1.0 (every eval task must pass), a lift minimum of 0 (any
 * positive lift publishes), no overwrite, no push.
 *
 * <p>
 * The two gates belong to different targets: {@link #scoreGate()} gates the agent-spec
 * build's deterministic eval, {@link #minLift()} gates the skill build's
 * with/without-skill comparison. Each target reads its own and ignores the other.
 *
 * @param maxRefine the maximum number of refinement rounds to run.
 * @param scoreGate the minimum eval score required to pass the build gate.
 * @param overwrite whether to overwrite an existing published pack.
 * @param push whether to push the published pack to a remote.
 * @param technique the generation technique the build compiles with; {@code null} means
 * the {@link GenerationTechnique#SINGLE_SHOT} default.
 * @param target the artifact the build produces; {@code null} means the
 * {@link BuildTarget#AGENT_SPEC} default.
 * @param minLift the skill lift required to pass the skill build's gate; the with-skill
 * pass rate must exceed the no-skill baseline by more than this.
 * @param model the LLM the whole build runs on — generation, eval and refinement alike;
 * {@code null} or empty means each call uses the configured default.
 * @author Ivan Rodriguez
 */
public record BuildOptions(int maxRefine, double scoreGate, boolean overwrite, boolean push,
		GenerationTechnique technique, BuildTarget target, double minLift, String model) {

	/** Default value for {@link #maxRefine}: up to 3 refinement rounds. */
	public static final int DEFAULT_MAX_REFINE = 3;

	/** Default value for {@link #scoreGate}: a score gate of 1.0. */
	public static final double DEFAULT_SCORE_GATE = 1.0;

	/**
	 * Default value for {@link #minLift}: any lift that beats the baseline at all
	 * publishes.
	 */
	public static final double DEFAULT_MIN_LIFT = 0.0;

	public BuildOptions {
		technique = (technique != null) ? technique : GenerationTechnique.SINGLE_SHOT;
		target = (target != null) ? target : BuildTarget.AGENT_SPEC;
	}

	/**
	 * The form that names no model, so every call uses the configured default.
	 */
	public BuildOptions(int maxRefine, double scoreGate, boolean overwrite, boolean push, GenerationTechnique technique,
			BuildTarget target, double minLift) {
		this(maxRefine, scoreGate, overwrite, push, technique, target, minLift, null);
	}

	public static BuildOptions defaults() {
		return new BuildOptions(DEFAULT_MAX_REFINE, DEFAULT_SCORE_GATE, false, false, GenerationTechnique.SINGLE_SHOT,
				BuildTarget.AGENT_SPEC, DEFAULT_MIN_LIFT);
	}
}
