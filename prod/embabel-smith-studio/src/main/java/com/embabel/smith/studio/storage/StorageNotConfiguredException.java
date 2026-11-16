/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.storage;

/**
 * Thrown by store operations when no storage endpoint is bound (change:
 * smith-studio-postgres-storage). The studio boots with no database configured; rather
 * than crashing, the affected operation reports this clear error.
 *
 * @author Ivan Rodriguez
 */
public class StorageNotConfiguredException extends RuntimeException {

	public StorageNotConfiguredException() {
		super("Storage not configured. Set the database endpoint in the Storage pane "
				+ "(or start the development database with `docker compose up`).");
	}

}
