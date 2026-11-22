/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.storage;

import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

import com.embabel.smith.common.trace.TraceRecord;
import com.embabel.smith.common.trace.TraceStore;
import com.embabel.smith.studio.storage.entity.TraceEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * A {@link TraceStore} backed by the storage database (change:
 * smith-studio-postgres-storage). The binding is resolved through a supplier so the
 * backend can be rebound at runtime — the same database as the packs, or a separate one —
 * while the record shape is unchanged.
 *
 * @author Ivan Rodriguez
 */
public class JpaTraceStore implements TraceStore {

	private final Supplier<StorageBinding> binding;

	private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

	public JpaTraceStore(Supplier<StorageBinding> binding) {
		this.binding = binding;
	}

	@Override
	public void append(TraceRecord record) {
		StorageBinding b = this.binding.get();
		b.tx().executeWithoutResult((status) -> b.traces().save(toEntity(record)));
	}

	@Override
	public ReadResult read() {
		StorageBinding b = this.binding.get();
		List<TraceEntity> entities = b.tx().execute((status) -> b.traces().findAll());
		List<TraceRecord> records = entities.stream()
			.sorted(Comparator.comparing(TraceEntity::getId))
			.map(this::toRecord)
			.toList();
		return new ReadResult(records, List.of());
	}

	private TraceEntity toEntity(TraceRecord r) {
		TraceEntity e = new TraceEntity();
		e.setRunId(r.runId());
		e.setTs(r.timestamp());
		e.setSourceUri((r.source() == null) ? null : r.source().uri());
		e.setSourceRef((r.source() == null) ? null : r.source().ref());
		e.setResolvedAt((r.source() == null) ? null : r.source().resolvedAt());
		e.setPackName((r.pack() == null) ? null : r.pack().name());
		e.setPackContentHash((r.pack() == null) ? null : r.pack().contentHash());
		e.setGoal(r.goal());
		e.setInput(r.input());
		e.setPlanner(r.planner());
		e.setTrustTier(r.trustTier());
		e.setStepsJson(serializeSteps(r.steps()));
		e.setSuccess(r.success());
		e.setError(r.error());
		e.setOutputExcerpt(r.outputExcerpt());
		return e;
	}

	private TraceRecord toRecord(TraceEntity e) {
		TraceRecord.SourceRef source = (e.getSourceUri() == null) ? null
				: new TraceRecord.SourceRef(e.getSourceUri(), e.getSourceRef(), e.getResolvedAt());
		TraceRecord.PackRef pack = (e.getPackName() == null) ? null
				: new TraceRecord.PackRef(e.getPackName(), e.getPackContentHash());
		return new TraceRecord(e.getRunId(), e.getTs(), source, pack, e.getGoal(), e.getInput(), e.getPlanner(),
				e.getTrustTier(), parseSteps(e.getStepsJson()), e.isSuccess(), e.getError(), e.getOutputExcerpt());
	}

	private String serializeSteps(List<TraceRecord.StepRecord> steps) {
		try {
			return this.json.writeValueAsString((steps != null) ? steps : List.of());
		}
		catch (Exception ex) {
			throw new IllegalStateException("cannot serialize trace steps: " + ex.getMessage(), ex);
		}
	}

	private List<TraceRecord.StepRecord> parseSteps(String stepsJson) {
		if (stepsJson == null || stepsJson.isBlank()) {
			return List.of();
		}
		CollectionType type = this.json.getTypeFactory()
			.constructCollectionType(List.class, TraceRecord.StepRecord.class);
		try {
			return this.json.readValue(stepsJson, type);
		}
		catch (Exception ex) {
			throw new IllegalStateException("cannot parse trace steps: " + ex.getMessage(), ex);
		}
	}

}
