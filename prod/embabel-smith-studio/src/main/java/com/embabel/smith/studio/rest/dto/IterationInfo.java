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

import java.util.List;

/**
 * The outcome of one loop iteration (the Auto-improve pane's on-demand trigger): how many
 * candidates were gated, and which specs were published (auto-publish) or queued for
 * approval. {@code ran} is false (with a structured {@code errorCode}) when the iteration
 * could not proceed, e.g. no model endpoint is configured.
 *
 * @param ran whether the iteration ran.
 * @param candidates how many candidates were gated.
 * @param publishedSpecs the specs published (auto-publish).
 * @param queuedSpecs the specs queued for approval.
 * @param autoPublish whether auto-publish mode is on.
 * @param errorCode the structured error code when the iteration could not proceed.
 * @param error the error message (null when ran).
 * @author Ivan Rodriguez
 */
public record IterationInfo(boolean ran, int candidates, List<String> publishedSpecs, List<String> queuedSpecs,
		boolean autoPublish, String errorCode, String error) {
}
