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

package com.embabel.smith.unified.dto;

import java.util.List;

/**
 * One action in a compiled draft: its name, the types it consumes, and the type it
 * produces.
 *
 * @param name The action name.
 * @param inputTypes The types the action consumes.
 * @param outputType The type the action produces.
 * @author Ivan Rodriguez
 */
public record ActionInfo(String name, List<String> inputTypes, String outputType) {
}
