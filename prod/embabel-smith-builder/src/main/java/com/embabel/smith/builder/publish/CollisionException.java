/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.publish;

/**
 * A build cannot be published because a same-named artifact — an agent-spec pack or a
 * skill folder — already exists at the destination. The build is blocked; the user may
 * retry with {@code --overwrite} to replace it.
 *
 * @author Ivan Rodriguez
 */
public class CollisionException extends RuntimeException {

	public CollisionException(String message) {
		super(message);
	}

}
