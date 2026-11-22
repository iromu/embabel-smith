/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.storage.entity;

import java.time.Instant;

import com.embabel.smith.builder.publish.PackVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A saved agent spec (change: smith-studio-postgres-storage). The spec is stored as a
 * single serialized blob in {@code content} (the
 * {@link com.embabel.smith.common.model.AgentSpec} as JSON); {@code (name, version)} is
 * the primary key, so every version of a pack is its own row (change:
 * publish-keeps-versions), and {@code goal}/{@code steps}/{@code savedAt} are
 * denormalized so the pack listing does not have to parse every stored spec.
 *
 * <p>
 * Column types are portable (long VARCHAR, TIMESTAMP) so the same model drives both the
 * in-memory H2 test profile and the Postgres development database. The long text uses
 * {@code SqlTypes.LONGVARCHAR} rather than {@code @Lob}: a CLOB maps to Postgres'
 * {@code oid} large-object type (a separate, non-portable object), whereas LONGVARCHAR is
 * an inline, queryable column on both databases. The key column is a plain VARCHAR for
 * the same reason, and {@code hbm2ddl.auto = update} will not re-key a table that already
 * exists — see {@link com.embabel.smith.studio.storage.PacksKeyMigration}.
 *
 * @author Ivan Rodriguez
 */
@Entity
@Table(name = "packs")
@IdClass(PackEntityId.class)
public class PackEntity {

	/**
	 * The version stored for an artifact that declares none ({@code design.md} decision 2
	 * of publish-keeps-versions): refusing such a pack would make
	 * {@code smith build --target
	 * skill} unpublishable, because a SKILL.md carries no version the builder reads.
	 */
	public static final String DEFAULT_VERSION = PackVersion.DEFAULT;

	@Id
	@Column(name = "name")
	private String name;

	@Id
	@Column(name = "version")
	private String version;

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

	public String getVersion() {
		return this.version;
	}

	public void setVersion(String version) {
		this.version = version;
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
