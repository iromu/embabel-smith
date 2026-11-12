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

package com.embabel.smith.adapter;

import com.embabel.agent.core.ToolGroup;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the {@code shell} tool group as a platform {@link ToolGroup} bean, so the
 * Embabel platform registers it under the role {@code shell}. A agent spec action that
 * declares {@code toolGroups: [shell]} then gets the real {@link ShellCommandTool}
 * injected by the platform — the "Spec Runtime Adapter" bridge from doc 03 §2.3 (Option
 * A, the shell tool group).
 *
 * @author Ivan Rodriguez
 */
@Configuration
public class ShellToolGroup {

	/** The tool-group role the agent spec actions reference. */
	public static final String ROLE = "shell";

	@Bean
	public ToolGroup shellToolGroupBean() {
		return ShellToolGroupFactory.shellGroup();
	}

}
