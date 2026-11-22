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

/**
 * Offline tests for the blueprint corpus: the documents under a directory travel as one
 * description, each labelled with its relative path in sorted order, and a tree that
 * would cross the corpus cap stops at the file that would cross it instead of shipping an
 * oversized prompt. The cap belongs to this class — the corpus a discovery presents has
 * its own, larger boundary in {@link BusinessProcessDiscovery} — and nothing asserted on
 * it before, so a changed constant or a dropped marker would have gone unnoticed.
 */
class BlueprintLoaderTest {

	/**
	 * The cap as the class states it. Deliberately a literal here rather than a loosened
	 * accessor: a bump has to fail a test.
	 */
	private static final int MAX_CHARS = 200_000;

	@TempDir
	Path docs;

	@Test
	void everyDocumentTravelsLabelledByItsRelativePath() throws IOException {
		write("b.md", "SECOND DOCUMENT");
		write("a.txt", "FIRST DOCUMENT");
		write("nested/c.markdown", "NESTED DOCUMENT");

		String corpus = new BlueprintLoader().resolve(null, this.docs.toString());

		assertThat(corpus).contains("--- a.txt ---").contains("--- b.md ---").contains("--- nested/c.markdown ---");
		assertThat(corpus.indexOf("FIRST DOCUMENT")).isLessThan(corpus.indexOf("SECOND DOCUMENT"));
	}

	@Test
	void aCorpusThatWouldCrossTheCapStopsAndNamesWhatItLeftOut() throws IOException {
		// Sized so the first document fits on its own and the second does not fit with
		// it: a corpus that never crosses the cap would pass this test for the wrong
		// reason.
		write("a.md", "a".repeat(MAX_CHARS * 3 / 4));
		write("b.md", "THE DOCUMENT THAT DOES NOT FIT" + "b".repeat(MAX_CHARS / 2));

		String corpus = new BlueprintLoader().resolve(null, this.docs.toString());

		assertThat(corpus)
			.contains("[corpus truncated at " + MAX_CHARS + " characters; b.md and later files were not included]");
		assertThat(corpus).doesNotContain("THE DOCUMENT THAT DOES NOT FIT");
		assertThat(corpus).hasSizeLessThan(MAX_CHARS);
	}

	private void write(String relative, String body) throws IOException {
		Path file = this.docs.resolve(relative);
		Files.createDirectories(file.getParent());
		Files.writeString(file, body);
	}

}
