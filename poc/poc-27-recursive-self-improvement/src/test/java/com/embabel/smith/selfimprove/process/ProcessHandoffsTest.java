/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.process;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToDoubleFunction;

import com.embabel.smith.builder.core.AgentsPackDraft;
import com.embabel.smith.builder.core.SingleShotAgentsPackGenerator;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentsPack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 5.2: a generated process is checked with the product's own hand-off validator,
 * with the pack folder written directly — {@code process} is not a build target, so there
 * is no build to add — and a spec set the validator rejects is named and never scored.
 *
 * <p>
 * Offline. No endpoint, no model call: the spec sets are the fixtures in
 * {@link ProcessFixtures}, and the scorer is a counter, which is what makes "not scored"
 * an assertion about a call that did not happen rather than a claim about a null field.
 * The counter is the whole point — a sound process reaches it and an unsound one does
 * not, so the same instrument shows both verdicts.
 *
 * @author Ivan Rodriguez
 */
class ProcessHandoffsTest {

	/**
	 * Stands in for the eval a build would run; counts the calls so the test can see
	 * them.
	 */
	private final AtomicInteger scoreCalls = new AtomicInteger();

	private final ToDoubleFunction<AgentsPack> scorer = (process) -> {
		this.scoreCalls.incrementAndGet();
		return 1.0;
	};

	@Test
	void aSoundProcessIsWrittenAsAPackFolderReadBackAndScored(@TempDir Path dir) {
		Path packDir = dir.resolve("self-build");

		ProcessHandoffs.Verdict verdict = ProcessHandoffs.verify(ProcessFixtures.soundProcess(), packDir, this.scorer);

		// The folder is the artifact: one manifest for the process, one pack directory
		// per
		// agent, which is what a source scans for when it looks for something to ingest.
		assertThat(packDir.resolve("process.yml")).isRegularFile();
		assertThat(packDir.resolve("compile-description/manifest.yml")).isRegularFile();
		assertThat(packDir.resolve("compile-description/goal.yml")).isRegularFile();
		assertThat(packDir.resolve("compile-description/steps/compile.yml")).isRegularFile();
		assertThat(packDir.resolve("publish-pack/manifest.yml")).isRegularFile();

		// Validation ran on what the folder declares, and the run order survived the
		// round
		// trip: the hand-off the validator judged is the one on disk.
		assertThat(verdict.process().agents()).extracting(AgentSpec::name)
			.containsExactly("compile-description", "publish-pack");
		assertThat(verdict.validation().sharedTypes()).as(() -> verdict.issues().toString()).isEqualTo(1);
		assertThat(verdict.issues()).isEmpty();
		assertThat(verdict.candidate().valid()).isTrue();
		assertThat(verdict.candidate().name()).isEqualTo("self-build");
		assertThat(verdict.scored()).isTrue();
		assertThat(this.scoreCalls).hasValue(1);
	}

	@Test
	void theGeneratedHandoffsValidateOnceTheyAreOnDisk(@TempDir Path dir) {
		// The offline stand-in for the live claim: what the generator emits is what gets
		// written, read back and judged — not a tidier hand-authored spec set. The model
		// answer
		// is canned, so a failure here is the generator's shape or the writer, not a
		// model.
		AgentsPackDraft draft = new SingleShotAgentsPackGenerator((system, user) -> ProcessFixtures.TWO_AGENT_ANSWER,
				SingleShotAgentsPackGenerator.defaultPrompt())
			.generateAgentsPack(SelfDescription.text());

		ProcessHandoffs.Verdict verdict = ProcessHandoffs.verify(draft.process(), dir.resolve("generated"),
				this.scorer);

		assertThat(verdict.validation().sharedTypes()).as(() -> verdict.issues().toString()).isEqualTo(1);
		assertThat(verdict.issues()).isEmpty();
		assertThat(verdict.candidate().valid()).isTrue();
		assertThat(verdict.scored()).isTrue();
		assertThat(this.scoreCalls).hasValue(1);
	}

	@Test
	void anUncoveredInputIsNamedAndNeverScored(@TempDir Path dir) {
		ProcessHandoffs.Verdict verdict = ProcessHandoffs.verify(ProcessFixtures.uncoveredInputProcess(),
				dir.resolve("packs"), this.scorer);

		// Named: the finding says which agent, which step, and which input nothing
		// produces.
		assertThat(verdict.issues())
			.anyMatch((issue) -> issue.contains("agent 'publish-pack'") && issue.contains("step 'publish'")
					&& issue.contains("needs input 'ReviewedDraft', which no agent produces"));
		assertThat(verdict.candidate().valid()).isFalse();
		assertThat(verdict.candidate().name()).as("named, so the report can say what was rejected")
			.isEqualTo("self-build");
		assertThat(verdict.scored()).isFalse();
		assertThat(this.scoreCalls).as("an unsound process is not worth a model call").hasValue(0);
	}

	@Test
	void aCycleNoReorderCanSettleIsNamedAndNeverScored(@TempDir Path dir) {
		ProcessHandoffs.Verdict verdict = ProcessHandoffs.verify(ProcessFixtures.mutualCycleProcess(),
				dir.resolve("packs"), this.scorer);

		assertThat(verdict.validation().hasCycle()).isTrue();
		assertThat(verdict.validation().cycle()).as(() -> verdict.issues().toString())
			.containsExactlyInAnyOrder("draft", "score");
		// The repair is reorder-only, so a mutual data dependency — not a wrong order —
		// is
		// reported rather than worked around.
		assertThat(verdict.reorders()).isZero();
		assertThat(verdict.candidate().valid()).isFalse();
		assertThat(verdict.scored()).isFalse();
		assertThat(this.scoreCalls).hasValue(0);
	}

	@Test
	void aCycleThatIsOnlyTheWrongRunOrderIsReorderedAndThenScored(@TempDir Path dir) {
		// The control. The same validator, the same repair and the same scorer, on the
		// other
		// shape of cycle — a declared order that contradicts a sound data flow. It
		// settles, so
		// the tests above cannot be read as this module refusing to score anything.
		ProcessHandoffs.Verdict verdict = ProcessHandoffs.verify(ProcessFixtures.wrongOrderProcess(),
				dir.resolve("packs"), this.scorer);

		assertThat(verdict.reorders()).as("one topological pass settles a wrong order").isEqualTo(1);
		assertThat(verdict.process().agents()).extracting(AgentSpec::name)
			.containsExactly("compile-description", "publish-pack");
		assertThat(verdict.issues()).as(() -> verdict.issues().toString()).isEmpty();
		assertThat(verdict.candidate().valid()).isTrue();
		assertThat(verdict.scored()).isTrue();
		assertThat(verdict.candidate().score()).isEqualTo(1.0);
	}

}
