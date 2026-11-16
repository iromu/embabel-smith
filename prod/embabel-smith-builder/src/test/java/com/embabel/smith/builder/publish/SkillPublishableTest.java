/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.publish;

import com.embabel.smith.builder.lifecycle.LifecycleStamper;
import com.embabel.smith.builder.skill.EvalSuite;
import com.embabel.smith.builder.skill.Skill;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Offline tests for the skill writer (task 3.2): a skill publishes to a {@code folder:}
 * target as a directory named after it, carrying its {@code SKILL.md}, the
 * {@code evals.json} it was validated against, and the lifecycle sidecar — under the same
 * collision gate and {@code --overwrite} rule a published pack is subject to.
 *
 * @author Ivan Rodriguez
 */
class SkillPublishableTest {

	private static final String NAME = "cold-store-goods-receipt";

	private static final Skill SKILL = new Skill(NAME, "Handle chilled goods receipts and reject a temperature breach.",
			"## Workflow\n1. Read the carrier temperature record.\n2. Reject a breach and escalate.\n");

	private static final EvalSuite EVALS = new EvalSuite(NAME, List.of(new EvalSuite.Eval(1, "receive a chilled pallet",
			"accepted after a check", List.of(), List.of("must apply FEFO", "must record the temperature"))));

	private final SkillPublishable publishable = new SkillPublishable(SKILL, EVALS,
			new LifecycleStamper().stamp("cafebabe000000000000000000000000000000000000000000000000000000", 0.75));

	@Test
	void aSkillPublishesToAFolderAsANamedDirectoryWithItsFiles(@TempDir Path library) throws Exception {
		FolderPublisher publisher = new FolderPublisher(PublishTarget.parse("folder:" + library));

		PublishResult result = publisher.publish(this.publishable, false, false);

		assertThat(result.location()).isEqualTo(library.resolve(NAME));
		assertThat(result.pushed()).isFalse();
		assertThat(result.location().resolve("SKILL.md")).isRegularFile();
		assertThat(result.location().resolve("evals.json")).isRegularFile();
		assertThat(result.location().resolve("lifecycle.yml")).isRegularFile();
	}

	@Test
	void thePublishedFilesAreTheSkillAndTheSuiteThatValidatedIt(@TempDir Path library) throws Exception {
		new FolderPublisher(PublishTarget.parse("folder:" + library)).publish(this.publishable, false, false);

		Path skillDir = library.resolve(NAME);
		assertThat(Files.readString(skillDir.resolve("SKILL.md"))).startsWith("---\nname: " + NAME)
			.contains("## Workflow");
		assertThat(EvalSuite.parse(Files.readString(skillDir.resolve("evals.json")))).isEqualTo(EVALS);
		assertThat(Files.readString(skillDir.resolve("lifecycle.yml"))).contains("INCUBATING").contains("0.75");
	}

	@Test
	void aSameNamedSkillCollidesUnlessOverwritten(@TempDir Path library) throws Exception {
		FolderPublisher publisher = new FolderPublisher(PublishTarget.parse("folder:" + library));
		publisher.publish(this.publishable, false, false);

		assertThatThrownBy(() -> publisher.publish(this.publishable, false, false))
			.isInstanceOf(CollisionException.class)
			.hasMessageContaining(NAME)
			.hasMessageContaining("--overwrite");

		PublishResult replaced = publisher.publish(this.publishable, true, false);
		assertThat(replaced.location().resolve("SKILL.md")).isRegularFile();
	}

	@Test
	void overwriteReplacesThePreviousSkillRatherThanMergingIntoIt(@TempDir Path library) throws Exception {
		FolderPublisher publisher = new FolderPublisher(PublishTarget.parse("folder:" + library));
		publisher.publish(this.publishable, false, false);
		Skill changed = new Skill(NAME, "A revised description of the same skill.",
				"## Workflow\n1. Only the new step.\n");
		SkillPublishable revised = new SkillPublishable(changed, new EvalSuite(NAME, List.of()),
				new LifecycleStamper().stamp("deadbeef000000000000000000000000000000000000000000000000000000", 1.0));

		publisher.publish(revised, true, false);

		String skillMd = Files.readString(library.resolve(NAME).resolve("SKILL.md"));
		assertThat(skillMd).contains("A revised description of the same skill.")
			.contains("Only the new step.")
			.doesNotContain("Read the carrier temperature record");
		assertThat(EvalSuite.parse(Files.readString(library.resolve(NAME).resolve("evals.json"))).evals()).isEmpty();
	}

}
