/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.config;

import com.embabel.smith.builder.lifecycle.LifecycleStamper;
import com.embabel.smith.builder.publish.PublishResult;
import com.embabel.smith.builder.publish.Publisher;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.samples.SampleAgentSpecs;
import com.embabel.smith.studio.improve.LoopPushDisabledException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Offline tests for the loop's publisher bean (change: publish-keeps-versions, decision
 * 4). A configured improve source still gets a publisher that writes; an unconfigured one
 * refuses the publish and names the setting to change, instead of defaulting the loop's
 * destination to the system temporary directory — a folder there is both overwritten by
 * the next iteration and discardable by the operating system.
 *
 * @author Ivan Rodriguez
 */
class StudioConfigurationLoopPublisherTest {

	private final AgentSpec pack = SampleAgentSpecs.explainer();

	@Test
	void noImproveSourceRefusesThePublishAndNamesTheSetting(@TempDir Path scratch) throws Exception {
		// java.io.tmpdir is redirected rather than observed: the fallback this replaces
		// resolved it when the bean was built, so a scratch directory proves nothing was
		// written there, and the assertion does not depend on what an earlier run left in
		// the real one.
		String tmpdir = System.getProperty("java.io.tmpdir");
		System.setProperty("java.io.tmpdir", scratch.toString());
		try {
			Publisher publisher = new StudioConfiguration().loopPublisher(null, "", "", "");

			assertThatThrownBy(
					() -> publisher.publish(this.pack, new LifecycleStamper().stamp(this.pack, 1.0), true, true))
				.isInstanceOf(LoopPushDisabledException.class)
				.hasMessageContaining(this.pack.name())
				.hasMessageContaining("smith.studio.improve-source")
				.hasMessageContaining("stays pending");
		}
		finally {
			System.setProperty("java.io.tmpdir", tmpdir);
		}
		try (var left = Files.list(scratch)) {
			assertThat(left.findAny()).isEmpty();
		}
	}

	@Test
	void aConfiguredImproveSourceStillGetsAPublisherThatWrites(@TempDir Path library) throws Exception {
		Publisher publisher = new StudioConfiguration().loopPublisher(null, "folder:" + library, "", "");

		PublishResult result = publisher.publish(this.pack, new LifecycleStamper().stamp(this.pack, 1.0), false, false);

		assertThat(result.location()).isEqualTo(library.resolve(this.pack.name()).toString());
		assertThat(Path.of(result.location()).resolve("manifest.yml")).isRegularFile();
	}

}
