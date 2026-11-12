package com.embabel.smith.studio.improve;

import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.runtime.trace.TraceRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RefinerTest {

	@Test
	void onlyFailingStepPromptChanges() {
		AgentSpec pack = TestFixtures.pack("extract", "s1", "s2", "s3");
		List<TraceRecord> failures = List
			.of(TestFixtures.failedTrace("r1", "extract", "input", TestFixtures.step("s1", "completed", "ok"),
					TestFixtures.step("s2", "failed", "bad output"), TestFixtures.step("s3", "completed", "ok")));

		Refiner refiner = new Refiner((current, feedback) -> current + " [revised]");
		Refiner.Refinement refinement = refiner.refine(pack, failures);

		assertThat(refinement.changed()).isTrue();
		assertThat(refinement.changedSteps()).containsExactly("s2");
		assertThat(refinement.candidate().step("s2").prompt()).isNotEqualTo(pack.step("s2").prompt());
		assertThat(refinement.candidate().step("s2").prompt()).contains("[revised]");
		// The steps without failure evidence are left unchanged.
		assertThat(refinement.candidate().step("s1").prompt()).isEqualTo(pack.step("s1").prompt());
		assertThat(refinement.candidate().step("s3").prompt()).isEqualTo(pack.step("s3").prompt());
	}

	@Test
	void evidenceIsInRewriteInput() {
		AgentSpec pack = TestFixtures.pack("extract", "s2");
		List<TraceRecord> failures = List.of(TestFixtures.failedTrace("r1", "extract", "the input",
				TestFixtures.step("s2", "failed", "the output excerpt")));

		String[] captured = new String[1];
		Refiner refiner = new Refiner((current, feedback) -> {
			captured[0] = feedback;
			return current + " [revised]";
		});
		refiner.refine(pack, failures);

		assertThat(captured[0]).contains("the input");
		assertThat(captured[0]).contains("the output excerpt");
		assertThat(captured[0]).contains("run failed"); // the failure reason
	}

	@Test
	void noFailureEvidenceLeavesPackUnchanged() {
		AgentSpec pack = TestFixtures.pack("extract", "s1", "s2");
		// A failed run with no executed steps (e.g. a model abort) gives no step-level
		// evidence.
		List<TraceRecord> failures = List.of(TestFixtures.failedTrace("r1", "extract", "input"));

		Refiner refiner = new Refiner((current, feedback) -> current + " [revised]");
		Refiner.Refinement refinement = refiner.refine(pack, failures);

		assertThat(refinement.changed()).isFalse();
		assertThat(refinement.changedSteps()).isEmpty();
		assertThat(refinement.candidate()).isSameAs(pack);
	}

}
