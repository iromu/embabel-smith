package com.embabel.smith.unified;

import com.embabel.smith.common.config.ModelEndpointConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoint-gated tests for the Run pane (POC 22). The run delegates to POC 1's no-code runtime behind
 * the {@link RunController.RunFn} seam, so the endpoint-present path is stubbed and the no-endpoint
 * path is driven by a real (empty) {@link ModelEndpointConfig} — the same "clear configuration error
 * before any model call" behaviour the other gated panes share.
 */
class RunControllerTest {

    private final ModelEndpointConfig noEndpoint = new ModelEndpointConfig("", "", "", "");
    private final ModelEndpointConfig endpoint = new ModelEndpointConfig("http://x", "key", "gpt", "gpt");
    private SpecCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new SpecCatalog("");
    }

    private MockMvc with(RunController.RunFn fn, ModelEndpointConfig ep) {
        return MockMvcBuilders.standaloneSetup(new RunController(catalog, fn, ep)).build();
    }

    @Test
    void noEndpointIsReportedClearly() throws Exception {
        MockMvc mvc = with((s, i) -> {
            throw new IllegalStateException("the runner must not be reached without an endpoint");
        }, noEndpoint);
        mvc.perform(post("/api/run").contentType(MediaType.APPLICATION_JSON)
                .content("{\"specName\":\"compose-extract\",\"input\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output").isEmpty())
                .andExpect(jsonPath("$.error").value(containsString("No resolvable model endpoint")));
    }

    @Test
    void aConfiguredEndpointRunsTheSpec() throws Exception {
        MockMvc mvc = with((s, i) -> "RUN_OUTPUT", endpoint);
        mvc.perform(post("/api/run").contentType(MediaType.APPLICATION_JSON)
                .content("{\"specName\":\"compose-extract\",\"input\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.output").value("RUN_OUTPUT"));
    }

    @Test
    void noSpecSelectedIsRejected() throws Exception {
        MockMvc mvc = with((s, i) -> "X", endpoint);
        mvc.perform(post("/api/run").contentType(MediaType.APPLICATION_JSON).content("{\"specName\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(containsString("No spec selected")));
    }

    @Test
    void anUnknownSpecIsRejected() throws Exception {
        MockMvc mvc = with((s, i) -> "X", endpoint);
        mvc.perform(post("/api/run").contentType(MediaType.APPLICATION_JSON).content("{\"specName\":\"nope\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(containsString("Unknown spec")));
    }
}
