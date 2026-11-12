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

package com.embabel.smith.ui.dto;

import java.util.List;

/**
 * The result of exporting a canvas: the composite's goal, steps, types, and written path.
 *
 * @param path the file path the composite spec was written to
 * @param goal the composite's exported goal
 * @param steps the ordered step names of the composite
 * @param types the types involved in the composite
 * @param issues the export issues, if any
 * @author Ivan Rodriguez
 */
public record ComposeResponse(String path, GoalInfo goal, List<String> steps, List<String> types, List<String> issues) {
}
