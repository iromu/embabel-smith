/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

/**
 * A security-scan request (the Security pane): the source and spec to scan. Offline.
 *
 * @param source the spec source to scan.
 * @param spec the spec name to scan.
 * @author Ivan Rodriguez
 */
public record SecurityRequest(String source, String spec) {
}
