/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

import java.util.List;

/**
 * The LLMs a model-launching pane may choose from, and the one the platform falls back to
 * when a request names none.
 *
 * @param models the LLM names the platform can run, sorted; empty when no provider is
 * configured
 * @param defaultModel the model a request without a {@code model} gets, or {@code null}
 * when the deployment names no usable default
 * @author Ivan Rodriguez
 */
public record ModelCatalog(List<String> models, String defaultModel) {
}
