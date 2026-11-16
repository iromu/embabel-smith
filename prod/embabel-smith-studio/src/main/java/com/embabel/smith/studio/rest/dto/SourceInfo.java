/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

/**
 * A spec source the studio offers: its URI and kind ({@code folder} or {@code git}).
 *
 * @param uri the source URI.
 * @param kind the source kind ({@code folder} or {@code git}).
 * @author Ivan Rodriguez
 */
public record SourceInfo(String uri, String kind) {
}
