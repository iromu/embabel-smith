/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.ui.dto;

/**
 * A directed canvas edge: {@code from}'s output feeds {@code to}'s input.
 *
 * @param from the source spec name
 * @param to the target spec name
 * @author Ivan Rodriguez
 */
public record Edge(String from, String to) {
}
