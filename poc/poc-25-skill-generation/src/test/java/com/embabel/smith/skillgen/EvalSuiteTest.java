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

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Offline tests for the evals.json suite: the bundled suites parse and are well-formed, and
 * inline JSON parses.
 *
 * @author Ivan Rodriguez
 */
class EvalSuiteTest {

	@Test
	void loadsBundledAuroraSuite() {
		EvalSuite suite = EvalSource.load("aurora");
		assertThat(suite.skillName()).isEqualTo("aurora");
		assertThat(suite.evals()).hasSize(4);
		assertThat(suite.evals())
			.allSatisfy((e) -> {
				assertThat(e.prompt()).isNotBlank();
				assertThat(e.expectations()).isNotEmpty();
			});
	}

	@Test
	void loadsAllBundledSuites() {
		for (String slug : List.of("aurora", "sentinel", "tradingagents")) {
			EvalSuite suite = EvalSource.load(slug);
			assertThat(suite.evals()).as(slug).isNotEmpty();
		}
	}

	@Test
	void parsesInlineJson() {
		String json = "{\"skill_name\":\"x\",\"evals\":[{\"id\":1,\"prompt\":\"p\",\"expected_output\":\"e\","
				+ "\"files\":[],\"expectations\":[\"a\",\"b\"]}]}";
		EvalSuite suite = EvalSuite.parse(json);
		assertThat(suite.skillName()).isEqualTo("x");
		assertThat(suite.evals()).hasSize(1);
		assertThat(suite.evals().get(0).expectations()).containsExactly("a", "b");
	}

	@Test
	void missingFilesAndExpectationsDefaultToEmpty() {
		EvalSuite suite = EvalSuite.parse("{\"skill_name\":\"x\",\"evals\":[{\"id\":1,\"prompt\":\"p\"}]}");
		assertThat(suite.evals().get(0).files()).isEmpty();
		assertThat(suite.evals().get(0).expectations()).isEmpty();
	}

}
