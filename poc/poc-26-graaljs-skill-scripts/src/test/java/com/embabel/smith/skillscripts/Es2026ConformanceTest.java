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

package com.embabel.smith.skillscripts;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Measures the ECMAScript 2026 surface the in-JVM engine actually gives a skill script. Each row
 * is a real script executed by the engine, so a pass is an execution, not a claim.
 * @author Ivan Rodriguez
 */
class Es2026ConformanceTest {

	private final List<Es2026Conformance.Row> rows = new Es2026Conformance(new GraalJsSkillScriptExecutionEngine(),
			Path.of(System.getProperty("java.io.tmpdir"))).run();

	@Test
	void everyEs2026FeatureInTheProbeListExecutes() {
		List<String> unsupported = Es2026Conformance.PROBES.stream()
			.filter((probe) -> "ES2026".equals(probe.since()))
			.map(Es2026Conformance.Probe::feature)
			.filter((feature) -> rows.stream()
				.noneMatch((row) -> row.probe().feature().equals(feature) && row.passed()))
			.toList();

		assertThat(unsupported).as("ES2026 features the engine could not execute: " + unsupported).isEmpty();
	}

	@Test
	void theAdjacentFeaturesARealScriptNeedsAlsoExecute() {
		assertThat(rows).filteredOn((row) -> !"ES2026".equals(row.probe().since()))
			.allMatch(Es2026Conformance.Row::passed);
	}

	@Test
	void theProbeListCoversTheWholeApprovedEs2026Surface() {
		assertThat(Es2026Conformance.PROBES).extracting(Es2026Conformance.Probe::feature)
			.contains("Math.sumPrecise", "Iterator.concat", "Array.fromAsync", "Error.isError",
					"Map.prototype.getOrInsert", "Map.prototype.getOrInsertComputed", "WeakMap.prototype.getOrInsert",
					"Uint8Array to/fromBase64 and to/fromHex", "JSON.rawJSON", "JSON.parse reviver source argument");
	}

	@Test
	void theReportRendersEveryRow() {
		String markdown = Es2026Conformance.toMarkdown(rows);

		assertThat(markdown).contains("| Feature | Since | Engine |");
		for (Es2026Conformance.Row row : rows) {
			assertThat(markdown).contains("`" + row.probe().feature() + "`");
		}
		assertThat(rows).hasSize(Es2026Conformance.PROBES.size());
	}

}
