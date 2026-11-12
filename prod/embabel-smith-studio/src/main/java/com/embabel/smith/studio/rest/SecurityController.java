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

import com.embabel.smith.builder.security.AgentSpecSecurityScanner;
import com.embabel.smith.builder.security.ScanReport;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.studio.rest.dto.ScanInfo;
import com.embabel.smith.studio.rest.dto.SecurityRequest;
import com.embabel.smith.studio.rest.dto.SecurityResponse;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Security pane (change 06): scan a agent spec from a source at install time with the
 * change-04 builder's static scanner. Offline — pure static analysis, no model calls.
 *
 * @author Ivan Rodriguez
 */
@RestController
public class SecurityController {

	private final SourceService sourceService;

	private final AgentSpecSecurityScanner scanner;

	public SecurityController(SourceService sourceService) {
		this.sourceService = sourceService;
		this.scanner = new AgentSpecSecurityScanner();
	}

	@PostMapping("/api/security")
	public SecurityResponse scan(@RequestBody SecurityRequest request) {
		if (request.source() == null || request.source().isBlank() || request.spec() == null
				|| request.spec().isBlank()) {
			return SecurityResponse.error("No source or spec selected; pick one to scan.");
		}
		try {
			AgentSpec pack = this.sourceService.readPack(request.source(), request.spec());
			ScanReport report = this.scanner.scan(pack);
			return SecurityResponse.ok(ScanInfo.of(report));
		}
		catch (Exception ex) {
			String message = (ex.getMessage() == null) ? ex.toString() : ex.getMessage();
			return SecurityResponse.error("Scan failed: " + message);
		}
	}

}
