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
 * The validation result for a canvas: whether it is a valid chain, the issues, and the
 * chain.
 *
 * @param valid Whether the canvas is a valid chain.
 * @param issues The validation issues.
 * @param chain The ordered chain of specs.
 * @author Ivan Rodriguez
 */
public record CanvasResponse(boolean valid, List<String> issues, List<String> chain) {
}
