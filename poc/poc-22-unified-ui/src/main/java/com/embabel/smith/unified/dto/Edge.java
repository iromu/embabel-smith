/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.unified.dto;

/**
 * A directed canvas edge: {@code from}'s output feeds {@code to}'s input.
 *
 * @param from The node whose output feeds the edge.
 * @param to The node whose input receives the edge.
 * @author Ivan Rodriguez
 */
public record Edge(String from, String to) {
}
