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
 * Offline tests for the NL-authoring pane (POC 15). The live compile is endpoint-gated,
 * so these pin the offline scaffolding: a blank description is rejected, and a missing
 * endpoint yields a clear configuration error (naming the missing vars) rather than an
 * opaque model error. Both paths return before any LLM call, so the controller is built
 * with no {@code Ai}/{@code AgentPlatform}.
 */
class NlAuthoringControllerTest {

	MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		NlAuthoringController controller = new NlAuthoringController(null, null, "", "", "", "");
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void aBlankDescriptionIsRejectedWithoutAnEndpoint() throws Exception {
		mockMvc.perform(post("/api/compile").contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("No description")));
	}

	@Test
	void aMissingEndpointReturnsAClearConfigError() throws Exception {
		mockMvc
			.perform(post("/api/compile").contentType(MediaType.APPLICATION_JSON)
				.content("{\"description\":\"A spec that explains a topic\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("OPENAI_CUSTOM_BASE_URL")))
			.andExpect(jsonPath("$.actions").isEmpty());
	}

}
