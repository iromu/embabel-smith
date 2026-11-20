/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.store;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.studio.storage.StorageBinding;
import com.embabel.smith.studio.storage.StorageContext;
import com.embabel.smith.studio.storage.entity.DiscoveryEntity;
import com.embabel.smith.studio.storage.entity.PackEntity;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The durable store for generated agent specs and discovery history (change:
 * smith-studio-postgres-storage). Every operation delegates to the current
 * {@link StorageBinding}; the public API is unchanged from the former file-based store. A
 * saved pack is one row keyed by {@code (name, version)} whose {@code content} is the
 * {@link AgentSpec} serialized as JSON, so each version of a pack is kept (change:
 * publish-keeps-versions). A bare name means the pack's highest version — decision 3 of
 * that change — and the comparison implementing that rule is here, in
 * {@code compareVersions(String, String)}, not in a query. When no database is bound,
 * mutating operations and loads report "storage not configured" while the listing
 * operations return an empty list, so the studio boots and stays usable with no storage.
 *
 * @author Ivan Rodriguez
 */
public class ProcessStore {

	private final StorageContext storage;

	private final ObjectMapper json = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
		.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	public ProcessStore(StorageContext storage) {
		this.storage = storage;
	}

	// ---- Packs --------------------------------------------------------------

	/**
	 * Saves the pack at the version its artifact declares, as the row for that
	 * {@code (name, version)}: republishing the same version updates it, a new version
	 * adds a row. A pack declaring no version is stored at
	 * {@link PackEntity#DEFAULT_VERSION}.
	 * @param pack the agent spec to save.
	 * @return the saved pack's listing record.
	 */
	public PackRecord savePack(AgentSpec pack) {
		String name = requireName(pack);
		StorageBinding b = this.storage.requireMain();
		return b.tx().execute((status) -> {
			PackEntity e = new PackEntity();
			e.setName(name);
			e.setVersion(storedVersion(pack));
			e.setGoal((pack.goal() == null) ? null : pack.goal().name());
			e.setSteps(pack.steps().size());
			e.setSavedAt(Instant.now());
			e.setContent(toJson(pack));
			b.packs().save(e);
			return new PackRecord(name, e.getGoal(), e.getSteps(), e.getSavedAt().toString());
		});
	}

	/**
	 * The saved packs, most recently saved first. A name held at several versions is
	 * listed once, by its highest version, so the listing agrees with what
	 * {@link #loadPack(String)} returns for that name (decision 3 of
	 * publish-keeps-versions).
	 * @return the saved packs.
	 */
	public List<PackRecord> listPacks() {
		StorageBinding b = this.storage.main();
		if (b == null) {
			return List.of();
		}
		List<PackEntity> entities = b.tx().execute((status) -> b.packs().findAll());
		return highestPerName(entities).stream()
			.map((e) -> new PackRecord(e.getName(), e.getGoal(), e.getSteps(),
					(e.getSavedAt() == null) ? "" : e.getSavedAt().toString()))
			.sorted(Comparator.comparing(ProcessStore.PackRecord::savedAt).reversed())
			.toList();
	}

	/**
	 * Loads a saved pack by name, resolving the name to its highest version, or
	 * {@code null} when no such pack is stored.
	 * @param name the pack name to load.
	 * @return the stored agent spec, or {@code null} when no such pack is stored.
	 */
	public AgentSpec loadPack(String name) {
		StorageBinding b = this.storage.requireMain();
		// No derived query: the ANTLR parser a finder would need is shadowed on this
		// classpath (see PackRepository), so the rows are fetched and narrowed here.
		List<PackEntity> rows = b.tx()
			.execute((status) -> b.packs().findAll().stream().filter((row) -> name.equals(row.getName())).toList());
		if (rows.isEmpty()) {
			return null;
		}
		return fromJson(highestPerName(rows).get(0).getContent());
	}

	// ---- Discovery history --------------------------------------------------

	/**
	 * Records a discovery in the database.
	 * @param sourceType the discovery source type.
	 * @param location the discovery location.
	 * @param docCount the number of discovered documents.
	 */
	public void recordDiscovery(String sourceType, String location, int docCount) {
		StorageBinding b = this.storage.requireMain();
		b.tx().executeWithoutResult((status) -> {
			DiscoveryEntity e = new DiscoveryEntity();
			e.setTs(Instant.now());
			e.setSourceType(sourceType);
			e.setLocation(location);
			e.setDocCount(docCount);
			b.discoveries().save(e);
		});
	}

	/**
	 * The recorded discoveries, most recent first.
	 * @return the recorded discoveries.
	 */
	public List<DiscoveryRecord> discoveries() {
		StorageBinding b = this.storage.main();
		if (b == null) {
			return List.of();
		}
		List<DiscoveryEntity> entities = b.tx().execute((status) -> b.discoveries().findAll());
		return entities.stream()
			.sorted(Comparator.comparing(DiscoveryEntity::getId).reversed())
			.map((e) -> new DiscoveryRecord((e.getTs() == null) ? "" : e.getTs().toString(), e.getSourceType(),
					e.getLocation(), e.getDocCount()))
			.toList();
	}

	private static String requireName(AgentSpec pack) {
		String name = pack.name();
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("The pack has no name to save");
		}
		return sanitize(name);
	}

	/**
	 * A pack name must be a single safe key.
	 * @param name the raw pack name.
	 * @return the sanitized pack name.
	 */
	private static String sanitize(String name) {
		return name.replaceAll("[^A-Za-z0-9._-]", "_");
	}

	/**
	 * The version a pack is stored at: the one its artifact declares, or the default when
	 * it declares none.
	 * @param pack the pack being stored.
	 * @return the version to key the row on.
	 */
	private static String storedVersion(AgentSpec pack) {
		String version = pack.version();
		return (version == null || version.isBlank()) ? PackEntity.DEFAULT_VERSION : version;
	}

	/**
	 * Narrows stored rows to one per pack name, keeping the highest version of each — the
	 * rule behind decision 3 of publish-keeps-versions. Both name-keyed readers go
	 * through here so the rule is not reinvented per reader.
	 * @param rows the rows to narrow.
	 * @return the highest-version row of each name, in first-seen order.
	 */
	private static List<PackEntity> highestPerName(List<PackEntity> rows) {
		Map<String, PackEntity> highest = new LinkedHashMap<>();
		for (PackEntity row : rows) {
			highest.merge(row.getName(), row, ProcessStore::keepHighest);
		}
		return new ArrayList<>(highest.values());
	}

	private static PackEntity keepHighest(PackEntity kept, PackEntity candidate) {
		return (compareVersions(kept.getVersion(), candidate.getVersion()) >= 0) ? kept : candidate;
	}

	/**
	 * Compares two stored versions segment by segment, numerically where a segment parses
	 * as an integer. This is the one place the "a name means its highest version" rule
	 * lives, and it has to be Java rather than an {@code ORDER BY version DESC}:
	 * lexicographic order puts {@code 1.9} above {@code 1.10}, and a derived ordering
	 * query is unavailable on this classpath (see
	 * {@link com.embabel.smith.studio.storage.repository.PackRepository}).
	 * @param one the version to place.
	 * @param other the version to place it against.
	 * @return a negative, zero or positive value as {@code one} is lower, equal or higher
	 * than {@code other}.
	 */
	private static int compareVersions(String one, String other) {
		String[] left = versionSegments(one);
		String[] right = versionSegments(other);
		int segments = Math.max(left.length, right.length);
		for (int index = 0; index < segments; index++) {
			String leftSegment = (index < left.length) ? left[index] : null;
			String rightSegment = (index < right.length) ? right[index] : null;
			int comparison = compareVersionSegments(leftSegment, rightSegment);
			if (comparison != 0) {
				return comparison;
			}
		}
		return 0;
	}

	private static String[] versionSegments(String version) {
		return (version == null || version.isBlank()) ? new String[0] : version.trim().split("\\.");
	}

	private static int compareVersionSegments(String one, String other) {
		if (one == null) {
			return (other != null) ? -1 : 0;
		}
		if (other == null) {
			return 1;
		}
		Long oneNumber = asNumber(one);
		Long otherNumber = asNumber(other);
		if ((oneNumber != null) && (otherNumber != null)) {
			return oneNumber.compareTo(otherNumber);
		}
		return one.compareTo(other);
	}

	private static Long asNumber(String segment) {
		try {
			return Long.valueOf(segment);
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	private String toJson(AgentSpec pack) {
		try {
			return this.json.writeValueAsString(pack);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to serialize pack: " + ex.getMessage(), ex);
		}
	}

	private AgentSpec fromJson(String content) {
		try {
			return this.json.readValue(content, AgentSpec.class);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to deserialize stored pack: " + ex.getMessage(), ex);
		}
	}

	/**
	 * A saved pack's listing metadata.
	 *
	 * @param name the pack name.
	 * @param goal the pack goal name.
	 * @param steps the number of steps.
	 * @param savedAt the save timestamp as a string.
	 */
	public record PackRecord(String name, String goal, int steps, String savedAt) {
	}

	/**
	 * A recorded discovery.
	 *
	 * @param timestamp the discovery timestamp as a string.
	 * @param sourceType the discovery source type.
	 * @param location the discovery location.
	 * @param docCount the number of discovered documents.
	 */
	public record DiscoveryRecord(String timestamp, String sourceType, String location, int docCount) {
	}

}
