/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.publish;

import java.io.IOException;
import java.nio.file.Path;

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleStamper;
import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.model.AgentSpec;

/**
 * The agent-spec pack as a {@link Publishable}: the pack directory (manifest, goal,
 * steps, types) plus its {@code lifecycle.yml} sidecar, written by the shared
 * {@link AgentSpecWriter} and {@link LifecycleStamper}.
 *
 * @author Ivan Rodriguez
 */
public class AgentSpecPublishable implements Publishable {

	private final AgentSpec pack;

	private final LifecycleMetadata lifecycle;

	private final AgentSpecWriter writer = new AgentSpecWriter();

	private final LifecycleStamper stamper = new LifecycleStamper();

	public AgentSpecPublishable(AgentSpec pack, LifecycleMetadata lifecycle) {
		this.pack = pack;
		this.lifecycle = lifecycle;
	}

	@Override
	public String name() {
		return this.pack.name();
	}

	@Override
	public double baselinePassRate() {
		return this.lifecycle.baselinePassRate();
	}

	@Override
	public void writeTo(Path directory) throws IOException {
		this.writer.write(this.pack, directory);
		this.stamper.write(directory, this.lifecycle);
	}

}
