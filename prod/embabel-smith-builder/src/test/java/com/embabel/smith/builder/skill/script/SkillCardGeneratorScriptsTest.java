/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill.script;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import com.embabel.agent.skills.Skills;
import com.embabel.agent.skills.script.ScriptArtifact;
import com.embabel.agent.skills.script.ScriptExecutionResult;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shipped {@code skill-card-generator} skill's three JavaScript scripts run by this
 * module's own {@link GraalJsSkillScriptExecutionEngine} rather than by Node: the asset
 * discovery report, the rendered governance card and the pre-submission gate all execute
 * against the sandbox's globals — {@code stdin}, {@code console}, {@code process.exit},
 * {@code readFile}, {@code writeFile} — with no {@code require}, no filesystem and no
 * environment.
 *
 * <p>
 * <b>The folder.</b> The skill is copied whole out of this module's classpath resources
 * into a temp pack root, so the scripts run beside the {@code references/} they ship
 * with. That matters rather than being tidiness: {@code discover_assets.js} appends the
 * style guide and the card layout by reading them from its own folder, so a folder
 * carrying only {@code scripts/} would print not-found lines and prove something other
 * than what is claimed here.
 *
 * <p>
 * <b>What each case pins.</b> Every expected string was read out of the script it came
 * from. The exit codes are the scripts' documented contract (0, 1, 2, 3), the stderr
 * strings are the marker classes and validation reasons they emit, and the artifact is
 * the card the renderer wrote rather than the card it echoed. The last case asserts no
 * run timed out, which is the assertion that these scripts fit the statement and
 * wall-clock budgets and never reached for a host construct.
 *
 * <p>
 * <b>Offline.</b> No model, no endpoint, no network: this is the engine and the shipped
 * scripts, in this JVM.
 *
 * @author Ivan Rodriguez
 */
class SkillCardGeneratorScriptsTest {

	private static final String SKILL_NAME = "skill-card-generator";

	private static final String RESOURCE_ROOT = "skills/" + SKILL_NAME;

	/** A card with every marker resolved, which is what the submission gate accepts. */
	private static final String CLEAN_CARD = """
			## Description: <br>
			Reads an invoice and reports its line items. <br>

			## Owner
			NVIDIA <br>

			### License/Terms of Use: <br>
			Apache 2.0 <br>

			## Reference(s): <br>
			- [Source](https://github.com/acme/invoices) <br>
			""";

	/**
	 * A card whose inferred owner field was never confirmed, so it must not be submitted.
	 */
	private static final String MARKED_CARD = """
			## Owner
			<span style="color:#d73a49">NVIDIA</span> <!-- VERIFY: inferred --> <br>
			""";

	private final GraalJsSkillScriptExecutionEngine engine = new GraalJsSkillScriptExecutionEngine();

	/**
	 * The door an agent actually comes through: this folder loads through the directory
	 * loader, so the model is offered one tool per script. It is a different claim from
	 * the runs below — a folder the loader refuses, for a frontmatter block it cannot
	 * parse or a file the body names that the folder does not carry, still executes its
	 * scripts when handed to the engine directly while offering an agent nothing but the
	 * generic skill tools. So the scripts would all be green and unreachable.
	 * @param packRoot where to unpack the shipped skill for this case
	 */
	@Test
	void offersOneToolPerScriptToAnAgent(@TempDir Path packRoot) {
		copySkillFolder(packRoot);

		Skills skills = SkillScriptFixtures.load(packRoot, this.engine);

		assertThat(SkillScriptFixtures.toolNames(skills)).contains(SKILL_NAME + "_discover_assets",
				SKILL_NAME + "_render_card", SKILL_NAME + "_validate_submission");
	}

	@Test
	void rendersTheCardThroughTheSandbox(@TempDir Path packRoot) {
		copySkillFolder(packRoot);

		ScriptExecutionResult.Success run = succeeded(run(packRoot, "render_card.js", contextOf()));

		assertThat(run.getExitCode()).as("a valid context renders").isZero();
		assertThat(run.getStdout()).contains("## Description:").contains("## Skill Version(s):");
		// The card the script wrote with writeFile, read back from the run's artifact
		// directory rather than from what it echoed: the artifact is the deliverable.
		List<ScriptArtifact> artifacts = run.getArtifacts();
		assertThat(artifacts).as("one artifact, the rendered card").hasSize(1);
		assertThat(artifacts.get(0).getName()).isEqualTo("invoice-reader-card.md");
		String artifact = read(artifacts.get(0).getPath());
		assertThat(artifact).contains("## Description:").contains("## Skill Version(s):");
		// Same bytes, not the same words retyped.
		assertThat(run.getStdout()).contains(artifact);
	}

	@Test
	void refusesAnIncompleteContext(@TempDir Path packRoot) {
		copySkillFolder(packRoot);

		ScriptExecutionResult.Success run = succeeded(
				run(packRoot, "render_card.js", contextOf("description_sentence", "output")));

		assertThat(run.getExitCode()).as("a validation failure is exit 3, not a crash").isEqualTo(3);
		assertThat(run.getStderr()).contains("Context validation failed")
			.contains("missing required key: 'description_sentence'")
			.contains("missing required key: 'output'");
		assertThat(run.getArtifacts()).as("nothing rendered, so nothing written").isEmpty();
	}

	@Test
	void cleanCardPassesSubmissionGate(@TempDir Path packRoot) {
		copySkillFolder(packRoot);

		ScriptExecutionResult.Success run = succeeded(run(packRoot, "validate_submission.js", CLEAN_CARD));

		assertThat(run.getExitCode()).as("no markers left").isZero();
		assertThat(run.getStdout()).contains("OK: the card has no unresolved verify/select markers.");
	}

	@Test
	void markedCardFailsSubmissionGate(@TempDir Path packRoot) {
		copySkillFolder(packRoot);

		ScriptExecutionResult.Success run = succeeded(run(packRoot, "validate_submission.js", MARKED_CARD));

		assertThat(run.getExitCode()).as("a reviewer left work undone, which is exit 1").isEqualTo(1);
		// Both marker classes the card carries, named by the class rather than by the
		// comment text, which is what tells the reviewer what to delete.
		assertThat(run.getStderr()).contains("FAIL").contains("[verify-comment]").contains("[verify-style]");
	}

	@Test
	void discoversSignalsFromStagedFiles(@TempDir Path packRoot) throws IOException {
		copySkillFolder(packRoot);
		Path stagedRoot = packRoot.resolve("target-repo");
		Path skillDefinition = stagedRoot.resolve("SKILL.md");
		Files.createDirectories(stagedRoot.resolve("docs"));
		Files.writeString(skillDefinition, """
				---
				name: invoice-reader
				description: Reads an invoice and reports its line items.
				---

				# Invoice Reader

				**Invoice to read**: $ARGUMENTS
				""", StandardCharsets.UTF_8);
		Path docsIndex = stagedRoot.resolve("docs").resolve("index.md");
		Files.writeString(docsIndex, "# Documentation index\n", StandardCharsets.UTF_8);
		String manifest = """
				{
				  "target": "/skills/invoice-reader",
				  "repo_root": "%s",
				  "git": { "describe": "v1.4.0", "last_commit_sha": "0f3c9a1" },
				  "files": [
				    { "path": "SKILL.md", "scope": "skill" },
				    { "path": "docs/index.md", "scope": "repo" }
				  ]
				}
				""".formatted(stagedRoot);

		ScriptExecutionResult.Success run = succeeded(
				run(packRoot, "discover_assets.js", manifest, List.of(skillDefinition, docsIndex)));

		assertThat(run.getExitCode()).isZero();
		assertThat(run.getStdout()).contains("=== STRUCTURED SIGNAL SUMMARY ===")
			.contains("name: invoice-reader")
			.contains("## $ARGUMENTS usage count:");
		// The two appendices come from the skill's own references/, resolved by the
		// sandbox's own-folder read rather than handed in as staged input.
		assertThat(run.getStdout()).contains("STYLE GUIDE EXCERPT")
			.contains("JINJA TEMPLATE EXCERPT")
			.doesNotContain("not found");
	}

	/**
	 * The report's file list is the original's list in the original's order. The Python
	 * sorted {@code Path} objects, which compare component by component, so a directory
	 * whose name is a prefix of its sibling's comes first: {@code a/notes.md} before
	 * {@code a-b/notes.md}. Sorting the same paths as strings reverses that, because "/"
	 * sorts below "-" and ".", and nothing else in the report reveals the difference.
	 * @param packRoot where to unpack the shipped skill for this case
	 */
	@Test
	void listsSiblingsInTheOrderTheOriginalSortsThem(@TempDir Path packRoot) throws IOException {
		copySkillFolder(packRoot);
		Path stagedRoot = packRoot.resolve("prefix-siblings");
		Files.createDirectories(stagedRoot.resolve("a"));
		Files.createDirectories(stagedRoot.resolve("a-b"));
		Path definition = stagedRoot.resolve("SKILL.md");
		Files.writeString(definition,
				"---\nname: ordering-probe\ndescription: Checks the list order.\n---\n\n# Probe\n",
				StandardCharsets.UTF_8);
		Path shallow = stagedRoot.resolve("a").resolve("notes.md");
		Path sibling = stagedRoot.resolve("a-b").resolve("notes.md");
		Files.writeString(shallow, "alpha\n", StandardCharsets.UTF_8);
		Files.writeString(sibling, "beta\n", StandardCharsets.UTF_8);
		String manifest = """
				{
				  "target": "%1$s",
				  "repo_root": "%1$s",
				  "files": [
				    { "path": "SKILL.md", "scope": "skill" },
				    { "path": "a/notes.md", "scope": "skill" },
				    { "path": "a-b/notes.md", "scope": "skill" }
				  ]
				}
				""".formatted(stagedRoot);

		ScriptExecutionResult.Success run = succeeded(
				run(packRoot, "discover_assets.js", manifest, List.of(definition, shallow, sibling)));

		assertThat(run.getExitCode()).isZero();
		assertThat(run.getStdout()).contains("  - a/notes.md").contains("  - a-b/notes.md");
		assertThat(run.getStdout().indexOf("  - a/notes.md"))
			.as("ordered by path component, the way sorted(Path) orders them, not by character")
			.isLessThan(run.getStdout().indexOf("  - a-b/notes.md"));
	}

	@Test
	void refusesAMissingManifest(@TempDir Path packRoot) {
		copySkillFolder(packRoot);

		ScriptExecutionResult.Success run = succeeded(run(packRoot, "discover_assets.js", ""));

		assertThat(run.getExitCode()).as("no manifest is a usage error, not a crash").isEqualTo(2);
		assertThat(run.getStderr()).contains("Usage:");
	}

	@Test
	void theScriptsStayInsideTheSandbox(@TempDir Path packRoot) {
		copySkillFolder(packRoot);

		ScriptExecutionResult discovery = run(packRoot, "discover_assets.js",
				"{\"target\": \"/skills/invoice-reader\", \"files\": []}");
		ScriptExecutionResult render = run(packRoot, "render_card.js", contextOf());
		ScriptExecutionResult gate = run(packRoot, "validate_submission.js", CLEAN_CARD);

		// A script that reached for require, fs or a host class would come back as a
		// Failure, and a script that outgrew the statement or wall-clock budget as a
		// timed-out one. Three completes is both budgets met and no host reach.
		assertThat(discovery).isInstanceOf(ScriptExecutionResult.Success.class);
		assertThat(render).isInstanceOf(ScriptExecutionResult.Success.class);
		assertThat(gate).isInstanceOf(ScriptExecutionResult.Success.class);
		assertThat(timedOut(discovery) || timedOut(render) || timedOut(gate)).isFalse();
	}

	private ScriptExecutionResult run(Path packRoot, String fileName, String stdin) {
		return run(packRoot, fileName, stdin, List.of());
	}

	private ScriptExecutionResult run(Path packRoot, String fileName, String stdin, List<Path> inputFiles) {
		return this.engine.execute(SkillScriptFixtures.script(packRoot, SKILL_NAME, fileName), List.of(), stdin,
				inputFiles);
	}

	/**
	 * The run's outcome, once it is established that the engine completed the script
	 * instead of refusing it: a script that reaches for a denied host construct, and one
	 * killed by a limit, both arrive as a {@code Failure}, and an exit code — including a
	 * deliberate non-zero one — arrives on a {@code Success}.
	 * @param result the result to narrow
	 * @return the completed run
	 */
	private static ScriptExecutionResult.Success succeeded(ScriptExecutionResult result) {
		assertThat(result).as("the engine ran the script rather than denying it")
			.isInstanceOf(ScriptExecutionResult.Success.class);
		return (ScriptExecutionResult.Success) result;
	}

	/**
	 * Whether a run reports that a limit killed it. The flag is carried by
	 * {@code Failure} alone — a {@code Success} cannot have been cancelled — so the
	 * {@link #succeeded} assertion is the bulk of this and the check below is the half
	 * that names the budget rather than the outcome.
	 * @param result the result to ask
	 * @return true when the run was stopped by the statement or wall-clock limit
	 */
	private static boolean timedOut(ScriptExecutionResult result) {
		return (result instanceof ScriptExecutionResult.Failure failure) && failure.getTimedOut();
	}

	/**
	 * The context fields the renderer's schema requires, one top-level JSON member per
	 * line so a field can be left out without duplicating the object.
	 * @return the members, in schema order
	 */
	private static List<String> contextMembers() {
		return List.of("\"skill_name\": \"invoice-reader\"", "\"skill_kind\": \"document-processing\"",
				"\"description_sentence\": \"Reads an invoice and reports its line items.\"",
				"\"usage_posture\": \"commercial\"", "\"owner\": { \"kind\": \"nvidia\" }",
				"\"license_identifier\": \"Apache 2.0\"", "\"use_case\": \"Accounts-payable automation\"",
				"\"deployment_geography\": \"Global\"",
				"\"references\": [ { \"label\": \"Source\", \"url\": \"https://github.com/acme/invoices\" } ]",
				"\"output\": { \"types\": [ \"Markdown\" ], \"format\": \"Markdown\", \"parameters\": \"none\","
						+ " \"other_properties\": \"None identified\" }",
				"\"skill_version\": \"1.0.0\"");
	}

	/**
	 * A context JSON on stdin, optionally missing named top-level fields so a validation
	 * failure can be provoked without a second copy of the whole context.
	 * @param omitted top-level keys to leave out
	 * @return the context to pipe in
	 */
	private static String contextOf(String... omitted) {
		List<String> members = new ArrayList<>(contextMembers());
		members.removeIf((member) -> Arrays.stream(omitted).anyMatch((key) -> member.startsWith("\"" + key + "\"")));
		return "{\n" + String.join(",\n  ", members) + "\n}\n";
	}

	private static String read(Path file) {
		try {
			return Files.readString(file, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not read " + file + ": " + ex.getMessage(), ex);
		}
	}

	/**
	 * Copy the skill out of this module's classpath resources into a pack root, scripts,
	 * {@code SKILL.md} and {@code references/} included, the way an installed skill sits
	 * on disk. A partial copy would change what the discovery report prints, because the
	 * script reads its own references through the sandbox.
	 * @param packRoot the directory to create the skill folder inside
	 */
	private static void copySkillFolder(Path packRoot) {
		Path source = skillFolderOnClasspath();
		Path target = packRoot.resolve(SKILL_NAME);
		try (Stream<Path> paths = Files.walk(source)) {
			for (Path file : paths.filter(Files::isRegularFile).toList()) {
				Path destination = target.resolve(source.relativize(file).toString());
				Files.createDirectories(destination.getParent());
				Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException ex) {
			throw new IllegalStateException(
					"Could not copy '" + SKILL_NAME + "' out of " + RESOURCE_ROOT + ": " + ex.getMessage(), ex);
		}
	}

	private static Path skillFolderOnClasspath() {
		URL root = SkillCardGeneratorScriptsTest.class.getClassLoader().getResource(RESOURCE_ROOT);
		if (root == null) {
			throw new IllegalStateException("'" + RESOURCE_ROOT + "' is not on the test classpath");
		}
		if (!"file".equals(root.getProtocol())) {
			throw new IllegalStateException(RESOURCE_ROOT + " is not an unpacked resource: " + root);
		}
		try {
			return Path.of(root.toURI());
		}
		catch (URISyntaxException ex) {
			throw new IllegalStateException("Unusable resource URL " + root + ": " + ex.getMessage(), ex);
		}
	}

}
