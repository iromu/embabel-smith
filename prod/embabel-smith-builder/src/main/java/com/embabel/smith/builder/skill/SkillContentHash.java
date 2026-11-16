/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import com.embabel.smith.builder.lifecycle.ContentHash;

/**
 * The lifecycle content hash of a published skill: the SHA-256 of its load-bearing
 * surface, which is the {@code SKILL.md} together with the {@code evals.json} it was
 * validated against. Both files are included because a skill's contract is its procedure
 * <em>and</em> the scenarios that established its baseline — a change to either
 * invalidates the baseline pass rate recorded beside them.
 *
 * @author Ivan Rodriguez
 */
public final class SkillContentHash {

	private SkillContentHash() {
	}

	/**
	 * The deterministic hash of a skill's load-bearing surface.
	 * @param skill the generated skill.
	 * @param evals the suite it was validated against.
	 * @return the lowercase hex digest.
	 */
	public static String of(Skill skill, EvalSuite evals) {
		return ContentHash.sha256Hex(skill.toSkillMd() + "\n---\n" + evals.toJson());
	}

}
