/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests of the sidecar reader that decides whether a pack is retired. The shape
 * that matters is the fallback: a pack with nothing recorded keeps running, because packs
 * published before stamping existed — and the frozen POC sources — have no sidecar at
 * all.
 */
class LifecycleSidecarTest {

	@TempDir
	Path tmp;

	@Test
	void theDeclaredStateIsReadFromTheSidecar() throws IOException {
		write("state: \"RETIRED\"\n", "retired-pack");

		assertThat(LifecycleSidecar.declaredState(this.tmp.resolve("retired-pack"))).contains("RETIRED");
		assertThat(LifecycleSidecar.retired(this.tmp.resolve("retired-pack"))).isTrue();
	}

	@Test
	void retirementIsMatchedRegardlessOfHowTheWordIsCased() throws IOException {
		write("state: \"retired\"\n", "lowercase-pack");

		assertThat(LifecycleSidecar.retired(this.tmp.resolve("lowercase-pack"))).isTrue();
	}

	@Test
	void anyOtherStateIsRunnable() throws IOException {
		write("state: \"ACTIVE\"\n", "active-pack");
		write("state: \"DEGRADED\"\n", "degraded-pack");

		assertThat(LifecycleSidecar.retired(this.tmp.resolve("active-pack"))).isFalse();
		assertThat(LifecycleSidecar.declaredState(this.tmp.resolve("degraded-pack"))).contains("DEGRADED");
		assertThat(LifecycleSidecar.retired(this.tmp.resolve("degraded-pack"))).isFalse();
	}

	@Test
	void aPackWithoutASidecarHasNoStateAndStaysRunnable() throws IOException {
		Files.createDirectories(this.tmp.resolve("no-sidecar"));

		assertThat(LifecycleSidecar.declaredState(this.tmp.resolve("no-sidecar"))).isEmpty();
		assertThat(LifecycleSidecar.retired(this.tmp.resolve("no-sidecar"))).isFalse();
	}

	@Test
	void aSidecarThatIsNotReadableIsNoStateAndStaysRunnable() throws IOException {
		// Not a mapping at all, so parsing it fails: an unreadable record must not be
		// read
		// as a retirement.
		write("just a word, not a lifecycle document\n", "corrupt-pack");

		assertThat(LifecycleSidecar.declaredState(this.tmp.resolve("corrupt-pack"))).isEmpty();
		assertThat(LifecycleSidecar.retired(this.tmp.resolve("corrupt-pack"))).isFalse();
	}

	@Test
	void aSidecarRecordingNoStateIsNoState() throws IOException {
		write("contentHash: \"0be2d86f\"\nbaselinePassRate: 1.0\n", "stateless-pack");

		assertThat(LifecycleSidecar.declaredState(this.tmp.resolve("stateless-pack"))).isEmpty();
		assertThat(LifecycleSidecar.retired(this.tmp.resolve("stateless-pack"))).isFalse();
	}

	private void write(String body, String packDir) throws IOException {
		Path dir = this.tmp.resolve(packDir);
		Files.createDirectories(dir);
		Files.writeString(dir.resolve(LifecycleSidecar.FILE_NAME), body);
	}

}
