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

package com.embabel.smith.studio.storage.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A run trace record (change: smith-studio-postgres-storage) — the database mirror of the
 * JSONL trace record. The scalar fields of
 * {@link com.embabel.smith.runtime.trace.TraceRecord} are stored as columns; the per-step
 * records are serialized as a JSON array in {@code steps_json} so the record shape is
 * unchanged by the backend. Column types are portable (long VARCHAR, BIGINT, TIMESTAMP);
 * the long text uses {@code SqlTypes.LONGVARCHAR} rather than {@code @Lob}, which would
 * map to Postgres' non-portable {@code oid} large-object type.
 *
 * @author Ivan Rodriguez
 */
@Entity
@Table(name = "traces")
public class TraceEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "run_id")
	private String runId;

	@Column(name = "ts")
	private Instant ts;

	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	@Column(name = "source_uri")
	private String sourceUri;

	@Column(name = "source_ref")
	private String sourceRef;

	@Column(name = "resolved_at")
	private String resolvedAt;

	@Column(name = "pack_name")
	private String packName;

	@Column(name = "pack_content_hash")
	private String packContentHash;

	@Column(name = "goal")
	private String goal;

	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	@Column(name = "input")
	private String input;

	@Column(name = "planner")
	private String planner;

	@Column(name = "trust_tier")
	private String trustTier;

	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	@Column(name = "steps_json")
	private String stepsJson;

	@Column(name = "success")
	private boolean success;

	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	@Column(name = "error")
	private String error;

	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	@Column(name = "output_excerpt")
	private String outputExcerpt;

	public Long getId() {
		return this.id;
	}

	public String getRunId() {
		return this.runId;
	}

	public void setRunId(String runId) {
		this.runId = runId;
	}

	public Instant getTs() {
		return this.ts;
	}

	public void setTs(Instant ts) {
		this.ts = ts;
	}

	public String getSourceUri() {
		return this.sourceUri;
	}

	public void setSourceUri(String sourceUri) {
		this.sourceUri = sourceUri;
	}

	public String getSourceRef() {
		return this.sourceRef;
	}

	public void setSourceRef(String sourceRef) {
		this.sourceRef = sourceRef;
	}

	public String getResolvedAt() {
		return this.resolvedAt;
	}

	public void setResolvedAt(String resolvedAt) {
		this.resolvedAt = resolvedAt;
	}

	public String getPackName() {
		return this.packName;
	}

	public void setPackName(String packName) {
		this.packName = packName;
	}

	public String getPackContentHash() {
		return this.packContentHash;
	}

	public void setPackContentHash(String packContentHash) {
		this.packContentHash = packContentHash;
	}

	public String getGoal() {
		return this.goal;
	}

	public void setGoal(String goal) {
		this.goal = goal;
	}

	public String getInput() {
		return this.input;
	}

	public void setInput(String input) {
		this.input = input;
	}

	public String getPlanner() {
		return this.planner;
	}

	public void setPlanner(String planner) {
		this.planner = planner;
	}

	public String getTrustTier() {
		return this.trustTier;
	}

	public void setTrustTier(String trustTier) {
		this.trustTier = trustTier;
	}

	public String getStepsJson() {
		return this.stepsJson;
	}

	public void setStepsJson(String stepsJson) {
		this.stepsJson = stepsJson;
	}

	public boolean isSuccess() {
		return this.success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public String getError() {
		return this.error;
	}

	public void setError(String error) {
		this.error = error;
	}

	public String getOutputExcerpt() {
		return this.outputExcerpt;
	}

	public void setOutputExcerpt(String outputExcerpt) {
		this.outputExcerpt = outputExcerpt;
	}

}
