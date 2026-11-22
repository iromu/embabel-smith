/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.publish;

/**
 * The outcome of a publish: where the artifact now lives, and (for git) whether the local
 * commit was pushed to the remote.
 *
 * <p>
 * The location is stated in the source's own terms and stays true after the publisher is
 * closed: a {@code folder:} publish reports the directory it wrote, a {@code git:}
 * publish reports the repository it committed to with the artifact's path inside it
 * ({@code git:<url>#<path>}). A git publish never reports a directory inside the working
 * tree the publisher clones into, because {@code close()} deletes that tree.
 *
 * @param location where the artifact lives, as the source states it
 * @param pushed whether the local commit was pushed to the remote (git only)
 * @author Ivan Rodriguez
 */
public record PublishResult(String location, boolean pushed) {
}
