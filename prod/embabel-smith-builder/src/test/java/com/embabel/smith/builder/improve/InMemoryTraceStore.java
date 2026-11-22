/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.improve;

import com.embabel.smith.common.trace.TraceRecord;
import com.embabel.smith.common.trace.TraceStore;

import java.util.ArrayList;
import java.util.List;

/**
 * The trace store the builder's improve tests write to: a list behind the
 * {@link TraceStore} SPI, in append order.
 * <p>
 * The builder carries its own store rather than the runtime's JSONL one on purpose: the
 * builder deploys and executes packs with no dependency on {@code embabel-smith-runtime},
 * and a test-scoped one would falsify that. A non-file store is an anticipated shape of
 * the SPI, whose javadoc says such a store reports an empty warnings list.
 *
 * @author Ivan Rodriguez
 */
class InMemoryTraceStore implements TraceStore {

	private final List<TraceRecord> records = new ArrayList<>();

	@Override
	public void append(TraceRecord record) {
		this.records.add(record);
	}

	@Override
	public ReadResult read() {
		return new ReadResult(List.copyOf(this.records), List.of());
	}

}
