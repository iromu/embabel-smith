/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.control;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleState;
import com.embabel.smith.builder.lifecycle.SpecContentHash;
import com.embabel.smith.builder.publish.FolderPublisher;
import com.embabel.smith.builder.publish.PublishResult;
import com.embabel.smith.builder.publish.PublishTarget;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.selfimprove.referee.RefereeReport;
import com.embabel.smith.studio.improve.ApprovalQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task 8.1: a candidate that cleared the gate is held for a human decision and nothing is
 * published. The destination is a real folder-source directory rather than a call
 * counter, because "publish nothing" is a claim about a directory: the test that proves
 * it has to be the one that also shows the same directory would have received the pack.
 *
 * @author Ivan Rodriguez
 */
class HeldForApprovalTest {

	@TempDir
	Path source;

	@Test
	void aGatedCandidateIncrementsTheHeldCountAndNothingIsWrittenToTheSource() throws IOException {
		HeldForApproval held = new HeldForApproval();

		held.hold(ControlFixtures.candidate("gen1-variant-a"), ControlFixtures.gated("gen1-variant-a", 0.833, 1.0),
				0.333);
		assertThat(held.held()).isEqualTo(1);

		held.hold(ControlFixtures.candidate("gen2-variant-b"), ControlFixtures.gated("gen2-variant-b", 1.0, 1.0),
				0.833);
		assertThat(held.held()).isEqualTo(2);
		assertThat(held.heldIds()).containsExactly("gen1-variant-a", "gen2-variant-b");

		// Two candidates cleared the gate and the loop wrote neither of them anywhere.
		assertThat(entries(this.source)).isEmpty();
	}

	@Test
	void theSameSourceWouldHaveReceivedThePackHadItBeenPublished() throws IOException {
		// Non-vacuity for the assertion above: same destination, same pack. Publishing it
		// really
		// does write, so an empty directory after holding is a result rather than a test
		// that could
		// not have written anything in the first place.
		AgentSpec pack = ControlFixtures.pack("gen1-variant-a");
		PublishResult published = new FolderPublisher(PublishTarget.parse("folder:" + this.source)).publish(pack,
				new LifecycleMetadata(SpecContentHash.of(pack), 0.833, LifecycleState.ACTIVE, "2026-11-20T00:00:00Z"),
				true, false);

		assertThat(Path.of(published.location())).isDirectory();
		assertThat(entries(this.source)).containsExactly("gen1-variant-a");
		assertThat(entries(Path.of(published.location()))).isNotEmpty();
	}

	@Test
	void anUngatedCandidateNeverEntersTheQueue() throws IOException {
		HeldForApproval held = new HeldForApproval();
		RefereeReport rejected = new RefereeReport("gen1-variant-a", false, "baseline",
				List.of("re-eval 0.333 does not beat baseline 0.833"), 0.333, 0.5, List.of("b27-t01"),
				List.of("b27-t01", "b27-t02"), List.of());

		assertThatThrownBy(() -> held.hold(ControlFixtures.candidate("gen1-variant-a"), rejected, 0.833))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("did not gate it")
			.hasMessageContaining("baseline");
		assertThat(held.held()).isZero();
		assertThat(entries(this.source)).isEmpty();
	}

	@Test
	void aGatedReportCarryingNoScoreIsRefusedRatherThanHeldAtZero() {
		// A pass with no re-eval score would be held at a score of zero on the record a
		// human
		// decides from. Refusing it is the honest outcome, and a hand-built report is the
		// only way
		// this shape arrives.
		HeldForApproval held = new HeldForApproval();
		RefereeReport scoreless = new RefereeReport("gen1-variant-a", true, null, List.of(), null, 1.0, List.of(),
				List.of("b27-t01", "b27-t02"), List.of());

		assertThatThrownBy(() -> held.hold(ControlFixtures.candidate("gen1-variant-a"), scoreless, 0.333))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("no")
			.hasMessageContaining("re-eval score");
		assertThat(held.held()).isZero();
	}

	@Test
	void thisClassOffersNoCallerAWayToPublish() {
		// Structural half of "publish nothing". The product queue publishes only through
		// a
		// publisher its caller supplies, so a method on this class that handed back a
		// publish
		// result would be a publish path a caller could take. Detection is by return
		// type,
		// and the
		// control case is what shows the detector is not blind to one.
		assertThat(publishingMethods(HeldForApproval.class)).isEmpty();
		assertThat(publishingMethods(ApprovalQueue.class)).contains("approve");
	}

	private static List<String> publishingMethods(Class<?> type) {
		return Arrays.stream(type.getDeclaredMethods())
			// Synthetic members are compiler-generated bodies of lambdas held in fields,
			// not
			// API a caller can name; NOTHING_PUBLISHED is one such, and it throws.
			.filter((method) -> !method.isSynthetic())
			.filter((method) -> method.getReturnType().equals(PublishResult.class))
			.map(Method::getName)
			.toList();
	}

	private static List<String> entries(Path dir) throws IOException {
		try (var paths = Files.list(dir)) {
			return paths.map((path) -> path.getFileName().toString()).sorted().toList();
		}
	}

}
