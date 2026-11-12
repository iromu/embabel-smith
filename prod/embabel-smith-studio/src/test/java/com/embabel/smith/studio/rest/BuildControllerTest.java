package com.embabel.smith.studio.rest;

import com.embabel.smith.builder.BuildOptions;
import com.embabel.smith.builder.BuildResult;
import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleState;
import com.embabel.smith.builder.publish.PublishResult;
import com.embabel.smith.builder.publish.PublishTarget;
import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.studio.rest.dto.BuildRequest;
import com.embabel.smith.studio.rest.dto.BuildResponse;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests for the Author pane: the endpoint gate, the input guards, and delegation
 * to the seam.
 */
class BuildControllerTest {

	@Test
	void returnsStructuredErrorWhenNoEndpoint() {
		BuildController controller = new BuildController((desc, target, options) -> {
			throw new AssertionError("must not build without an endpoint");
		}, new ModelEndpointConfig("", "", "", ""));
		BuildResponse response = controller
			.build(new BuildRequest("a summarizer", "folder:/tmp/out", null, null, null));
		assertThat(response.errorCode()).isEqualTo("MODEL_NOT_CONFIGURED");
		assertThat(response.error()).contains("No resolvable model endpoint");
		assertThat(response.status()).isEqualTo("BLOCKED");
	}

	@Test
	void returnsErrorWhenNoDescription() {
		BuildController controller = new BuildController((desc, target, options) -> {
			throw new AssertionError("must not build without a description");
		}, configured());
		BuildResponse response = controller.build(new BuildRequest("", "folder:/tmp/out", null, null, null));
		assertThat(response.error()).contains("No description");
	}

	@Test
	void returnsErrorWhenNoOutputSource() {
		BuildController controller = new BuildController((desc, target, options) -> {
			throw new AssertionError("must not build without an output source");
		}, configured());
		BuildResponse response = controller.build(new BuildRequest("a summarizer", "", null, null, null));
		assertThat(response.error()).contains("No output source");
	}

	@Test
	void buildsAndReturnsReportWhenConfigured() {
		AtomicReference<PublishTarget> seenTarget = new AtomicReference<>();
		AtomicReference<BuildOptions> seenOptions = new AtomicReference<>();
		BuildController controller = new BuildController((desc, target, options) -> {
			seenTarget.set(target);
			seenOptions.set(options);
			return BuildResult.built("summarizer", 1.0, null,
					new LifecycleMetadata("hash", 1.0, LifecycleState.ACTIVE, "now"),
					new PublishResult(Path.of("/tmp/out/summarizer"), false));
		}, configured());
		BuildResponse response = controller.build(new BuildRequest("a summarizer", "folder:/tmp/out", 5, true, false));
		assertThat(response.status()).isEqualTo("BUILT");
		assertThat(response.packName()).isEqualTo("summarizer");
		assertThat(response.score()).isEqualTo(1.0);
		assertThat(response.published().location()).isEqualTo("/tmp/out/summarizer");
		assertThat(seenTarget.get().type()).isEqualTo(PublishTarget.Type.FOLDER);
		assertThat(seenOptions.get().maxRefine()).isEqualTo(5);
		assertThat(seenOptions.get().overwrite()).isTrue();
	}

	@Test
	void reportsBlockedBuild() {
		BuildController controller = new BuildController((desc, target, options) -> BuildResult.blocked("eval",
				"summarizer", List.of("score below gate"), 0.5, null, null), configured());
		BuildResponse response = controller
			.build(new BuildRequest("a summarizer", "folder:/tmp/out", null, null, null));
		assertThat(response.status()).isEqualTo("BLOCKED");
		assertThat(response.stage()).isEqualTo("eval");
		assertThat(response.issues()).contains("score below gate");
	}

	private static ModelEndpointConfig configured() {
		return new ModelEndpointConfig("http://localhost:4000", "key", "gpt", "gpt");
	}

}
