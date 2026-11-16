/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

/**
 * The save-storage-settings request (change: smith-studio-postgres-storage): the Storage
 * pane's flat form. {@code url}/{@code user}/{@code password} are the packs database;
 * {@code traceType} is jsonl / postgres-same / postgres-separate; the {@code trace*}
 * fields are only used when {@code traceType} is postgres-separate (an independent trace
 * database).
 *
 * @param url the packs database URL.
 * @param user the packs database user.
 * @param password the packs database password.
 * @param traceType the trace storage type (jsonl / postgres-same / postgres-separate).
 * @param traceUrl the separate trace database URL.
 * @param traceUser the separate trace database user.
 * @param tracePassword the separate trace database password.
 * @author Ivan Rodriguez
 */
public record StorageSettingsRequest(String url, String user, String password, String traceType, String traceUrl,
		String traceUser, String tracePassword) {
}
