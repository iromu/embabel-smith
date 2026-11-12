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

package com.embabel.smith.studio.rest.dto;

/**
 * The generate request: a blueprint either as a {@code blueprint} string or as a
 * {@code path} to a directory of business-process documents (the server reads the docs).
 * Exactly one should be set; the path wins when both are.
 *
 * @param blueprint the agent spec blueprint as a string.
 * @param path a directory of business-process documents the server reads.
 * @author Ivan Rodriguez
 */
public record GenerateRequest(String blueprint, String path) {
}
