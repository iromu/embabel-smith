package com.embabel.smith.unified;

import com.embabel.smith.retrieval.scale.LexicalScorer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Offline tests for the Scale pane (POC 22). The measurement delegates to POC 12's
 * {@code ScaleRetrievalRunner} over {@code ScaleSpecLibrary} with the offline lexical
 * scorer, so these pin the precision@1 shape: the full library (core + near-duplicates)
 * is larger than the core, both P@1 values are in [0,1], and the growth curve is
 * non-empty. The body is parsed with Jackson (like {@link RetrieveControllerTest}) so the
 * cross-field {@code fullSize > coreSize} check is a plain numeric comparison rather than
 * a JsonPath matcher.
 */
class ScaleControllerTest {

	private final ObjectMapper json = new ObjectMapper();

	MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders
			.standaloneSetup(new ScaleController(new ScorerBinding(new LexicalScorer(), "lexical")))
			.build();
	}

	@Test
	void scaleReportsPrecisionAt1AndAGrowthCurve() throws Exception {
		String body = mockMvc.perform(get("/api/scale"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		JsonNode node = json.readTree(body);

		assertThat(node.get("scorer").asText()).isEqualTo("lexical");
		int coreSize = node.get("coreSize").asInt();
		int fullSize = node.get("fullSize").asInt();
		double coreP1 = node.get("coreP1").asDouble();
		double fullP1 = node.get("fullP1").asDouble();
		assertThat(coreSize).isGreaterThan(0);
		assertThat(fullSize).isGreaterThan(coreSize);
		assertThat(coreP1).isBetween(0.0, 1.0);
		assertThat(fullP1).isBetween(0.0, 1.0);
		assertThat(node.get("curve").isArray()).isTrue();
		assertThat(node.get("curve").size()).isGreaterThan(0);
	}

}
