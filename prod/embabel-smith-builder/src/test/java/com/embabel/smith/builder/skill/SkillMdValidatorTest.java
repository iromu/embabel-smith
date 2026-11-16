/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests for the frontmatter gate (task 1.2): each Agent Skills constraint is
 * violated in turn and the validator must name that specific constraint, so a blocked
 * build says what is wrong rather than merely refusing.
 *
 * @author Ivan Rodriguez
 */
class SkillMdValidatorTest {

	private static final String VALID_NAME = "cold-store-goods-receipt";

	private static final String VALID_DESCRIPTION = "Handle chilled goods receipts and reject temperature breaches.";

	@Test
	void aWellFormedSkillHasNoIssues() {
		assertThat(SkillMdValidator.validate(VALID_NAME, VALID_DESCRIPTION)).isEmpty();
	}

	@Test
	void aMissingNameIsReported() {
		assertThat(SkillMdValidator.validate(null, VALID_DESCRIPTION)).containsExactly("name is missing");
		assertThat(SkillMdValidator.validate("", VALID_DESCRIPTION)).containsExactly("name is missing");
	}

	@Test
	void anOverLongNameIsReportedWithItsLength() {
		assertThat(SkillMdValidator.validate("a".repeat(65), VALID_DESCRIPTION))
			.anyMatch((issue) -> issue.startsWith("name exceeds 64 characters (65)"));
	}

	@Test
	void aNameAtTheLengthLimitIsAccepted() {
		assertThat(SkillMdValidator.validate("a".repeat(64), VALID_DESCRIPTION)).isEmpty();
	}

	@Test
	void uppercaseAndPunctuationInANameAreReported() {
		assertThat(SkillMdValidator.validate("Cold_Store", VALID_DESCRIPTION))
			.anyMatch((issue) -> issue.startsWith("name has characters other than"));
	}

	@Test
	void leadingTrailingAndConsecutiveHyphensAreEachReported() {
		assertThat(SkillMdValidator.validate("-leading", VALID_DESCRIPTION))
			.anyMatch((issue) -> issue.equals("name starts with a hyphen"));
		assertThat(SkillMdValidator.validate("trailing-", VALID_DESCRIPTION))
			.anyMatch((issue) -> issue.equals("name ends with a hyphen"));
		assertThat(SkillMdValidator.validate("double--hyphen", VALID_DESCRIPTION))
			.anyMatch((issue) -> issue.equals("name contains consecutive hyphens"));
	}

	@Test
	void aMissingDescriptionIsReported() {
		assertThat(SkillMdValidator.validate(VALID_NAME, null)).containsExactly("description is empty");
		assertThat(SkillMdValidator.validate(VALID_NAME, "   ")).containsExactly("description is empty");
	}

	@Test
	void anOverLongDescriptionIsReportedWithItsLength() {
		assertThat(SkillMdValidator.validate(VALID_NAME, "d".repeat(1025)))
			.anyMatch((issue) -> issue.startsWith("description exceeds 1024 characters (1025)"));
	}

	@Test
	void aDescriptionAtTheLengthLimitIsAccepted() {
		assertThat(SkillMdValidator.validate(VALID_NAME, "d".repeat(1024))).isEmpty();
	}

	@Test
	void everyViolationOfOneSkillIsReportedTogether() {
		assertThat(SkillMdValidator.validate("Bad_Name-", "")).hasSize(3)
			.anyMatch((issue) -> issue.startsWith("name has characters other than"))
			.anyMatch((issue) -> issue.equals("name ends with a hyphen"))
			.anyMatch((issue) -> issue.equals("description is empty"));
	}

}
