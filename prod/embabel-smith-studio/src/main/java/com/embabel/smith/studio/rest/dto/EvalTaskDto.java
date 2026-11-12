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
 * One acceptance case for evaluating a spec (mirrors the builder's {@code EvalTask}).
 *
 * @param name the task name.
 * @param input the input for the task.
 * @param expected the expected output.
 * @author Ivan Rodriguez
 */
public record EvalTaskDto(String name, String input, String expected) {
}
