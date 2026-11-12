package com.embabel.smith.unified;

import com.embabel.smith.retrieval.SpecEntry;
import com.embabel.smith.retrieval.scale.LexicalScorer;
import com.embabel.smith.unified.dto.RetrieveResponse;
import com.embabel.smith.unified.dto.RetrieveResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Offline tests for the Retrieve pane (POC 22). The endpoint reuses POC 12's
 * scorer-agnostic {@code ScaleSpecRegistry} over the catalog with the offline lexical
 * scorer, so these pin the "which specs would this query route to" ranking — no model
 * needed. The pane also applies POC 18's library curation (retired dropped, degraded
 * de-ranked, every hit stamped with its lifecycle state), and reports the active scorer
 * name so a denser scorer (the seam) is observable.
 */
class RetrieveControllerTest {

	private static final String EXTRACT = "compose-extract";

	private static final String EXTRACT_QUERY = "extract the topic audience and tone from the user request";

	private final ObjectMapper json = new ObjectMapper();

	private SpecCatalog catalog;

	private SpecCuration curation;

	MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		catalog = new SpecCatalog("");
		curation = new SpecCuration(catalog);
		mockMvc = MockMvcBuilders
			.standaloneSetup(
					new RetrieveController(catalog, curation, new ScorerBinding(new LexicalScorer(), "lexical")))
			.build();
	}

	private RetrieveResponse retrieve(String query, int k) throws Exception {
		String body = String.format("{\"query\":\"%s\",\"k\":%d}", query, k);
		String out = mockMvc.perform(post("/api/retrieve").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return json.readValue(out, RetrieveResponse.class);
	}

	private Optional<RetrieveResult> find(RetrieveResponse resp, String name) {
		return resp.results().stream().filter(r -> name.equals(r.name())).findFirst();
	}

	@Test
	void anExtractQueryRanksTheExtractSpecFirst() throws Exception {
		String body = String.format("{\"query\":\"%s\",\"k\":3}", EXTRACT_QUERY);
		mockMvc.perform(post("/api/retrieve").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.scorer").value("lexical"))
			.andExpect(jsonPath("$.results[0].name").value(org.hamcrest.Matchers.containsString("extract")))
			.andExpect(jsonPath("$.results[0].score").value(org.hamcrest.Matchers.greaterThan(0.0)));
	}

	@Test
	void resultsAreSortedByScoreDescendingAndBoundedByK() throws Exception {
		RetrieveResponse resp = retrieve("topic explainer background message", 2);
		assertThat(resp.results()).hasSize(2);
		assertThat(resp.results().get(0).score()).isGreaterThanOrEqualTo(resp.results().get(1).score());
	}

	@Test
	void resultsCarryALifecycleState() throws Exception {
		RetrieveResponse resp = retrieve(EXTRACT_QUERY, 5);
		assertThat(resp.results()).isNotEmpty();
		assertThat(resp.results())
			.allSatisfy(r -> assertThat(r.state()).isIn("INCUBATING", "ACTIVE", "DEGRADED", "RETIRED"));
	}

	@Test
	void aRetiredSpecIsDroppedFromRetrieval() throws Exception {
		curation.curate(EXTRACT, 0.1); // score collapse -> RETIRED, out of the library
		List<RetrieveResult> results = retrieve(EXTRACT_QUERY, 10).results();
		assertThat(results).noneMatch(r -> EXTRACT.equals(r.name()));
	}

	@Test
	void aDegradedSpecIsDeRanked() throws Exception {
		double activeScore = find(retrieve(EXTRACT_QUERY, 10), EXTRACT).orElseThrow().score();
		curation.curate(EXTRACT, 0.85); // score drop -> DEGRADED, de-ranked
		RetrieveResult degraded = find(retrieve(EXTRACT_QUERY, 10), EXTRACT).orElseThrow();
		assertThat(degraded.state()).isEqualTo("DEGRADED");
		assertThat(degraded.score()).isLessThan(activeScore);
	}

	@Test
	void swappingTheScorerChangesTheReportedNameAndRanking() throws Exception {
		// A scorer that always ranks compose-explain first, named "custom" — different
		// from lexical.
		ScorerBinding custom = new ScorerBinding((query, entry) -> "compose-explain".equals(entry.name()) ? 1.0 : 0.0,
				"custom");
		MockMvc customMvc = MockMvcBuilders.standaloneSetup(new RetrieveController(catalog, curation, custom)).build();
		String body = String.format("{\"query\":\"%s\",\"k\":3}", EXTRACT_QUERY);
		customMvc.perform(post("/api/retrieve").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.scorer").value("custom"))
			.andExpect(jsonPath("$.results[0].name").value("compose-explain"));
	}

}
