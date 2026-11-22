/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.publish;

/**
 * The version of a pack that declares none. It lives here rather than in a persistence
 * type because both the store that keys a pack by {@code (name, version)} and the
 * improvement loop that computes the next patch version need it, and the two must agree
 * on one value (change: publish-keeps-versions, decision 2).
 *
 * @author Ivan Rodriguez
 */
public final class PackVersion {

	/**
	 * The version stored for an artifact that declares none: refusing such a pack would
	 * make {@code smith build --target skill} unpublishable, because a SKILL.md carries
	 * no version the builder reads.
	 */
	public static final String DEFAULT = "1.0.0";

	private PackVersion() {
	}

}
