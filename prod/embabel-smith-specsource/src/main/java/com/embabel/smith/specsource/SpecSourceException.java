/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.specsource;

/**
 * A spec source cannot be materialized: missing folder, unreachable remote, unknown ref,
 * or a non-git directory. The message names the offending source and the underlying
 * failure.
 *
 * @author Ivan Rodriguez
 */
public class SpecSourceException extends RuntimeException {

	public SpecSourceException(String message) {
		super(message);
	}

	public SpecSourceException(String message, Throwable cause) {
		super(message, cause);
	}

}
