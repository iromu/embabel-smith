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

package com.embabel.smith.studio.rest;

import com.embabel.smith.studio.discover.BusinessProcessDiscovery;
import com.embabel.smith.studio.rest.dto.DiscoverRequest;
import com.embabel.smith.studio.rest.dto.DiscoverResponse;
import com.embabel.smith.studio.store.ProcessStore;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The workspace's discovery/ingest step: load the business-process documents from a local
 * docs path or a repository URL, and return them for presentation. Offline for a local
 * path (no model call); a repository URL is cloned first. Each successful load is
 * recorded to the durable discovery history; the model-based extraction of a raw codebase
 * is a separate step ({@code /api/extract}).
 *
 * @author Ivan Rodriguez
 */
@RestController
public class DiscoverController {

	private final BusinessProcessDiscovery discovery;

	private final ProcessStore store;

	public DiscoverController(BusinessProcessDiscovery discovery, ProcessStore store) {
		this.discovery = discovery;
		this.store = store;
	}

	@PostMapping("/api/discover")
	public DiscoverResponse discover(@RequestBody DiscoverRequest request) {
		try {
			BusinessProcessDiscovery.DiscoverResult result = this.discovery.load(request.path(), request.url());
			recordDiscoverySafely(result);
			return DiscoverResponse.of(result.sourceType(), result.location(), result.docs());
		}
		catch (RuntimeException ex) {
			return DiscoverResponse.failed((ex.getMessage() == null) ? ex.toString() : ex.getMessage());
		}
	}

	/**
	 * Discovery history is best-effort: a recording failure must not fail the load.
	 * @param result the discovery result to record.
	 */
	private void recordDiscoverySafely(BusinessProcessDiscovery.DiscoverResult result) {
		try {
			this.store.recordDiscovery(result.sourceType(), result.location(), result.docs().size());
		}
		catch (RuntimeException ignored) {
			// ignore — the docs still load
		}
	}

}
