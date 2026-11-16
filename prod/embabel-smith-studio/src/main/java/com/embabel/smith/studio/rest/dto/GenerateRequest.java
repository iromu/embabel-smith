/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

/**
 * The generate request: a blueprint either as a {@code blueprint} string or as a
 * {@code path} to a directory of business-process documents (the server reads the docs),
 * plus the generation {@code technique}. Exactly one of blueprint/path should be set; the
 * path wins when both are.
 *
 * @param blueprint the agent spec blueprint as a string.
 * @param path a directory of business-process documents the server reads.
 * @param technique the generation technique ({@code single-shot} or {@code embabel});
 * defaults to {@code single-shot} when absent.
 * @param model the LLM to generate with; blank uses the configured default.
 * @author Ivan Rodriguez
 */
public record GenerateRequest(String blueprint, String path, String technique, String model) {
}
