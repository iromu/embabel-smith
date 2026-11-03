package com.embabel.smith.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Offline tests for the eval/refinement pane (POC 15). The live eval + rewrite are endpoint-gated, so
 * these pin the offline scaffolding: no skill selected, an unknown skill, and a missing endpoint
 * (naming the missing vars) — each returns before any model call, so the controller is built with no
 * {@code AgentPlatform}.
 */
class EvalControllerTest {

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SkillCatalog catalog = new SkillCatalog("");
        EvalController controller = new EvalController(catalog, null, "", "", "", "");
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void noSkillSelectedIsRejected() throws Exception {
        mockMvc.perform(post("/api/eval").contentType(MediaType.APPLICATION_JSON)
                .content("{\"skillName\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error")
                        .value(org.hamcrest.Matchers.containsString("No skill selected")));
    }

    @Test
    void anUnknownSkillIsRejected() throws Exception {
        mockMvc.perform(post("/api/eval").contentType(MediaType.APPLICATION_JSON)
                .content("{\"skillName\":\"nope\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error")
                        .value(org.hamcrest.Matchers.containsString("Unknown skill")));
    }

    @Test
    void aKnownSkillWithoutAnEndpointReturnsAClearConfigError() throws Exception {
        mockMvc.perform(post("/api/eval").contentType(MediaType.APPLICATION_JSON)
                .content("{\"skillName\":\"explainer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error")
                        .value(org.hamcrest.Matchers.containsString("OPENAI_CUSTOM_BASE_URL")))
                .andExpect(jsonPath("$.failingInputs").isEmpty());
    }
}
