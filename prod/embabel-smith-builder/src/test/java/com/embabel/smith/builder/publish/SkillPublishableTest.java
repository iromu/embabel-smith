/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.publish;

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleStamper;
import com.embabel.smith.builder.skill.EvalSuite;
import com.embabel.smith.builder.skill.Skill;
import com.embabel.smith.builder.skill.SkillContentHash;
import com.embabel.smith.builder.skill.SkillMdValidator;
import com.embabel.smith.builder.skill.SkillScript;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

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

	/**
	 * The two model-authored files of this fixture as the publish path wrote them before
	 * a skill could carry scripts — 203 and 270 bytes, captured from this same publisher
	 * at the commit before task 2.2 landed. They are the baseline the scriptless
	 * assertion is made against.
	 */
	private static final String SKILL_MD_BEFORE_SCRIPTS = "---\nname: " + NAME + "\ndescription: \"Handle chilled goods"
			+ " receipts and reject a temperature breach.\"\n---\n\n## Workflow\n1. Read the carrier temperature"
			+ " record.\n2. Reject a breach and escalate.\n";

	private static final String EVALS_JSON_BEFORE_SCRIPTS = "{\n  \"skill_name\" : \"" + NAME
			+ "\",\n  \"evals\" : [ {\n"
			+ "    \"id\" : 1,\n    \"prompt\" : \"receive a chilled pallet\",\n    \"expected_output\" :"
			+ " \"accepted after a check\",\n    \"files\" : [ ],\n    \"expectations\" : [ \"must apply FEFO\","
			+ " \"must record the temperature\" ]\n  } ]\n}";

	/** A script that asks for nothing the script sandbox does not give it. */
	private static final String CHECK_SCRIPT = "const limit = Number(args[0] ?? '4');\n"
			+ "const reading = Number(stdin.trim());\n" + "if (!Number.isFinite(reading)) {\n"
			+ "  console.error('no temperature reading on stdin');\n" + "  process.exit(2);\n" + "}\n"
			+ "console.log(reading > limit ? 'reject and escalate' : 'accept and file the record');\n";

	private final SkillPublishable publishable = new SkillPublishable(SKILL, EVALS,
			new LifecycleStamper().stamp("cafebabe000000000000000000000000000000000000000000000000000000", 0.75));

	@Test
	void aSkillPublishesToAFolderAsANamedDirectoryWithItsFiles(@TempDir Path library) throws Exception {
		FolderPublisher publisher = new FolderPublisher(PublishTarget.parse("folder:" + library));

		PublishResult result = publisher.publish(this.publishable, false, false);

		assertThat(result.location()).isEqualTo(library.resolve(NAME).toString());
		assertThat(result.pushed()).isFalse();
		assertThat(Path.of(result.location()).resolve("SKILL.md")).isRegularFile();
		assertThat(Path.of(result.location()).resolve("evals.json")).isRegularFile();
		assertThat(Path.of(result.location()).resolve("lifecycle.yml")).isRegularFile();
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
		assertThat(Path.of(replaced.location()).resolve("SKILL.md")).isRegularFile();
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

	@Test
	void anInstructionsOnlySkillPublishesTheBytesItPublishedBeforeScriptsExisted(@TempDir Path library)
			throws Exception {
		new FolderPublisher(PublishTarget.parse("folder:" + library)).publish(this.publishable, false, false);

		// The published tree, byte for byte, as the publish path wrote it before a skill
		// could carry scripts. The two literals below were captured from this same
		// publish
		// path at the commit before task 2.2 (the dump is recorded in the change notes),
		// so
		// the assertion is against what a build used to write and not against a re-run of
		// the
		// code that writes it.
		PublishedTree published = read(library.resolve(NAME));
		assertThat(published.names()).containsExactlyInAnyOrder("SKILL.md", "evals.json", "lifecycle.yml");
		assertThat(published.contents().get("SKILL.md")).isEqualTo(SKILL_MD_BEFORE_SCRIPTS);
		assertThat(published.contents().get("evals.json")).isEqualTo(EVALS_JSON_BEFORE_SCRIPTS);

		// The sidecar is the same writer with the same three fields, and the fourth is
		// the
		// clock — so everything the writer decides is pinned, and only the timestamp is
		// not.
		List<String> lifecycle = published.contents().get("lifecycle.yml").lines().toList();
		assertThat(lifecycle).startsWith("---",
				"contentHash: \"cafebabe000000000000000000000000000000000000000000000000000000\"",
				"baselinePassRate: 0.75", "state: \"INCUBATING\"");
		assertThat(lifecycle).anyMatch((line) -> line.startsWith("updatedAt: \""));
	}

	@Test
	void aSkillWithScriptsPublishesThemBesideTheThreeFilesItAlwaysWrote(@TempDir Path library) throws Exception {
		Skill withScript = new Skill(NAME, SKILL.description(), SKILL.body(),
				List.of(new SkillScript("scripts/check-temperature.js", CHECK_SCRIPT)));

		new FolderPublisher(PublishTarget.parse("folder:" + library))
			.publish(new SkillPublishable(withScript, EVALS, lifecycleFor(withScript)), false, false);

		PublishedTree published = read(library.resolve(NAME));
		assertThat(published.names()).containsExactlyInAnyOrder("SKILL.md", "evals.json", "lifecycle.yml", "scripts/",
				"scripts/check-temperature.js");
		// The script is written verbatim, and the three files it now shares the folder
		// with
		// are byte-identical to the ones published without it: scripts are files in the
		// folder, not a field of SKILL.md, so nothing about the existing three changes.
		assertThat(published.contents().get("scripts/check-temperature.js")).isEqualTo(CHECK_SCRIPT);
		assertThat(published.contents().get("SKILL.md")).isEqualTo(SKILL_MD_BEFORE_SCRIPTS);
		assertThat(published.contents().get("evals.json")).isEqualTo(EVALS_JSON_BEFORE_SCRIPTS);
	}

	@Test
	void aFolderThatCarriesScriptsStillCarriesValidFrontmatterAndALifecycleSidecar(@TempDir Path library)
			throws Exception {
		Skill withScript = new Skill(NAME, SKILL.description(), SKILL.body(),
				List.of(new SkillScript("scripts/check-temperature.js", CHECK_SCRIPT)));

		new FolderPublisher(PublishTarget.parse("folder:" + library))
			.publish(new SkillPublishable(withScript, EVALS, lifecycleFor(withScript)), false, false);

		Path skillDir = library.resolve(NAME);
		String skillMd = Files.readString(skillDir.resolve("SKILL.md"));
		String frontmatter = skillMd.substring(4, skillMd.indexOf("\n---\n", 4));
		Map<String, Object> loaded = new Yaml().load(frontmatter);
		assertThat(loaded.get("name")).isEqualTo(NAME);
		assertThat(loaded.get("description")).isEqualTo(SKILL.description());
		assertThat(SkillMdValidator.validate(NAME, (String) loaded.get("description"))).isEmpty();
		assertThat(EvalSuite.parse(Files.readString(skillDir.resolve("evals.json")))).isEqualTo(EVALS);

		String lifecycle = Files.readString(skillDir.resolve("lifecycle.yml"));
		assertThat(lifecycle).contains("state: \"INCUBATING\"").contains("baselinePassRate: 0.75");
		// The baseline is keyed on the script as well: a skill whose script changed is
		// not
		// the skill that pass rate was measured on, and the drift signal must say so.
		assertThat(lifecycle).contains("contentHash: \"" + SkillContentHash.of(withScript, EVALS) + "\"");
		assertThat(SkillContentHash.of(withScript, EVALS)).isNotEqualTo(SkillContentHash.of(SKILL, EVALS));
	}

	@Test
	void anOverwriteLeavesNoScriptFromThePublishItReplaces(@TempDir Path library) throws Exception {
		FolderPublisher publisher = new FolderPublisher(PublishTarget.parse("folder:" + library));
		Skill withScript = new Skill(NAME, SKILL.description(), SKILL.body(),
				List.of(new SkillScript("scripts/check-temperature.js", CHECK_SCRIPT)));
		publisher.publish(new SkillPublishable(withScript, EVALS, lifecycleFor(withScript)), false, false);

		publisher.publish(this.publishable, true, false);

		// The replacing build wrote no script, so the folder must not still carry the old
		// one: a reader would run code the published build never declared and never
		// scanned.
		assertThat(read(library.resolve(NAME)).names()).containsExactlyInAnyOrder("SKILL.md", "evals.json",
				"lifecycle.yml");
	}

	private static LifecycleMetadata lifecycleFor(Skill skill) {
		return new LifecycleStamper().stamp(SkillContentHash.of(skill, EVALS), 0.75);
	}

	/**
	 * The published folder as the two collections a test should assert on: every entry it
	 * contains — folders included, with a trailing slash, because a publisher that
	 * creates an empty one has changed the published artifact without changing any file —
	 * and the whole content of every file. Listing the tree, rather than the files
	 * someone remembered to name, is what makes the scriptless assertion a statement
	 * about the absence of a folder.
	 */
	private static PublishedTree read(Path skillDir) throws IOException {
		Map<String, String> contents = new TreeMap<>();
		Set<String> entries = new TreeSet<>();
		try (Stream<Path> paths = Files.walk(skillDir)) {
			paths.filter((path) -> !path.equals(skillDir)).forEach((path) -> {
				entries.add(entry(skillDir, path));
				if (Files.isRegularFile(path)) {
					contents.put(entry(skillDir, path), readString(path));
				}
			});
		}
		return new PublishedTree(Set.copyOf(entries), Map.copyOf(contents));
	}

	private static String entry(Path skillDir, Path path) {
		String relative = skillDir.relativize(path).toString().replace('\\', '/');
		return Files.isDirectory(path) ? relative + "/" : relative;
	}

	private static String readString(Path file) {
		try {
			return Files.readString(file);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private record PublishedTree(Set<String> names, Map<String, String> contents) {
	}

}
