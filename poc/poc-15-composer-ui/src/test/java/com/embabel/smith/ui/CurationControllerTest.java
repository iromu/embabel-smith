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
 * Offline tests for the curation re-eval (POC 15). The live re-eval is endpoint-gated, so
 * these pin the offline scaffolding: no spec selected, an unknown spec, and a missing
 * endpoint (naming the missing vars) — each returns before any model call, so the
 * controller is built with no {@code AgentPlatform}.
 */
class CurationControllerTest {

	MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		SpecCatalog catalog = new SpecCatalog("");
		CurationController controller = new CurationController(catalog, new SpecCuration(catalog), null, "", "", "",
				"");
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void noSpecSelectedIsRejected() throws Exception {
		mockMvc.perform(post("/api/curate").contentType(MediaType.APPLICATION_JSON).content("{\"specName\":\"\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("No spec selected")));
	}

	@Test
	void anUnknownSpecIsRejected() throws Exception {
		mockMvc.perform(post("/api/curate").contentType(MediaType.APPLICATION_JSON).content("{\"specName\":\"nope\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Unknown spec")));
	}

	@Test
	void aKnownSpecWithoutAnEndpointReturnsAClearConfigError() throws Exception {
		mockMvc
			.perform(post("/api/curate").contentType(MediaType.APPLICATION_JSON)
				.content("{\"specName\":\"compose-extract\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("OPENAI_CUSTOM_BASE_URL")));
	}

}
