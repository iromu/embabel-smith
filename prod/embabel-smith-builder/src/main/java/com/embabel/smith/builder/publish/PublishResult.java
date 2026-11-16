/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.publish;

import java.nio.file.Path;

/**
 * The outcome of a publish: where the pack now lives, and (for git) whether the local
 * commit was pushed to the remote.
 *
 * @param location the directory where the published pack now lives
 * @param pushed whether the local commit was pushed to the remote (git only)
 * @author Ivan Rodriguez
 */
public record PublishResult(Path location, boolean pushed) {
}
