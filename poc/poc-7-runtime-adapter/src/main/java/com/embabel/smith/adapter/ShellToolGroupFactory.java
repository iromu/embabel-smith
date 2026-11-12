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

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import com.embabel.agent.core.ToolGroup;
import com.embabel.agent.core.ToolGroupDescription;
import com.embabel.agent.core.ToolGroupMetadata;
import com.embabel.agent.core.ToolGroupPermission;

/**
 * Builds the {@code shell} {@link ToolGroup} from Java.
 *
 * <p>
 * Embabel's {@code ToolGroupMetadata} is a Kotlin interface whose only factory is a
 * mangled companion {@code operator invoke} and whose
 * {@code AssetCoordinates.getVersion()} is name-mangled ({@code getVersion-Id9oKnY()}),
 * so it cannot be implemented or constructed directly from Java. This factory resolves
 * the mangled companion method by signature (stable across the 1.5.x line) and wraps the
 * result with {@link ToolGroup.Companion#ofTools}. Keeping the reflection in one place
 * means the rest of the POC uses the clean {@link ToolGroup} API.
 *
 * @author Ivan Rodriguez
 */
public final class ShellToolGroupFactory {

	private ShellToolGroupFactory() {
	}

	public static ToolGroup shellGroup() {
		ToolGroupDescription description = ToolGroupDescription
			.create("Executes shell commands on the host and returns their output", ShellToolGroup.ROLE);
		ToolGroupMetadata metadata = metadata(description);
		return ToolGroup.Companion.ofTools(metadata, List.of(ShellCommandTool.tool()));
	}

	private static ToolGroupMetadata metadata(ToolGroupDescription description) {
		Object companion = ToolGroupMetadata.Companion;
		for (Method method : companion.getClass().getMethods()) {
			// The (ToolGroupDescription, name, provider, permissions, version) overload.
			if (method.getParameterCount() == 5 && method.getParameterTypes()[0] == ToolGroupDescription.class
					&& method.getParameterTypes()[4] == String.class) {
				try {
					return (ToolGroupMetadata) method.invoke(companion, description, ShellToolGroup.ROLE,
							"embabel-smith", Set.of(ToolGroupPermission.HOST_ACCESS), "1.0.0");
				}
				catch (ReflectiveOperationException ex) {
					throw new IllegalStateException("Failed to build shell ToolGroupMetadata", ex);
				}
			}
		}
		throw new IllegalStateException(
				"No 5-arg ToolGroupMetadata(ToolGroupDescription, ...) factory found; Embabel version mismatch?");
	}

}
