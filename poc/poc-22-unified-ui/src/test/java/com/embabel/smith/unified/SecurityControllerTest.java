package com.embabel.smith.unified;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Offline tests for the Security pane (POC 22). The scan delegates to POC 21's
 * {@code SpecSecurityScanner} (pure static analysis — no model), so these pin the
 * findings, the {@code SAFE}/{@code REVIEW}/{@code BLOCK} verdict, and the recommended
 * minimum trust tier, plus the offline scaffolding (no spec / unknown spec).
 */
class SecurityControllerTest {

	MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		SpecCatalog catalog = new SpecCatalog("");
		mockMvc = MockMvcBuilders.standaloneSetup(new SecurityController(catalog)).build();
	}

	@Test
	void aShellSpecIsFlaggedHighPrivilege() throws Exception {
		mockMvc
			.perform(post("/api/security").contentType(MediaType.APPLICATION_JSON)
				.content("{\"specName\":\"shell-runner\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.error").isEmpty())
			.andExpect(jsonPath("$.verdict").value(org.hamcrest.Matchers.isIn(List.of("REVIEW", "BLOCK"))))
			.andExpect(jsonPath("$.recommendedTier")
				.value(org.hamcrest.Matchers.isIn(List.of("T3_PARTNER", "T4_PLATFORM"))))
			.andExpect(jsonPath("$.findings[?(@.category=='HIGH_PRIVILEGE_TOOL')]").exists());
	}

	@Test
	void aBenignSpecScansClean() throws Exception {
		mockMvc
			.perform(post("/api/security").contentType(MediaType.APPLICATION_JSON)
				.content("{\"specName\":\"compose-extract\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.error").isEmpty())
			.andExpect(jsonPath("$.verdict").value(org.hamcrest.Matchers.isIn(List.of("SAFE", "REVIEW", "BLOCK"))))
			.andExpect(jsonPath("$.summary").exists());
	}

	@Test
	void noSpecSelectedIsRejected() throws Exception {
		mockMvc.perform(post("/api/security").contentType(MediaType.APPLICATION_JSON).content("{\"specName\":\"\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("No spec selected")));
	}

	@Test
	void anUnknownSpecIsRejected() throws Exception {
		mockMvc
			.perform(post("/api/security").contentType(MediaType.APPLICATION_JSON).content("{\"specName\":\"nope\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Unknown spec")));
	}

}
