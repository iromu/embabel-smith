package com.embabel.smith.unified;

import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.refinement.RefinementRunner;
import org.junit.jupiter.api.BeforeEach;
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
 * Endpoint-gated tests for the Eval pane (POC 22). The eval + refine loop delegates to
 * POC 9's {@code RefinementRunner} behind the {@link EvalController.EvalFn} seam, so the
 * endpoint-present path is stubbed and the no-endpoint path is driven by a real (empty)
 * {@link ModelEndpointConfig}.
 */
class EvalControllerTest {

	private final ModelEndpointConfig noEndpoint = new ModelEndpointConfig("", "", "", "");

	private final ModelEndpointConfig endpoint = new ModelEndpointConfig("http://x", "key", "gpt", "gpt");

	private SpecCatalog catalog;

	@BeforeEach
	void setUp() {
		catalog = new SpecCatalog("");
	}

	private MockMvc with(EvalController.EvalFn fn, ModelEndpointConfig ep) {
		return MockMvcBuilders.standaloneSetup(new EvalController(catalog, fn, ep)).build();
	}

	@Test
	void noEndpointIsReportedClearly() throws Exception {
		MockMvc mvc = with((s, t) -> {
			throw new IllegalStateException("the evaluator must not be reached without an endpoint");
		}, noEndpoint);
		mvc.perform(
				post("/api/eval").contentType(MediaType.APPLICATION_JSON).content("{\"specName\":\"compose-extract\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.error").value(containsString("No resolvable model endpoint")));
	}

	@Test
	void aConfiguredEndpointEvaluatesAndRefines() throws Exception {
		RefinementRunner.RefinementOutcome outcome = new RefinementRunner.RefinementOutcome(0.5, 0.9, "revised prompt",
				List.of("bad input"));
		MockMvc mvc = with((s, t) -> outcome, endpoint);
		mvc.perform(
				post("/api/eval").contentType(MediaType.APPLICATION_JSON).content("{\"specName\":\"compose-extract\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.error").isEmpty())
			.andExpect(jsonPath("$.specName").value("compose-extract"))
			.andExpect(jsonPath("$.beforeScore").value(0.5))
			.andExpect(jsonPath("$.afterScore").value(0.9))
			.andExpect(jsonPath("$.refined").value(true))
			.andExpect(jsonPath("$.improved").value(true))
			.andExpect(jsonPath("$.revisedPrompt").value("revised prompt"))
			.andExpect(jsonPath("$.failingInputs[0]").value("bad input"));
	}

	@Test
	void noSpecSelectedIsRejected() throws Exception {
		MockMvc mvc = with((s, t) -> {
			throw new IllegalStateException("X");
		}, endpoint);
		mvc.perform(post("/api/eval").contentType(MediaType.APPLICATION_JSON).content("{\"specName\":\"\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.error").value(containsString("No spec selected")));
	}

}
