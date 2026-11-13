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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Offline tests for the skill generator: a canned model answer is parsed into a {@link Skill},
 * the name is fixed by the blueprint, and markdown fences are stripped.
 *
 * @author Ivan Rodriguez
 */
class SkillGeneratorTest {

	@Test
	void parsesModelAnswerIntoSkill() {
		ChatCaller stub = (system, user) -> "{\"description\":\"Handles X. Use when...\",\"body\":\"# Steps\\n1. Do A\"}";
		Skill skill = new SkillGenerator(stub, "SYS").generate("aurora", "blueprint text");
		assertThat(skill.name()).isEqualTo("aurora");
		assertThat(skill.description()).isEqualTo("Handles X. Use when...");
		assertThat(skill.body()).contains("Do A");
	}

	@Test
	void stripsMarkdownFences() {
		ChatCaller stub = (system, user) -> "```json\n{\"description\":\"d\",\"body\":\"b\"}\n```";
		Skill skill = new SkillGenerator(stub, "SYS").generate("aurora", "t");
		assertThat(skill.description()).isEqualTo("d");
		assertThat(skill.body()).isEqualTo("b");
	}

	@Test
	void rendersSkillMdWithFrontmatter() {
		ChatCaller stub = (system, user) -> "{\"description\":\"d\",\"body\":\"body text\"}";
		Skill skill = new SkillGenerator(stub, "SYS").generate("aurora", "t");
		String md = skill.toSkillMd();
		assertThat(md).startsWith("---\nname: aurora\ndescription: d\n---\n\n");
		assertThat(md).contains("body text");
	}

	@Test
	void throwsWhenBodyIsMissing() {
		ChatCaller stub = (system, user) -> "{\"description\":\"d\",\"body\":\"\"}";
		assertThatThrownBy(() -> new SkillGenerator(stub, "SYS").generate("aurora", "t"))
			.isInstanceOf(IllegalStateException.class);
	}

}
