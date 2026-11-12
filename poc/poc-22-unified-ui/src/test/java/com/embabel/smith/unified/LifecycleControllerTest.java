package com.embabel.smith.unified;

import com.embabel.smith.common.config.ModelEndpointConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.isIn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoint-gated tests for the Lifecycle pane (POC 22). The live re-eval delegates to POC
 * 18's {@code LiveEvalRunner} behind the {@link LifecycleController.LiveEvalFn} seam, so
 * the endpoint-present path is stubbed and the no-endpoint path is driven by a real
 * (empty) {@link ModelEndpointConfig}. The pure state-machine half (the transition) runs
 * offline over {@link SpecCuration}.
 */
class LifecycleControllerTest {

	private final ModelEndpointConfig noEndpoint = new ModelEndpointConfig("", "", "", "");

	private final ModelEndpointConfig endpoint = new ModelEndpointConfig("http://x", "key", "gpt", "gpt");

	private SpecCatalog catalog;

	private SpecCuration curation;

	@BeforeEach
	void setUp() {
		catalog = new SpecCatalog("");
		curation = new SpecCuration(catalog);
	}

	private MockMvc with(LifecycleController.LiveEvalFn fn, ModelEndpointConfig ep) {
		return MockMvcBuilders.standaloneSetup(new LifecycleController(catalog, curation, fn, ep)).build();
	}

	@Test
	void noEndpointIsReportedClearly() throws Exception {
		MockMvc mvc = with((s, t) -> 0.0, noEndpoint);
		mvc.perform(post("/api/curate").contentType(MediaType.APPLICATION_JSON)
			.content("{\"specName\":\"compose-extract\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.error").value(containsString("No resolvable model endpoint")));
	}

	@Test
	void aConfiguredEndpointReEvaluatesAndTransitions() throws Exception {
		// A high pass-rate keeps the seeded ACTIVE spec surfaced; the response carries
		// the transition.
		MockMvc mvc = with((s, t) -> 0.95, endpoint);
		mvc.perform(post("/api/curate").contentType(MediaType.APPLICATION_JSON)
			.content("{\"specName\":\"compose-extract\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.error").isEmpty())
			.andExpect(jsonPath("$.specName").value("compose-extract"))
			.andExpect(jsonPath("$.before").value("ACTIVE"))
			.andExpect(jsonPath("$.after").value(isIn(List.of("INCUBATING", "ACTIVE", "DEGRADED", "RETIRED"))))
			.andExpect(jsonPath("$.currentScore").value(0.95))
			.andExpect(jsonPath("$.verdict").exists());
	}

	@Test
	void noSpecSelectedIsRejected() throws Exception {
		MockMvc mvc = with((s, t) -> 0.0, endpoint);
		mvc.perform(post("/api/curate").contentType(MediaType.APPLICATION_JSON).content("{\"specName\":\"\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.error").value(containsString("No spec selected")));
	}

}
