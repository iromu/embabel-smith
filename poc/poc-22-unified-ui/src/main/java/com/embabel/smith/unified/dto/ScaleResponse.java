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
 * The at-scale retrieval measurement (POC 12): precision@1 under the active scorer for
 * the core library and for the full library (core + near-duplicates — the shadowing
 * pressure), plus a sampled growth curve.
 *
 * @param scorer The active scorer name.
 * @param coreSize The core library size.
 * @param coreP1 The precision@1 for the core library.
 * @param fullSize The full library size.
 * @param fullP1 The precision@1 for the full library.
 * @param curve The sampled growth curve.
 * @author Ivan Rodriguez
 */
public record ScaleResponse(String scorer, int coreSize, double coreP1, int fullSize, double fullP1,
		List<ScalePoint> curve) {
}
