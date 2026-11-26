/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.publish;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleStamper;
import com.embabel.smith.builder.skill.EvalSuite;
import com.embabel.smith.builder.skill.Skill;
import com.embabel.smith.builder.skill.SkillScript;

/**
 * The skill writer: an Agent Skill folder as a {@link Publishable}. A skill is a
 * directory named after the skill carrying its required {@code SKILL.md}, the
 * {@code evals.json} suite it was validated against, and the {@code lifecycle.yml}
 * sidecar — the same collision and no-push semantics as a pack, over a different
 * directory shape. When the skill carries scripts they are written into a
 * {@code scripts/} folder beside those three files; when it carries none the folder is
 * not created, so an instructions-only skill publishes exactly the tree it always
 * published.
 *
 * @author Ivan Rodriguez
 */
public class SkillPublishable implements Publishable {

	private final Skill skill;

	private final EvalSuite evals;

	private final LifecycleMetadata lifecycle;

	private final LifecycleStamper stamper = new LifecycleStamper();

	public SkillPublishable(Skill skill, EvalSuite evals, LifecycleMetadata lifecycle) {
		this.skill = skill;
		this.evals = evals;
		this.lifecycle = lifecycle;
	}

	@Override
	public String name() {
		return this.skill.name();
	}

	@Override
	public double baselinePassRate() {
		return this.lifecycle.baselinePassRate();
	}

	@Override
	public void writeTo(Path directory) throws IOException {
		Files.writeString(directory.resolve("SKILL.md"), this.skill.toSkillMd(), StandardCharsets.UTF_8);
		Files.writeString(directory.resolve("evals.json"), this.evals.toJson(), StandardCharsets.UTF_8);
		for (SkillScript script : this.skill.scripts()) {
			// The path is already confined to scripts/<name>.js by SkillScript, so this
			// resolves inside the published folder whatever the model declared.
			Path target = directory.resolve(script.path());
			Files.createDirectories(target.getParent());
			Files.writeString(target, script.content(), StandardCharsets.UTF_8);
		}
		this.stamper.write(directory, this.lifecycle);
	}

}
