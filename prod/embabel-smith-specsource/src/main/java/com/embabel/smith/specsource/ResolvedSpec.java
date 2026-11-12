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

package com.embabel.smith.specsource;

import java.nio.file.Path;

/**
 * A single agent spec resolved from a source.
 *
 * @param root the directory holding the pack's format files
 * @param name the pack name declared in its {@code manifest.yml}
 * @param contentHash SHA-256 over the pack's format-owned files; stable for identical
 * content
 * @param validationOk whether the pack passed agent spec structure validation
 * @author Ivan Rodriguez
 */
public record ResolvedSpec(Path root, String name, String contentHash, boolean validationOk) {
}
