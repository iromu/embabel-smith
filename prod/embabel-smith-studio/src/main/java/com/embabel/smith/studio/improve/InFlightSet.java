/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.improve;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the specs with an improvement currently in flight. Enforces "at most one active
 * improvement per spec": a spec already in flight is skipped for the current iteration.
 *
 * @author Ivan Rodriguez
 */
public final class InFlightSet {

	private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

	/**
	 * Marks the spec in flight.
	 * @param spec the spec to mark as in flight
	 * @return {@code true} when it was not already in flight (the caller may proceed).
	 */
	public boolean begin(String spec) {
		return this.inFlight.add(spec);
	}

	/**
	 * Marks the spec no longer in flight.
	 * @param spec the spec to mark as no longer in flight
	 */
	public void end(String spec) {
		this.inFlight.remove(spec);
	}

	/**
	 * Whether the spec is currently in flight.
	 * @param spec the spec to check
	 * @return {@code true} when the spec is currently in flight.
	 */
	public boolean inFlight(String spec) {
		return this.inFlight.contains(spec);
	}

	/**
	 * Whether no spec is currently in flight.
	 * @return {@code true} when no spec is currently in flight.
	 */
	public boolean isEmpty() {
		return this.inFlight.isEmpty();
	}

}
