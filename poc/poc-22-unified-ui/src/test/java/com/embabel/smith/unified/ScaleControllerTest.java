package com.embabel.smith.unified;

import com.embabel.smith.retrieval.scale.LexicalScorer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Offline tests for the Scale pane (POC 22). The measurement delegates to POC 12's
 * {@code ScaleRetrievalRunner} over {@code ScaleSkillLibrary} with the offline lexical scorer, so
 * these pin the precision@1 shape: the full library (core + near-duplicates) is larger than the core,
 * both P@1 values are in [0,1], and the growth curve is non-empty.
 */
class ScaleControllerTest {

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ScaleController(new ScorerBinding(new LexicalScorer(), "lexical"))).build();
    }

    @Test
    void scaleReportsPrecisionAt1AndAGrowthCurve() throws Exception {
        mockMvc.perform(get("/api/scale"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scorer").value("lexical"))
                .andExpect(jsonPath("$.coreSize").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.fullSize").value(org.hamcrest.Matchers.greaterThan("$.coreSize")))
                .andExpect(jsonPath("$.coreP1").value(org.hamcrest.Matchers.between(0.0, 1.0)))
                .andExpect(jsonPath("$.fullP1").value(org.hamcrest.Matchers.between(0.0, 1.0)))
                .andExpect(jsonPath("$.curve").isArray())
                .andExpect(jsonPath("$.curve.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }
}
