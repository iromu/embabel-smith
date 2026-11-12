/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
