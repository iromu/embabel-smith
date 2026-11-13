/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
