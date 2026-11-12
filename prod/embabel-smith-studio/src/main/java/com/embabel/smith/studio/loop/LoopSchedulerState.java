/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.studio.loop;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The mutable control state of the self-improvement loop's schedule (change 06): whether
 * the interval schedule is running, and whether gated candidates are auto-published. The
 * schedule is <em>off by default</em> — the loop runs only when started from the UI or
 * triggered on demand.
 *
 * <p>
 * Plain, thread-safe state (no Spring): the {@code @Scheduled} scheduler reads
 * {@link #running()} each tick, and the REST surface flips the flags. The auto-publish
 * flag is consulted by the {@link LoopService} after each iteration, so the toggle takes
 * effect without rebuilding the loop.
 *
 * @author Ivan Rodriguez
 */
public final class LoopSchedulerState {

	private final AtomicBoolean running;

	private final AtomicBoolean autoPublish;

	public LoopSchedulerState(boolean autoPublish) {
		this.running = new AtomicBoolean(false);
		this.autoPublish = new AtomicBoolean(autoPublish);
	}

	/**
	 * Whether the interval schedule is currently running (default: false).
	 * @return whether the interval schedule is currently running.
	 */
	public boolean running() {
		return this.running.get();
	}

	/** Starts the schedule (idempotent). */
	public void start() {
		this.running.set(true);
	}

	/** Stops the schedule (idempotent). */
	public void stop() {
		this.running.set(false);
	}

	/**
	 * Whether gated candidates are auto-published instead of held for approval.
	 * @return whether gated candidates are auto-published.
	 */
	public boolean autoPublish() {
		return this.autoPublish.get();
	}

	/**
	 * Sets the auto-publish mode.
	 * @param autoPublish the auto-publish mode to set.
	 */
	public void setAutoPublish(boolean autoPublish) {
		this.autoPublish.set(autoPublish);
	}

}
