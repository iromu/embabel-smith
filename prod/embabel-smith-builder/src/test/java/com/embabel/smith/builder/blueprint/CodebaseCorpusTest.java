/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.blueprint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Offline tests for the codebase corpus: documents are read before source, build output
 * and dependencies are left out, a link that leaves the repository is not read, an
 * oversized file is truncated rather than dropped, and the documents cannot take more
 * than their share of the cap.
 */
class CodebaseCorpusTest {

	@TempDir
	Path codebase;

	@TempDir
	Path outside;

	@Test
	void documentsAreReadBeforeSource() throws IOException {
		write("src/main.rs", "SOURCE BODY");
		write("README.md", "DOCUMENT BODY");

		CodebaseCorpus.Corpus corpus = new CodebaseCorpus().load(this.codebase);

		assertThat(corpus.text()).contains("--- README.md ---").contains("--- src/main.rs ---");
		assertThat(corpus.text().indexOf("DOCUMENT BODY")).isLessThan(corpus.text().indexOf("SOURCE BODY"));
	}

	@Test
	void buildOutputAndDependenciesAreLeftOut() throws IOException {
		write("src/main.rs", "REAL CODE");
		write("target/generated.rs", "BUILD OUTPUT");
		write("node_modules/dep/index.js", "A DEPENDENCY");
		write("assets/logo.png", "NOT TEXT");

		CodebaseCorpus.Corpus corpus = new CodebaseCorpus().load(this.codebase);

		assertThat(corpus.text()).contains("REAL CODE").doesNotContain("BUILD OUTPUT").doesNotContain("A DEPENDENCY");
		assertThat(corpus.filesIncluded()).isEqualTo(1);
	}

	@Test
	void aLinkThatLeavesTheCodebaseIsNotRead() throws IOException {
		// The corpus goes out over the network to a model endpoint, so a clone gets to
		// decide its own contents but not what else on the machine travels with them.
		write("src/main.rs", "REAL CODE");
		Files.writeString(this.outside.resolve("id_rsa"), "PRIVATE KEY MATERIAL");
		Files.createSymbolicLink(this.codebase.resolve("notes.md"), this.outside.resolve("id_rsa"));

		CodebaseCorpus.Corpus corpus = new CodebaseCorpus().load(this.codebase);

		assertThat(corpus.text()).contains("REAL CODE").doesNotContain("PRIVATE KEY MATERIAL");
		assertThat(corpus.filesIncluded()).isEqualTo(1);
	}

	@Test
	void anOversizedFileIsTruncatedRatherThanDropped() throws IOException {
		write("big.rs", "a".repeat(CodebaseCorpus.MAX_FILE_CHARS + 5_000));

		CodebaseCorpus.Corpus corpus = new CodebaseCorpus().load(this.codebase);

		assertThat(corpus.text()).contains("[... file truncated ...]");
		assertThat(corpus.filesIncluded()).isEqualTo(1);
	}

	@Test
	void filesBeyondTheTotalCapAreCountedNotSwallowed() throws IOException {
		for (int file = 0; file < 14; file++) {
			write("src/module" + file + ".rs", "b".repeat(CodebaseCorpus.MAX_FILE_CHARS));
		}

		CodebaseCorpus.Corpus corpus = new CodebaseCorpus().load(this.codebase);

		assertThat(corpus.filesOmitted()).isPositive();
		assertThat(corpus.filesIncluded()).isPositive();
		assertThat(corpus.filesIncluded() + corpus.filesOmitted()).isEqualTo(14);
		assertThat(corpus.text()).hasSizeLessThan(CodebaseCorpus.MAX_CHARS + 1_000);
	}

	@Test
	void documentsCannotCrowdTheSourceOutOfTheCorpus() throws IOException {
		// Sized so the documents fill their share almost exactly: that is the case where
		// a
		// source budget computed wrongly leaves the source out entirely, which is what a
		// real repository with a large docs/ tree does.
		for (int doc = 0; doc < 12; doc++) {
			write("docs/guide" + doc + ".md", "d".repeat(19_900));
		}
		write("src/main.rs", "s".repeat(CodebaseCorpus.MAX_FILE_CHARS));

		CodebaseCorpus.Corpus corpus = new CodebaseCorpus().load(this.codebase);

		assertThat(corpus.text()).contains("src/main.rs");
		assertThat(corpus.text()).hasSizeGreaterThan(CodebaseCorpus.DOC_SHARE);
		assertThat(corpus.filesOmitted()).isPositive();
	}

	@Test
	void aDirectoryWithNothingRecognisableFails() throws IOException {
		write("assets/logo.png", "NOT TEXT");

		assertThatThrownBy(() -> new CodebaseCorpus().load(this.codebase)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("--subdir");
	}

	private void write(String relative, String body) throws IOException {
		Path file = this.codebase.resolve(relative);
		Files.createDirectories(file.getParent());
		Files.writeString(file, body);
	}

}
