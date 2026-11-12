package com.embabel.smith.studio.rest;

import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared fixtures for the REST controller tests: builds valid agent specs and writes them
 * as subdirectories of a temp folder source, so the offline panes (packs, security,
 * trust) can be exercised against real, on-disk packs.
 */
final class RestTestFixtures {

	private RestTestFixtures() {
	}

	/**
	 * A valid pack: an exported goal and one plain action step per name (no tool groups).
	 */
	static AgentSpec pack(String name, String... stepNames) {
		return new AgentSpec(new AgentSpecManifest(name, "1.0.0"),
				GoalStepSpec.exportedGoal(name, "goal " + name, "BackgroundMessage"), steps(name, stepNames),
				List.of());
	}

	/**
	 * A valid pack whose single step declares the given tool group (for the Trust pane).
	 */
	static AgentSpec packWithToolGroup(String name, String stepName, String toolGroup) {
		ActionStepSpec step = new ActionStepSpec(ActionStepSpec.STEP_TYPE, stepName, "step " + stepName,
				List.of("UserInput"), "BackgroundMessage", "perform " + stepName + " carefully", null, null, null, null,
				null, null, List.of(toolGroup));
		return new AgentSpec(new AgentSpecManifest(name, "1.0.0"),
				GoalStepSpec.exportedGoal(name, "goal " + name, "BackgroundMessage"), List.of(step), List.of());
	}

	private static List<ActionStepSpec> steps(String name, String... stepNames) {
		List<ActionStepSpec> out = new ArrayList<>();
		for (String step : stepNames) {
			out.add(ActionStepSpec.action(step, "step " + step, List.of("UserInput"), "BackgroundMessage",
					"perform " + step + " carefully"));
		}
		return out;
	}

	/** Writes the pack as a subdirectory named after the pack under the source root. */
	static void writePack(Path sourceRoot, AgentSpec pack) {
		try {
			new AgentSpecWriter().write(pack, sourceRoot.resolve(pack.name()));
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
