/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.publish;

import java.io.IOException;
import java.nio.file.Path;

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleStamper;
import com.embabel.smith.common.io.AgentsPackWriter;
import com.embabel.smith.common.model.AgentsPack;

/**
 * A composed process as a {@link Publishable}: the process manifest with one directory
 * per agent pack, plus the {@code lifecycle.yml} sidecar the whole published process
 * carries. Because the packs land as ordinary pack directories under one parent, a source
 * pointed at the published directory ingests them as the multi-pack world the runtime
 * already composes.
 *
 * @author Ivan Rodriguez
 */
public class AgentsPackPublishable implements Publishable {

	private final AgentsPack agentsPack;

	private final LifecycleMetadata lifecycle;

	private final AgentsPackWriter writer = new AgentsPackWriter();

	private final LifecycleStamper stamper = new LifecycleStamper();

	public AgentsPackPublishable(AgentsPack agentsPack, LifecycleMetadata lifecycle) {
		this.agentsPack = agentsPack;
		this.lifecycle = lifecycle;
	}

	@Override
	public String name() {
		return this.agentsPack.process();
	}

	@Override
	public double baselinePassRate() {
		return this.lifecycle.baselinePassRate();
	}

	@Override
	public void writeTo(Path directory) throws IOException {
		this.writer.write(this.agentsPack, directory);
		this.stamper.write(directory, this.lifecycle);
	}

}
