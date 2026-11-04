package com.embabel.smith.unified;

import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.unified.dto.ActionInfo;
import com.embabel.smith.unified.dto.AuthorDraft;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoint-gated tests for the Author pane (POC 22). The compile delegates to POC 2's
 * {@code NlCompileRunner} behind the {@link AuthorController.CompileFn} seam, so the endpoint-present
 * path is stubbed and the no-endpoint path is driven by a real (empty) {@link ModelEndpointConfig}.
 */
class AuthorControllerTest {

    private final ModelEndpointConfig noEndpoint = new ModelEndpointConfig("", "", "", "");
    private final ModelEndpointConfig endpoint = new ModelEndpointConfig("http://x", "key", "gpt", "gpt");

    private MockMvc with(AuthorController.CompileFn fn, ModelEndpointConfig ep) {
        return MockMvcBuilders.standaloneSetup(new AuthorController(fn, ep)).build();
    }

    @Test
    void noEndpointIsReportedClearly() throws Exception {
        MockMvc mvc = with((a, d) -> {
            throw new IllegalStateException("the compiler must not be reached without an endpoint");
        }, noEndpoint);
        mvc.perform(post("/api/compile").contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"explain something\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draft").isEmpty())
                .andExpect(jsonPath("$.error").value(containsString("No resolvable model endpoint")));
    }

    @Test
    void aConfiguredEndpointCompilesADraft() throws Exception {
        AuthorDraft draft = new AuthorDraft(
                "my-goal", "BackgroundMessage", true,
                List.of(new ActionInfo("step1", List.of("UserInput"), "BackgroundMessage")),
                "GOOD", false, "ok", List.of());
        MockMvc mvc = with((a, d) -> draft, endpoint);
        mvc.perform(post("/api/compile").contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"explain something\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.draft.goalName").value("my-goal"))
                .andExpect(jsonPath("$.draft.qualityVerdict").value("GOOD"))
                .andExpect(jsonPath("$.draft.actions[0].name").value("step1"));
    }

    @Test
    void aBlankDescriptionIsRejected() throws Exception {
        MockMvc mvc = with((a, d) -> {
            throw new IllegalStateException("X");
        }, endpoint);
        mvc.perform(post("/api/compile").contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(containsString("No description supplied")));
    }
}
