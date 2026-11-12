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

package com.embabel.smith.studio.store;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

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
 * saved pack is one row whose {@code content} is the {@link AgentSpec} serialized as
 * JSON. When no database is bound, mutating operations and loads report "storage not
 * configured" while the listing operations return an empty list, so the studio boots and
 * stays usable with no storage.
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
	 * Saves the pack to the database and returns its listing record.
	 * @param pack the agent spec to save.
	 * @return the saved pack's listing record.
	 */
	public PackRecord savePack(AgentSpec pack) {
		String name = requireName(pack);
		StorageBinding b = this.storage.requireMain();
		return b.tx().execute((status) -> {
			PackEntity e = new PackEntity();
			e.setName(name);
			e.setGoal((pack.goal() == null) ? null : pack.goal().name());
			e.setSteps(pack.steps().size());
			e.setSavedAt(Instant.now());
			e.setContent(toJson(pack));
			b.packs().save(e);
			return new PackRecord(name, e.getGoal(), e.getSteps(), e.getSavedAt().toString());
		});
	}

	/**
	 * The saved packs, most recently saved first.
	 * @return the saved packs.
	 */
	public List<PackRecord> listPacks() {
		StorageBinding b = this.storage.main();
		if (b == null) {
			return List.of();
		}
		List<PackEntity> entities = b.tx().execute((status) -> b.packs().findAll());
		return entities.stream()
			.map((e) -> new PackRecord(e.getName(), e.getGoal(), e.getSteps(),
					(e.getSavedAt() == null) ? "" : e.getSavedAt().toString()))
			.sorted(Comparator.comparing(ProcessStore.PackRecord::savedAt).reversed())
			.toList();
	}

	/**
	 * Loads a saved pack by name, or {@code null} when no such pack is stored.
	 * @param name the pack name to load.
	 * @return the stored agent spec, or {@code null} when no such pack is stored.
	 */
	public AgentSpec loadPack(String name) {
		StorageBinding b = this.storage.requireMain();
		PackEntity e = b.tx().execute((status) -> b.packs().findById(name).orElse(null));
		if (e == null) {
			return null;
		}
		return fromJson(e.getContent());
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
