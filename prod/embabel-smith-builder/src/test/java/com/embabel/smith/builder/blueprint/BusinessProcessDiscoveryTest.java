/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.blueprint;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline test for the repository clone: git must be started non-interactively, the same
 * posture {@code GitPublisher} uses, so a private repository fails rather than waits for
 * credentials on a terminal nobody is reading. The process is asserted before it starts,
 * so no network is needed.
 */
class BusinessProcessDiscoveryTest {

	@Test
	void theCloneStartsGitNonInteractively() {
		ProcessBuilder builder = BusinessProcessDiscovery.cloneProcess("git@example.invalid:private/repo.git",
				Path.of("/tmp/smith-clone-posture"));

		assertThat(builder.environment()).containsEntry("GIT_TERMINAL_PROMPT", "0");
		assertThat(builder.command()).containsSubsequence("git", "-c", "core.askpass=false", "clone", "--depth", "1");
	}

}
