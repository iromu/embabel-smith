/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest;

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleState;
import com.embabel.smith.builder.lifecycle.SpecContentHash;
import com.embabel.smith.common.io.Yaml;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.studio.rest.dto.RetrieveRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Offline MockMvc tests for the Retrieve endpoint, against real on-disk packs (the
 * {@link RestTestFixtures} pattern) with lifecycle sidecars written next to them. The
 * JSON is asserted through Jackson rather than the json-path matchers so the assertions
 * name the DTO field that broke. No test here carries a live-endpoint gate: retrieval
 * makes no model call.
 */
class RetrieveControllerTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Test
	void namesTheScorerAndRanksTheBestMatchFirst(@TempDir Path tmp) throws Exception {
		RestTestFixtures.writePack(tmp, RestTestFixtures.pack("invoicing", "reconcile"));
		RestTestFixtures.writePack(tmp, RestTestFixtures.pack("weather", "forecast"));

		JsonNode body = retrieve(tmp, "reconcile", null);

		assertThat(body.path("error").isNull()).isTrue();
		assertThat(body.path("scorer").asText()).isNotBlank();
		assertThat(body.path("results").get(0).path("name").asText()).isEqualTo("invoicing");
	}

	@Test
	void omitsARetiredPackFromTheResults(@TempDir Path tmp) throws Exception {
		AgentSpec retired = RestTestFixtures.pack("alpha", "summarise");
		RestTestFixtures.writePack(tmp, retired);
		writeSidecar(tmp.resolve("alpha"), SpecContentHash.of(retired), LifecycleState.RETIRED);
		RestTestFixtures.writePack(tmp, RestTestFixtures.pack("bravo", "summarise"));

		JsonNode body = retrieve(tmp, "summarise", null);

		// Both packs match the query; only the retired one is gone.
		assertThat(body.path("results")).hasSize(1);
		assertThat(body.path("results").get(0).path("name").asText()).isEqualTo("bravo");
	}

	@Test
	void aDegradedPackScoresHalfItsIdenticalPackAndCarriesTheState(@TempDir Path tmp) throws Exception {
		// Two packs with the same step and the same token count: the only difference is
		// the sidecar, so any score gap is the curation rule and nothing else.
		AgentSpec healthy = RestTestFixtures.pack("alpha", "summarise");
		RestTestFixtures.writePack(tmp, healthy);
		AgentSpec degraded = RestTestFixtures.pack("bravo", "summarise");
		RestTestFixtures.writePack(tmp, degraded);
		writeSidecar(tmp.resolve("bravo"), SpecContentHash.of(degraded), LifecycleState.DEGRADED);

		JsonNode results = retrieve(tmp, "summarise", null).path("results");

		assertThat(results).hasSize(2);
		assertThat(results.get(0).path("name").asText()).isEqualTo("alpha");
		assertThat(results.get(1).path("name").asText()).isEqualTo("bravo");
		double healthyScore = results.get(0).path("score").asDouble();
		assertThat(healthyScore).isGreaterThan(0.0);
		// Scores are published rounded to four decimals, so half of the rounded healthy
		// score is not bit-exact with the rounded half. The tolerance is two orders of
		// magnitude tighter than the gap a missing halving would leave.
		assertThat(results.get(1).path("score").asDouble()).isCloseTo(healthyScore * 0.5, within(0.0001));
		assertThat(results.get(1).path("lifecycleState").asText()).isEqualTo("DEGRADED");
	}

	@Test
	void aPackWithNoSidecarIsSurfacedAtFullScoreAndReportsUnknown(@TempDir Path tmp) throws Exception {
		RestTestFixtures.writePack(tmp, RestTestFixtures.pack("invoicing", "reconcile"));

		JsonNode hit = retrieve(tmp, "reconcile", null).path("results").get(0);

		assertThat(hit.path("score").asDouble()).isGreaterThan(0.0);
		assertThat(hit.path("lifecycleState").asText()).isEqualTo("UNKNOWN");
	}

	@Test
	void aBlankQueryReturnsNoResultsButStillNamesTheScorer(@TempDir Path tmp) throws Exception {
		RestTestFixtures.writePack(tmp, RestTestFixtures.pack("invoicing", "reconcile"));

		JsonNode body = retrieve(tmp, "", null);

		assertThat(body.path("error").isNull()).isTrue();
		assertThat(body.path("scorer").asText()).isNotBlank();
		assertThat(body.path("results")).isEmpty();
	}

	@Test
	void aSourceWithNoPacksReturnsNoResultsRatherThanAnError(@TempDir Path tmp) throws Exception {
		JsonNode body = retrieve(tmp, "anything", null);

		assertThat(body.path("error").isNull()).isTrue();
		assertThat(body.path("scorer").asText()).isNotBlank();
		assertThat(body.path("results")).isEmpty();
	}

	@Test
	void anUnparseableSourceReturnsTheStructuredErrorTheOtherPanesUse(@TempDir Path tmp) throws Exception {
		JsonNode body = retrieve(tmp, "nonsense", "anything", null);

		assertThat(body.path("error").asText()).contains("nonsense");
		assertThat(body.path("results")).isEmpty();
	}

	/**
	 * The controller resolves the URI the request names, whatever source it was built
	 * with.
	 */
	private static MockMvc mvc(Path sourceRoot) {
		return MockMvcBuilders.standaloneSetup(new RetrieveController(new SourceService("folder:" + sourceRoot, "")))
			.build();
	}

	private static JsonNode retrieve(Path sourceRoot, String query, Integer k) throws Exception {
		return retrieve(sourceRoot, "folder:" + sourceRoot, query, k);
	}

	private static JsonNode retrieve(Path sourceRoot, String source, String query, Integer k) throws Exception {
		ResultActions performed = mvc(sourceRoot)
			.perform(post("/api/retrieve").contentType(MediaType.APPLICATION_JSON)
				.content(MAPPER.writeValueAsString(new RetrieveRequest(source, query, k))))
			.andExpect(status().isOk());
		return MAPPER.readTree(performed.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

	private static void writeSidecar(Path packDir, String hash, LifecycleState state) throws Exception {
		LifecycleMetadata metadata = new LifecycleMetadata(hash, 1.0, state, "2026-01-01T00:00:00Z");
		Files.writeString(packDir.resolve("lifecycle.yml"), Yaml.mapper().writeValueAsString(metadata));
	}

}
