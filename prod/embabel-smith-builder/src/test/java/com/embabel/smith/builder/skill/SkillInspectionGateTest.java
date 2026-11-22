/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.embabel.smith.builder.BuildOptions;
import com.embabel.smith.builder.core.BuildTarget;
import com.embabel.smith.builder.core.GenerationTechnique;
import com.embabel.smith.builder.core.PackName;
import com.embabel.smith.builder.core.SpecPromptCaller;
import com.embabel.smith.builder.publish.PublishTarget;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests for the inspection gate inside the skill pipeline: where it stops the
 * build, how many drafts the redraft loop is allowed, what it hands back to the model and
 * what it refuses to, and what the report says when it refuses. Every test here runs with
 * a scripted {@link SpecPromptCaller} and the default lineup, so no model endpoint is
 * needed and none of the four scanner binaries is invoked.
 *
 * @author Ivan Rodriguez
 */
class SkillInspectionGateTest {

	private static final String SKILL_PROMPT = "GEN-SKILL";

	private static final String EVALS_PROMPT = "GEN-EVALS";

	/**
	 * A draft the authoring rules refuse: no top-level title, and PII-shaped text. The
	 * missing heading is the blocking finding; the email is a {@code pii} advisory, which
	 * is what makes this body the fixture for both halves of the feed-back rule.
	 */
	private static final String REFUSED_BODY = "Drop the pallet on the receiving bay and log it.\\n"
			+ "Call the site lead, john.doe@acmecorp-consulting.com, on 555-123-4567.\\n";

	/**
	 * A name of its own: the gate materialises the draft under
	 * {@code <temp>/<skill name>}, so two test classes sharing a slug would share a
	 * folder.
	 */
	private static final String DESCRIPTION = "Inspect a chilled goods receipt into the cold store";

	private final List<String> systems = new ArrayList<>();

	private final List<String> users = new ArrayList<>();

	private List<String> skillRequests() {
		List<String> requests = new ArrayList<>();
		for (int i = 0; i < this.systems.size(); i++) {
			if (this.systems.get(i).startsWith(SKILL_PROMPT)) {
				requests.add(this.users.get(i));
			}
		}
		return requests;
	}

	private long skillCalls() {
		return this.systems.stream().filter((system) -> system.startsWith(SKILL_PROMPT)).count();
	}

	/**
	 * Runs one scripted build. Any call beyond the two drafting prompts is a failure in
	 * the tests below, because the gate is supposed to stop the build before the arms are
	 * paid for — so the caller throws rather than answering.
	 */
	private SkillBuildResult build(String body, int maxInspect, PublishTarget target) {
		SpecPromptCaller model = (system, user) -> {
			this.systems.add(system);
			this.users.add(user);
			if (system.startsWith(SKILL_PROMPT)) {
				return "{\"description\": \"Handle chilled goods receipts and reject a temperature breach.\", "
						+ "\"body\": \"" + body + "\"}";
			}
			if (system.startsWith(EVALS_PROMPT)) {
				return "{\"skill_name\": \"cold-store\", \"evals\": []}";
			}
			throw new IllegalStateException("the gate should have stopped this build before: " + system);
		};
		BuildOptions options = new BuildOptions(BuildOptions.DEFAULT_MAX_REFINE, BuildOptions.DEFAULT_SCORE_GATE, false,
				false, GenerationTechnique.SINGLE_SHOT, BuildTarget.SKILL, BuildOptions.DEFAULT_MIN_LIFT, null,
				maxInspect, false);
		return new SkillBuilderService(model, SKILL_PROMPT, EVALS_PROMPT, "GRADE", "WITH-ARM", "BASE-ARM")
			.build(DESCRIPTION, target, options);
	}

	private static PublishTarget folderTarget(Path library) {
		return PublishTarget.parse("folder:" + library);
	}

	// --- 3.1: the gate is before the eval drafting

	@Test
	void aRefusedDraftCostsNoEvalScenario(@TempDir Path library) {
		SkillBuildResult result = build(REFUSED_BODY, 0, folderTarget(library));

		assertThat(result.isBuilt()).as(() -> result.report()).isFalse();
		assertThat(result.stage()).isEqualTo("inspect");
		assertThat(result.issues()).anyMatch((issue) -> issue.contains("Missing top-level heading"));
		// The eval suite is what the arms run against, so a build that stops at the gate
		// must not have drafted it — nor drafted anything else the model is paid for.
		assertThat(this.systems).noneMatch((system) -> system.startsWith(EVALS_PROMPT));
		assertThat(library).isEmptyDirectory();
	}

	// --- 3.3: the loop bound

	@Test
	void theLoopDrawsExactlyMaxInspectPlusOneDraftsAndStops(@TempDir Path library) {
		for (int maxInspect : new int[] { 0, 1, 3 }) {
			this.systems.clear();
			this.users.clear();
			SkillBuildResult result = build(REFUSED_BODY, maxInspect, folderTarget(library));

			assertThat(result.stage()).isEqualTo("inspect");
			// One first draft plus one redraft per allowed round, and the last round's
			// draft is the one that was inspected — no redraft is bought after the
			// verdict.
			assertThat(skillCalls()).as("drafts drawn at --max-inspect " + maxInspect).isEqualTo(maxInspect + 1L);
		}
	}

	@Test
	void everyRedraftAfterTheFirstCarriesThePreviousFindings(@TempDir Path library) {
		build(REFUSED_BODY, 2, folderTarget(library));

		List<String> requests = skillRequests();
		assertThat(requests).hasSize(3);
		// Only rounds after the first are redrafts: the first draft has nothing to fix.
		assertThat(requests.get(0)).doesNotContain("<findings>");
		assertThat(requests.subList(1, requests.size()))
			.allSatisfy((request) -> assertThat(request).contains("<findings>").contains("Missing top-level heading"));
	}

	// --- 3.2: the redraft request keeps the original instruction block

	@Test
	void theRedraftAppendsTheFindingsToTheUnchangedInstructionBlock(@TempDir Path library) {
		build(REFUSED_BODY, 1, folderTarget(library));

		List<String> requests = skillRequests();
		assertThat(requests).hasSize(2);
		String first = requests.get(0);
		String redraft = requests.get(1);
		// The findings are appended to the original request, which therefore arrives
		// byte-for-byte unchanged: a redraft is asked to fix named findings, not to
		// answer
		// a different question.
		assertThat(redraft).startsWith(first);
		assertThat(redraft).contains("<document>\n" + DESCRIPTION + "\n</document>");
	}

	// --- 3.5: what is fed back, and what is only reported

	/**
	 * The seam classifies every finding of a failing blocking check as blocking, so a
	 * MEDIUM convention nudge reported by a check that also failed outright rides along
	 * in the redraft; the text that never reaches the model is what the seam records as
	 * advisory — the warnings, and every {@code pii} finding.
	 */
	@Test
	void advisoriesAndPiiAreReportedButNeverFedBack(@TempDir Path library) {
		SkillBuildResult result = build(REFUSED_BODY, 1, folderTarget(library));

		List<String> requests = skillRequests();
		assertThat(requests).hasSize(2);
		String redraft = requests.get(1);
		assertThat(redraft).as("the blocking finding is what the redraft is for").contains("Missing top-level heading");
		// The pii advisory names an address that is in the draft. If advisories were fed
		// back to the model, this is where it would reappear — and it must not, whatever
		// severity the pii validator gives it.
		assertThat(redraft).doesNotContain("acmecorp-consulting").doesNotContain("Email address");
		assertThat(result.inspection().advisoryFindings()).anyMatch((advisory) -> advisory.startsWith("pii: "));
		// Reported, though: the advisory is in the build report, labelled with its check.
		assertThat(result.report()).contains("pii: ");
	}

	@Test
	void noAdvisoryReachesTheRedraftPrompt(@TempDir Path library) {
		SkillBuildResult result = build(REFUSED_BODY, 1, folderTarget(library));

		String redraft = skillRequests().get(1);
		assertThat(result.inspection().advisoryFindings())
			.as("every advisory stays out of the prompt, whatever it says")
			.noneMatch((advisory) -> redraft.contains(advisory));
	}

	// --- 3.6: the last round publishes nothing and opens no publisher

	@Test
	void aSkillStillRefusedAfterTheLastRoundOpensNoPublisher(@TempDir Path out) {
		// A git target is the proof that no publisher was opened: GitPublisher clones in
		// its constructor, so reaching it with a repository that does not exist would
		// throw rather than return this result.
		PublishTarget target = PublishTarget.parse("git:" + out.resolve("no-such-repository"));

		SkillBuildResult result = build(REFUSED_BODY, 1, target);

		assertThat(result.isBuilt()).as(() -> result.report()).isFalse();
		assertThat(result.stage()).isEqualTo("inspect");
		assertThat(result.published()).isNull();
		assertThat(result.report()).contains("SKILL BUILD BLOCKED at inspect").contains("2 drafts");
		assertThat(result.report()).contains("Missing top-level heading");
	}

	// --- 3.7: the gate reads only SKILL.md

	@Test
	void theGateMaterialisesOnlyTheSkillFile(@TempDir Path library) throws Exception {
		// The gate writes the draft to <temp>/<skill name>/SKILL.md. A file already in
		// that folder is the witness: the gate must neither overwrite it nor leave a
		// second
		// artifact of its own behind. evals.json is the file to watch, because scanning
		// it
		// is the other change's job and this gate must not duplicate that.
		Path folder = Path.of(System.getProperty("java.io.tmpdir")).resolve(PackName.slug(DESCRIPTION));
		Files.createDirectories(folder);
		Path witness = folder.resolve("evals.json");
		String seeded = "{\"evals\": [{\"id\": 1, \"prompt\": \"receive a chilled pallet\"}]}";
		Files.writeString(witness, seeded);
		try {
			SkillBuildResult result = build(REFUSED_BODY, 0, folderTarget(library));

			assertThat(result.stage()).isEqualTo("inspect");
			try (var files = Files.list(folder)) {
				assertThat(files.map((path) -> path.getFileName().toString()).toList()).as("the gate's own footprint")
					.containsExactly("evals.json");
			}
			assertThat(Files.readString(witness)).as("the gate wrote nothing over the witness").isEqualTo(seeded);
			// And the evals suite it must not scan was never even drafted at this point.
			assertThat(this.systems).noneMatch((system) -> system.startsWith(EVALS_PROMPT));
		}
		finally {
			Files.deleteIfExists(witness);
			Files.deleteIfExists(folder);
		}
	}

	// --- 4.1a / 4.3: the lineup and its disclosure

	@Test
	void theDefaultLineupRunsSevenChecksAndDisclosesThree(@TempDir Path library) {
		// The gate's own record travels on the result whatever stage the build finally
		// stopped at, so this reads it off a blocked build and never runs a full
		// inspection — this machine has gitleaks, skillspector and semgrep installed, and
		// a real --full-inspection run would shell out to all three.
		SkillBuildResult result = build(REFUSED_BODY, 0, folderTarget(library));

		SkillBuildResult.InspectionReport inspection = result.inspection();
		assertThat(inspection).isNotNull();
		assertThat(inspection.checksRun()).isEqualTo(SkillInspector.requestedChecks(false).size()).isEqualTo(7);
		assertThat(inspection.checksNotRun()).containsExactly("security", "secrets", "code-integrity");
		assertThat(SkillInspector.requestedChecks(false)).contains("schema", "pii");
		assertThat(SkillInspector.requestedChecks(true)).hasSize(9);
		// `secrets` is disclosed as not run on the default path, and it is not a check
		// any
		// lineup can ask for by name — the evaluator runs it inside code-integrity.
		assertThat(SkillInspector.requestedChecks(false)).doesNotContain("secrets");
		assertThat(SkillInspector.requestedChecks(true)).doesNotContain("secrets");
		assertThat(result.report()).contains("not scanned: security, secrets, code-integrity");
	}

}
