/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.skillgen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Offline tests for the extraction step: a canned model answer is parsed into the ordered
 * list of skill scopes, names are normalized to valid slugs, and an empty decomposition
 * is rejected.
 *
 * @author Ivan Rodriguez
 */
class SkillDecomposerTest {

	@Test
	void parsesDecompositionIntoOrderedScopes() {
		ChatCaller stub = (system,
				user) -> "{\"skills\":[{\"name\":\"aurora-order-intake\",\"scope\":\"Owns order intake\"},"
						+ "{\"name\":\"aurora-delivery\",\"scope\":\"Owns delivery confirmation\"}]}";
		List<SkillDecomposer.SkillScope> scopes = new SkillDecomposer(stub, "SYS").decompose("blueprint text");
		assertThat(scopes).hasSize(2);
		assertThat(scopes.get(0).name()).isEqualTo("aurora-order-intake");
		assertThat(scopes.get(0).scope()).contains("order intake");
		assertThat(scopes.get(1).name()).isEqualTo("aurora-delivery");
	}

	@Test
	void normalizesNamesToValidSlugs() {
		ChatCaller stub = (system, user) -> "{\"skills\":[{\"name\":\"Aurora Order Intake!\",\"scope\":\"s\"}]}";
		List<SkillDecomposer.SkillScope> scopes = new SkillDecomposer(stub, "SYS").decompose("t");
		assertThat(scopes).hasSize(1);
		assertThat(scopes.get(0).name()).isEqualTo("aurora-order-intake");
	}

	@Test
	void skipsSkillsMissingANameOrScope() {
		ChatCaller stub = (system,
				user) -> "{\"skills\":[{\"name\":\"\",\"scope\":\"s\"},{\"name\":\"x\",\"scope\":\"   \"},"
						+ "{\"name\":\"good\",\"scope\":\"scope text\"}]}";
		List<SkillDecomposer.SkillScope> scopes = new SkillDecomposer(stub, "SYS").decompose("t");
		assertThat(scopes).hasSize(1);
		assertThat(scopes.get(0).name()).isEqualTo("good");
	}

	@Test
	void stripsMarkdownFences() {
		ChatCaller stub = (system, user) -> "```json\n{\"skills\":[{\"name\":\"one\",\"scope\":\"scope text\"}]}\n```";
		List<SkillDecomposer.SkillScope> scopes = new SkillDecomposer(stub, "SYS").decompose("t");
		assertThat(scopes).hasSize(1);
		assertThat(scopes.get(0).name()).isEqualTo("one");
	}

	@Test
	void throwsWhenNoSkillsAreReturned() {
		ChatCaller stub = (system, user) -> "{\"skills\":[]}";
		assertThatThrownBy(() -> new SkillDecomposer(stub, "SYS").decompose("t"))
			.isInstanceOf(IllegalStateException.class);
	}

}
