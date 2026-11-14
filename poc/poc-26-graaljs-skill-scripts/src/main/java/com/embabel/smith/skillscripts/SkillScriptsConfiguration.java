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

import com.embabel.agent.skills.Skills;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the in-JVM engine into the skill pack — the two lines the POC is about. Everything
 * else is Embabel's own {@link Skills}.
 *
 * @author Ivan Rodriguez
 */
@Configuration
public class SkillScriptsConfiguration {

	/**
	 * Install the in-JVM JavaScript engine.
	 * @return the GraalJS-backed engine, so {@code .js} skill scripts run inside the agent's JVM.
	 */
	@Bean
	public GraalJsSkillScriptExecutionEngine skillScriptExecutionEngine() {
		return new GraalJsSkillScriptExecutionEngine();
	}

	/**
	 * Expose the skill pack and its scripts.
	 * @param engine the installed script engine
	 * @return the helper that loads the skill pack and reaches its scripts
	 */
	@Bean
	public SkillScriptRuntime skillScriptRuntime(SkillScriptExecutionEngine engine) {
		return new SkillScriptRuntime(engine);
	}

	/**
	 * Load the skill pack with the engine attached.
	 * @param runtime the runtime holding the engine
	 * @param skillsDir directory of the skill pack to install
	 * @return the skill pack with its scripts turned into agent tools
	 */
	@Bean
	public Skills skills(SkillScriptRuntime runtime,
			@Value("${smith.skill-scripts.dir:src/main/resources/skills}") String skillsDir) {
		return runtime.skillsFrom(Path.of(skillsDir));
	}

	/**
	 * Register the demo agent that calls the script through the model.
	 * @param skills the skill pack to expose to the model
	 * @return the endpoint-gated demo agent
	 */
	@Bean
	public SkillScriptAgent skillScriptAgent(Skills skills) {
		return new SkillScriptAgent(skills);
	}

}
