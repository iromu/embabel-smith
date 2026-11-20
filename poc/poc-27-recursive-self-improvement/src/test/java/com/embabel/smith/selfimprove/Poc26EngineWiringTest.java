/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove;

import com.embabel.agent.skills.script.ScriptLanguage;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;
import com.embabel.smith.skillscripts.GraalJsSkillScriptExecutionEngine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wiring check for the POC 26 dependency: the script engine this module's generated
 * policies are going to run on resolves on this module's classpath and is usable, with no
 * endpoint and no model call. It is deliberately one assertion — if the dependency goes
 * missing the module stops compiling, and if the engine's JavaScript support goes missing
 * this fails.
 *
 * @author Ivan Rodriguez
 */
class Poc26EngineWiringTest {

	@Test
	void graalJsEngineFromPoc26ResolvesAndRunsJavaScript() {
		SkillScriptExecutionEngine engine = new GraalJsSkillScriptExecutionEngine();

		assertThat(engine.supportedLanguages()).contains(ScriptLanguage.JAVASCRIPT);
	}

}
