/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.iromu.embabel.smith.skillevaluator.Tier1Runner;
import org.iromu.embabel.smith.skillevaluator.model.Finding;
import org.iromu.embabel.smith.skillevaluator.model.Severity;
import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;
import org.junit.jupiter.api.Test;

import com.embabel.smith.builder.skill.SkillInspector.Inspection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests for the inspection seam: a canned {@link Skill} is materialised and
 * checked by the evaluator, and canned {@link ValidationResult}s stand in for the results
 * a real run cannot produce on demand (an incomplete scan, a high-severity PII hit). No
 * model endpoint, and none of {@code gitleaks}, {@code skillspector}, {@code bandit} or
 * {@code semgrep} is needed — the default lineup never asks for a check that shells out.
 *
 * @author Ivan Rodriguez
 */
class SkillInspectorTest {

	private static final String SKILL_NAME = "inspect-cold-store-goods-receipt";

	private static final String DESCRIPTION = "Use when receiving chilled or frozen goods into a cold store, "
			+ "checking the carrier's temperature record before accepting a delivery.";

	/**
	 * A draft written the way the skill writing guidelines ask: title, sections,
	 * examples.
	 */
	private static Skill wellFormedSkill() {
		return new Skill(SKILL_NAME, DESCRIPTION, """
				# Cold store goods receipt

				Receive chilled and frozen goods against the carrier's temperature record.

				## Instructions

				1. Read the temperature record the carrier hands over.
				2. Compare every reading with the stored range for the product.
				3. Reject the delivery and escalate to the duty manager when a reading is outside the range.
				4. Ask the recipient to confirm before closing the receipt.

				## Examples

				- Chilled dairy at 6 C against a 0-4 C range: reject and escalate.
				- Frozen goods at -18 C: accept and file the record.

				## Troubleshooting

				- A missing temperature record is treated as a breach until the carrier supplies one.
				""");
	}

	/** A draft the authoring rules refuse: no top-level title, and PII-shaped text. */
	private static Skill malformedSkill() {
		return new Skill(SKILL_NAME, DESCRIPTION, """
				Drop the pallet on the receiving bay and log it.
				Call the site lead, john.doe@acmecorp-consulting.com, on 555-123-4567.
				""");
	}

	private static Path materialisedFolder(String skillName) {
		return Path.of(System.getProperty("java.io.tmpdir")).resolve(skillName);
	}

	@Test
	void theSkillIsMaterialisedUnderAFolderNamedAfterItAndRemovedAfterwards() {
		// The folder name has to be the skill name: the schema check reports a HIGH
		// name_consistency finding when frontmatter and folder disagree, which would
		// block
		// every build. The evaluator names the file it read in each finding, so a passing
		// schema run is the proof of the path it was given.
		Inspection inspection = new SkillInspector().inspect(malformedSkill(), false);

		assertThat(inspection.blockingFindings()).as("findings name the materialised path")
			.anyMatch(finding -> finding.contains(SKILL_NAME + "/SKILL.md"));
		assertThat(inspection.blockingFindings()).as("the folder was not named after the skill")
			.noneMatch(finding -> finding.contains("doesn't match frontmatter name"));
		assertThat(materialisedFolder(SKILL_NAME)).doesNotExist();
	}

	@Test
	void noTempFolderSurvivesAFailingInspection() {
		Path folder = materialisedFolder(SKILL_NAME);
		assertThat(new SkillInspector().inspect(malformedSkill(), false).passed()).isFalse();
		assertThat(folder).as("a failed inspection must still clean up").doesNotExist();
	}

	@Test
	void aDraftThatBreaksTheAuthoringRulesIsBlocked() {
		Inspection inspection = new SkillInspector().inspect(malformedSkill(), false);

		assertThat(inspection.passed()).isFalse();
		assertThat(inspection.blockingFindings()).isNotEmpty();
		// The body has no top-level heading, which is the one body rule that hard-fails.
		assertThat(inspection.blockingFindings()).anyMatch(finding -> finding.contains("Missing top-level heading"));
	}

	@Test
	void aWellFormedDraftIsNotBlockedAndStartsNoExternalScan() {
		Inspection inspection = new SkillInspector().inspect(wellFormedSkill(), false);

		assertThat(inspection.blockingFindings()).isEmpty();
		assertThat(inspection.passed()).isTrue();
		// An incomplete scan is only ever recorded by a check that shells out, so an
		// empty
		// list here is the proof that no ProcessBuilder was reachable from this lineup.
		assertThat(inspection.incompleteScans()).isEmpty();
		// The advisory list is never empty for a generated skill: it has no LICENSE file,
		// no metadata.author, and it is materialised outside skills/, so the checks that
		// report those as MEDIUM/LOW always have something to say. What a well-formed
		// draft
		// must not get is a blocking finding, and every advisory is labelled with the
		// check
		// that produced it so a reader can tell a ran-and-found-nothing scan from a
		// skipped
		// one.
		assertThat(inspection.advisoryFindings()).isNotEmpty().allMatch(advisory -> advisory.contains(": "));
		assertThat(inspection.advisoryFindings()).anyMatch(advisory -> advisory.startsWith("license: "));
		assertThat(materialisedFolder(SKILL_NAME)).doesNotExist();
	}

	@Test
	void aHighSeverityPiiFindingIsAdvisoryAndNeverBlocks() {
		ValidationResult pii = new ValidationResult();
		pii.setValidatorName("PII Scan");
		pii.addFinding(new Finding("PII", Severity.HIGH, "pii_email", "Email address found in content",
				"/tmp/" + SKILL_NAME + "/SKILL.md", 3, null, "Remove the address", null));

		Inspection inspection = SkillInspector.classify(List.of(pii), List.of());

		assertThat(pii.isPassed()).as("the stub really does fail").isFalse();
		assertThat(inspection.passed()).as("pii never owns the verdict, whatever its severity").isTrue();
		assertThat(inspection.blockingFindings()).isEmpty();
		assertThat(inspection.advisoryFindings()).singleElement()
			.satisfies(advisory -> assertThat(advisory).startsWith("pii: ")
				.contains("Email address found in content")
				.contains("Remove the address"));
	}

	@Test
	void aFailingCheckOtherThanPiiStillBlocks() {
		// The counterpart of the rule above: the advisory downgrade is keyed on the check
		// name, so no other check can borrow it.
		ValidationResult schema = new ValidationResult();
		schema.setValidatorName("Schema & Repository Governance");
		schema.addFinding(new Finding("SCHEMA", Severity.HIGH, "body_heading", "Body is missing a top-level title",
				"/tmp/" + SKILL_NAME + "/SKILL.md", 1, null, "Add a '# Title' heading", null));

		Inspection inspection = SkillInspector.classify(List.of(schema), List.of());

		assertThat(inspection.passed()).isFalse();
		assertThat(inspection.advisoryFindings()).isEmpty();
		assertThat(inspection.blockingFindings()).singleElement()
			.satisfies(finding -> assertThat(finding).contains("Body is missing a top-level title")
				.contains("Add a '# Title' heading"));
	}

	@Test
	void anIncompleteScanIsNeitherAPassNorABlock() {
		ValidationResult security = new ValidationResult();
		security.setValidatorName("Security Scan");
		security.addWarning("skillspector not installed - skipping security scan.");
		security.markScanIncomplete("skillspector");
		ValidationResult unicode = new ValidationResult();
		unicode.setValidatorName("Unicode Smuggling Detection");

		Inspection clean = SkillInspector.classify(List.of(security, unicode), List.of());

		assertThat(security.getStatus()).isEqualTo("incomplete");
		assertThat(clean.incompleteScans()).containsExactly("skillspector");
		assertThat(clean.passed()).as("the tool inventory of the machine does not decide the verdict").isTrue();
		assertThat(clean.blockingFindings()).isEmpty();

		// The verdict still moves on findings alone when there are findings.
		ValidationResult schema = new ValidationResult();
		schema.setValidatorName("Schema & Repository Governance");
		schema.addError("SKILL.md not found in skill directory");

		Inspection dirty = SkillInspector.classify(List.of(security, unicode, schema), List.of());
		assertThat(dirty.passed()).isFalse();
		assertThat(dirty.blockingFindings()).containsExactly("SKILL.md not found in skill directory");
		assertThat(dirty.incompleteScans()).containsExactly("skillspector");
	}

	@Test
	void aQualityGateFailureBlocksOnItsOwn() {
		// The score shortfall is a gate failure with no finding of its own; losing it
		// would
		// silently pass every thin skill.
		ValidationResult quality = new ValidationResult();
		quality.setValidatorName("QUALITY");
		quality.failGate("Quality score 61.0/100 is below the required 70/100 in SKILL.md");

		Inspection inspection = SkillInspector.classify(List.of(quality), List.of());

		assertThat(inspection.passed()).isFalse();
		assertThat(inspection.blockingFindings())
			.containsExactly("Quality score 61.0/100 is below the required 70/100 in SKILL.md");
	}

	@Test
	void theDefaultRunDisclosesTheToolBackedChecksAndAFullRunDoesNot() {
		Inspection inspection = new SkillInspector().inspect(wellFormedSkill(), false);
		assertThat(inspection.checksNotRun()).containsExactly("security", "secrets", "code-integrity");

		assertThat(SkillInspector.requestedChecks(false)).doesNotContain("security", "secrets", "code-integrity");
		assertThat(SkillInspector.requestedChecks(false)).contains("schema", "version", "quality", "unicode", "lint",
				"license", "pii");

		// The full lineup is asserted at the request level, never executed: this machine
		// has
		// gitleaks, skillspector and semgrep installed, so a real --full-inspection run
		// in
		// the suite would shell out to all three.
		assertThat(SkillInspector.requestedChecks(true)).isEqualTo(Tier1Runner.DEFAULT_CHECKS);
		assertThat(SkillInspector.classify(List.of(), List.of()).checksNotRun()).isEmpty();
		// `secrets` is disclosed but not requestable: the evaluator runs it inside
		// code-integrity, and asking for it by name is an unknown-check error.
		assertThat(Tier1Runner.DEFAULT_CHECKS).doesNotContain("secrets");
	}

	@Test
	void aPreexistingFolderOfTheSameNameIsNotDeleted() throws Exception {
		// The materialised folder is created under the system temp root under a name the
		// skill chooses, so a pre-existing path of the same name must not be deleted.
		Path folder = materialisedFolder("inspect-foreign-folder");
		Files.createDirectories(folder);
		Path foreign = folder.resolve("keep-me.txt");
		Files.writeString(foreign, "not ours");

		new SkillInspector().inspect(new Skill("inspect-foreign-folder", DESCRIPTION, "# Title\n\nBody."), false);

		assertThat(foreign).exists();
		Files.deleteIfExists(foreign);
		Files.deleteIfExists(folder);
	}

}
