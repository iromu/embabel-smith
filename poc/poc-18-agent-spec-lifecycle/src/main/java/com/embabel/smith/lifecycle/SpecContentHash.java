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

package com.embabel.smith.lifecycle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.DynamicTypeDef;

/**
 * A deterministic content hash of a agent spec's load-bearing surface: the goal (name +
 * output type), each action step (name, inputs, output, tool groups, prompt), and each
 * dynamic type.
 *
 * <p>
 * Any change to what the spec <em>is</em> — its prompt, its referenced types, its tool
 * groups — changes the hash. That is the <em>content drift</em> signal: the "silent
 * breakage" doc 02 §5 caveat 4 warns about, where a spec quietly diverges from the
 * version its baseline eval was recorded against (the APIs / types / prompt it references
 * have changed).
 *
 * @author Ivan Rodriguez
 */
public final class SpecContentHash {

	private SpecContentHash() {
	}

	public static String of(AgentSpec pack) {
		StringBuilder canonical = new StringBuilder();
		canonical.append("goal:")
			.append(pack.goal().name())
			.append('|')
			.append(pack.goal().outputTypeName())
			.append('\n');
		for (ActionStepSpec step : pack.steps()) {
			canonical.append("step:")
				.append(step.name())
				.append("|in:")
				.append(step.inputTypeNames())
				.append("|out:")
				.append(step.outputTypeName())
				.append("|tools:")
				.append(step.toolGroups())
				.append("|prompt:")
				.append(step.prompt())
				.append('\n');
		}
		for (DynamicTypeDef type : pack.types()) {
			canonical.append("type:").append(type.name()).append("|props:").append(type.ownProperties()).append('\n');
		}
		return sha256Hex(canonical.toString());
	}

	private static String sha256Hex(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(bytes.length * 2);
			for (byte b : bytes) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}

}
