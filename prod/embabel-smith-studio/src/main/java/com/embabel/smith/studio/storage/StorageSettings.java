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

package com.embabel.smith.studio.storage;

/**
 * The studio's storage settings (change: smith-studio-postgres-storage): the packs
 * database and the trace backend. Persisted as a local JSON document (it cannot live in
 * the database it configures) and editable at runtime from the Storage pane.
 *
 * <p>
 * {@code packDb} is the database holding saved packs and the discovery history.
 * {@code trace} selects the trace backend: the JSONL file (default, {@code url} unused),
 * the packs' database ({@code postgres-same}, {@code url} unused), or an independent
 * database ({@code
 * postgres-separate}, with its own {@code url}/{@code user}/{@code password}).
 *
 * @param packDb the packs database endpoint.
 * @param trace the trace backend selection.
 * @author Ivan Rodriguez
 */
public record StorageSettings(DbSettings packDb, TraceSettings trace) {

	/** The trace backend type. */
	public static final String TRACE_JSONL = "jsonl";

	/** The trace backend type for the packs' database. */
	public static final String TRACE_POSTGRES_SAME = "postgres-same";

	/** The trace backend type for a separate database. */
	public static final String TRACE_POSTGRES_SEPARATE = "postgres-separate";

	/** The default: no packs database, JSONL traces. */
	public static StorageSettings defaults() {
		return new StorageSettings(new DbSettings(null, null, null), new TraceSettings(TRACE_JSONL, null, null, null));
	}

	/**
	 * A database endpoint with credentials.
	 *
	 * @param url the JDBC URL.
	 * @param user the database user.
	 * @param password the database password.
	 */
	public record DbSettings(String url, String user, String password) {

		/** True when the endpoint is fully specified enough to connect. */
		public boolean isConfigured() {
			return this.url != null && !this.url.isBlank();
		}
	}

	/**
	 * The trace backend selection plus the optional separate-database endpoint.
	 *
	 * @param type the backend type, or null/blank for the JSONL default.
	 * @param url the separate-database JDBC URL.
	 * @param user the separate-database user.
	 * @param password the separate-database password.
	 */
	public record TraceSettings(String type, String url, String user, String password) {

		/** The backend type, defaulting to the JSONL file store. */
		public String typeOrDefault() {
			return (this.type == null || this.type.isBlank()) ? TRACE_JSONL : this.type;
		}
	}
}
