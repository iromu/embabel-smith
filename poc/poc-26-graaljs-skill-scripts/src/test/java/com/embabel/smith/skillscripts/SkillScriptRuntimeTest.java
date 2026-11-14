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

import java.util.List;

import com.embabel.agent.skills.Skills;
import com.embabel.agent.skills.script.NoOpExecutionEngine;
import com.embabel.agent.skills.script.ScriptExecutionResult;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The POC's central claim, pinned offline: with an engine installed, Embabel turns a skill's
 * {@code scripts/} folder into tools the agent can call — and with Embabel's default
 * {@link NoOpExecutionEngine} it does not.
 * @author Ivan Rodriguez
 */
class SkillScriptRuntimeTest {

	private static final String INVOICE = """
			{
			  "number": "INV-2026-014",
			  "currency": "EUR",
			  "lines": [
			    { "description": "Cold-chain pallet", "category": "goods", "qty": 12, "unitPrice": 84.99 },
			    { "description": "Refrigerated haulage", "category": "freight", "qty": 1, "unitPrice": 240.00 }
			  ]
			}
			""";

	private final SkillScriptRuntime runtime = new SkillScriptRuntime(new GraalJsSkillScriptExecutionEngine());

	private Skills skills() {
		return this.runtime.skillsFrom(SkillFixtures.bundledSkillsDir());
	}

	@Test
	void theBundledSkillIsLoadedWithItsScriptDiscovered() {
		Skills skills = skills();

		assertThat(skills.getSkills()).extracting((loaded) -> loaded.getName()).contains("invoice-totalling");
		assertThat(skills.getSkills().get(0).hasScripts()).isTrue();
		assertThat(skills.getSkills().get(0).getScripts())
			.extracting((script) -> script.getFileName())
			.contains("compute_totals.js");
	}

	@Test
	void installingTheEngineTurnsTheScriptIntoACallableTool() {
		List<String> withEngine = this.runtime.toolNames(skills());
		List<String> withoutEngine = new SkillScriptRuntime(NoOpExecutionEngine.INSTANCE)
			.toolNames(new SkillScriptRuntime(NoOpExecutionEngine.INSTANCE).skillsFrom(SkillFixtures.bundledSkillsDir()));

		assertThat(withEngine).anyMatch((name) -> name.contains("compute_totals"));
		// This is the gap the POC closes: Embabel's default engine offers no script tool at all.
		assertThat(withoutEngine).noneMatch((name) -> name.contains("compute_totals"));
		// The non-script skill tools are present in both, so the difference is the script.
		assertThat(withoutEngine).contains("activate");
	}

	@Test
	void theScriptToolComputesTheFiguresThroughTheToolEntryPoint() {
		String input = """
				{"args": [], "stdin": %s, "inputFiles": []}
				""".formatted(jsonString(INVOICE));

		String output = this.runtime.callTool(skills(), scriptToolName(), input);

		assertThat(output).contains("\"netTotal\": 1259.88")
			.contains("\"vatTotal\": 50.4")
			.contains("\"payable\": 1310.28")
			.contains("\"checksum\"");
	}

	@Test
	void theScriptAppliesTheDiscountRulesAndRejectsAnUnapprovedDiscount() {
		ScriptExecutionResult discounted = this.runtime.runScript(skills(), "invoice-totalling", "compute_totals.js",
				List.of("--discount=10"), INVOICE, List.of());
		assertThat(discounted).isInstanceOf(ScriptExecutionResult.Success.class);
		assertThat(((ScriptExecutionResult.Success) discounted).getStdout()).contains("\"netTotal\": 1157.89")
			.contains("\"payable\": 1208.29");

		ScriptExecutionResult unapproved = this.runtime.runScript(skills(), "invoice-totalling", "compute_totals.js",
				List.of("--discount=20"), INVOICE, List.of());
		assertThat(unapproved).isInstanceOf(ScriptExecutionResult.Success.class);
		ScriptExecutionResult.Success rejected = (ScriptExecutionResult.Success) unapproved;
		assertThat(rejected.getExitCode()).isEqualTo(2);
		assertThat(rejected.getStderr()).contains("REJECTED").contains("needs an --approval");
	}

	@Test
	void theScriptRejectsAnUnknownCategoryRatherThanDefaultingIt() {
		String unknownCategory = """
				{
				  "number": "INV-2026-015",
				  "lines": [
				    { "description": "Widgets", "category": "widgets", "qty": 1, "unitPrice": 10.00 }
				  ]
				}
				""";

		ScriptExecutionResult result = this.runtime.runScript(skills(), "invoice-totalling", "compute_totals.js",
				List.of(), unknownCategory, List.of());

		assertThat(result).isInstanceOf(ScriptExecutionResult.Success.class);
		ScriptExecutionResult.Success rejected = (ScriptExecutionResult.Success) result;
		assertThat(rejected.getExitCode()).isEqualTo(2);
		assertThat(rejected.getStderr()).contains("unknown category 'widgets'");
	}

	private String scriptToolName() {
		return this.runtime.toolNames(skills())
			.stream()
			.filter((name) -> name.contains("compute_totals"))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("No script tool was exposed for compute_totals.js"));
	}

	private static String jsonString(String raw) {
		return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
	}

}
