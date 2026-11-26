/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.tools;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.core.ToolGroup;
import com.embabel.smith.builder.retrieval.LexicalScorer;
import com.embabel.smith.builder.retrieval.RetrievalCuration;
import com.embabel.smith.builder.retrieval.SpecEntries;
import com.embabel.smith.builder.retrieval.SpecEntry;
import com.embabel.smith.builder.retrieval.SpecRanker;
import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.specsource.FolderSpecSource;
import com.embabel.smith.specsource.ResolvedSpec;
import com.embabel.smith.specsource.ResolvedSpecSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The {@code library} tool group: the packs already in the library, listable and rankable
 * by a pack step, so an agent generating from a blueprint can see what already exists
 * rather than inventing a name and a shape.
 *
 * <p>
 * <b>Folder sources only.</b> The library is a directory the driver named, resolved
 * through {@link FolderSpecSource}. A {@code git:} source is deliberately not offered:
 * resolving one spawns {@code git} and writes a source cache, which is a subprocess and a
 * write — two of the four things design D3 keeps out of the tool surface. The builder's
 * own publish path can still target git; that is a stage, not an offer.
 *
 * <p>
 * <b>The ranking is the builder's, not a new one.</b> {@link SpecRanker} with
 * {@link LexicalScorer} and {@link RetrievalCuration} are the same classes the studio's
 * retrieval endpoint drives, so a step and the studio rank a library identically.
 * Lifecycle states are not read here — the builder has no sidecar store — which is the
 * same sidecar-less answer the studio gives a pack with no recorded contract: surfaced at
 * full score.
 *
 * @author Ivan Rodriguez
 */
@Configuration
public class PackLibraryToolGroup {

	/** The tool-group role a pack step declares to see the library. */
	public static final String ROLE = "library";

	/** Lists the packs in the library the driver opened. */
	public static final String LIST_PACKS = "list_packs";

	/** Ranks the library against a task description. */
	public static final String RANK_PACKS = "rank_packs";

	/** The task argument of {@link #RANK_PACKS}. */
	public static final String QUERY = "query";

	/** How many ranked hits the tool reports. */
	static final int TOP_K = 10;

	/**
	 * The group the platform registers under {@link #ROLE}.
	 * @param surface what the driver opened for this build
	 * @return the {@code library} tool group
	 */
	@Bean
	public ToolGroup libraryTools(ToolSurface surface) {
		return ToolGroups.ofTools(ROLE, "See the agent specs already in this build's library: list them, or rank "
				+ "them against a task description. The library is the directory the operator named, never a path "
				+ "you choose.", List.of(listPacks(surface), rankPacks(surface)));
	}

	private static Tool listPacks(ToolSurface surface) {
		return Tool.Companion.of(LIST_PACKS,
				"List the agent specs in the library this build was given, with the hash and validation status "
						+ "recorded for each. No arguments.",
				Tool.InputSchema.of(), Tool.Metadata.DEFAULT, (Tool.Function) (arguments) -> Tool.Result
					.text(ToolOutput.cap(surface.remembered(LIST_PACKS, arguments, () -> listing(surface)))));
	}

	private static Tool rankPacks(ToolSurface surface) {
		return Tool.Companion.of(RANK_PACKS,
				"Rank the library against a task description and return the top hits with their scores, using the "
						+ "builder's own lexical scorer. Pass the task in your own words.",
				Tool.InputSchema
					.of(Tool.Parameter.string(QUERY, "What you are trying to build or reuse, in a sentence", true)),
				Tool.Metadata.DEFAULT,
				(Tool.Function) (arguments) -> Tool.Result.text(ToolOutput.cap(surface.remembered(RANK_PACKS, arguments,
						() -> ranking(surface, ToolArguments.string(arguments, QUERY))))));
	}

	/**
	 * The library as one line per pack.
	 * @param surface the driver's opened library
	 * @return the listing, or the reason there is none
	 */
	static String listing(ToolSurface surface) {
		ResolvedSpecSource resolved = resolve(surface);
		if (resolved == null) {
			return "This build was given no pack library, so there is nothing to list.";
		}
		List<String> lines = new ArrayList<>(resolved.warnings());
		lines.add(resolved.packs().size() + " agent specs in " + resolved.origin().uri() + ":");
		resolved.packs()
			.stream()
			.sorted(Comparator.comparing(ResolvedSpec::name))
			.forEach((pack) -> lines
				.add("  " + pack.name() + " (" + pack.contentHash() + ", validated=" + pack.validationOk() + ")"));
		return String.join("\n", lines);
	}

	/**
	 * The library ranked against a task. The scorer and the curation rules are the
	 * builder's own, so the order a step sees is the order the studio's retrieval gives.
	 * @param surface the driver's opened library
	 * @param query the task as the model phrased it
	 * @return the ranked hits, or the reason there are none
	 */
	static String ranking(ToolSurface surface, String query) {
		if (query.isBlank()) {
			return RANK_PACKS + " needs the '" + QUERY + "' argument: what you are trying to build or reuse.";
		}
		ResolvedSpecSource resolved = resolve(surface);
		if (resolved == null) {
			return "This build was given no pack library, so nothing can be ranked.";
		}
		List<SpecEntry> entries = entries(resolved);
		if (entries.isEmpty()) {
			return "The library at " + resolved.origin().uri() + " holds no readable agent spec to rank.";
		}
		SpecRanker ranker = new SpecRanker(new LexicalScorer());
		List<SpecRanker.ScoredEntry> curated = new RetrievalCuration().curate(ranker.rank(query, entries, TOP_K),
				(entry) -> null, TOP_K);
		if (curated.isEmpty()) {
			return "Nothing in the library matched '" + query + "'.";
		}
		List<String> lines = new ArrayList<>();
		lines.add(curated.size() + " library packs ranked for that task:");
		curated.forEach((scored) -> lines
			.add("  " + scored.entry().name() + " — " + scored.score() + " — " + scored.entry().description()));
		return String.join("\n", lines);
	}

	private static ResolvedSpecSource resolve(ToolSurface surface) {
		Path root = surface.libraryRoot();
		return (root != null) ? new FolderSpecSource("folder:" + root, root).resolve() : null;
	}

	private static List<SpecEntry> entries(ResolvedSpecSource resolved) {
		AgentSpecReader reader = new AgentSpecReader();
		List<SpecEntry> entries = new ArrayList<>();
		for (ResolvedSpec pack : resolved.packs()) {
			try {
				AgentSpec read = reader.read(pack.root());
				entries.add(SpecEntries.of(read));
			}
			catch (Exception ex) {
				// A pack that will not read is a pack retrieval cannot offer. It is left
				// out
				// rather than failing the call, because the source already reported the
				// packs
				// it validated and a step reading a listing needs the rest of them.
			}
		}
		return entries;
	}

}
