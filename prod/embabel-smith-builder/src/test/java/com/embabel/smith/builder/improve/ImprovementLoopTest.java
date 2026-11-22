/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.improve;

import com.embabel.smith.builder.eval.EvalTask;
import com.embabel.smith.builder.eval.InvokerFactory;
import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleStamper;
import com.embabel.smith.builder.lifecycle.LifecycleState;
import com.embabel.smith.builder.publish.PublishResult;
import com.embabel.smith.builder.publish.PublishTarget;
import com.embabel.smith.builder.publish.Publishable;
import com.embabel.smith.builder.publish.FolderPublisher;
import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.io.Yaml;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.trace.TraceStore;
import com.embabel.smith.specsource.FolderSpecSource;
import com.embabel.smith.specsource.SpecSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ImprovementLoopTest {

	@TempDir
	Path tmp;

	private final AgentSpecWriter writer = new AgentSpecWriter();

	private final InvokerFactory passingInvoker = pack -> input -> "pass";

	private final List<EvalTask> tasks = List.of(new EvalTask("t", "in", "pass"));

	private SpecSource source(AgentSpec pack) throws IOException {
		Path root = tmp.resolve("source");
		writer.write(pack, root.resolve(pack.name()));
		return new FolderSpecSource("folder:" + root, root);
	}

	private TraceStore store() {
		return new InMemoryTraceStore();
	}

	/** A store that records nothing — these tests are about the publish, not the row. */
	private CandidateStore unusedStore() {
		return mock(CandidateStore.class);
	}

	/** The policy for a folder improve source — the loop's common case. */
	private LoopPublishPolicy folderPolicy(boolean push) {
		return folderPolicy(push, unusedStore());
	}

	private LoopPublishPolicy folderPolicy(boolean push, CandidateStore packStore) {
		return new LoopPublishPolicy(push, "folder:" + this.tmp.resolve("source"), packStore);
	}

	/** The policy for a git improve source, whose commit needs a push to survive. */
	private LoopPublishPolicy gitPolicy(boolean push) {
		return new LoopPublishPolicy(push, "git:https://example.com/iromu/published-packs.git", unusedStore());
	}

	private ImprovementLoop loop(SpecSource source, TraceStore store, List<Publishable> published,
			boolean autoPublish) {
		return loop(source, store, published, autoPublish, folderPolicy(false));
	}

	private ImprovementLoop loop(SpecSource source, TraceStore store, List<Publishable> published, boolean autoPublish,
			LoopPublishPolicy publishPolicy) {
		return new ImprovementLoop(source, store, (current, feedback) -> current + " [revised]", passingInvoker, tasks,
				(p, overwrite, push) -> {
					published.add(p);
					return new PublishResult("/tmp/" + p.name(), push);
				}, publishPolicy, autoPublish);
	}

	/** A loop whose publisher records the push flag it was called with. */
	private ImprovementLoop pushRecordingLoop(SpecSource source, TraceStore store, boolean[] push,
			LoopPublishPolicy publishPolicy) {
		return new ImprovementLoop(source, store, (current, feedback) -> current + " [revised]", passingInvoker, tasks,
				(p, overwrite, pushFlag) -> {
					push[0] = pushFlag;
					return new PublishResult("/tmp/" + p.name(), pushFlag);
				}, publishPolicy, true); // auto-publish, so the publish happens in the
											// pass
	}

	@Test
	void failingWindowProducesGatedCandidate() throws IOException {
		AgentSpec pack = TestFixtures.pack("extract", "s1", "s2");
		TraceStore store = store();
		store.append(TestFixtures.failedTrace("r1", "extract", "input", TestFixtures.step("s1", "completed", "ok"),
				TestFixtures.step("s2", "failed", "bad")));

		List<Publishable> published = new ArrayList<>();
		ImprovementLoop loop = loop(source(pack), store, published, false); // hold mode

		List<PendingCandidate> produced = loop.runOnce();

		assertThat(produced).hasSize(1);
		assertThat(loop.queue().size()).isEqualTo(1);
		assertThat(published).isEmpty(); // held, not published
		PendingCandidate candidate = produced.get(0);
		assertThat(candidate.spec()).isEqualTo("extract");
		assertThat(candidate.candidate().step("s2").prompt()).contains("[revised]");
		assertThat(candidate.candidate().step("s1").prompt()).isEqualTo(pack.step("s1").prompt());
		assertThat(candidate.proposedLifecycle().state()).isEqualTo(LifecycleState.ACTIVE);
	}

	@Test
	void cleanWindowPublishesNothing() throws IOException {
		AgentSpec pack = TestFixtures.pack("extract", "s1", "s2");
		TraceStore store = store();
		store.append(TestFixtures.okTrace("r1", "extract", "input", TestFixtures.step("s1", "completed", "ok"),
				TestFixtures.step("s2", "completed", "ok")));

		List<Publishable> published = new ArrayList<>();
		ImprovementLoop loop = loop(source(pack), store, published, false);

		List<PendingCandidate> produced = loop.runOnce();

		assertThat(produced).isEmpty();
		assertThat(loop.queue().size()).isZero();
		assertThat(published).isEmpty();
	}

	@Test
	void secondRunOverUnchangedTracesPublishesNothing() throws IOException {
		AgentSpec pack = TestFixtures.pack("extract", "s1", "s2");
		TraceStore store = store();
		store.append(TestFixtures.failedTrace("r1", "extract", "input", TestFixtures.step("s2", "failed", "bad")));

		List<Publishable> published = new ArrayList<>();
		ImprovementLoop loop = loop(source(pack), store, published, true); // auto-publish

		loop.runOnce();
		assertThat(published).hasSize(1); // run 1 published the improvement

		loop.runOnce();
		assertThat(published).hasSize(1); // run 2 is idempotent: nothing new
	}

	@Test
	void autoPublishPublishesImmediately() throws IOException {
		AgentSpec pack = TestFixtures.pack("extract", "s1", "s2");
		TraceStore store = store();
		store.append(TestFixtures.failedTrace("r1", "extract", "input", TestFixtures.step("s2", "failed", "bad")));

		List<Publishable> published = new ArrayList<>();
		ImprovementLoop loop = loop(source(pack), store, published, true); // auto-publish

		List<PendingCandidate> produced = loop.runOnce();

		assertThat(produced).isEmpty(); // auto-published, not held
		assertThat(loop.queue().size()).isZero();
		assertThat(published).hasSize(1);
	}

	@Test
	void publishDoesNotPushByDefault() throws IOException {
		AgentSpec pack = TestFixtures.pack("extract", "s1", "s2");
		TraceStore store = store();
		store.append(TestFixtures.failedTrace("r1", "extract", "input", TestFixtures.step("s2", "failed", "bad")));

		boolean[] push = { false };
		// smith.studio.loop.push unset (the shipped default) -> the publisher is told not
		// to push.
		ImprovementLoop loop = pushRecordingLoop(source(pack), store, push, folderPolicy(false));

		loop.runOnce();

		assertThat(push[0]).isFalse();
	}

	@Test
	void publishPushesOnlyWhenPushingIsEnabled() throws IOException {
		AgentSpec pack = TestFixtures.pack("extract", "s1", "s2");
		TraceStore store = store();
		store.append(TestFixtures.failedTrace("r1", "extract", "input", TestFixtures.step("s2", "failed", "bad")));

		boolean[] push = { false };
		ImprovementLoop loop = pushRecordingLoop(source(pack), store, push, folderPolicy(true));

		loop.runOnce();

		assertThat(push[0]).isTrue();
	}

	@Test
	void aGitSourceWithPushingEnabledIsPublishedWithPush() throws IOException {
		AgentSpec pack = TestFixtures.pack("extract", "s1", "s2");
		TraceStore store = store();
		store.append(TestFixtures.failedTrace("r1", "extract", "input", TestFixtures.step("s2", "failed", "bad")));

		boolean[] push = { false };
		List<Publishable> published = new ArrayList<>();
		ImprovementLoop loop = new ImprovementLoop(source(pack), store, (current, feedback) -> current + " [revised]",
				passingInvoker, tasks, (p, overwrite, pushFlag) -> {
					push[0] = pushFlag;
					published.add(p);
					return new PublishResult("/tmp/" + p.name(), pushFlag);
				}, gitPolicy(true), true); // auto-publish

		loop.runOnce();

		assertThat(published).hasSize(1);
		assertThat(push[0]).isTrue(); // an operator who opted in gets the push
	}

	@Test
	void aGitSourceWithPushingOffIsNotPublishedAndTheCandidateStaysPending() throws IOException {
		AgentSpec pack = TestFixtures.pack("extract", "s1", "s2");
		TraceStore store = store();
		store.append(TestFixtures.failedTrace("r1", "extract", "input", TestFixtures.step("s2", "failed", "bad")));

		List<Publishable> published = new ArrayList<>();
		ImprovementLoop loop = loop(source(pack), store, published, true, gitPolicy(false));

		List<PendingCandidate> produced = loop.runOnce();

		assertThat(published).isEmpty(); // the doomed publish is never attempted
		assertThat(produced).extracting(PendingCandidate::spec).containsExactly("extract");
		assertThat(loop.queue().pending()).extracting(PendingCandidate::spec).containsExactly("extract");
	}

	@Test
	void aFolderSourcePublishesWithPushingOff() throws IOException {
		AgentSpec pack = TestFixtures.pack("extract", "s1", "s2");
		TraceStore store = store();
		store.append(TestFixtures.failedTrace("r1", "extract", "input", TestFixtures.step("s2", "failed", "bad")));

		List<Publishable> published = new ArrayList<>();
		ImprovementLoop loop = loop(source(pack), store, published, true, folderPolicy(false));

		List<PendingCandidate> produced = loop.runOnce();

		assertThat(published).hasSize(1); // a local write is already durable
		assertThat(produced).isEmpty();
		assertThat(loop.queue().size()).isZero();
	}

	@Test
	void inFlightSpecIsSkipped() throws IOException {
		AgentSpec pack = TestFixtures.pack("extract", "s1", "s2");
		Path root = tmp.resolve("source");
		writer.write(pack, root.resolve("extract"));
		new LifecycleStamper().write(root.resolve("extract"), TestFixtures.sidecar(pack, 0.5, LifecycleState.ACTIVE));
		SpecSource source = new FolderSpecSource("folder:" + root, root);

		TraceStore store = store();
		store.append(TestFixtures.failedTrace("r1", "extract", "input", TestFixtures.step("s2", "failed", "bad")));

		List<Publishable> published = new ArrayList<>();
		List<AgentSpec> invoked = new ArrayList<>();
		ImprovementLoop loop = new ImprovementLoop(source, store, (current, feedback) -> current + " [revised]", p -> {
			invoked.add(p);
			return input -> "pass";
		}, tasks, (p, overwrite, push) -> {
			published.add(p);
			return new PublishResult("/tmp/" + p.name(), false);
		}, folderPolicy(false), true);
		loop.inFlight().begin("extract"); // simulate a concurrent iteration already
											// improving it

		loop.runOnce();

		assertThat(published).isEmpty(); // skipped: the spec was already in flight
		assertThat(invoked).isEmpty(); // skipped before detect: no eval budget was spent
	}

	@Test
	void rejectedCandidateIsRecordedWithItsScore() throws IOException {
		AgentSpec pack = TestFixtures.pack("extract", "s1", "s2");
		TraceStore store = store();
		store.append(TestFixtures.failedTrace("r1", "extract", "input", TestFixtures.step("s1", "completed", "ok"),
				TestFixtures.step("s2", "failed", "bad")));

		List<Publishable> published = new ArrayList<>();
		// The failing invoker scores 0.0 and the fresh incubating sidecar's baseline is
		// 0.0, so
		// 0.0 does not strictly beat 0.0 and the candidate is rejected at the baseline
		// stage.
		ImprovementLoop loop = new ImprovementLoop(source(pack), store, (current, feedback) -> current + " [revised]",
				p -> input -> "fail", tasks, (p, overwrite, push) -> {
					published.add(p);
					return new PublishResult("/tmp/" + p.name(), false);
				}, folderPolicy(false), true);

		loop.runOnce();

		assertThat(published).isEmpty();
		GateResult rejection = loop.lastRejection("extract");
		assertThat(rejection).isNotNull();
		assertThat(rejection.stage()).isEqualTo("baseline");
		assertThat(rejection.score()).isEqualTo(0.0);
	}

	@Test
	void contentDriftTriggersLiveReeval() throws IOException {
		AgentSpec pack = TestFixtures.pack("extract", "s1", "s2");
		Path root = tmp.resolve("source");
		writer.write(pack, root.resolve("extract"));
		// A drifted sidecar: its recorded hash does not match the current pack's content.
		new LifecycleStamper().write(root.resolve("extract"),
				new LifecycleMetadata("some-other-hash", 0.0, LifecycleState.INCUBATING, "t"));
		SpecSource source = new FolderSpecSource("folder:" + root, root);

		TraceStore store = store();
		store.append(TestFixtures.okTrace("r1", "extract", "input", TestFixtures.step("s2", "completed", "ok")));

		List<Publishable> published = new ArrayList<>();
		List<AgentSpec> invoked = new ArrayList<>();
		ImprovementLoop loop = new ImprovementLoop(source, store, (current, feedback) -> current + " [revised]", p -> {
			invoked.add(p);
			return input -> "pass";
		}, tasks, (p, overwrite, push) -> {
			published.add(p);
			return new PublishResult("/tmp/" + p.name(), false);
		}, folderPolicy(false), true);

		loop.runOnce();

		assertThat(invoked).isNotEmpty(); // drift forces a live re-eval even for a
											// non-regressable pack
	}

	@Test
	void activePackBelowBaselineDegradesInPass() throws IOException {
		AgentSpec pack = TestFixtures.pack("extract", "s1", "s2");
		Path root = tmp.resolve("source");
		writer.write(pack, root.resolve("extract"));
		new LifecycleStamper().write(root.resolve("extract"), TestFixtures.sidecar(pack, 0.9, LifecycleState.ACTIVE));
		SpecSource source = new FolderSpecSource("folder:" + root, root);

		TraceStore store = store();
		store.append(TestFixtures.okTrace("r1", "extract", "input", TestFixtures.step("s2", "completed", "ok")));

		List<Publishable> published = new ArrayList<>();
		ImprovementLoop loop = new ImprovementLoop(source, store, (current, feedback) -> current + " [revised]",
				p -> input -> "fail", // scores 0.0, below the 0.9 baseline
				tasks, (p, overwrite, push) -> {
					published.add(p);
					return new PublishResult("/tmp/" + p.name(), false);
				}, folderPolicy(false), true);

		loop.runOnce();

		LifecycleMetadata lifecycle = Yaml.mapper()
			.readValue(Files.readString(root.resolve("extract").resolve("lifecycle.yml")), LifecycleMetadata.class);
		assertThat(lifecycle.state()).isEqualTo(LifecycleState.DEGRADED);
		assertThat(published).isEmpty();
	}

	@Test
	void retiredPackIsNotRefined() throws IOException {
		AgentSpec pack = TestFixtures.pack("extract", "s1", "s2");
		Path root = tmp.resolve("source");
		writer.write(pack, root.resolve("extract"));
		// The pack is retired: its sidecar records the RETIRED state at a sub-baseline
		// pass-rate,
		// so the (passing) re-eval would beat the baseline if the loop were to refine it.
		new LifecycleStamper().write(root.resolve("extract"), TestFixtures.sidecar(pack, 0.5, LifecycleState.RETIRED));
		SpecSource source = new FolderSpecSource("folder:" + root, root);

		TraceStore store = store();
		store.append(TestFixtures.failedTrace("r1", "extract", "input", TestFixtures.step("s2", "failed", "bad")));

		List<Publishable> published = new ArrayList<>();
		ImprovementLoop loop = loop(source, store, published, true); // auto-publish

		List<PendingCandidate> produced = loop.runOnce();

		assertThat(produced).isEmpty();
		assertThat(loop.queue().size()).isZero();
		assertThat(published).isEmpty(); // retired packs are excluded from automatic
											// refinement
	}

	@Test
	void degradedPackThatRetiresInPassIsNotRefined() throws IOException {
		AgentSpec pack = TestFixtures.pack("extract", "s1", "s2");
		Path root = tmp.resolve("source");
		writer.write(pack, root.resolve("extract"));
		// A degraded pack with a high baseline; the failing invoker scores below it.
		new LifecycleStamper().write(root.resolve("extract"), TestFixtures.sidecar(pack, 0.9, LifecycleState.DEGRADED));
		SpecSource source = new FolderSpecSource("folder:" + root, root);

		TraceStore store = store();
		store.append(TestFixtures.failedTrace("r1", "extract", "input", TestFixtures.step("s1", "completed", "ok"),
				TestFixtures.step("s2", "failed", "bad")));

		boolean[] refined = { false };
		List<Publishable> published = new ArrayList<>();
		// The failing invoker scores 0.0 on the "pass" task (below the 0.9 baseline), so
		// the
		// degraded pack retires; the recording rewriter proves the refiner is never
		// reached.
		ImprovementLoop loop = new ImprovementLoop(source, store, (current, feedback) -> {
			refined[0] = true;
			return current + " [revised]";
		}, p -> input -> "fail", tasks, (p, overwrite, push) -> {
			published.add(p);
			return new PublishResult("/tmp/" + p.name(), false);
		}, folderPolicy(false), true);

		loop.runOnce();

		assertThat(refined[0]).isFalse(); // the just-retired pack was not refined
		assertThat(published).isEmpty();
		LifecycleMetadata lifecycle = Yaml.mapper()
			.readValue(Files.readString(root.resolve("extract").resolve("lifecycle.yml")), LifecycleMetadata.class);
		assertThat(lifecycle.state()).isEqualTo(LifecycleState.RETIRED);
	}

	/**
	 * A loop publish leaves a versioned row (change: publish-keeps-versions, tasks 2.4
	 * and 3.3). The destination is a real folder publisher so the row and the written
	 * pack can be compared, and the store is a mock so the write itself is observed.
	 */
	@Test
	void aLoopPublishLeavesARowAtTheCandidateVersion() throws IOException {
		AgentSpec pack = TestFixtures.pack("extract", "s1", "s2"); // version 1.0.0
		Path destination = this.tmp.resolve("published");
		TraceStore store = store();
		store.append(TestFixtures.failedTrace("r1", "extract", "input", TestFixtures.step("s2", "failed", "bad")));

		CandidateStore packStore = mock(CandidateStore.class);
		ImprovementLoop loop = new ImprovementLoop(source(pack), store, (current, feedback) -> current + " [revised]",
				passingInvoker, tasks, new FolderPublisher(PublishTarget.parse("folder:" + destination)),
				folderPolicy(false, packStore), true); // auto-publish, so the publish
														// happens in the pass

		loop.runOnce();

		ArgumentCaptor<AgentSpec> saved = ArgumentCaptor.forClass(AgentSpec.class);
		verify(packStore).save(saved.capture());
		// Mutation "increment removed": the candidate would be the baseline's 1.0.0 and
		// this would fail. Mutation "save-out removed": the verify above never happens.
		assertThat(saved.getValue().name()).isEqualTo("extract");
		assertThat(saved.getValue().version()).isEqualTo("1.0.1");

		// The folder and the row are the same pack at the same version — the folder is
		// named by the pack alone, so the version has to agree rather than be inferred.
		AgentSpec written = new AgentSpecReader().read(destination.resolve("extract"));
		assertThat(written.version()).isEqualTo(saved.getValue().version());
		assertThat(written.step("s2").prompt()).isEqualTo(saved.getValue().step("s2").prompt());
	}

}
