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
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A saved agent spec (change: smith-studio-postgres-storage). The spec is stored as a
 * single serialized blob in {@code content} (the
 * {@link com.embabel.smith.common.model.AgentSpec} as JSON); {@code name} is the primary
 * key, and {@code goal}/{@code steps}/{@code savedAt} are denormalized so the pack
 * listing does not have to parse every stored spec.
 *
 * <p>
 * Column types are portable (long VARCHAR, TIMESTAMP) so the same model drives both the
 * in-memory H2 test profile and the Postgres development database. The long text uses
 * {@code SqlTypes.LONGVARCHAR} rather than {@code @Lob}: a CLOB maps to Postgres'
 * {@code oid} large-object type (a separate, non-portable object), whereas LONGVARCHAR is
 * an inline, queryable column on both databases.
 *
 * @author Ivan Rodriguez
 */
@Entity
@Table(name = "packs")
public class PackEntity {

	@Id
	@Column(name = "name")
	private String name;

	@Column(name = "goal")
	private String goal;

	@Column(name = "steps")
	private int steps;

	@Column(name = "saved_at")
	private Instant savedAt;

	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	@Column(name = "content")
	private String content;

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getGoal() {
		return this.goal;
	}

	public void setGoal(String goal) {
		this.goal = goal;
	}

	public int getSteps() {
		return this.steps;
	}

	public void setSteps(int steps) {
		this.steps = steps;
	}

	public Instant getSavedAt() {
		return this.savedAt;
	}

	public void setSavedAt(Instant savedAt) {
		this.savedAt = savedAt;
	}

	public String getContent() {
		return this.content;
	}

	public void setContent(String content) {
		this.content = content;
	}

}
