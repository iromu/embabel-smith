/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

import java.util.Locale;

/**
 * Derives a stable, filesystem-safe pack name from a natural-language description. The
 * same description always yields the same name, so rebuilding the same spec targets the
 * same source location (which is what the collision gate detects).
 *
 * @author Ivan Rodriguez
 */
public final class PackName {

	private PackName() {
	}

	public static String slug(String description) {
		String slug = (description != null)
				? description.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "") : "";
		if (slug.length() > 40) {
			slug = slug.substring(0, 40).replaceAll("-+$", "");
		}
		return slug.isEmpty() ? "built-spec" : slug;
	}

}
