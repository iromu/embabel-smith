package com.embabel.smith.unified;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Offline tests for the Trust pane (POC 22). The check delegates to POC 10's
 * {@code TrustTierPolicy} (the per-tier tool-group allowlist — pure, no model), so these pin the
 * allow/deny decision: a {@code shell} spec is denied at {@code T1} (no tool access) and allowed at
 * {@code T4} (everything), plus the offline scaffolding (unknown tier, no spec, unknown spec).
 */
class TrustControllerTest {

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SpecCatalog catalog = new SpecCatalog("");
        mockMvc = MockMvcBuilders.standaloneSetup(new TrustController(catalog)).build();
    }

    @Test
    void aShellSpecIsDeniedAtT1() throws Exception {
        mockMvc.perform(post("/api/trust").contentType(MediaType.APPLICATION_JSON)
                .content("{\"specName\":\"shell-runner\",\"tier\":\"T1_COMMUNITY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.disallowedGroups[?(@=='shell')]").exists())
                .andExpect(jsonPath("$.declaredToolGroups[?(@=='shell')]").exists());
    }

    @Test
    void aShellSpecIsAllowedAtT4() throws Exception {
        mockMvc.perform(post("/api/trust").contentType(MediaType.APPLICATION_JSON)
                .content("{\"specName\":\"shell-runner\",\"tier\":\"T4_PLATFORM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.disallowedGroups").isEmpty());
    }

    @Test
    void anUnknownTierIsRejected() throws Exception {
        mockMvc.perform(post("/api/trust").contentType(MediaType.APPLICATION_JSON)
                .content("{\"specName\":\"shell-runner\",\"tier\":\"T9\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error")
                        .value(org.hamcrest.Matchers.containsString("Unknown tier")));
    }

    @Test
    void noSpecSelectedIsRejected() throws Exception {
        mockMvc.perform(post("/api/trust").contentType(MediaType.APPLICATION_JSON)
                .content("{\"specName\":\"\",\"tier\":\"T3_PARTNER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error")
                        .value(org.hamcrest.Matchers.containsString("No spec selected")));
    }
}
