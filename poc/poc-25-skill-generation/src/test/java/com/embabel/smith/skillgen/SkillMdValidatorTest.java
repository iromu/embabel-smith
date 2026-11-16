/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.skillgen;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Offline tests for the Agent Skills frontmatter constraints.
 *
 * @author Ivan Rodriguez
 */
class SkillMdValidatorTest {

	@Test
	void validNameAndDescriptionHaveNoIssues() {
		assertThat(SkillMdValidator.validate("aurora-cold-chain", "Handles AURORA cold-chain orders. Use when..."))
			.isEmpty();
	}

	@Test
	void rejectsUppercaseName() {
		assertThat(SkillMdValidator.validate("Aurora", "x")).anyMatch((i) -> i.contains("lowercase"));
	}

	@Test
	void rejectsLeadingHyphen() {
		assertThat(SkillMdValidator.validate("-aurora", "x")).anyMatch((i) -> i.contains("starts with a hyphen"));
	}

	@Test
	void rejectsTrailingHyphen() {
		assertThat(SkillMdValidator.validate("aurora-", "x")).anyMatch((i) -> i.contains("ends with a hyphen"));
	}

	@Test
	void rejectsConsecutiveHyphens() {
		assertThat(SkillMdValidator.validate("aurora--chain", "x")).anyMatch((i) -> i.contains("consecutive hyphens"));
	}

	@Test
	void rejectsNameOverSixtyFourCharacters() {
		assertThat(SkillMdValidator.validate("a".repeat(65), "x")).anyMatch((i) -> i.contains("64"));
	}

	@Test
	void rejectsEmptyDescription() {
		assertThat(SkillMdValidator.validate("aurora", "   ")).anyMatch((i) -> i.contains("description is empty"));
	}

	@Test
	void rejectsDescriptionOverOneThousandTwentyFourCharacters() {
		assertThat(SkillMdValidator.validate("aurora", "a".repeat(1025))).anyMatch((i) -> i.contains("1024"));
	}

	@Test
	void blueprintSlugIsAcceptedAsName() {
		assertThat(SkillMdValidator.validate("tradingagents", "d")).isEmpty();
	}

}
