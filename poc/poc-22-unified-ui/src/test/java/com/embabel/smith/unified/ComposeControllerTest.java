package com.embabel.smith.unified;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Offline tests for the Compose pane (POC 22). The endpoints delegate to POC 14's proven
 * model, so these pin the canvas type-check, chain, and composite export the
 * drag-and-drop UI drives. A hand-built {@link SpecCatalog} (empty override → walk up to
 * the repo's agent-specs dir) plus a temp export dir keeps the test hermetic and fully
 * offline.
 */
class ComposeControllerTest {

	@TempDir
	Path outDir;

	MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		SpecCatalog catalog = new SpecCatalog("");
		mockMvc = MockMvcBuilders.standaloneSetup(new ComposeController(catalog, outDir)).build();
	}

	@Test
	void listsTheComposeSpecs() throws Exception {
		mockMvc.perform(get("/api/specs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.name=='compose-extract')]").exists())
			.andExpect(jsonPath("$[?(@.name=='compose-explain')]").exists());
	}

	@Test
	void aValidChainIsAccepted() throws Exception {
		String body = "{\"nodes\":[\"compose-extract\",\"compose-explain\"],"
				+ "\"edges\":[{\"from\":\"compose-extract\",\"to\":\"compose-explain\"}]}";
		mockMvc.perform(post("/api/canvas").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.valid").value(true))
			.andExpect(jsonPath("$.chain[0]").value("compose-extract"))
			.andExpect(jsonPath("$.chain[1]").value("compose-explain"));
	}

	@Test
	void aTypeIncompatibleEdgeIsRejected() throws Exception {
		// explain outputs BackgroundMessage, which extract does not consume (its input is
		// UserInput).
		String body = "{\"nodes\":[\"compose-explain\",\"compose-extract\"],"
				+ "\"edges\":[{\"from\":\"compose-explain\",\"to\":\"compose-extract\"}]}";
		mockMvc.perform(post("/api/canvas").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.valid").value(false))
			.andExpect(jsonPath("$.issues[0]").exists());
	}

	@Test
	void composeExportsTheComposite() throws Exception {
		String body = "{\"name\":\"compose-composite\"," + "\"nodes\":[\"compose-extract\",\"compose-explain\"],"
				+ "\"edges\":[{\"from\":\"compose-extract\",\"to\":\"compose-explain\"}]}";
		mockMvc.perform(post("/api/compose").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.goal.export").value(true))
			.andExpect(jsonPath("$.steps").isArray())
			.andExpect(jsonPath("$.issues").isEmpty());
	}

}
