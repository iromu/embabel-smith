/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.lifecycle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The digest the lifecycle sidecar is keyed on: a deterministic hash of whatever
 * canonical text a build declares to be its load-bearing surface. Shared by the pack hash
 * ({@link SpecContentHash}) and the skill hash ({@code SkillContentHash}) so both targets
 * record a comparable drift signal.
 *
 * @author Ivan Rodriguez
 */
public final class ContentHash {

	private ContentHash() {
	}

	/**
	 * The SHA-256 of the canonical text, hex-encoded.
	 * @param canonical the canonical form of the artifact's load-bearing surface.
	 * @return the lowercase hex digest.
	 */
	public static String sha256Hex(String canonical) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(bytes.length * 2);
			for (byte b : bytes) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}

}
