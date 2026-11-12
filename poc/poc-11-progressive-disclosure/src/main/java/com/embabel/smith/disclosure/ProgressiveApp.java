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

package com.embabel.smith.disclosure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.model.AgentSpec;

/**
 * The progressive-disclosure context-cost report (POC 11). Offline: loads a agent spec
 * that ships a bundled reference, and shows the plan context stays bounded as the
 * reference grows, while an eager (inlined) prompt grows linearly.
 *
 * <p>
 * Run: {@code mvn -pl poc/poc-11-progressive-disclosure exec:java}
 *
 * @author Ivan Rodriguez
 */
public final class ProgressiveApp {

	private ProgressiveApp() {
	}

	public static void main(String[] args) throws IOException {
		Path packDir = findPack("reference-heavy");
		AgentSpec pack = new AgentSpecReader().read(packDir);
		String baseReference = Files.readString(packDir.resolve("references/domain.md"));

		ProgressiveDisclosureRunner runner = new ProgressiveDisclosureRunner();

		System.out.println("=== POC 11: progressive-disclosure stress test ===");
		System.out.println("Spec: " + pack.name() + " — ships a bundled reference (references/domain.md)");
		System.out.printf("%n%-12s %-16s %-18s %-18s %-10s%n", "reference", "plan context", "progressive prompt",
				"eager prompt", "eager/progressive");
		System.out.println("-".repeat(76));

		for (int scale : new int[] { 1, 10, 50, 100 }) {
			String reference = repeat(baseReference, scale);
			ProgressiveDisclosureRunner.ContextCost cost = runner.measure(pack, reference);
			System.out.printf("%-12s %-16d %-18d %-18d %-10.2fx%n", kb(cost.referenceChars()), cost.planContextChars(),
					cost.progressivePromptChars(), cost.eagerPromptChars(), cost.eagerToProgressiveRatio());
		}

		ProgressiveDisclosureRunner.ContextCost small = runner.measure(pack, baseReference);
		System.out.printf(
				"%nPlan context is constant (%d chars) while the reference grows — the planner never loads the%n",
				small.planContextChars());
		System.out.println(
				"bundled reference. Eager disclosure pays the reference cost in every prompt; progressive disclosure");
		System.out.println("fetches it on demand, keeping the loaded context bounded.");
	}

	private static String repeat(String text, int times) {
		StringBuilder sb = new StringBuilder(text.length() * times);
		for (int i = 0; i < times; i++) {
			sb.append(text);
		}
		return sb.toString();
	}

	private static String kb(int chars) {
		return String.format("%.1f KB", chars / 1024.0);
	}

	private static Path findPack(String name) {
		Path dir = Path.of("").toAbsolutePath();
		while (dir != null) {
			Path candidate = dir.resolve("agent-specs").resolve(name);
			if (Files.isDirectory(candidate)) {
				return candidate;
			}
			dir = dir.getParent();
		}
		throw new IllegalStateException("Could not locate agent-specs/" + name);
	}

}
