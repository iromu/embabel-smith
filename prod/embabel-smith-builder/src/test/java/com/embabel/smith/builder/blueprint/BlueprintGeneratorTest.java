/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.blueprint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.embabel.smith.builder.blueprint.BlueprintGenerator.BlueprintResult;
import com.embabel.smith.builder.blueprint.BlueprintGenerator.CodebaseSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Offline tests for the repository-to-blueprint step, with a stub model: the seven
 * deliverables and their index are written, a partial answer says what it missed, an
 * answer that names no deliverable is kept whole rather than discarded, and the documents
 * already in the output directory survive every run that did not reach an answer to
 * replace them with.
 */
class BlueprintGeneratorTest {

	@TempDir
	Path codebase;

	@TempDir
	Path out;

	@Test
	void writesEveryDeliverableAndAnIndex() throws IOException {
		Files.writeString(this.codebase.resolve("app.py"), "def take_order():\n    pass\n");

		BlueprintResult result = generate(fullAnswer());

		assertThat(result.complete()).isTrue();
		assertThat(Files.list(this.out).map(Path::getFileName).map(Path::toString).sorted().toList()).containsExactly(
				"ACTORS_AND_ROLES.md", "BUSINESS_OVERVIEW.md", "BUSINESS_PROCESSES.md", "BUSINESS_RULES.md",
				"EXCEPTIONS_AND_EDGE_CASES.md", "README.md", "STATES_AND_TRANSITIONS.md", "WORKFLOWS.md");
		assertThat(Files.readString(this.out.resolve("README.md"))).contains("smith blueprint")
			.contains(this.codebase.toString());
		assertThat(result.report()).startsWith("BUILD OK: blueprint of ").contains("8 documents in " + this.out);
	}

	@Test
	void aPartialAnswerReportsTheDeliverablesItMissed() throws IOException {
		Files.writeString(this.codebase.resolve("app.py"), "pass");

		BlueprintResult result = generate("## BUSINESS_OVERVIEW.md\n\nOverview.\n\n## WORKFLOWS.md\n\nSteps.\n");

		assertThat(result.complete()).isFalse();
		assertThat(result.missing()).containsExactly("ACTORS_AND_ROLES.md", "BUSINESS_PROCESSES.md",
				"BUSINESS_RULES.md", "STATES_AND_TRANSITIONS.md", "EXCEPTIONS_AND_EDGE_CASES.md");
		assertThat(result.report()).startsWith("BUILD BLOCKED:").contains("  missing   : ACTORS_AND_ROLES.md");
		assertThat(Files.exists(this.out.resolve("WORKFLOWS.md"))).isTrue();
	}

	@Test
	void anAnswerWithNoDeliverableHeadingsKeepsTheRawAnswer() throws IOException {
		Files.writeString(this.codebase.resolve("app.py"), "pass");

		BlueprintResult result = generate("# Some project\n\nIt does things.\n");

		assertThat(result.complete()).isFalse();
		assertThat(result.missing()).containsExactlyElementsOf(BlueprintSplitter.DELIVERABLES);
		assertThat(Files.readString(this.out.resolve("DISCOVERY_ANSWER.md"))).contains("It does things.");
	}

	@Test
	void anExistingBlueprintIsNotReplacedWithoutOverwrite() throws IOException {
		Files.writeString(this.codebase.resolve("app.py"), "pass");
		Files.writeString(this.out.resolve("BUSINESS_OVERVIEW.md"), "hand written");

		assertThatThrownBy(() -> generate(fullAnswer())).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("--overwrite");
		assertThat(Files.readString(this.out.resolve("BUSINESS_OVERVIEW.md"))).isEqualTo("hand written");
	}

	@Test
	void aSubdirectoryNarrowsWhatTheModelIsShown() throws IOException {
		write("service/app.py", "SERVICE CODE");
		write("other/x.py", "OTHER CODE");
		String[] user = new String[1];
		BlueprintGenerator generator = new BlueprintGenerator(new ProcessExtractor((system, message) -> {
			user[0] = message;
			return fullAnswer();
		}, "DISCOVERY-PROMPT"));

		generator.generate(new CodebaseSource(null, this.codebase.toString(), null, "service"), this.out, false);

		assertThat(user[0]).contains("SERVICE CODE").doesNotContain("OTHER CODE");
	}

	@Test
	void aSubdirectoryThatEscapesTheCodebaseIsRefused() {
		assertThatThrownBy(() -> new BlueprintGenerator(new ProcessExtractor((system, user) -> fullAnswer(), "P"))
			.generate(new CodebaseSource(null, this.codebase.toString(), null, "../elsewhere"), this.out, false))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("--subdir");
	}

	@Test
	void aCodebasePathWrittenWithADotDotStillAcceptsItsSubdir() throws IOException {
		write("service/app.py", "SERVICE CODE");

		BlueprintResult result = new BlueprintGenerator(new ProcessExtractor((system, user) -> fullAnswer(), "P"))
			.generate(new CodebaseSource(null, this.codebase.resolve("service/..").toString(), null, "service"),
					this.out, false);

		assertThat(result.complete()).isTrue();
	}

	@Test
	void aRunThatFailsBeforeItsAnswerLeavesTheBlueprintItWasGiven() throws IOException {
		Files.writeString(this.codebase.resolve("app.py"), "pass");
		Files.writeString(this.out.resolve("BUSINESS_OVERVIEW.md"), "hand written");

		assertThatThrownBy(() -> new BlueprintGenerator(new ProcessExtractor((system, user) -> {
			throw new IllegalStateException("endpoint timeout");
		}, "P")).generate(new CodebaseSource(null, this.codebase.toString(), null, null), this.out, true))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("endpoint timeout");
		assertThat(Files.readString(this.out.resolve("BUSINESS_OVERVIEW.md"))).isEqualTo("hand written");
	}

	@Test
	void anArchiveIsReadAsTheRepositoryItWraps() throws IOException {
		// A forge archive names its own ref, and that name is not part of the repository:
		// the paths the model is shown have to be the ones a reader of the project knows.
		Path archive = Files.createTempFile(this.codebase, "codebase", ".zip");
		try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(archive))) {
			out.putNextEntry(new ZipEntry("acme-main/src/app.py"));
			out.write("ARCHIVED CODE".getBytes(StandardCharsets.UTF_8));
			out.closeEntry();
		}
		String[] user = new String[1];

		BlueprintResult result = new BlueprintGenerator(new ProcessExtractor((system, message) -> {
			user[0] = message;
			return fullAnswer();
		}, "P")).generate(new CodebaseSource(null, null, archive.toString(), null), this.out, false);

		assertThat(result.complete()).isTrue();
		assertThat(user[0]).contains("ARCHIVED CODE").contains("--- src/app.py ---");
	}

	@Test
	void aSourceNamingBothLocationsIsRefused() {
		assertThatThrownBy(() -> new CodebaseSource("git@example.invalid:x/y.git", "/tmp/code", null, null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void anOverwriteRunLeavesNoDocumentTheNewAnswerDidNotProduce() throws IOException {
		Files.writeString(this.codebase.resolve("app.py"), "pass");
		Files.writeString(this.out.resolve("WORKFLOWS.md"), "from an earlier, luckier run");

		new BlueprintGenerator(new ProcessExtractor((system, user) -> "## BUSINESS_OVERVIEW.md\n\nOverview.\n", "P"))
			.generate(new CodebaseSource(null, this.codebase.toString(), null, null), this.out, true);

		assertThat(Files.exists(this.out.resolve("WORKFLOWS.md"))).isFalse();
		assertThat(Files.exists(this.out.resolve("BUSINESS_OVERVIEW.md"))).isTrue();
	}

	private void write(String relative, String body) throws IOException {
		Path file = this.codebase.resolve(relative);
		Files.createDirectories(file.getParent());
		Files.writeString(file, body);
	}

	private BlueprintResult generate(String answer) {
		return new BlueprintGenerator(new ProcessExtractor((system, user) -> answer, "DISCOVERY-PROMPT"))
			.generate(new CodebaseSource(null, this.codebase.toString(), null, null), this.out, false);
	}

	private static String fullAnswer() {
		Map<String, String> bodies = Map.of("BUSINESS_OVERVIEW", "Overview.", "ACTORS_AND_ROLES", "Actors.",
				"BUSINESS_PROCESSES", "Processes.", "WORKFLOWS", "Steps.", "BUSINESS_RULES", "Rules.",
				"STATES_AND_TRANSITIONS", "States.", "EXCEPTIONS_AND_EDGE_CASES", "Exceptions.");
		StringBuilder answer = new StringBuilder();
		for (String deliverable : BlueprintSplitter.DELIVERABLES) {
			answer.append("## ").append(deliverable).append(".md\n\n").append(bodies.get(deliverable)).append("\n\n");
		}
		return answer.toString();
	}

}
