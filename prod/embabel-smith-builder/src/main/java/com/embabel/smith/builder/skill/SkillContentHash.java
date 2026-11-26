/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import com.embabel.smith.builder.lifecycle.ContentHash;

/**
 * The lifecycle content hash of a published skill: the SHA-256 of its load-bearing
 * surface, which is the {@code SKILL.md} together with the {@code evals.json} it was
 * validated against and the source of every script it publishes. All three are included
 * because a skill's contract is its procedure, the scenarios that established its
 * baseline, and the JavaScript that now carries part of the procedure — a change to any
 * of them invalidates the baseline pass rate recorded beside them, and a skill whose
 * script drifted from the procedure it documents is exactly the drift this hash exists to
 * catch.
 *
 * <p>
 * A skill with no scripts contributes nothing here, so its hash is the string the hash
 * was before scripts existed.
 *
 * @author Ivan Rodriguez
 */
public final class SkillContentHash {

	private SkillContentHash() {
	}

	/**
	 * The deterministic hash of a skill's load-bearing surface. The scripts are taken in
	 * path order rather than generation order, so a redraft that shuffled them is not
	 * mistaken for a skill that changed.
	 * @param skill the generated skill.
	 * @param evals the suite it was validated against.
	 * @return the lowercase hex digest.
	 */
	public static String of(Skill skill, EvalSuite evals) {
		StringBuilder surface = new StringBuilder(skill.toSkillMd()).append("\n---\n").append(evals.toJson());
		skill.scriptSources()
			.forEach((path, source) -> surface.append("\n---\n").append(path).append('\n').append(source));
		return ContentHash.sha256Hex(surface.toString());
	}

}
