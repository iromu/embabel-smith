/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.remote;

/**
 * A remote-action sidecar an operator configured: the name the product calls it by, and
 * the base URL its three contract endpoints hang off. Both come from configuration — a
 * request can select one of these by name but never supply an address of its own.
 *
 * @param name the name configuration gave this bridge, used to select it
 * @param baseUrl the base URL of the sidecar, without a trailing slash
 * @author Ivan Rodriguez
 */
public record RemoteBridge(String name, String baseUrl) {
}
