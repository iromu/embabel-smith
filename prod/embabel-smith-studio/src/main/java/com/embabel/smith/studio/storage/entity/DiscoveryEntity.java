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
 * A recorded discovery (change: smith-studio-postgres-storage) — one row per successful
 * business process discovery, replacing the append-only {@code discovery-history.jsonl}
 * file.
 *
 * @author Ivan Rodriguez
 */
@Entity
@Table(name = "discovery_history")
public class DiscoveryEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "ts")
	private Instant ts;

	@Column(name = "source_type")
	private String sourceType;

	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	@Column(name = "location")
	private String location;

	@Column(name = "doc_count")
	private int docCount;

	public Long getId() {
		return this.id;
	}

	public Instant getTs() {
		return this.ts;
	}

	public void setTs(Instant ts) {
		this.ts = ts;
	}

	public String getSourceType() {
		return this.sourceType;
	}

	public void setSourceType(String sourceType) {
		this.sourceType = sourceType;
	}

	public String getLocation() {
		return this.location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public int getDocCount() {
		return this.docCount;
	}

	public void setDocCount(int docCount) {
		this.docCount = docCount;
	}

}
