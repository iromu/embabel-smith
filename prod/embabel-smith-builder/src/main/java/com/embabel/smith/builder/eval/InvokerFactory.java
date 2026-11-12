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

import com.embabel.smith.common.model.AgentSpec;

/**
 * Produces the {@link AgentSpecInvoker} for a given pack. The production implementation
 * deploys the pack and returns an invoker that runs it; because the pack's prompts change
 * across refinement rounds, a fresh invoker is built for each round's pack.
 *
 * @author Ivan Rodriguez
 */
@FunctionalInterface
public interface InvokerFactory {

	AgentSpecInvoker invokerFor(AgentSpec pack);

}
