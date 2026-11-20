/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.improve;

/**
 * A candidate was not published because the improve source is a git repository and the
 * loop is not allowed to push. The publisher's commit would live only in the temporary
 * clone it deletes on {@code close()}, so attempting the publish would silently discard
 * the improvement; the publish is refused instead and the candidate stays pending.
 *
 * @author Ivan Rodriguez
 */
public class LoopPushDisabledException extends IllegalStateException {

	public LoopPushDisabledException(String message) {
		super(message);
	}

}
