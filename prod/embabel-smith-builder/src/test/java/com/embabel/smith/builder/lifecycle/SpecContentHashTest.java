/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.lifecycle;

import java.util.List;

import com.embabel.smith.builder.improve.Detector;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.GoalStepSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a pack has to change for its content hash to move — including a reference
 * declaration, which is the edit that used to be invisible here.
 *
 * <p>
 * <b>Why a reference is load-bearing.</b> A step's {@code references} list asks for the
 * pack's skills to be in play for that step's model call, so a pack whose only edit was
 * to that list is a pack whose behaviour can differ from the version its baseline eval
 * was recorded against. That is the definition of the thing drift detection is for, and
 * until now the canonicalisation — which reads model fields, not files — omitted the
 * component: a pack with a reference and the same pack without one hashed to the same 64
 * hex characters, so the loop read a real change as an untouched deployment. ({@code
 * PackContentHasher}, which hashes the pack's files, has always seen it, since the key is
 * bytes in {@code steps/*.yml}; the two hashes are different quantities and never match
 * each other — see docs/06 §"Content hashes".)
 *
 * <p>
 * <b>Both directions, and the corpus left alone.</b> A gained and a lost declaration are
 * asserted separately because a canonicalisation that appends a clause only when the list
 * is non-empty could in principle see one and not the other. The third test is the one
 * that keeps this a new case rather than a new baseline: a pack that declares nothing
 * hashes to the value the pre-references canonicalisation produced for it — captured from
 * that code, not from this — so no sidecar in the existing corpus starts reading as
 * drifted for a change nobody made. It is not a vacuous equality either: the gained case
 * below compares against the same literal, so were the canonicalisation still ignoring
 * the component, that case would fail rather than this one quietly passing.
 *
 * @author Ivan Rodriguez
 */
class SpecContentHashTest {

	/**
	 * The hash {@link SpecContentHash} produced for {@link #pack(List, List) this pack}
	 * with no references declared, measured from the canonicalisation before the
	 * component was added to it.
	 */
	private static final String NO_REFERENCES = "6f46ceee70d4430368368c54e4a885a8194d9d22782f6b59922aa9249616fe9c";

	private final Detector detector = new Detector();

	@Test
	@DisplayName("a step that gained a reference declaration moves the hash")
	void gainedReferenceDrifts() {
		assertThat(SpecContentHash.of(pack(List.of("repo-map"), List.of())))
			.isNotEqualTo(SpecContentHash.of(pack(List.of(), List.of())))
			.isNotEqualTo(NO_REFERENCES);
	}

	@Test
	@DisplayName("and a step that lost one moves it just the same")
	void lostReferenceDrifts() {
		assertThat(SpecContentHash.of(pack(List.of(), List.of())))
			.as("deleting a declaration is as much a change as writing one")
			.isNotEqualTo(SpecContentHash.of(pack(List.of("repo-map"), List.of())));
	}

	@Test
	@DisplayName("a pack carrying no references hashes exactly as it did before")
	void aPackWithoutReferencesIsUnmoved() {
		assertThat(SpecContentHash.of(pack(List.of(), List.of()))).isEqualTo(NO_REFERENCES);
	}

	@Test
	@DisplayName("a declaration on any step counts, not only on the first")
	void everyStepIsCanonicalisedWithItsOwnDeclaration() {
		assertThat(SpecContentHash.of(pack(List.of(), List.of("repo-map"))))
			.as("the second step declares it, so the pack differs from the one that declares nothing")
			.isNotEqualTo(NO_REFERENCES)
			.isNotEqualTo(SpecContentHash.of(pack(List.of("repo-map"), List.of())));
	}

	@Test
	@DisplayName("the loop reports the drift a reference-only edit causes")
	void theDetectorNamesAReferenceOnlyEditAsContentDrift() {
		// The consumer, not just the number: a sidecar recorded against the pack before a
		// reference was added has to produce the "content drift" reason, which is the
		// sentence the improvement loop acts on.
		AgentSpec before = pack(List.of(), List.of());
		AgentSpec after = pack(List.of("repo-map"), List.of());
		LifecycleMetadata sidecar = new LifecycleMetadata(SpecContentHash.of(before), 1.0, LifecycleState.ACTIVE, "t");
		assertThat(this.detector.detect("drift-demo", after, sidecar, List.of()).reasons()).contains("content drift");
	}

	/**
	 * The pack every case above varies: an exported goal and two steps, each free to
	 * declare its own references.
	 * @param firstRefs the references the {@code draft} step declares
	 * @param secondRefs the references the {@code check} step declares
	 * @return the spec to hash
	 */
	private static AgentSpec pack(List<String> firstRefs, List<String> secondRefs) {
		return new AgentSpec(new AgentSpecManifest("drift-demo", "1.0.0"),
				GoalStepSpec.exportedGoal("drift-demo", "goal drift-demo", "BackgroundMessage"),
				List.of(step("draft", firstRefs), step("check", secondRefs)), List.of());
	}

	/**
	 * One step, declaring nothing but the references it is handed.
	 * @param name the step name
	 * @param references its {@code references} list, empty for none
	 * @return the step
	 */
	private static ActionStepSpec step(String name, List<String> references) {
		return new ActionStepSpec(ActionStepSpec.STEP_TYPE, name, "step " + name, List.of("UserInput"),
				"BackgroundMessage", "perform " + name + " carefully {{userInput.content}}", null, null, null, null,
				null, null, null, references);
	}

}
