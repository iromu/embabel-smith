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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Offline tests for discovery loading. The clone test asserts git's posture before the
 * process starts — non-interactive, the same {@code GitPublisher} uses, so a private
 * repository fails rather than waits for credentials on a terminal nobody is reading —
 * and the folder tests cover {@code load} itself: the result's shape and key ordering,
 * and the three ways an input can be unusable. None of them reach the network.
 */
class BusinessProcessDiscoveryTest {

	@TempDir
	Path docs;

	@Test
	void theCloneStartsGitNonInteractively() {
		ProcessBuilder builder = BusinessProcessDiscovery.cloneProcess("git@example.invalid:private/repo.git",
				Path.of("/tmp/smith-clone-posture"));

		assertThat(builder.environment()).containsEntry("GIT_TERMINAL_PROMPT", "0");
		assertThat(builder.command()).containsSubsequence("git", "-c", "core.askpass=false", "clone", "--depth", "1");
	}

	@Test
	void aFolderResultKeysItsDocumentsByRelativePathInSortedOrder() throws IOException {
		write("b.md", "SECOND DOCUMENT");
		write("a.md", "FIRST DOCUMENT");
		write("nested/c.md", "NESTED DOCUMENT");
		// A generation would take this file (BlueprintLoader reads .txt too); discovery
		// presents documents only, so it must not appear here.
		write("notes.txt", "NOT A DOCUMENT");

		BusinessProcessDiscovery.DiscoverResult result = new BusinessProcessDiscovery().load(this.docs.toString(),
				null);

		assertThat(result.sourceType()).isEqualTo("folder");
		assertThat(result.location()).isEqualTo(this.docs.toString());
		assertThat(result.docs().keySet()).containsExactly("a.md", "b.md", "nested/c.md");
		assertThat(result.docs().get("nested/c.md")).isEqualTo("NESTED DOCUMENT");
		assertThat(result.docs().values()).doesNotContain("NOT A DOCUMENT");
	}

	@Test
	void aPathThatIsNotADirectoryNamesIt() throws IOException {
		Path file = write("a.md", "DOCUMENT");

		assertThatIllegalArgumentException()
			.isThrownBy(() -> new BusinessProcessDiscovery().load(file.toString(), null))
			.withMessageContaining("Not a directory: ");
	}

	@Test
	void neitherInputIsRefused() {
		assertThatIllegalArgumentException().isThrownBy(() -> new BusinessProcessDiscovery().load(null, null))
			.withMessage("Provide a docs path or a repository URL.");
		assertThatIllegalArgumentException().isThrownBy(() -> new BusinessProcessDiscovery().load("  ", "  "))
			.withMessage("Provide a docs path or a repository URL.");
	}

	@Test
	void aFolderWithoutDocumentsNamesTheDirectory() throws IOException {
		write("notes.txt", "NOT A DOCUMENT");

		assertThatIllegalArgumentException()
			.isThrownBy(() -> new BusinessProcessDiscovery().load(this.docs.toString(), null))
			.withMessageContaining("No business-process documents (.md) found under ");
	}

	private Path write(String relative, String body) throws IOException {
		Path file = this.docs.resolve(relative);
		Files.createDirectories(file.getParent());
		Files.writeString(file, body);
		return file;
	}

}
