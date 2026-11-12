package com.embabel.smith.studio.rest;

import com.embabel.smith.studio.rest.dto.TrustRequest;
import com.embabel.smith.studio.rest.dto.TrustResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Offline tests for the Trust pane: a pack's tool groups against a tier allowlist. */
class TrustControllerTest {

	@Test
	void allowsPackWithNoToolGroupsUnderStandard(@TempDir Path tmp) {
		RestTestFixtures.writePack(tmp, RestTestFixtures.pack("summarizer", "summarize"));
		TrustController controller = new TrustController(new SourceService("folder:" + tmp, ""));
		TrustResponse response = controller.check(new TrustRequest("folder:" + tmp, "summarizer", "STANDARD"));
		assertThat(response.error()).isNull();
		assertThat(response.allowed()).isTrue();
		assertThat(response.disallowedGroups()).isEmpty();
		assertThat(response.tier()).isEqualTo("STANDARD");
	}

	@Test
	void deniesShellToolGroupUnderStandard(@TempDir Path tmp) {
		RestTestFixtures.writePack(tmp, RestTestFixtures.packWithToolGroup("shell-run", "run", "shell"));
		TrustController controller = new TrustController(new SourceService("folder:" + tmp, ""));
		TrustResponse response = controller.check(new TrustRequest("folder:" + tmp, "shell-run", "STANDARD"));
		assertThat(response.allowed()).isFalse();
		assertThat(response.disallowedGroups()).contains("shell");
		assertThat(response.declaredToolGroups()).contains("shell");
	}

	@Test
	void allowsShellToolGroupUnderHigh(@TempDir Path tmp) {
		RestTestFixtures.writePack(tmp, RestTestFixtures.packWithToolGroup("shell-run", "run", "shell"));
		TrustController controller = new TrustController(new SourceService("folder:" + tmp, ""));
		TrustResponse response = controller.check(new TrustRequest("folder:" + tmp, "shell-run", "HIGH"));
		assertThat(response.allowed()).isTrue();
	}

	@Test
	void defaultsToStandardTierWhenUnspecified(@TempDir Path tmp) {
		RestTestFixtures.writePack(tmp, RestTestFixtures.packWithToolGroup("shell-run", "run", "shell"));
		TrustController controller = new TrustController(new SourceService("folder:" + tmp, ""));
		TrustResponse response = controller.check(new TrustRequest("folder:" + tmp, "shell-run", null));
		assertThat(response.allowed()).isFalse();
		assertThat(response.tier()).isEqualTo("STANDARD");
	}

	@Test
	void returnsErrorWhenNoSourceOrSpec() {
		TrustController controller = new TrustController(new SourceService("", ""));
		TrustResponse response = controller.check(new TrustRequest("", "", "STANDARD"));
		assertThat(response.error()).contains("No source or spec");
	}

}
