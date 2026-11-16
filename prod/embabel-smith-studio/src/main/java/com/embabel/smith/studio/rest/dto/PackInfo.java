/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

/**
 * A pack in a source with its lifecycle state and drift status (the Lifecycle pane): the
 * {@code lifecycleState}/{@code baselinePassRate}/{@code updatedAt} are {@code null} when
 * the pack has no lifecycle sidecar; {@code drift} is true when the pack's content hash
 * has moved from the baseline recorded in the sidecar.
 *
 * @param name the pack name.
 * @param contentHash the pack's content hash.
 * @param lifecycleState the lifecycle state name (null without a sidecar).
 * @param baselinePassRate the baseline eval pass rate (null without a sidecar).
 * @param drift whether the content hash moved from the baseline.
 * @param updatedAt the last update time (null without a sidecar).
 * @author Ivan Rodriguez
 */
public record PackInfo(String name, String contentHash, String lifecycleState, Double baselinePassRate, boolean drift,
		String updatedAt) {
}
