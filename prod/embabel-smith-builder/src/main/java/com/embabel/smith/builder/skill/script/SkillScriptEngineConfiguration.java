/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill.script;

import com.embabel.agent.skills.script.SkillScriptExecutionEngine;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publishes the in-JVM script engine as a bean — behind a property that defaults it off.
 *
 * <p>
 * <b>Why off by default.</b> Embabel offers a skill's script as a tool only when an
 * engine that claims the script's language is installed
 * ({@code LoadedSkill.getScriptTools} filters on {@code supportedLanguages()}), and its
 * default engine claims nothing. Installing the engine therefore <em>adds</em> tools to a
 * build: a script becomes callable that was inert text a run ago. That is a change to
 * what a build can reach, and this repo's rule for that is that it is somebody's explicit
 * decision, not a side effect of a dependency landing on the classpath.
 * {@code smith.builder.skill-scripts.enabled=true} is the decision; the ordinary build's
 * tool list is what it was before this class existed.
 *
 * <p>
 * The engine is published under Embabel's own {@link SkillScriptExecutionEngine} type so
 * a consumer attaches it through the framework's seam —
 * {@code Skills.withScriptExecutionEngine
 * (engine)} — rather than against this module's class.
 *
 * @author Ivan Rodriguez
 */
@Configuration
public class SkillScriptEngineConfiguration {

	/** The switch that installs the engine. Unset means Embabel's default behaviour. */
	public static final String ENABLED_PROPERTY = "smith.builder.skill-scripts.enabled";

	/**
	 * The in-JVM JavaScript engine, present only when the switch is on.
	 * @return the GraalJS-backed engine.
	 */
	@Bean
	@ConditionalOnProperty(name = ENABLED_PROPERTY, havingValue = "true")
	public SkillScriptExecutionEngine skillScriptExecutionEngine() {
		return new GraalJsSkillScriptExecutionEngine();
	}

}
