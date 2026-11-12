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

package com.embabel.smith.builder;

import java.nio.file.Path;

/**
 * The output of the {@code writeAgentSpec} step: a reference to the agent spec now on
 * disk. This is the builder's goal state — "a validated agent spec exists on disk".
 *
 * @param path the directory on disk holding the written agent spec
 * @param name the agent name from the written spec's manifest
 * @param version the version from the written spec's manifest
 * @author Ivan Rodriguez
 */
public record AgentSpecRef(Path path, String name, String version) {
}
