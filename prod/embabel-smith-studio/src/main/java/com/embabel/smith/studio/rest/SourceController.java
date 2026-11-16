/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest;

import java.util.List;

import com.embabel.smith.studio.rest.dto.PackInfo;
import com.embabel.smith.studio.rest.dto.SourceInfo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The sources surface (change 06): lists the studio's configured sources, and the packs
 * of a source with their lifecycle state and content-hash drift status (the Lifecycle
 * pane). Offline — reads from disk, no model calls.
 *
 * @author Ivan Rodriguez
 */
@RestController
public class SourceController {

	private final SourceService sourceService;

	public SourceController(SourceService sourceService) {
		this.sourceService = sourceService;
	}

	/**
	 * The configured sources the studio offers.
	 * @return the configured sources, each with its kind.
	 */
	@GetMapping("/api/sources")
	public List<SourceInfo> sources() {
		return this.sourceService.listSources();
	}

	/**
	 * The packs of a source, each with its lifecycle state, baseline, and drift status.
	 * @param source the source URI to list packs from.
	 * @return the packs of the source.
	 */
	@GetMapping("/api/packs")
	public List<PackInfo> packs(@RequestParam("source") String source) {
		return this.sourceService.listPacks(source);
	}

	/**
	 * The goal names a source's packs export — the Run pane runs a goal, not a pack name.
	 * @param source the source URI to list goals from.
	 * @return the exported goal names of the source's packs.
	 */
	@GetMapping("/api/goals")
	public List<String> goals(@RequestParam("source") String source) {
		return this.sourceService.listGoals(source);
	}

}
