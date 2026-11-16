/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

/**
 * A trust-tier check request (the Trust pane): the source, spec, and tier. Offline.
 *
 * @param source the spec source to check.
 * @param spec the spec name to check.
 * @param tier the trust tier to check against.
 * @author Ivan Rodriguez
 */
public record TrustRequest(String source, String spec, String tier) {
}
