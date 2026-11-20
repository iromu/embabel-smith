/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.storage.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * The composite primary key of a stored pack (change: publish-keeps-versions): the pack
 * name plus the version the artifact declared, per {@code design.md} decision 2. The
 * store assigns it, so the class carries the no-arg constructor and the accessors
 * Hibernate uses to materialize it.
 *
 * @author Ivan Rodriguez
 */
public class PackEntityId implements Serializable {

	private static final long serialVersionUID = 1L;

	private String name;

	private String version;

	public PackEntityId() {
	}

	public PackEntityId(String name, String version) {
		this.name = name;
		this.version = version;
	}

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

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if ((obj == null) || (getClass() != obj.getClass())) {
			return false;
		}
		PackEntityId other = (PackEntityId) obj;
		return Objects.equals(this.name, other.name) && Objects.equals(this.version, other.version);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.name, this.version);
	}

}
