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

import com.embabel.smith.runtime.trace.JsonlTraceStore;
import com.embabel.smith.runtime.trace.TraceRecord;
import com.embabel.smith.runtime.trace.TraceStore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/**
 * Owns the studio's storage bindings and the trace backend (change:
 * smith-studio-postgres-storage). The main binding (packs + discovery history) and an
 * optional separate trace binding are built programmatically and hot-swapped on rebind;
 * the previous binding is destroyed. Nothing JPA-related is a Spring-managed bean, so a
 * rebind never orphans live beans.
 *
 * <p>
 * Startup resolution: the persisted settings document wins, then the {@code
 * smith.studio.storage.url} property (test profile / env), then unbound. The app boots in
 * all three cases; when unbound, store operations report "storage not configured". A
 * configured but unreachable database at startup leaves the context unbound rather than
 * failing the boot.
 *
 * @author Ivan Rodriguez
 */
@Component
public class StorageContext {

	private static final Logger log = LoggerFactory.getLogger(StorageContext.class);

	private final StorageSettingsService settingsService;

	private final JsonlTraceStore jsonlTraceStore;

	private final String propertyUrl;

	private final String propertyUser;

	private final String propertyPassword;

	private final TraceStore traceStore = new DelegatingTraceStore();

	private volatile StorageBinding main;

	private volatile StorageBinding traceSeparate;

	private volatile TraceStore activeTrace;

	public StorageContext(StorageSettingsService settingsService, JsonlTraceStore jsonlTraceStore, String propertyUrl,
			String propertyUser, String propertyPassword) {
		this.settingsService = settingsService;
		this.jsonlTraceStore = jsonlTraceStore;
		this.propertyUrl = propertyUrl;
		this.propertyUser = propertyUser;
		this.propertyPassword = propertyPassword;
		this.activeTrace = jsonlTraceStore;
	}

	@PostConstruct
	void startup() {
		StorageSettings settings = this.settingsService.load();
		if (settings == null) {
			settings = fromProperty();
		}
		if (settings != null && settings.packDb().isConfigured()) {
			try {
				bind(settings);
			}
			catch (RuntimeException ex) {
				log.warn("Storage database not reachable at startup; starting unbound: {}", ex.getMessage());
			}
		}
	}

	private StorageSettings fromProperty() {
		if (this.propertyUrl == null || this.propertyUrl.isBlank()) {
			return null;
		}
		return new StorageSettings(new StorageSettings.DbSettings(this.propertyUrl, this.propertyUser,
				this.propertyPassword),
				new StorageSettings.TraceSettings(StorageSettings.TRACE_JSONL, null, null, null));
	}

	/**
	 * The current packs/discovery binding, or {@code null} when unbound.
	 * @return the current binding, or {@code null} when unbound.
	 */
	public StorageBinding main() {
		return this.main;
	}

	/**
	 * True when a packs database is bound.
	 * @return true when a packs database is bound.
	 */
	public boolean isBound() {
		return this.main != null;
	}

	/**
	 * The packs/discovery binding, or a clear error when unbound.
	 * @return the packs/discovery binding.
	 */
	public StorageBinding requireMain() {
		StorageBinding b = this.main;
		if (b == null) {
			throw new StorageNotConfiguredException();
		}
		return b;
	}

	/**
	 * The stable trace store; delegates to the current backend, which may change at
	 * runtime.
	 * @return the stable trace store.
	 */
	public TraceStore traceStore() {
		return this.traceStore;
	}

	/**
	 * Rebinds storage to the settings: verifies the packs endpoint, atomically swaps the
	 * main binding (destroying the old one), and applies the trace backend. If the packs
	 * endpoint is unreachable the exception propagates and the previous binding stays in
	 * effect. A separate trace database that is unreachable keeps the previous trace
	 * backend rather than failing the main rebind.
	 * @param settings the settings to rebind storage to.
	 */
	public synchronized void bind(StorageSettings settings) {
		StorageBinding newMain = StorageBinding.create(settings.packDb());
		StorageBinding oldMain = this.main;
		this.main = newMain;
		if (oldMain != null) {
			oldMain.close();
		}
		applyTraceBackend(settings.trace());
	}

	private synchronized void applyTraceBackend(StorageSettings.TraceSettings trace) {
		String type = trace.typeOrDefault();
		switch (type) {
			case StorageSettings.TRACE_POSTGRES_SAME -> {
				this.activeTrace = (this.main != null) ? new JpaTraceStore(this::main) : this.jsonlTraceStore;
				closeSeparateIfUnused();
			}
			case StorageSettings.TRACE_POSTGRES_SEPARATE -> {
				try {
					StorageBinding newSeparate = StorageBinding
						.create(new StorageSettings.DbSettings(trace.url(), trace.user(), trace.password()));
					StorageBinding oldSeparate = this.traceSeparate;
					this.traceSeparate = newSeparate;
					if (oldSeparate != null) {
						oldSeparate.close();
					}
					this.activeTrace = new JpaTraceStore(() -> this.traceSeparate);
				}
				catch (RuntimeException ex) {
					log.warn("Separate trace database not reachable; keeping the current trace backend: {}",
							ex.getMessage());
				}
			}
			default -> {
				this.activeTrace = this.jsonlTraceStore;
				closeSeparateIfUnused();
			}
		}
	}

	private void closeSeparateIfUnused() {
		StorageBinding separate = this.traceSeparate;
		if (separate != null) {
			separate.close();
			this.traceSeparate = null;
		}
	}

	@PreDestroy
	public void shutdown() {
		StorageBinding m = this.main;
		if (m != null) {
			m.close();
		}
		StorageBinding t = this.traceSeparate;
		if (t != null) {
			t.close();
		}
	}

	/** Forwards to the current trace backend, which can be swapped at runtime. */
	private final class DelegatingTraceStore implements TraceStore {

		@Override
		public void append(TraceRecord record) {
			StorageContext.this.activeTrace.append(record);
		}

		@Override
		public ReadResult read() {
			return StorageContext.this.activeTrace.read();
		}

	}

}
