/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.retrieval.remote;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteEmbeddingGateTest {

	@Test
	void readyWhenModelIsServed() {
		RemoteEmbeddingGate.Result r = RemoteEmbeddingGate.check(() -> List.of("nomic-embed-text-v2", "Qwen-instruct"),
				"nomic-embed-text-v2");
		assertThat(r.ready()).isTrue();
		assertThat(r.failure()).isNull();
	}

	@Test
	void notReadyWhenEndpointUnreachable() {
		RemoteEmbeddingGate.Result r = RemoteEmbeddingGate.check(() -> {
			throw new RuntimeException("connection refused");
		}, "nomic-embed-text-v2");
		assertThat(r.ready()).isFalse();
		assertThat(r.failure()).contains("not reachable").contains("connection refused");
	}

	@Test
	void notReadyWhenModelNotListed() {
		RemoteEmbeddingGate.Result r = RemoteEmbeddingGate.check(() -> List.of("Qwen-instruct"), "nomic-embed-text-v2");
		assertThat(r.ready()).isFalse();
		assertThat(r.failure()).contains("not served").contains("Qwen-instruct");
	}

	@Test
	void notReadyWhenNoModelsListed() {
		RemoteEmbeddingGate.Result r = RemoteEmbeddingGate.check(() -> List.of(), "nomic-embed-text-v2");
		assertThat(r.ready()).isFalse();
		assertThat(r.failure()).contains("no models");
	}

}
