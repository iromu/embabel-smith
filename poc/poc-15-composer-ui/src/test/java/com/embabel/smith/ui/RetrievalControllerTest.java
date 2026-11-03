package com.embabel.smith.ui;

import com.embabel.smith.ui.dto.RetrieveResponse;
import com.embabel.smith.ui.dto.RetrieveResult;
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
 * Offline tests for the retrieval pane (POC 15). The endpoint reuses POC 12's scorer-agnostic
 * {@code ScaleSkillRegistry} over the catalog with the offline lexical scorer, so these pin the
 * "which skills would this query route to" ranking the canvas drives — no model needed. The pane
 * also applies POC 18's library curation: a retired skill is dropped and a degraded one is de-ranked.
 */
class RetrievalControllerTest {

    private static final String EXTRACT = "compose-extract";
    private static final String EXTRACT_QUERY = "extract the topic audience and tone from the user request";

    private final ObjectMapper json = new ObjectMapper();
    private SkillCatalog catalog;
    private SkillCuration curation;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        catalog = new SkillCatalog("");
        curation = new SkillCuration(catalog);
        mockMvc = MockMvcBuilders.standaloneSetup(new RetrievalController(catalog, curation)).build();
    }

    private RetrieveResponse retrieve(String query, int k) throws Exception {
        String body = String.format("{\"query\":\"%s\",\"k\":%d}", query, k);
        String out = mockMvc.perform(post("/api/retrieve")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readValue(out, RetrieveResponse.class);
    }

    private Optional<RetrieveResult> find(RetrieveResponse resp, String name) {
        return resp.results().stream().filter(r -> name.equals(r.name())).findFirst();
    }

    @Test
    void anExtractQueryRanksTheExtractSkillFirst() throws Exception {
        String body = String.format("{\"query\":\"%s\",\"k\":3}", EXTRACT_QUERY);
        mockMvc.perform(post("/api/retrieve").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scorer").value("lexical"))
                .andExpect(jsonPath("$.results[0].name")
                        .value(org.hamcrest.Matchers.containsString("extract")))
                .andExpect(jsonPath("$.results[0].score").value(org.hamcrest.Matchers.greaterThan(0.0)));
    }

    @Test
    void anExplainQueryRanksAnExplainSkillFirst() throws Exception {
        String body = "{\"query\":\"write a complete explainer for the user\",\"k\":3}";
        mockMvc.perform(post("/api/retrieve").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].name")
                        .value(org.hamcrest.Matchers.containsString("explain")));
    }

    @Test
    void resultsAreSortedByScoreDescendingAndBoundedByK() throws Exception {
        RetrieveResponse resp = retrieve("topic explainer background message", 2);
        assertThat(resp.results()).hasSize(2);
        assertThat(resp.results().get(0).score())
                .isGreaterThanOrEqualTo(resp.results().get(1).score());
    }

    @Test
    void resultsCarryALifecycleState() throws Exception {
        RetrieveResponse resp = retrieve(EXTRACT_QUERY, 5);
        assertThat(resp.results()).isNotEmpty();
        assertThat(resp.results())
                .allSatisfy(r -> assertThat(r.state()).isIn("INCUBATING", "ACTIVE", "DEGRADED", "RETIRED"));
    }

    @Test
    void aRetiredSkillIsDroppedFromRetrieval() throws Exception {
        curation.curate(EXTRACT, 0.1); // score collapse -> RETIRED, out of the library
        List<RetrieveResult> results = retrieve(EXTRACT_QUERY, 10).results();
        assertThat(results).noneMatch(r -> EXTRACT.equals(r.name()));
    }

    @Test
    void aDegradedSkillIsDeRanked() throws Exception {
        double activeScore = find(retrieve(EXTRACT_QUERY, 10), EXTRACT)
                .orElseThrow().score();
        curation.curate(EXTRACT, 0.85); // score drop -> DEGRADED, de-ranked
        RetrieveResult degraded = find(retrieve(EXTRACT_QUERY, 10), EXTRACT).orElseThrow();
        assertThat(degraded.state()).isEqualTo("DEGRADED");
        assertThat(degraded.score()).isLessThan(activeScore);
    }
}
