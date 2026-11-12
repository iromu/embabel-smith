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

package com.embabel.smith.studio.generate;

/**
 * The model seam for generation: a system prompt plus a user message in, the raw
 * completion out. Endpoint-gated in the production implementation; a stub stands in for
 * offline tests.
 *
 * @author Ivan Rodriguez
 */
@FunctionalInterface
public interface FlowPromptCaller {

	String call(String system, String user);

}
