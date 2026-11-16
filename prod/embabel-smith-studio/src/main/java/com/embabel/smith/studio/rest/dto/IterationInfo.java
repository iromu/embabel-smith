/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
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
