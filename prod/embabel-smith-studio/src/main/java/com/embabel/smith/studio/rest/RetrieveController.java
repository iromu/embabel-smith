/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.embabel.smith.builder.lifecycle.LifecycleState;
import com.embabel.smith.builder.retrieval.LexicalScorer;
import com.embabel.smith.builder.retrieval.RetrievalCuration;
import com.embabel.smith.builder.retrieval.SpecEntries;
import com.embabel.smith.builder.retrieval.SpecEntry;
import com.embabel.smith.builder.retrieval.SpecRanker;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.studio.rest.dto.PackInfo;
import com.embabel.smith.studio.rest.dto.RetrieveRequest;
import com.embabel.smith.studio.rest.dto.RetrieveResponse;
import com.embabel.smith.studio.rest.dto.RetrieveResult;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Retrieve pane: rank a source's packs against a natural-language task and return the
 * curated top-k. The whole of this class is reading the source and shaping the DTO — the
 * scoring, the ranking, the curation and the top-k all live in the builder's
 * {@code retrieval/} package, because the studio holds no capability of its own.
 *
 * <p>
 * Offline: the ranking is computed from the pack metadata already on disk and makes no
 * model call, so the pane works with no endpoint configured. The response names the
 * active scorer; swapping it is a builder change, not a change to this endpoint or its
 * shape.
 *
 * @author Ivan Rodriguez
 */
@RestController
public class RetrieveController {

	/**
	 * The one scorer that ships. Naming it here keeps the seam a one-method interface.
	 */
	private static final String SCORER_NAME = "lexical";

	/** What a pack with no lifecycle sidecar reports its state as. */
	private static final String NO_STATE = "UNKNOWN";

	private final SourceService sourceService;

	private final SpecRanker ranker;

	private final RetrievalCuration curation;

	public RetrieveController(SourceService sourceService) {
		this.sourceService = sourceService;
		this.ranker = new SpecRanker(new LexicalScorer());
		this.curation = new RetrievalCuration();
	}

	/**
	 * Rank the source's packs against the query, curate the ranking with each pack's
	 * recorded lifecycle state, and return the top-k.
	 * @param request the source, the task, and how many hits to return.
	 * @return the ranked hits with the active scorer, or the structured error when the
	 * source cannot be resolved.
	 */
	@PostMapping("/api/retrieve")
	public RetrieveResponse retrieve(@RequestBody RetrieveRequest request) {
		if (request.source() == null || request.source().isBlank()) {
			return RetrieveResponse.error("No source selected; pick one to search.");
		}
		try {
			List<SpecEntry> entries = new ArrayList<>();
			Map<String, LifecycleState> states = new LinkedHashMap<>();
			for (PackInfo pack : this.sourceService.listPacks(request.source())) {
				AgentSpec read = readOrNull(request.source(), pack.name());
				if (read != null) {
					entries.add(SpecEntries.of(read));
					states.put(pack.name(), parseState(pack.lifecycleState()));
				}
			}
			List<RetrieveResult> results = this.curation
				.curate(this.ranker.rank(request.query(), entries, entries.size()), (entry) -> states.get(entry.name()),
						request.effectiveK())
				.stream()
				.map((scored) -> toResult(scored, states))
				.toList();
			return RetrieveResponse.ok(SCORER_NAME, results);
		}
		catch (Exception ex) {
			String message = (ex.getMessage() == null) ? ex.toString() : ex.getMessage();
			return RetrieveResponse.error("Retrieve failed: " + message);
		}
	}

	/**
	 * The pack, or {@code null} when it cannot be read. An unreadable pack contributes no
	 * entry rather than failing the query — a source with nothing readable in it is an
	 * empty result, not a fault.
	 * @param source the source URI to read from.
	 * @param packName the pack to read.
	 * @return the pack, or {@code null} when it cannot be read.
	 */
	private AgentSpec readOrNull(String source, String packName) {
		try {
			return this.sourceService.readPack(source, packName);
		}
		catch (RuntimeException ex) {
			return null;
		}
	}

	private static RetrieveResult toResult(SpecRanker.ScoredEntry scored, Map<String, LifecycleState> states) {
		SpecEntry entry = scored.entry();
		LifecycleState state = states.get(entry.name());
		return new RetrieveResult(entry.name(), entry.description(), scored.score(), entry.outputType(),
				(state != null) ? state.name() : NO_STATE);
	}

	/**
	 * The sidecar's state name as the enum the curation rules read; {@code null} for a
	 * pack with no sidecar.
	 * @param stateName the lifecycle state name the source reported.
	 * @return the lifecycle state, or {@code null} when there is none.
	 */
	private static LifecycleState parseState(String stateName) {
		if (stateName == null || stateName.isBlank()) {
			return null;
		}
		try {
			return LifecycleState.valueOf(stateName);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

}
