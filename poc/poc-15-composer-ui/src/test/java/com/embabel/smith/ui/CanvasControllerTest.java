package com.embabel.smith.ui;

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
 * Offline tests for the visual Composer's REST surface (POC 15). The endpoints delegate
 * to POC 14's proven model, so these pin the canvas type-check, chain, and composite
 * export that the drag-and-drop UI drives.
 *
 * <p>
 * Uses a standalone {@link MockMvc} (spring-test) rather than {@code @WebMvcTest}: the
 * latter lives in the {@code spring-boot-webmvc-test} slice, which is not present in the
 * offline repo at this project's resolved version. A hand-built {@link SpecCatalog}
 * (empty override → walk up to the repo's agent specs) plus a temp export dir keeps the
 * test hermetic and fully offline.
 */
class CanvasControllerTest {

	@TempDir
	Path outDir;

	MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		SpecCatalog catalog = new SpecCatalog("");
		mockMvc = MockMvcBuilders.standaloneSetup(new CanvasController(catalog, outDir)).build();
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
			.andExpect(jsonPath("$.goal.outputType").value("com.embabel.agent.spec.model.BackgroundMessage"))
			.andExpect(jsonPath("$.goal.export").value(true))
			.andExpect(jsonPath("$.steps[0]").value("extractTopic"))
			.andExpect(jsonPath("$.steps[1]").value("writeExplainer"));
	}

}
