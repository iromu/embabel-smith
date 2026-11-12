package com.embabel.smith.studio.improve;

import com.embabel.smith.builder.eval.EvalTask;
import com.embabel.smith.builder.eval.InvokerFactory;
import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleStamper;
import com.embabel.smith.builder.lifecycle.LifecycleState;
import com.embabel.smith.builder.publish.PublishResult;
import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.io.Yaml;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.runtime.trace.JsonlTraceStore;
import com.embabel.smith.specsource.FolderSpecSource;
import com.embabel.smith.specsource.SpecSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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

	private JsonlTraceStore store() {
		return new JsonlTraceStore(tmp.resolve("traces.jsonl"));
	}

	private ImprovementLoop loop(SpecSource source, JsonlTraceStore store, List<AgentSpec> published,
			boolean autoPublish) {
		return new ImprovementLoop(source, store, (current, feedback) -> current + " [revised]", passingInvoker, tasks,
				(p, lifecycle, overwrite, push) -> {
					published.add(p);
					return new PublishResult(Path.of("/tmp/" + p.name()), false);
				}, autoPublish);
	}

	@Test
	void failingWindowProducesGatedCandidate() throws IOException {
		AgentSpec pack = TestFixtures.pack("extract", "s1", "s2");
		JsonlTraceStore store = store();
		store.append(TestFixtures.failedTrace("r1", "extract", "input", TestFixtures.step("s1", "completed", "ok"),
				TestFixtures.step("s2", "failed", "bad")));

		List<AgentSpec> published = new ArrayList<>();
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
		JsonlTraceStore store = store();
		store.append(TestFixtures.okTrace("r1", "extract", "input", TestFixtures.step("s1", "completed", "ok"),
				TestFixtures.step("s2", "completed", "ok")));

		List<AgentSpec> published = new ArrayList<>();
		ImprovementLoop loop = loop(source(pack), store, published, false);

		List<PendingCandidate> produced = loop.runOnce();

		assertThat(produced).isEmpty();
		assertThat(loop.queue().size()).isZero();
		assertThat(published).isEmpty();
	}

	@Test
	void secondRunOverUnchangedTracesPublishesNothing() throws IOException {
		AgentSpec pack = TestFixtures.pack("extract", "s1", "s2");
		JsonlTraceStore store = store();
		store.append(TestFixtures.failedTrace("r1", "extract", "input", TestFixtures.step("s2", "failed", "bad")));

		List<AgentSpec> published = new ArrayList<>();
		ImprovementLoop loop = loop(source(pack), store, published, true); // auto-publish

		loop.runOnce();
		assertThat(published).hasSize(1); // run 1 published the improvement

		loop.runOnce();
		assertThat(published).hasSize(1); // run 2 is idempotent: nothing new
	}

	@Test
	void autoPublishPublishesImmediately() throws IOException {
		AgentSpec pack = TestFixtures.pack("extract", "s1", "s2");
		JsonlTraceStore store = store();
		store.append(TestFixtures.failedTrace("r1", "extract", "input", TestFixtures.step("s2", "failed", "bad")));

		List<AgentSpec> published = new ArrayList<>();
		ImprovementLoop loop = loop(source(pack), store, published, true); // auto-publish

		List<PendingCandidate> produced = loop.runOnce();

		assertThat(produced).isEmpty(); // auto-published, not held
		assertThat(loop.queue().size()).isZero();
		assertThat(published).hasSize(1);
	}

	@Test
	void publishPersistsToTheSourceWithPush() throws IOException {
		AgentSpec pack = TestFixtures.pack("extract", "s1", "s2");
		JsonlTraceStore store = store();
		store.append(TestFixtures.failedTrace("r1", "extract", "input", TestFixtures.step("s2", "failed", "bad")));

		boolean[] push = { false };
		ImprovementLoop loop = new ImprovementLoop(source(pack), store, (current, feedback) -> current + " [revised]",
				passingInvoker, tasks, (p, lifecycle, overwrite, pushFlag) -> {
					push[0] = pushFlag;
					return new PublishResult(Path.of("/tmp/" + p.name()), pushFlag);
				}, true); // auto-publish

		loop.runOnce();

		assertThat(push[0]).isTrue(); // the loop pushes improvements into the source
										// (git)
	}

	@Test
	void inFlightSpecIsSkipped() throws IOException {
		AgentSpec pack = TestFixtures.pack("extract", "s1", "s2");
		Path root = tmp.resolve("source");
		writer.write(pack, root.resolve("extract"));
		new LifecycleStamper().write(root.resolve("extract"), TestFixtures.sidecar(pack, 0.5, LifecycleState.ACTIVE));
		SpecSource source = new FolderSpecSource("folder:" + root, root);

		JsonlTraceStore store = store();
		store.append(TestFixtures.failedTrace("r1", "extract", "input", TestFixtures.step("s2", "failed", "bad")));

		List<AgentSpec> published = new ArrayList<>();
		List<AgentSpec> invoked = new ArrayList<>();
		ImprovementLoop loop = new ImprovementLoop(source, store, (current, feedback) -> current + " [revised]", p -> {
			invoked.add(p);
			return input -> "pass";
		}, tasks, (p, lifecycle, overwrite, push) -> {
			published.add(p);
			return new PublishResult(Path.of("/tmp/" + p.name()), false);
		}, true);
		loop.inFlight().begin("extract"); // simulate a concurrent iteration already
											// improving it

		loop.runOnce();

		assertThat(published).isEmpty(); // skipped: the spec was already in flight
		assertThat(invoked).isEmpty(); // skipped before detect: no eval budget was spent
	}

	@Test
	void rejectedCandidateIsRecordedWithItsScore() throws IOException {
		AgentSpec pack = TestFixtures.pack("extract", "s1", "s2");
		JsonlTraceStore store = store();
		store.append(TestFixtures.failedTrace("r1", "extract", "input", TestFixtures.step("s1", "completed", "ok"),
				TestFixtures.step("s2", "failed", "bad")));

		List<AgentSpec> published = new ArrayList<>();
		// The failing invoker scores 0.0 and the fresh incubating sidecar's baseline is
		// 0.0, so
		// 0.0 does not strictly beat 0.0 and the candidate is rejected at the baseline
		// stage.
		ImprovementLoop loop = new ImprovementLoop(source(pack), store, (current, feedback) -> current + " [revised]",
				p -> input -> "fail", tasks, (p, lifecycle, overwrite, push) -> {
					published.add(p);
					return new PublishResult(Path.of("/tmp/" + p.name()), false);
				}, true);

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

		JsonlTraceStore store = store();
		store.append(TestFixtures.okTrace("r1", "extract", "input", TestFixtures.step("s2", "completed", "ok")));

		List<AgentSpec> published = new ArrayList<>();
		List<AgentSpec> invoked = new ArrayList<>();
		ImprovementLoop loop = new ImprovementLoop(source, store, (current, feedback) -> current + " [revised]", p -> {
			invoked.add(p);
			return input -> "pass";
		}, tasks, (p, lifecycle, overwrite, push) -> {
			published.add(p);
			return new PublishResult(Path.of("/tmp/" + p.name()), false);
		}, true);

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

		JsonlTraceStore store = store();
		store.append(TestFixtures.okTrace("r1", "extract", "input", TestFixtures.step("s2", "completed", "ok")));

		List<AgentSpec> published = new ArrayList<>();
		ImprovementLoop loop = new ImprovementLoop(source, store, (current, feedback) -> current + " [revised]",
				p -> input -> "fail", // scores 0.0, below the 0.9 baseline
				tasks, (p, lifecycle, overwrite, push) -> {
					published.add(p);
					return new PublishResult(Path.of("/tmp/" + p.name()), false);
				}, true);

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

		JsonlTraceStore store = store();
		store.append(TestFixtures.failedTrace("r1", "extract", "input", TestFixtures.step("s2", "failed", "bad")));

		List<AgentSpec> published = new ArrayList<>();
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

		JsonlTraceStore store = store();
		store.append(TestFixtures.failedTrace("r1", "extract", "input", TestFixtures.step("s1", "completed", "ok"),
				TestFixtures.step("s2", "failed", "bad")));

		boolean[] refined = { false };
		List<AgentSpec> published = new ArrayList<>();
		// The failing invoker scores 0.0 on the "pass" task (below the 0.9 baseline), so
		// the
		// degraded pack retires; the recording rewriter proves the refiner is never
		// reached.
		ImprovementLoop loop = new ImprovementLoop(source, store, (current, feedback) -> {
			refined[0] = true;
			return current + " [revised]";
		}, p -> input -> "fail", tasks, (p, lifecycle, overwrite, push) -> {
			published.add(p);
			return new PublishResult(Path.of("/tmp/" + p.name()), false);
		}, true);

		loop.runOnce();

		assertThat(refined[0]).isFalse(); // the just-retired pack was not refined
		assertThat(published).isEmpty();
		LifecycleMetadata lifecycle = Yaml.mapper()
			.readValue(Files.readString(root.resolve("extract").resolve("lifecycle.yml")), LifecycleMetadata.class);
		assertThat(lifecycle.state()).isEqualTo(LifecycleState.RETIRED);
	}

}
