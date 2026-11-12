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

package com.embabel.smith.builder.eval;

/**
 * Runs the built spec once and returns the goal output's content. The production
 * implementation deploys the pack and invokes it as a single typed goal run
 * (endpoint-gated); offline tests inject a fake so the evaluate → score → refine flow is
 * verifiable without a live model.
 *
 * @author Ivan Rodriguez
 */
@FunctionalInterface
public interface AgentSpecInvoker {

	String invoke(String input);

}
