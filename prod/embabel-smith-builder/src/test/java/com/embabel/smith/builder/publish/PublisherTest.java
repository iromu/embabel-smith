/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.publish;

import com.embabel.smith.builder.BuildResult;
import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleStamper;
import com.embabel.smith.builder.skill.EvalSuite;
import com.embabel.smith.builder.skill.Skill;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.samples.SampleAgentSpecs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Offline tests of publishing (task 4.3): a folder publish writes the pack plus its
 * sidecar; a git publish commits into a local bare repository and (with push) lands the
 * pack in the remote; a same-named artifact collides unless {@code --overwrite} replaces
 * it. Both publishable shapes are exercised against both targets — the skill cases cover
 * the {@code Publishable} generalization over {@code git:}, which no test reached before.
 *
 * <p>
 * A publish's location is asserted in the source's own terms, and for git it is read
 * after {@code close()} has deleted the working tree, because that is when a reader sees
 * it.
 */
class PublisherTest {

	private static final String SKILL_NAME = "cold-store-goods-receipt";

	private final AgentSpec pack = SampleAgentSpecs.explainer();

	private final LifecycleMetadata lifecycle = new LifecycleStamper().stamp(pack, 1.0);

	private final SkillPublishable skill = skillPublishable(
			"Handle chilled goods receipts and reject a temperature" + " breach.",
			"Read the carrier temperature record.");

	@Test
	void folderPublishWritesThePackAndSidecar(@TempDir Path library) throws Exception {
		FolderPublisher publisher = new FolderPublisher(PublishTarget.parse("folder:" + library));

		PublishResult result = publisher.publish(pack, lifecycle, false, false);

		assertThat(result.location()).isEqualTo(library.resolve("explainer").toString());
		assertThat(Path.of(result.location()).resolve("manifest.yml")).isRegularFile();
		assertThat(Path.of(result.location()).resolve("lifecycle.yml")).isRegularFile();
		assertThat(result.pushed()).isFalse();
	}

	@Test
	void aFolderCollisionBlocksUnlessOverwrite(@TempDir Path library) throws Exception {
		FolderPublisher publisher = new FolderPublisher(PublishTarget.parse("folder:" + library));
		publisher.publish(pack, lifecycle, false, false);

		assertThatThrownBy(() -> publisher.publish(pack, lifecycle, false, false))
			.isInstanceOf(CollisionException.class)
			.hasMessageContaining("explainer");

		PublishResult replaced = publisher.publish(pack, lifecycle, true, false);
		assertThat(Path.of(replaced.location()).resolve("manifest.yml")).isRegularFile();
	}

	@Test
	void gitPublishCommitsAndPushesIntoALocalRepo(@TempDir Path tmp) throws Exception {
		Path bare = tmp.resolve("repo.git");
		runGit("init", "--bare", bare.toString());

		GitPublisher publisher = new GitPublisher(PublishTarget.parse("git:" + bare));
		try {
			// Local commit only (no push): the commit exists, the remote does not.
			PublishResult local = publisher.publish(pack, lifecycle, false, false);
			assertThat(local.pushed()).isFalse();

			// A same-named pack now exists in the working tree → collision without
			// overwrite.
			assertThatThrownBy(() -> publisher.publish(pack, lifecycle, false, false))
				.isInstanceOf(CollisionException.class);

			// Overwrite + push: the commit lands in the bare repository.
			PublishResult pushed = publisher.publish(pack, lifecycle, true, true);
			assertThat(pushed.pushed()).isTrue();

			Path verify = tmp.resolve("verify");
			runGit("clone", "--quiet", bare.toString(), verify.toString());
			assertThat(verify.resolve("explainer").resolve("manifest.yml")).isRegularFile();
			assertThat(verify.resolve("explainer").resolve("lifecycle.yml")).isRegularFile();

			// The commit message names the pack and its baseline score (design D3).
			assertThat(runGit("-C", verify.toString(), "log", "-1", "--pretty=%s"))
				.isEqualTo("smith: publish " + pack.name() + " (baseline 1.000)");

			// The reported location names the source, not the deleted working tree.
			assertThat(pushed.location()).isEqualTo("git:" + bare + "#explainer");

			// And that is what a reader is shown: the build report prints the location it
			// was given, so a git build's `published:` line names the git source.
			assertThat(BuildResult.built(pack.name(), 1.0, null, this.lifecycle, pushed).report())
				.contains("published:   git:" + bare + "#explainer (pushed)");
		}
		finally {
			publisher.close();
		}
	}

	@Test
	void aSkillPublishedToGitLandsInACommitThatOutlivesThePublisher(@TempDir Path tmp) throws Exception {
		Path bare = tmp.resolve("skill-repo.git");
		runGit("init", "--bare", bare.toString());

		GitPublisher publisher = new GitPublisher(PublishTarget.parse("git:" + bare));
		PublishResult result;
		try {
			// Push so the commit reaches the bare repository — a default publish commits
			// only in the working tree, which close() then deletes.
			result = publisher.publish(this.skill, false, true);
			assertThat(result.pushed()).isTrue();
		}
		finally {
			publisher.close();
		}

		// Read back from a fresh clone: the commit, not the publisher's copy of it.
		Path verify = tmp.resolve("verify");
		runGit("clone", "--quiet", bare.toString(), verify.toString());
		assertThat(verify.resolve(SKILL_NAME).resolve("SKILL.md")).isRegularFile();
		assertThat(verify.resolve(SKILL_NAME).resolve("evals.json")).isRegularFile();
		assertThat(verify.resolve(SKILL_NAME).resolve("lifecycle.yml")).isRegularFile();
		assertThat(runGit("-C", verify.toString(), "log", "-1", "--pretty=%s"))
			.isEqualTo("smith: publish " + SKILL_NAME + " (baseline 0.750)");

		// Asserted after close(): the report is read once the tree it used to point into
		// is gone. The local bare repository is itself under a temp dir, so the check is
		// that the location names the source rather than the working tree, and that the
		// string resolves to nothing on disk.
		assertThat(result.location()).isEqualTo("git:" + bare + "#" + SKILL_NAME);
		assertThat(result.location()).doesNotContain("smith-git-publish-");
		assertThat(Path.of(result.location())).doesNotExist();
	}

	@Test
	void aSkillCollidesInAGitSourceAndOverwriteReplacesTheDirectory(@TempDir Path tmp) throws Exception {
		Path bare = tmp.resolve("skill-repo.git");
		runGit("init", "--bare", bare.toString());

		GitPublisher publisher = new GitPublisher(PublishTarget.parse("git:" + bare));
		try {
			publisher.publish(this.skill, false, false);

			assertThatThrownBy(() -> publisher.publish(this.skill, false, false)).isInstanceOf(CollisionException.class)
				.hasMessageContaining(SKILL_NAME)
				.hasMessageContaining("--overwrite");

			publisher.publish(skillPublishable("A revised description of the same skill.", "Only the new step."), true,
					true);

			Path verify = tmp.resolve("verify");
			runGit("clone", "--quiet", bare.toString(), verify.toString());
			String skillMd = Files.readString(verify.resolve(SKILL_NAME).resolve("SKILL.md"));
			assertThat(skillMd).contains("A revised description of the same skill.")
				.doesNotContain("Read the carrier temperature record");
		}
		finally {
			publisher.close();
		}
	}

	/**
	 * Decision 5 of change publish-keeps-versions: the git destination needs no
	 * versioning machinery of its own. Two publishes of one name are two commits in the
	 * target repository, and the {@code overwrite} that replaces the working copy in the
	 * clone does not lose the first publish — it stays readable out of history.
	 */
	@Test
	void aGitSourceKeepsEveryPublishOfOneNameAsACommit(@TempDir Path tmp) throws Exception {
		Path bare = tmp.resolve("versions.git");
		runGit("init", "--bare", bare.toString());

		GitPublisher publisher = new GitPublisher(PublishTarget.parse("git:" + bare));
		try {
			publisher.publish(this.skill, false, true);
			publisher.publish(skillPublishable("A revised description of the same skill.", "Only the new step."), true,
					true);
		}
		finally {
			publisher.close();
		}

		Path verify = tmp.resolve("verify");
		runGit("clone", "--quiet", bare.toString(), verify.toString());
		assertThat(runGit("-C", verify.toString(), "rev-list", "--count", "HEAD")).isEqualTo("2");

		// The working copy holds the second publish.
		assertThat(Files.readString(verify.resolve(SKILL_NAME).resolve("SKILL.md")))
			.contains("A revised description of the same skill.");

		// The first publish is read out of the repository's history, not a working tree.
		assertThat(runGit("-C", verify.toString(), "show", "HEAD~1:" + SKILL_NAME + "/SKILL.md"))
			.contains("Handle chilled goods receipts")
			.contains("Read the carrier temperature record")
			.doesNotContain("A revised description of the same skill.");
	}

	/**
	 * A skill with its drafted suite and a 0.75 baseline, for the git cases. The baseline
	 * is in the commit message, so the value is asserted rather than incidental.
	 */
	private static SkillPublishable skillPublishable(String description, String workflowStep) {
		Skill skill = new Skill(SKILL_NAME, description, "## Workflow\n1. " + workflowStep + "\n");
		EvalSuite evals = new EvalSuite(SKILL_NAME, List.of(new EvalSuite.Eval(1, "receive a chilled pallet",
				"accepted after a check", List.of(), List.of("must apply FEFO"))));
		return new SkillPublishable(skill, evals,
				new LifecycleStamper().stamp("cafebabe000000000000000000000000000000000000000000000000000000", 0.75));
	}

	private static String runGit(String... args) {
		List<String> command = new java.util.ArrayList<>(List.of("git", "-c", "core.askpass=false"));
		command.addAll(List.of(args));
		try {
			Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
			String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			int exit = process.waitFor();
			if (exit != 0) {
				throw new IllegalStateException("git " + String.join(" ", args) + " failed: " + out);
			}
			return out.trim();
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

}
