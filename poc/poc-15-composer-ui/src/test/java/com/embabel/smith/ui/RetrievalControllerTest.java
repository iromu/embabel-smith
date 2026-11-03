package com.embabel.smith.ui;

import com.embabel.smith.ui.dto.RetrieveResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Offline tests for the retrieval pane (POC 15). The endpoint reuses POC 12's scorer-agnostic
 * {@code ScaleSkillRegistry} over the catalog with the offline lexical scorer, so these pin the
 * "which skills would this query route to" ranking the canvas drives — no model needed.
 */
class RetrievalControllerTest {

    private final ObjectMapper json = new ObjectMapper();
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SkillCatalog catalog = new SkillCatalog("");
        mockMvc = MockMvcBuilders.standaloneSetup(new RetrievalController(catalog)).build();
    }

    @Test
    void anExtractQueryRanksTheExtractSkillFirst() throws Exception {
        String body = "{\"query\":\"extract the topic audience and tone from the user request\",\"k\":3}";
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
        String body = "{\"query\":\"topic explainer background message\",\"k\":2}";
        String out = mockMvc.perform(post("/api/retrieve").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        RetrieveResponse resp = json.readValue(out, RetrieveResponse.class);
        assertThat(resp.results()).hasSize(2);
        assertThat(resp.results().get(0).score())
                .isGreaterThanOrEqualTo(resp.results().get(1).score());
    }
}
