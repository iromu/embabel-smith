/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.security;

/**
 * The overall install-time verdict for a scanned agent spec.
 *
 * @author Ivan Rodriguez
 */
public enum ScanVerdict {

	/** No findings — safe to load at the recommended tier. */
	SAFE,

	/**
	 * Warning-level findings — load only under scrutiny, at the recommended (higher)
	 * tier.
	 */
	REVIEW,

	/** A critical finding — do not load; quarantine the pack. */
	BLOCK

}
