/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.unified.dto;

/**
 * A request to check a spec's tool groups against a trust tier.
 *
 * @param specName The spec to check.
 * @param tier The trust tier to check against.
 * @author Ivan Rodriguez
 */
public record TrustRequest(String specName, String tier) {
}
